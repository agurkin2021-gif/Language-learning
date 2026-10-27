package ru.spanish.learning;

/**
 * Bundled, optional Piper Daniela es_AR model.
 * The verified INT8 variant is shipped inside the signed application APK.
 * Spanish orthography is passed through unchanged.
 */
final class DanielaVoiceOption {
    static final String ID = "piper:es_AR-daniela-high";
    static final String MODEL_DIRECTORY = "embedded-daniela";
    static final String MODEL_PATH = MODEL_DIRECTORY + "/daniela.onnx";
    static final String TOKENS_PATH = MODEL_DIRECTORY + "/tokens.txt";
    static final String DATA_DIRECTORY = MODEL_DIRECTORY + "/espeak-ng-data";
    static final String PREVIEW = "El talle de la calle. La lluvia llegó ayer. Yo llevo la llave.";

    private DanielaVoiceOption() {}

    static boolean matches(String name) {
        return ID.equals(name);
    }
}
