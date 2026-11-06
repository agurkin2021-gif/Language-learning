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
            Files.writeString(paths[0].toPath(), "<svg><rect width=\"200\" height=\"200\" /></svg>",
                    StandardCharsets.UTF_8);
            Files.writeString(paths[1].toPath(), "🚗", StandardCharsets.UTF_8);
            Files.writeString(paths[2].toPath(), "<svg>own Mama image</svg>", StandardCharsets.UTF_8);
            assertEquals(0, FamilyVocabularyTransfer.copyPictures(getContext(), sample, StudyProfile.MAMA, false));
            assertEquals(1, FamilyVocabularyTransfer.copyPictures(getContext(), sample, StudyProfile.DIMA, false));
            assertEquals(0, FamilyVocabularyTransfer.copyPictures(getContext(), sample, StudyProfile.DIMA, false));
            assertEquals(1, FamilyVocabularyTransfer.copyPictures(getContext(), sample, StudyProfile.MAMA, true));
            assertEquals(Files.readString(paths[0].toPath()), Files.readString(paths[3].toPath()));
            assertEquals("🚗", Files.readString(paths[4].toPath()));
            assertEquals("<svg>own Mama image</svg>", Files.readString(paths[2].toPath()));
        } finally {
            for (File file : paths) Files.deleteIfExists(file.toPath());
        }
    }
}
