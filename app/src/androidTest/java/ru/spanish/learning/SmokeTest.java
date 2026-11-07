package ru.spanish.learning;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class SmokeTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public SmokeTest() { super(MainActivity.class); }
    private View find(View v,String text) {
        if(v instanceof TextView && text.equals(((TextView)v).getText().toString())) return v;
        if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) { View result=find(((ViewGroup)v).getChildAt(i),text); if(result!=null) return result; }
        return null;
    }
    private void tap(String text) {
        getInstrumentation().runOnMainSync(() -> {
            View v=find(getActivity().getWindow().getDecorView(),text); assertNotNull(text,v); v.performClick();
        });
        getInstrumentation().waitForIdleSync();
    }
    private void openSampleStory() {
        getInstrumentation().runOnMainSync(() -> {
            View root = getActivity().getWindow().getDecorView();
            View name = find(root, "Una tarde en la plaza — пример");
            assertNotNull("Sample story should appear as a single list row", name);
            assertEquals("Story title must fit on one line", 1, ((TextView) name).getMaxLines());
            View row = (View) name.getParent();
            assertTrue("Tap the whole story row", row.isClickable());
            int heightDp = Math.round(row.getHeight() / getActivity().getResources().getDisplayMetrics().density);
            assertTrue("Story row should be compact: " + heightDp + "dp", heightDp >= 48 && heightDp <= 52);
            assertNull("Large action button must be removed from the list", find(root, "Открыть и слушать"));
            row.performClick();
        });
        getInstrumentation().waitForIdleSync();
    }
    public void testNavigationAndPlayerSurviveRepeatedTaps() {
        getActivity(); tap("Карточки");
        getInstrumentation().runOnMainSync(() -> {
            View root = getActivity().getWindow().getDecorView();
            assertNotNull(find(root, "＋ Слово"));
            View addWord = find(root, "＋ Слово");
            View addPhrase = find(root, "＋ Устойчивое выражение");
            assertNotNull(addWord);
            assertNotNull(addPhrase);
            assertEquals("The two bottom buttons share one height", addWord.getHeight(), addPhrase.getHeight());
            assertEquals("Both bottom buttons align along their top edge", addWord.getTop(), addPhrase.getTop());
            assertNull("No update button on the cards screen", find(root, "Обновление"));
            assertNull("No build details button on the cards screen", find(root, "Версия / сборка"));
            TextView version = (TextView) find(root, "Версия " + BuildConfig.VERSION_NAME);
            assertNotNull("Version label beneath the study card", version);
            float scaledDensity = getActivity().getResources().getDisplayMetrics().scaledDensity;
            assertEquals("Version is small text", 11f, version.getTextSize() / scaledDensity, 0.1f);
            assertNotNull(find(root, "ChatGPT — голос / текст"));
            assertNull(find(root, "⚙"));
        });
        getInstrumentation().runOnMainSync(() -> {
            View root = getActivity().getWindow().getDecorView();
            assertNull("Mastered archive must not be exposed", find(root, "Освоенное"));
            assertNull("No archive return action", find(root, "← К изучению"));
            assertNull("No archived card listing", find(root, "Вернуть к изучению"));
            assertNotNull(find(root, "Карточки"));
            assertNotNull("Language button in corner of Cards",
                    find(root, "ES ▾"));
        });
        tap("Рассказы");
        getInstrumentation().runOnMainSync(() -> {
            View root = getActivity().getWindow().getDecorView();
            assertNull(find(root, "Обновление"));
            assertNull(find(root, "Версия / сборка"));
            assertNotNull(find(root, "⚙"));
            assertNotNull("Language button in corner of Stories",
                    find(root, "ES ▾"));
        });
        openSampleStory();
        getInstrumentation().runOnMainSync(() -> {
            View root = getActivity().getWindow().getDecorView();
            assertNotNull(find(root, "Лусия живёт в маленьком доме."));
            assertNotNull("Story audio can be exported", find(root, "Отправить аудио"));
            TextView spanish = (TextView) find(root, "Lucía vive en una casa pequeña.");
            assertNotNull("Spanish story sentence is visible", spanish);
            assertFalse("Spanish story text has no selection handles", spanish.isTextSelectable());
            assertNull(find(root, "Обновление"));
            assertNotNull(find(root, "⚙"));
        });
        // The full-story button restarts audio from sentence one without a text dialog.
        getInstrumentation().runOnMainSync(() -> {
            MainActivity activity = getActivity();
            View button = find(activity.getWindow().getDecorView(), "Читать весь рассказ заново");
            assertNotNull("Full story audio button", button);
            try {
                java.lang.reflect.Field field = MainActivity.class.getDeclaredField("player");
                field.setAccessible(true);
                SpeechPlayer player = (SpeechPlayer) field.get(activity);
                assertTrue("Sample has multiple sentences", player.state.count > 2);
                player.pause();
                player.state.load(player.state.count, 2);
                player.sentenceMode = true;
                button.performClick();
                assertEquals("Restart reading at the first sentence", 0, player.state.index);
                assertFalse("Whole-story narration must continue automatically", player.sentenceMode);
                assertEquals("Lucía vive en una casa pequeña.", player.current());
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        });
        getInstrumentation().waitForIdleSync();
        tap("⚙");
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        getInstrumentation().waitForIdleSync();
        for(int i=0;i<8;i++) tap("← Назад");
        tap("Карточки"); tap("Рассказы"); openSampleStory();
        getInstrumentation().runOnMainSync(() -> assertNotNull(find(getActivity().getWindow().getDecorView(),"← Библиотека")));
        tap("← Библиотека");
    }
    public void testBackControlRewindsOneSentencePerTapAndMovesAuxiliaryControlsDown() {
        getActivity();
        tap("Рассказы");
        openSampleStory();
        getInstrumentation().runOnMainSync(() -> {
            MainActivity activity = getActivity();
            View root = activity.getWindow().getDecorView();
            View back = find(root, "← Назад");
            View original = find(root, "Lucía vive en una casa pequeña.");
            View language = find(root, "Язык озвучки: Испанский ▾");
            View attribution = find(root, "Встроенный пример • написан заранее, не ИИ-генерация по архиву");
            assertNotNull("Back button must be visible", back);
            assertNotNull("Original story sentence", original);
            assertNotNull("Narration selector", language);
            assertNotNull("Source in footer", attribution);
            try {
                java.lang.reflect.Field toggleField = MainActivity.class.getDeclaredField("toggle");
                java.lang.reflect.Field statusField = MainActivity.class.getDeclaredField("playbackStatus");
                toggleField.setAccessible(true);
                statusField.setAccessible(true);
                View primary = (View) toggleField.get(activity);
                View status = (View) statusField.get(activity);
                View panel = (View) original.getParent();
                assertNotNull("Main Continue/Pause button", primary);
                assertNotNull("Playback status", status);
                ViewGroup container = (ViewGroup) panel.getParent();
                assertSame("Main player button shares the story content layout", container, primary.getParent());
                assertEquals("Continue/Pause must be directly above the reading panel",
                        container.indexOfChild(primary) + 1, container.indexOfChild(panel));
                assertTrue("Status follows the primary button",
                        container.indexOfChild(status) > container.indexOfChild(primary));
                assertTrue("Footer language follows the primary button",
                        container.indexOfChild(language) > container.indexOfChild(primary));
                assertTrue("Main audio control remains a large tap target",
                        primary.getMinimumHeight() >= Math.round(70 *
                                activity.getResources().getDisplayMetrics().density));
            } catch (ReflectiveOperationException reflection) {
                throw new AssertionError(reflection);
            }
            assertNull("Old repeat button is removed", find(root, "Повторить предложение"));
            assertNull("Word-holding hint is removed",
                    find(root, "Удерживайте незнакомое испанское слово, чтобы добавить его в карточки."));
            int[] backXY = new int[2], textXY = new int[2], languageXY = new int[2], sourceXY = new int[2];
            back.getLocationOnScreen(backXY);
            original.getLocationOnScreen(textXY);
            language.getLocationOnScreen(languageXY);
            attribution.getLocationOnScreen(sourceXY);
            assertTrue("Back button is above the story text", backXY[1] < textXY[1]);
            assertTrue("Narration selector is below the story text", languageXY[1] > textXY[1]);
            assertTrue("Attribution is below narration selector", sourceXY[1] > languageXY[1]);
            assertTrue("Back button is left aligned", backXY[0] <= original.getResources()
                    .getDisplayMetrics().widthPixels / 3);
            assertEquals("Narration selector uses compact text", 13f,
                    ((TextView) language).getTextSize() /
                            activity.getResources().getDisplayMetrics().scaledDensity, 0.2f);

            try {
                java.lang.reflect.Field field = MainActivity.class.getDeclaredField("player");
                field.setAccessible(true);
                SpeechPlayer player = (SpeechPlayer) field.get(activity);
                assertTrue(player.state.count > 5);
                player.pause();
                player.state.load(player.state.count, 5);
                back.performClick();
                assertEquals("First tap repeats current phrase", 5, player.state.index);
                back.performClick();
                assertEquals("Second tap goes back one", 4, player.state.index);
                back.performClick();
                assertEquals("Third tap goes back two", 3, player.state.index);
                for (int i = 0; i < 10; i++) back.performClick();
                assertEquals("Rewind never goes below zero", 0, player.state.index);
                player.pause();
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    public void testStoryCanSwitchSpanishAndRussianNarrationWithoutChangingDisplayedText() {
        getActivity();
        tap("Рассказы");
        openSampleStory();
        getInstrumentation().runOnMainSync(() -> {
            MainActivity activity = getActivity();
            View root = activity.getWindow().getDecorView();
            assertNotNull("Narration language button must be visible", find(root,
                    "Язык озвучки: Испанский ▾"));
            assertTrue("Russian translation can be selected", activity.selectStoryPlaybackLanguage(true));
            root = activity.getWindow().getDecorView();
            assertNotNull(find(root, "Язык озвучки: Русский ▾"));
            assertNotNull("Original Spanish is still readable",
                    find(root, "Lucía vive en una casa pequeña."));
            assertNotNull("Russian translation is visible",
                    find(root, "Лусия живёт в маленьком доме."));
            try {
                java.lang.reflect.Field field = MainActivity.class.getDeclaredField("player");
                field.setAccessible(true);
                SpeechPlayer player = (SpeechPlayer) field.get(activity);
                assertEquals("ru", player.learningLanguage());
                assertEquals("Лусия живёт в маленьком доме.", player.current());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
            assertTrue(activity.selectStoryPlaybackLanguage(false));
            root = activity.getWindow().getDecorView();
            assertNotNull(find(root, "Язык озвучки: Испанский ▾"));
            assertNotNull(find(root, "Lucía vive en una casa pequeña."));
        });
        getInstrumentation().waitForIdleSync();
    }

    public void testChatGptSendShowsVisibleProgressAndDisablesDuplicates() throws Exception {
        getActivity();
        tap("Рассказы");
        tap("ChatGPT — голос / текст");
        getInstrumentation().runOnMainSync(() -> {
            MainActivity screen = getActivity();
            try {
                java.lang.reflect.Field sendField =
                        MainActivity.class.getDeclaredField("assistantSendButton");
                java.lang.reflect.Field spinnerField =
                        MainActivity.class.getDeclaredField("assistantProgressRow");
                sendField.setAccessible(true);
                spinnerField.setAccessible(true);
                android.widget.Button send = (android.widget.Button) sendField.get(screen);
                View progress = (View) spinnerField.get(screen);
                assertNotNull(send);
                assertNotNull(progress);
                assertEquals(View.GONE, progress.getVisibility());

                screen.updateAssistantBusy(true, "ChatGPT сочиняет рассказ…");
                assertEquals(View.VISIBLE, progress.getVisibility());
                assertEquals("⏳ Генерирую…", send.getText().toString());
                assertFalse("Duplicate send is blocked while busy", send.isEnabled());
                screen.updateAssistantBusy(false, null);
                assertEquals(View.GONE, progress.getVisibility());
                assertEquals("Отправить", send.getText().toString());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        getInstrumentation().waitForIdleSync();
    }

}
