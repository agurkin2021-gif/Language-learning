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
            assertNotNull(find(root, "＋ Устойчивое выражение"));
            assertNotNull(find(root, "Обновление"));
            assertNotNull(find(root, "Версия / сборка"));
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
        });
        tap("Сказки");
        getInstrumentation().runOnMainSync(() -> {
            View root = getActivity().getWindow().getDecorView();
            assertNull(find(root, "Обновление"));
            assertNull(find(root, "Версия / сборка"));
            assertNotNull(find(root, "⚙"));
        });
        tap("Открыть и слушать");
        getInstrumentation().runOnMainSync(() -> {
            View root = getActivity().getWindow().getDecorView();
            TextView spoken = (TextView) find(root, "Лусия живёт в маленьком доме.");
            assertNotNull(spoken);
            assertFalse("Spanish story text has no selection handles", spoken.isTextSelectable());
            assertNull(find(root, "Обновление"));
            assertNotNull(find(root, "⚙"));
        });
        tap("Читать весь рассказ");
        getInstrumentation().runOnMainSync(() -> {
            TextView original = (TextView) find(getActivity().getWindow().getDecorView(),
                    "Lucía vive en una casa pequeña.");
            assertNotNull("Spanish sentences must be individually touchable", original);
            assertFalse(original.isTextSelectable());
        });
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
}
