package ru.spanish.learning;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public final class ManualStoryPausesTest {
    @Test public void toggleAndPersistIndependentlyPerSentence() {
        String first = "Los hombres van a la ciudad.";
        int gap = ManualStoryPauses.nearestGap(first, 4);
        assertEquals(3, gap);
        Map<Integer,List<Integer>> positions = new HashMap<>();
        ManualStoryPauses.toggle(positions, 2, gap);
        assertEquals("2:3", ManualStoryPauses.encode(positions));
        assertEquals(positions, ManualStoryPauses.decode("2:3,2:3,999:5,bad",
                List.of("a", "b", first)));
        ManualStoryPauses.toggle(positions, 2, gap);
        assertTrue(positions.isEmpty());
    }

    @Test public void visualMarkersRoundTripWithoutEditingOriginalText() {
        String source = "Los hombres van a la ciudad.";
        List<Integer> gaps = List.of(3, 17);
        String display = ManualStoryPauses.display(source, gaps);
        assertEquals(2, display.split("⏸", -1).length - 1);
        for (int index=0; index<source.length(); index++) {
            int displayIndex = index + (index>=3?ManualStoryPauses.SYMBOL.length():0)
                    + (index>=17?ManualStoryPauses.SYMBOL.length():0);
            assertEquals(index, ManualStoryPauses.originalOffset(displayIndex, gaps));
        }
        assertEquals(3, ManualStoryPauses.originalOffset(4, gaps));
        assertEquals(17, ManualStoryPauses.originalOffset(17 + ManualStoryPauses.SYMBOL.length() + 1, gaps));
        assertEquals("Los hombres van a la ciudad.", source);
    }

    @Test public void pauseAndSlowWordPiecesHaveCorrectOffsets() {
        String text = "Los hombres van a la ciudad.";
        int gap = ManualStoryPauses.nearestGap(text, 19);
        assertEquals(20, gap); // gap after "la"
        List<ManualStoryPauses.Piece> parts = ManualStoryPauses.pieces(text, List.of(gap), true);
        long pauses = parts.stream().filter(ManualStoryPauses.Piece::silence).count();
        assertEquals(1L, pauses);
        String spoken = parts.stream().filter(p->!p.silence())
                .map(ManualStoryPauses.Piece::text).reduce("", String::concat);
        // Every fragment is spoken exactly once; trailing/leading whitespace can be trimmed by TTS.
        assertEquals(text, spoken);
        assertTrue(parts.stream().anyMatch(p->p.slow() && p.text().trim().equals("Los")));
        assertTrue(parts.stream().anyMatch(p->p.slow() && p.text().trim().equals("la")));
        assertTrue(parts.stream().anyMatch(p->p.silence() && p.sourceStart()==gap));
    }

    @Test public void disabledWordRateDoesNotSplitWordsAtAll() {
        String text = "Los hombres van a la ciudad.";
        List<ManualStoryPauses.Piece> pieces = ManualStoryPauses.pieces(text, List.of(3), false);
        assertEquals(3, pieces.size());
        assertFalse(pieces.get(0).slow());
        assertTrue(pieces.get(1).silence());
        assertFalse(pieces.get(2).slow());
    }
}
