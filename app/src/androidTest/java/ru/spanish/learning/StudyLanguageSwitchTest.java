package ru.spanish.learning;

import android.content.Context;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import java.util.List;

/** ES/EN corner switch: validates both screens while leaving Spanish data intact. */
public final class StudyLanguageSwitchTest
        extends ActivityInstrumentationTestCase2<MainActivity> {
    public StudyLanguageSwitchTest() { super(MainActivity.class); }

    private View find(View root, String label) {
        if (root instanceof TextView && label.contentEquals(((TextView) root).getText()))
            return root;
        if (root instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) root;
            for (int i = 0; i < parent.getChildCount(); i++) {
                View result = find(parent.getChildAt(i), label);
                if (result != null) return result;
            }
        }
        return null;
    }

    private void tap(String label) {
        getInstrumentation().runOnMainSync(() -> {
            View view = find(getActivity().getWindow().getDecorView(), label);
            assertNotNull("Missing label: " + label, view);
            assertTrue("Could not tap " + label, view.performClick());
        });
        getInstrumentation().waitForIdleSync();
    }

    private void select(String label) {
        for (int attempt = 0; attempt < 35; attempt++) {
            AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(label);
                for (AccessibilityNodeInfo node : nodes) {
                    if (!label.contentEquals(node.getText())) continue;
                    AccessibilityNodeInfo clickable = node;
                    while (!clickable.isClickable() && clickable.getParent() != null)
                        clickable = clickable.getParent();
                    if (clickable.isClickable()) {
                        assertTrue("Unable to choose " + label,
                                clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK));
                        getInstrumentation().waitForIdleSync();
                        return;
                    }
                }
            }
            SystemClock.sleep(120);
        }
        fail("Language choice not found: " + label);
    }

    public void testCornerSwitchChangesScreensAndRetainsSpanishCards() {
        // Deterministic start without deleting either existing workspace.
        getInstrumentation().getTargetContext()
                .getSharedPreferences("ui", Context.MODE_PRIVATE)
                .edit().putString("studyLanguage", "es")
                .putString("screen", "cards").commit();
        getActivity();
        getInstrumentation().waitForIdleSync();

        getInstrumentation().runOnMainSync(() -> {
            View root = getActivity().getWindow().getDecorView();
            assertNotNull(find(root, "ES ▾"));
            assertNotNull(find(root, "Испанский • аргентинский вариант"));
            assertNotNull(find(root, "Карточек: 950"));
        });
        tap("ES ▾");
        select("Английский (EN)");
        getInstrumentation().runOnMainSync(() -> {
            View root = getActivity().getWindow().getDecorView();
            assertNotNull(find(root, "EN ▾"));
            assertNotNull(find(root, "Английский • личный словарь"));
            assertNull("Spanish starter set must not appear in English",
                    find(root, "Карточек: 950"));
            assertNotNull(find(root, "ChatGPT — голос / текст"));
        });

        tap("Сказки");
        getInstrumentation().runOnMainSync(() -> {
            View root = getActivity().getWindow().getDecorView();
            assertNotNull(find(root, "Библиотека английских сказок"));
            assertNotNull(find(root, "EN ▾"));
            assertNotNull(find(root, "⚙"));
        });
        tap("EN ▾");
        select("Испанский (ES)");
        tap("Карточки");
        getInstrumentation().runOnMainSync(() -> {
            View root = getActivity().getWindow().getDecorView();
            assertNotNull(find(root, "ES ▾"));
            assertNotNull(find(root, "Испанский • аргентинский вариант"));
            assertNotNull(find(root, "Карточек: 950"));
        });
    }
}
