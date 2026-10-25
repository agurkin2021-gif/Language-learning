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
        });
        tap("Освоенное"); tap("← К изучению");
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
            assertNotNull(find(root, "Лусия живёт в маленьком доме."));
            assertNull(find(root, "Обновление"));
            assertNotNull(find(root, "⚙"));
        });
        tap("⚙");
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        getInstrumentation().waitForIdleSync();
        for(int i=0;i<8;i++) tap("Повторить предложение");
        tap("Карточки"); tap("Сказки"); tap("Открыть и слушать");
        getInstrumentation().runOnMainSync(() -> assertNotNull(find(getActivity().getWindow().getDecorView(),"← Библиотека")));
        tap("← Библиотека");
    }
}
