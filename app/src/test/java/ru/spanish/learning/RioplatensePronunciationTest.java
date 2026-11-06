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
    @Test public void russianScriptureReferencesAreReadAsPlainNumbersNotTime() {
        assertEquals("Второзаконие. одиннадцать двадцать шесть двадцать восемь,",
                RioplatensePronunciation.forSpeech("Второзаконие. 11:26–28,", "ru"));
        assertEquals("Иеремия. два восемь:",
                RioplatensePronunciation.forSpeech("Иеремия. 2:8:", "ru"));
        assertEquals("Иеремия. Пять: одиннадцать — двенадцать, пять одиннадцать двенадцать:",
                RioplatensePronunciation.forSpeech(
                        "Иеремия. Пять: одиннадцать — двенадцать, 5:11–12:", "ru"));
        assertEquals("Иеремия. семнадцать двадцать один двадцать три:",
                RioplatensePronunciation.forSpeech("Иеремия. 17:21–23:", "ru"));
        assertEquals("Вторая книга Паралипоменон тридцать четыре четырнадцать:",
                RioplatensePronunciation.forSpeech(
                        "Вторая книга Паралипоменон 34:14:", "ru"));
        assertEquals("Софония два один два:",
                RioplatensePronunciation.forSpeech("Софония 2:1-2:", "ru"));
    }
    @Test public void russianSpeechDoesNotChangeOtherNumbersOrVisibleStoryText() {
        String story = "В семнадцатом веке до нашей эры. 609 год.";
        assertEquals(story, RioplatensePronunciation.forSpeech(story, "ru"));
        assertEquals("Deuteronomio. 11:26-28,",
                RioplatensePronunciation.forSpeech("Deuteronomio. 11:26-28,", "es"));
        assertEquals("11:26–28", RioplatensePronunciation.forSpeech("11:26–28", "en"));
        assertNull(RioplatensePronunciation.forSpeech(null, "ru"));
    }
    @Test public void otherLanguagesAndNullArePreserved() {
        assertEquals("yellow", RioplatensePronunciation.forSpeech("yellow", "en"));
        assertNull(RioplatensePronunciation.forSpeech(null, "es"));
    }
}
