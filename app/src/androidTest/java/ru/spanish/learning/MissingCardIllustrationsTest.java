package ru.spanish.learning;

import android.test.AndroidTestCase;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** The repair excludes mastered cards and already illustrated ones. */
public final class MissingCardIllustrationsTest extends AndroidTestCase {
    public void testOnlyUnmasteredCardsWithoutPicturesAreSelected() throws Exception {
        String database = "missing-pictures-test.db";
        getContext().deleteDatabase(database);
        String unknown = "dibujopruebaunica90711";
        String known = "dibujopruebaconocida90712";
        File unknownImage = CardImageLoader.imageFile(getContext(), unknown, false);
        File knownImage = CardImageLoader.imageFile(getContext(), known, false);
        unknownImage.delete();
        knownImage.delete();
        try (Store store = new Store(getContext(), database)) {
            store.addCard(unknown, "тестовое незнакомое", 0, 2);
            store.addCard(known, "тестовое освоенное", 0, 2);
            long knownId = -1;
            for (Store.Card card : store.vocabulary())
                if (known.equals(card.spanish())) knownId = card.id();
            assertTrue(knownId > 0);
            store.setMastered(knownId, true);

            assertTrue("Starter emoji SVG is a placeholder, not a true drawing",
                    CardImageLoader.isBundledEmojiPlaceholder(getContext(), "ser"));
            assertFalse("The app should offer to redraw starter emoji cards",
                    CardImageLoader.hasIllustration(getContext(), "ser", false));
            List<Store.Card> missing = MissingCardIllustrations.pending(getContext(), store);
            assertTrue(missing.stream().anyMatch(c -> unknown.equals(c.spanish())));
            assertFalse(missing.stream().anyMatch(c -> known.equals(c.spanish())));

            File directory = unknownImage.getParentFile();
            assertTrue(directory.isDirectory() || directory.mkdirs());
            String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"256\" height=\"256\" viewBox=\"0 0 256 256\"><circle cx=\"128\" cy=\"128\" r=\"80\" fill=\"#22aabb\"/><path d=\"M70 128L124 182 191 85\" fill=\"none\" stroke=\"#ffffff\" stroke-width=\"16\"/></svg>";
            try (FileOutputStream output = new FileOutputStream(unknownImage)) {
                output.write(svg.getBytes(StandardCharsets.UTF_8));
            }
            assertTrue(CardImageLoader.hasIllustration(getContext(), unknown, false));
            assertFalse(MissingCardIllustrations.pending(getContext(), store)
                    .stream().anyMatch(c -> unknown.equals(c.spanish())));
            assertFalse(CardImageLoader.hasIllustration(getContext(), unknown, true));
        } finally {
            unknownImage.delete();
            knownImage.delete();
            getContext().deleteDatabase(database);
        }
    }
}
