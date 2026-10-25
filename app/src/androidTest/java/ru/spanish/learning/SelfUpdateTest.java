package ru.spanish.learning;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.test.InstrumentationTestRunner;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONObject;

/**
 * Opt-in two-process test driven only by scripts/test_self_update.sh on an emulator.
 * A successful full APK replacement kills the first instrumentation process. The host
 * must verify the installed version and run the second test; that process death alone
 * is never accepted as a passing test. Ordinary connected tests do not set selfUpdate.
 */
@android.annotation.TargetApi(34)
public final class SelfUpdateTest extends InstrumentationTestCase {
    private static final String DATABASE = "self-update-check.db";
    private static final String PREFS = "self_update_test";

    private boolean enabled() {
        Bundle arguments = ((InstrumentationTestRunner) getInstrumentation()).getArguments();
        return "true".equals(arguments.getString("selfUpdate"));
    }

    private Context target() { return getInstrumentation().getTargetContext(); }

    private long installedVersion(Context context) throws Exception {
        return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
    }

    private String digest(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder value = new StringBuilder();
        for (byte b : digest.digest()) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return value.toString();
    }

    public void testPrepareAndRequestSelfUpdate() throws Exception {
        if (!enabled()) return;
        Context context = target();
        assertEquals("This integration test must run on Android 14", 34, Build.VERSION.SDK_INT);
        assertTrue("Only a debug test fixture may be replaced", BuildConfig.DEBUG);
        assertTrue("The host must grant the normal one-time installer permission",
                context.getPackageManager().canRequestPackageInstalls());
        File oldReport = new File(context.getFilesDir(), "self-update-result.json");
        assertTrue("Remove any stale result before beginning", !oldReport.exists() || oldReport.delete());
        File fixture = new File(context.getFilesDir(), "updates/self-update-fixture.apk");
        assertTrue("The host must supply the next-version APK", fixture.isFile());
        PackageInfo next = context.getPackageManager().getPackageArchiveInfo(
                fixture.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        assertNotNull("APK must parse", next);
        long previousVersion = installedVersion(context);
        long nextVersion = next.getLongVersionCode();
        assertEquals(context.getPackageName(), next.packageName);
        assertEquals("Test exactly one increasing version", previousVersion + 1, nextVersion);

        context.deleteDatabase(DATABASE);
        long wordId, storyId;
        try (Store store = new Store(context, DATABASE)) {
            store.addCard("mate", "мате — сохранение при обновлении", 0);
            wordId = store.cards(false, 0).get(0).id();
            store.setMastered(wordId, true);
            store.addCard("por favor", "пожалуйста — сохранение при обновлении", 1);
            storyId = store.addStory("Проверка обновления", "Uno. Dos.", "Тест самообновления");
            store.position(storyId, 1);
        }
        assertTrue(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .clear().putLong("previous_version", previousVersion).putLong("next_version", nextVersion)
                .putLong("word_id", wordId).putLong("story_id", storyId)
                .putString("sentinel", "retained-through-self-update")
                .putBoolean("request_started", true).commit());

        AppUpdater.setForeground(false);
        assertTrue("The production installer must commit the verified APK",
                AppUpdater.installVerified(context, fixture, nextVersion, digest(fixture)));

        // Replacing this package should stop this process. If it does not, report
        // failure rather than turn a mere queued PackageInstaller session into success.
        SystemClock.sleep(30000);
        fail("Package replacement did not stop the old process: " + AppUpdater.status(context));
    }

    public void testUpdatedVersionKeepsDataAndNeedsNoConfirmation() throws Exception {
        if (!enabled()) return;
        Context context = target();
        SharedPreferences saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long previous = saved.getLong("previous_version", -1);
        long expected = saved.getLong("next_version", -1);
        assertTrue("The first process must have seeded the fixture", saved.getBoolean("request_started", false));
        assertTrue(previous > 0);
        assertEquals(previous + 1, expected);
        assertEquals("The package manager must report the actual new APK", expected, installedVersion(context));
        assertEquals("retained-through-self-update", saved.getString("sentinel", ""));
        assertFalse("No system install confirmation may be outstanding", AppUpdater.hasPendingConfirmation(context));

        SharedPreferences update = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE);
        assertEquals("PackageInstaller must have delivered SUCCESS", PackageInstaller.STATUS_SUCCESS,
                update.getInt("last_result", Integer.MIN_VALUE));
        assertEquals("updated", update.getString("state", ""));
        assertEquals(expected, update.getLong("last_installed_version", -1));

        try (Store store = new Store(context, DATABASE)) {
            assertEquals(1, store.cards(true, 0).size());
            Store.Card word = store.cards(true, 0).get(0);
            assertEquals(saved.getLong("word_id", -1), word.id());
            assertEquals("mate", word.spanish());
            assertEquals("мате — сохранение при обновлении", word.meaning());
            assertTrue(store.cards(false, 0).isEmpty());
            assertEquals(1, store.cards(false, 1).size());
            assertEquals("por favor", store.cards(false, 1).get(0).spanish());
            Store.Story story = null;
            for (Store.Story candidate : store.stories()) {
                if (candidate.id() == saved.getLong("story_id", -1)) story = candidate;
            }
            assertNotNull(story);
            assertEquals("Проверка обновления", story.title());
            assertEquals("Uno. Dos.", story.body());
            assertEquals(1, story.position());
        }
        JSONObject report = new JSONObject();
        report.put("success", true);
        report.put("sdk", Build.VERSION.SDK_INT);
        report.put("previous_version", previous);
        report.put("installed_version", expected);
        report.put("package_installer_status", update.getInt("last_result", -1));
        report.put("manual_confirmation_required", false);
        report.put("sqlite_cards_archive_story_position_preserved", true);
        report.put("shared_preferences_preserved", true);
        try (FileOutputStream output = new FileOutputStream(new File(context.getFilesDir(), "self-update-result.json"))) {
            output.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
    }
}
