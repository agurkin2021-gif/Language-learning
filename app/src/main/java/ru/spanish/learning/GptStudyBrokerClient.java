package ru.spanish.learning;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * User-controlled intermediary for permitted OpenAI API calls, not the
 * ChatGPT consumer website or Subscription Sharing. The OpenAI API key stays
 * exclusively on the intermediary server, never in the APK.
 */
public final class GptStudyBrokerClient {
    private static final String PREF = "gpt_study_broker";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public interface Listener {
        void progress(String stage);
        void completed(JSONObject result, String error);
    }

    public static boolean configured(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return !pref.getString("base", "").isBlank()
                && !pref.getString("token", "").isBlank();
    }

    public static String baseUrl(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString("base", "");
    }

    public static void configure(Context context, String url, String bearer) {
        String base = validateBaseUrl(url);
        if (bearer == null || bearer.length() < 20 || bearer.length() > 200
                || !bearer.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Нужен токен посредника (не ключ OpenAI API)");
        // App sandbox. Server API key is never stored here or embedded in APK.
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString("base", base).putString("token", bearer).apply();
    }

    public static void disconnect(Context context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply();
    }

    static String validateBaseUrl(String raw) {
        String url = raw == null ? "" : raw.trim();
        if (url.length() > 300) throw new IllegalArgumentException("Адрес слишком длинный");
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || (uri.getPath() != null &&
                    !uri.getPath().isEmpty() && !"/".equals(uri.getPath())))
                throw new IllegalArgumentException("Нужен HTTPS-адрес посредника без пути");
            return "https://" + uri.getRawAuthority().toLowerCase(java.util.Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Неверный HTTPS-адрес посредника", e);
        }
    }

    public void request(Context context, Store store, String language,
                        String kind, String userTask, Listener listener) {
        if (!configured(context)) {
            listener.completed(null, "Сначала настройте посредника ChatGPT");
            return;
        }
        final String url = baseUrl(context);
        final String bearer = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString("token", "");
        worker.execute(() -> {
            try {
                listener.progress("Передаю словарь и задание посреднику ChatGPT…");
                JSONObject payload = makeRequest(store.vocabulary(), language, kind, userTask);
                JSONObject answer = exchange(url, bearer, "POST", "/v1/jobs", payload);
                String id = answer.getString("job_id");
                if (!id.matches("[A-Za-z0-9_-]{20,60}"))
                    throw new IOException("Посредник вернул неверный ID");
                long deadline = android.os.SystemClock.elapsedRealtime() + 8 * 60_000L;
                while (android.os.SystemClock.elapsedRealtime() < deadline
                        && !Thread.currentThread().isInterrupted()) {
                    Thread.sleep(2000);
                    JSONObject job = exchange(url, bearer, "GET", "/v1/jobs/" + id, null);
                    String state = job.optString("status");
                    if ("done".equals(state)) {
                        JSONObject result = job.getJSONObject("result");
                        checkAudits(result);
                        if (!language.equals(result.optString("language"))
                                || !kind.equals(result.optString("kind")))
                            throw new IOException("Ответ относится к другому языку или типу");
                        listener.completed(result, "");
                        return;
                    }
                    if ("error".equals(state))
                        throw new IOException(job.optString("error", "Не прошёл аудит"));
                    if (!"running".equals(state))
                        throw new IOException("Неизвестный статус посредника");
                    listener.progress(job.optString("stage", "ChatGPT обрабатывает запрос…"));
                }
                throw new IOException("Время ожидания посредника истекло");
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                listener.completed(null, "Запрос остановлен");
            } catch (Exception e) {
                listener.completed(null, e.getMessage() == null
                        ? "Не удалось получить ответ ChatGPT" : e.getMessage());
            }
        });
    }

    static JSONObject makeRequest(List<Store.Card> vocabulary, String language,
                                  String kind, String task) throws Exception {
        if (!"es".equals(language) && !"en".equals(language))
            throw new IllegalArgumentException("Неправильный язык");
        if (!"story".equals(kind) && !"word".equals(kind))
            throw new IllegalArgumentException("Неправильная команда");
        ArrayList<Store.Card> sorted = new ArrayList<>(vocabulary);
        sorted.sort(Comparator.comparing((Store.Card c) -> !c.mastered())
                .thenComparing(c -> c.kind() != 1));
        JSONArray cards = new JSONArray();
        for (int i = 0; i < Math.min(sorted.size(), 180); i++) {
            Store.Card card = sorted.get(i);
            cards.put(new JSONObject().put("term", card.spanish())
                    .put("meaning", card.meaning())
                    .put("mastered", card.mastered())
                    .put("phrase", card.kind() == 1));
        }
        String text = task == null ? "" : task.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("Введите задачу или слово");
        JSONObject request = new JSONObject().put("kind", kind)
                .put("language", language).put("task", text).put("cards", cards);
        if ("word".equals(kind)) request.put("word", text);
        return request;
    }

    static void checkAudits(JSONObject result) throws Exception {
        JSONArray audits = result.getJSONArray("audits");
        if (audits.length() != 3)
            throw new IOException("Нет трёх независимых проверок перевода");
        for (int i = 0; i < 3; i++) {
            JSONObject item = audits.getJSONObject(i);
            if (item.optInt("stage", 0) != i + 1
                    || !item.optBoolean("passed", false))
                throw new IOException("Аудит перевода " + (i + 1) + "/3 не пройден");
        }
    }

    static RegularChatGptBridge.StoryDraft story(JSONObject result) throws Exception {
        checkAudits(result);
        JSONObject json = new JSONObject()
                .put("format", "study_story_v1")
                .put("language", result.getString("language"))
                .put("title", result.getString("title"))
                .put("body", result.getString("body"))
                .put("translation", result.getString("translation"));
        return RegularChatGptBridge.parseStory(json.toString());
    }

    private static JSONObject exchange(String base, String token, String method,
                                       String path, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(35000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json");
        connection.setUseCaches(false);
        try {
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                if (bytes.length > 115000) throw new IOException("Словарь превышает ограничение");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = connection.getOutputStream()) { os.write(bytes); }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) throw new IOException("Пустой ответ посредника HTTP " + status);
            String text;
            try (InputStream input = stream) {
                text = new String(input.readNBytes(150000), StandardCharsets.UTF_8);
            }
            JSONObject response = new JSONObject(text);
            if (status >= 400)
                throw new IOException(response.optString("error", "Ошибка HTTP " + status));
            return response;
        } finally {
            connection.disconnect();
        }
    }

    public void close() {
        worker.shutdownNow();
    }
}
