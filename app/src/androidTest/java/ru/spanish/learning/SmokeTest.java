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
        tap("Сказки");
        getInstrumentation().runOnMainSync(() -> {
            View root = getActivity().getWindow().getDecorView();
            assertNull(find(root, "Обновление"));
            assertNull(find(root, "Версия / сборка"));
            assertNotNull(find(root, "⚙"));
            assertNotNull("Language button in corner of Stories",
                    find(root, "ES ▾"));
        });
        tap("Открыть и слушать");
        getInstrumentation().runOnMainSync(() -> {
            View root = getActivity().getWindow().getDecorView();
            assertNotNull(find(root, "Лусия живёт в маленьком доме."));
            TextView spanish = (TextView) find(root, "Lucía vive en una casa pequeña.");
            assertNotNull("Spanish story sentence is visible", spanish);
            assertFalse("Spanish story text has no selection handles", spanish.isTextSelectable());
            assertNull(find(root, "Обновление"));
            assertNotNull(find(root, "⚙"));
        });
        // The full-story view is a separate Dialog window, not Activity.getWindow().
        // Its individually touchable words are exercised by StoryWordPickerTest and StoreTest.
        tap("Читать весь рассказ");
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        getInstrumentation().waitForIdleSync();
        tap("⚙");
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        getInstrumentation().waitForIdleSync();
        for(int i=0;i<8;i++) tap("Повторить предложение");
        tap("Карточки"); tap("Сказки"); tap("Открыть и слушать");
        getInstrumentation().runOnMainSync(() -> assertNotNull(find(getActivity().getWindow().getDecorView(),"← Библиотека")));
        tap("← Библиотека");
    }
    public void testChatGptSendShowsVisibleProgressAndDisablesDuplicates() throws Exception {
        getActivity();
        tap("Сказки");
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

                screen.updateAssistantBusy(true, "ChatGPT сочиняет сказку…");
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
