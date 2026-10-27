package ru.spanish.learning;

import android.test.AndroidTestCase;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;
import com.k2fsa.sherpa.onnx.GeneratedAudio;
import java.io.InputStream;

/** Checks the actual bundled native model, not only the voice selector label. */
public final class EmbeddedDanielaTest extends AndroidTestCase {
    public void testNativePiperDanielaGeneratesArgentinianSpanish() throws Exception {
        try (InputStream modelFile = getContext().getAssets().open(DanielaVoiceOption.MODEL_PATH);
             InputStream tokenFile = getContext().getAssets().open(DanielaVoiceOption.TOKENS_PATH)) {
            assertTrue("Daniela ONNX model is missing or empty", modelFile.read() >= 0);
            assertTrue("Piper tokens are missing or empty", tokenFile.read() >= 0);
        }
        assertTrue(getContext().getAssets().list(DanielaVoiceOption.DATA_DIRECTORY).length > 0);

        OfflineTtsVitsModelConfig vits = new OfflineTtsVitsModelConfig();
        vits.setModel(DanielaVoiceOption.MODEL_PATH);
        vits.setTokens(DanielaVoiceOption.TOKENS_PATH);
        vits.setDataDir(DanielaVoiceOption.DATA_DIRECTORY);
        OfflineTtsModelConfig model = new OfflineTtsModelConfig();
        model.setVits(vits);
        model.setNumThreads(2);
        OfflineTtsConfig config = new OfflineTtsConfig();
        config.setModel(model);

        OfflineTts tts = new OfflineTts(getContext().getAssets(), config);
        try {
            GeneratedAudio sound = tts.generate("Yo llevo la llave.", 0, 1.0f);
            assertTrue("Native Daniela should output audible samples", sound.getSamples().length > 11025);
            assertEquals(22050, sound.getSampleRate());
        } finally {
            tts.release();
        }
    }
}
