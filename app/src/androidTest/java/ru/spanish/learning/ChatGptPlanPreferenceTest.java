package ru.spanish.learning;

import android.test.AndroidTestCase;

public final class ChatGptPlanPreferenceTest extends AndroidTestCase {
    public void testFreePlusChoiceSavedPerInstallation() {
        String old = ChatGptPlanPreference.selected(getContext());
        try {
            ChatGptPlanPreference.select(getContext(), ChatGptPlanPreference.FREE);
            assertEquals("free", ChatGptPlanPreference.selected(getContext()));
            assertEquals(0, ChatGptPlanPreference.selectedIndex(getContext()));
            assertEquals("Бесплатный", ChatGptPlanPreference.label(getContext()));

            ChatGptPlanPreference.select(getContext(), ChatGptPlanPreference.PLUS);
            assertEquals("plus", ChatGptPlanPreference.selected(getContext()));
            assertEquals(1, ChatGptPlanPreference.selectedIndex(getContext()));
            assertTrue(ChatGptPlanPreference.label(getContext()).contains("$20"));
        } finally {
            ChatGptPlanPreference.select(getContext(), old);
        }
    }

    public void testRejectUnknownPlanWithoutChangingSelected() {
        String old = ChatGptPlanPreference.selected(getContext());
        try {
            ChatGptPlanPreference.select(getContext(), "enterprise-secret");
            fail("Unknown tariffs must not be accepted");
        } catch (IllegalArgumentException expected) {
            assertEquals(old, ChatGptPlanPreference.selected(getContext()));
        }
    }

    public void testPreferenceDoesNotCallPaidApiOrSubscriptionSharing() {
        assertEquals("https://chatgpt.com/", ChatGptPlanPreference.officialChatUrl());
        ChatGptPlanPreference.select(getContext(), ChatGptPlanPreference.FREE);
        assertFalse(GptStudyBrokerClient.configured(getContext()));
    }
}
