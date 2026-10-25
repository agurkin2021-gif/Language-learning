package ru.spanish.learning;

import android.test.AndroidTestCase;

public class StoreTest extends AndroidTestCase {
    public void testPersistenceAndExplicitArchive() {
        getContext().deleteDatabase("test-learning.db");
        Store s = new Store(getContext(), "test-learning.db");

        int wordsBefore = s.cards(false, 0).size();
        int expressionsBefore = s.cards(false, 1).size();
        assertEquals(300, s.cards(false, 0, 1).size());
        assertEquals(300, s.cards(false, 0, 2).size());
        assertEquals(300, s.cards(false, 0, 3).size());
        assertEquals(900, wordsBefore);
        assertEquals(50, expressionsBefore);

        s.addCard("mate personalizado", "мате — пользовательская карточка", 0);
        s.addCard("por favor personalizado", "пожалуйста — пользовательская карточка", 1);
        assertEquals(wordsBefore + 1, s.cards(false, 0).size());
        assertEquals(expressionsBefore + 1, s.cards(false, 1).size());

        long id = -1;
        for (Store.Card card : s.cards(false, 0)) {
            if ("mate personalizado".equals(card.spanish())) id = card.id();
        }
        assertTrue(id > 0);
        s.setMastered(id, true);
        s.close();

        s = new Store(getContext(), "test-learning.db");
        boolean foundMastered = false;
        for (Store.Card card : s.mastered()) {
            if ("mate personalizado".equals(card.spanish())) foundMastered = true;
        }
        assertTrue(foundMastered);
        assertEquals(expressionsBefore + 1, s.cards(false, 1).size());

        long story = StoryGenerator.save(s, new StoryGenerator.Draft("Prueba", "Hola. Buen día.", "Привет. Добрый день.", "Тестовый адаптер"));
        s.position(story, 1);
        s.close();

        s = new Store(getContext(), "test-learning.db");
        assertEquals("Prueba", s.stories().get(0).title());
        assertEquals("Привет. Добрый день.", s.stories().get(0).translation());
        assertEquals(1, s.stories().get(0).position());
        s.setMastered(id, false);

        boolean returned = false;
        for (Store.Card card : s.cards(false, 0)) {
            if ("mate personalizado".equals(card.spanish())) returned = true;
        }
        assertTrue(returned);
        s.close();
        getContext().deleteDatabase("test-learning.db");
    }

    public void testCustomCategoryAndWeakReviewSchedulePersist() {
        String database = "test-review-schedule.db";
        getContext().deleteDatabase(database);
        long id = -1;
        try (Store s = new Store(getContext(), database)) {
            s.addCard("palabra personalizada", "слово для проверки", 0, 2);
            for (Store.Card card : s.cards(false, 0, 2)) {
                if ("palabra personalizada".equals(card.spanish())) {
                    id = card.id();
                    break;
                }
            }
            assertTrue(id > 0);
            s.review(id, 0, 5);
        }

        try (Store s = new Store(getContext(), database)) {
            Store.Card found = null;
            for (Store.Card card : s.cards(false, 0, 2)) if (card.id() == id) found = card;
            assertNotNull(found);
            assertEquals(0, found.lastResult());
            assertEquals(1, found.reviewCount());
            assertEquals(5, found.nextDue());
            s.review(id, 2, 0);
        }

        try (Store s = new Store(getContext(), database)) {
            boolean mastered = false;
            for (Store.Card card : s.cards(true, 0, 2)) if (card.id() == id) mastered = true;
            assertTrue(mastered);
        }
        getContext().deleteDatabase(database);
    }
}
