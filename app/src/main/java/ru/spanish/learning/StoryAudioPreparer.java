package ru.spanish.learning;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Prepare the COMPLETE marked story before the first teleprompter playback.
 * No synthesis, alignment, or splicing is ever performed while speaking.
 *
 * Each recording is one natural TTS utterance, with word frame coordinates
 * captured from the very same muted speak() request. Original WAVs and their
 * frame maps are reused when markers change.
 *
 * All public methods and onDone/onError run on the main thread, except PCM
 * callbacks (which forward directly to the synchronized capture) and disk
 * operations on the dedicated executor.
 */
final class StoryAudioPreparer {
    interface Listener { void changed(); }
    private static final String PREFIX = "story-prep:";
    private final Handler main;
    private final Listener listener;
    private final SharedPreferences prefs;
    private final File folder;
    private final ExecutorService disk = Executors.newSingleThreadExecutor();

    private TextToSpeech tts;
    private List<String> sentences = List.of();
    private Map<Integer, List<Integer>> markers = Map.of();
    private final Map<Integer, File> prepared = new HashMap<>();
    private String storyKey = "", language = "es", voice = "";
    private float speed;
    private int pauseMs;
    private volatile int generation;
    private int nextIndex;
    private int completed;
    private volatile boolean required, preparing, ready;
    private String error = "";
    private volatile String utterance = "";
    private String spoken = "";
    private List<Integer> gaps = List.of();
    private File original, result;
    private volatile TimedStoryCapture capture;

    StoryAudioPreparer(Context context, Handler main, Listener listener) {
        this.main = main;
        this.listener = listener;
        prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE);
        folder = new File(context.getFilesDir(), "story_prepared_audio");
    }

    static boolean isPreparationId(String id) {
        return id != null && id.startsWith(PREFIX);
    }

    boolean required() { return required; }
    boolean hasError() { return required && !error.isEmpty(); }
    boolean isPreparing() { return required && preparing; }
    boolean isReady() { return required && ready && !preparing && error.isEmpty()
            && prepared.size() == sentences.size(); }
    String status() {
        if (!required) return "";
        if (!error.isEmpty()) return "Подготовка остановлена: " + error;
        return isReady() ? "Аудио всего рассказа готово"
                : "Подготовка аудио всего рассказа: " + completed + " / " + sentences.size();
    }
    File audioAt(int index) {
        File wav = prepared.get(index);
        return isReady() && wav != null && wav.isFile() && wav.length() > 44 ? wav : null;
    }

    void cancel() {
        ++generation;
        required = false;
        preparing = false;
        ready = false;
        error = "";
        completed = 0;
        nextIndex = 0;
        utterance = "";
        capture = null;
        prepared.clear();
        // Never delete cached whole-sentence WAVs: marker changes reuse them.
    }

    void close() {
        cancel();
        disk.shutdownNow();
    }

    void start(TextToSpeech engine, List<String> all,
               Map<Integer, List<Integer>> positions, int durationMs,
               String key, String lang, float rate, String voiceName) {
        cancel();
        if (durationMs <= 0 || "ru".equals(lang)
                || positions == null || positions.isEmpty()) return;
        required = true;
        if (engine == null) {
            error = "Голос ещё не готов";
            listener.changed();
            return;
        }
        tts = engine;
        sentences = List.copyOf(all);
        markers = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> e : positions.entrySet())
            markers.put(e.getKey(), List.copyOf(e.getValue()));
        storyKey = key == null ? "" : key;
        language = lang;
        speed = rate;
        voice = voiceName == null ? "" : voiceName;
        pauseMs = durationMs;
        preparing = true;
        if (!folder.exists() && !folder.mkdirs()) {
            fail("Не удалось создать папку аудио");
            return;
        }
        prepareNext(generation);
        listener.changed();
    }

    /** Unique source WAV identity: markers and pause duration are excluded. */
    private File sourceAt(int index, String phrase) {
        String identity = storyKey + ":" + index + ":rate:"
                + Float.floatToIntBits(speed) + ":voice:" + voice + ":text:" + phrase;
        return new File(folder, UUID.nameUUIDFromBytes(
                identity.getBytes(StandardCharsets.UTF_8)) + ".wav");
    }

    /** Prepared output identity includes every marker and duration. */
    private File outputAt(File source, List<Integer> pauseGaps) {
        if (pauseGaps.isEmpty()) return source;
        String identity = source.getName() + ":pause:" + pauseMs + ":" + pauseGaps;
        return new File(folder, UUID.nameUUIDFromBytes(
                identity.getBytes(StandardCharsets.UTF_8)) + ".wav");
    }

    private static String rangeKey(File file) {
        return "story-prep-frames:" + file.getName();
    }

    private void prepareNext(int run) {
        if (run != generation || !preparing) return;
        if (nextIndex >= sentences.size()) {
            preparing = false;
            ready = true;
            listener.changed();
            return;
        }
        final int index = nextIndex;
        try {
            spoken = RioplatensePronunciation.forSpeech(sentences.get(index), language);
            List<Integer> originalGaps = markers.getOrDefault(index, List.of());
            gaps = originalGaps.isEmpty() ? List.of()
                    : SpokenGapMapper.map(sentences.get(index), spoken, originalGaps, language);
            original = sourceAt(index, spoken);
            result = outputAt(original, gaps);
            if (result.isFile() && result.length() > 44) {
                finishedOne(run, index, result);
                return;
            }
            if (original.isFile() && original.length() > 44) {
                List<ProsodyPauseAudio.WordTiming> cached = ProsodyTimingCache.decode(
                        prefs.getString(rangeKey(original), ""), spoken);
                if (gaps.isEmpty() || ProsodyTimingCache.covers(spoken, gaps, cached)) {
                    finalizeWav(run, index, original, result, spoken, gaps, cached);
                    return;
                }
            }
            // Synthesize the ENTIRE sentence ahead of time; never at playback.
            utterance = PREFIX + run + ":" + index + ":" + System.nanoTime();
            capture = new TimedStoryCapture(utterance);
            tts.setSpeechRate(speed);
            Bundle muted = new Bundle();
            muted.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0f);
            if (tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, muted, utterance)
                    == TextToSpeech.ERROR)
                fail("Голос не смог подготовить предложение " + (index + 1));
        } catch (RuntimeException e) {
            fail("Предложение " + (index + 1) + ": " + e.getMessage());
        }
    }

    boolean accepts(String id) {
        return isPreparationId(id) && id.equals(utterance) && preparing;
    }

    void begin(String id, int rate, int encoding, int channels) {
        TimedStoryCapture active = capture;
        if (active != null && accepts(id)) active.begin(id, rate, encoding, channels);
    }
    void audio(String id, byte[] bytes) {
        TimedStoryCapture active = capture;
        if (active != null && accepts(id)) active.append(id, bytes);
    }
    void range(String id, int start, int end, int frame) {
        TimedStoryCapture active = capture;
        if (active != null && accepts(id)) active.range(id, start, end, frame);
    }

    void done(String id) {
        if (!accepts(id)) return;
        TimedStoryCapture finished = capture;
        capture = null;
        final int run = generation;
        final int index = nextIndex;
        final File source = original;
        final File target = result;
        final String text = spoken;
        final List<Integer> chosenGaps = gaps;
        if (finished == null) {
            fail("Синтезатор не передал запись");
            return;
        }
        disk.execute(() -> {
            try {
                TimedStoryCapture.Snapshot snapshot = finished.snapshot();
                List<ProsodyPauseAudio.WordTiming> ranges = snapshot.ranges();
                if (!chosenGaps.isEmpty()
                        && !ProsodyTimingCache.covers(text, chosenGaps, ranges))
                    throw new IOException("нет точных координат выбранных слов");
                if (run != generation) return;
                File temp = File.createTempFile("full-tts-", ".wav", folder);
                try {
                    snapshot.save(temp);
                    if (run != generation) return;
                    Files.move(temp.toPath(), source.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    temp.delete();
                }
                if (run != generation) return;
                main.post(() -> {
                    if (run != generation) return;
                    prefs.edit().putString(rangeKey(source),
                            ProsodyTimingCache.encode(ranges)).apply();
                    finalizeWav(run, index, source, target, text, chosenGaps, ranges);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (run == generation)
                        fail("Предложение " + (index + 1) + ": " + e.getMessage());
                });
            }
        });
    }

    void error(String id) {
        if (accepts(id)) fail("Голос не смог озвучить предложение " + (nextIndex + 1));
    }

    private void finalizeWav(int run, int index, File source, File target,
                             String text, List<Integer> positions,
                             List<ProsodyPauseAudio.WordTiming> ranges) {
        if (positions.isEmpty()) {
            finishedOne(run, index, source);
            return;
        }
        disk.execute(() -> {
            try {
                if (run != generation) return;
                File temp = File.createTempFile("marked-story-", ".wav", folder);
                try {
                    ProsodyPauseAudio.insertSilenceExact(
                            source, temp, text, positions, ranges, pauseMs);
                    if (run != generation) return;
                    Files.move(temp.toPath(), target.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    temp.delete();
                }
                main.post(() -> finishedOne(run, index, target));
            } catch (Exception e) {
                main.post(() -> {
                    if (run == generation)
                        fail("Предложение " + (index + 1) + ": " + e.getMessage());
                });
            }
        });
    }

    private void finishedOne(int run, int index, File file) {
        if (run != generation || !preparing || index != nextIndex) return;
        if (!file.isFile() || file.length() <= 44) {
            fail("Не удалось сохранить предложение " + (index + 1));
            return;
        }
        prepared.put(index, file);
        completed++;
        nextIndex++;
        listener.changed();
        // Cache hits advance from the main thread, one per loop iteration,
        // so a long cached story cannot overflow the Java call stack.
        main.post(() -> prepareNext(run));
    }

    private void fail(String reason) {
        preparing = false;
        ready = false;
        capture = null;
        utterance = "";
        error = reason;
        listener.changed();
    }
}
