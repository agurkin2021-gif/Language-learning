package ru.spanish.learning;

import android.test.AndroidTestCase;

/** Copy exactly the saved Papa expressions, including mastered cards, to Pimen only. */
public final class PapaPimenPhraseCopyTest extends AndroidTestCase {
    private static Store.Card findPhrase(Store store, String phrase) {
        for (Store.Card card : store.vocabulary()) {
            if (card.kind() == 1 && phrase.equals(card.spanish())) return card;
        }
        return null;
    }

    public void testCopyAllSpanishPhrasesWithoutMasteryOrDuplicates() {
        String papaDb = "test-phrases-papa-es.db";
        String pimenDb = "test-phrases-pimen-es.db";
        getContext().deleteDatabase(papaDb);
        getContext().deleteDatabase(pimenDb);
        try (Store papa = Store.forProfileTest(getContext(), papaDb, "papa", false);
             Store pimen = Store.forProfileTest(getContext(), pimenDb, "pimen", false)) {
            papa.addCard("Yo pienso que sí", "Я думаю, что да", 1, 0);
            papa.addCard("Te llamé ayer", "Я позвонил тебе вчера", 1, 0);
            Store.Card learned = findPhrase(papa, "Yo pienso que sí");
            assertNotNull(learned);
            papa.setMastered(learned.id(), true);

            int originalTargetCount = pimen.vocabulary().size();
            int added = pimen.copyPhrasesFrom(papa);
            assertTrue("Expected both new Papa phrases", added >= 2);
            assertEquals(originalTargetCount + added, pimen.vocabulary().size());
            Store.Card copied = findPhrase(pimen, "Yo pienso que sí");
            assertNotNull("Mastered Papa phrase must also be copied", copied);
            assertEquals("Я думаю, что да", copied.meaning());
            assertFalse("Pimen progress is independent", copied.mastered());
            assertEquals(0, pimen.copyPhrasesFrom(papa));

            assertTrue(pimen.deleteCard(copied.id()));
            assertEquals(0, pimen.copyPhrasesFrom(papa));
            assertNull("Do not restore deliberately deleted phrase", findPhrase(pimen, "Yo pienso que sí"));
            assertTrue("Papa still retains mastered phrase", findPhrase(papa, "Yo pienso que sí").mastered());

            papa.addCard("Mañana te llamo", "Завтра я тебе позвоню", 1, 0);
            assertEquals(1, pimen.copyPhrasesFrom(papa));
            assertNotNull(findPhrase(pimen, "Mañana te llamo"));
        } finally {
            getContext().deleteDatabase(papaDb);
            getContext().deleteDatabase(pimenDb);
        }
    }

    public void testBothEnglishAndSpanishStaySeparate() {
        String enPapa = "test-phrases-papa-en.db";
        String enPimen = "test-phrases-pimen-en.db";
        String esPimen = "test-phrases-pimen-es2.db";
        for (String name : new String[]{enPapa, enPimen, esPimen}) getContext().deleteDatabase(name);
        try (Store papa = Store.forProfileTest(getContext(), enPapa, "papa", true);
             Store pimen = Store.forProfileTest(getContext(), enPimen, "pimen", true);
             Store spanish = Store.forProfileTest(getContext(), esPimen, "pimen", false)) {
            papa.addCard("I need to know", "Мне нужно знать", 1, 0);
            assertEquals(1, pimen.copyPhrasesFrom(papa));
            assertNotNull(findPhrase(pimen, "I need to know"));
            assertNull(findPhrase(spanish, "I need to know"));
            try {
                spanish.copyPhrasesFrom(papa);
                fail("Cross-language copy must fail");
            } catch (IllegalArgumentException expected) {}
        } finally {
            for (String name : new String[]{enPapa, enPimen, esPimen}) getContext().deleteDatabase(name);
        }
    }
}
