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

    @Test public void slowExactlySixRequestedWords() {
        String source = "LOS libros, las casas, DEL pueblo, el templo, LA mesa y les dije. Él vio al niño y una calle.";
        List<ArticleSlowSpeech.Part> parts = ArticleSlowSpeech.split(source);
        StringBuilder reconstructed = new StringBuilder();
        java.util.List<String> slow = new java.util.ArrayList<>();
        for (ArticleSlowSpeech.Part part : parts) {
            assertEquals(reconstructed.length(), part.start());
            assertFalse(part.text().trim().isEmpty());
            reconstructed.append(part.text());
            if (part.slow()) slow.add(part.text().trim());
        }
        assertEquals(source, reconstructed.toString());
        assertEquals(java.util.List.of("LOS", "las", "DEL", "el", "LA", "les"), slow);
    }

    @Test public void neverSlowAnotherShortWordOrEmbeddedFragments() {
        String source = "de que se va con y por eso. Él leyó la carta del amigo: les dijo.";
        List<ArticleSlowSpeech.Part> parts = ArticleSlowSpeech.split(source);
        StringBuilder rebuilt = new StringBuilder();
        java.util.List<String> slow = new java.util.ArrayList<>();
        for (ArticleSlowSpeech.Part part : parts) {
            assertEquals(rebuilt.length(), part.start());
            rebuilt.append(part.text());
            if (part.slow()) slow.add(part.text().trim());
        }
        assertEquals(source, rebuilt.toString());
        assertEquals(java.util.List.of("la", "del", "les"), slow);
    }

    @Test public void noFalsePositivesForLongWordsAndAccents() {
        String source = "palabras delantal elección lástima ellos él LOS";
        List<ArticleSlowSpeech.Part> parts = ArticleSlowSpeech.split(source);
        assertEquals(java.util.List.of("LOS"), parts.stream()
                .filter(ArticleSlowSpeech.Part::slow).map(p -> p.text().trim()).toList());
    }

    @Test public void emptyAndNoShortWords() {
        assertEquals(1, ArticleSlowSpeech.split("").size());
        assertFalse(ArticleSlowSpeech.split("Jeremías hablaba.").get(0).slow());
    }
}
