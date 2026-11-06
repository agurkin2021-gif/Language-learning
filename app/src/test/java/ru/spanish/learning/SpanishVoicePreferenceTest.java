package ru.spanish.learning;

import org.junit.Test;
import java.util.Locale;
import static org.junit.Assert.*;

public class SpanishVoicePreferenceTest {
    @Test public void requestedLatinAmericanLocalesTakePriority() {
        int uruguay = SpanishVoicePreference.regionRank(Locale.forLanguageTag("es-UY"));
        int colombia = SpanishVoicePreference.regionRank(Locale.forLanguageTag("es-CO"));
        int venezuela = SpanishVoicePreference.regionRank(Locale.forLanguageTag("es-VE"));
        int mexico = SpanishVoicePreference.regionRank(Locale.forLanguageTag("es-MX"));
        int argentina = SpanishVoicePreference.regionRank(Locale.forLanguageTag("es-AR"));

        assertEquals(0, uruguay);
        assertEquals(1, colombia);
        assertEquals(2, venezuela);
        assertEquals(3, mexico);
        assertEquals(4, argentina);
        assertTrue(uruguay < colombia);
        assertTrue(colombia < venezuela);
        assertTrue(venezuela < mexico);
        assertTrue(mexico < argentina);
    }

    @Test public void highestTtsQualityOutranksRegionForSpanishPronunciation() {
        Locale argentina = Locale.forLanguageTag("es-AR");
        Locale uruguay = Locale.forLanguageTag("es-UY");
        Locale mexico = Locale.forLanguageTag("es-MX");
        // An objectively higher Android TTS quality beats a preferred locale.
        assertTrue(SpanishVoicePreference.compareQualityFirst(
                100, mexico, 300, argentina) > 0);
        assertTrue(SpanishVoicePreference.compareQualityFirst(
                300, mexico, 100, uruguay) < 0);
        // With equal quality, preserve the historical Latin-American fallback.
        assertTrue(SpanishVoicePreference.compareQualityFirst(
                300, uruguay, 300, mexico) < 0);
        assertEquals(0, SpanishVoicePreference.compareQualityFirst(
                300, argentina, 300, argentina));
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
