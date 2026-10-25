package ru.spanish.learning;

import android.test.AndroidTestCase;

public class StoreTest extends AndroidTestCase {
    public void testPersistenceAndExplicitArchive() {
        getContext().deleteDatabase("test-learning.db");
        Store s = new Store(getContext(), "test-learning.db");

        int wordsBefore = s.cards(false, 0).size();
        int expressionsBefore = s.cards(false, 1).size();
        assertTrue(wordsBefore >= 900);
        assertTrue(expressionsBefore >= 50);

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

        long story = StoryGenerator.save(s, new StoryGenerator.Draft("Prueba", "Hola. Buen día.", "Тестовый адаптер"));
        s.position(story, 1);
        s.close();

        s = new Store(getContext(), "test-learning.db");
        assertEquals("Prueba", s.stories().get(0).title());
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
}
