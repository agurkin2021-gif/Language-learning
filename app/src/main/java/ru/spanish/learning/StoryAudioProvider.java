package ru.spanish.learning;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Read-only, time-scoped URI grants for finished audio attachments only. */
public final class StoryAudioProvider extends ContentProvider {
    private static final String FOLDER = "shared-story-audio";

    static File folder(Context context) { return new File(context.getCacheDir(), FOLDER); }

    static String filename(String title, String token) {
        String label = title == null ? "" :
                title.replaceAll("[^\\p{L}\\p{N} _-]", " ").trim().replaceAll("\\s+", " ");
        if (label.length() > 48) label = label.substring(0, 48).trim();
        if (label.isEmpty()) label = "Рассказ";
        return label + "-" + token + ".wav";
    }

    static Uri uri(Context context, String filename) {
        return new Uri.Builder().scheme("content")
                .authority(context.getPackageName() + ".storyaudio")
                .appendPath("audio").appendPath(filename).build();
    }

    private File allowedFile(Uri uri) throws FileNotFoundException {
        if (uri == null || !("content".equals(uri.getScheme()))
                || getContext() == null
                || !((getContext().getPackageName() + ".storyaudio").equals(uri.getAuthority()))
                || uri.getPathSegments().size() != 2
                || !"audio".equals(uri.getPathSegments().get(0)))
            throw new FileNotFoundException("Invalid audio URI");
        String name = uri.getLastPathSegment();
        if (name == null || !name.matches("[\\p{L}\\p{N} _-]{1,48}-[a-f0-9]{32}\\.wav"))
            throw new FileNotFoundException("Invalid shared audio name");
        File file = new File(folder(getContext()), name);
        if (!file.isFile()) throw new FileNotFoundException(name);
        return file;
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "audio/wav"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                   String[] selectionArgs, String sortOrder) {
        try {
            File file = allowedFile(uri);
            String[] columns = projection == null
                    ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor result = new MatrixCursor(columns);
            Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = file.getName();
                if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
            }
            result.addRow(row);
            return result;
        } catch (FileNotFoundException notFound) { return null; }
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(allowedFile(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read only");
    }
    @Override public int delete(Uri uri, String where, String[] args) {
        throw new UnsupportedOperationException("Read only");
    }
    @Override public int update(Uri uri, ContentValues values, String where, String[] args) {
        throw new UnsupportedOperationException("Read only");
    }
}
