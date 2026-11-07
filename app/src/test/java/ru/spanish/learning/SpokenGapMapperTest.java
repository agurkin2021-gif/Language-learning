package ru.spanish.learning;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;

public final class SpokenGapMapperTest {
    @Test public void mapsEverySelectedGapWithoutChangingWholeSentence() {
        String original = "Lucía vive en una casa pequeña.";
        List<Integer> gaps = List.of(original.indexOf(" vive"), original.indexOf(" casa"));
        List<Integer> positions = SpokenGapMapper.map(original,
                RioplatensePronunciation.forSpeech(original, "es"), gaps, "es");
        assertEquals("Every selected marker is mapped, in the same order", gaps, positions);
    }

    @Test public void mapsGapAfterExpandedBibleVerseToSameSpokenWord() {
        String original = "Juan 11:26 y después siguió.";
        int chosen = original.indexOf(" y");
        String speech = RioplatensePronunciation.forSpeech(original, "es");
        List<Integer> positions = SpokenGapMapper.map(original, speech, List.of(chosen), "es");
        assertEquals(speech.indexOf(" y después"), (int) positions.get(0));
        assertTrue(positions.get(0) > chosen);
    }

    @Test public void rejectsGapsWhoseNormalizationRemovesTheBoundary() {
        String original = "once - doce";
        String speech = RioplatensePronunciation.forSpeech(original, "es");
        assertEquals("once doce", speech);
        try {
            SpokenGapMapper.map(original, speech, List.of(original.indexOf(" doce")), "es");
            fail("Removed punctuation cannot silently shift a requested pause");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("normalization"));
        }
    }

    @Test public void refusesUnsortedDuplicatedOrNonWhitespaceMarkers() {
        String original = "Uno dos tres";
        for (List<Integer> invalid : List.of(List.of(3,3), List.of(7,3), List.of(2))) {
            try {
                SpokenGapMapper.map(original, original, invalid, "es");
                fail("Invalid positions must not shift pauses: " + invalid);
            } catch (IllegalArgumentException expected) {
                assertNotNull(expected.getMessage());
            }
        }
    }
}
