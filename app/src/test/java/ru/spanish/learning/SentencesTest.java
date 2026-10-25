package ru.spanish.learning;
import org.junit.Test;
import static org.junit.Assert.*;
public class SentencesTest {
    @Test public void spanishPunctuationAndNewlines() {
        assertArrayEquals(new String[]{"¿Cómo estás?", "¡Bien!", "Voy a casa.", "Hasta mañana…"}, Sentences.split("¿Cómo estás? ¡Bien!\nVoy a casa. Hasta mañana…").toArray());
    }
    @Test public void emptyIgnored() { assertTrue(Sentences.split(" \n ").isEmpty()); }
    @Test public void longInputBoundedWithoutLoss() {
        String longWord="a".repeat(1000); assertEquals(longWord, String.join("",Sentences.split(longWord)));
        for(String part:Sentences.split(longWord)) assertTrue(part.length()<=240);
    }
}
