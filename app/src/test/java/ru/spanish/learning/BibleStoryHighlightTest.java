package ru.spanish.learning;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;

public class BibleStoryHighlightTest {
    @Test public void scopeOnlySpecialStory() {
        assertFalse(BibleStoryHighlight.appliesTo("Gerizim y Ebal — versión fácil (Argentina)"));
        assertTrue(BibleStoryHighlight.appliesTo("Gerizim y Ebal — citas en rojo"));
    }

    @Test public void referencesAndCompleteVersesAreRedButNarrativeAndOtherQuotesStayNormal() {
        String source = "Texto normal.\\n"
                + "Jeremías, capítulo dos, versículo ocho.\\n"
                + "«Los sacerdotes no dijeron: ¿Dónde está Jehová?\\n"
                + "y los profetas profetizaron en Baal.»\\n"
                + "La palabra «tof» significa tambor.\\n"
                + "«Hemos pecado contra Ti».";
        source = source.replace("\\n", "\n");
        List<String> parts = Sentences.split(source);
        boolean[] marked = BibleStoryHighlight.mark(source);
        assertEquals(parts.size(), marked.length);
        int referenceCount = 0, bibleLines = 0, otherQuotes = 0;
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            if (part.startsWith("Jeremías, capítulo")) {
                assertTrue(marked[i]); referenceCount++;
            } else if (part.startsWith("«Los sacerdotes") || part.startsWith("y los profetas")
                    || part.startsWith("«Hemos pecado")) {
                assertTrue(marked[i]); bibleLines++;
            } else if (part.contains("«tof»")) {
                assertFalse(marked[i]); otherQuotes++;
            } else {
                assertFalse(marked[i]);
            }
        }
        assertEquals(1, referenceCount);
        assertEquals(3, bibleLines);
        assertEquals(1, otherQuotes);
    }

    @Test public void separateReferencesDoNotColorNarrationBetweenQuotations() {
        String source = "Deuteronomio, capítulo once.\n«Bendición.»\n"
                + "Esto es narración.\nSofonías, capítulo dos.\n«Maldición.»\nFinal.";
        boolean[] flags = BibleStoryHighlight.mark(source);
        List<String> parts = Sentences.split(source);
        assertEquals(6, flags.length);
        for (int i=0;i<6;i++) assertEquals("index " + i, i != 2 && i != 5, flags[i]);
        assertEquals(6, parts.size());
    }
}
