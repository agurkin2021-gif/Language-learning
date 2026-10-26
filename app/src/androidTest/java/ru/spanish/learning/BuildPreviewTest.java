package ru.spanish.learning;

import android.graphics.Bitmap;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;

/** Screenshots of the actual installed app for the private CI build report. */
public class BuildPreviewTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public BuildPreviewTest() { super(MainActivity.class); }

    private View find(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = find(group.getChildAt(i), text);
                if (result != null) return result;
            }
        }
        return null;
    }

    private void tap(String text) {
        getInstrumentation().runOnMainSync(() -> {
            View button = find(getActivity().getWindow().getDecorView(), text);
            assertNotNull("Missing navigation button: " + text, button);
            button.performClick();
        });
        getInstrumentation().waitForIdleSync();
    }

    private void openSampleStory() {
        getInstrumentation().runOnMainSync(() -> {
            View name = find(getActivity().getWindow().getDecorView(), "Una tarde en la plaza — пример");
            assertNotNull("Compact story row is missing", name);
            ((View) name.getParent()).performClick();
        });
        getInstrumentation().waitForIdleSync();
    }

    private void capture(String filename) throws Exception {
        getInstrumentation().waitForIdleSync();
        getInstrumentation().getUiAutomation().waitForIdle(300, 5000);
        Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull("Android could not capture the app screen", screenshot);
        File directory = getInstrumentation().getTargetContext().getExternalFilesDir("build-preview");
        assertNotNull("App screenshot directory unavailable", directory);
        assertTrue("Could not create screenshot directory", directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, filename))) {
            assertTrue("Could not encode screenshot", screenshot.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            screenshot.recycle();
        }
    }

    public void testCaptureBuildScreens() throws Exception {
        getActivity();
        tap("Карточки");
        // Navigate out of a remembered archive without removing any saved data.
        getInstrumentation().runOnMainSync(() -> {
            View back = find(getActivity().getWindow().getDecorView(), "← Вернуться к изучению");
            if (back != null) back.performClick();
        });
        capture("01-cards.png");
        tap("Сказки");
        capture("02-story-library.png");
        openSampleStory();
        capture("02-story-player.png");
    }
}
