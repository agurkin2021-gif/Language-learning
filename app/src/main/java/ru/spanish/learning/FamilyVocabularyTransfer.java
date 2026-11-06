package ru.spanish.learning;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Explicit, local Papa -> Mama/Dima transfer. Never overwrites either target profile. */
final class FamilyVocabularyTransfer {
    record Result(int mamaCards, int dimaCards, int mamaPictures, int dimaPictures) {}

    private FamilyVocabularyTransfer() {}

    static Result copyFromPapa(Context context) throws IOException {
        int mamaCards = 0;
        int dimaCards = 0;
        int mamaPictures = 0;
        int dimaPictures = 0;
        for (boolean english : new boolean[]{false, true}) {
            try (Store papa = Store.forProfile(context, StudyProfile.PAPA, english);
                 Store mama = Store.forProfile(context, StudyProfile.MAMA, english);
                 Store dima = Store.forProfile(context, StudyProfile.DIMA, english)) {
                // Complete local vocabulary, including the mastered archive and all translations.
                List<Store.Card> cards = papa.vocabulary();
                mamaCards += mama.copyTranslatedVocabularyFrom(papa);
                dimaCards += dima.copyTranslatedVocabularyFrom(papa);
                // Pictures are stored outside SQLite and outside the GitHub vocabulary JSON.
                // Copy them even for already-present target cards, but preserve existing art.
                mamaPictures += copyPictures(context, cards, StudyProfile.MAMA, english);
                dimaPictures += copyPictures(context, cards, StudyProfile.DIMA, english);
            }
        }
        return new Result(mamaCards, dimaCards, mamaPictures, dimaPictures);
    }

    static int copyPictures(Context context, List<Store.Card> cards,
                            String targetProfile, boolean english) throws IOException {
        if (!StudyProfile.MAMA.equals(targetProfile) && !StudyProfile.DIMA.equals(targetProfile))
            throw new IllegalArgumentException("Target must be Mama or Dima");
        File root = context.getFilesDir();
        File sourceDir = new File(root, StudyProfile.imageDirectory(StudyProfile.PAPA, english));
        File targetDir = new File(root, StudyProfile.imageDirectory(targetProfile, english));
        if (!sourceDir.isDirectory()) return 0;
        int copied = 0;
        for (Store.Card card : cards) {
            if (card.spanish() == null || card.spanish().isBlank()
                    || card.meaning() == null || card.meaning().isBlank()) continue;
            String key = CardImageLoader.key(card.spanish());
            File toSvg = new File(targetDir, key + ".svg");
            File toEmoji = new File(targetDir, key + ".emoji");
            if (toSvg.exists() || toEmoji.exists()) continue; // Preserve custom target pictures.
            File fromEmoji = new File(sourceDir, key + ".emoji");
            File fromSvg = new File(sourceDir, key + ".svg");
            File source = fromEmoji.isFile() ? fromEmoji : (fromSvg.isFile() ? fromSvg : null);
            if (source == null || source.length() == 0) continue;
            if (!targetDir.exists() && !targetDir.mkdirs() && !targetDir.isDirectory())
                throw new IOException("Cannot create picture directory: " + targetProfile);
            File target = new File(targetDir, source.getName());
            Path temp = Files.createTempFile(targetDir.toPath(), ".transfer-", ".tmp");
            try {
                Files.copy(source.toPath(), temp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temp, target.toPath()); // No overwrite if another operation created it.
                copied++;
            } finally {
                Files.deleteIfExists(temp);
            }
        }
        return copied;
    }
}
