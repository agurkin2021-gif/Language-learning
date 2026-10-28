package ru.spanish.learning;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Private per-account story delivery. Never reads the public shared feed. */
public final class PersonalStorySync {
    private static final String PREFS = "personal_story_delivery";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    public interface Callback { void done(int added, int rejected, String error); }

    public static String status(Context context) {
        if (!PrivateVocabularySync.isConfigured(context)) return "Личный канал не подключён";
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
                    String profile = PrivateVocabularySync.profileRepository(app);
                    if (profile.isEmpty()) throw new IllegalStateException("No personal repository");
                    String json = PrivateVocabularySync.downloadStoryFeed(
                            app, language, profile, store.profileId());
                    // A profile changed during network access must not inject its old stories.
                    if (!profile.equals(PrivateVocabularySync.profileRepository(app)))
                        throw new IllegalStateException("Profile changed");
                    Result result = importStories(store, new JSONObject(json), profile, language);
                    added = result.added();
                    rejected = result.rejected();
                    setStatus(app, store.profileId(), rejected == 0
                            ? "Личные рассказы проверены (" + language.toUpperCase(Locale.ROOT) + ")"
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

    record Result(int added, int rejected) {}

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
