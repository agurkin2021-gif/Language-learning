package ru.spanish.learning;

import android.test.AndroidTestCase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Fail-closed regression tests for every offline Russian translation review. */
public final class OfflineStoryTranslationAuditTest extends AndroidTestCase {
    private static final String SOURCE =
            "Tengo ganas de comer. No puedo salir. Vos venís mañana.";
    private static final String DRAFT =
            "Я хочу поесть. Я не могу выйти. Ты придёшь завтра.";
    private static final String LITERAL =
            "Я имею желание поесть (мне хочется поесть). Я не могу выйти. Ты приходишь завтра.";

    public void testThreeReviewsAreRequiredAndLiteralMeaningWins() throws Exception {
        List<Integer> passes = new ArrayList<>();
        List<Integer> stages = new ArrayList<>();
        String result = OfflineStoryTranslationAudit.review("es", SOURCE, DRAFT,
                (pass, instructions, json) -> {
                    passes.add(pass);
                    assertTrue(instructions.contains("АУДИТ " + pass + "/3"));
                    assertTrue(json.contains("Tengo ganas de comer"));
                    if (pass == 1) {
                        assertTrue(json.contains("unverified_russian_draft"));
                        return "{\"ok\":true,\"translation\":\""
                                + LITERAL + "\",\"reason\":\"\"}";
                    }
                    assertTrue(json.contains("audited_russian_translation"));
                    return "{\"ok\":true,\"reason\":\"\"}";
                }, stages::add);
        assertEquals(LITERAL, result);
        assertEquals(Arrays.asList(1, 2, 3), passes);
        assertEquals(Arrays.asList(1, 2, 3), stages);
    }

    public void testGrammarAuditMayVetoBeforeFinalStage() throws Exception {
        List<Integer> passes = new ArrayList<>();
        try {
            OfflineStoryTranslationAudit.review("es", SOURCE, DRAFT,
                    (pass, rules, json) -> {
                        passes.add(pass);
                        if (pass == 1)
                            return "{\"ok\":true,\"translation\":\""
                                    + LITERAL + "\"}";
                        return "{\"ok\":false,\"reason\":\"время venís перепутано\"}";
                    }, null);
            fail("Rejected audit must never return a savable translation");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("2/3"));
        }
        assertEquals(Arrays.asList(1, 2), passes);
    }

    public void testFinalAuditMayVetoAfterTwoApprovals() throws Exception {
        List<Integer> passes = new ArrayList<>();
        try {
            OfflineStoryTranslationAudit.review("es", SOURCE, DRAFT,
                    (pass, rules, json) -> {
                        passes.add(pass);
                        if (pass == 1)
                            return "{\"ok\":true,\"translation\":\""
                                    + LITERAL + "\"}";
                        return pass == 2 ? "{\"ok\":true}"
                                : "{\"ok\":false,\"reason\":\"слишком свободный перевод\"}";
                    }, null);
            fail("Final audit veto must block SQLite save");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("3/3"));
        }
        assertEquals(Arrays.asList(1, 2, 3), passes);
    }

    public void testMalformedOrUntranslatedResultRejected() throws Exception {
        for (String invalid : new String[]{
                "", "I could not translate", "{\"ok\":true}",
                "{\"ok\":true,\"translation\":\"Нет перевода. Нет перевода. Нет перевода.\"}",
                "{\"ok\":true,\"translation\":\"Русский текст. Только два предложения.\"}"
        }) {
            final String response = invalid;
            try {
                OfflineStoryTranslationAudit.review("es", SOURCE, DRAFT,
                        (pass, rules, json) -> response, null);
                fail("Invalid initial audit result should fail closed: " + invalid);
            } catch (IllegalArgumentException expected) {
                assertFalse(expected.getMessage().isEmpty());
            }
        }
    }

    public void testBadLanguageAndSourceShapeAreRejectedBeforeModelCall() throws Exception {
        for (String language : new String[]{"ru", "", "es-MX"}) {
            try {
                OfflineStoryTranslationAudit.review(language, SOURCE, DRAFT,
                        (pass, rules, json) -> "{\"ok\":true}", null);
                fail("Only ES or EN accepted");
            } catch (IllegalArgumentException expected) {
                assertFalse(expected.getMessage().isEmpty());
            }
        }
        try {
            OfflineStoryTranslationAudit.review("es", "One sentence.", "Одно предложение.",
                    (pass, rules, json) -> "{\"ok\":true}", null);
            fail("Not a three-sentence story");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("3 исходных"));
        }
    }
}
