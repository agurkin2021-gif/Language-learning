package ru.spanish.learning;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;

public class ArticleSlowSpeechTest {
    @Test public void allSpanishStoriesIncludingNormalAndBibleCopies() {
        // Does not depend on any title or profile: the setting applies globally.
        assertTrue(ArticleSlowSpeech.appliesTo("es", false, .45f));
        assertTrue(ArticleSlowSpeech.appliesTo("es", false, 1f));
        assertFalse(ArticleSlowSpeech.appliesTo("es", false, 0f));
        assertFalse(ArticleSlowSpeech.appliesTo("en", false, .45f));
        assertFalse(ArticleSlowSpeech.appliesTo("es", true, .45f));
    }

    @Test public void slowEveryWordUpToThreeLettersPlusFourLetterArticles() {
        String source = "Él vio el libro y la mesa, los niños, las casas; un niño, una niña, unos libros, unas fotos, al monte, del pueblo.";
        List<ArticleSlowSpeech.Part> parts = ArticleSlowSpeech.split(source);
        StringBuilder reconstructed = new StringBuilder();
        java.util.List<String> slow = new java.util.ArrayList<>();
        for (ArticleSlowSpeech.Part part : parts) {
            assertEquals(reconstructed.length(), part.start());
            reconstructed.append(part.text());
            if (part.slow()) slow.add(part.text().trim());
        }
        assertEquals(source, reconstructed.toString());
        assertEquals(java.util.List.of("Él", "vio", "el", "y", "la", "los", "las",
                "un", "una", "unos", "unas", "al", "del"), slow);
    }

    @Test public void neverBreakInsideWordsOrReadWhiteSpaceAlone() {
        String source = "El libro, palabras, él, alguien, delantal y una.";
        List<ArticleSlowSpeech.Part> parts = ArticleSlowSpeech.split(source);
        StringBuilder reconstructed = new StringBuilder();
        for (ArticleSlowSpeech.Part part : parts) {
            assertFalse(part.text().trim().isEmpty());
            assertEquals(reconstructed.length(), part.start());
            reconstructed.append(part.text());
        }
        assertEquals(source, reconstructed.toString());
        assertEquals(4, parts.stream().filter(ArticleSlowSpeech.Part::slow).count());
    }

    @Test public void shortEverydayWordsAreSlowedButLongOnesAreNot() {
        String source = "de la casa, que se va con su hijo en el tren y por eso.";
        List<ArticleSlowSpeech.Part> parts = ArticleSlowSpeech.split(source);
        StringBuilder rebuilt = new StringBuilder();
        java.util.List<String> slow = new java.util.ArrayList<>();
        for (ArticleSlowSpeech.Part part : parts) {
            assertEquals(rebuilt.length(), part.start());
            rebuilt.append(part.text());
            if (part.slow()) slow.add(part.text().trim());
        }
        assertEquals(source, rebuilt.toString());
        assertEquals(java.util.List.of("de", "la", "que", "se", "va", "con",
                "su", "en", "el", "y", "por", "eso"), slow);
    }

    @Test public void emptyAndNoShortWords() {
        assertEquals(1, ArticleSlowSpeech.split("").size());
        assertFalse(ArticleSlowSpeech.split("Jeremías hablaba.").get(0).slow());
    }
}
