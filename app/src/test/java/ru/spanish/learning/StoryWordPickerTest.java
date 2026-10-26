package ru.spanish.learning;

import org.junit.Test;
import static org.junit.Assert.*;

public final class StoryWordPickerTest {
    @Test public void capturesSpanishAccentsAndPunctuationWithoutSelectingAnything() {
        String text = "¿Querés tomar un mate? ¡Lucía dijo que sí!";
        StoryWordPicker.Match querer = StoryWordPicker.at(text, text.indexOf("querés") + 3);
        assertEquals("Querés", querer.word());
        assertEquals("Querés", text.substring(querer.start(), querer.end()));
        assertEquals("Lucía", StoryWordPicker.at(text, text.indexOf("Lucía") + 3).word());
        assertEquals("sí", StoryWordPicker.at(text, text.indexOf("sí")).word());
        assertNull(StoryWordPicker.at(text, text.indexOf('¿')));
        assertNull(StoryWordPicker.at(text, text.indexOf(' ')));
        assertNull(StoryWordPicker.at(text, text.indexOf('?')));
    }

    @Test public void recognizesIrregularConjugationsAsWholePrintedWords() {
        for (String word : new String[]{"fui", "tuve", "dije", "querés", "tenía", "había", "dámelo"}) {
            String text = "Ahora " + word + " acá.";
            assertEquals(word, StoryWordPicker.at(text, 6 + word.length() / 2).word());
        }
    }

    @Test public void supportsCompoundsAndNoManualSelectionOrFallbackOnWhitespace() {
        String text = "Es teórico-práctico, no d'água.";
        assertEquals("teórico-práctico", StoryWordPicker.at(text, text.indexOf('-')).word());
        assertEquals("d'água", StoryWordPicker.at(text, text.indexOf('\'')).word());
        assertNull(StoryWordPicker.at(text, -1));
        assertNull(StoryWordPicker.at(text, text.length()));
        assertNull(StoryWordPicker.at(text, text.indexOf(',')));
    }
}
