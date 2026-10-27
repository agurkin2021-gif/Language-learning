package ru.spanish.learning;

import android.test.AndroidTestCase;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;
import com.k2fsa.sherpa.onnx.GeneratedAudio;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
        vits.setDataDir(DanielaAssets.ensurePhonemeDirectory(getContext()));
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
            // Real card texts, including a bare word with no punctuation.
            for (String card : new String[]{"toalla", "cepillo", "calle"}) {
                GeneratedAudio word = tts.generate(EmbeddedDanielaSpeech.speechText(card), 0, .85f);
                assertTrue("No samples generated for card: " + card, word.getSamples().length > 3000);
            }
        } finally {
            tts.release();
        }
    }

    public void testEmbeddedDanielaActuallyPlaysTwoCards() throws Exception {
        EmbeddedDanielaSpeech voice = new EmbeddedDanielaSpeech(getContext());
        try {
            CountDownLatch prepared = new CountDownLatch(1);
            AtomicReference<String> prepareError = new AtomicReference<>();
            voice.prepare(new EmbeddedDanielaSpeech.InitListener() {
                @Override public void onReady() { prepared.countDown(); }
                @Override public void onError(String reason) {
                    prepareError.set(reason);
                    prepared.countDown();
                }
            });
            assertTrue("Daniela initialization timed out", prepared.await(120, TimeUnit.SECONDS));
            assertNull("Daniela initialization failed", prepareError.get());
            for (String card : new String[]{"toalla", "calle"}) {
                CountDownLatch completed = new CountDownLatch(1);
                AtomicReference<String> speechError = new AtomicReference<>();
                voice.speak(card, .85f, completed::countDown,
                        reason -> { speechError.set(reason); completed.countDown(); });
                assertTrue("Daniela did not finish card: " + card,
                        completed.await(90, TimeUnit.SECONDS));
                assertNull("Daniela failed to play card: " + card, speechError.get());
            }
        } finally {
            voice.close();
        }
    }

    public void testPiperCardInputPreservesSpanishLetters() {
        assertEquals("toalla.", EmbeddedDanielaSpeech.speechText("toalla"));
        assertEquals("cepillo.", EmbeddedDanielaSpeech.speechText("cepillo"));
        assertEquals("Yo llevo la llave.", EmbeddedDanielaSpeech.speechText("Yo llevo la llave."));
        assertEquals("Hoy soy yo.", EmbeddedDanielaSpeech.speechText("Hoy soy yo"));
    }
}
