package ru.spanish.learning;

/**
 * Rioplatense soft sheismo: consonantal ll/y -> soft Russian щ hint.
 * Only TTS input changes. The zero-width character after ll keeps UTF-16
 * positions aligned with the original text for story highlighting/resume.
 * Mixed-script TTS is engine-dependent and must be checked on the device.
 */
final class RioplatensePronunciation {
    private RioplatensePronunciation() {}

    static String forSpeech(String original, String language) {
        if (original == null || !"es".equals(language)) return original;
        StringBuilder out = new StringBuilder(original.length());
        for (int i = 0; i < original.length(); i++) {
            char c = original.charAt(i);
            if ((c == 'l' || c == 'L') && i + 1 < original.length()
                    && (original.charAt(i + 1) == 'l' || original.charAt(i + 1) == 'L')) {
                out.append('щ').append('\u200B');
                i++;
            } else if ((c == 'y' || c == 'Y') && consonantalY(original, i)) {
                out.append('щ');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean consonantalY(String text, int index) {
        char c = text.charAt(index);
        if (c != 'y' && c != 'Y') return false;
        int start = index;
        while (start > 0 && Character.isLetter(text.charAt(start - 1))) start--;
        int end = index + 1;
        while (end < text.length() && Character.isLetter(text.charAt(end))) end++;
        // Spanish standalone conjunction "y", final vocalic y in hoy/soy,
        // and vowel y in rey are not consonantal.
        if (end - start == 1 || index == end - 1) return false;
        return index == start || isVowel(text.charAt(index - 1));
    }

    private static boolean isVowel(char c) {
        return "aeiouáéíóúüAEIOUÁÉÍÓÚÜ".indexOf(c) >= 0;
    }
}
