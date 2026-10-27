package ru.spanish.learning;

import org.junit.Test;
import static org.junit.Assert.*;

public class RioplatensePronunciationTest {
    @Test public void softLlAndConsonantalY() {
        assertEquals("cepiщ\u200Bo", RioplatensePronunciation.forSpeech("cepillo", "es"));
        assertEquals("taщ\u200Be", RioplatensePronunciation.forSpeech("talle", "es"));
        assertEquals("caщ\u200Be", RioplatensePronunciation.forSpeech("calle", "es"));
        assertEquals("щ\u200Buvia", RioplatensePronunciation.forSpeech("lluvia", "es"));
        assertEquals("щo", RioplatensePronunciation.forSpeech("yo", "es"));
        assertEquals("aщer", RioplatensePronunciation.forSpeech("ayer", "es"));
        assertEquals("maщo", RioplatensePronunciation.forSpeech("mayo", "es"));
    }
    @Test public void vowelYAndConjunctionRemain() {
        assertEquals("Hoy soy rey y voy.", RioplatensePronunciation.forSpeech("Hoy soy rey y voy.", "es"));
        assertEquals("El lápiz y la mesa.", RioplatensePronunciation.forSpeech("El lápiz y la mesa.", "es"));
    }
    @Test public void englishAndStorageAreUntouched() {
        assertEquals("yellow", RioplatensePronunciation.forSpeech("yellow", "en"));
        assertNull(RioplatensePronunciation.forSpeech(null, "es"));
        assertEquals("casa", RioplatensePronunciation.forSpeech("casa", "es"));
    }
    @Test public void utf16OffsetsStayAligned() {
        String original = "El cepillo y la lluvia. Yo llegué ayer.";
        String spoken = RioplatensePronunciation.forSpeech(original, "es");
        assertEquals(original.length(), spoken.length());
        assertEquals(original.indexOf("lluvia"), spoken.indexOf("щ\u200Buvia"));
        assertEquals(original.indexOf("ayer"), spoken.indexOf("aщer"));
    }
}
