package ru.spanish.learning;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.text.Layout;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * Real Android long presses: one gesture places a visible, persisted marker.
 * No tap during audio playback and no timing-calibration button.
 */
public final class ManualPauseGestureAndroidTest
        extends ActivityInstrumentationTestCase2<MainActivity> {
    public ManualPauseGestureAndroidTest() { super(MainActivity.class); }

    private View find(View view, String value) {
        if (view instanceof TextView && value.equals(((TextView) view).getText().toString()))
            return view;
        if (view instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view;
            for (int i = 0; i < parent.getChildCount(); i++) {
                View result = find(parent.getChildAt(i), value);
                if (result != null) return result;
            }
        }
        return null;
    }

    private void tap(String label) {
        getInstrumentation().runOnMainSync(() -> {
            View button = find(getActivity().getWindow().getDecorView(), label);
            assertNotNull(label, button);
            button.performClick();
        });
        getInstrumentation().waitForIdleSync();
    }

    private String pauseKey() throws Exception {
        Field field = MainActivity.class.getDeclaredField("manualStoryKey");
        field.setAccessible(true);
        return (String) field.get(getActivity());
    }

    private void hold(TextView label, int visibleOffset) {
        float[] coordinate = new float[2];
        getInstrumentation().runOnMainSync(() -> {
            Layout layout = label.getLayout();
            assertNotNull("Sentence must have a measurable text layout", layout);
            int line = layout.getLineForOffset(visibleOffset);
            int[] position = new int[2];
            label.getLocationOnScreen(position);
            coordinate[0] = position[0] + label.getTotalPaddingLeft()
                    + layout.getPrimaryHorizontal(visibleOffset);
            coordinate[1] = position[1] + label.getTotalPaddingTop()
                    + (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f;
        });
        long time = SystemClock.uptimeMillis();
        getInstrumentation().sendPointerSync(MotionEvent.obtain(time, time,
                MotionEvent.ACTION_DOWN, coordinate[0], coordinate[1], 0));
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 250L);
        getInstrumentation().sendPointerSync(MotionEvent.obtain(time,
                SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
                coordinate[0], coordinate[1], 0));
        getInstrumentation().waitForIdleSync();
    }

    public void testOneHoldPerGapPersistsMultipleExactPositions() throws Exception {
        getActivity();
        tap("Рассказы");
        getInstrumentation().runOnMainSync(() -> {
            View title = find(getActivity().getWindow().getDecorView(),
                    "Una tarde en la plaza — пример");
            assertNotNull(title);
            ((View) title.getParent()).performClick();
        });
        getInstrumentation().waitForIdleSync();

        final String original = "Lucía vive en una casa pequeña.";
        final int first = original.indexOf(" vive");
        final int second = original.indexOf(" casa");
        final String key = pauseKey();
        final SharedPreferences settings = getActivity()
                .getSharedPreferences("ui", Context.MODE_PRIVATE);
        final TextView[] target = new TextView[1];

        // Tests should not inherit personal markers from prior emulator runs.
        getInstrumentation().runOnMainSync(() -> {
            settings.edit().remove(key).commit();
            try {
                Field f = MainActivity.class.getDeclaredField("manualStoryMarks");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<Integer, List<Integer>> marks = (Map<Integer, List<Integer>>)
                        f.get(getActivity());
                marks.clear();
                target[0] = (TextView) find(getActivity().getWindow().getDecorView(), original);
                assertNotNull(target[0]);
                target[0].setText(original);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        getInstrumentation().waitForIdleSync();

        try {
            hold(target[0], first);
            List<Integer> saved = ManualStoryPauses.decode(settings.getString(key, ""),
                    List.of(original)).get(0);
            assertEquals(List.of(first), saved);
            assertEquals(1, target[0].getText().toString().split("⏸", -1).length - 1);

            hold(target[0], second + ManualStoryPauses.SYMBOL.length());
            saved = ManualStoryPauses.decode(settings.getString(key, ""),
                    List.of(original)).get(0);
            assertEquals(List.of(first, second), saved);
            assertEquals(2, target[0].getText().toString().split("⏸", -1).length - 1);

            hold(target[0], first); // holding the visible marker removes it
            saved = ManualStoryPauses.decode(settings.getString(key, ""),
                    List.of(original)).get(0);
            assertEquals(List.of(second), saved);
            assertNull(find(getActivity().getWindow().getDecorView(),
                    "Зафиксировать паузу"));
            assertNull(find(getActivity().getWindow().getDecorView(),
                    "Перезаписать тайминги пауз"));
        } finally {
            settings.edit().remove(key).commit();
        }
    }
}
