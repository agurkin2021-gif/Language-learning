package ru.spanish.learning;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Opt-in, upload-only snapshot of the phone's entire ES and EN dictionaries.
 * Vocabulary never enters the public content repository or the ChatGPT API.
 * Only an authenticated PRIVATE repository is an allowed destination.
 *
 * The phone stays the source of truth; stories flow separately through
 * RemoteContentSync, and neither path modifies locally saved cards.
 */
public final class PrivateVocabularySync {
    private static final String API = "https://api.github.com/repos/";
    private static final String KEY_ALIAS = "SpanishLearningPrivateVocabularyV1";
    private static final String SECRET_FILE = "vocabulary_sync.enc";
    private static final String STATE_PREFS = "vocabulary_sync_state";
    private static final int PERIODIC_JOB_ID = 72131;
    private static final long MIN_SYNC_INTERVAL_MS = 15 * 60 * 1000L;
    private static final ScheduledExecutorService EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "vocabulary-upload");
                t.setDaemon(true);
                return t;
            });
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static ScheduledFuture<?> pending;

    private record Config(String repo, String token, String deviceId) {}

    private PrivateVocabularySync() {}

    /** Credentials cannot be backed up: Android Keystore keys are device-specific. */
    private static File secretFile(Context context) {
        return new File(context.getNoBackupFilesDir(), SECRET_FILE);
    }

    public static boolean isConfigured(Context context) {
        return secretFile(context).isFile();
    }

    public static String repository(Context context) {
        if (!isConfigured(context)) return "agurkin2021-gif/Language-learning-private";
        try { return readConfig(context).repo(); }
        catch (Exception ignored) { return ""; }
    }

    public static String status(Context context) {
        return context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                .getString("status", isConfigured(context) ? "Ожидается синхронизация" : "Не подключено");
    }

    public static synchronized void configure(Context context, String repository, String token) throws Exception {
        String repo = repository == null ? "" : repository.trim();
        String secret = token == null ? "" : token.trim();
        if (!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
                || secret.isEmpty() || secret.length() > 500)
            throw new IllegalArgumentException("Укажите owner/repo и GitHub token");
        String deviceId = UUID.randomUUID().toString();
        if (isConfigured(context)) {
            try { deviceId = readConfig(context).deviceId(); }
            catch (Exception ignored) { /* A restored/invalid key needs a fresh device id. */ }
        }
        JSONObject data = new JSONObject();
        data.put("repo", repo);
        data.put("token", secret);
        data.put("device_id", deviceId);
        byte[] encrypted = encrypt(data.toString().getBytes(StandardCharsets.UTF_8));
        AtomicFile atomic = new AtomicFile(secretFile(context));
        FileOutputStream out = atomic.startWrite();
        try {
            out.write(encrypted);
            atomic.finishWrite(out);
        } catch (Exception e) {
            atomic.failWrite(out);
            throw e;
        }
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        // Persistent JobScheduler work survives app process death and reboot.
        ensureBackgroundSync(context);
        schedule(context);
    }

    public static synchronized void disable(Context context) {
        GENERATION.incrementAndGet();
        if (pending != null) pending.cancel(false);
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (jobs != null) jobs.cancel(PERIODIC_JOB_ID);
        new AtomicFile(secretFile(context)).delete();
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    /**
     * Keep a durable periodic network job in addition to immediate sync on local edits.
     * Android may defer jobs in Doze / restricted battery mode; offline changes remain
     * in SQLite and the next permitted network run exports a complete new snapshot.
     */
    static synchronized void ensureBackgroundSync(Context context) {
        if (!isConfigured(context)) return;
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (jobs == null || jobs.getPendingJob(PERIODIC_JOB_ID) != null) return;
        JobInfo job = new JobInfo.Builder(PERIODIC_JOB_ID,
                new ComponentName(context, VocabularySyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(MIN_SYNC_INTERVAL_MS)
                .build();
        jobs.schedule(job);
    }

    /** Debounced after edits and on app launch; offline retry has a durable network job. */
    public static synchronized void schedule(Context context) {
        if (!isConfigured(context)) return;
        ensureBackgroundSync(context);
        Context app = context.getApplicationContext();
        int generation = GENERATION.incrementAndGet();
        if (pending != null) pending.cancel(false);
        pending = EXECUTOR.schedule(() -> sync(app, generation), 2, TimeUnit.SECONDS);
    }

    /** Invoked by a system job, not tied to an Activity or any ChatGPT usage limit. */
    static void runScheduled(Context context, Runnable finished) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                if (isConfigured(app)) sync(app, GENERATION.get());
            } finally {
                finished.run();
            }
        });
    }

    private static void sync(Context context, int generation) {
        if (generation != GENERATION.get() || !isConfigured(context)) return;
        SharedPreferences state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
        try {
            Config config = readConfig(context);
            String repoUrl = API + config.repo();
            JSONObject repository = request("GET", repoUrl, config.token(), null, false);
            if (!repository.optBoolean("private", false))
                throw new IllegalArgumentException("Репозиторий должен быть приватным");
            String branch = repository.getString("default_branch");
            if (branch.isEmpty()) throw new IllegalArgumentException("У репозитория нет основной ветки");
            uploadLanguage(context, config, branch, "es", generation, state);
            uploadLanguage(context, config, branch, "en", generation, state);
            if (generation == GENERATION.get()) {
                state.edit().putString("status", "Словарь синхронизирован с приватным GitHub").apply();
            }
        } catch (Exception error) {
            if (generation == GENERATION.get()) {
                // Do not store exception bodies, responses, repository contents or credentials.
                String message = error instanceof IllegalArgumentException ? error.getMessage() :
                        error.getMessage() != null && error.getMessage().matches("GitHub HTTP [0-9]{3}")
                        ? error.getMessage() : "Нет соединения или ошибка синхронизации";
                state.edit().putString("status", "Ошибка: " + message).apply();
            }
        }
    }

    private static void uploadLanguage(Context context, Config config, String branch,
                                       String language, int generation, SharedPreferences state) throws Exception {
        if (generation != GENERATION.get()) return;
        Store local = "en".equals(language) ? Store.english(context) : new Store(context);
        List<Store.Card> cards;
        try { cards = local.vocabulary(); }
        finally { local.close(); }
        String cacheKey = "published_" + language;
        // Every installation uses its own device UUID. Even a new empty EN
        // dictionary is recorded, without overwriting another device's data.
        String json = snapshot(language, config.deviceId(), cards).toString();
        String digest = hexDigest(json);
        String path = "vocabulary/" + language + "-" + config.deviceId() + ".json";
        String apiUrl = API + config.repo() + "/contents/" + path;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (generation != GENERATION.get()) return;
            JSONObject current = request("GET", apiUrl + "?ref=" +
                    URLEncoder.encode(branch, "UTF-8"), config.token(), null, true);
            // Verify the actual remote contents, not only a cached local
            // receipt: restore files deleted or modified on GitHub, and
            // avoid redundant commits when the snapshot is unchanged.
            if (current != null && "file".equals(current.optString("type"))
                    && "base64".equals(current.optString("encoding"))) {
                byte[] remote = Base64.decode(current.optString("content"), Base64.DEFAULT);
                if (digest.equals(hexDigest(new String(remote, StandardCharsets.UTF_8)))) {
                    state.edit().putString(cacheKey, digest).apply();
                    return;
                }
            }
            JSONObject body = new JSONObject();
            body.put("message", "Sync " + language + " personal vocabulary from Android");
            body.put("branch", branch);
            body.put("content", Base64.encodeToString(json.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
            if (current != null) body.put("sha", current.getString("sha"));
            try {
                if (generation != GENERATION.get()) return;
                request("PUT", apiUrl, config.token(), body, false);
                state.edit().putString(cacheKey, digest).apply();
                return;
            } catch (HttpConflict conflict) {
                if (attempt == 2) throw conflict;
            }
        }
    }

    /** Includes every card, every phrase and both mastery states; no 300-card cap. */
    static JSONObject snapshot(String language, String deviceId, List<Store.Card> cards) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schema", 1);
        root.put("language", language);
        root.put("device_id", deviceId);
        JSONArray entries = new JSONArray();
        for (Store.Card card : cards) {
            JSONObject entry = new JSONObject();
            entry.put("id", card.id());
            entry.put("text", card.spanish());
            entry.put("translation", card.meaning());
            entry.put("kind", card.kind() == 1 ? "phrase" : "word");
            entry.put("category", card.category());
            entry.put("mastered", card.mastered());
            entry.put("last_result", card.lastResult());
            entry.put("review_count", card.reviewCount());
            entry.put("next_due", card.nextDue());
            entries.put(entry);
        }
        root.put("entries", entries);
        return root;
    }

    /** Account selector without exposing tokens or other credentials. */
    public static String profileRepository(Context context) {
        if (!isConfigured(context)) return "";
        try { return readConfig(context).repo(); }
        catch (Exception ignored) { return ""; }
    }

    /** Stories are read from the same PRIVATE repo that receives this phone's vocabulary. */
    public static String downloadStoryFeed(Context context, String language, String expectedRepo)
            throws Exception {
        if (!"es".equals(language) && !"en".equals(language))
            throw new IllegalArgumentException("language");
        Config config = readConfig(context);
        if (!config.repo().equals(expectedRepo))
            throw new IllegalStateException("Profile changed");
        JSONObject repository = request("GET", API + config.repo(), config.token(), null, false);
        if (!repository.optBoolean("private", false))
            throw new IllegalArgumentException("Репозиторий должен быть приватным");
        String branch = repository.getString("default_branch");
        if (branch.isEmpty()) throw new IllegalArgumentException("У репозитория нет основной ветки");
        String path = "content/stories-" + language + ".json";
        JSONObject file = request("GET", API + config.repo() + "/contents/" + path +
                "?ref=" + URLEncoder.encode(branch, "UTF-8"), config.token(), null, true);
        if (file == null) return new JSONObject().put("version", 1)
                .put("items", new JSONArray()).toString();
        if (!"file".equals(file.optString("type")) || !"base64".equals(file.optString("encoding")))
            throw new IllegalArgumentException("Неверный формат файла рассказов");
        String encoded = file.getString("content");
        if (encoded.length() > 720000) throw new IllegalArgumentException("Файл рассказов слишком большой");
        byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
        if (bytes.length > 512 * 1024) throw new IllegalArgumentException("Файл рассказов слишком большой");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String hexDigest(String text) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        return result.toString();
    }

    private static class HttpConflict extends java.io.IOException {}

    private static JSONObject request(String method, String url, String token,
                                      JSONObject body, boolean missingIsFine) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("User-Agent", "SpanishLearning-vocabulary-sync");
        try {
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            if (status == 404 && missingIsFine) return null;
            if (status == 409) throw new HttpConflict();
            if (status < 200 || status >= 300)
                throw new java.io.IOException("GitHub HTTP " + status);
            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                for (int n; (n = in.read(buffer)) != -1; ) {
                    if (out.size() + n > 1024 * 1024) throw new java.io.IOException("GitHub response too large");
                    out.write(buffer, 0, n);
                }
                return new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
            }
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] encrypt(byte[] plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        return (Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "." +
                Base64.encodeToString(cipher.doFinal(plain), Base64.NO_WRAP))
                .getBytes(StandardCharsets.US_ASCII);
    }

    private static Config readConfig(Context context) throws Exception {
        byte[] blob = Files.readAllBytes(secretFile(context).toPath());
        String data = new String(blob, StandardCharsets.US_ASCII);
        int dot = data.indexOf('.');
        if (dot < 1) throw new IllegalStateException("Invalid private settings");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(),
                new GCMParameterSpec(128, Base64.decode(data.substring(0, dot), Base64.NO_WRAP)));
        JSONObject value = new JSONObject(new String(cipher.doFinal(
                Base64.decode(data.substring(dot + 1), Base64.NO_WRAP)), StandardCharsets.UTF_8));
        return new Config(value.getString("repo"), value.getString("token"),
                value.getString("device_id"));
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        SecretKey result = (SecretKey) store.getKey(KEY_ALIAS, null);
        if (result != null) return result;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }
}
