package ru.spanish.learning;

import android.app.UiAutomation;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

/** Verify both destructive confirmations without ever wiping the shared emulator test database. */
public final class ResetDatabaseUiTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public ResetDatabaseUiTest() { super(MainActivity.class); }

    private View find(View root, String expected) {
        if (root instanceof TextView && expected.equals(((TextView) root).getText().toString())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) root;
            for (int i = 0; i < parent.getChildCount(); i++) {
                View found = find(parent.getChildAt(i), expected);
                if (found != null) return found;
            }
        }
        return null;
    }

    private View findDescription(View root, String description) {
        CharSequence label = root.getContentDescription();
        if (label != null && description.contentEquals(label)) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void tapActivity(String label) {
        getInstrumentation().runOnMainSync(() -> {
            View view = find(getActivity().getWindow().getDecorView(), label);
            assertNotNull(label, view);
            view.performClick();
        });
        getInstrumentation().waitForIdleSync();
    }

    private AccessibilityNodeInfo findAccessibleText(AccessibilityNodeInfo node, String expected) {
        if (node == null) return null;
        CharSequence label = node.getText();
        // Android's native AlertDialog buttons can appear uppercased by the theme.
        if (label != null && expected.equalsIgnoreCase(label.toString().trim())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo match = findAccessibleText(node.getChild(i), expected);
            if (match != null) return match;
        }
        return null;
    }

    private AccessibilityNodeInfo inActiveDialog(String expected) {
        UiAutomation ui = getInstrumentation().getUiAutomation();
        for (int attempt = 0; attempt < 20; attempt++) {
            AccessibilityNodeInfo found = findAccessibleText(ui.getRootInActiveWindow(), expected);
            if (found != null) return found;
            SystemClock.sleep(120);
        }
        fail("Missing dialog text: " + expected);
        return null;
    }

    private void tapDialog(String label) {
        AccessibilityNodeInfo current = inActiveDialog(label);
        while (!current.isClickable() && current.getParent() != null) current = current.getParent();
        assertTrue("Dialog action must be clickable: " + label, current.isClickable());
        assertTrue("Dialog click failed: " + label,
                current.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        getInstrumentation().waitForIdleSync();
    }

    public void testStoryDeleteNeedsSecondTapAndCanBeCancelled() {
        getInstrumentation().getTargetContext()
                .getSharedPreferences("ui", android.content.Context.MODE_PRIVATE)
                .edit().putString("studyLanguage", "es").commit();
        getActivity();
        String title = "Тест удаления рассказа " + System.nanoTime();
        long id;
        try (Store db = new Store(getInstrumentation().getTargetContext())) {
            id = db.addStory(title, "Hola.", "Привет.", "Личный тест");
        }
        tapActivity("Рассказы");
        String action = "Удалить рассказ: " + title;
        getInstrumentation().runOnMainSync(() -> {
            View root = getActivity().getWindow().getDecorView();
            View name = find(root, title);
            View cross = findDescription(root, action);
            assertNotNull(name);
            assertNotNull(cross);
            assertEquals("Cross and title belong to one compact row", name.getParent(), cross.getParent());
            assertTrue(((View) name.getParent()).isClickable());
            assertTrue(cross.isClickable());
            cross.performClick();
        });
        getInstrumentation().waitForIdleSync();
        assertNotNull(inActiveDialog("Удалить рассказ?"));
        try (Store db = new Store(getInstrumentation().getTargetContext())) {
            assertTrue("First click must not delete", db.stories().stream()
                    .anyMatch(story -> story.id() == id));
        }
        tapDialog("Отмена");
        try (Store db = new Store(getInstrumentation().getTargetContext())) {
            assertTrue("Cancel must preserve the story", db.stories().stream()
                    .anyMatch(story -> story.id() == id));
        }
        getInstrumentation().runOnMainSync(() -> {
            View cross = findDescription(getActivity().getWindow().getDecorView(), action);
            assertNotNull(cross);
            cross.performClick();
        });
        getInstrumentation().waitForIdleSync();
        tapDialog("Удалить");
        try (Store db = new Store(getInstrumentation().getTargetContext())) {
            assertFalse("Second click deletes exactly the selected story", db.stories().stream()
                    .anyMatch(story -> story.id() == id));
            assertTrue("The original story must remain", db.stories().stream()
                    .anyMatch(story -> "Una tarde en la plaza — пример".equals(story.title())));
        }
        getInstrumentation().runOnMainSync(() ->
                assertNull("Deleted story disappears immediately",
                        find(getActivity().getWindow().getDecorView(), title)));
    }

    public void testBothResetDialogStagesCanBeCanceledWithoutDeletingWords() {
        getActivity();
        int originalCount;
        try (Store store = new Store(getInstrumentation().getTargetContext())) {
            originalCount = store.vocabulary().size();
            assertTrue("Test begins with a working learning database", originalCount > 0);
        }

        tapActivity("Рассказы");
        tapActivity("⚙");
        tapDialog("Сброс базы");
        assertNotNull(inActiveDialog("Сбросить базу?"));
        tapDialog("Отмена");

        tapDialog("Сброс базы");
        tapDialog("Продолжить");
        assertNotNull(inActiveDialog("Точно удалить всё?"));
        tapDialog("Отмена");

        try (Store store = new Store(getInstrumentation().getTargetContext())) {
            assertEquals("Canceling either confirmation must preserve all words",
                    originalCount, store.vocabulary().size());
            assertFalse("Canceling must not disable the shared feed",
                    store.sharedContentDisabled());
        }
        tapDialog("Закрыть");
        tapActivity("Карточки");
    }
}
