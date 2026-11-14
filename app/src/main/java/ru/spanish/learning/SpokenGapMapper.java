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
            if (!ManualStoryPauses.isGap(original, gap) || gap <= previous)
                throw new IllegalArgumentException("Invalid pause position: " + gap);
            String normalizedPrefix = RioplatensePronunciation.forSpeech(
                    original.substring(0, gap), language);
            int position = normalizedPrefix == null ? -1 : normalizedPrefix.length();
            if (normalizedPrefix == null || !spoken.startsWith(normalizedPrefix)
                    || !ManualStoryPauses.isGap(spoken, position)) {
                // Regexes may absorb punctuation across the chosen gap:
                // "once - doce" becomes "once doce". An EXACT normalized suffix
                // can anchor the same word boundary without estimating time.
                String suffix = RioplatensePronunciation.forSpeech(
                        original.substring(gap), language);
                position = suffix == null || !spoken.endsWith(suffix)
                        ? -1 : spoken.length() - suffix.length();
                if (!ManualStoryPauses.isGap(spoken, position))
                    throw new IllegalArgumentException(
                            "Speech normalization changed the chosen word boundary: " + gap);
            }
            if (!mapped.isEmpty() && position <= mapped.get(mapped.size() - 1))
                throw new IllegalArgumentException("Overlapping speech pause positions");
            mapped.add(position);
            previous = gap;
        }
        return List.copyOf(mapped);
    }
}
