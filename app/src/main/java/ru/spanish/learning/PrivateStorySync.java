package ru.spanish.learning;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Authenticated personal inbox: private GitHub repo -> this installation only.
 * Other devices and the legacy public feed are never read in personal mode.
 * Does not need ChatGPT credentials, a paid ChatGPT subscription or an APK update.
 */
public final class PrivateStorySync {
    private static final String API = "https://api.github.com/repos/";
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface Callback {
        void done(int added, int rejected, String error);
    }

    public void sync(Context context, Store store, String language, Callback callback) {
        executor.execute(() -> {
            int added = 0, rejected = 0;
            String error = "";
            try {
                if (store.sharedContentDisabled()) {
                    callback.done(0, 0, "");
                    return;
                }
                PrivateVocabularySync.StorySource source = PrivateVocabularySync.storySource(context);
                if (source == null) {
                    callback.done(0, 0, "Личный GitHub не подключён");
                    return;
                }
                JSONObject repo = request(API + source.repo(), source.token(), false);
                if (!repo.optBoolean("private", false))
                    throw new IllegalArgumentException("Репозиторий должен быть приватным");
                String branch = repo.getString("default_branch");
                String path = inboxPath(source.deviceId(), language);
                JSONObject response = request(API + source.repo() + "/contents/" + path
                        + "?ref=" + URLEncoder.encode(branch, "UTF-8"), source.token(), true);
                if (response == null) {
                    callback.done(0, 0, "");
                    return; // No stories published yet is normal.
                }
                if (!"file".equals(response.optString("type")))
                    throw new IllegalArgumentException("Неверный формат файла рассказов");
                String encoded = response.getString("content").replaceAll("\\s", "");
                if (encoded.length() > MAX_RESPONSE_BYTES)
                    throw new IllegalArgumentException("Файл рассказов слишком большой");
                byte[] decoded = Base64.getDecoder().decode(encoded);
                if (decoded.length > 1024 * 1024)
                    throw new IllegalArgumentException("Файл рассказов слишком большой");
                JSONObject root = new JSONObject(new String(decoded, StandardCharsets.UTF_8));
                if (root.optInt("version", -1) != 1)
                    throw new IllegalArgumentException("Неподдерживаемая версия рассказов");
                JSONArray stories = root.getJSONArray("stories");
                if (stories.length() > 500)
                    throw new IllegalArgumentException("Слишком много рассказов");
                for (int i = 0; i < stories.length(); ++i) {
                    try {
                        JSONObject item = stories.getJSONObject(i);
                        String id = item.getString("id").trim();
                        if (!id.matches("[A-Za-z0-9_.-]{1,100}"))
                            throw new IllegalArgumentException("Некорректный ID рассказа");
                        String title = clean(item.getString("title"), 300);
                        String text = clean(item.getString("spanish"), 20000);
                        String russian = clean(item.getString("russian"), 20000);
                        if (title.isEmpty() || text.isEmpty() || russian.isEmpty())
                            throw new IllegalArgumentException("Пустой рассказ");
                        if (store.importRemoteStory(externalId(source.repo(), source.deviceId(), language, id),
                                title, text, russian)) ++added;
                    } catch (Exception invalidStory) {
                        ++rejected; // Invalid individual item cannot block good ones.
                    }
                }
            } catch (Exception e) {
                // Do not expose network response bodies or any authorization data.
                if (e instanceof IllegalArgumentException) error = e.getMessage();
                else if (e instanceof GitHubHttpException) error = "GitHub HTTP " + ((GitHubHttpException) e).status;
                else error = "Нет соединения или ошибка получения рассказов";
            }
            callback.done(added, rejected, error);
        });
    }

    static String inboxPath(String deviceId, String language) {
        if (!deviceId.matches("[a-fA-F0-9-]{36}") || (!"en".equals(language) && !"es".equals(language)))
            throw new IllegalArgumentException("Неверный профиль или язык");
        return "stories/" + deviceId + "/" + language + ".json";
    }

    static String externalId(String repo, String deviceId, String language, String storyId) {
        return "personal:" + repo.toLowerCase(java.util.Locale.ROOT) + ":" +
                deviceId + ":" + language + ":" + storyId;
    }

    private static String clean(String text, int max) {
        String result = text.trim();
        if (result.length() > max) throw new IllegalArgumentException("Слишком длинный текст");
        return result;
    }

    private static class GitHubHttpException extends Exception {
        final int status;
        GitHubHttpException(int status) { this.status = status; }
    }

    private static JSONObject request(String url, String token, boolean allowMissing) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("User-Agent", "SpanishLearning-private-stories");
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setUseCaches(false);
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(15000);
        try {
            int status = connection.getResponseCode();
            if (allowMissing && status == 404) return null;
            if (status < 200 || status >= 300) throw new GitHubHttpException(status);
            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[8192];
                for (int n; (n = in.read(chunk)) != -1;) {
                    if (buffer.size() + n > MAX_RESPONSE_BYTES)
                        throw new IllegalArgumentException("Ответ GitHub слишком большой");
                    buffer.write(chunk, 0, n);
                }
                return new JSONObject(buffer.toString(StandardCharsets.UTF_8.name()));
            }
        } finally {
            connection.disconnect();
        }
    }

    public void close() { executor.shutdownNow(); }
}
