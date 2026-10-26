package ru.spanish.learning;

import org.junit.Test;
import java.util.Locale;
import static org.junit.Assert.*;

public class SpanishVoicePreferenceTest {
    @Test public void rioDeLaPlataPrecedesOtherLatinAmericanLocales() {
        int argentina = SpanishVoicePreference.regionRank(Locale.forLanguageTag("es-AR"));
        int uruguay = SpanishVoicePreference.regionRank(Locale.forLanguageTag("es-UY"));
        int mexico = SpanishVoicePreference.regionRank(Locale.forLanguageTag("es-MX"));
        assertEquals(0, argentina);
        assertTrue(argentina < uruguay);
        assertTrue(uruguay < mexico);
    }

    @Test public void rejectsSpainGenericAndNonSpanishVoices() {
        assertEquals(-1, SpanishVoicePreference.regionRank(Locale.forLanguageTag("es-ES")));
        assertEquals(-1, SpanishVoicePreference.regionRank(Locale.forLanguageTag("es")));
        assertEquals(-1, SpanishVoicePreference.regionRank(Locale.forLanguageTag("en-US")));
        assertEquals(-1, SpanishVoicePreference.regionRank(null));
    }

    @Test public void regionalLabelsRemainDistinct() {
        assertEquals("Аргентина", SpanishVoicePreference.regionLabel(Locale.forLanguageTag("es-AR")));
        assertEquals("Уругвай", SpanishVoicePreference.regionLabel(Locale.forLanguageTag("es-UY")));
        assertEquals("Мексика", SpanishVoicePreference.regionLabel(Locale.forLanguageTag("es-MX")));
        assertEquals("Латинская Америка", SpanishVoicePreference.regionLabel(Locale.forLanguageTag("es-CO")));
    }

    @Test public void retainsLatinAmericanFallbackOptions() {
        assertTrue(SpanishVoicePreference.regionRank(Locale.forLanguageTag("es-CO")) > 0);
        assertTrue(SpanishVoicePreference.regionRank(Locale.forLanguageTag("es-419")) > 0);
        assertTrue(SpanishVoicePreference.regionRank(Locale.forLanguageTag("es-US")) > 0);
    }
}
