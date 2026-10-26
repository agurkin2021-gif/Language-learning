package ru.spanish.learning;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/** Only active cards missing both a packaged and local illustration. */
final class MissingCardIllustrations {
    private MissingCardIllustrations() {}

    static List<Store.Card> pending(Context context, Store store) {
        List<Store.Card> missing = new ArrayList<>();
        for (Store.Card card : store.vocabulary()) {
            if (card.mastered()) continue;
            if (!CardImageLoader.hasIllustration(context, card.spanish(), store.isEnglish()))
                missing.add(card);
        }
        return missing;
    }
}
