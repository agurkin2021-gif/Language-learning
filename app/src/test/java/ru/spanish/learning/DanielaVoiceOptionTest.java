package ru.spanish.learning;

import org.junit.Test;
import static org.junit.Assert.*;

public class DanielaVoiceOptionTest {
    @Test public void danielaIsAnIndependentOptionalEngine() {
        assertTrue(DanielaVoiceOption.matches("piper:es_AR-daniela-high"));
        assertFalse(DanielaVoiceOption.matches("es-AR"));
        assertFalse(DanielaVoiceOption.matches(null));
        assertEquals("com.k2fsa.sherpa.onnx.tts.engine", DanielaVoiceOption.ENGINE_PACKAGE);
        assertEquals("vits-piper-es_AR-daniela-high", DanielaVoiceOption.MODEL_DIRECTORY);
        assertEquals("es_AR-daniela-high.onnx", DanielaVoiceOption.MODEL_FILENAME);
    }

    @Test public void previewUsesUnmodifiedSpanishWithLlAndY() {
        assertEquals("El talle de la calle. La lluvia llegó ayer. Yo llevo la llave.",
                DanielaVoiceOption.PREVIEW);
        assertTrue(DanielaVoiceOption.INSTALL_PAGE.startsWith(
                "https://k2-fsa.github.io/sherpa/onnx/tts/all/Spanish/"));
    }
}
