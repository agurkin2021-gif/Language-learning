package ru.spanish.learning;

import java.util.ArrayList;
import java.util.List;

/**
 * Map user-owned gaps in the displayed sentence to the single normalized TTS
 * utterance. Refuse an ambiguous transformation rather than move a pause.
 */
final class SpokenGapMapper {
    private SpokenGapMapper() {}

    static List<Integer> map(String original, String spoken, List<Integer> gaps,
                             String language) {
        if (original == null || spoken == null || gaps == null)
            throw new IllegalArgumentException("Missing sentence or pause markers");
        List<Integer> mapped = new ArrayList<>();
        int previous = 0;
        for (int gap : gaps) {
            if (gap < 1 || gap >= original.length()
                    || !Character.isWhitespace(original.charAt(gap)) || gap <= previous)
                throw new IllegalArgumentException("Invalid pause position: " + gap);
            String normalizedPrefix = RioplatensePronunciation.forSpeech(
                    original.substring(0, gap), language);
            if (normalizedPrefix == null || !spoken.startsWith(normalizedPrefix))
                throw new IllegalArgumentException(
                        "Speech normalization changed the chosen word boundary: " + gap);
            int position = normalizedPrefix.length();
            if (position < 1 || position >= spoken.length()
                    || !Character.isWhitespace(spoken.charAt(position)))
                throw new IllegalArgumentException(
                        "The chosen word boundary is not present in speech: " + gap);
            if (!mapped.isEmpty() && position <= mapped.get(mapped.size() - 1))
                throw new IllegalArgumentException("Overlapping speech pause positions");
            mapped.add(position);
            previous = gap;
        }
        return List.copyOf(mapped);
    }
}
