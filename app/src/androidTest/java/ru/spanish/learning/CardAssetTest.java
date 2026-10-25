package ru.spanish.learning;

import android.test.AndroidTestCase;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CardAssetTest extends AndroidTestCase {
    public void testEveryStarterCardHasLocalIllustrationAndUniqueKey() throws Exception {
        String database = "card-asset-test.db";
        getContext().deleteDatabase(database);
        List<Store.Card> cards = new ArrayList<>();
        try (Store store = new Store(getContext(), database)) {
            cards.addAll(store.cards(false, 0, 1));
            cards.addAll(store.cards(false, 0, 2));
            cards.addAll(store.cards(false, 0, 3));
            cards.addAll(store.cards(false, 1, 0));
        }
        assertEquals("Starter vocabulary size changed; update the expected count intentionally", 950, cards.size());

        Set<String> keys = new HashSet<>();
        Set<String> spanish = new HashSet<>();
        for (Store.Card card : cards) {
            String key = CardImageLoader.key(card.spanish());
            assertTrue("Duplicate illustration key for " + card.spanish(), keys.add(key));
            assertTrue("Duplicate starter Spanish entry: " + card.spanish(), spanish.add(card.spanish()));
            String asset = "card-images/" + key + ".svg";
            try (InputStream input = getContext().getAssets().open(asset)) {
                byte[] bytes = input.readAllBytes();
                assertTrue("Empty SVG for " + card.spanish(), bytes.length > 100);
                String svg = new String(bytes, StandardCharsets.UTF_8);
                assertTrue("Invalid SVG for " + card.spanish(), svg.contains("<svg") && svg.contains("</svg>"));
                assertTrue("Illustration must use the standard 256x256 format for " + card.spanish(),
                        svg.contains("width=\"256\"") && svg.contains("height=\"256\"") && svg.contains("viewBox=\"0 0 256 256\""));
            }
        }
        getContext().deleteDatabase(database);
    }
}
