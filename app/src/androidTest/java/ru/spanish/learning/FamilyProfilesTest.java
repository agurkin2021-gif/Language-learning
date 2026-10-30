package ru.spanish.learning;

import android.test.AndroidTestCase;
import org.json.JSONArray;
import org.json.JSONObject;

/** Isolation tests use disposable DBs; never reset the user's existing databases. */
public final class FamilyProfilesTest extends AndroidTestCase {
    public void testPapaKeepsLegacyPathsAndNewProfilesAreInitiallyEmpty() throws Exception {
        assertEquals("learning.db", StudyProfile.databaseName(StudyProfile.PAPA, false));
        assertEquals("learning_en.db", StudyProfile.databaseName(StudyProfile.PAPA, true));
        assertEquals("history", StudyProfile.preferenceKey("papa", "es", "history"));
        assertEquals("screen", StudyProfile.preferenceKey("papa", "es", "screen"));
        assertEquals("learning_mama.db", StudyProfile.databaseName("mama", false));
        assertEquals("learning_en_dima.db", StudyProfile.databaseName("dima", true));
        assertEquals("mama_screen", StudyProfile.preferenceKey("mama", "es", "screen"));
        assertEquals("dima_en_screen", StudyProfile.preferenceKey("dima", "en", "screen"));
        assertEquals("card-images", StudyProfile.imageDirectory("papa", false));
        assertEquals("card-images-en-mama", StudyProfile.imageDirectory("mama", true));
        assertEquals("content/stories-es.json", StudyProfile.storyFeedPath("papa", "es"));
        assertEquals("content/mama/stories-es.json", StudyProfile.storyFeedPath("mama", "es"));
        assertEquals("content/dima/stories-en.json", StudyProfile.storyFeedPath("dima", "en"));

        String papaDb = "test-family-papa.db";
        String mamaDb = "test-family-mama.db";
        String dimaDb = "test-family-dima.db";
        for (String name : new String[]{papaDb, mamaDb, dimaDb})
            getContext().deleteDatabase(name);
        try (Store papa = Store.forProfileTest(getContext(), papaDb, "papa", false);
             Store mama = Store.forProfileTest(getContext(), mamaDb, "mama", false);
             Store dima = Store.forProfileTest(getContext(), dimaDb, "dima", false)) {
            assertTrue("Papa legacy starter words remain", papa.vocabulary().size() >= 900);
            assertEquals(700, mama.vocabulary().size());
            assertEquals(183, mama.cards(false, 0, 1).size());
            assertEquals(406, mama.cards(false, 0, 2).size());
            assertEquals(111, mama.cards(false, 0, 3).size());
            assertEquals(0, mama.mastered().size());
            assertEquals(1, mama.stories().size());
            assertEquals(1000, dima.vocabulary().size());
            assertEquals(0, dima.mastered().size());
            assertEquals(700, BeginnerCards.size());
            assertEquals(1, dima.stories().size());

            mama.addCard("buen día", "добрый день", 0, 2);
            dima.addCard("buen día", "добрый день", 0, 2);
            assertEquals(701, mama.vocabulary().size());
            assertEquals(1001, dima.vocabulary().size());
            assertTrue("Adding to Mama must not alter Papa", papa.vocabulary().size() >= 900);
            long mamaAdded = mama.vocabulary().get(700).id();
            mama.setMastered(mamaAdded, true);
            assertTrue(mama.vocabulary().get(700).mastered());
            assertFalse(dima.vocabulary().get(1000).mastered());
            mama.deleteCard(mamaAdded);
            assertEquals(700, mama.vocabulary().size());
            assertEquals(1001, dima.vocabulary().size());
        } finally {
            for (String name : new String[]{papaDb, mamaDb, dimaDb})
                getContext().deleteDatabase(name);
        }
    }

    public void testPrivateStoryImportAndSeparateGitHubSnapshots() throws Exception {
        String mamaDb = "test-family-story-mama.db";
        String dimaDb = "test-family-story-dima.db";
        getContext().deleteDatabase(mamaDb);
        getContext().deleteDatabase(dimaDb);
        try (Store mama = Store.forProfileTest(getContext(), mamaDb, "mama", false);
             Store dima = Store.forProfileTest(getContext(), dimaDb, "dima", false)) {
            assertTrue(mama.sharedContentDisabled());
            assertFalse("Empty Mama may still import *private* stories", mama.personalStoriesDisabled());
            assertFalse(dima.personalStoriesDisabled());
            JSONObject story = new JSONObject().put("type", "story")
                    .put("id", "new-story").put("title", "Las noticias")
                    .put("profile", "mama").put("language", "es")
                    .put("spanish", "Milei habló hoy. Muchas personas escucharon.")
                    .put("russian", "Милей говорил сегодня. Многие люди слушали.");
            JSONObject feed = new JSONObject().put("version", 1)
                    .put("items", new JSONArray().put(story));
            assertEquals(1, PersonalStorySync.importStories(
                    mama, feed, "family/private", "es").added());
            assertEquals(1, dima.stories().size());
            assertEquals(0, PersonalStorySync.importStories(
                    mama, feed, "family/private", "es").added());
            story.put("profile", "dima");
            assertEquals(1, PersonalStorySync.importStories(
                    dima, feed, "family/private", "es").added());
            assertEquals(2, mama.stories().size());
            assertEquals(2, dima.stories().size());

            mama.addCard("mensaje", "сообщение", 0, 2);
            JSONObject snapshotMama = PrivateVocabularySync.snapshot("mama", "es",
                    "device-test", mama.vocabulary());
            JSONObject snapshotDima = PrivateVocabularySync.snapshot("dima", "es",
                    "device-test", dima.vocabulary());
            assertEquals("mama", snapshotMama.getString("profile"));
            assertEquals("dima", snapshotDima.getString("profile"));
            assertEquals(701, snapshotMama.getJSONArray("entries").length());
            assertEquals(1000, snapshotDima.getJSONArray("entries").length());
            assertEquals("vocabulary/mama/es-device-test.json",
                    StudyProfile.vocabularyPath("mama", "es", "device-test"));
            assertEquals("vocabulary/dima/es-device-test.json",
                    StudyProfile.vocabularyPath("dima", "es", "device-test"));
        } finally {
            getContext().deleteDatabase(mamaDb);
            getContext().deleteDatabase(dimaDb);
        }
    }

    /** Existing version-7 Mama/Dima databases receive exactly one starter pack. */
    public void testUpgradeSeedsExistingFamilyProfileWithoutOverwritingManualCards() {
        String dbName = "test-family-a01-upgrade.db";
        getContext().deleteDatabase(dbName);
        try {
            try (Store mama = Store.forProfileTest(getContext(), dbName, "mama", false)) {
                assertEquals(700, mama.vocabulary().size());
                // Simulate an earlier v7 installation which was empty except for
                // a personal, already mastered word. Keep its ID and translation.
                android.database.sqlite.SQLiteDatabase db = mama.getWritableDatabase();
                db.delete("cards", null, null);
                db.delete("app_state", "key=?", new String[]{"beginner_700_seeded"});
                mama.addCard("mi palabra especial", "моё особенное слово", 0, 2);
                long id = mama.vocabulary().get(0).id();
                mama.setMastered(id, true);
                db.setVersion(7);
            }
            try (Store mama = Store.forProfileTest(getContext(), dbName, "mama", false)) {
                assertEquals(701, mama.vocabulary().size());
                assertEquals(1, mama.mastered().size());
                assertEquals("mi palabra especial", mama.mastered().get(0).spanish());
                assertEquals("моё особенное слово", mama.mastered().get(0).meaning());
            }
            try (Store mama = Store.forProfileTest(getContext(), dbName, "mama", false)) {
                assertEquals("Reopening must not insert duplicates", 701, mama.vocabulary().size());
            }
        } finally {
            getContext().deleteDatabase(dbName);
        }
    }

    public void testFamilyResetAndDeletionNeverReinsertStarterWords() {
        String resetName = "test-family-a01-reset.db";
        String deleteName = "test-family-a01-delete.db";
        getContext().deleteDatabase(resetName);
        getContext().deleteDatabase(deleteName);
        try {
            try (Store dima = Store.forProfileTest(getContext(), deleteName, "dima", false)) {
                assertEquals(1000, dima.vocabulary().size());
                assertTrue(dima.deleteCard(dima.vocabulary().get(0).id()));
                assertEquals(999, dima.vocabulary().size());
            }
            try (Store dima = Store.forProfileTest(getContext(), deleteName, "dima", false)) {
                assertEquals("Deleted starter word stays removed", 999, dima.vocabulary().size());
            }
            try (Store mama = Store.forProfileTest(getContext(), resetName, "mama", false)) {
                mama.resetLearningData();
                assertEquals(0, mama.vocabulary().size());
                // Even an accidental version-7->8 migration must respect reset.
                mama.getWritableDatabase().setVersion(7);
            }
            try (Store mama = Store.forProfileTest(getContext(), resetName, "mama", false)) {
                assertEquals("Explicit reset must not reseed", 0, mama.vocabulary().size());
            }
        } finally {
            getContext().deleteDatabase(resetName);
            getContext().deleteDatabase(deleteName);
        }
    }
}
