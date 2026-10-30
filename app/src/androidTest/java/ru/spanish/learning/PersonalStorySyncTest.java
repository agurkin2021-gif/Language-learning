package ru.spanish.learning;

import android.test.AndroidTestCase;
import org.json.JSONArray;
import org.json.JSONObject;

public class PersonalStorySyncTest extends AndroidTestCase {
    private static JSONObject feed(String id, String title) throws Exception {
        JSONObject story = new JSONObject();
        story.put("type", "story");
        story.put("profile", "papa");
        story.put("language", "es");
        story.put("id", id);
        story.put("title", title);
        story.put("spanish", "Dos gatitos juegan. Después vuelven a casa.");
        story.put("russian", "Два котёнка играют. Потом они возвращаются домой.");
        return new JSONObject().put("version", 1)
                .put("items", new JSONArray().put(story));
    }

    public void testSeparateAccountsLanguagesAndNoDuplicateImports() throws Exception {
        String esName = "test-personal-story-es.db";
        String enName = "test-personal-story-en.db";
        getContext().deleteDatabase(esName);
        getContext().deleteDatabase(enName);
        try (Store es = new Store(getContext(), esName);
             Store en = Store.englishForTest(getContext(), enName)) {
            int esBefore = es.stories().size();
            int enBefore = en.stories().size();
            JSONObject first = feed("story-1", "Для первого профиля");
            JSONObject second = feed("story-1", "Для второго профиля");

            PersonalStorySync.Result firstResult =
                    PersonalStorySync.importStories(es, first, "wife/private-es", "es");
            assertEquals(1, firstResult.added());
            assertEquals(0, firstResult.rejected());
            assertEquals(0, PersonalStorySync.importStories(
                    es, first, "wife/private-es", "es").added());

            // Same external story id from a different profile must not collide.
            assertEquals(1, PersonalStorySync.importStories(
                    es, second, "husband/private-es", "es").added());
            assertEquals(esBefore + 2, es.stories().size());

            // EN disables the PUBLIC feed by default, but a personal EN story is valid.
            assertTrue(en.sharedContentDisabled());
            assertFalse(en.personalStoriesDisabled());
            JSONObject englishFeed = feed("story-1", "English separate");
            englishFeed.getJSONArray("items").getJSONObject(0).put("language", "en");
            assertEquals(1, PersonalStorySync.importStories(
                    en, englishFeed, "wife/private-es", "en").added());
            assertEquals(enBefore + 1, en.stories().size());

            JSONObject invalid = feed("invalid-1", "Invalid card injection");
            invalid.getJSONArray("items").getJSONObject(0).put("language", "en");
            invalid.getJSONArray("items").getJSONObject(0).put("type", "word");
            assertEquals(1, PersonalStorySync.importStories(
                    en, invalid, "wife/private-es", "en").rejected());
            assertEquals(enBefore + 1, en.stories().size());
        } finally {
            getContext().deleteDatabase(esName);
            getContext().deleteDatabase(enName);
        }
    }


    public void testCanonicalFeedSeparatesProfilesAndLanguages() throws Exception {
        String papaDb = "test-one-feed-papa.db";
        String mamaDb = "test-one-feed-mama.db";
        String dimaDb = "test-one-feed-dima.db";
        for (String db : new String[]{papaDb, mamaDb, dimaDb}) getContext().deleteDatabase(db);
        try (Store papa = Store.forProfileTest(getContext(), papaDb, "papa", false);
             Store mama = Store.forProfileTest(getContext(), mamaDb, "mama", false);
             Store dima = Store.forProfileTest(getContext(), dimaDb, "dima", false)) {
            JSONObject feed = new JSONObject().put("version", 1).put("items", new JSONArray());
            for (String profile : new String[]{"papa", "mama", "dima"}) {
                JSONObject item = new JSONObject().put("id", "one-" + profile)
                        .put("type", "story").put("profile", profile).put("language", "es")
                        .put("title", "Story " + profile).put("spanish", "Hola, amigo.")
                        .put("russian", "Привет, друг.");
                feed.getJSONArray("items").put(item);
            }
            feed.getJSONArray("items").put(new JSONObject().put("id", "english")
                    .put("type", "story").put("profile", "papa").put("language", "en")
                    .put("title", "English story").put("spanish", "Hello.")
                    .put("russian", "Привет."));
            assertEquals(1, PersonalStorySync.importStories(papa, feed, "public", "es").added());
            assertEquals(1, PersonalStorySync.importStories(mama, feed, "public", "es").added());
            assertEquals(1, PersonalStorySync.importStories(dima, feed, "public", "es").added());
            assertEquals(0, PersonalStorySync.importStories(papa, feed, "public", "es").added());
            assertEquals(0, PersonalStorySync.importStories(papa, feed, "public", "en").added());
            JSONObject unknown = new JSONObject().put("version", 1).put("items", new JSONArray()
                    .put(new JSONObject().put("id", "bad").put("type", "story")
                            .put("profile", "unexpected").put("language", "es")
                            .put("title", "Bad").put("spanish", "Hola.")
                            .put("russian", "Привет.")));
            assertEquals(1, PersonalStorySync.importStories(papa, unknown, "public", "es").rejected());
        } finally {
            for (String db : new String[]{papaDb, mamaDb, dimaDb}) getContext().deleteDatabase(db);
        }
    }

    public void testResetBlocksPersonalRestoration() throws Exception {
        String esName = "test-reset-personal-es.db";
        String enName = "test-reset-personal-en.db";
        getContext().deleteDatabase(esName);
        getContext().deleteDatabase(enName);
        try (Store es = new Store(getContext(), esName);
             Store en = Store.englishForTest(getContext(), enName)) {
            es.resetLearningData();
            en.resetLearningData();
            assertTrue(es.personalStoriesDisabled());
            assertTrue(en.personalStoriesDisabled());
            assertEquals(0, PersonalStorySync.importStories(
                    es, feed("story-after-reset", "Should not return"), "wife/private", "es").added());
            JSONObject englishReset = feed("story-after-reset", "Should not return");
            englishReset.getJSONArray("items").getJSONObject(0).put("language", "en");
            assertEquals(0, PersonalStorySync.importStories(
                    en, englishReset, "wife/private", "en").added());
            assertTrue(es.stories().isEmpty());
            assertTrue(en.stories().isEmpty());
        } finally {
            getContext().deleteDatabase(esName);
            getContext().deleteDatabase(enName);
        }
    }
}
