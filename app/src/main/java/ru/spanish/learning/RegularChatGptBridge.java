package ru.spanish.learning;

import org.json.JSONObject;
import java.util.Locale;

/**
 * Plain-text bridge to the OFFICIAL ChatGPT interface, not the OpenAI API.
 * The Android app builds an accurate snapshot; it cannot submit browser
 * messages on behalf of the user or read ChatGPT's authenticated reply.
 */
public final class RegularChatGptBridge {
    public record StoryDraft(String language, String title, String body, String translation) {}
    private RegularChatGptBridge() {}

    public static String prompt(Store store, String userRequest) {
        if (store == null || userRequest == null || userRequest.trim().isEmpty())
            throw new IllegalArgumentException("Напишите задание для ChatGPT");
        String lang = store.isEnglish() ? "en" : "es";
        StringBuilder result = new StringBuilder();
        result.append("Контекст из моего приложения изучения языка.\n");
        result.append("Я изучаю ").append(store.isEnglish() ? "английский" : "аргентинский испанский (es-AR, voseo: vos, tenés)")
                .append(". Отвечай мне по-русски. Не смешивай английский и испанский.\n");
        result.append("В приложении мой собственный словарь; метка 'знаю' означает освоенное, 'изучаю' — ещё не освоенное.\n");
        result.append("Используй максимально возможное число освоенных выражений, не выдумывай отсутствующие слова и не добавляй карточки сам.\n\n");
        result.append("МОЙ СЛОВАРЬ, актуальный снимок в момент отправки:\n");
        int count = 0;
        for (Store.Card card : store.vocabulary()) {
            if (++count > 12000) throw new IllegalArgumentException("Словарь слишком большой");
            result.append(card.mastered() ? "[знаю]" : "[изучаю]")
                    .append(card.kind() == 1 ? "[выражение] " : "[слово] ")
                    .append(oneLine(card.spanish())).append(" = ")
                    .append(oneLine(card.meaning())).append('\n');
        }
        result.append("\nЗАДАЧА:\n").append(userRequest.trim()).append("\n\n");
        result.append("Если задача — рассказ, сказка, история или новости для чтения в библиотеке, верни ТОЛЬКО корректный JSON-объект БЕЗ markdown и без пояснений, в точной форме:\n");
        result.append("{\"format\":\"study_story_v1\",\"language\":\"").append(lang)
                .append("\",\"title\":\"Название\",\"body\":\"Текст на изучаемом языке\",\"translation\":\"Русский перевод\"}\n");
        result.append("Количество предложений изучаемого языка и русского перевода должно совпадать; каждое предложение должно точно соответствовать своему переводу. ");
        result.append("Текст рассказа должен звучать естественно. ");
        result.append(store.isEnglish() ? "Пиши по-английски." : "Используй живой rioplatense voseo.");
        result.append("\nЕсли задача НЕ про рассказ или чтение, отвечай обычным текстом. ");
        result.append("Не говори, что сам добавил слова или сохранил рассказ в приложении: для сохранения я скопирую твой ответ и подтвержу импорт в приложении.");
        return result.toString();
    }

    private static String oneLine(String v) {
        return (v == null ? "" : v).replace('\n', ' ').replace('\r', ' ').trim();
    }

    public static StoryDraft parseStory(String clipboardText) throws Exception {
        if (clipboardText == null || clipboardText.trim().isEmpty())
            throw new IllegalArgumentException("Скопируйте JSON рассказа из ChatGPT");
        String text = clipboardText.trim();
        if (text.startsWith("```")) {
            int newLine = text.indexOf('\n');
            int close = text.lastIndexOf("```");
            if (newLine < 0 || close <= newLine)
                throw new IllegalArgumentException("Скопируйте весь ответ ChatGPT");
            text = text.substring(newLine + 1, close).trim();
        }
        if (text.length() > 80000) throw new IllegalArgumentException("Ответ слишком длинный");
        JSONObject obj = new JSONObject(text);
        if (!"study_story_v1".equals(obj.optString("format")))
            throw new IllegalArgumentException("Нужен JSON рассказа формата study_story_v1");
        String lang = obj.getString("language").trim().toLowerCase(Locale.ROOT);
        if (!"es".equals(lang) && !"en".equals(lang))
            throw new IllegalArgumentException("Неизвестный язык рассказа");
        String title = bounded(obj.getString("title"), 300, "Название");
        String body = bounded(obj.getString("body"), 20000, "Текст рассказа");
        String translation = bounded(obj.getString("translation"), 20000, "Перевод");
        if (Sentences.split(body).isEmpty() || Sentences.split(body).size() != Sentences.split(translation).size())
            throw new IllegalArgumentException("Не совпадает количество предложений оригинала и перевода");
        return new StoryDraft(lang, title, body, translation);
    }

    private static String bounded(String v, int size, String label) {
        String s = v == null ? "" : v.trim();
        if (s.isEmpty()) throw new IllegalArgumentException(label + ": пусто");
        if (s.length() > size) throw new IllegalArgumentException(label + ": слишком длинный текст");
        return s;
    }
}
