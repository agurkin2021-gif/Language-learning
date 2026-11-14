package ru.spanish.learning;

import java.util.ArrayList;
import java.util.List;

/** A fixed-size active window over the unmastered cards already in the local dictionary. */
final class StudyBatch {
    private StudyBatch() {}

    /** 0 means the legacy unbounded view; every positive value is a batch size. */
    static List<Store.Card> active(List<Store.Card> unmastered, int limit) {
        int count = limit <= 0 ? unmastered.size() : Math.min(unmastered.size(), limit);
        return new ArrayList<>(unmastered.subList(0, count));
    }

    /** Separate phrase limit. Word preferences stay unchanged for upgrades from version 414. */
    static String preferenceKey(int kind, int category) {
        if (kind == 1) return "studyBatchPhrases";
        if (kind != 0) throw new IllegalArgumentException("kind");
        return preferenceKey(category);
    }

    /** Unique limits for All / Verbs / Nouns / Adjectives; profile and language are scoped by prefKey. */
    static String preferenceKey(int category) {
        if (category < 0 || category > 3)
            throw new IllegalArgumentException("category");
        return "studyBatchCategory" + category;
    }
}
