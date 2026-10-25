package ru.spanish.learning;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RemoteContentSync {
    private static final String FEED_URL =
            "https://raw.githubusercontent.com/agurkin2021-gif/Language-learning/live-content/content/live_content.json";
    private static final int MAX_BYTES = 1024 * 1024;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public interface Callback {
        void done(int added, int rejected, String error);
    }

    public void sync(Store store, Callback callback) {
        worker.execute(() -> {
            int added = 0;
            int rejected = 0;
            String error = "";
            try {
                JSONObject root = new JSONObject(download());
                if (root.optInt("version", 1) != 1) throw new IllegalArgumentException("Неподдерживаемая версия контента");
                JSONArray items = root.optJSONArray("items");
                if (items == null) items = new JSONArray();
                if (items.length() > 500) throw new IllegalArgumentException("Слишком много элементов");
                for (int i = 0; i < items.length(); i++) {
                    try {
                        JSONObject item = items.getJSONObject(i);
                        String id = clean(item.optString("id"), 120);
                        String type = clean(item.optString("type"), 30).toLowerCase(java.util.Locale.ROOT);
                        if (id.isEmpty() || type.isEmpty()) throw new IllegalArgumentException("id/type");
                        boolean imported;
                        if ("word".equals(type) || "phrase".equals(type)) {
                            String spanish = clean(item.optString("spanish"), 500);
                            String meaning = clean(item.optString("meaning"), 1000);
                            int kind = "phrase".equals(type) ? 1 : 0;
                            int category = kind == 1 ? 0 : category(item);
                            imported = store.importRemoteCard(id, spanish, meaning, kind, category);
                        } else if ("story".equals(type)) {
                            String title = clean(item.optString("title"), 300);
                            String spanish = clean(item.optString("spanish"), 20000);
                            String russian = clean(item.optString("russian"), 20000);
                            imported = store.importRemoteStory(id, title, spanish, russian);
                        } else {
                            throw new IllegalArgumentException("type");
                        }
                        if (imported) added++;
                    } catch (Exception ignored) {
                        rejected++;
                    }
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            callback.done(added, rejected, error);
        });
    }

    private String download() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(FEED_URL).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "SpanishLearning/1");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                for (int n; (n = in.read(buffer)) != -1; ) {
                    total += n;
                    if (total > MAX_BYTES) throw new IllegalStateException("Контент слишком большой");
                    out.write(buffer, 0, n);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    private int category(JSONObject item) {
        Object raw = item.opt("category");
        if (raw instanceof Number) {
            int value = ((Number) raw).intValue();
            if (value >= 1 && value <= 3) return value;
        }
        String value = raw == null ? "" : raw.toString().trim().toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "verb", "глагол" -> 1;
            case "noun", "существительное" -> 2;
            case "adjective", "прилагательное" -> 3;
            default -> throw new IllegalArgumentException("category");
        };
    }

    private String clean(String value, int max) {
        String result = value == null ? "" : value.trim();
        if (result.length() > max) throw new IllegalArgumentException("Слишком длинный текст");
        return result;
    }

    public void close() {
        worker.shutdownNow();
    }
}
