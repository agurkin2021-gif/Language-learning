package ru.spanish.learning;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.json.JSONException;
import org.json.JSONObject;

/** Downloads only this app's signed releases. Installation never starts from a visible Activity. */
public final class AppUpdater {
    private static final String PACKAGE = "ru.spanish.learning";
    private static final String REPOSITORY = "agurkin2021-gif/Language-learning";
    private static final String BASE = "https://raw.githubusercontent.com/" + REPOSITORY
            + "/refs/heads/phone-builds/";
    private static final String PREFS = "app_updates";
    private static final String CALLBACK_SESSION = "ru.spanish.learning.UPDATE_SESSION";
    private static final int PERIODIC_JOB = 72101;
    private static final int SOON_JOB = 72102;
    private static final long RETRY_DELAY_MS = 15L * 60 * 1000;
    private static final long SESSION_TIMEOUT_MS = 30L * 60 * 1000;
    private static final long TRANSFER_LIMIT_MS = 120_000;
    private static final Object INSTALL_LOCK = new Object();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean CHECK_QUEUED = new AtomicBoolean();
    private static volatile Context application;
    private static volatile boolean foreground;
    private static int writingSession = -1;

    private AppUpdater() {}

    public static void configureSchedule(Context context) {
        Context app = remember(context);
        if (BuildConfig.DEBUG) return;
        JobScheduler scheduler = app.getSystemService(JobScheduler.class);
        if (scheduler == null || scheduler.getPendingJob(PERIODIC_JOB) != null) return;
        scheduler.schedule(new JobInfo.Builder(PERIODIC_JOB,
                new ComponentName(app, UpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(RETRY_DELAY_MS).setPersisted(true).build());
    }

    public static void setForeground(boolean value) {
        synchronized (INSTALL_LOCK) { foreground = value; }
        Context app = application;
        if (app == null || BuildConfig.DEBUG) return;
        JobScheduler scheduler = app.getSystemService(JobScheduler.class);
        if (value) {
            if (scheduler != null) scheduler.cancel(SOON_JOB);
        } else scheduleSoon(app);
    }

    public static void check(Context context) {
        Context app = remember(context);
        if (BuildConfig.DEBUG || !CHECK_QUEUED.compareAndSet(false, true)) return;
        WORKER.execute(() -> {
            try { perform(app, false, () -> false); }
            finally { CHECK_QUEUED.set(false); }
        });
    }

    public static String status(Context context) {
        if (BuildConfig.DEBUG) return "Служебная сборка: автоматическая проверка обновлений отключена.";
        return prefs(context).getString("status", "Обновления проверяются при открытии приложения.");
    }

    public static boolean hasPendingConfirmation(Context context) {
        return confirmation(context) != null;
    }

    /** Call only in response to the user's explicit request to continue an Android confirmation. */
    public static boolean continuePending(Activity activity) {
        PendingIntent pending = confirmation(activity);
        if (pending == null) return false;
        try {
            pending.send();
            return true;
        } catch (PendingIntent.CanceledException ignored) {
            synchronized (INSTALL_LOCK) {
                abandonRecordedSession(activity, prefs(activity).getInt("session_id", -1),
                        "Системный запрос обновления истёк. Обновление будет проверено позже.");
            }
            return false;
        }
    }

    static void runScheduled(Context context, AtomicBoolean stopped, Runnable finished) {
        Context app = remember(context);
        WORKER.execute(() -> {
            try {
                if (!BuildConfig.DEBUG && !foreground && !stopped.get())
                    perform(app, true, () -> stopped.get() || foreground);
            } finally { finished.run(); }
        });
    }

    private static Context remember(Context context) {
        application = context.getApplicationContext();
        return application;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void scheduleSoon(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null || foreground || BuildConfig.DEBUG) return;
        scheduler.schedule(new JobInfo.Builder(SOON_JOB, new ComponentName(context, UpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(10_000).build());
    }

    private static void setStatus(Context context, String state, String message) {
        prefs(context).edit().putString("state", state).putString("status", message).apply();
    }

    private static File directory(Context context) throws IOException {
        File directory = new File(context.getFilesDir(), "updates");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Private directory unavailable");
        return directory;
    }

    private static void perform(Context context, boolean mayInstall, BooleanSupplier cancelled) {
        try {
            cancelled(cancelled);
            if (reconcileSession(context)) return;
            SharedPreferences settings = prefs(context);
            long retryAt = settings.getLong("retry_after", 0);
            if (System.currentTimeMillis() < retryAt) return;
            setStatus(context, "checking", "Проверяется новая версия приложения…");
            JSONObject metadata = new JSONObject(new String(readMetadata(cancelled), StandardCharsets.UTF_8));
            Object number = metadata.opt("version_code");
            if (!(number instanceof Number)) throw new IOException("Invalid metadata");
            long version = ((Number) number).longValue();
            if (((Number) number).doubleValue() != (double) version
                    || !REPOSITORY.equals(metadata.optString("repository"))
                    || !PACKAGE.equals(metadata.optString("application_id"))
                    || !"phone-builds".equals(metadata.optString("channel")))
                throw new IOException("Invalid metadata");
            String expectedDigest = metadata.optString("apk_sha256").toLowerCase(Locale.ROOT);
            if (!UpdatePolicy.validDigest(expectedDigest)) throw new IOException("Invalid digest");
            long installed = version(installedPackage(context));
            if (!UpdatePolicy.newer(version, installed)) {
                setStatus(context, "current", "Установлена актуальная версия приложения.");
                return;
            }
            if (settings.getLong("blocked_version", -1) == version) {
                settings.edit().remove("blocked_version").remove("retry_after").commit();
                setStatus(context, "retrying", "Предыдущая попытка была отменена. Обновление запускается повторно.");
            }
            File apk = new File(directory(context), "update.apk");
            boolean staged = settings.getLong("staged_version", -1) == version
                    && expectedDigest.equals(settings.getString("staged_sha256", "")) && apk.isFile();
            if (staged) {
                try { verifyPackage(context, apk, version, expectedDigest); }
                catch (IOException invalidStage) {
                    settings.edit().remove("staged_version").remove("staged_sha256").commit();
                    staged = false;
                }
            }
            if (!staged) {
                setStatus(context, "downloading", "Новая версия скачивается в приложение…");
                downloadApk(context, apk, expectedDigest, cancelled);
                verifyPackage(context, apk, version, expectedDigest);
                settings.edit().putLong("staged_version", version)
                        .putString("staged_sha256", expectedDigest).commit();
            }
            cancelled(cancelled);
            setStatus(context, "staged", "Новая версия подготовлена. Обновление выполняется после выхода из приложения.");
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                setStatus(context, "permission", "Для автоматических обновлений разрешите этому приложению установку обновлений в настройках Android.");
                return;
            }
            if (mayInstall && !foreground) installVerified(context, apk, version, expectedDigest, cancelled);
            else if (!foreground) scheduleSoon(context);
        } catch (Cancelled ignored) {
            // The app became visible or Android stopped the job. Leave a completed stage intact.
        } catch (IOException | JSONException | PackageManager.NameNotFoundException | RuntimeException ignored) {
            prefs(context).edit().putLong("retry_after", System.currentTimeMillis() + RETRY_DELAY_MS).apply();
            setStatus(context, "failed", "Обновление пока не удалось проверить. Текущая версия продолжает работать; проверка повторится позже.");
        }
    }

    private static byte[] readMetadata(BooleanSupplier cancelled) throws IOException {
        HttpURLConnection connection = connect("build-info.json");
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (connection.getContentLengthLong() > UpdatePolicy.MAX_METADATA_BYTES) throw new IOException("Metadata too large");
            copy(input, output, UpdatePolicy.MAX_METADATA_BYTES, cancelled);
            return output.toByteArray();
        } finally { connection.disconnect(); }
    }

    private static void downloadApk(Context context, File destination, String digest, BooleanSupplier cancelled) throws IOException {
        File partial = new File(directory(context), "update.part");
        HttpURLConnection connection = connect("SpanishLearning.apk");
        try {
            if (connection.getContentLengthLong() > UpdatePolicy.MAX_APK_BYTES) throw new IOException("APK too large");
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(partial)) {
                copy(input, output, UpdatePolicy.MAX_APK_BYTES, cancelled);
                output.getFD().sync();
            }
            if (!digest.equals(sha256(partial))) throw new IOException("APK checksum mismatch");
            if (destination.exists() && !destination.delete()) throw new IOException("Previous stage unavailable");
            if (!partial.renameTo(destination)) throw new IOException("Stage unavailable");
        } finally {
            connection.disconnect();
            if (partial.exists()) partial.delete();
        }
    }

    private static HttpURLConnection connect(String file) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE + file).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("User-Agent", "SpanishLearning-Android-Updater");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("Update endpoint unavailable");
            return connection;
        } catch (IOException failure) {
            connection.disconnect();
            throw failure;
        }
    }

    private static void copy(InputStream input, OutputStream output, long limit, BooleanSupplier stopped) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0, deadline = SystemClock.elapsedRealtime() + TRANSFER_LIMIT_MS;
        while (true) {
            cancelled(stopped);
            if (SystemClock.elapsedRealtime() > deadline) throw new IOException("Transfer timed out");
            int count = input.read(buffer);
            if (count == -1) break;
            total += count;
            if (total > limit) throw new IOException("Transfer too large");
            output.write(buffer, 0, count);
        }
        if (total == 0) throw new IOException("Empty transfer");
    }

    private static String sha256(File file) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return hex(digest.digest());
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    @SuppressWarnings("deprecation")
    private static int signatureFlags() {
        return Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
    }

    private static PackageInfo installedPackage(Context context) throws PackageManager.NameNotFoundException {
        return context.getPackageManager().getPackageInfo(PACKAGE, signatureFlags());
    }

    @SuppressWarnings("deprecation")
    private static long version(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    @SuppressWarnings("deprecation")
    private static List<String> signers(PackageInfo info) {
        Signature[] signatures = Build.VERSION.SDK_INT >= 28
                ? info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners()
                : info.signatures;
        List<String> result = new ArrayList<>();
        if (signatures != null) for (Signature signature : signatures)
            result.add(hex(digest().digest(signature.toByteArray())));
        return result;
    }

    private static void verifyPackage(Context context, File apk, long expectedVersion, String expectedDigest) throws IOException {
        if (!apk.getCanonicalPath().startsWith(directory(context).getCanonicalPath() + File.separator)
                || !apk.isFile() || apk.length() == 0 || apk.length() > UpdatePolicy.MAX_APK_BYTES
                || !UpdatePolicy.validDigest(expectedDigest)
                || !expectedDigest.equalsIgnoreCase(sha256(apk)))
            throw new IOException("Unverified APK file");
        PackageInfo candidate = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), signatureFlags());
        if (candidate == null) throw new IOException("Invalid APK");
        try {
            PackageInfo installed = installedPackage(context);
            UpdatePolicy.validatePackage(PACKAGE, candidate.packageName, expectedVersion, version(candidate),
                    version(installed), signers(candidate), signers(installed));
        } catch (PackageManager.NameNotFoundException | IllegalArgumentException failure) {
            throw new IOException("Unverified APK identity");
        }
    }

    /** Package-private so the same production checks can be exercised by an on-device upgrade test. */
    static boolean installVerified(Context context, File apk, long expectedVersion, String expectedDigest) throws IOException {
        return installVerified(remember(context), apk, expectedVersion, expectedDigest, () -> false);
    }

    private static boolean installVerified(Context context, File apk, long expectedVersion,
                                           String expectedDigest, BooleanSupplier stopped) throws IOException {
        verifyPackage(context, apk, expectedVersion, expectedDigest);
        cancelled(stopped);
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        int sessionId;
        synchronized (INSTALL_LOCK) {
            if (foreground || !context.getPackageManager().canRequestPackageInstalls() || reconcileSession(context)) return false;
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(PACKAGE);
            params.setSize(apk.length());
            if (Build.VERSION.SDK_INT >= 31)
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            sessionId = installer.createSession(params);
            writingSession = sessionId;
            if (!prefs(context).edit().putInt("session_id", sessionId)
                    .putLong("last_attempt_version", expectedVersion)
                    .putLong("session_started", System.currentTimeMillis())
                    .putString("state", "installing")
                    .putString("status", "Android устанавливает подготовленное обновление.").commit()) {
                installer.abandonSession(sessionId);
                writingSession = -1;
                throw new IOException("Could not record install session");
            }
        }
        boolean committed = false;
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try (InputStream input = new FileInputStream(apk); OutputStream output = session.openWrite("base.apk", 0, apk.length())) {
                copy(input, output, UpdatePolicy.MAX_APK_BYTES, () -> stopped.getAsBoolean() || foreground);
                session.fsync(output);
            }
            synchronized (INSTALL_LOCK) {
                cancelled(stopped);
                if (foreground) throw new Cancelled();
                Intent result = new Intent(context, UpdateReceiver.class)
                        .setAction(PACKAGE + ".UPDATE_RESULT." + sessionId)
                        .putExtra(CALLBACK_SESSION, sessionId);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
                PendingIntent callback = PendingIntent.getBroadcast(context, sessionId, result, flags);
                session.commit(callback.getIntentSender());
                committed = true;
                writingSession = -1;
            }
            return true;
        } finally {
            if (!committed) {
                try { installer.abandonSession(sessionId); } catch (RuntimeException ignored) {}
                synchronized (INSTALL_LOCK) {
                    if (writingSession == sessionId) writingSession = -1;
                    if (prefs(context).getInt("session_id", -1) == sessionId)
                        prefs(context).edit().remove("session_id").remove("session_started")
                                .putString("state", "staged")
                                .putString("status", "Обновление подготовлено и ждёт выхода из приложения.").commit();
                }
            }
        }
    }

    /** Returns true while a committed session or a genuine Android confirmation remains outstanding. */
    private static boolean reconcileSession(Context context) {
        synchronized (INSTALL_LOCK) {
            SharedPreferences settings = prefs(context);
            int sessionId = settings.getInt("session_id", -1);
            PackageInstaller installer = context.getPackageManager().getPackageInstaller();
            if (sessionId < 0) {
                // Recover the small process-death window between createSession and saving its ID.
                for (PackageInstaller.SessionInfo orphan : installer.getMySessions()) {
                    if (!PACKAGE.equals(orphan.getAppPackageName())) continue;
                    if (!orphan.isSealed()) {
                        try { installer.abandonSession(orphan.getSessionId()); }
                        catch (RuntimeException ignored) {
                            settings.edit().putInt("session_id", orphan.getSessionId())
                                    .putLong("session_started", System.currentTimeMillis())
                                    .putString("state", "failed")
                                    .putString("status", "Android ещё не освободил предыдущую сессию обновления.").commit();
                            return true;
                        }
                    } else {
                        settings.edit().putInt("session_id", orphan.getSessionId())
                                .putLong("session_started", System.currentTimeMillis())
                                .putLong("last_attempt_version", settings.getLong("staged_version", -1))
                                .putString("state", "installing")
                                .putString("status", "Проверяется результат предыдущего обновления.").commit();
                        return true;
                    }
                }
                return false;
            }
            PackageInstaller.SessionInfo session = installer.getSessionInfo(sessionId);
            if (session != null) {
                if (sessionId == writingSession) return true;
                boolean waiting = "pending".equals(settings.getString("state", ""));
                long elapsed = System.currentTimeMillis() - settings.getLong("session_started", 0);
                if (!session.isSealed()) {
                    abandonRecordedSession(context, sessionId,
                            "Подготовка обновления была прервана. Проверка повторится позже.");
                } else if (waiting && confirmation(context) == null) {
                    abandonRecordedSession(context, sessionId,
                            "Системный запрос обновления истёк. Проверка повторится позже.");
                } else if (!waiting && (elapsed < 0 || elapsed > SESSION_TIMEOUT_MS)) {
                    abandonRecordedSession(context, sessionId,
                            "Android не завершил обновление вовремя. Проверка повторится позже.");
                }
                return true;
            }
            cancelConfirmation(context);
            long attempted = settings.getLong("last_attempt_version", -1);
            boolean updated = false;
            try { updated = attempted > 0 && version(installedPackage(context)) >= attempted; }
            catch (PackageManager.NameNotFoundException ignored) {}
            SharedPreferences.Editor edit = settings.edit().remove("session_id").remove("session_started")
                    .remove("pending_identity").remove("pending_request_code");
            if (updated) edit.putString("state", "updated").putString("status", "Приложение обновлено автоматически.")
                    .putInt("last_result", PackageInstaller.STATUS_SUCCESS).putLong("last_installed_version", attempted);
            else edit.putLong("retry_after", System.currentTimeMillis() + RETRY_DELAY_MS)
                    .putString("state", "failed").putString("status", "Обновление не завершено. Следующая проверка будет позже.");
            edit.commit();
            return !updated;
        }
    }

    static void handleInstallResult(Context context, Intent result) {
        if (result == null) return;
        int sessionId = result.getIntExtra(CALLBACK_SESSION, -1);
        if (sessionId < 0) return;
        synchronized (INSTALL_LOCK) {
            SharedPreferences settings = prefs(context);
            if (settings.getInt("session_id", -1) != sessionId) return;
            int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            settings.edit().putInt("last_result", status).commit();
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirmation;
                if (Build.VERSION.SDK_INT >= 33) confirmation = result.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
                else {
                    @SuppressWarnings("deprecation") Intent legacy = result.getParcelableExtra(Intent.EXTRA_INTENT);
                    confirmation = legacy;
                }
                if (confirmation != null) {
                    cancelConfirmation(context);
                    PendingIntent.getActivity(context, sessionId, confirmation,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    Intent identity = new Intent(confirmation).replaceExtras((Bundle) null);
                    settings.edit().putString("pending_identity", identity.toUri(Intent.URI_INTENT_SCHEME))
                            .putInt("pending_request_code", sessionId).putString("state", "pending")
                            .putString("status", "Android требует подтверждения обновления. Откройте системный запрос в разделе обновлений.").commit();
                } else {
                    abandonRecordedSession(context, sessionId,
                            "Android не предоставил запрос подтверждения. Проверка повторится позже.");
                }
                return;
            }
            cancelConfirmation(context);
            long attempted = settings.getLong("last_attempt_version", -1);
            SharedPreferences.Editor edit = settings.edit().remove("session_id").remove("session_started")
                    .remove("pending_identity").remove("pending_request_code");
            if (status == PackageInstaller.STATUS_SUCCESS) {
                edit.putString("state", "updated").putString("status", "Приложение обновлено автоматически.")
                        .putLong("last_installed_version", attempted).remove("retry_after")
                        .remove("blocked_version").remove("staged_version").remove("staged_sha256");
            } else if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
                edit.remove("blocked_version")
                        .putLong("retry_after", System.currentTimeMillis() + RETRY_DELAY_MS)
                        .putString("state", "failed")
                        .putString("status", "Обновление было отменено. Приложение повторит попытку позже.");
            } else {
                edit.putLong("retry_after", System.currentTimeMillis() + RETRY_DELAY_MS)
                        .putString("state", "failed").putString("status", "Android не установил обновление. Текущая версия продолжает работать.");
            }
            edit.commit();
        }
    }

    private static PendingIntent confirmation(Context context) {
        SharedPreferences settings = prefs(context);
        String identity = settings.getString("pending_identity", "");
        int request = settings.getInt("pending_request_code", -1);
        if (identity.isEmpty() || request < 0) return null;
        try {
            Intent intent = Intent.parseUri(identity, Intent.URI_INTENT_SCHEME);
            return PendingIntent.getActivity(context, request, intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        } catch (Exception ignored) { return null; }
    }

    private static void cancelConfirmation(Context context) {
        PendingIntent pending = confirmation(context);
        if (pending != null) pending.cancel();
    }

    /** Clear our record before abandoning so our own cancellation callback cannot look like a user refusal. */
    private static void abandonRecordedSession(Context context, int sessionId, String message) {
        cancelConfirmation(context);
        prefs(context).edit().remove("session_id").remove("session_started")
                .remove("pending_identity").remove("pending_request_code")
                .putLong("retry_after", System.currentTimeMillis() + RETRY_DELAY_MS)
                .putString("state", "failed").putString("status", message).commit();
        if (sessionId >= 0) {
            try { context.getPackageManager().getPackageInstaller().abandonSession(sessionId); }
            catch (RuntimeException ignored) {}
        }
    }

    private static void cancelled(BooleanSupplier stopped) throws Cancelled {
        if (stopped.getAsBoolean()) throw new Cancelled();
    }

    private static final class Cancelled extends IOException {}
}
