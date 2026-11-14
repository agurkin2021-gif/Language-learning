package ru.spanish.learning;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

/**
 * Locally cached Google Android TTS. Not a Google Translate audio API.
 * Stored separately from the user's human microphone recordings.
 */
final class GoogleStoryAudio {
    private final File directory;
    private final List<String> sentences;
    private final String language;

    GoogleStoryAudio(Context context, String profile, String language, long id, String body) {
        if (id < 0) throw new IllegalArgumentException("Missing story ID");
        sentences = Sentences.split(body == null ? "" : body);
        String member = StudyProfile.normalized(profile);
        String lang = "en".equals(language) ? "en" : "es";
        this.language = lang;
        directory = new File(new File(new File(new File(context.getFilesDir(),
                "google-story-audio"), member), lang),
                id + "-" + digest(body == null ? "" : body));
    }

    private static String digest(String text) {
        try {
            byte[] sha = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < 12; i++) result.append(String.format(Locale.ROOT, "%02x", sha[i] & 255));
            return result.toString();
        } catch (Exception problem) {
            throw new IllegalStateException("SHA-256 unavailable", problem);
        }
    }

    int count() { return sentences.size(); }
    String sentence(int i) { return sentences.get(i); }

    File segment(int index) {
        if (index < 0 || index >= sentences.size()) throw new IndexOutOfBoundsException();
        // Reuse existing clips unless their spoken form changed. Verse references
        // were previously recorded with clock/time/dash pronunciation; those
        // clips must be regenerated rather than replayed from the old cache.
        String raw = sentences.get(index);
        String normalized = RioplatensePronunciation.forSpeech(raw, language);
        String suffix = normalized.equals(raw) ? "" : "-speech-normalized-v4";
        return new File(directory, String.format(Locale.ROOT, "sentence-%05d%s.wav", index, suffix));
    }

    boolean recorded(int index) {
        File file = segment(index);
        if (!file.isFile() || file.length() < 48) return false;
        try (FileInputStream source = new FileInputStream(file)) {
            byte[] header = new byte[4];
            return source.read(header) == 4 && header[0] == 'R'
                    && header[1] == 'I' && header[2] == 'F' && header[3] == 'F';
        } catch (IOException ignored) { return false; }
    }

    boolean complete() {
        if (sentences.isEmpty()) return false;
        for (int i = 0; i < sentences.size(); i++) if (!recorded(i)) return false;
        return true;
    }

    File staging(int i) throws IOException {
        segment(i);
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Не удалось создать каталог Google-аудио");
        return new File(directory, String.format(Locale.ROOT, "pending-%05d.wav", i));
    }

    void publish(int i, File staging) throws IOException {
        if (!staging.isFile() || staging.length() < 48)
            throw new IOException("Google не создал аудиофайл");
        try (FileInputStream source = new FileInputStream(staging)) {
            byte[] header = new byte[4];
            if (source.read(header) != 4 || header[0] != 'R'
                    || header[1] != 'I' || header[2] != 'F' || header[3] != 'F')
                throw new IOException("Неподдерживаемый формат аудио Google");
        }
        Files.move(staging.toPath(), segment(i).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    void deleteAll() {
        File[] clips = directory.listFiles();
        if (clips != null) for (File file : clips) if (file.isFile()) file.delete();
        directory.delete();
    }

    static void deleteLanguage(Context context, String profile, String language) {
        File root = new File(new File(new File(context.getFilesDir(),
                "google-story-audio"), StudyProfile.normalized(profile)),
                "en".equals(language) ? "en" : "es");
        File[] folders = root.listFiles();
        if (folders != null) for (File folder : folders) {
            File[] clips = folder.listFiles();
            if (clips != null) for (File file : clips) if (file.isFile()) file.delete();
            folder.delete();
        }
        root.delete();
    }
}
