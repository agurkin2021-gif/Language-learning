package ru.spanish.learning;
import android.test.AndroidTestCase;
import org.json.JSONArray;
import org.json.JSONObject;

public final class PimenProfileTest extends AndroidTestCase {
    public void testPaths() {
        assertEquals(3, StudyProfile.IDS.length);
        assertEquals(3, StudyProfile.LABELS.length);
        for (String id : StudyProfile.IDS) assertFalse("pimen".equals(id));
        for (String label : StudyProfile.LABELS) assertFalse("Пимен".equals(label));
        assertEquals("pimen", StudyProfile.normalized("pimen"));
        assertEquals("Пимен", StudyProfile.name("pimen"));
        assertEquals("learning_pimen.db", StudyProfile.databaseName("pimen", false));
        assertEquals("learning_en_pimen.db", StudyProfile.databaseName("pimen", true));
        assertEquals("pimen_en_screen", StudyProfile.preferenceKey("pimen", "en", "screen"));
        assertEquals("card-images-en-pimen", StudyProfile.imageDirectory("pimen", true));
        assertEquals("learning.db", StudyProfile.databaseName("papa", false));
    }

    public void testProfileIsolation() throws Exception {
        String esName = "test-pimen-es.db";
        String enName = "test-pimen-en.db";
        getContext().deleteDatabase(esName);
        getContext().deleteDatabase(enName);
        try (Store es = Store.forProfileTest(getContext(), esName, "pimen", false);
             Store en = Store.forProfileTest(getContext(), enName, "pimen", true)) {
            assertEquals(700, es.vocabulary().size());
            assertEquals(0, en.vocabulary().size());
            es.addCard("hago caso", "слушаюсь", 1, 0);
            assertEquals(701, es.vocabulary().size());
            assertEquals(0, en.vocabulary().size());
            JSONObject story = new JSONObject().put("type", "story")
                    .put("profile", "pimen").put("language", "es")
                    .put("id", "pimen-test").put("title", "Mi historia")
                    .put("spanish", "Hola. Chau.")
                    .put("russian", "Привет. Пока.");
            JSONObject feed = new JSONObject().put("version", 1)
                    .put("items", new JSONArray().put(story));
            assertEquals(1, PersonalStorySync.importStories(es, feed, "public", "es").added());
            assertEquals(0, PersonalStorySync.importStories(en, feed, "public", "en").added());
            JSONObject archive = VocabularyReconcile.serialize("pimen", "es", es.vocabularyForSync());
            assertEquals("pimen", archive.getString("profile"));
            try {
                VocabularyReconcile.parse(archive, "papa", "es");
                fail("Cross-profile archive accepted");
            } catch (IllegalArgumentException expected) { }
        } finally {
            getContext().deleteDatabase(esName);
            getContext().deleteDatabase(enName);
        }
    }
}
