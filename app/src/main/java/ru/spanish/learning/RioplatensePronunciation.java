package ru.spanish.learning;

/**
 * Safe speech normalization. Never inject Cyrillic or zero-width characters
 * into Spanish TTS: es-US voices pronounce mixed-script words incorrectly.
 * Let the installed Spanish voice pronounce the original orthography.
 * Rioplatense sheismo requires a compatible voice, not character substitution.
 */
final class RioplatensePronunciation {
    private RioplatensePronunciation() {}

    static String forSpeech(String original, String language) {
        return original;
    }
}
