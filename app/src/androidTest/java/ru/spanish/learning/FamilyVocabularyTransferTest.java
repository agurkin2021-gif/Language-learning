package ru.spanish.learning;

import android.test.AndroidTestCase;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** Real SQLite and file-system checks; disposable test databases only. */
public final class FamilyVocabularyTransferTest extends AndroidTestCase {
    public void testTranslatedPapaCardsCopyWithoutOverwritingOrResurrectingTargetData() {
        String papaDb = "test-family-transfer-papa.db";
        String mamaDb = "test-family-transfer-mama.db";
        String dimaDb = "test-family-transfer-dima.db";
        for (String db : new String[]{papaDb, mamaDb, dimaDb})
            getContext().deleteDatabase(db);
        try (Store papa = Store.forProfileTest(getContext(), papaDb, StudyProfile.PAPA, false);
             Store mama = Store.forProfileTest(getContext(), mamaDb, StudyProfile.MAMA, false);
             Store dima = Store.forProfileTest(getContext(), dimaDb, StudyProfile.DIMA, false)) {
            String personal = "transferencia familiar de prueba";
            papa.addCard(personal, "личный перевод папы", 0, 2);
            long papaId = -1;
            for (Store.Card card : papa.vocabulary())
                if (personal.equals(card.spanish())) papaId = card.id();
            assertTrue(papaId > 0);
            papa.setMastered(papaId, true);
            assertTrue(papa.containsCard(personal, 0));

            // Existing Mama translation and mastery must always win over Papa.
            String mamaOwn = "palabra exclusiva mama transfer";
            mama.addCard(mamaOwn, "свой перевод мамы", 0, 2);
            long mamaOwnId = -1;
            for (Store.Card card : mama.vocabulary())
                if (mamaOwn.equals(card.spanish())) mamaOwnId = card.id();
            mama.setMastered(mamaOwnId, true);

            assertTrue(mama.copyTranslatedVocabularyFrom(papa) > 0);
            assertTrue(dima.copyTranslatedVocabularyFrom(papa) > 0);
            assertEquals(0, mama.copyTranslatedVocabularyFrom(papa));
            assertEquals(0, dima.copyTranslatedVocabularyFrom(papa));
            boolean foundMama = false, foundDima = false, foundMamaOwn = false;
            long copiedId = -1;
            for (Store.Card card : mama.vocabulary()) {
                if (personal.equals(card.spanish())) {
                    foundMama = true;
                    copiedId = card.id();
                    assertEquals("личный перевод папы", card.meaning());
                    assertFalse("Copied word starts studying, not mastered", card.mastered());
                }
                if (mamaOwn.equals(card.spanish())) {
                    foundMamaOwn = true;
                    assertEquals("свой перевод мамы", card.meaning());
                    assertTrue(card.mastered());
                }
            }
            for (Store.Card card : dima.vocabulary())
                if (personal.equals(card.spanish())) foundDima = true;
            assertTrue(foundMama);
            assertTrue(foundDima);
            assertTrue(foundMamaOwn);

            // A deliberate deletion in a target is not undone by repeating the transfer.
            assertTrue(mama.deleteCard(copiedId));
            assertEquals(0, mama.copyTranslatedVocabularyFrom(papa));
            assertFalse(mama.containsCard(personal, 0));
            assertTrue(papa.vocabulary().stream()
                    .anyMatch(card -> personal.equals(card.spanish()) && card.mastered()));
        } finally {
            for (String db : new String[]{papaDb, mamaDb, dimaDb})
                getContext().deleteDatabase(db);
        }
    }

    public void testLimitedTransferSkipsStudyingMasteredDeletedAndPhrasesIndependently() {
        String[] dbNames = {
                "test-limited-papa-en.db", "test-limited-mama-en.db",
                "test-limited-dima-en.db"
        };
        for (String db : dbNames) getContext().deleteDatabase(db);
        try (Store papa = Store.forProfileTest(getContext(), dbNames[0], StudyProfile.PAPA, true);
             Store mama = Store.forProfileTest(getContext(), dbNames[1], StudyProfile.MAMA, true);
             Store dima = Store.forProfileTest(getContext(), dbNames[2], StudyProfile.DIMA, true)) {
            String[] words = {
                    "family test unique alpha", "family test unique beta",
                    "family test unique gamma", "family test unique delta",
                    "family test unique epsilon"
            };
            for (int i=0;i<words.length;i++)
                papa.addCard(words[i], "papa translation " + i, 0, 2);
            // Papa's mastered archive is still eligible as a transfer source.
            papa.setMastered(papa.vocabulary().get(0).id(), true);
            papa.addCard("family test expression one", "expression", 1, 0);

            mama.addCard(words[0], "Mama own studying definition", 0, 2);
            mama.addCard(words[1], "Mama own mastered definition", 0, 2);
            mama.setMastered(mama.vocabulary().get(1).id(), true);
            mama.addCard(words[2], "Previously deleted by Mama", 0, 2);
            long deleted = mama.vocabulary().get(2).id();
            assertTrue(mama.deleteCard(deleted));

            dima.addCard(words[0], "Dima own definition", 0, 2);
            dima.setMastered(dima.vocabulary().get(0).id(), true);

            List<Store.Card> forMama = mama.copyMissingWordsFrom(papa, 2);
            List<Store.Card> forDima = dima.copyMissingWordsFrom(papa, 2);
            assertEquals(2, forMama.size());
            assertEquals(words[3], forMama.get(0).spanish());
            assertEquals(words[4], forMama.get(1).spanish());
            assertEquals(2, forDima.size());
            assertEquals(words[1], forDima.get(0).spanish());
            assertEquals(words[2], forDima.get(1).spanish());

            // Repeating the transfer with a new limit adds the next missing word only.
            assertEquals(0, mama.copyMissingWordsFrom(papa, 2).size());
            List<Store.Card> dimaNext = dima.copyMissingWordsFrom(papa, 1);
            assertEquals(1, dimaNext.size());
            assertEquals(words[3], dimaNext.get(0).spanish());

            // "All" takes every remaining missing word in ONLY the selected target.
            List<Store.Card> allRemaining = dima.copyMissingWordsFrom(papa, Integer.MAX_VALUE);
            assertEquals(1, allRemaining.size());
            assertEquals(words[4], allRemaining.get(0).spanish());
            assertEquals(0, dima.copyMissingWordsFrom(papa, Integer.MAX_VALUE).size());
            assertEquals(0, mama.copyMissingWordsFrom(papa, Integer.MAX_VALUE).size());

            assertFalse(mama.containsCard(words[2], 0));
            assertFalse(mama.containsCard("family test expression one", 1));
            assertFalse(dima.containsCard("family test expression one", 1));
            assertEquals("Mama own studying definition", mama.vocabulary().get(0).meaning());
            assertFalse(mama.vocabulary().get(0).mastered());
            assertEquals("Mama own mastered definition", mama.vocabulary().get(1).meaning());
            assertTrue(mama.vocabulary().get(1).mastered());
            assertEquals("Dima own definition", dima.vocabulary().get(0).meaning());
            assertTrue(dima.vocabulary().get(0).mastered());

            for (Store.Card card : mama.vocabulary()) {
                if (words[3].equals(card.spanish()) || words[4].equals(card.spanish())) {
                    assertEquals(0, card.reviewCount());
                    assertFalse(card.mastered());
                    assertTrue(card.meaning().startsWith("papa translation"));
                }
            }
            assertEquals(6, papa.vocabulary().size()); // Source was never modified.
            try {
                mama.copyMissingWordsFrom(papa, 0);
                fail("Zero must be rejected");
            } catch (IllegalArgumentException expected) {}
        } finally {
            for (String db : dbNames) getContext().deleteDatabase(db);
        }
    }

    public void testTransferRejectsPapaAndPimenAsRecipients() throws Exception {
        for (String invalid : new String[]{StudyProfile.PAPA, StudyProfile.PIMEN, "unknown"}) {
            try {
                FamilyVocabularyTransfer.copyFromPapa(getContext(), invalid, 10, false);
                fail("Must reject target " + invalid);
            } catch (IllegalArgumentException expected) {}
        }
        try {
            FamilyVocabularyTransfer.copyFromPapa(getContext(), StudyProfile.MAMA, 0, false);
            fail("Must reject zero word limit");
        } catch (IllegalArgumentException expected) {}
    }

    public void testCopiesExistingPapaSvgAndEmojiButNeverReplacesTargetArt() throws Exception {
        String word = "exclusivo para transferir imagen familiar";
        String key = CardImageLoader.key(word);
        File files = getContext().getFilesDir();
        File papaEs = new File(files, StudyProfile.imageDirectory(StudyProfile.PAPA, false));
        File papaEn = new File(files, StudyProfile.imageDirectory(StudyProfile.PAPA, true));
        File mamaEs = new File(files, StudyProfile.imageDirectory(StudyProfile.MAMA, false));
        File dimaEs = new File(files, StudyProfile.imageDirectory(StudyProfile.DIMA, false));
        File mamaEn = new File(files, StudyProfile.imageDirectory(StudyProfile.MAMA, true));
        File[] paths = {
                new File(papaEs, key + ".svg"), new File(papaEn, key + ".emoji"),
                new File(mamaEs, key + ".svg"), new File(dimaEs, key + ".svg"),
                new File(mamaEn, key + ".emoji")
        };
        List<Store.Card> sample = List.of(new Store.Card(1, word, "перевод",
                0, 2, false, 0, 0, 0));
        try {
            papaEs.mkdirs();
            papaEn.mkdirs();
            mamaEs.mkdirs();
            Files.write(paths[0].toPath(), "<svg><rect width=\"200\" height=\"200\" /></svg>".getBytes(StandardCharsets.UTF_8));
            Files.write(paths[1].toPath(), "🚗".getBytes(StandardCharsets.UTF_8));
            Files.write(paths[2].toPath(), "<svg>own Mama image</svg>".getBytes(StandardCharsets.UTF_8));
            assertEquals(0, FamilyVocabularyTransfer.copyPictures(getContext(), sample, StudyProfile.MAMA, false));
            assertEquals(1, FamilyVocabularyTransfer.copyPictures(getContext(), sample, StudyProfile.DIMA, false));
            assertEquals(0, FamilyVocabularyTransfer.copyPictures(getContext(), sample, StudyProfile.DIMA, false));
            assertEquals(1, FamilyVocabularyTransfer.copyPictures(getContext(), sample, StudyProfile.MAMA, true));
            assertEquals(new String(Files.readAllBytes(paths[0].toPath()), StandardCharsets.UTF_8), new String(Files.readAllBytes(paths[3].toPath()), StandardCharsets.UTF_8));
            assertEquals("🚗", new String(Files.readAllBytes(paths[4].toPath()), StandardCharsets.UTF_8));
            assertEquals("<svg>own Mama image</svg>", new String(Files.readAllBytes(paths[2].toPath()), StandardCharsets.UTF_8));
        } finally {
            for (File file : paths) Files.deleteIfExists(file.toPath());
        }
    }
}
