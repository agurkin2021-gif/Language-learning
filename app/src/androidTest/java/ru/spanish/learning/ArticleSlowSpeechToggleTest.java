package ru.spanish.learning;

import android.accessibilityservice.AccessibilityService;
import android.app.UiAutomation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import java.util.List;

/** The global rate control belongs to Settings, never above the reader. */
public final class ArticleSlowSpeechToggleTest
        extends ActivityInstrumentationTestCase2<MainActivity> {
    public ArticleSlowSpeechToggleTest() { super(MainActivity.class); }

    private View byText(View v, String text) {
        if (v instanceof TextView && text.equals(((TextView) v).getText().toString())) return v;
        if (v instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = byText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        return null;
    }

    private View byTag(View v, String tag) {
        if (tag.equals(v.getTag())) return v;
        if (v instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = byTag(group.getChildAt(i), tag);
                if (found != null) return found;
            }
        return null;
    }

    private boolean clickAccessibilityText(String text) {
        UiAutomation automation = getInstrumentation().getUiAutomation();
        for (int attempt = 0; attempt < 15; attempt++) {
            AccessibilityNodeInfo root = automation.getRootInActiveWindow();
            if (root != null) {
                try {
                    List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
                    for (AccessibilityNodeInfo node : nodes) {
                        if (node.getText() != null && text.contentEquals(node.getText())
                                && node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                            return true;
                    }
                } finally {
                    root.recycle();
                }
            }
            SystemClock.sleep(150);
        }
        return false;
    }

    public void testToggleLivesOnlyInSettingsAndRestoresLastRate() throws Exception {
        SharedPreferences prefs = getInstrumentation().getTargetContext()
                .getSharedPreferences("ui", Context.MODE_PRIVATE);
        float original = prefs.getFloat("shortWordSpeed", .45f);
        float oldLast = prefs.getFloat("lastShortWordSpeed", .45f);
        String oldScreen = prefs.getString("screen", "cards");
        String oldProfile = prefs.getString("activeProfile", "papa");
        String oldLanguage = prefs.getString("studyLanguage", "es");
        try {
            prefs.edit().putString("activeProfile", "papa")
                    .putString("studyLanguage", "es")
                    .putString("screen", "stories")
                    .putBoolean("storyReadRussian", false)
                    .putFloat("shortWordSpeed", .45f)
                    .putFloat("lastShortWordSpeed", .6f).commit();
            MainActivity activity = getActivity();
            getInstrumentation().waitForIdleSync();
            getInstrumentation().runOnMainSync(() -> {
                View root = activity.getWindow().getDecorView();
                View name = byText(root, "Una tarde en la plaza — пример");
                assertNotNull("Sample story missing", name);
                ((View) name.getParent()).performClick();
            });
            getInstrumentation().waitForIdleSync();

            java.lang.reflect.Field field = MainActivity.class.getDeclaredField("player");
            field.setAccessible(true);
            SpeechPlayer player = (SpeechPlayer) field.get(activity);
            assertTrue("Slow words enabled on initial story", player.slowArticlesEnabled());
            assertEquals(.45f, player.shortWordSpeed, .001f);

            getInstrumentation().runOnMainSync(() -> {
                View reader = activity.getWindow().getDecorView();
                assertNull("No article toggle is permitted over the reading panel",
                        byTag(reader, "story-short-word-toggle"));
                View settings = byText(reader, "⚙");
                assertNotNull("Settings gear must remain reachable", settings);
                settings.performClick();
            });
            getInstrumentation().waitForIdleSync();

            assertTrue("Off control must exist in Settings",
                    clickAccessibilityText("ОТКЛЮЧИТЬ замедление (los, las, del, el, la, les)"));
            getInstrumentation().waitForIdleSync();
            assertFalse("Off takes effect without reopening story", player.slowArticlesEnabled());
            assertEquals(0f, player.shortWordSpeed, .001f);
            assertEquals(0f, prefs.getFloat("shortWordSpeed", -1f), .001f);

            assertTrue("On control must replace off control in Settings",
                    clickAccessibilityText("ВКЛЮЧИТЬ замедление (los, las, del, el, la, les)"));
            getInstrumentation().waitForIdleSync();
            assertTrue("On takes effect without reopening story", player.slowArticlesEnabled());
            assertEquals(.6f, player.shortWordSpeed, .001f);
            assertEquals(.6f, prefs.getFloat("shortWordSpeed", -1f), .001f);
            getInstrumentation().runOnMainSync(() ->
                    assertNull("Toggle must not reappear on story reader",
                            byTag(activity.getWindow().getDecorView(), "story-short-word-toggle")));
        } finally {
            prefs.edit().putString("activeProfile", oldProfile)
                    .putString("studyLanguage", oldLanguage).putString("screen", oldScreen)
                    .putFloat("shortWordSpeed", original)
                    .putFloat("lastShortWordSpeed", oldLast).commit();
        }
    }
}
