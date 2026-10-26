package ru.spanish.learning;

import android.test.AndroidTestCase;

public class StoreTest extends AndroidTestCase {
    public void testPersistenceAndExplicitArchive() {
        getContext().deleteDatabase("test-learning.db");
        Store s = new Store(getContext(), "test-learning.db");

        int wordsBefore = s.cards(false, 0).size();
        int expressionsBefore = s.cards(false, 1).size();
        assertEquals(300, s.cards(false, 0, 1).size());
        assertEquals(300, s.cards(false, 0, 2).size());
        assertEquals(300, s.cards(false, 0, 3).size());
        assertEquals(900, wordsBefore);
        assertEquals(50, expressionsBefore);

        s.addCard("mate personalizado", "мате — пользовательская карточка", 0);
        s.addCard("por favor personalizado", "пожалуйста — пользовательская карточка", 1);
        assertEquals(wordsBefore + 1, s.cards(false, 0).size());
        assertEquals(expressionsBefore + 1, s.cards(false, 1).size());

        long id = -1;
        for (Store.Card card : s.cards(false, 0)) {
            if ("mate personalizado".equals(card.spanish())) id = card.id();
        }
        assertTrue(id > 0);
        s.setMastered(id, true);
        s.close();

        s = new Store(getContext(), "test-learning.db");
        boolean foundMastered = false;
        for (Store.Card card : s.mastered()) {
            if ("mate personalizado".equals(card.spanish())) foundMastered = true;
        }
        assertTrue(foundMastered);
        assertEquals(expressionsBefore + 1, s.cards(false, 1).size());

        long story = StoryGenerator.save(s, new StoryGenerator.Draft("Prueba", "Hola. Buen día.", "Привет. Добрый день.", "Тестовый адаптер"));
        s.position(story, 1);
        s.close();

        s = new Store(getContext(), "test-learning.db");
        assertEquals("Prueba", s.stories().get(0).title());
        assertEquals("Привет. Добрый день.", s.stories().get(0).translation());
        assertEquals(1, s.stories().get(0).position());
        s.setMastered(id, false);

        boolean returned = false;
        for (Store.Card card : s.cards(false, 0)) {
            if ("mate personalizado".equals(card.spanish())) returned = true;
        }
        assertTrue(returned);
        s.close();
        getContext().deleteDatabase("test-learning.db");
    }


    public void testAssistantReceivesAllMasteredAndStudyCards() {
        String database = "test-assistant-vocabulary.db";
        getContext().deleteDatabase(database);
        try (Store store = new Store(getContext(), database)) {
            java.util.List<Store.Card> original = store.vocabulary();
            assertEquals(950, original.size());

            // The 301st–320th mastered cards used to be missing from assistant context.
            store.getWritableDatabase().execSQL(
                    "UPDATE cards SET mastered=1 WHERE id IN (SELECT id FROM cards ORDER BY id ASC LIMIT 320)");
            store.addCard("palabra nueva para aprender", "новое слово для изучения", 0, 1);
            store.addCard("frase nueva para estudiar", "новое выражение для изучения", 1, 0);
            assertEquals(952, store.vocabulary().size());
            assertEquals(320, store.mastered().size());

            ChatGptAssistant assistant = new ChatGptAssistant(getContext());
            try {
                String instructions = assistant.buildInstructions(store);
                assertTrue(instructions.contains("Всего карточек в локальном словаре: 952"));
                assertTrue(instructions.contains("Освоено (320)"));
                assertTrue(instructions.contains("В изучении (632)"));
                Store.Card beyond300 = original.get(319);
                assertTrue("The 320th learned card must be in the assistant context",
                        instructions.contains(beyond300.spanish() + " = " + beyond300.meaning()));
                assertTrue("New unmastered words must be available",
                        instructions.contains("palabra nueva para aprender = новое слово для изучения"));
                assertTrue("New unmastered phrases must be available",
                        instructions.contains("frase nueva para estudiar = новое выражение для изучения"));
            } finally {
                assistant.close();
            }
        }
        getContext().deleteDatabase(database);
    }

    public void testLongPressStoryWordSavesExactFormAndContextWithoutChangingStory() throws Exception {
        String db = "test-story-word.db";
        getContext().deleteDatabase(db);
        try (Store store = new Store(getContext(), db)) {
            int original = store.vocabulary().size();
            String spanish = "¿Querés tomar un mate?";
            String russian = "Хочешь выпить мате?";
            String originalStoryBody = store.stories().get(0).body();
            assertEquals(Store.StoryWordSave.ADDED,
                    StoryVocabulary.add(getContext(), store, "Querés", spanish, russian));
            assertEquals(original + 1, store.vocabulary().size());
            Store.Card saved = null;
            for (Store.Card c : store.vocabulary()) {
                if ("Querés".equals(c.spanish())) { saved = c; break; }
            }
            assertNotNull(saved);
            assertEquals(1, saved.category());
            assertTrue(saved.meaning().contains("querer"));
            assertTrue(saved.meaning().contains(spanish));
            assertTrue(saved.meaning().contains(russian));
            assertTrue(new java.io.File(getContext().getFilesDir(),
                    "card-images/" + CardImageLoader.key("Querés") + ".svg").isFile());
            assertEquals(Store.StoryWordSave.ALREADY_STUDYING,
                    StoryVocabulary.add(getContext(), store, "querés", spanish, russian));
            assertEquals(original + 1, store.vocabulary().size());

            store.setMastered(saved.id(), true);
            assertEquals(Store.StoryWordSave.RESTORED,
                    StoryVocabulary.add(getContext(), store, "QUERÉS", spanish, russian));
            assertEquals(original + 1, store.vocabulary().size());
            boolean returnedToStudy = false;
            for (Store.Card candidate : store.vocabulary()) {
                if (candidate.id() == saved.id()) returnedToStudy = !candidate.mastered();
            }
            assertTrue("Holding a mastered word returns it to learning", returnedToStudy);

            assertEquals(Store.StoryWordSave.ADDED,
                    StoryVocabulary.add(getContext(), store, "caminábamos",
                            "Antes caminábamos juntos.", "Раньше мы гуляли вместе."));
            Store.Card unknown = store.vocabulary().get(store.vocabulary().size() - 1);
            assertEquals("caminábamos", unknown.spanish());
            assertEquals(0, unknown.category());
            assertTrue(unknown.meaning().contains("Раньше мы гуляли вместе."));
            assertEquals(originalStoryBody, store.stories().get(0).body());
        }
        getContext().deleteDatabase(db);
    }

    public void testRemoteContentImportsOnceWithoutApkUpdate() {
        String database = "test-remote-content.db";
        getContext().deleteDatabase(database);
        try (Store s = new Store(getContext(), database)) {
            int words = s.cards(false, 0).size();
            int phrases = s.cards(false, 1).size();
            int stories = s.stories().size();

            assertTrue(s.importRemoteCard("word-1", "barco", "лодка", 0, 2));
            assertFalse(s.importRemoteCard("word-1", "barco", "лодка", 0, 2));
            assertTrue(s.importRemoteCard("phrase-1", "tener ganas de", "хотеть, иметь желание", 1, 0));
            assertTrue(s.importRemoteStory("story-1", "El pescador", "El pescador mira el mar. Vuelve a casa.", "Рыбак смотрит на море. Он возвращается домой."));

            assertEquals(words + 1, s.cards(false, 0).size());
            assertEquals(phrases + 1, s.cards(false, 1).size());
            assertEquals(stories + 1, s.stories().size());
            assertEquals("El pescador", s.stories().get(0).title());
        }
        getContext().deleteDatabase(database);
    }

    public void testCustomCategoryAndWeakReviewSchedulePersist() {
        String database = "test-review-schedule.db";
        getContext().deleteDatabase(database);
        long id = -1;
        try (Store s = new Store(getContext(), database)) {
            s.addCard("palabra personalizada", "слово для проверки", 0, 2);
            for (Store.Card card : s.cards(false, 0, 2)) {
                if ("palabra personalizada".equals(card.spanish())) {
                    id = card.id();
                    break;
                }
            }
            assertTrue(id > 0);
            s.review(id, 0, 5);
        }

        try (Store s = new Store(getContext(), database)) {
            Store.Card found = null;
            for (Store.Card card : s.cards(false, 0, 2)) if (card.id() == id) found = card;
            assertNotNull(found);
            assertEquals(0, found.lastResult());
            assertEquals(1, found.reviewCount());
            assertEquals(5, found.nextDue());
            s.review(id, 2, 0);
        }

        try (Store s = new Store(getContext(), database)) {
            boolean mastered = false;
            for (Store.Card card : s.cards(true, 0, 2)) if (card.id() == id) mastered = true;
            assertTrue(mastered);
        }
        getContext().deleteDatabase(database);
    }

    /** The first install has starter data; a deliberate reset never restores it. */
    public void testResetClearsCardsStoriesProgressAndBlocksSharedFeed() {
        String database = "test-reset-learning.db";
        getContext().deleteDatabase(database);
        try (Store s = new Store(getContext(), database)) {
            assertEquals(950, s.vocabulary().size());
            assertEquals(1, s.stories().size());
            assertFalse(s.sharedContentDisabled());

            s.addCard("palabra personal", "личное слово", 0, 1);
            s.addCard("frase personal", "личное выражение", 1, 0);
            s.setMastered(s.vocabulary().get(0).id(), true);
            s.addStory("Mi historia", "Hola.", "Привет.", "Личный рассказ");
            assertTrue(s.importRemoteCard("shared-word", "barco", "лодка", 0, 2));
            assertTrue(s.importRemoteStory("shared-story", "Pescador", "Vuelve.", "Возвращается."));
            assertEquals(953, s.vocabulary().size());
            assertEquals(3, s.stories().size());

            s.resetLearningData();
            assertTrue("Shared GitHub feed is disabled only for this database", s.sharedContentDisabled());
            assertEquals(0, s.vocabulary().size());
            assertEquals(0, s.mastered().size());
            assertEquals(0, s.cards(false, 0).size());
            assertEquals(0, s.cards(false, 1).size());
            assertEquals(0, s.cardsForReview(0, 0, 0).size());
            assertEquals(0, s.stories().size());
            assertFalse("Remote cards must not refill an explicitly cleared database",
                    s.importRemoteCard("new-shared-word", "flor", "цветок", 0, 2));
            assertFalse("Remote stories must not refill an explicitly cleared database",
                    s.importRemoteStory("new-shared-story", "Otra", "Hola.", "Привет."));
            s.addCard("nuevo", "новое личное слово", 0, 1);
            s.addStory("Nuevo cuento", "Hola.", "Привет.", "Личный рассказ");
        }

        try (Store s = new Store(getContext(), database)) {
            assertTrue(s.sharedContentDisabled());
            assertEquals("A user can add words again after the reset", 1, s.vocabulary().size());
            assertEquals("A user can add stories again after the reset", 1, s.stories().size());
            assertEquals("nuevo", s.vocabulary().get(0).spanish());
            assertEquals("Nuevo cuento", s.stories().get(0).title());
            // Simulate the next DB migration: no starter words must come back.
            s.onUpgrade(s.getWritableDatabase(), 7, 8);
            assertEquals(1, s.vocabulary().size());
            assertEquals(1, s.stories().size());
            assertTrue(s.sharedContentDisabled());
        }
        getContext().deleteDatabase(database);
    }

    /** Upgrading existing v6 installs must not resurrect cards deleted before the update. */
    public void testUpgradeFromVersionSixKeepsRemovedStarterWordsRemoved() {
        String database = "test-upgrade-v6-no-reseed.db";
        getContext().deleteDatabase(database);
        try (Store s = new Store(getContext(), database)) {
            assertEquals(950, s.vocabulary().size());
            android.database.sqlite.SQLiteDatabase db = s.getWritableDatabase();
            db.execSQL("DELETE FROM cards WHERE id=(SELECT id FROM cards ORDER BY id LIMIT 1)");
            db.execSQL("DROP TABLE app_state");
            db.setVersion(6);
        }
        try (Store s = new Store(getContext(), database)) {
            assertEquals("Schema upgrade must not restore manually deleted cards", 949, s.vocabulary().size());
            assertFalse(s.sharedContentDisabled());
            assertEquals(1, s.stories().size());
        }
        getContext().deleteDatabase(database);
    }

    public void testChatGptStorySaveProducesVerifiedCompletionReceipt() throws Exception {
        String database = "test-chatgpt-story-receipt.db";
        getContext().deleteDatabase(database);
        try (Store s = new Store(getContext(), database)) {
            String result = ChatGptAssistant.saveGeneratedStory(s, "El gato y el coche",
                    "El gato mira un coche. El coche se mueve.",
                    "Кот смотрит на машинку. Машинка едет.");
            ChatGptAssistant.SavedStory saved =
                    ChatGptAssistant.savedStoryFromToolResponse("add_story", result);
            assertNotNull("Only a real database save can produce a popup", saved);
            assertEquals("El gato y el coche", saved.title());
            assertTrue(saved.id() > 0);
            assertEquals("El gato y el coche", s.stories().get(0).title());
            assertEquals(saved.id(), s.stories().get(0).id());

            String duplicate = ChatGptAssistant.saveGeneratedStory(s, "El gato y el coche",
                    "El gato mira un coche. El coche se mueve.",
                    "Кот смотрит на машинку. Машинка едет.");
            assertNull("A duplicate must not announce a NEW story",
                    ChatGptAssistant.savedStoryFromToolResponse("add_story", duplicate));
            assertNull("Prose or another tool must not trigger the story popup",
                    ChatGptAssistant.savedStoryFromToolResponse("add_word", result));
            assertNull(ChatGptAssistant.savedStoryFromToolResponse("add_story",
                    "{\"ok\":false,\"status\":\"added\",\"story_id\":9,\"title\":\"Fake\"}"));
            assertNull(ChatGptAssistant.savedStoryFromToolResponse("add_story",
                    "{\"ok\":true,\"status\":\"added\",\"title\":\"No ID\"}"));
            assertNull(ChatGptAssistant.savedStoryFromToolResponse("add_story", "broken JSON"));
        } finally {
            getContext().deleteDatabase(database);
        }
    }


    /** English starts empty; Spanish database and progress remain untouched. */
    public void testEnglishWorkspaceIndependentFromSpanish() {
        String esDb = "test-es-independent.db", enDb = "test-en-independent.db";
        getContext().deleteDatabase(esDb);
        getContext().deleteDatabase(enDb);
        try {
            try (Store spanish = new Store(getContext(), esDb);
                 Store english = Store.englishForTest(getContext(), enDb)) {
                assertFalse(spanish.isEnglish());
                assertTrue(english.isEnglish());
                assertEquals(950, spanish.vocabulary().size());
                assertEquals(1, spanish.stories().size());
                assertEquals(0, english.vocabulary().size());
                assertEquals(0, english.stories().size());
                assertTrue(english.sharedContentDisabled());
                assertFalse(english.importRemoteCard("remote-es", "hola", "привет", 0, 1));

                english.addCard("cat", "кот", 0, 2);
                english.addCard("take care", "береги себя", 1, 0);
                english.addStory("The cat", "The cat sleeps.", "Кот спит.", "English story");
                assertEquals(2, english.vocabulary().size());
                assertEquals(1, english.stories().size());
                assertEquals(950, spanish.vocabulary().size());
                assertEquals(1, spanish.stories().size());

                ChatGptAssistant assistant = new ChatGptAssistant(getContext());
                try {
                    String en = assistant.buildInstructions(english);
                    assertTrue(en.contains("изучения АНГЛИЙСКОГО языка"));
                    assertTrue(en.contains("cat = кот"));
                    assertTrue(en.contains("take care = береги себя"));
                    assertFalse(en.contains("vos/tenés"));
                    String es = assistant.buildInstructions(spanish);
                    assertTrue(es.contains("Rioplatense Spanish"));
                    assertFalse(es.contains("cat = кот"));
                } finally { assistant.close(); }

                english.resetLearningData();
                assertEquals(0, english.vocabulary().size());
                assertEquals(0, english.stories().size());
                assertEquals(950, spanish.vocabulary().size());
            }
            try (Store english = Store.englishForTest(getContext(), enDb);
                 Store spanish = new Store(getContext(), esDb)) {
                assertEquals(0, english.vocabulary().size());
                assertTrue(english.sharedContentDisabled());
                assertEquals(950, spanish.vocabulary().size());
                assertEquals(1, spanish.stories().size());
            }
        } finally {
            getContext().deleteDatabase(esDb);
            getContext().deleteDatabase(enDb);
        }
    }

}
