package ru.spanish.learning;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;

public class BibleStoryHighlightTest {
    @Test public void onlyExtraCopyIsColored() {
        assertTrue(BibleStoryHighlight.appliesTo("Gerizim y Ebal — citas en rojo"));
        assertFalse(BibleStoryHighlight.appliesTo("Gerizim y Ebal — versión fácil (Argentina)"));
        assertFalse(BibleStoryHighlight.appliesTo("Gerizim y Ebal"));
    }

    @Test public void coloredBibleLabelsAndMultiLineQuotesPreserveNormalText() {
        String story = "Texto normal.\n"
                + "Jeremías, capítulo dos, versículo ocho.\n"
                + "«Los sacerdotes no dijeron: ¿Dónde está Jehová?\n"
                + "y los profetas profetizaron en Baal»\n"
                + "La palabra «tof» significa tambor.\n"
                + "«Hemos pecado contra Ti».";
        List<String> fragments = Sentences.split(story);
        boolean[] red = BibleStoryHighlight.mark(story);
        assertEquals(fragments.size(), red.length);
        assertEquals(6, red.length);
        assertFalse(red[0]);
        assertTrue(red[1]);
        assertTrue(red[2]);
        assertTrue(red[3]);
        assertFalse(red[4]);
        assertTrue(red[5]);
    }

    @Test public void adjacentVersesDoNotColorIntermediateNarration() {
        String story = "Deuteronomio, capítulo once.\n«Bendición.»\n"
                + "Texto del autor.\nSofonías, capítulo dos.\n«Maldición.»\nOtro texto.";
        boolean[] red = BibleStoryHighlight.mark(story);
        assertEquals(6, red.length);
        for (int i=0;i<6;i++) assertEquals("index " + i, i != 2 && i != 5, red[i]);
    }
}
