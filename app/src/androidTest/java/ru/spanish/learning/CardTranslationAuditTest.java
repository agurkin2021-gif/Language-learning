package ru.spanish.learning;

import android.test.AndroidTestCase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Regression checks for the three independent model reviews before storage. */
public final class CardTranslationAuditTest extends AndroidTestCase {
    public void testThreeReviewsKeepLiteralFirstAndGlossInParentheses() throws Exception {
        List<Integer> called = new ArrayList<>();
        List<Integer> progress = new ArrayList<>();
        String value = CardTranslationAudit.review("tengo ganas de comer",
                "мне хочется поесть", "phrase", true, false,
                (pass, instructions, json) -> {
                    called.add(pass);
                    assertTrue(instructions.contains("АУДИТ " + pass + "/3"));
                    assertTrue(json.contains("tengo ganas de comer"));
                    if (pass == 1) return "{\"ok\":true,\"literal\":\"я имею желание поесть\","
                            + "\"idiomatic\":\"мне хочется поесть\",\"reason\":\"\"}";
                    assertTrue(json.contains("я имею желание поесть"));
                    assertTrue(json.contains("мне хочется поесть"));
                    return "{\"ok\":true,\"reason\":\"\"}";
                }, progress::add);
        assertEquals("я имею желание поесть (мне хочется поесть)", value);
        assertEquals(Arrays.asList(1, 2, 3), called);
        assertEquals(Arrays.asList(1, 2, 3), progress);
    }

    public void testSimpleNounNeedsNoUnnecessarySemanticGloss() throws Exception {
        String value = CardTranslationAudit.review("gato", "кот",
                "noun", false, false,
                (pass, instructions, json) -> pass == 1
                        ? "{\"ok\":true,\"literal\":\"кот\",\"idiomatic\":\"\"}"
                        : "{\"ok\":true}", null);
        assertEquals("кот", value);
    }

    public void testSecondAuditRejectsAndNeverCallsThird() throws Exception {
        List<Integer> passes = new ArrayList<>();
        try {
            CardTranslationAudit.review("no tengo", "у меня нет",
                    "phrase", true, false,
                    (pass, instructions, json) -> {
                        passes.add(pass);
                        if (pass == 1)
                            return "{\"ok\":true,\"literal\":\"я имею\",\"idiomatic\":\"\"}";
                        return "{\"ok\":false,\"reason\":\"пропущено отрицание no\"}";
                    }, null);
            fail("Missing negation must not reach SQLite");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Аудит 2/3"));
        }
        assertEquals(Arrays.asList(1, 2), passes);
    }

    public void testThirdAuditCanVetoAfterTwoApprovals() throws Exception {
        List<Integer> passes = new ArrayList<>();
        try {
            CardTranslationAudit.review("me doy cuenta", "я понимаю",
                    "phrase", true, false,
                    (pass, instructions, json) -> {
                        passes.add(pass);
                        if (pass == 1) return "{\"ok\":true,\"literal\":\"я даю себе отчёт\","
                                + "\"idiomatic\":\"я понимаю\"}";
                        return pass == 2 ? "{\"ok\":true}"
                                : "{\"ok\":false,\"reason\":\"пояснение слишком общее\"}";
                    }, null);
            fail("Third veto must stop the card");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Аудит 3/3"));
        }
        assertEquals(Arrays.asList(1, 2, 3), passes);
    }

    public void testMissingOrMalformedAuditResultsFailClosed() throws Exception {
        for (String bad : new String[]{"", "not json", "{\"ok\":true}",
                "{\"ok\":false,\"reason\":\"не уверен\"}"}) {
            try {
                CardTranslationAudit.review("house", "дом", "noun", false, true,
                        (pass, rules, json) -> bad, null);
                fail("Bad stage-one reply may not save: " + bad);
            } catch (IllegalArgumentException expected) {
                assertFalse(expected.getMessage().isEmpty());
            }
        }
        try {
            CardTranslationAudit.literalFirst("иметь право (я прав)", "я прав");
            fail("Literal must not contain preformatted explanation");
        } catch (IllegalArgumentException expected) {}
        try {
            CardTranslationAudit.literalFirst("иметь право", "иметь право");
            fail("No duplicate gloss");
        } catch (IllegalArgumentException expected) {}
    }
}
