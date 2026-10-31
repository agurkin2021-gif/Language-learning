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
 * Bidirectional sync of all three family profiles (Spanish and English) with
 * the EXISTING public Language-learning repository, branch live-content.
 * Phone edits and remote revisions converge with tombstones and SHA retries.
 *
 * Only GitHub credentials stay private in Android Keystore. Vocabulary content
 * and mastered-state metadata ARE PUBLIC in this repository by user request.
 * Stories continue to use their independent STORY INBOX feed.
 */
public final class PrivateVocabularySync {
    private static final String API = "https://api.github.com/repos/";
    private static final String PROJECT_REPOSITORY = "agurkin2021-gif/Language-learning";
    private static final String CONTENT_BRANCH = "live-content";
    private static final String KEY_ALIAS = "SpanishLearningPrivateVocabularyV1";
    private static final String SECRET_FILE = "vocabulary_sync.enc";
    private static final String STATE_PREFS = "vocabulary_sync_state";
    private static final int PERIODIC_JOB_ID = 72131;
    private static final long MIN_SYNC_INTERVAL_MS = 15 * 60 * 1000L;
    private static final ScheduledExecutorService EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "vocabulary-two-way-sync");
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
        return PROJECT_REPOSITORY;
    }

    public static String status(Context context) {
        return context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                .getString("status", isConfigured(context) ? "Ожидается синхронизация" : "Не подключено");
    }

    public static synchronized void configure(Context context, String repository, String token) throws Exception {
        String repo = repository == null ? "" : repository.trim();
        String secret = token == null ? "" : token.trim();
        if (!PROJECT_REPOSITORY.equalsIgnoreCase(repo)
                || secret.isEmpty() || secret.length() > 500)
            throw new IllegalArgumentException("Нужен Language-learning и GitHub token с правом записи");
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

    /** Two-way, per-profile and per-language synchronization in the EXISTING repository.
     * No new repository and no public credential files are created.
     */
    private static void sync(Context context, int generation) {
        if (generation != GENERATION.get() || !isConfigured(context)) return;
        SharedPreferences state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
        try {
            Config config = readConfig(context);
            JSONObject repository = request("GET", API + PROJECT_REPOSITORY, config.token(), null, false);
            if (!PROJECT_REPOSITORY.equalsIgnoreCase(repository.optString("full_name")))
                throw new IllegalArgumentException("GitHub repository mismatch");
            // All three profiles are always synchronized, irrespective of the selected UI tab.
            for (String profile : StudyProfile.IDS) {
                for (String language : new String[]{"es", "en"}) {
                    if (generation != GENERATION.get()) return;
                    syncOne(context, config, profile, language, generation);
                }
            }
            if (generation == GENERATION.get())
                state.edit().putString("status",
                        "Двусторонняя синхронизация: Language-learning, 3 профиля").apply();
        } catch (Exception error) {
            if (generation == GENERATION.get()) {
                String message = error.getMessage() != null &&
                        error.getMessage().matches("GitHub HTTP [0-9]{3}")
                        ? error.getMessage() : error instanceof IllegalArgumentException
                        ? error.getMessage() : "Нет связи или ошибка синхронизации";
                state.edit().putString("status", "Ошибка: " + message).apply();
            }
        }
    }

    /** Optimistic SHA compare-and-swap prevents concurrent phones from overwriting changes. */
    private static void syncOne(Context context, Config config, String profile,
                                String language, int generation) throws Exception {
        String path = "content/" + profile + "/vocabulary-" + language + ".json";
        String api = API + PROJECT_REPOSITORY + "/contents/" + path;
        for(int attempt = 0; attempt < 5; attempt++) {
            if (generation != GENERATION.get()) return;
            Store local = Store.forProfile(context, profile, "en".equals(language));
            try {
                List<Store.SyncEntry> localEntries = local.vocabularyForSync();
                JSONObject current = request("GET", api + "?ref=" + CONTENT_BRANCH,
                        config.token(), null, true);
                List<Store.SyncEntry> cloudEntries;
                if(current == null) cloudEntries = new java.util.ArrayList<>();
                else {
                    if(!"file".equals(current.optString("type"))
                            || !"base64".equals(current.optString("encoding")))
                        throw new IllegalArgumentException("Invalid cloud vocabulary file");
                    byte[] bytes=Base64.decode(current.getString("content"), Base64.DEFAULT);
                    if(bytes.length > 1024*1024)
                        throw new IllegalArgumentException("Vocabulary archive exceeds 1 MB");
                    cloudEntries=VocabularyReconcile.parse(
                            new JSONObject(new String(bytes, StandardCharsets.UTF_8)),profile,language);
                }
                // Full reset on this phone must also remove older remote cards,
                // including entries not yet downloaded when the reset happened.
                localEntries.addAll(local.resetTombstonesFor(cloudEntries));
                List<Store.SyncEntry> merged = VocabularyReconcile.merge(localEntries,cloudEntries);
                String encoded=VocabularyReconcile.serialize(profile,language,merged).toString();
                boolean same = current != null && encoded.equals(new String(
                        Base64.decode(current.getString("content"),Base64.DEFAULT),StandardCharsets.UTF_8));
                if(!same) {
                    JSONObject body=new JSONObject();
                    body.put("branch",CONTENT_BRANCH);
                    body.put("message","Sync "+profile+" "+language+" complete vocabulary");
                    body.put("content",Base64.encodeToString(
                            encoded.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP));
                    if(current!=null) body.put("sha", current.getString("sha"));
                    try { request("PUT",api,config.token(),body,false); }
                    catch(HttpConflict conflict) {
                        if(attempt==4) throw conflict;
                        continue; // Read the changed remote SHA and merge again.
                    }
                }
                // Never apply a stale response over a more recent local edit.
                if(generation != GENERATION.get()) return;
                local.applySyncedVocabulary(merged);
                return;
            } finally { local.close(); }
        }
    }

    private static void uploadLanguage(Context context, Config config, String branch,
                                       String profile, String language, int generation,
                                       SharedPreferences state) throws Exception {
        if (generation != GENERATION.get()) return;
        Store local = Store.forProfile(context, profile, "en".equals(language));
        List<Store.Card> cards;
        try { cards = local.vocabulary(); }
        finally { local.close(); }
        String cacheKey = "published_" + (StudyProfile.isPapa(profile) ? "" : profile + "_") + language;
        // Papa retains the exact legacy path, other family members are scoped.
        String json = snapshot(profile, language, config.deviceId(), cards).toString();
        String digest = hexDigest(json);
        String path = StudyProfile.vocabularyPath(profile, language, config.deviceId());
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

    /** Per-profile, per-language story backup. Separate from the curated incoming story feed. */
    private static void uploadStories(Context context, Config config, String branch,
                                      String profile, String language, int generation) throws Exception {
        if (generation != GENERATION.get()) return;
        Store local = Store.forProfile(context, profile, "en".equals(language));
        List<Store.Story> stories;
        try { stories = local.stories(); } finally { local.close(); }
        JSONObject root = new JSONObject();
        root.put("schema", 1);
        root.put("profile", profile);
        root.put("language", language);
        root.put("device_id", config.deviceId());
        JSONArray entries = new JSONArray();
        for (Store.Story story : stories) {
            JSONObject entry = new JSONObject();
            entry.put("id", story.id());
            entry.put("title", story.title());
            entry.put("spanish", story.body());
            entry.put("russian", story.translation());
            entry.put("source", story.source());
            entry.put("position", story.position());
            entries.put(entry);
        }
        root.put("entries", entries);
        String json = root.toString();
        String digest = hexDigest(json);
        String path = "stories/" + profile + "/" + language + "-" + config.deviceId() + ".json";
        String apiUrl = API + config.repo() + "/contents/" + path;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (generation != GENERATION.get()) return;
            JSONObject current = request("GET", apiUrl + "?ref=" +
                    URLEncoder.encode(branch, "UTF-8"), config.token(), null, true);
            if (current != null && "base64".equals(current.optString("encoding"))) {
                byte[] remote = Base64.decode(current.optString("content"), Base64.DEFAULT);
                if (digest.equals(hexDigest(new String(remote, StandardCharsets.UTF_8)))) return;
            }
            JSONObject body = new JSONObject();
            body.put("message", "Backup " + profile + " " + language + " stories from Android");
            body.put("branch", branch);
            body.put("content", Base64.encodeToString(json.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
            if (current != null) body.put("sha", current.getString("sha"));
            try {
                request("PUT", apiUrl, config.token(), body, false);
                return;
            } catch (HttpConflict conflict) {
                if (attempt == 2) throw conflict;
            }
        }
    }

    private static String readableRepository(Context context) {
        try { return readConfig(context).repo(); }
        catch (Exception ignored) { return ""; }
    }

    /** Includes every card, every phrase and both mastery states; no 300-card cap. */
    static JSONObject snapshot(String language, String deviceId, List<Store.Card> cards) throws Exception {
        return snapshot(StudyProfile.PAPA, language, deviceId, cards);
    }

    static JSONObject snapshot(String profile, String language, String deviceId,
                               List<Store.Card> cards) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schema", 1);
        root.put("language", language);
        root.put("device_id", deviceId);
        if (!StudyProfile.isPapa(profile)) root.put("profile", StudyProfile.normalized(profile));
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
        return downloadStoryFeed(context, language, expectedRepo, StudyProfile.PAPA);
    }

    public static String downloadStoryFeed(Context context, String language,
                                           String expectedRepo, String profile)
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
        String path = StudyProfile.storyFeedPath(profile, language);
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

    /** Curated incoming cards live in a separate path from phone-generated backups. */
    public static String downloadCardFeed(Context context, String language,
                                          String expectedRepo, String profile) throws Exception {
        if (!"es".equals(language) && !"en".equals(language))
            throw new IllegalArgumentException("language");
        Config config = readConfig(context);
        if (!config.repo().equals(expectedRepo)) throw new IllegalStateException("Profile changed");
        JSONObject repository = request("GET", API + config.repo(), config.token(), null, false);
        if (!repository.optBoolean("private", false))
            throw new IllegalArgumentException("Repository must be private");
        String branch = repository.getString("default_branch");
        String path = "content/" + StudyProfile.normalized(profile) + "/cards-" + language + ".json";
        JSONObject file = request("GET", API + config.repo() + "/contents/" + path +
                "?ref=" + URLEncoder.encode(branch, "UTF-8"), config.token(), null, true);
        if (file == null) return new JSONObject().put("version", 1)
                .put("items", new JSONArray()).toString();
        if (!"file".equals(file.optString("type")) || !"base64".equals(file.optString("encoding")))
            throw new IllegalArgumentException("Invalid cards feed");
        byte[] bytes = Base64.decode(file.getString("content"), Base64.DEFAULT);
        if (bytes.length > 512 * 1024) throw new IllegalArgumentException("Cards feed too large");
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
