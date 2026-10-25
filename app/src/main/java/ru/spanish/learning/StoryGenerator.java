package ru.spanish.learning;

import java.util.List;

/** Integration boundary. A future consented adapter receives only mastered cards.
 * No network implementation, credentials, chat access or fabricated generation in v1.
 * Implementations must run off the UI thread and return a complete, attributed story.
 */
public interface StoryGenerator {
    record Draft(String title, String spanish, String provenance) {}
    Draft generate(List<Store.Card> mastered) throws Exception;
    static long save(Store store, Draft draft) {
        return store.addStory(draft.title(), draft.spanish(), draft.provenance());
    }
}
