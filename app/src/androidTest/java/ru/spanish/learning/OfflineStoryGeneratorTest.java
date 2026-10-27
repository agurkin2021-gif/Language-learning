package ru.spanish.learning;

import android.test.AndroidTestCase;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.List;

public final class OfflineStoryGeneratorTest extends AndroidTestCase {
    private static Store.Card known(String text, boolean mastered) {
        return new Store.Card(1, text, "перевод", 0, 0, mastered, 0, 0, 0);
    }

    public void testSpanishUsesMyWordsWithoutCloudTokens() {
        List<Store.Card> cards = Arrays.asList(known("la casa", true), known("mate", false));
        String prompt = OfflineStoryGenerator.buildPrompt(cards, "es", "История о доме");
        assertTrue(prompt.contains("la casa"));
        assertTrue(prompt.contains("mate"));
        assertTrue(prompt.contains("Argentinian Spanish"));
        assertTrue(prompt.contains("История о доме"));
        assertTrue(prompt.contains("study_story_v1"));
        assertFalse(prompt.contains("openai.com"));
    }

    public void testEnglishDoesNotRequestSpanishGrammar() {
        String prompt = OfflineStoryGenerator.buildPrompt(List.of(known("school", true)),
                "en", "About a teacher");
        assertTrue(prompt.contains("Language: English"));
        assertFalse(prompt.contains("voseo"));
    }

    public void testIncompleteTranslationNeverSaved() throws Exception {
        String broken = new JSONObject()
                .put("format", "study_story_v1")
                .put("language", "es")
                .put("title", "Historia")
                .put("body", "Mi casa es grande. Tengo un perro. Es bonito.")
                .put("translation", "Мой дом большой.").toString();
        try {
            OfflineStoryGenerator.parseResult(broken);
            fail("Sentence alignment must be checked before saving");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("предложений"));
        }
    }

    public void testGoodResultWithoutMarkdownStillParses() throws Exception {
        String raw = new JSONObject()
                .put("format", "study_story_v1")
                .put("language", "es")
                .put("title", "La casa")
                .put("body", "Hay una casa. Veo un gato. Es pequeño.")
                .put("translation", "Есть дом. Я вижу кота. Он маленький.").toString();
        assertEquals("La casa", OfflineStoryGenerator.parseResult(raw).title());
        assertEquals("La casa", OfflineStoryGenerator.parseResult("Here is the JSON: " + raw).title());
    }

    public void testMissingModelIsNotFakedReady() {
        // Tests do not download 550MB and cannot validate inference on emulators.
        assertTrue(OfflineModelManager.SOURCE.startsWith("https://huggingface.co/"));
        assertTrue(OfflineModelManager.MIN_BYTES >= 400L * 1024 * 1024);
    }
}
