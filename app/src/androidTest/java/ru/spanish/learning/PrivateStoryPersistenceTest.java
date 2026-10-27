package ru.spanish.learning;

import android.test.AndroidTestCase;

public final class PrivateStoryPersistenceTest extends AndroidTestCase {
    public void testPersonalStoriesAreIsolatedAndStayDeletedAfterRestart() {
        String firstDb = "test-story-inbox-wife.db";
        String secondDb = "test-story-inbox-husband.db";
        getContext().deleteDatabase(firstDb);
        getContext().deleteDatabase(secondDb);
        String text = "Dos gatos juegan. Después descansan.";
        String ru = "Два кота играют. Потом отдыхают.";
        String wifeId = PrivateStorySync.externalId("wife/private",
                "a1b2c3d4-0000-4000-8000-111111111111", "es", "story1");
        String husbandId = PrivateStorySync.externalId("husband/private",
                "a1b2c3d4-0000-4000-8000-222222222222", "es", "story1");
        try {
            try (Store wife = new Store(getContext(), firstDb);
                 Store husband = new Store(getContext(), secondDb)) {
                assertTrue(wife.importRemoteStory(wifeId, "Личная история жены", text, ru));
                assertFalse(wife.importRemoteStory(wifeId, "Личная история жены", text, ru));
                assertTrue(husband.importRemoteStory(husbandId, "Личная история мужа", text, ru));
                assertFalse("Data from one phone must not appear on the other",
                        husband.stories().stream().anyMatch(s -> "Личная история жены".equals(s.title())));
                long localId = wife.stories().stream()
                        .filter(s -> "Личная история жены".equals(s.title()))
                        .findFirst().orElseThrow().id();
                assertTrue(wife.deleteStory(localId));
                assertFalse("Deleted story must never be restored", wife.importRemoteStory(
                        wifeId, "Личная история жены", text, ru));
            }
            try (Store wife = new Store(getContext(), firstDb)) {
                assertFalse("Tombstone must survive restart", wife.importRemoteStory(
                        wifeId, "Личная история жены", text, ru));
                assertFalse(wife.stories().stream().anyMatch(s ->
                        "Личная история жены".equals(s.title())));
            }
        } finally {
            getContext().deleteDatabase(firstDb);
            getContext().deleteDatabase(secondDb);
        }
    }
}
