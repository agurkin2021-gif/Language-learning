package ru.spanish.learning;

/**
 * Optional, independently installed sherpa-onnx Android TTS engine with
 * the precise Piper es_AR-daniela-high model. Never mimic its sound by
 * modifying Spanish spelling or changing the default Android TTS voice.
 */
final class DanielaVoiceOption {
    static final String ID = "piper:es_AR-daniela-high";
    static final String ENGINE_PACKAGE = "com.k2fsa.sherpa.onnx.tts.engine";
    static final String MODEL_DIRECTORY = "vits-piper-es_AR-daniela-high";
    static final String MODEL_FILENAME = "es_AR-daniela-high.onnx";
    static final String INSTALL_PAGE =
            "https://k2-fsa.github.io/sherpa/onnx/tts/all/Spanish/vits-piper-es_AR-daniela-high.html";
    static final String PREVIEW = "El talle de la calle. La lluvia llegó ayer. Yo llevo la llave.";

    private DanielaVoiceOption() {}

    static boolean matches(String name) {
        return ID.equals(name);
    }
}
