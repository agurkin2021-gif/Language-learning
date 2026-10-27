package ru.spanish.learning;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

/**
 * A per-installation, per-language pointer to the user's EXISTING
 * authenticated ChatGPT conversation. Never stores ChatGPT cookies or tokens.
 *
 * The official web UI owns the conversation history and any project context.
 * Android has no supported API for reading, injecting or sending its messages.
 */
public final class PersistentChatContext {
    private static final String PREFS = "official_chatgpt_conversations";
    private PersistentChatContext() {}

    public static String conversationUrl(Context context, String language) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(prefKey(language), "");
    }

    public static void link(Context context, String language, String rawUrl) {
        String url = validateConversationUrl(rawUrl);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(prefKey(language), url).apply();
    }

    public static void unlink(Context context, String language) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(prefKey(language)).apply();
    }

    private static String prefKey(String language) {
        if (!"es".equals(language) && !"en".equals(language))
            throw new IllegalArgumentException("Недопустимый язык");
        return "conversation_" + language;
    }

    static String validateConversationUrl(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > 1024)
            throw new IllegalArgumentException("Вставьте ссылку на существующий диалог ChatGPT");
        Uri uri = Uri.parse(trimmed);
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) ||
                !("chatgpt.com".equalsIgnoreCase(host) || "www.chatgpt.com".equalsIgnoreCase(host)) ||
                uri.getUserInfo() != null || uri.getPort() != -1 ||
                uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Разрешены только прямые ссылки https://chatgpt.com/c/...");
        String path = uri.getPath();
        // Both regular conversations and conversations belonging to a Project.
        if (path == null || !(path.matches("/c/[A-Za-z0-9-]+/?") ||
                path.matches("/g/[A-Za-z0-9_-]+/c/[A-Za-z0-9-]+/?")))
            throw new IllegalArgumentException("Нужна ссылка на сам диалог, а не на пустой чат или публичную копию");
        return "https://chatgpt.com" + path;
    }

    /**
     * Short message for a chat whose history/instructions were already set up.
     * No giant vocabulary dump per request. This deliberately does not claim
     * that ChatGPT sees live vocabulary until a connector is configured.
     */
    public static String followUp(String request, String language, String repo, String deviceId) {
        if (request == null || request.trim().isEmpty())
            throw new IllegalArgumentException("Напишите задание");
        StringBuilder out = new StringBuilder();
        out.append("Продолжаем ЭТОТ диалог. Учитывай его историю, договорённости и инструкции проекта.\n");
        out.append("Изучаемый язык: ")
                .append("en".equals(language) ? "английский" : "аргентинский испанский, voseo")
                .append(". Отвечай мне по-русски.\n");
        if (repo != null && !repo.isEmpty() && deviceId != null && !deviceId.isEmpty()) {
            out.append("Если GitHub подключён к ЭТОМУ аккаунту ChatGPT и у тебя есть доступ к PRIVATE-репозиторию ")
                    .append(repo).append(", прочитай актуальный словарь vocabulary/")
                    .append(language).append("-").append(deviceId).append(".json перед составлением рассказа. ")
                    .append("Если доступа нет, прямо сообщи, не утверждай, что прочитал словарь.\n");
        } else {
            out.append("Не утверждай, что видишь актуальные слова на телефоне: подключение словаря не подтверждено.\n");
        }
        out.append("Моя задача: ").append(request.trim()).append("\n");
        out.append("Если нужен рассказ для моей библиотеки, ответь только JSON без markdown: ")
                .append("{\"format\":\"study_story_v1\",\"language\":\"").append(language)
                .append("\",\"title\":\"Название\",\"body\":\"Рассказ\",\"translation\":\"Русский перевод\"}. ")
                .append("Число предложений оригинала и перевода должно совпадать. ")
                .append("Не утверждай, что сохранял что-либо в Android: для этого есть кнопка импорта.");
        return out.toString();
    }
}
