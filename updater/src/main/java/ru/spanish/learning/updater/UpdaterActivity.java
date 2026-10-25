package ru.spanish.learning.updater;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

public final class UpdaterActivity extends Activity {
    public static final String ACTION_UPDATE = "ru.spanish.learning.updater.UPDATE";
    private static final String TARGET = "ru.spanish.learning";
    private static final String REPOSITORY = "agurkin2021-gif/Language-learning";
    private static final String BASE = "https://raw.githubusercontent.com/" + REPOSITORY + "/refs/heads/phone-builds/";
    private static final String RELEASE_CERT = "ea5402c00a64b4a6e1940624743c58c38593a12b15ca6c85e955b05ba5ac641d";
    private static final String CALLBACK_SESSION = "session_id";
    private static final long MAX_APK_BYTES = 32L * 1024 * 1024;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static volatile WeakReference<UpdaterActivity> active = new WeakReference<>(null);

    private TextView status;
    private boolean started;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        active = new WeakReference<>(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);
        ProgressBar progress = new ProgressBar(this);
        root.addView(progress);
        status = new TextView(this);
        status.setTextSize(20);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 32, 0, 0);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        active = new WeakReference<>(this);
        if (started) return;
        if (!getPackageManager().canRequestPackageInstalls()) {
            show("Один раз разрешите этому приложению устанавливать обновления.");
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException e) {
                fail("Не удалось открыть разрешение Android.");
            }
            return;
        }
        started = true;
        beginUpdate();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!RUNNING.get()) {
            started = false;
            onResume();
        }
    }

    private void beginUpdate() {
        if (!RUNNING.compareAndSet(false, true)) {
            show("Обновление уже выполняется.");
            return;
        }
        show("Проверяю новую версию…");
        WORKER.execute(() -> {
            boolean committed = false;
            try {
                Candidate candidate = loadCandidate();
                if (candidate == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Установлена актуальная версия", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }
                install(candidate);
                committed = true;
            } catch (Exception error) {
                Log.e("SpanishUpdater", "Update failed", error);
                fail("Обновление не выполнено.");
            } finally {
                if (!committed) RUNNING.set(false);
            }
        });
    }

    private Candidate loadCandidate() throws Exception {
        String testPath = BuildConfig.DEBUG ? getIntent().getStringExtra("test_apk_path") : null;
        if (testPath != null && !testPath.isEmpty()) {
            File file = new File(testPath).getCanonicalFile();
            if (!file.getPath().startsWith(getFilesDir().getCanonicalPath() + File.separator))
                throw new IOException("Bad test path");
            return verifyCandidate(file, -1, null, true);
        }

        JSONObject metadata = new JSONObject(new String(readUrl(BASE + "build-info.json", 16 * 1024), StandardCharsets.UTF_8));
        if (!TARGET.equals(metadata.optString("application_id"))
                || !REPOSITORY.equals(metadata.optString("repository"))
                || !"phone-builds".equals(metadata.optString("channel"))
                || !RELEASE_CERT.equalsIgnoreCase(metadata.optString("certificate_sha256")))
            throw new IOException("Bad metadata");
        long expectedVersion = metadata.getLong("version_code");
        PackageInfo installed = installedPackage();
        if (version(installed) >= expectedVersion) return null;
        String expectedSha = metadata.getString("apk_sha256").toLowerCase(Locale.ROOT);
        if (!expectedSha.matches("[0-9a-f]{64}")) throw new IOException("Bad digest");

        showFromWorker("Скачиваю обновление…");
        File apk = new File(getFilesDir(), "SpanishLearning.apk");
        download(BASE + "SpanishLearning.apk", apk);
        if (!expectedSha.equals(sha256(apk))) throw new IOException("Digest mismatch");
        return verifyCandidate(apk, expectedVersion, expectedSha, false);
    }

    private Candidate verifyCandidate(File apk, long expectedVersion, String expectedSha, boolean debugTest) throws Exception {
        if (!apk.isFile() || apk.length() == 0 || apk.length() > MAX_APK_BYTES) throw new IOException("Bad APK");
        PackageInfo candidate = getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (candidate == null || !TARGET.equals(candidate.packageName)) throw new IOException("Wrong package");
        PackageInfo installed = installedPackage();
        long candidateVersion = version(candidate);
        if (candidateVersion <= version(installed)) throw new IOException("Not newer");
        if (expectedVersion > 0 && candidateVersion != expectedVersion) throw new IOException("Wrong version");
        if (expectedSha != null && !expectedSha.equals(sha256(apk))) throw new IOException("Wrong digest");

        String[] candidateSigners = signerDigests(candidate);
        String[] installedSigners = signerDigests(installed);
        Arrays.sort(candidateSigners);
        Arrays.sort(installedSigners);
        if (!Arrays.equals(candidateSigners, installedSigners) || candidateSigners.length != 1)
            throw new IOException("Wrong signer");
        if (!debugTest && !RELEASE_CERT.equals(candidateSigners[0])) throw new IOException("Untrusted signer");
        return new Candidate(apk, candidateVersion);
    }

    private void install(Candidate candidate) throws Exception {
        showFromWorker("Устанавливаю обновление…");
        PackageInstaller installer = getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(TARGET);
        params.setSize(candidate.apk.length());
        params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        if (Build.VERSION.SDK_INT >= 33) params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE);

        int sessionId = installer.createSession(params);
        boolean committed = false;
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try (InputStream input = new FileInputStream(candidate.apk);
                 OutputStream output = session.openWrite("base.apk", 0, candidate.apk.length())) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                session.fsync(output);
            }
            Intent result = new Intent(this, InstallResultReceiver.class)
                    .setAction(getPackageName() + ".INSTALL_RESULT." + sessionId)
                    .putExtra(CALLBACK_SESSION, sessionId);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent callback = PendingIntent.getBroadcast(this, sessionId, result, flags);
            session.commit(callback.getIntentSender());
            committed = true;
        } finally {
            if (!committed) {
                try { installer.abandonSession(sessionId); } catch (RuntimeException ignored) {}
            }
        }
    }

    static void handleInstallResult(Context context, Intent result) {
        int statusCode = result.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        UpdaterActivity activity = active.get();
        if (statusCode == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation;
            if (Build.VERSION.SDK_INT >= 33) confirmation = result.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            else {
                @SuppressWarnings("deprecation") Intent legacy = result.getParcelableExtra(Intent.EXTRA_INTENT);
                confirmation = legacy;
            }
            if (confirmation != null && activity != null && !activity.isFinishing()) {
                activity.runOnUiThread(() -> {
                    try {
                        activity.show("Android просит подтвердить первый переход на новый установщик.");
                        activity.startActivity(confirmation);
                    } catch (RuntimeException error) {
                        activity.fail("Не удалось открыть подтверждение Android.");
                    }
                });
            }
            return;
        }
        if (statusCode == PackageInstaller.STATUS_SUCCESS) {
            RUNNING.set(false);
            if (activity != null && !activity.isFinishing()) {
                activity.runOnUiThread(() -> activity.afterSuccess());
            }
            return;
        }
        RUNNING.set(false);
        if (activity != null && !activity.isFinishing()) {
            String message = result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            activity.fail(message == null || message.isBlank() ? "Android отменил установку." : message);
        }
    }

    private void afterSuccess() {
        try {
            InstallSourceInfo source = getPackageManager().getInstallSourceInfo(TARGET);
            String installer = source.getInstallingPackageName();
            if (!getPackageName().equals(installer)) {
                fail("Обновление установлено, но Android не назначил этот Updater установщиком.");
                return;
            }
        } catch (PackageManager.NameNotFoundException e) {
            fail("Обновлённое приложение не найдено.");
            return;
        }
        show("Готово.");
        Intent launch = getPackageManager().getLaunchIntentForPackage(TARGET);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        }
        finish();
    }

    private PackageInfo installedPackage() throws PackageManager.NameNotFoundException {
        return getPackageManager().getPackageInfo(TARGET, PackageManager.GET_SIGNING_CERTIFICATES);
    }

    private static long version(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private static String[] signerDigests(PackageInfo info) throws Exception {
        Signature[] signatures = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        if (signatures == null) return new String[0];
        String[] result = new String[signatures.length];
        for (int i = 0; i < signatures.length; i++)
            result[i] = hex(MessageDigest.getInstance("SHA-256").digest(signatures[i].toByteArray()));
        return result;
    }

    private static byte[] readUrl(String url, long limit) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("User-Agent", "SpanishLearning-Updater");
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > limit) throw new IOException("Too large");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    private static void download(String url, File destination) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("User-Agent", "SpanishLearning-Updater");
        try (InputStream input = connection.getInputStream(); FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_APK_BYTES) throw new IOException("Too large");
                out.write(buffer, 0, count);
            }
            out.getFD().sync();
        } finally {
            connection.disconnect();
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }

    private void show(String text) {
        if (status != null) status.setText(text);
    }

    private void showFromWorker(String text) {
        runOnUiThread(() -> show(text));
    }

    private void fail(String text) {
        runOnUiThread(() -> {
            show(text);
            Toast.makeText(this, text, Toast.LENGTH_LONG).show();
            started = false;
        });
    }

    private record Candidate(File apk, long version) {}
}
