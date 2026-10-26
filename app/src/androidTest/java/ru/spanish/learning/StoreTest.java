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


    public void testPermanentDeleteRemovesStudyAndMasteredCardsWithoutReseeding() {
        String database = "test-permanent-card-delete.db";
        getContext().deleteDatabase(database);
        try {
            try (Store store = new Store(getContext(), database)) {
                assertEquals(950, store.vocabulary().size());
                long original = store.vocabulary().get(0).id();
                store.setMastered(original, true);
                assertTrue(store.deleteCard(original));
                assertFalse("Already removed card is not deleted twice", store.deleteCard(original));
                assertFalse("Invalid ID must not delete anything", store.deleteCard(-1));
                assertEquals(949, store.vocabulary().size());
                assertEquals(0, store.mastered().size());
                assertEquals(949, store.cards(false, 0).size() + store.cards(false, 1).size());

                store.addCard("borrar definitivamente", "безвозвратно удалить", 0, 1);
                long userId = store.vocabulary().get(store.vocabulary().size() - 1).id();
                assertTrue(store.deleteCard(userId));
                assertEquals(949, store.vocabulary().size());

                assertTrue(store.importRemoteCard("remote-deletion-tombstone",
                        "tarjeta remota", "карточка из ленты", 0, 2));
                long remoteId = store.vocabulary().get(store.vocabulary().size() - 1).id();
                assertTrue(store.deleteCard(remoteId));
                assertFalse("Deleted remote card must not reappear after sync",
                        store.importRemoteCard("remote-deletion-tombstone",
                                "tarjeta remota", "карточка из ленты", 0, 2));
            }
            try (Store reopened = new Store(getContext(), database)) {
                assertEquals("Deleted words must stay gone after reopening", 949,
                        reopened.vocabulary().size());
                assertEquals(0, reopened.mastered().size());
                reopened.onUpgrade(reopened.getWritableDatabase(), 7, 8);
                assertEquals("A future DB upgrade must not restore deleted cards", 949,
                        reopened.vocabulary().size());
            }
        } finally {
            getContext().deleteDatabase(database);
        }
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

    public void testLongPressStoryWordOnlySavesVerifiedMeaningAndPreservesStory() throws Exception {
        String db = "test-story-word.db";
        getContext().deleteDatabase(db);
        try (Store store = new Store(getContext(), db)) {
            int original = store.vocabulary().size();
            String originalStoryBody = store.stories().get(0).body();
            // Shared app image files may outlive earlier tests using the same word.
            new java.io.File(getContext().getFilesDir(),
                    "card-images/" + CardImageLoader.key("Querés") + ".svg").delete();

            // The previous long-press path created actual study cards containing
            // descriptions such as "Словоформа из сказки" instead of a translation.
            // These MUST fail closed at the database boundary.
            for (String bad : new String[]{
                    "Словоформа из сказки. Точный перевод уточняется по контексту.",
                    "Форма глагола querer",
                    "Слово из английской сказки. Точный перевод уточняется по контексту.",
                    "Контекст: ¿Querés tomar un mate?"
            }) {
                try {
                    store.addStoryWord("Querés", bad, 1);
                    fail("Unverified card must not be saved: " + bad);
                } catch (IllegalArgumentException expected) {
                    assertFalse(expected.getMessage().isEmpty());
                }
            }
            assertEquals(original, store.vocabulary().size());
            assertFalse(new java.io.File(getContext().getFilesDir(),
                    "card-images/" + CardImageLoader.key("Querés") + ".svg").isFile());

            assertEquals(Store.StoryWordSave.ADDED,
                    StoryVocabulary.saveVerified(getContext(), store, "Querés", "ты хочешь", 1));
            assertEquals(original + 1, store.vocabulary().size());
            Store.Card saved = null;
            for (Store.Card c : store.vocabulary()) {
                if ("Querés".equals(c.spanish())) { saved = c; break; }
            }
            assertNotNull(saved);
            assertEquals(1, saved.category());
            assertEquals("ты хочешь", saved.meaning());
            assertTrue(new java.io.File(getContext().getFilesDir(),
                    "card-images/" + CardImageLoader.key("Querés") + ".svg").isFile());
            assertEquals(Store.StoryWordSave.ALREADY_STUDYING,
                    StoryVocabulary.existing(store, "querés"));
            assertEquals(original + 1, store.vocabulary().size());

            store.setMastered(saved.id(), true);
            assertEquals(Store.StoryWordSave.RESTORED,
                    StoryVocabulary.existing(store, "QUERÉS"));
            assertEquals(original + 1, store.vocabulary().size());
            boolean restoredWithMeaning = false;
            for (Store.Card restoredCard : store.vocabulary()) {
                if (restoredCard.id() == saved.id()) {
                    assertEquals("ты хочешь", restoredCard.meaning());
                    restoredWithMeaning = true;
                }
            }
            assertTrue(restoredWithMeaning);

            assertEquals(Store.StoryWordSave.ADDED,
                    StoryVocabulary.saveVerified(getContext(), store, "caminábamos",
                            "мы гуляли", 1));
            Store.Card walk = store.vocabulary().get(store.vocabulary().size() - 1);
            assertEquals("caminábamos", walk.spanish());
            assertEquals("мы гуляли", walk.meaning());
            assertEquals(1, walk.category());
            assertEquals(originalStoryBody, store.stories().get(0).body());
        }
        getContext().deleteDatabase(db);
    }

    public void testStoryTranslationProposalNeverAcceptsPlaceholders() throws Exception {
        StoryVocabulary.Proposal good = StoryVocabulary.proposal("querés",
                "{\"ok\":true,\"literal\":\"ты хочешь\",\"idiomatic\":\"\",\"category\":\"verb\"}");
        assertEquals("ты хочешь", good.russian());
        assertEquals(1, good.category());
        assertEquals("verb", good.grammar());

        for (String bad : new String[]{
                "{\"ok\":false,\"literal\":\"\",\"category\":\"other\"}",
                "{\"ok\":true,\"literal\":\"Форма глагола querer\",\"category\":\"verb\"}",
                "{\"ok\":true,\"literal\":\"Перевод уточняется\",\"category\":\"verb\"}",
                "{\"ok\":true,\"literal\":\"ты хочешь\",\"category\":\"unknown\"}",
                "{\"ok\":true,\"literal\":\"\",\"category\":\"verb\"}",
                "not-json"
        }) {
            try {
                StoryVocabulary.proposal("querés", bad);
                fail("Translation proposal must fail closed: " + bad);
            } catch (Exception expected) {
                assertNotNull(expected);
            }
        }
    }

    public void testStoryDeletePersistsAndDoesNotRestoreFromRemoteSync() {
        String name = "test-stories-delete.db";
        getContext().deleteDatabase(name);
        try {
            try (Store store = new Store(getContext(), name)) {
                assertEquals(1, store.stories().size());
                long seedId = store.stories().get(0).id();
                long localId = store.addStory("Prueba", "Hola.", "Привет.", "Личное");
                assertTrue(store.importRemoteStory("remote-story-deleted", "El gato",
                        "El gato duerme.", "Кот спит."));
                long remoteId = store.stories().get(0).id();
                assertTrue(store.deleteStory(seedId));
                assertTrue(store.deleteStory(localId));
                assertTrue(store.deleteStory(remoteId));
                assertFalse(store.deleteStory(localId));
                assertFalse(store.deleteStory(-1));
                assertEquals(0, store.stories().size());
                assertFalse("Deleted remote story is not reimported",
                        store.importRemoteStory("remote-story-deleted", "El gato",
                                "El gato duerme.", "Кот спит."));
                assertFalse(store.sharedContentDisabled());
                assertEquals(950, store.vocabulary().size());
            }
            try (Store store = new Store(getContext(), name)) {
                assertEquals("Deleted starter story must not be reseeded", 0, store.stories().size());
                assertFalse("Remote tombstone persists across database reopen",
                        store.importRemoteStory("remote-story-deleted", "El gato",
                                "El gato duerme.", "Кот спит."));
                assertTrue("Other stories can still be imported",
                        store.importRemoteStory("new-remote-story", "La luna",
                                "La luna brilla.", "Луна светит."));
                assertEquals(1, store.stories().size());
            }
        } finally {
            getContext().deleteDatabase(name);
        }
    }

    public void testEnglishStoryDeleteDoesNotAffectSpanishStories() {
        String es = "test-story-delete-es.db", en = "test-story-delete-en.db";
        getContext().deleteDatabase(es);
        getContext().deleteDatabase(en);
        try {
            try (Store spanish = new Store(getContext(), es);
                 Store english = Store.englishForTest(getContext(), en)) {
                long id = english.addStory("The cat", "The cat sleeps.", "Кот спит.", "Личное");
                assertTrue(english.deleteStory(id));
                assertEquals(0, english.stories().size());
                assertEquals(1, spanish.stories().size());
            }
        } finally {
            getContext().deleteDatabase(es);
            getContext().deleteDatabase(en);
        }
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
            assertTrue(s.importRemoteCard("phrase-1", "tengo ganas de comer", "мне хочется поесть", 1, 0));
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


    /** New phones receive 50 ready-to-speak expressions, not dictionary infinitives. */
    public void testSeedExpressionsAreConversationalOnFreshInstall() {
        String database = "test-conversational-seed.db";
        getContext().deleteDatabase(database);
        try (Store spanish = new Store(getContext(), database)) {
            java.util.List<Store.Card> phrases = spanish.cards(false, 1);
            assertEquals(50, phrases.size());
            boolean foundTengo = false, foundGanas = false, foundAPesar = false;
            for (Store.Card card : phrases) {
                assertFalse("Infinitive starter expression: " + card.spanish(),
                        SpanishPhrasePolicy.startsWithInfinitive(card.spanish()));
                assertFalse("Unfinished 'tengo que' should include an action",
                        "tengo que".equals(card.spanish()));
                if ("tengo razón".equals(card.spanish())) foundTengo = true;
                if ("tengo ganas de comer".equals(card.spanish())) foundGanas = true;
                if ("a pesar de".equals(card.spanish())) foundAPesar = true;
            }
            assertTrue(foundTengo && foundGanas && foundAPesar);
        } finally {
            getContext().deleteDatabase(database);
        }
    }

    /** A phrase's infinitive head is forbidden, but stories and plain verbs remain free. */
    public void testInfinitiveExpressionPolicyOnlyForNewSpanishPhrases() {
        String database = "test-conversational-phrases.db";
        String englishDatabase = "test-english-phrases.db";
        getContext().deleteDatabase(database);
        getContext().deleteDatabase(englishDatabase);
        try {
            try (Store spanish = new Store(getContext(), database);
                 Store english = Store.englishForTest(getContext(), englishDatabase)) {
                int initial = spanish.vocabulary().size();
                // The new starter pack already contains these two spoken forms.
                assertFalse(spanish.addCardIfMissing("tengo razón", "я прав", 1, 0));
                assertFalse(spanish.addCardIfMissing("tengo ganas de comer",
                        "мне хочется поесть", 1, 0));
                spanish.addCard("él tiene razón", "он прав", 1, 0);
                assertTrue(spanish.addCardIfMissing("tenés razón", "ты прав", 1, 0));
                assertTrue(spanish.addCardIfMissing("tengo ganas de dormir",
                        "мне хочется спать", 1, 0));
                assertEquals(initial + 3, spanish.vocabulary().size());

                try {
                    spanish.addCard("tener razón", "быть правым", 1, 0);
                    fail("Infinitive-based study phrase was accepted");
                } catch (IllegalArgumentException expected) {
                    assertTrue(expected.getMessage().contains("разговорная"));
                }
                try {
                    spanish.addCardIfMissing("darse cuenta de", "осознавать", 1, 0);
                    fail("The assistant must not save an infinitive-based expression");
                } catch (IllegalArgumentException expected) { }
                try {
                    spanish.importRemoteCard("bad-phrase", "estar de acuerdo", "быть согласным", 1, 0);
                    fail("Remote sync must not import an infinitive-based phrase");
                } catch (IllegalArgumentException expected) { }
                assertEquals(initial + 3, spanish.vocabulary().size());

                // Individual verb cards are outside the phrase-only restriction.
                spanish.addCard("comer", "есть", 0, 1);
                // Stories may contain infinitives freely.
                spanish.addStory("Sin límites", "Quiero comer. Voy a salir.",
                        "Я хочу есть. Я собираюсь выйти.", "Test");
                assertEquals(2, spanish.stories().size());
                // English study phrases are outside the Spanish-specific rule.
                english.addCard("to be honest", "честно говоря", 1, 0);
                assertEquals(1, english.vocabulary().size());
            }
        } finally {
            getContext().deleteDatabase(database);
            getContext().deleteDatabase(englishDatabase);
        }
    }


}
