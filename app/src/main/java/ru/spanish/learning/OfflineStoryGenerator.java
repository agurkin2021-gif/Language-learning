package ru.spanish.learning;

import android.content.Context;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import org.json.JSONObject;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * On-device model runner. No HTTP calls, subscription sharing, or remote API.
 * The user's complete dictionary remains on the phone; a context-size-bounded
 * selection is provided to the model for each story request.
 */
public final class OfflineStoryGenerator {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "offline-story-generator");
        t.setDaemon(true);
        return t;
    });

    public interface Listener {
        void progress(String stage);
        void complete(long storyId, String title, String error);
    }

    public void generate(Context context, Store store, String language,
                         String userRequest, Listener listener) {
        worker.execute(() -> {
            LlmInference model = null;
            try {
                if (!OfflineModelManager.ready(context))
                    throw new IllegalStateException("Локальная модель не скачана");
                listener.progress("Подбираю слова и выражения из личного словаря…");
                String prompt = buildPrompt(store.vocabulary(), language, userRequest);
                listener.progress("Загружаю локальную нейросеть в память телефона…");
                LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(OfflineModelManager.file(context).getAbsolutePath())
                        .setMaxTokens(1280)
                        .setMaxTopK(40)
                        .build();
                model = LlmInference.createFromOptions(context.getApplicationContext(), options);
                final LlmInference auditorModel = model;
                RegularChatGptBridge.StoryDraft draft = null;
                String validationError = "";
                for (int attempt = 0; attempt < 2 && draft == null; attempt++) {
                    listener.progress(attempt == 0
                            ? "Нейросеть пишет рассказ и русский перевод прямо на телефоне…"
                            : "Повторно проверяю формат и перевод рассказа…");
                    String response = model.generateResponse(attempt == 0
                            ? prompt : prompt + "\nВАЖНО: прежний ответ оказался некорректным (" +
                                validationError + "). Верни только JSON без лишнего текста.");
                    try {
                        RegularChatGptBridge.StoryDraft candidate = parseResult(response);
                        if (!language.equals(candidate.language()))
                            throw new IllegalArgumentException("Ответ на другом языке");
                        // Three separate, fail-closed semantic audits. A JSON structure
                        // and a matching sentence count are not translation QA.
                        String translation = OfflineStoryTranslationAudit.review(
                                language, candidate.body(), candidate.translation(),
                                (pass, rules, data) -> auditorModel.generateResponse(
                                        rules + "\\nINPUT_JSON:\\n" + data),
                                pass -> listener.progress("Аудит перевода " + pass
                                        + "/3: проверяю каждую словоформу и смысл…"));
                        draft = new RegularChatGptBridge.StoryDraft(
                                candidate.language(), candidate.title(), candidate.body(), translation);
                    } catch (Exception invalid) {
                        validationError = invalid.getMessage();
                    }
                }
                if (draft == null)
                    throw new IllegalArgumentException("Модель не смогла подготовить корректный рассказ: " +
                            validationError + ". Попробуйте более короткую тему.");
                listener.progress("Сохраняю проверенный рассказ в личную библиотеку…");
                long id = store.addStory(draft.title(), draft.body(), draft.translation(),
                        "Локальная модель • Qwen2.5 (офлайн, перевод прошёл аудит 3/3)");
                listener.complete(id, draft.title(), "");
            } catch (OutOfMemoryError e) {
                listener.complete(-1, "", "Недостаточно оперативной памяти для локальной модели. " +
                        "Закройте другие приложения и повторите.");
            } catch (Exception | LinkageError e) {
                String message = e.getMessage();
                listener.complete(-1, "", message == null || message.isEmpty()
                        ? "Не удалось запустить нейросеть на этом телефоне"
                        : "Ошибка локальной модели: " + message);
            } finally {
                if (model != null) {
                    try { model.close(); } catch (Exception ignored) { }
                }
            }
        });
    }

    static RegularChatGptBridge.StoryDraft parseResult(String response) throws Exception {
        if (response == null) throw new IllegalArgumentException("Модель вернула пустой ответ");
        String raw = response.trim();
        if (!raw.startsWith("{") && !raw.startsWith("```")) {
            int start = raw.indexOf('{'), end = raw.lastIndexOf('}');
            if (start >= 0 && end > start) raw = raw.substring(start, end + 1);
        }
        return RegularChatGptBridge.parseStory(raw);
    }

    /** CPU Qwen .task 1280-token context: cap input to allow room for output. */
    static String buildPrompt(List<Store.Card> vocabulary, String language, String request) {
        if (!"es".equals(language) && !"en".equals(language))
            throw new IllegalArgumentException("Поддерживаются только испанский и английский");
        String task = request == null || request.trim().isEmpty()
                ? "Короткая сказка о дружбе" : request.trim();
        if (task.length() > 450) task = task.substring(0, 450);
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a bilingual language teacher. Generate a tiny beginner story for a Russian speaker. ");
        prompt.append("Language: ").append("es".equals(language)
                ? "Argentinian Spanish with natural voseo" : "English").append(". ");
        prompt.append("Story topic: ").append(task.replace('\n', ' ')).append(". ");
        prompt.append("Write EXACTLY 3 short sentences in that language and 3 matching Russian sentences. ");
        prompt.append("Keep original and Russian sentence order identical; use only correct natural grammar. ");
        prompt.append("Translate each meaningful word-form, including negation, person and tense; keep Russian literal-first. ");
        prompt.append("Use some known words from this learner list naturally: ");
        int selected = 0;
        if (vocabulary != null) {
            // Prioritize mastered cards and stable due vocabulary; never force every
            // word into the prompt because the model context is finite.
            for (int pass = 0; pass < 2; pass++) {
                for (Store.Card card : vocabulary) {
                    if ((pass == 0) != card.mastered()) continue;
                    String word = card.spanish().replaceAll("[\\r\\n]+", " ").trim();
                    if (word.length() < 2 || word.length() > 38) continue;
                    if (selected > 0) prompt.append("; ");
                    prompt.append(word);
                    if (++selected >= 22) break;
                }
                if (selected >= 22) break;
            }
        }
        if (selected == 0) prompt.append("beginner vocabulary");
        prompt.append(". Return a single valid JSON object ONLY, no markdown or comments: ");
        prompt.append("{\"format\":\"study_story_v1\",\"language\":\"")
                .append(language)
                .append("\",\"title\":\"short title\",\"body\":\"three sentences\",")
                .append("\"translation\":\"exact Russian translation in three sentences\"}");
        return prompt.toString();
    }

    public void close() { worker.shutdownNow(); }
}
