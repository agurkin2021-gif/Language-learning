package ru.spanish.learning;

import org.junit.Test;
import static org.junit.Assert.*;

public final class PrivateStorySyncTest {
    @Test public void twoPhonesAlwaysHaveDifferentInboxPaths() {
        String a = "a1b2c3d4-0000-4000-8000-111111111111";
        String b = "a1b2c3d4-0000-4000-8000-222222222222";
        assertEquals("stories/" + a + "/es.json", PrivateStorySync.inboxPath(a, "es"));
        assertEquals("stories/" + a + "/en.json", PrivateStorySync.inboxPath(a, "en"));
        assertNotEquals(PrivateStorySync.inboxPath(a, "es"), PrivateStorySync.inboxPath(b, "es"));
        assertNotEquals(PrivateStorySync.inboxPath(a, "es"), PrivateStorySync.inboxPath(a, "en"));
    }
    @Test public void storyIdsAreScopedByRepositoryDeviceAndLanguage() {
        String device = "a1b2c3d4-0000-4000-8000-111111111111";
        String one = PrivateStorySync.externalId("alice/private", device, "es", "story-1");
        assertNotEquals(one, PrivateStorySync.externalId("bob/private", device, "es", "story-1"));
        assertNotEquals(one, PrivateStorySync.externalId("alice/private", device, "en", "story-1"));
        assertNotEquals(one, PrivateStorySync.externalId("alice/private", "different", "es", "story-1"));
        assertEquals(one, PrivateStorySync.externalId("ALICE/PRIVATE", device, "es", "story-1"));
    }
    @Test(expected = IllegalArgumentException.class)
    public void rejectPathTraversal() {
        PrivateStorySync.inboxPath("../../token", "es");
    }
    @Test(expected = IllegalArgumentException.class)
    public void rejectOtherLanguages() {
        PrivateStorySync.inboxPath("a1b2c3d4-0000-4000-8000-111111111111", "ru");
    }
}
