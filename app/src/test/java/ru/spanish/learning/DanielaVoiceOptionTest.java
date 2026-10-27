package ru.spanish.learning;

import org.junit.Test;
import static org.junit.Assert.*;

public class DanielaVoiceOptionTest {
    @Test public void danielaIsBundledAndRemainsOptional() {
        assertTrue(DanielaVoiceOption.matches("piper:es_AR-daniela-high"));
        assertFalse(DanielaVoiceOption.matches("es-AR"));
        assertFalse(DanielaVoiceOption.matches(null));
        assertEquals("embedded-daniela/daniela.onnx", DanielaVoiceOption.MODEL_PATH);
        assertEquals("embedded-daniela/tokens.txt", DanielaVoiceOption.TOKENS_PATH);
        assertEquals("embedded-daniela/espeak-ng-data", DanielaVoiceOption.DATA_DIRECTORY);
    }

    @Test public void previewDoesNotAlterLatinSpanishSpelling() {
        assertEquals("El talle de la calle. La lluvia llegó ayer. Yo llevo la llave.",
                DanielaVoiceOption.PREVIEW);
        assertEquals(DanielaVoiceOption.PREVIEW,
                RioplatensePronunciation.forSpeech(DanielaVoiceOption.PREVIEW, "es"));
    }
}
