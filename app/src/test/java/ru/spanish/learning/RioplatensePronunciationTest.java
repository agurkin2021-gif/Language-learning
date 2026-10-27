package ru.spanish.learning;

import org.junit.Test;
import static org.junit.Assert.*;

public class RioplatensePronunciationTest {
    @Test public void originalSpanishSpellingIsSentToTts() {
        for (String word : new String[]{"toalla", "cepillo", "calle", "talle", "lluvia", "yo", "ayer", "hoy", "soy", "rey"}) {
            assertEquals(word, RioplatensePronunciation.forSpeech(word, "es"));
        }
    }
    @Test public void originalStoryOffsetsAndPunctuationArePreserved() {
        String story = "Yo llevé la toalla y el cepillo ayer.";
        assertEquals(story, RioplatensePronunciation.forSpeech(story, "es"));
    }
    @Test public void otherLanguagesAndNullArePreserved() {
        assertEquals("yellow", RioplatensePronunciation.forSpeech("yellow", "en"));
        assertNull(RioplatensePronunciation.forSpeech(null, "es"));
    }
}
