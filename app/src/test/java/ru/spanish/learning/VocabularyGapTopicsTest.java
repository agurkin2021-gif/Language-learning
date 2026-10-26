package ru.spanish.learning;

import org.junit.Test;
import static org.junit.Assert.*;

public final class VocabularyGapTopicsTest {
    @Test public void exposesAllThirteenTopicsInOrder() {
        String[] topics = VocabularyGapTopics.names();
        assertArrayEquals(new String[]{
                "Связная речь и рассказы",
                "Путешествия: аэропорт и паспортный контроль",
                "Миграционная служба",
                "ТВ-новости и политика",
                "Бытовое общение",
                "Покупки продуктов",
                "Богословие и чтение Библии",
                "Салон красоты: общение с мастером",
                "Доктор и здоровье",
                "Школа и дети",
                "Животные",
                "Туризм и отдых",
                "Спорт: волейбол"
        },topics);
        topics[0] = "переписано";
        assertEquals("Связная речь и рассказы", VocabularyGapTopics.names()[0]);
    }

    @Test public void everyTopicAnalyzesBothArchivesAndRespectsTenCardLimit() {
        for (int i = 0; i < VocabularyGapTopics.names().length; i++) {
            String prompt = VocabularyGapTopics.prompt(i, false);
            assertTrue(VocabularyGapTopics.isGapAnalysis(prompt));
            assertTrue(prompt.contains("максимум 10 новых слов и устойчивых выражений"));
            assertTrue(prompt.contains("освоенные"));
            assertTrue(prompt.contains("изучаемые"));
            assertTrue(prompt.contains("не добавляй"));
            assertTrue(prompt.contains("рассказ"));
            assertTrue(prompt.contains("SVG"));
            assertTrue(prompt.contains("точный русский"));
            assertTrue(prompt.contains("никаких инфинитивных заготовок"));
            assertTrue(prompt.contains(VocabularyGapTopics.names()[i]));
            assertEquals(10, new CardAdditionPolicy.Batch(prompt).limit);
        }
    }

    @Test public void keepsEnglishAndSpanishSeparatedAndChecksTopicBounds() {
        String es = VocabularyGapTopics.prompt(2, false);
        String en = VocabularyGapTopics.prompt(2, true);
        assertTrue(es.contains("Rioplatense"));
        assertTrue(es.contains("vos/tenés"));
        assertTrue(en.contains("английский"));
        assertFalse(en.contains("Rioplatense"));
        assertFalse(en.contains("никаких инфинитивных заготовок"));
        assertFalse(VocabularyGapTopics.isGapAnalysis("Добавь 10 слов"));
        try { VocabularyGapTopics.prompt(-1, false); fail("Negative index"); }
        catch (IllegalArgumentException expected) {}
        try { VocabularyGapTopics.prompt(13, true); fail("Out-of-range index"); }
        catch (IllegalArgumentException expected) {}
    }
}
