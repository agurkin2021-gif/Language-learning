package ru.spanish.learning;

/**
 * Give Spanish TTS a Rioplatense sheísmo pronunciation hint for written "ll".
 * Locale es-AR alone does not guarantee that an installed Android voice says
 * "sh" rather than "y". This changes speech input ONLY, never the displayed
 * text or stored vocabulary/stories.
 *
 * Each "ll" becomes two characters "sh": keep the same UTF-16 length so
 * Android TTS onRangeStart offsets still refer to the original story text.
 * The exact sound ultimately depends on the installed speech engine.
 */
final class RioplatensePronunciation {
    private RioplatensePronunciation() {}

    static String forSpeech(String original, String language) {
        if (original == null || !"es".equals(language)) return original;
        StringBuilder out = null;
        int last = 0;
        for (int i = 0; i + 1 < original.length(); i++) {
            char first = original.charAt(i);
            char second = original.charAt(i + 1);
            if ((first == 'l' || first == 'L') && (second == 'l' || second == 'L')) {
                if (out == null) out = new StringBuilder(original.length());
                out.append(original, last, i);
                out.append(first == 'L' ? 'S' : 's');
                out.append(first == 'L' && second == 'L' ? 'H' : 'h');
                last = i + 2;
                i++;
            }
        }
        if (out == null) return original;
        return out.append(original, last, original.length()).toString();
    }
}
