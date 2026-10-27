package ru.spanish.learning;

import android.test.AndroidTestCase;

public class PersistentChatContextTest extends AndroidTestCase {
    public void testNormalAndProjectConversationsAllowed() {
        assertEquals("https://chatgpt.com/c/1234-aaaa",
                PersistentChatContext.validateConversationUrl("https://chatgpt.com/c/1234-aaaa"));
        assertEquals("https://chatgpt.com/g/g-p-123-project/c/abc-def",
                PersistentChatContext.validateConversationUrl("https://chatgpt.com/g/g-p-123-project/c/abc-def"));
    }

    public void testRejectEmptyNewChatAndSharedSnapshots() {
        assertInvalid("https://chatgpt.com/");
        assertInvalid("https://chatgpt.com/share/abc");
        assertInvalid("https://chatgpt.com/g/g-p-project/project");
        assertInvalid("https://chatgpt.com.evil.test/c/abc");
        assertInvalid("http://chatgpt.com/c/abc");
        assertInvalid("https://evil.test@chatgpt.com/c/abc");
        assertInvalid("https://chatgpt.com/c/abc?prompt=secret");
    }

    private static void assertInvalid(String url) {
        try {
            PersistentChatContext.validateConversationUrl(url);
            fail("Should not accept " + url);
        } catch (IllegalArgumentException expected) { }
    }

    public void testPerLanguageAndPerDeviceStyleLocalPreferences() {
        String originalEs = PersistentChatContext.conversationUrl(getContext(), "es");
        String originalEn = PersistentChatContext.conversationUrl(getContext(), "en");
        try {
            PersistentChatContext.link(getContext(), "es", "https://chatgpt.com/c/abc-100");
            PersistentChatContext.link(getContext(), "en", "https://chatgpt.com/c/abc-200");
            assertEquals("https://chatgpt.com/c/abc-100",
                    PersistentChatContext.conversationUrl(getContext(), "es"));
            assertEquals("https://chatgpt.com/c/abc-200",
                    PersistentChatContext.conversationUrl(getContext(), "en"));
            PersistentChatContext.unlink(getContext(), "es");
            assertEquals("https://chatgpt.com/c/abc-200",
                    PersistentChatContext.conversationUrl(getContext(), "en"));
        } finally {
            if (originalEs.isEmpty()) PersistentChatContext.unlink(getContext(), "es");
            else PersistentChatContext.link(getContext(), "es", originalEs);
            if (originalEn.isEmpty()) PersistentChatContext.unlink(getContext(), "en");
            else PersistentChatContext.link(getContext(), "en", originalEn);
        }
    }

    public void testCompactMessageDoesNotDumpVocabulary() {
        String result = PersistentChatContext.followUp("Рассказ о двух котятах", "es",
                "account/private", "1234-5678");
        assertTrue(result.contains("Рассказ о двух котятах"));
        assertTrue(result.contains("vocabulary/es-1234-5678.json"));
        assertTrue(result.contains("study_story_v1"));
        assertFalse(result.contains("МОЙ СЛОВАРЬ, актуальный снимок"));
        assertTrue(result.contains("Если доступа нет"));
    }
}