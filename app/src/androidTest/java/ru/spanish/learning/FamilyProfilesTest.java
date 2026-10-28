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
            assertEquals(0, mama.vocabulary().size());
            assertEquals(0, mama.stories().size());
            assertEquals(0, dima.vocabulary().size());
            assertEquals(0, dima.stories().size());

            mama.addCard("buen día", "добрый день", 0, 0);
            dima.addCard("buen día", "добрый день", 0, 0);
            assertEquals(1, mama.vocabulary().size());
            assertEquals(1, dima.vocabulary().size());
            assertTrue("Adding to Mama must not alter Papa", papa.vocabulary().size() >= 900);
            mama.setMastered(mama.vocabulary().get(0).id(), true);
            assertTrue(mama.vocabulary().get(0).mastered());
            assertFalse(dima.vocabulary().get(0).mastered());
            mama.deleteCard(mama.vocabulary().get(0).id());
            assertEquals(0, mama.vocabulary().size());
            assertEquals(1, dima.vocabulary().size());
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
                    .put("spanish", "Milei habló hoy. Muchas personas escucharon.")
                    .put("russian", "Милей говорил сегодня. Многие люди слушали.");
            JSONObject feed = new JSONObject().put("version", 1)
                    .put("items", new JSONArray().put(story));
            assertEquals(1, PersonalStorySync.importStories(
                    mama, feed, "family/private", "es").added());
            assertEquals(0, dima.stories().size());
            assertEquals(0, PersonalStorySync.importStories(
                    mama, feed, "family/private", "es").added());
            assertEquals(1, PersonalStorySync.importStories(
                    dima, feed, "family/private", "es").added());
            assertEquals(1, mama.stories().size());
            assertEquals(1, dima.stories().size());

            mama.addCard("mensaje", "сообщение", 0, 2);
            JSONObject snapshotMama = PrivateVocabularySync.snapshot("mama", "es",
                    "device-test", mama.vocabulary());
            JSONObject snapshotDima = PrivateVocabularySync.snapshot("dima", "es",
                    "device-test", dima.vocabulary());
            assertEquals("mama", snapshotMama.getString("profile"));
            assertEquals("dima", snapshotDima.getString("profile"));
            assertEquals(1, snapshotMama.getJSONArray("entries").length());
            assertEquals(0, snapshotDima.getJSONArray("entries").length());
            assertEquals("vocabulary/mama/es-device-test.json",
                    StudyProfile.vocabularyPath("mama", "es", "device-test"));
            assertEquals("vocabulary/dima/es-device-test.json",
                    StudyProfile.vocabularyPath("dima", "es", "device-test"));
        } finally {
            getContext().deleteDatabase(mamaDb);
            getContext().deleteDatabase(dimaDb);
        }
    }
}
