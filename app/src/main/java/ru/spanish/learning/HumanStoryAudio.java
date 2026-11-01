package ru.spanish.learning;

import android.content.Context;
import android.media.MediaRecorder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.List;

/**
 * Offline human narration: one AAC/M4A file per spoken sentence.
 * Kept in app-private durable storage, never in a ChatGPT request.
 * Profile, language, story ID and full text fingerprint isolate recordings.
 */
final class HumanStoryAudio {
    private final File directory;
    private final List<String> sentences;
    private MediaRecorder microphone;
    private File temporary;
    private int recordingIndex = -1;

    HumanStoryAudio(Context context, String profile, String language, long storyId, String body) {
        if (storyId < 0) throw new IllegalArgumentException("Missing story ID");
        String member = StudyProfile.normalized(profile);
        String lang = "en".equals(language) ? "en" : "es";
        sentences = Sentences.split(body == null ? "" : body);
        directory = new File(new File(new File(
                new File(context.getFilesDir(), "human-story-audio"), member), lang),
                storyId + "-" + fingerprint(body == null ? "" : body));
    }

    private static String fingerprint(String text) {
        try {
            byte[] sum = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 12; i++) hex.append(String.format(java.util.Locale.ROOT, "%02x", sum[i] & 255));
            return hex.toString();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    int count() { return sentences.size(); }
    String sentence(int index) { return sentences.get(index); }
    boolean isRecording() { return microphone != null; }

    File segment(int index) {
        checkIndex(index);
        return new File(directory, String.format(java.util.Locale.ROOT, "sentence-%05d.m4a", index));
    }

    boolean recorded(int index) {
        File file = segment(index);
        return file.isFile() && file.length() > 256;
    }

    int recordedCount() {
        int count = 0;
        for (int i = 0; i < sentences.size(); i++) if (recorded(i)) count++;
        return count;
    }

    boolean complete() {
        return !sentences.isEmpty() && recordedCount() == sentences.size();
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= sentences.size())
            throw new IndexOutOfBoundsException("Sentence index: " + index);
    }

    /** Only a complete take replaces a saved sentence. */
    void start(int index) throws IOException {
        checkIndex(index);
        if (microphone != null) throw new IllegalStateException("Already recording");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Не удалось создать папку записей");
        File part = new File(directory, String.format(java.util.Locale.ROOT, "pending-%05d.m4a", index));
        if (part.exists() && !part.delete()) throw new IOException("Не удалось очистить черновик");
        MediaRecorder recorder = new MediaRecorder();
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setOutputFile(part.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            microphone = recorder;
            temporary = part;
            recordingIndex = index;
        } catch (RuntimeException | IOException error) {
            recorder.release();
            part.delete();
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Не удалось запустить запись: " + error.getMessage(), error);
        }
    }

    /**
     * save=false discards an interrupted take; existing audio is untouched.
     * Android can reject extremely short recordings; no replacement occurs then.
     */
    void stop(boolean save) throws IOException {
        if (microphone == null) return;
        MediaRecorder recorder = microphone;
        File part = temporary;
        int index = recordingIndex;
        microphone = null;
        temporary = null;
        recordingIndex = -1;
        boolean stopped = false;
        try {
            recorder.stop();
            stopped = true;
        } catch (RuntimeException error) {
            if (save) throw new IOException("Запись слишком короткая или прервана", error);
        } finally {
            recorder.release();
            if (!save || !stopped) part.delete();
        }
        if (save) {
            try {
                if (!part.isFile() || part.length() <= 256) throw new IOException("Запись слишком короткая");
                Files.move(part.toPath(), segment(index).toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } finally {
                part.delete();
            }
        }
    }

    void deleteSentence(int index) throws IOException {
        if (isRecording()) throw new IllegalStateException("Recording in progress");
        File file = segment(index);
        if (file.exists() && !file.delete()) throw new IOException("Не удалось удалить запись");
    }

    void deleteAll() {
        if (isRecording()) {
            try { stop(false); } catch (IOException ignored) {}
        }
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) if (file.isFile()) file.delete();
        directory.delete();
    }

    /** Invoked only after explicit reset for the selected profile and language. */
    static void deleteLanguage(Context context, String profile, String language) {
        File root = new File(new File(new File(context.getFilesDir(),
                "human-story-audio"), StudyProfile.normalized(profile)),
                "en".equals(language) ? "en" : "es");
        File[] stories = root.listFiles();
        if (stories != null) {
            for (File story : stories) {
                File[] segments = story.listFiles();
                if (segments != null) for (File file : segments) if (file.isFile()) file.delete();
                story.delete();
            }
        }
        root.delete();
    }
}
