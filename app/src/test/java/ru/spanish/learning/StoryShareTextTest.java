package ru.spanish.learning;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StoryShareTextTest {
    @Test public void sharesOriginalAndRussianWithTitleAndNoTruncation() {
        String original = "Hola. ".repeat(1800) + "\n¡Vamos!";
        String translation = "Привет. ".repeat(1800) + "\nПойдём!";
        String result = StoryShareText.compose("  La historia  ", original, translation);
        assertEquals("La historia\n\n" + original + "\n\nПеревод:\n" + translation, result);
        assertTrue(result.endsWith("Пойдём!"));
    }

    @Test public void doesNotInventMissingRussianTranslation() {
        assertEquals("Título\n\nTexto completo", StoryShareText.compose("Título", "Texto completo", " "));
        assertFalse(StoryShareText.compose("Título", "Texto completo", "").contains("Перевод:"));
    }

    @Test public void nullFieldsRemainSafe() {
        assertEquals("", StoryShareText.compose(null, null, null));
        assertEquals("Texto", StoryShareText.compose(null, "Texto", null));
    }
}
