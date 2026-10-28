package ru.spanish.learning;

import android.database.sqlite.SQLiteDatabase;
import android.test.AndroidTestCase;

/** Baksik story must be added exactly once only to Mama/Dima ES DBs. */
public final class FamilyStoryTest extends AndroidTestCase {
    private static void clean(String... names) {
        // Cleanup is intentionally done by each test with its own Context.
    }

    public void testFreshProfilesHaveOneTranslatedBaksikStory() {
        String papaDb = "test-baksik-papa.db";
        String mamaDb = "test-baksik-mama.db";
        String dimaDb = "test-baksik-dima.db";
        String englishDb = "test-baksik-english.db";
        for (String n : new String[]{papaDb,mamaDb,dimaDb,englishDb})
            getContext().deleteDatabase(n);
        try (Store papa = Store.forProfileTest(getContext(), papaDb, "papa", false);
             Store mama = Store.forProfileTest(getContext(), mamaDb, "mama", false);
             Store dima = Store.forProfileTest(getContext(), dimaDb, "dima", false);
             Store en = Store.forProfileTest(getContext(), englishDb, "mama", true)) {
            assertEquals(1, papa.stories().size());
            assertFalse(FamilyStory.TITLE.equals(papa.stories().get(0).title()));
            assertEquals(0, en.stories().size());
            assertEquals(1, mama.stories().size());
            assertEquals(1, dima.stories().size());
            Store.Story story = mama.stories().get(0);
            assertEquals(FamilyStory.TITLE, story.title());
            assertEquals(FamilyStory.BODY.strip(), story.body());
            assertEquals(FamilyStory.RUSSIAN.strip(), story.translation());
            assertEquals(181, Sentences.split(story.body()).size());
            assertEquals(Sentences.split(story.body()).size(),
                    Sentences.split(story.translation()).size());
            assertTrue(story.body().contains("Baksik corrió"));
            assertTrue(story.body().contains("Mañana voy a caminar"));
            assertTrue(story.body().contains("¡Mañana, no este sábado!"));
            assertEquals(story.body(), dima.stories().get(0).body());
            assertEquals(700, mama.vocabulary().size());
            assertEquals(700, dima.vocabulary().size());
        } finally {
            for (String n : new String[]{papaDb,mamaDb,dimaDb,englishDb})
                getContext().deleteDatabase(n);
        }
    }

    public void testUpgradeImportsOnceKeepsPersonalStoriesAndDuplicatesAway() {
        String dbName = "test-baksik-upgrade.db";
        getContext().deleteDatabase(dbName);
        try {
            try (Store mama = Store.forProfileTest(getContext(), dbName, "mama", false)) {
                SQLiteDatabase db = mama.getWritableDatabase();
                db.delete("stories", null, null);
                db.delete("app_state", "key=?", new String[]{"family_story_baksik_20261028"});
                mama.addStory("Личная история", "Hola, mamá.", "Привет, мама.", "personal");
                db.setVersion(8);
            }
            try (Store mama = Store.forProfileTest(getContext(), dbName, "mama", false)) {
                assertEquals(2, mama.stories().size());
                boolean userStory = false, bundled = false;
                for (Store.Story story : mama.stories()) {
                    if ("Личная история".equals(story.title())) userStory = true;
                    if (FamilyStory.TITLE.equals(story.title())) bundled = true;
                }
                assertTrue(userStory);
                assertTrue(bundled);
            }
            try (Store mama = Store.forProfileTest(getContext(), dbName, "mama", false)) {
                assertEquals("Repeating open must not duplicate stories", 2, mama.stories().size());
            }
        } finally {
            getContext().deleteDatabase(dbName);
        }
    }

    public void testDeleteDoesNotReinstallOnReopenOrSchemaUpgrade() {
        String dbName = "test-baksik-deleted.db";
        getContext().deleteDatabase(dbName);
        try {
            try (Store dima = Store.forProfileTest(getContext(), dbName, "dima", false)) {
                assertTrue(dima.deleteStory(dima.stories().get(0).id()));
                assertEquals(0, dima.stories().size());
                dima.getWritableDatabase().setVersion(8);
            }
            try (Store dima = Store.forProfileTest(getContext(), dbName, "dima", false)) {
                assertEquals("User deleted this story; upgrade must not restore it",
                        0, dima.stories().size());
            }
        } finally {
            getContext().deleteDatabase(dbName);
        }
    }

    public void testExplicitResetBlocksStoryEvenAfterUpgrade() {
        String dbName = "test-baksik-reset.db";
        getContext().deleteDatabase(dbName);
        try {
            try (Store mama = Store.forProfileTest(getContext(), dbName, "mama", false)) {
                mama.resetLearningData();
                assertEquals(0, mama.stories().size());
                mama.getWritableDatabase().setVersion(8);
            }
            try (Store mama = Store.forProfileTest(getContext(), dbName, "mama", false)) {
                assertEquals(0, mama.stories().size());
                assertEquals(0, mama.vocabulary().size());
            }
        } finally {
            getContext().deleteDatabase(dbName);
        }
    }
}
