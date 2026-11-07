package ru.spanish.learning;

import android.content.Context;
import android.content.SharedPreferences;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** The global on/off control must take effect on the OPEN story immediately. */
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

    public void testToggleIsImmediateAndRestoresLastRate() throws Exception {
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
                View button = byTag(activity.getWindow().getDecorView(), "story-short-word-toggle");
                assertNotNull("Off switch must be in story reader", button);
                assertTrue(((TextView) button).getText().toString().startsWith("ОТКЛЮЧИТЬ"));
                button.performClick();
            });
            getInstrumentation().waitForIdleSync();
            assertFalse("Off takes effect without reopening story", player.slowArticlesEnabled());
            assertEquals(0f, player.shortWordSpeed, .001f);
            assertEquals(0f, prefs.getFloat("shortWordSpeed", -1f), .001f);

            getInstrumentation().runOnMainSync(() -> {
                View button = byTag(activity.getWindow().getDecorView(), "story-short-word-toggle");
                assertNotNull("On switch must replace off switch immediately", button);
                assertTrue(((TextView) button).getText().toString().startsWith("ВКЛЮЧИТЬ"));
                button.performClick();
            });
            getInstrumentation().waitForIdleSync();
            assertTrue("On takes effect without reopening story", player.slowArticlesEnabled());
            assertEquals(.6f, player.shortWordSpeed, .001f);
            assertEquals(.6f, prefs.getFloat("shortWordSpeed", -1f), .001f);
        } finally {
            prefs.edit().putString("activeProfile", oldProfile)
                    .putString("studyLanguage", oldLanguage).putString("screen", oldScreen)
                    .putFloat("shortWordSpeed", original)
                    .putFloat("lastShortWordSpeed", oldLast).commit();
        }
    }
}
