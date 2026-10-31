package ru.spanish.learning;

import android.test.AndroidTestCase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

public class PrivateVocabularySyncTest extends AndroidTestCase {
    public void testFullVocabularySnapshotPreservesWordsPhrasesTranslationsAndMastery() throws Exception {
        String esDb = "test-private-vocabulary-es.db";
        String enDb = "test-private-vocabulary-en.db";
        getContext().deleteDatabase(esDb);
        getContext().deleteDatabase(enDb);
        try {
            try (Store es = new Store(getContext(), esDb)) {
                // The snapshot must include everything, not just cards currently in study.
                es.addCard("caminábamos", "мы гуляли", 0, 1);
                List<Store.Card> all = es.vocabulary();
                long addedId = all.get(all.size() - 1).id();
                es.review(addedId, 2, 0);
                all = es.vocabulary();
                JSONObject snapshot = PrivateVocabularySync.snapshot("es", "device-test", all);
                JSONArray entries = snapshot.getJSONArray("entries");
                assertEquals(all.size(), entries.length());
                assertEquals("es", snapshot.getString("language"));
                assertEquals("device-test", snapshot.getString("device_id"));
                assertTrue("Starter vocabulary includes phrases", hasKind(entries, "phrase"));
                JSONObject exact = find(entries, "caminábamos");
                assertNotNull(exact);
                assertEquals("мы гуляли", exact.getString("translation"));
                assertTrue(exact.getBoolean("mastered"));
                assertEquals("word", exact.getString("kind"));
                assertEquals(1, exact.getInt("category"));
                assertEquals(1, exact.getInt("review_count"));
                assertEquals(2, exact.getInt("last_result"));
                assertTrue(exact.has("next_due"));

                // Changing mastery and deleting cards updates the next full snapshot;
                // deleted entries must not be carried forward by stale incremental state.
                es.setMastered(addedId, false);
                assertFalse(find(PrivateVocabularySync.snapshot("es", "device-test",
                        es.vocabulary()).getJSONArray("entries"), "caminábamos")
                        .getBoolean("mastered"));
                assertTrue(es.deleteCard(addedId));
                JSONArray after = PrivateVocabularySync.snapshot("es", "device-test",
                        es.vocabulary()).getJSONArray("entries");
                assertEquals(entries.length() - 1, after.length());
                assertNull(find(after, "caminábamos"));
            }

            try (Store en = Store.englishForTest(getContext(), enDb)) {
                en.addCard("kitten", "котёнок", 0, 2);
                en.addCard("take care of", "заботиться о", 1, 0);
                JSONObject snapshot = PrivateVocabularySync.snapshot("en", "device-test", en.vocabulary());
                JSONArray entries = snapshot.getJSONArray("entries");
                assertEquals(2, entries.length());
                assertEquals("en", snapshot.getString("language"));
                assertNotNull(find(entries, "kitten"));
                assertNotNull(find(entries, "take care of"));
                assertEquals("phrase", find(entries, "take care of").getString("kind"));
                assertNull(find(entries, "caminábamos"));
            }
        } finally {
            getContext().deleteDatabase(esDb);
            getContext().deleteDatabase(enDb);
        }
    }

    public void testSyncReconcilesMasteryAndDeletionPerProfile() throws Exception {
        Store.SyncEntry seed = new Store.SyncEntry(
                "¿Qué hacés?", "Что делаешь?", 1, 0, false, 0, 0, 0, 0, "seed", false);
        Store.SyncEntry learned = new Store.SyncEntry(
                "¿Qué hacés?", "Что делаешь?", 1, 0, true, 2, 3, 0, 200, "phone-a", false);
        Store.SyncEntry deleted = new Store.SyncEntry(
                "¿Qué hacés?", "", 1, 0, false, 0, 0, 0, 300, "phone-b", true);
        java.util.List<Store.SyncEntry> merged = VocabularyReconcile.merge(
                java.util.List.of(seed, learned), java.util.List.of(deleted));
        assertEquals(1,merged.size());
        assertTrue(merged.get(0).deleted());
        JSONObject data = VocabularyReconcile.serialize("papa","es",merged);
        assertEquals("papa",data.getString("profile"));
        assertEquals(1,VocabularyReconcile.parse(data,"papa","es").size());
        try {
            VocabularyReconcile.parse(data,"mama","es");
            fail("Cross-profile vocabulary must be rejected");
        } catch (IllegalArgumentException expected) { }
    }

    public void testCloudMasteryIsAppliedWithoutDuplicateCard() throws Exception {
        String db = "test-vocabulary-two-way.db";
        getContext().deleteDatabase(db);
        try(Store store = Store.forProfileTest(getContext(),db,"mama",false)) {
            int baseline = store.vocabulary().size();
            Store.SyncEntry cloud = new Store.SyncEntry("Te digo algo", "Я тебе кое-что скажу",
                    1, 0, true, 2, 4, 0, 1000000, "cloud-test", false);
            assertTrue(store.applySyncedVocabulary(java.util.List.of(cloud)));
            assertEquals(baseline+1,store.vocabulary().size());
            assertFalse(store.applySyncedVocabulary(java.util.List.of(cloud)));
            Store.SyncEntry laterDeletion = new Store.SyncEntry("Te digo algo", "",1,0,
                    false,0,0,0,2000000,"cloud-test",true);
            assertTrue(store.applySyncedVocabulary(java.util.List.of(laterDeletion)));
            assertEquals(baseline,store.vocabulary().size());
            assertFalse(store.applySyncedVocabulary(java.util.List.of(cloud)));
            assertEquals(baseline,store.vocabulary().size());
        } finally { getContext().deleteDatabase(db); }
    }

    private static boolean hasKind(JSONArray entries, String kind) throws Exception {
        for (int i = 0; i < entries.length(); i++)
            if (kind.equals(entries.getJSONObject(i).getString("kind"))) return true;
        return false;
    }

    private static JSONObject find(JSONArray entries, String word) throws Exception {
        for (int i = 0; i < entries.length(); i++) {
            JSONObject item = entries.getJSONObject(i);
            if (word.equals(item.getString("text"))) return item;
        }
        return null;
    }
}
