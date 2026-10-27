package ru.spanish.learning;

import android.test.AndroidTestCase;
import org.json.JSONObject;

public final class RegularChatGptBridgeTest extends AndroidTestCase {
    public void testPromptContainsActualCurrentCardsAndLanguage() throws Exception {
        String db = "test-regular-bridge-es.db";
        getContext().deleteDatabase(db);
        try (Store store = new Store(getContext(), db)) {
            store.addCard("el gato", "кот", 0, 2);
            String prompt = RegularChatGptBridge.prompt(store, "Расскажи о двух котятах");
            assertTrue(prompt.contains("el gato = кот"));
            assertTrue(prompt.contains("vos, tenés"));
            assertTrue(prompt.contains("Расскажи о двух котятах"));
            assertTrue(prompt.contains("study_story_v1"));
        } finally { getContext().deleteDatabase(db); }
    }

    public void testStoriesValidateAndNeverWriteWithoutUserApproval() throws Exception {
        JSONObject json = new JSONObject()
                .put("format", "study_story_v1")
                .put("language", "es")
                .put("title", "Los gatitos")
                .put("body", "Dos gatos juegan. Después descansan.")
                .put("translation", "Два котёнка играют. Потом отдыхают.");
        RegularChatGptBridge.StoryDraft draft = RegularChatGptBridge.parseStory(
                "```json\n" + json.toString() + "\n```");
        assertEquals("es", draft.language());
        assertEquals("Los gatitos", draft.title());
        String db = "test-regular-bridge-story.db";
        getContext().deleteDatabase(db);
        try (Store store = new Store(getContext(), db)) {
            assertFalse(store.stories().stream().anyMatch(s -> "Los gatitos".equals(s.title())));
            assertTrue(store.addStoryIfMissing(draft.title(), draft.body(), draft.translation(), "test"));
            assertFalse(store.addStoryIfMissing(draft.title(), draft.body(), draft.translation(), "test"));
        } finally { getContext().deleteDatabase(db); }
    }

    public void testMismatchedTranslationRejected() throws Exception {
        String value = new JSONObject()
                .put("format", "study_story_v1").put("language", "es")
                .put("title", "Prueba").put("body", "Una frase. Otra frase.")
                .put("translation", "Только одна фраза.").toString();
        try {
            RegularChatGptBridge.parseStory(value);
            fail("Translation length mismatch must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("предложений"));
        }
    }

    public void testEnglishModeStaysIndependent() throws Exception {
        String db = "test-regular-bridge-en.db";
        getContext().deleteDatabase(db);
        try (Store english = Store.englishForTest(getContext(), db)) {
            String prompt = RegularChatGptBridge.prompt(english, "Short story");
            assertTrue(prompt.contains("study_story_v1"));
            assertFalse(prompt.contains("vos, tenés"));
        } finally { getContext().deleteDatabase(db); }
    }
}
