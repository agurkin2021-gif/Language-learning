package ru.spanish.learning;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Public, profile-isolated story delivery from ONE canonical feed. */
public final class PersonalStorySync {
    private static final String PREFS = "personal_story_delivery";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    public interface Callback { void done(int added, int rejected, String error); }

    public static String status(Context context) {
        
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(statusKey(StudyProfile.active(context)), "Ожидается первая проверка");
    }

    public void sync(Context context, Store store, String language, Callback callback) {
        Context app = context.getApplicationContext();
        worker.execute(() -> {
            int added = 0, rejected = 0;
            String error = "";
            try {
                if (store.personalStoriesDisabled()) {
                    setStatus(app, store.profileId(), "Рассказы отключены после сброса базы");
                } else {
                    String profile = store.profileId();
                    String cardsJson = download("content/" + profile + "/cards-" + language + ".json");
                    Result cards = importCards(store, new JSONObject(cardsJson), "public", language);
                    added += cards.added();
                    rejected += cards.rejected();
                    // Only one canonical story input exists for every profile and language.
                    String json = download("content/stories.json");
                    Result result = importStories(store, new JSONObject(json), "public", language);
                    added += result.added();
                    rejected += result.rejected();
                    setStatus(app, store.profileId(), rejected == 0
                            ? "Рассказы проверены (" + language.toUpperCase(Locale.ROOT) + ")"
                            : "Добавлено: " + added + "; отклонено: " + rejected);
                }
            } catch (Exception failure) {
                error = failure.getMessage() != null
                        && failure.getMessage().matches("GitHub HTTP [0-9]{3}")
                        ? failure.getMessage() : "Не удалось получить личные рассказы";
                setStatus(app, store.profileId(), error);
            }
            callback.done(added, rejected, error);
        });
    }

    private static String download(String path) throws Exception {
        String url = "https://raw.githubusercontent.com/agurkin2021-gif/Language-learning/live-content/" + path;
        HttpURLConnection connection = (HttpURLConnection) new URL(url + "?t=" + System.currentTimeMillis()).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(12000);
        connection.setUseCaches(false);
        try {
            int status = connection.getResponseCode();
            if (status == 404) return new JSONObject().put("version", 1).put("items", new JSONArray()).toString();
            if (status != 200) throw new IllegalStateException("HTTP " + status);
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                for (int n; (n = input.read(buffer)) != -1;) {
                    if (output.size() + n > 1024 * 1024) throw new IllegalArgumentException("Feed too large");
                    output.write(buffer, 0, n);
                }
                return output.toString(StandardCharsets.UTF_8.name());
            }
        } finally { connection.disconnect(); }
    }

    record Result(int added, int rejected) {}

    static Result importCards(Store store, JSONObject root, String repository, String language)
            throws Exception {
        if (root.optInt("version", 1) != 1) throw new IllegalArgumentException("Card feed version");
        JSONArray items = root.optJSONArray("items");
        if (items == null) items = new JSONArray();
        if (items.length() > 5000) throw new IllegalArgumentException("Too many cards");
        int added = 0, rejected = 0;
        String prefix = "private-card:" + repository + ":" + store.profileId() + ":" + language + ":";
        for (int i = 0; i < items.length(); i++) {
            try {
                JSONObject item = items.getJSONObject(i);
                String type = item.getString("type");
                if (!"word".equals(type) && !"phrase".equals(type))
                    throw new IllegalArgumentException("type");
                String id = clean(item.getString("id"), 120);
                String word = clean(item.getString("spanish"), 500);
                String meaning = clean(item.getString("meaning"), 1000);
                if (id.isEmpty() || word.isEmpty() || meaning.isEmpty())
                    throw new IllegalArgumentException("empty card");
                int kind = "phrase".equals(type) ? 1 : 0;
                int category = kind == 1 ? 0 : item.getInt("category");
                if (store.importPrivateCard(prefix + id, word, meaning, kind, category)) added++;
            } catch (Exception bad) { rejected++; }
        }
        return new Result(added, rejected);
    }



    static Result importStories(Store store, JSONObject root, String repository, String language)
            throws Exception {
        if (!"es".equals(language) && !"en".equals(language))
            throw new IllegalArgumentException("language");
        if (root.optInt("version", 1) != 1)
            throw new IllegalArgumentException("Неподдерживаемый формат");
        JSONArray items = root.optJSONArray("items");
        if (items == null) items = new JSONArray();
        if (items.length() > 500) throw new IllegalArgumentException("Слишком много рассказов");
        int added = 0, rejected = 0;
        String prefix = "personal:" + repository + ":"
                + (StudyProfile.isPapa(store.profileId()) ? "" : store.profileId() + ":")
                + language + ":";
        for (int i = 0; i < items.length(); i++) {
            try {
                JSONObject item = items.getJSONObject(i);
                if (!"story".equals(item.optString("type"))) throw new IllegalArgumentException("type");
                // Unknown profiles must never default to Papa.
                String itemProfile = item.optString("profile");
                String itemLanguage = item.optString("language");
                if (!(StudyProfile.PAPA.equals(itemProfile) || StudyProfile.MAMA.equals(itemProfile)
                        || StudyProfile.DIMA.equals(itemProfile))
                        || !("es".equals(itemLanguage) || "en".equals(itemLanguage)))
                    throw new IllegalArgumentException("profile/language");
                if (!store.profileId().equals(itemProfile) || !language.equals(itemLanguage))
                    continue;
                String id = clean(item.optString("id"), 120);
                String title = clean(item.optString("title"), 300);
                String text = clean(item.optString("spanish"), 20000);
                String russian = clean(item.optString("russian"), 20000);
                if (id.isEmpty() || title.isEmpty() || text.isEmpty())
                    throw new IllegalArgumentException("missing fields");
                if (store.importPrivateStory(prefix + id, title, text, russian)) added++;
            } catch (Exception invalid) {
                rejected++;
            }
        }
        return new Result(added, rejected);
    }

    private static String clean(String text, int max) {
        String value = text == null ? "" : text.trim();
        if (value.length() > max) throw new IllegalArgumentException("Too long");
        return value;
    }

    private static String statusKey(String profile) {
        return StudyProfile.isPapa(profile) ? "status" : "status_" + profile;
    }

    private static void setStatus(Context context, String profile, String status) {
        SharedPreferences pref = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        pref.edit().putString(statusKey(profile), status).apply();
    }

    public void close() { worker.shutdownNow(); }
}
