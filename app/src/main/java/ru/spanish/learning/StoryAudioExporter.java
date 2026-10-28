package ru.spanish.learning;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Creates one complete WAV attachment using the same installed voice as the player. */
final class StoryAudioExporter {
    interface Listener {
        void progress(int done, int total);
        void finished(Uri uri);
        void failed(String problem);
    }

    private static final int MAX_CHUNK = 1800; // Conservative, below Android's TTS input limit.
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private final Listener listener;
    private final List<String> chunks;
    private final String voiceName;
    private final float rate;
    private final File output;
    private final File part;
    private final String token;
    private TextToSpeech tts;
    private WavAssembler wav;
    private volatile boolean cancelled;
    private int index;

    StoryAudioExporter(Context context, String title, String body, String language, String voiceName,
                       float rate, Listener listener) {
        this.context = context.getApplicationContext();
        this.voiceName = voiceName;
        this.rate = rate;
        this.listener = listener;
        this.chunks = chunks(body, language);
        this.token = UUID.randomUUID().toString().replace("-", "");
        File folder = StoryAudioProvider.folder(this.context);
        this.output = new File(folder, StoryAudioProvider.filename(title, token));
        this.part = new File(folder, "working-" + token + ".wav");
    }

    static List<String> chunks(String text, String language) {
        List<String> result = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        for (String line : Sentences.split(text == null ? "" : text)) {
            String utterance = RioplatensePronunciation.forSpeech(line, language);
            if (pending.length() > 0 && pending.length() + 1 + utterance.length() > MAX_CHUNK) {
                result.add(pending.toString());
                pending.setLength(0);
            }
            if (pending.length() > 0) pending.append(" ");
            pending.append(utterance);
        }
        if (pending.length() > 0) result.add(pending.toString());
        return result;
    }

    void start() {
        if (chunks.isEmpty()) { fail("Рассказ пустой."); return; }
        listener.progress(0, chunks.size());
        tts = new TextToSpeech(context, status -> main.post(() -> initialize(status)));
    }

    private void initialize(int status) {
        if (cancelled) return;
        if (status != TextToSpeech.SUCCESS || tts == null) {
            fail("Android TTS недоступен. Проверьте установленный голос.");
            return;
        }
        Voice selected = null;
        try {
            for (Voice choice : tts.getVoices()) {
                if (voiceName.equals(choice.getName())) { selected = choice; break; }
            }
            if (selected == null || tts.setVoice(selected) != TextToSpeech.SUCCESS
                    || tts.setSpeechRate(rate) != TextToSpeech.SUCCESS) {
                fail("Выбранный голос недоступен для сохранения аудио.");
                return;
            }
        } catch (RuntimeException error) {
            fail("Не удалось настроить голос Android.");
            return;
        }
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) {
                if (!token.equals(id) || cancelled) return;
                files.execute(() -> mergePart());
            }
            @Override public void onError(String id) {
                if (token.equals(id)) main.post(() -> fail("Синтезатор не смог создать аудиозапись."));
            }
            @Override public void onError(String id, int errorCode) { onError(id); }
        });
        synthesizeNext();
    }

    private void synthesizeNext() {
        if (cancelled) return;
        if (index == chunks.size()) { finish(); return; }
        if (!StoryAudioProvider.folder(context).isDirectory()
                && !StoryAudioProvider.folder(context).mkdirs()) {
            fail("Не удалось создать временный аудиофайл.");
            return;
        }
        if (tts.synthesizeToFile(chunks.get(index), new Bundle(), part, token)
                != TextToSpeech.SUCCESS) {
            fail("Установленный голос не поддерживает запись в аудиофайл.");
        }
    }

    private void mergePart() {
        if (cancelled) return;
        try {
            if (wav == null) wav = new WavAssembler(output);
            wav.append(part);
            if (!part.delete()) part.deleteOnExit();
            main.post(() -> {
                if (cancelled) return;
                index++;
                listener.progress(index, chunks.size());
                synthesizeNext();
            });
        } catch (Exception error) {
            main.post(() -> fail("Не удалось объединить аудио: " + error.getMessage()));
        }
    }

    private void finish() {
        files.execute(() -> {
            try {
                if (cancelled || wav == null) return;
                wav.finish();
                if (cancelled) { cleanup(); return; }
                Uri uri = StoryAudioProvider.uri(context, output.getName());
                main.post(() -> {
                    if (!cancelled) {
                        closeTts();
                        files.shutdown();
                        listener.finished(uri);
                    }
                });
            } catch (Exception error) {
                main.post(() -> fail("Ошибка сохранения аудио: " + error.getMessage()));
            }
        });
    }

    private void fail(String reason) {
        if (cancelled) return;
        cancelled = true;
        closeTts();
        files.execute(this::cleanup);
        files.shutdown();
        listener.failed(reason);
    }

    void cancel() {
        if (cancelled) return;
        cancelled = true;
        closeTts();
        files.execute(this::cleanup);
        files.shutdown();
    }

    private void closeTts() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }

    private void cleanup() {
        try { if (wav != null) wav.abort(); } catch (IOException ignored) {}
        part.delete();
        output.delete();
    }
}
