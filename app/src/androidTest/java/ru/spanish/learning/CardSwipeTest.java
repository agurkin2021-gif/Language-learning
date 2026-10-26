package ru.spanish.learning;

import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.List;

/** Exercises the swipe surface, tap-to-translate and the non-destructive known action. */
public final class CardSwipeTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public CardSwipeTest() { super(MainActivity.class); }

    private View tagged(View root, String tag) {
        if (tag.equals(root.getTag())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = tagged(group.getChildAt(i), tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    private TextView text(View root, String expected) {
        if (root instanceof TextView && expected.equals(((TextView) root).getText().toString())) return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = text(group.getChildAt(i), expected);
                if (found != null) return found;
            }
        }
        return null;
    }

    private View root() { return getActivity().getWindow().getDecorView(); }

    private int[] counter() {
        ViewGroup view = (ViewGroup) root();
        return findCounter(view);
    }

    private int[] findCounter(View view) {
        if (view instanceof TextView) {
            String value = ((TextView) view).getText().toString();
            if (value.matches("[0-9]+ / [0-9]+")) {
                String[] pieces = value.split(" / ");
                return new int[]{Integer.parseInt(pieces[0]), Integer.parseInt(pieces[1])};
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                int[] counter = findCounter(group.getChildAt(i));
                if (counter != null) return counter;
            }
        }
        return null;
    }

    private void gesture(float fromFraction, float toFraction) {
        getInstrumentation().runOnMainSync(() -> {
            View surface = tagged(root(), "card-swipe-surface");
            View front = tagged(root(), "card-swipe-front");
            View next = tagged(root(), "card-swipe-next");
            assertNotNull("Swipe touch surface", surface);
            assertNotNull("Current card view", front);
            assertNotNull("Neighboring card view", next);
            assertTrue(surface.getWidth() > 200);
            long time = SystemClock.uptimeMillis();
            float y = surface.getHeight() / 2f;
            float start = surface.getWidth() * fromFraction;
            float end = surface.getWidth() * toFraction;
            MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, start, y, 0);
            surface.dispatchTouchEvent(down);
            down.recycle();
            MotionEvent move = MotionEvent.obtain(time, time + 30, MotionEvent.ACTION_MOVE, end, y, 0);
            surface.dispatchTouchEvent(move);
            move.recycle();
            if (fromFraction != toFraction) {
                float delta = end - start;
                assertEquals("Current card follows the finger horizontally",
                        delta, front.getTranslationX(), 1.5f);
                assertTrue("Adjacent card enters the viewport during swipe",
                        delta < 0 ? next.getTranslationX() < surface.getWidth()
                                : tagged(root(), "card-swipe-previous").getTranslationX() > -surface.getWidth());
            }
            MotionEvent up = MotionEvent.obtain(time, time + 60, MotionEvent.ACTION_UP, end, y, 0);
            surface.dispatchTouchEvent(up);
            up.recycle();
        });
        if (fromFraction != toFraction) SystemClock.sleep(380);
        getInstrumentation().waitForIdleSync();
    }

    public void testShortDragReturnsToSameCard() {
        getActivity();
        getInstrumentation().waitForIdleSync();
        int[] before = counter();
        gesture(.52f, .43f); // Less than 18% of the card width: animate back.
        assertEquals(before[0], counter()[0]);
        assertEquals(before[1], counter()[1]);
        getInstrumentation().runOnMainSync(() -> {
            View front = tagged(root(), "card-swipe-front");
            assertEquals("Canceled swipe snaps back to its original place",
                    0f, front.getTranslationX(), 1.5f);
        });
    }

    public void testBothSwipeDirectionsTapAndKnownCross() {
        getActivity();
        getInstrumentation().runOnMainSync(() -> {
            TextView tab = text(root(), "Карточки");
            assertNotNull(tab);
            tab.performClick();
        });
        getInstrumentation().waitForIdleSync();
        int[] originalCounter = counter();
        assertNotNull(originalCounter);
        int originalIndex = originalCounter[0];
        int total = originalCounter[1];
        assertTrue(total > 2);

        int kind = getActivity().getSharedPreferences("ui", 0).getInt("kind", 0);
        int category = getActivity().getSharedPreferences("ui", 0).getInt("category", 0);
        long[] learnedId = {-1};
        try (Store store = new Store(getInstrumentation().getTargetContext())) {
            List<Store.Card> active = store.cards(false, kind, kind == 0 ? category : 0);
            assertEquals(total, active.size());
            Store.Card initial = active.get(originalIndex - 1);
            learnedId[0] = initial.id();
            int originalVocabularySize = store.vocabulary().size();
            assertNotNull(text(root(), initial.spanish()));
            assertNull("Old action must not appear", text(root(), "Не знаю"));
            assertNull("Old action must not appear", text(root(), "Знаю"));

            gesture(.5f, .5f);
            assertNotNull("Tap reveals Russian", text(root(), initial.meaning()));
            gesture(.8f, .2f);
            assertEquals("Swipe left moves to next", originalIndex % total + 1, counter()[0]);
            assertNotNull("New card begins in Spanish", text(root(), active.get(originalIndex % total).spanish()));
            gesture(.2f, .8f);
            assertEquals("Swipe right returns to previous", originalIndex, counter()[0]);
            assertNotNull("Returning begins in Spanish", text(root(), initial.spanish()));

            getInstrumentation().runOnMainSync(() -> {
                TextView cross = text(root(), "×");
                assertNotNull("Top-right red known cross", cross);
                assertEquals("Открыть подтверждение удаления из изучения",
                        cross.getContentDescription().toString());
                cross.performClick();
                TextView confirm = text(root(), "УДАЛИТЬ");
                assertNotNull("Second-tap confirmation pill", confirm);
                assertEquals(View.VISIBLE, confirm.getVisibility());
            });
            getInstrumentation().waitForIdleSync();
            assertEquals("First tap must not remove the current card", total, counter()[1]);
            for (Store.Card item : store.vocabulary()) {
                if (item.id() == learnedId[0]) assertFalse("No mastery before confirmation", item.mastered());
            }

            getInstrumentation().runOnMainSync(() -> {
                // Tapping the card without confirming closes the pill and keeps the card.
                View surface = tagged(root(), "card-swipe-surface");
                assertNotNull(surface);
                surface.performClick();
                assertEquals(View.GONE, text(root(), "УДАЛИТЬ").getVisibility());

                TextView cross = text(root(), "×");
                cross.performClick();
                TextView confirm = text(root(), "УДАЛИТЬ");
                assertEquals(View.VISIBLE, confirm.getVisibility());
                confirm.performClick();
            });
            getInstrumentation().waitForIdleSync();
            assertEquals("Only confirmation marks the card as learned", total - 1, counter()[1]);
            assertEquals("Card is retained in the dictionary", originalVocabularySize, store.vocabulary().size());
            boolean mastered = false;
            for (Store.Card card : store.vocabulary()) {
                if (card.id() == learnedId[0]) { mastered = card.mastered(); break; }
            }
            assertTrue("Learned card is retained in complete vocabulary", mastered);
        } finally {
            try (Store store = new Store(getInstrumentation().getTargetContext())) {
                if (learnedId[0] >= 0) store.setMastered(learnedId[0], false);
            }
        }
    }
}
