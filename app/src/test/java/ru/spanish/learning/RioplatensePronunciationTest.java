package ru.spanish.learning;

import org.junit.Test;
import static org.junit.Assert.*;

public class RioplatensePronunciationTest {
    @Test public void talleAndOtherLlWordsUseSheismo() {
        assertEquals("tashe", RioplatensePronunciation.forSpeech("talle", "es"));
        assertEquals("cashe", RioplatensePronunciation.forSpeech("calle", "es"));
        assertEquals("shuvia", RioplatensePronunciation.forSpeech("lluvia", "es"));
        assertEquals("ladrisho", RioplatensePronunciation.forSpeech("ladrillo", "es"));
        assertEquals("shegó", RioplatensePronunciation.forSpeech("llegó", "es"));
    }

    @Test public void normalSpanishSentenceRetainsAccentsAndPunctuation() {
        String text = "El talle de la calle. La lluvia llegó ayer. ¡Ella está allí!";
        assertEquals("El tashe de la cashe. La shuvia shegó ayer. ¡Esha está ashí!",
                RioplatensePronunciation.forSpeech(text, "es"));
    }

    @Test public void singleLAndConjunctionYAreUnchanged() {
        String text = "El lápiz y la mesa. Hoy leo el libro.";
        assertEquals(text, RioplatensePronunciation.forSpeech(text, "es"));
    }

    @Test public void doesNotModifyEnglishSpeech() {
        String text = "The tall yellow building is lovely.";
        assertEquals(text, RioplatensePronunciation.forSpeech(text, "en"));
    }

    @Test public void supportsInitialCapitalAndAllCaps() {
        assertEquals("Shave, TASHE, shaves",
                RioplatensePronunciation.forSpeech("Llave, TALLE, llaves", "es"));
    }

    @Test public void replacementPreservesUtf16OffsetsForStoryResume() {
        String text = "El taller está allí. La lluvia cae sobre la calle.";
        String spoken = RioplatensePronunciation.forSpeech(text, "es");
        assertEquals(text.length(), spoken.length());
        assertEquals(text.indexOf("La lluvia"), spoken.indexOf("La shuvia"));
        int offset = text.indexOf("lluvia");
        assertEquals(RioplatensePronunciation.forSpeech(text.substring(offset), "es"),
                spoken.substring(offset));
    }

    @Test public void nullOrUnmodifiedTextAreSafe() {
        assertNull(RioplatensePronunciation.forSpeech(null, "es"));
        assertEquals("", RioplatensePronunciation.forSpeech("", "es"));
        assertEquals("casa", RioplatensePronunciation.forSpeech("casa", "es"));
    }
}
