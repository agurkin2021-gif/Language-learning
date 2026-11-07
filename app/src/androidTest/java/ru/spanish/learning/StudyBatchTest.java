package ru.spanish.learning;

import android.test.AndroidTestCase;

import java.util.List;

/** Real SQLite verification of the refill-on-mastered workflow. */
public final class StudyBatchTest extends AndroidTestCase {
    private static final String TEST_DB = "test-study-batch-limits.db";

    @Override protected void setUp() throws Exception {
        super.setUp();
        getContext().deleteDatabase(TEST_DB);
    }

    @Override protected void tearDown() throws Exception {
        getContext().deleteDatabase(TEST_DB);
        super.tearDown();
    }

    public void testActiveSetRefillsFromUnmasteredPoolAfterMasteryAndDeletion() {
        try (Store store = Store.forProfileTest(getContext(), TEST_DB, StudyProfile.MAMA, true)) {
            String[] words = {"estudiar ventana alfa", "estudiar ventana beta",
                    "estudiar ventana gamma", "estudiar ventana delta",
                    "estudiar ventana epsilon"};
            for (String word : words) store.addCard(word, "перевод " + word, 0, 1);

            List<Store.Card> initial = StudyBatch.active(store.cards(false, 0, 1), 3);
            assertEquals(3, initial.size());
            assertEquals(words[0], initial.get(0).spanish());
            assertEquals(words[2], initial.get(2).spanish());

            store.review(initial.get(1).id(), 2, 0); // ✓ archives beta
            List<Store.Card> afterKnown = StudyBatch.active(store.cards(false, 0, 1), 3);
            assertEquals(3, afterKnown.size());
            assertEquals(words[0], afterKnown.get(0).spanish());
            assertEquals(words[2], afterKnown.get(1).spanish());
            assertEquals(words[3], afterKnown.get(2).spanish());
            assertTrue(store.mastered().stream().anyMatch(c -> words[1].equals(c.spanish())));
            assertEquals(1, store.learnedWordCount());

            assertTrue(store.deleteCard(afterKnown.get(0).id()));
            List<Store.Card> afterDelete = StudyBatch.active(store.cards(false, 0, 1), 3);
            assertEquals(3, afterDelete.size());
            assertEquals(words[2], afterDelete.get(0).spanish());
            assertEquals(words[4], afterDelete.get(2).spanish());
            assertEquals(2, StudyBatch.active(store.cards(false, 0, 1), 2).size());

            // All remaining words become accessible by removing the active cap.
            assertEquals(3, StudyBatch.active(store.cards(false, 0, 1), 0).size());
            store.review(afterDelete.get(0).id(), 2, 0);
            store.review(afterDelete.get(1).id(), 2, 0);
            store.review(afterDelete.get(2).id(), 2, 0);
            assertTrue(StudyBatch.active(store.cards(false, 0, 1), 3).isEmpty());
            assertEquals(4, store.learnedWordCount());
        }
    }

    public void testPhraseWindowAutoRefillsAndSeparatelyPersistsLimit() {
        try (Store store = Store.forProfileTest(getContext(), TEST_DB, StudyProfile.MAMA, true)) {
            String[] phrases = {
                    "Phrase batch first to learn", "Phrase batch second to learn",
                    "Phrase batch third to learn", "Phrase batch fourth to learn"
            };
            for (String phrase : phrases)
                store.addCard(phrase, "meaning " + phrase, 1, 0);
            String phraseKey = StudyBatch.preferenceKey(1, 0);
            assertFalse(phraseKey.equals(StudyBatch.preferenceKey(0, 0)));
            assertFalse(phraseKey.equals(StudyBatch.preferenceKey(0, 1)));

            List<Store.Card> current = StudyBatch.active(store.cards(false, 1, 0), 2);
            assertEquals(2, current.size());
            assertEquals(phrases[0], current.get(0).spanish());
            assertEquals(phrases[1], current.get(1).spanish());
            store.review(current.get(0).id(), 2, 0); // ✓ archive phrase
            List<Store.Card> next = StudyBatch.active(store.cards(false, 1, 0), 2);
            assertEquals(2, next.size());
            assertEquals(phrases[1], next.get(0).spanish());
            assertEquals(phrases[2], next.get(1).spanish());
            assertTrue(store.mastered().stream().anyMatch(c -> phrases[0].equals(c.spanish())));

            store.review(next.get(0).id(), 2, 0);
            store.review(next.get(1).id(), 2, 0);
            List<Store.Card> last = StudyBatch.active(store.cards(false, 1, 0), 2);
            assertEquals(1, last.size());
            assertEquals(phrases[3], last.get(0).spanish());
            assertEquals(1, StudyBatch.active(store.cards(false, 1, 0), 0).size());
            assertEquals(0, store.cards(false, 0, 0).size());
        }
    }

    public void testPartOfSpeechFiltersAndPreferencesAreIndependent() {
        try (Store store = Store.forProfileTest(getContext(), TEST_DB, StudyProfile.DIMA, true)) {
            store.addCard("study batch verb one", "v1", 0, 1);
            store.addCard("study batch verb two", "v2", 0, 1);
            store.addCard("study batch noun", "n1", 0, 2);
            store.addCard("study batch adjective", "a1", 0, 3);

            assertEquals(1, StudyBatch.active(store.cards(false, 0, 1), 1).size());
            assertEquals(2, StudyBatch.active(store.cards(false, 0, 1), 2).size());
            assertEquals(1, StudyBatch.active(store.cards(false, 0, 2), 10).size());
            assertEquals(1, StudyBatch.active(store.cards(false, 0, 3), 10).size());
            assertEquals(4, StudyBatch.active(store.cards(false, 0, 0), 0).size());

            String papaSpanish = StudyProfile.preferenceKey(
                    StudyProfile.PAPA, "es", StudyBatch.preferenceKey(1));
            String papaEnglish = StudyProfile.preferenceKey(
                    StudyProfile.PAPA, "en", StudyBatch.preferenceKey(1));
            String mamaSpanish = StudyProfile.preferenceKey(
                    StudyProfile.MAMA, "es", StudyBatch.preferenceKey(1));
            String dimaSpanish = StudyProfile.preferenceKey(
                    StudyProfile.DIMA, "es", StudyBatch.preferenceKey(1));
            String phraseSpanish = StudyProfile.preferenceKey(
                    StudyProfile.PAPA, "es", StudyBatch.preferenceKey(1, 0));
            String phraseEnglish = StudyProfile.preferenceKey(
                    StudyProfile.PAPA, "en", StudyBatch.preferenceKey(1, 0));
            String phraseMama = StudyProfile.preferenceKey(
                    StudyProfile.MAMA, "es", StudyBatch.preferenceKey(1, 0));
            String nounSpanish = StudyProfile.preferenceKey(
                    StudyProfile.PAPA, "es", StudyBatch.preferenceKey(2));
            String adjectiveSpanish = StudyProfile.preferenceKey(
                    StudyProfile.PAPA, "es", StudyBatch.preferenceKey(3));
            assertFalse(papaSpanish.equals(papaEnglish));
            assertFalse(papaSpanish.equals(mamaSpanish));
            assertFalse(papaSpanish.equals(dimaSpanish));
            assertFalse(papaSpanish.equals(phraseSpanish));
            assertFalse(phraseSpanish.equals(phraseEnglish));
            assertFalse(phraseSpanish.equals(phraseMama));
            assertFalse(papaSpanish.equals(nounSpanish));
            assertFalse(papaSpanish.equals(adjectiveSpanish));
        }
    }
}
