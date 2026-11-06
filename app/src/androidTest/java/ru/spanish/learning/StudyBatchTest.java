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
            String nounSpanish = StudyProfile.preferenceKey(
                    StudyProfile.PAPA, "es", StudyBatch.preferenceKey(2));
            String adjectiveSpanish = StudyProfile.preferenceKey(
                    StudyProfile.PAPA, "es", StudyBatch.preferenceKey(3));
            assertFalse(papaSpanish.equals(papaEnglish));
            assertFalse(papaSpanish.equals(mamaSpanish));
            assertFalse(papaSpanish.equals(dimaSpanish));
            assertFalse(papaSpanish.equals(nounSpanish));
            assertFalse(papaSpanish.equals(adjectiveSpanish));
        }
    }
}
