package ru.spanish.learning;

import android.os.SystemClock;
import android.content.ContentValues;
import android.view.accessibility.AccessibilityNodeInfo;
import android.test.ActivityInstrumentationTestCase2;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.List;

/** Exercises swipe behavior, permanent deletion and marking a card learned. */
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

    public void testCardViewAndImagesStayMountedBetweenSwipes() {
        getActivity();
        getInstrumentation().waitForIdleSync();
        View viewport = tagged(root(), "card-swipe-viewport");
        View initialCard = tagged(root(), "card-swipe-front");
        View nextCard = tagged(root(), "card-swipe-next");
        assertNotNull(viewport);
        assertNotNull(initialCard);
        assertNotNull(nextCard);
        int[] initialCounter = counter();

        gesture(.8f, .2f);
        assertSame("Pager remains attached instead of rebuilding the screen",
                viewport, tagged(root(), "card-swipe-viewport"));
        assertSame("The preloaded next card itself becomes the centered card",
                nextCard, tagged(root(), "card-swipe-front"));
        assertEquals(initialCounter[0] % initialCounter[1] + 1, counter()[0]);

        gesture(.2f, .8f);
        assertSame("Reversing returns the original Card view, not a new WebView",
                initialCard, tagged(root(), "card-swipe-front"));
        assertSame("The pager is reused after navigating both directions",
                viewport, tagged(root(), "card-swipe-viewport"));
        assertEquals(initialCounter[0], counter()[0]);
    }

    private void clickAlertAction(String label) {
        for (int attempt = 0; attempt < 35; attempt++) {
            AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(label)) {
                    if (!label.contentEquals(node.getText())) continue;
                    AccessibilityNodeInfo clickable = node;
                    while (!clickable.isClickable() && clickable.getParent() != null)
                        clickable = clickable.getParent();
                    if (clickable.isClickable()) {
                        assertTrue("Cannot press dialog action " + label,
                                clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK));
                        getInstrumentation().waitForIdleSync();
                        return;
                    }
                }
            }
            SystemClock.sleep(120);
        }
        fail("Dialog action not found: " + label);
    }

    public void testLeftCrossConfirmsPermanentDeletionNotMastery() {
        android.content.Context context = getInstrumentation().getTargetContext();
        context.getSharedPreferences("ui", 0).edit()
                .putString("studyLanguage", "es")
                .putString("screen", "cards")
                .putInt("kind", 0)
                .putInt("category", 1)
                .putInt("reviewIndex", 0).commit();
        getActivity();
        getInstrumentation().waitForIdleSync();

        Store.Card target;
        int total = counter()[1];
        try (Store store = new Store(context)) {
            target = store.cards(false, 0, 1).get(counter()[0] - 1);
            assertEquals(950, store.vocabulary().size());
        }

        getInstrumentation().runOnMainSync(() -> {
            View delete = tagged(root(), "card-delete-forever");
            assertNotNull(delete);
            delete.performClick();
        });
        getInstrumentation().waitForIdleSync();
        clickAlertAction("Отмена");
        assertEquals("Cancellation keeps the card visible", total, counter()[1]);

        try (Store store = new Store(context)) {
            assertTrue("Cancellation keeps the card in the study list",
                    store.cards(false, 0, 1).stream().anyMatch(c -> c.id() == target.id()));
        }

        boolean removed = false;
        try {
            getInstrumentation().runOnMainSync(() -> {
                View delete = tagged(root(), "card-delete-forever");
                assertNotNull(delete);
                delete.performClick();
            });
            getInstrumentation().waitForIdleSync();
            clickAlertAction("Удалить");
            removed = true;
            assertEquals("Confirmed deletion removes one card", total - 1, counter()[1]);
            try (Store store = new Store(context)) {
                assertEquals(949, store.vocabulary().size());
                assertFalse("Deleted card must not remain in vocabulary or mastered archive",
                        store.vocabulary().stream().anyMatch(c -> c.id() == target.id()));
                assertFalse(store.mastered().stream().anyMatch(c -> c.id() == target.id()));
            }
        } finally {
            // Restore the fixture with its original ID and state, even if an assertion fails.
            if (removed) try (Store store = new Store(context)) {
                ContentValues values = new ContentValues();
                values.put("id", target.id());
                values.put("spanish", target.spanish());
                values.put("meaning", target.meaning());
                values.put("kind", target.kind());
                values.put("category", target.category());
                values.put("mastered", target.mastered() ? 1 : 0);
                values.put("last_result", target.lastResult());
                values.put("review_count", target.reviewCount());
                values.put("next_due", target.nextDue());
                store.getWritableDatabase().insertOrThrow("cards", null, values);
            }
        }
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

    public void testBothSwipeDirectionsTapAndKnownCheck() {
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
                TextView delete = text(root(), "×");
                TextView learned = text(root(), "✓");
                assertNotNull("Left permanent-delete cross", delete);
                assertNotNull("Right green learned check", learned);
                assertEquals("Удалить карточку навсегда",
                        delete.getContentDescription().toString());
                assertEquals("Знаю — перенести карточку в освоенное",
                        learned.getContentDescription().toString());
                assertSame(delete, tagged(root(), "card-delete-forever"));
                assertSame(learned, tagged(root(), "card-mark-known"));
                learned.performClick();
            });
            getInstrumentation().waitForIdleSync();
            assertEquals("Green check marks card as learned in one tap", total - 1, counter()[1]);
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
