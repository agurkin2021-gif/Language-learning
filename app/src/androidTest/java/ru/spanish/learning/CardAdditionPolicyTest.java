package ru.spanish.learning;

import android.test.AndroidTestCase;
import org.json.JSONObject;
import java.io.File;

public class CardAdditionPolicyTest extends AndroidTestCase {
    private static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"256\""
            + " height=\"256\" viewBox=\"0 0 256 256\">"
            + "<circle cx=\"128\" cy=\"128\" r=\"65\" fill=\"#476a91\"/></svg>";

    private static ChatGptAssistant.ToolCall word(String foreign, String russian,
                                                   String category, String svg) throws Exception {
        JSONObject args = new JSONObject()
                .put("spanish", foreign).put("russian", russian)
                .put("category", category).put("svg", svg);
        return new ChatGptAssistant.ToolCall("test-id", "add_word", args.toString());
    }

    public void testLimitIsHardAndRequestMaySetSmallerCount() throws Exception {
        CardAdditionPolicy.Batch hundred = new CardAdditionPolicy.Batch("Добавь 100 слов A1");
        assertEquals(10, hundred.limit);
        assertEquals(2, new CardAdditionPolicy.Batch("Добавь 2 слова").limit);
        assertEquals(3, new CardAdditionPolicy.Batch("Добавь 3 простых испанских слова уровня A1").limit);
        assertEquals(5, new CardAdditionPolicy.Batch("Добавь 5 новых частотных испанских слов").limit);
        assertEquals(10, new CardAdditionPolicy.Batch("Добавь 100 самых употребительных испанских слов A1").limit);
        assertEquals(4, new CardAdditionPolicy.Batch("Добавь 4 устойчивых выражения").limit);
        assertEquals(10, new CardAdditionPolicy.Batch("Добавь слова А1").limit);
        assertEquals(10, new CardAdditionPolicy.Batch("Добавь 999 слов").limit);
        assertEquals(1, new CardAdditionPolicy.Batch("Добавь 1 слово").limit);

        String db = "test-batch-card-limit.db";
        getContext().deleteDatabase(db);
        try (Store store = new Store(getContext(), db)) {
            store.resetLearningData();
            ChatGptAssistant assistant = new ChatGptAssistant(getContext());
            try {
                for (int i = 0; i < 11; i++) {
                    String spanish = "nuevapalabra" + (char) ('a' + i);
                    String result = assistant.runLimitedTool(store,
                            word(spanish, "новое слово", "noun", SVG), hundred);
                    JSONObject json = new JSONObject(result);
                    if (i < 10) {
                        assertTrue("Insertion " + i + " failed: " + result,
                                json.optBoolean("ok"));
                        assertEquals("added", json.optString("status"));
                    } else {
                        assertFalse(json.optBoolean("ok"));
                        assertEquals("limit_reached", json.optString("status"));
                    }
                }
                assertEquals(10, hundred.added);
                assertEquals(10, hundred.attempted);
                assertEquals(10, store.vocabulary().size());
                assertTrue(hundred.report().contains("добавлено 10"));
                assertTrue(hundred.report().contains("Лимит достигнут"));
                assertTrue(hundred.report().contains("Добавлены: nuevapalabraa"));
                assertFalse(hundred.report().contains("nuevapalabrak"));
            } finally { assistant.close(); }
        } finally {
            getContext().deleteDatabase(db);
        }
    }

    public void testEmojiIsNativeAndDuplicateCannotOverwritePictureOrMeaning() throws Exception {
        String db = "test-batch-emoji.db";
        getContext().deleteDatabase(db);
        File emoji = CardAdditionPolicy.emojiFile(getContext(), "gato", false);
        try (Store store = new Store(getContext(), db)) {
            store.resetLearningData();
            ChatGptAssistant assistant = new ChatGptAssistant(getContext());
            try {
                CardAdditionPolicy.Batch quota = new CardAdditionPolicy.Batch("Добавь 10 слов");
                JSONObject first = new JSONObject(assistant.runLimitedTool(store,
                        word("gato", "кот", "noun", ""), quota));
                assertEquals("added", first.getString("status"));
                assertEquals("emoji", first.getString("image"));
                assertTrue(emoji.isFile());
                assertEquals("🐈", CardAdditionPolicy.savedEmoji(getContext(), "gato", false));
                assertTrue(CardImageLoader.hasIllustration(getContext(), "gato", false));
                JSONObject again = new JSONObject(assistant.runLimitedTool(store,
                        word("GATO", "собака", "noun", "<script>bad</script>"), quota));
                assertEquals("already_exists", again.getString("status"));
                assertEquals(1, store.vocabulary().size());
                assertEquals("кот", store.vocabulary().get(0).meaning());
                assertEquals("🐈", CardAdditionPolicy.savedEmoji(getContext(), "gato", false));
                assertEquals(1, quota.added);
                assertEquals(1, quota.duplicates);
            } finally { assistant.close(); }
        } finally {
            emoji.delete();
            getContext().deleteDatabase(db);
        }
    }

    public void testRejectsUntranslatedValuesAndInvalidSvgBeforeDatabaseSave() throws Exception {
        String db = "test-batch-validation.db";
        getContext().deleteDatabase(db);
        try (Store store = new Store(getContext(), db)) {
            store.resetLearningData();
            ChatGptAssistant assistant = new ChatGptAssistant(getContext());
            try {
                CardAdditionPolicy.Batch batch = new CardAdditionPolicy.Batch("Добавь 10 слов");
                JSONObject badMeaning = new JSONObject(assistant.runLimitedTool(store,
                        word("gallina", "перевод уточняется по контексту", "noun", SVG),
                        batch));
                assertFalse(badMeaning.getBoolean("ok"));
                JSONObject noImage = new JSONObject(assistant.runLimitedTool(store,
                        word("gallina", "курица", "noun", ""), batch));
                assertFalse(noImage.getBoolean("ok"));
                JSONObject script = new JSONObject(assistant.runLimitedTool(store,
                        word("gallina", "курица", "noun",
                                "<svg width=\"256\" height=\"256\" viewBox=\"0 0 256 256\">"
                                        + "<script>alert(1)</script><circle cx=\"5\" cy=\"5\" r=\"1\"/></svg>"),
                        batch));
                assertFalse(script.getBoolean("ok"));
                JSONObject good = new JSONObject(assistant.runLimitedTool(store,
                        word("gallina", "курица", "noun", SVG), batch));
                assertEquals("added", good.getString("status"));
                assertEquals("svg", good.getString("image"));
                assertEquals(1, store.vocabulary().size());
                assertEquals(3, batch.rejected);
            } finally { assistant.close(); }
        } finally {
            CardImageLoader.imageFile(getContext(), "gallina", false).delete();
            getContext().deleteDatabase(db);
        }
    }

    public void testPhraseRulesUnchangedAndEnglishImageSeparate() throws Exception {
        String db = "test-batch-expressions.db";
        getContext().deleteDatabase(db);
        try (Store spanish = new Store(getContext(), db)) {
            spanish.resetLearningData();
            ChatGptAssistant assistant = new ChatGptAssistant(getContext());
            try {
                CardAdditionPolicy.Batch quota = new CardAdditionPolicy.Batch("Добавь 10 выражений");
                JSONObject bad = new JSONObject().put("spanish", "tener razón")
                        .put("russian", "быть правым").put("svg", SVG);
                JSONObject result = new JSONObject(assistant.runLimitedTool(spanish,
                        new ChatGptAssistant.ToolCall("test-id", "add_phrase", bad.toString()), quota));
                assertFalse(result.getBoolean("ok"));
                bad.put("spanish", "tengo razón").put("russian", "я прав");
                JSONObject good = new JSONObject(assistant.runLimitedTool(spanish,
                        new ChatGptAssistant.ToolCall("test-id", "add_phrase", bad.toString()), quota));
                assertEquals("added", good.getString("status"));
                assertEquals(1, spanish.vocabulary().size());
            } finally { assistant.close(); }
        } finally {
            CardImageLoader.imageFile(getContext(), "tengo razón", false).delete();
            getContext().deleteDatabase(db);
        }
        assertEquals("🐈", CardAdditionPolicy.exactEmoji("cat", "кот", true, 2));
        assertNull(CardAdditionPolicy.exactEmoji("cat", "собака", true, 2));
        assertNull(CardAdditionPolicy.exactEmoji("gato", "кот", false, 1));
    }
}
