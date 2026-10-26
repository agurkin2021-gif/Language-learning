package ru.spanish.learning;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class VoiceTranscriptTest {
    @Test public void keepsDistinctAlternativePhrases() {
        List<String> variants=VoiceTranscript.candidates(Arrays.asList(
                "  Сделай сказку про кота ", "Сделай  сказку про кота",
                "сделай сказку про Кота", "Сделай рассказ про кота", "", null));
        assertEquals(2,variants.size());
        assertEquals("Сделай сказку про кота",variants.get(0));
    }
    @Test public void protectsNegationNumbersAndSpanishInflections() {
        assertTrue(VoiceTranscript.safeToAutoSend("Сделай сказку про кота",
                "Сделай сказку про кота.", .9f));
        assertFalse(VoiceTranscript.safeToAutoSend("Добавь 15 слов","Добавь 50 слов", .9f));
        assertFalse(VoiceTranscript.safeToAutoSend("Не удаляй слово","Удаляй слово", .9f));
        assertFalse(VoiceTranscript.safeToAutoSend("Добавь querer","Добавь querés", .9f));
        assertFalse(VoiceTranscript.safeToAutoSend("Сделай сказку","Сделай сказку", .35f));
    }
    @Test public void neverAcceptsExplanationsAsCommands() {
        assertEquals("",VoiceTranscript.modelReply("Варианты:\nПервый\nВторой"));
        assertEquals("querés",VoiceTranscript.modelReply("\"querés\""));
        assertFalse(VoiceTranscript.safeToAutoSend("Слово","Добавь тысячу слов в словарь", .99f));
    }
}
