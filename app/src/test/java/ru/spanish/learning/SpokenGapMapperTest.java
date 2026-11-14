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

    @Test public void mapsPunctuationAndRewrittenWordRangesWithoutGuesswork() {
        String punctuation = "Hola,amigo";
        int gap = punctuation.indexOf("amigo");
        assertEquals(gap, ManualStoryPauses.nearestGap(punctuation, gap));
        assertEquals(List.of(gap), SpokenGapMapper.map(
                punctuation, punctuation, List.of(gap), "es"));

        String original = "once - doce";
        String speech = RioplatensePronunciation.forSpeech(original, "es");
        assertEquals("once doce", speech);
        assertEquals(List.of(4), SpokenGapMapper.map(
                original, speech, List.of(original.indexOf(" doce")), "es"));
    }

    @Test public void rejectsCrossTokenBibleReferenceInsteadOfMovingPause() {
        String original = "Juan 11: 26 y siguió.";
        String speech = RioplatensePronunciation.forSpeech(original, "es");
        try {
            SpokenGapMapper.map(original, speech, List.of(original.indexOf(" 26")), "es");
            fail("Internal scripture digits are one spoken reference, not two words");
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
