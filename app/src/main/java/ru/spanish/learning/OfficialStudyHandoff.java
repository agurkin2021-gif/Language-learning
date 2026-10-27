package ru.spanish.learning;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Official consumer ChatGPT hand-off. NO API calls, embedded AI, browser cookies
 * or Subscription Sharing. Each response is copied/shared by the user.
 *
 * Three real, sequential audit responses are required before the app may save.
 * The random session identifier prevents accidental cross-imports between
 * phones, languages, stories and the clipboard.
 */
public final class OfficialStudyHandoff {
    private static final String PREFS = "official_study_handoff";
    private static final String DRAFT = "draft";
    private static final String AUDIT = "audit";
    private static final String VERIFIED = "verified";
    private static final String REJECTED = "rejected";

    private OfficialStudyHandoff() {}

    public record Outcome(String status, String prompt, String kind, String title,
                          String body, String translation, String term, String message) {}

    private static String key(String language) {
        if (!"es".equals(language) && !"en".equals(language))
            throw new IllegalArgumentException("Некорректный язык");
        return "session_" + language;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JSONObject state(Context ctx, String language) {
        String raw = prefs(ctx).getString(key(language), "");
        if (raw.isEmpty()) return null;
        try { return new JSONObject(raw); }
        catch (Exception ignored) { return null; }
    }

    private static void persist(Context ctx, String language, JSONObject state) {
        if (!prefs(ctx).edit().putString(key(language), state.toString()).commit())
            throw new IllegalStateException("Не удалось сохранить состояние проверки");
    }

    public static void clear(Context ctx, String language) {
        prefs(ctx).edit().remove(key(language)).commit();
    }

    public static String stage(Context ctx, String language) {
        JSONObject s = state(ctx, language);
        if (s == null) return "";
        String status = s.optString("status");
        if (DRAFT.equals(status)) return "Ожидается рассказ или перевод из ChatGPT";
        if (AUDIT.equals(status)) return "Ожидается аудит " + s.optInt("stage") + "/3";
        if (VERIFIED.equals(status)) return "Все три аудита пройдены. Осталось сохранить";
        if (REJECTED.equals(status)) return "Проверка отклонена; начните заново";
        return "";
    }

    public static boolean pending(Context ctx, String language) {
        JSONObject s = state(ctx, language);
        if (s == null) return false;
        return DRAFT.equals(s.optString("status")) || AUDIT.equals(s.optString("status"))
                || VERIFIED.equals(s.optString("status"));
    }

    public static String preparedPrompt(Context ctx, String language) {
        JSONObject s = state(ctx, language);
        if (s == null) return "";
        if (!DRAFT.equals(s.optString("status")) && !AUDIT.equals(s.optString("status")))
            return "";
        return s.optString("prompt", "");
    }

    private static String nonce() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }

    /** Called only by a user's tap on an existing ChatGPT button. */
    public static String begin(Context ctx, String language, String kind, String request,
                               List<Store.Card> vocabulary) throws Exception {
        if (!"story".equals(kind) && !"word".equals(kind) && !"phrase".equals(kind))
            throw new IllegalArgumentException("Неизвестный режим передачи");
        if (request == null || request.trim().isEmpty() || request.length() > 1800)
            throw new IllegalArgumentException("Нужно конкретное задание или словоформа");
        JSONObject previous = state(ctx, language);
        if (previous != null && pending(ctx, language))
            throw new IllegalStateException(stage(ctx, language) + ". Завершите или отмените текущую проверку.");
        // Reject a stale previous session, then create a new one.
        JSONObject s = new JSONObject();
        String id = nonce();
        s.put("session_id", id);
        s.put("language", language);
        s.put("kind", kind);
        s.put("request", request.trim());
        s.put("status", DRAFT);
        s.put("stage", 0);

        StringBuilder prompt = new StringBuilder();
        prompt.append("Учебное задание для обычного ChatGPT (без API). Отвечай строго JSON, ");
        prompt.append("без markdown, не меняй session_id. Перевод должен быть дословно ");
        prompt.append("точным, без догадок; никогда не подменяй непонятные слова созвучными. ");
        prompt.append("Для испанского используй rioplatense (vos, tenés), для английского EN. ");
        prompt.append("Русский перевод должен передавать точные формы и контекст. ");
        prompt.append("После твоего первого ответа последуют ТРИ ОТДЕЛЬНЫЕ проверки, ");
        prompt.append("их прохождение нельзя подтверждать в этом ответе.\n");
        prompt.append("session_id: ").append(id).append("\n");
        prompt.append("Изучаемый язык: ").append(language).append(".\n");
        prompt.append("Задание: ").append(request.trim()).append("\n");

        if ("story".equals(kind)) {
            prompt.append("Напиши рассказ из 3–7 законченных предложений. ");
            prompt.append("Верни JSON: {\"format\":\"study_story_v1\",");
            prompt.append("\"session_id\":\"").append(id).append("\",");
            prompt.append("\"language\":\"").append(language).append("\",");
            prompt.append("\"title\":\"...\",\"body\":\"...\",\"translation\":\"...\"}. ");
            prompt.append("Число предложений оригинала и перевода должно совпадать.\n");
            ArrayList<Store.Card> cards = new ArrayList<>(vocabulary);
            cards.sort(Comparator.comparing((Store.Card c) -> !c.mastered())
                    .thenComparing(c -> c.kind() != 1));
            prompt.append("Мой личный словарь (изученные сначала, выражения в приоритете):\n");
            for (int i = 0; i < Math.min(120, cards.size()); i++) {
                Store.Card c = cards.get(i);
                prompt.append(c.mastered() ? "[знаю]" : "[учусь]");
                prompt.append(c.kind() == 1 ? "[выражение] " : "[слово] ");
                prompt.append(sanitize(c.spanish(), 110)).append(" = ");
                prompt.append(sanitize(c.meaning(), 160)).append("\n");
            }
            if (cards.size() > 120) prompt.append("Показаны первые 120 карточек из ").append(cards.size()).append(".\n");
        } else {
            prompt.append("Переведи ТОЛЬКО исходную словоформу или выражение, ");
            prompt.append("не подменяй его другой формой. Точный дословный перевод первым; ");
            prompt.append("естественное значение при необходимости в скобках. ");
            prompt.append("Если значение неоднозначно, явно укажи варианты в контексте.\n");
            prompt.append("Верни JSON: {\"format\":\"study_word_v1\",");
            prompt.append("\"session_id\":\"").append(id).append("\",");
            prompt.append("\"language\":\"").append(language).append("\",");
            prompt.append("\"kind\":\"").append(kind).append("\",");
            prompt.append("\"term\":\"").append(escapeJson(request.trim())).append("\",");
            prompt.append("\"translation\":\"...\"}.\n");
        }
        s.put("prompt", prompt.toString());
        persist(ctx, language, s);
        return prompt.toString();
    }

    private static String sanitize(String raw, int max) {
        String s = raw == null ? "" : raw.replace('\r', ' ').replace('\n', ' ').trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String escapeJson(String raw) {
        return JSONObject.quote(raw).substring(1, JSONObject.quote(raw).length() - 1);
    }

    private static JSONObject parse(String raw) throws Exception {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("```")) {
            int newline = text.indexOf('\n');
            int close = text.lastIndexOf("```");
            if (newline < 0 || close <= newline) throw new IllegalArgumentException("Неполный JSON-ответ");
            text = text.substring(newline + 1, close).trim();
        }
        if (text.length() < 2 || text.length() > 80000)
            throw new IllegalArgumentException("Скопируйте полный JSON-ответ ChatGPT");
        return new JSONObject(text);
    }

    public static boolean isOurReply(Context ctx, String language, String raw) {
        JSONObject s = state(ctx, language);
        if (s == null || !pending(ctx, language) || raw == null) return false;
        try {
            return s.optString("session_id").equals(parse(raw).optString("session_id"));
        } catch (Exception ignored) {
            return false;
        }
    }

    public static Outcome accept(Context ctx, String language, String raw) throws Exception {
        JSONObject s = state(ctx, language);
        if (s == null) throw new IllegalStateException("Сначала отправьте задание в ChatGPT");
        if (REJECTED.equals(s.optString("status")))
            throw new IllegalStateException("Проверка отклонена. Начните новую");
        if (VERIFIED.equals(s.optString("status"))) return completed(s);
        JSONObject reply = parse(raw);
        if (!s.optString("session_id").equals(reply.optString("session_id")))
            throw new IllegalArgumentException("Этот ответ относится к другому заданию или телефону");
        if (DRAFT.equals(s.optString("status"))) {
            String kind = s.getString("kind");
            String translation;
            if ("story".equals(kind)) {
                if (!"study_story_v1".equals(reply.optString("format")))
                    throw new IllegalArgumentException("Ожидается рассказ study_story_v1");
                RegularChatGptBridge.StoryDraft draft =
                        RegularChatGptBridge.parseStory(reply.toString());
                if (!language.equals(draft.language()))
                    throw new IllegalArgumentException("Ответ для другого языка");
                s.put("title", draft.title());
                s.put("body", draft.body());
                translation = draft.translation();
            } else {
                if (!"study_word_v1".equals(reply.optString("format"))
                        || !language.equals(reply.optString("language"))
                        || !kind.equals(reply.optString("kind"))
                        || !s.getString("request").equals(reply.optString("term")))
                    throw new IllegalArgumentException("Исходная словоформа изменена или неверный формат");
                translation = reply.optString("translation", "").trim();
                if (translation.isEmpty() || translation.length() > 650)
                    throw new IllegalArgumentException("Нет корректного русского перевода");
                s.put("term", s.getString("request"));
            }
            s.put("translation", translation);
            s.put("status", AUDIT);
            s.put("stage", 1);
            s.put("prompt", auditPrompt(s));
            persist(ctx, language, s);
            return new Outcome(AUDIT, s.getString("prompt"), kind, "", "", "",
                    "", "Рассказ или слово получены. Следующий шаг: аудит 1/3");
        }

        if (!AUDIT.equals(s.optString("status")))
            throw new IllegalStateException("Недопустимое состояние проверки");
        int stage = s.getInt("stage");
        if (!"study_audit_v1".equals(reply.optString("format"))
                || reply.optInt("stage", -1) != stage)
            throw new IllegalArgumentException("Ожидается результат аудита " + stage + "/3");
        if (!reply.has("pass") || !(reply.opt("pass") instanceof Boolean)
                || !s.getString("translation").equals(reply.optString("verified_translation")))
            throw new IllegalArgumentException("Аудит не подтвердил дословно тот же перевод");
        JSONArray issues = reply.optJSONArray("issues");
        if (issues == null) throw new IllegalArgumentException("В аудите отсутствует массив issues");
        if (!reply.getBoolean("pass") || issues.length() != 0) {
            s.put("status", REJECTED);
            persist(ctx, language, s);
            throw new IllegalArgumentException("Аудит " + stage + "/3 обнаружил ошибку. " +
                    (issues.length() > 0 ? issues.optString(0) : "Перевод не подтверждён") +
                    ". Ничего не сохранено.");
        }
        if (stage == 3) {
            s.put("status", VERIFIED);
            s.remove("prompt");
            persist(ctx, language, s);
            return completed(s);
        }
        s.put("stage", stage + 1);
        s.put("prompt", auditPrompt(s));
        persist(ctx, language, s);
        return new Outcome(AUDIT, s.getString("prompt"), s.getString("kind"), "",
                "", "", "", "Аудит " + stage + "/3 пройден. Следующий: " + (stage + 1) + "/3");
    }

    private static String auditPrompt(JSONObject s) throws Exception {
        int stage = s.getInt("stage");
        String directive;
        if (stage == 1) {
            directive = "Проверь ДОСЛОВНО каждую словоформу и перевод без вольных парафраз.";
        } else if (stage == 2) {
            directive = "Проверь ГРАММАТИКУ, лица, времена, отрицание, предлоги, аргентинский voseo.";
        } else if (stage == 3) {
            directive = "НЕЗАВИСИМО проверь СМЫСЛ, отсутствие пропусков, идиомы и контекст.";
        } else {
            throw new IllegalArgumentException("Аудит должен быть 1, 2 или 3");
        }
        JSONObject material = new JSONObject();
        material.put("language", s.getString("language"));
        material.put("kind", s.getString("kind"));
        if ("story".equals(s.getString("kind"))) {
            material.put("body", s.getString("body"));
        } else {
            material.put("term", s.getString("term"));
        }
        material.put("translation", s.getString("translation"));
        return "Это отдельный аудит " + stage + "/3, а не ответ на старый аудит. " +
                directive + " Ошибка или сомнение = pass:false; не исправляй молча. " +
                "Верни ТОЛЬКО JSON (без Markdown) с ТОЧНЫМИ ключами и session_id: " +
                "{\"format\":\"study_audit_v1\",\"session_id\":\"" +
                s.getString("session_id") + "\",\"stage\":" + stage +
                ",\"pass\":true,\"issues\":[],\"verified_translation\":" +
                JSONObject.quote(s.getString("translation")) + "}. " +
                "В verified_translation дословно скопируй исходный русский перевод, " +
                "БЕЗ единого изменения; при ошибке сообщи pass:false и issues. " +
                "Материал:\n" + material.toString();
    }

    private static Outcome completed(JSONObject s) throws Exception {
        if (!VERIFIED.equals(s.getString("status")))
            throw new IllegalStateException("Три аудита ещё не пройдены");
        return new Outcome(VERIFIED, "", s.getString("kind"),
                s.optString("title"), s.optString("body"),
                s.getString("translation"), s.optString("term"),
                "Все 3 аудита пройдены; можно сохранять");
    }

    public static Outcome alreadyVerified(Context ctx, String language) throws Exception {
        JSONObject s = state(ctx, language);
        if (s == null || !VERIFIED.equals(s.optString("status")))
            throw new IllegalStateException("Проверенный материал пока отсутствует");
        return completed(s);
    }
}
