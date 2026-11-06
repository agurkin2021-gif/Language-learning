package ru.spanish.learning;

import org.junit.Test;
import static org.junit.Assert.*;

public class RioplatensePronunciationTest {
    @Test public void originalSpanishSpellingIsSentToTts() {
        for (String word : new String[]{"toalla", "cepillo", "calle", "talle",
                "lluvia", "yo", "ayer", "hoy", "soy", "rey"}) {
            assertEquals(word, RioplatensePronunciation.forSpeech(word, "es"));
        }
    }

    @Test public void normalStorySentencesRemainUnchanged() {
        String spanish = "Yo llevé la toalla y el cepillo ayer.";
        assertEquals(spanish, RioplatensePronunciation.forSpeech(spanish, "es"));
        String russian = "В семнадцатом веке до нашей эры. 609 год.";
        assertEquals(russian, RioplatensePronunciation.forSpeech(russian, "ru"));
        assertEquals("yellow", RioplatensePronunciation.forSpeech("yellow", "en"));
    }

    @Test public void russianScriptureReferencesAreCardinalNumbersWithoutHyphenOrTime() {
        assertEquals("Второзаконие. одиннадцать двадцать шесть двадцать восемь,",
                RioplatensePronunciation.forSpeech("Второзаконие. 11:26–28,", "ru"));
        assertEquals("Иеремия. два восемь:",
                RioplatensePronunciation.forSpeech("Иеремия. 2:8:", "ru"));
        assertEquals("Иеремия двадцать два четырнадцать тридцать",
                RioplatensePronunciation.forSpeech("Иеремия 22: 14-30", "ru"));
        assertEquals("Иеремия двадцать два четырнадцать тридцать",
                RioplatensePronunciation.forSpeech("Иеремия 22 : 14 — 30", "ru"));
        assertEquals("Иеремия. пять одиннадцать двенадцать:",
                RioplatensePronunciation.forSpeech("Иеремия. 5:11–12:", "ru"));
        assertEquals("Иеремия. семнадцать двадцать один двадцать три:",
                RioplatensePronunciation.forSpeech("Иеремия. 17:21–23:", "ru"));
        assertEquals("Вторая книга Паралипоменон тридцать четыре четырнадцать:",
                RioplatensePronunciation.forSpeech(
                        "Вторая книга Паралипоменон 34:14:", "ru"));
        assertEquals("Софония два один два:",
                RioplatensePronunciation.forSpeech("Софония 2:1-2:", "ru"));
        assertEquals("Псалом сто девятнадцать один сто семьдесят шесть",
                RioplatensePronunciation.forSpeech("Псалом 119:1-176", "ru"));
    }

    @Test public void spanishScriptureReferencesAreCardinalNumbersWithoutGuionOrHora() {
        assertEquals("Jeremías veintidós catorce treinta",
                RioplatensePronunciation.forSpeech("Jeremías 22: 14-30", "es"));
        assertEquals("Jeremías veintidós catorce treinta",
                RioplatensePronunciation.forSpeech("Jeremías 22:14–30", "es"));
        assertEquals("Jeremías veintidós catorce treinta",
                RioplatensePronunciation.forSpeech("Jeremías 22 : 14 — 30", "es"));
        assertEquals("Deuteronomio. once veintiséis veintiocho,",
                RioplatensePronunciation.forSpeech("Deuteronomio. 11:26-28,", "es"));
        assertEquals("Jeremías. cinco once doce:",
                RioplatensePronunciation.forSpeech("Jeremías. 5:11–12:", "es"));
        assertEquals("Sofonías dos uno dos:",
                RioplatensePronunciation.forSpeech("Sofonías 2:1–2:", "es"));
        assertEquals("Crónicas treinta y cuatro veintitrés treinta y uno",
                RioplatensePronunciation.forSpeech("Crónicas 34:23-31", "es"));
        assertEquals("Salmos ciento diecinueve uno ciento setenta y seis",
                RioplatensePronunciation.forSpeech("Salmos 119:1-176", "es"));
    }

    @Test public void spellingOutVerseRangesDoesNotSpeakDashesInEitherLanguage() {
        assertEquals("Jeremías. Cinco: once doce, cinco once doce:",
                RioplatensePronunciation.forSpeech(
                        "Jeremías. Cinco: once-doce, 5:11–12:", "es"));
        assertEquals("Jeremías. Cinco: veintiséis veintiocho:",
                RioplatensePronunciation.forSpeech(
                        "Jeremías. Cinco: veintiséis–veintiocho:", "es"));
        assertEquals("Иеремия. Пять: одиннадцать двенадцать, пять одиннадцать двенадцать:",
                RioplatensePronunciation.forSpeech(
                        "Иеремия. Пять: одиннадцать — двенадцать, 5:11–12:", "ru"));
        assertEquals("Buenos Aires-Mendoza es una ruta.",
                RioplatensePronunciation.forSpeech("Buenos Aires-Mendoza es una ruta.", "es"));
        assertEquals("Он шёл туда-сюда.",
                RioplatensePronunciation.forSpeech("Он шёл туда-сюда.", "ru"));
    }

    @Test public void biblicalSpanishStressIsNormalizedWithoutChangingOriginalText() {
        assertEquals("su cerviz, por no oír ni",
                RioplatensePronunciation.forSpeech("su cerviz, por no oir ni", "es"));
        assertEquals("Antes que para el decreto",
                RioplatensePronunciation.forSpeech("Antes que pára el decreto", "es"));
        assertEquals("las palabras de Necao que eran de boca de Dios",
                RioplatensePronunciation.forSpeech(
                        "las palabras de Nechâo que eran de boca de Dios", "es"));
        assertEquals("los altos de Tófet,",
                RioplatensePronunciation.forSpeech("los altos de Topheth,", "es"));
        assertEquals("puertas de Jerusalén;",
                RioplatensePronunciation.forSpeech("puertas de Jerusalem;", "es"));
        assertEquals("mandé a vuestros padres",
                RioplatensePronunciation.forSpeech("mandé á vuestros padres", "es"));
        assertEquals("Gerizim o Ebal, bendición o maldición.",
                RioplatensePronunciation.forSpeech(
                        "Gerizim o Ebal, bendicióno maldición.", "es"));
    }
    @Test public void validSpanishStressMarksAndVoseoArePreserved() {
        for (String word : new String[] {
                "Canaán", "Jehová", "Jeremías", "Josías", "Sofonías",
                "Manasés", "Nínive", "Tófet", "Moisés", "Ezequías",
                "Éufrates", "Hilcías", "salí", "oír", "escuchás", "tenés",
                "podés", "andá", "esperá", "buscá", "sentate"}) {
            assertEquals(word, RioplatensePronunciation.forSpeech(word, "es"));
        }
        assertEquals("Mas ellos no oyeron ni inclinaron su oído.",
                RioplatensePronunciation.forSpeech(
                        "Mas ellos no oyeron ni inclinaron su oído.", "es"));
        assertEquals("Jerusalem", RioplatensePronunciation.forSpeech("Jerusalem", "en"));
    }

    @Test public void punctuationAndOtherLanguagesAreNotAltered() {
        assertEquals("11:26–28", RioplatensePronunciation.forSpeech("11:26–28", "en"));
        assertEquals("Текст: десять сорок, а не 12:30:45.",
                RioplatensePronunciation.forSpeech("Текст: десять сорок, а не 12:30:45.", "ru"));
        assertNull(RioplatensePronunciation.forSpeech(null, "es"));
        assertNull(RioplatensePronunciation.forSpeech(null, "ru"));
    }
}
