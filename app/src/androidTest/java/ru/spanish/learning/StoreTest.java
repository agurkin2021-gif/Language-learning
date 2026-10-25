package ru.spanish.learning;
import android.test.AndroidTestCase;
public class StoreTest extends AndroidTestCase {
    public void testPersistenceAndExplicitArchive() {
        getContext().deleteDatabase("test-learning.db");
        Store s=new Store(getContext(), "test-learning.db"); s.addCard("mate","мате",0); s.addCard("por favor","пожалуйста",1);
        assertEquals(0,s.mastered().size()); long id=s.cards(false,0).get(0).id();
        s.setMastered(id,true); s.close(); s=new Store(getContext(), "test-learning.db");
        assertEquals(1,s.mastered().size()); assertEquals(0,s.cards(false,0).size()); assertEquals(1,s.cards(false,1).size());
        long story=StoryGenerator.save(s,new StoryGenerator.Draft("Prueba","Hola. Buen día.","Тестовый адаптер")); s.position(story,1); s.close(); s=new Store(getContext(), "test-learning.db");
        assertEquals("Prueba",s.stories().get(0).title()); assertEquals(1,s.stories().get(0).position());
        s.setMastered(id,false); assertTrue(s.mastered().isEmpty()); s.close(); getContext().deleteDatabase("test-learning.db");
    }
}
