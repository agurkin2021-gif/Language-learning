package ru.spanish.learning;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Preserve user-created SVG and emoji art when only a card's written form is edited. */
final class CardImageMigration {
    private CardImageMigration() {}

    static void copyOnEdit(Context context, String previous, String updated, boolean english)
            throws IOException {
        if (previous == null || updated == null || previous.equals(updated)) return;
        File directory = new File(context.getFilesDir(),
                StudyProfile.imageDirectory(StudyProfile.active(context), english));
        String from = CardImageLoader.key(previous);
        String to = CardImageLoader.key(updated);
        if (from.equals(to)) return;
        copyIfMissing(new File(directory, from + ".svg"), new File(directory, to + ".svg"));
        copyIfMissing(new File(directory, from + ".emoji"), new File(directory, to + ".emoji"));
    }

    private static void copyIfMissing(File source, File destination) throws IOException {
        if (!source.isFile() || destination.exists()) return;
        File parent = destination.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs()))
            throw new IOException("Cannot create images directory");
        File temp = File.createTempFile("edit-", ".tmp", parent);
        try {
            try (FileInputStream input = new FileInputStream(source);
                 FileOutputStream output = new FileOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                output.getFD().sync();
            }
            if (!destination.exists() && !temp.renameTo(destination))
                throw new IOException("Cannot preserve card picture");
        } finally {
            if (temp.exists()) temp.delete();
        }
    }
}
