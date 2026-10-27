package ru.spanish.learning;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Native Piper Daniela model and sherpa-onnx runtime bundled into the APK.
 * No external TTS app, permission, installation or network service is needed.
 * Initialization and audio synthesis never block Android's UI thread.
 */
final class EmbeddedDanielaSpeech {
    interface InitListener {
        void onReady();
        void onError(String why);
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final String TAG = "DanielaSpeech";
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    private final AtomicInteger generation = new AtomicInteger();
    private volatile boolean closed;
    private volatile boolean preparing;
    private volatile boolean ready;
    private volatile OfflineTts engine;
    private volatile AudioTrack currentTrack;

    EmbeddedDanielaSpeech(Context context) {
        this.context = context.getApplicationContext();
    }

    boolean isReady() {
        return !closed && ready && engine != null;
    }

    /** Run exactly once on a worker thread, then notify the main thread. */
    void prepare(InitListener listener) {
        if (closed) return;
        if (isReady()) {
            main.post(listener::onReady);
            return;
        }
        if (preparing) return;
        preparing = true;
        worker.execute(() -> {
            try {
                // Fail explicitly if an APK was built without the voice model.
                try (java.io.InputStream modelFile = context.getAssets()
                        .open(DanielaVoiceOption.MODEL_PATH)) {
                    if (modelFile.read() < 0) throw new IOException("Пустая модель голоса");
                }
                OfflineTtsVitsModelConfig vits = new OfflineTtsVitsModelConfig();
                vits.setModel(DanielaVoiceOption.MODEL_PATH);
                vits.setTokens(DanielaVoiceOption.TOKENS_PATH);
                vits.setDataDir(DanielaAssets.ensurePhonemeDirectory(context));
                OfflineTtsModelConfig model = new OfflineTtsModelConfig();
                model.setVits(vits);
                model.setNumThreads(2);
                OfflineTtsConfig config = new OfflineTtsConfig();
                config.setModel(model);
                OfflineTts candidate = new OfflineTts(context.getAssets(), config);
                if (closed) {
                    candidate.release();
                    return;
                }
                engine = candidate;
                ready = true;
                main.post(() -> {
                    if (!closed) listener.onReady();
                });
            } catch (Exception | LinkageError ex) {
                String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                main.post(() -> {
                    if (!closed) listener.onError("Не удалось запустить встроенный голос Daniela: " + reason);
                });
            } finally {
                preparing = false;
            }
        });
    }

    /**
     * Generate each card or story sentence and play PCM locally.
     * Completion is called only after playback drains, not merely after synthesis.
     */
    void speak(String spanish, float speed, Runnable done, Consumer<String> failed) {
        if (!isReady()) {
            main.post(() -> failed.accept("Синтезатор Daniela ещё не готов"));
            return;
        }
        int id = generation.incrementAndGet();
        stopTrack();
        // Drop stale cards while a slower native inference is finishing.
        worker.getQueue().clear();
        worker.execute(() -> {
            AudioTrack track = null;
            boolean succeeded = false;
            String failure = "Неизвестная ошибка воспроизведения";
            try {
                if (closed || generation.get() != id) return;
                GeneratedAudio generated = engine.generate(speechText(spanish), 0,
                        Math.max(.5f, Math.min(1.5f, speed)));
                float[] samples = generated.getSamples();
                int rate = generated.getSampleRate();
                if (samples == null || rate < 8000 || samples.length == 0)
                    throw new IllegalStateException("Модель не вернула звук");
                if (closed || generation.get() != id) return;

                int minBytes = AudioTrack.getMinBufferSize(rate,
                        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (minBytes <= 0) throw new IllegalStateException("Нет аудиовыхода");
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setSampleRate(rate)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(Math.max(minBytes, 8192))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();
                if (track.getState() != AudioTrack.STATE_INITIALIZED)
                    throw new IllegalStateException("Не удалось открыть аудиовыход");
                currentTrack = track;
                track.play();
                byte[] pcm = new byte[8192];
                int position = 0;
                while (position < samples.length && !closed && generation.get() == id) {
                    int frames = Math.min(pcm.length / 2, samples.length - position);
                    for (int j = 0; j < frames; j++) {
                        float f = Math.max(-1f, Math.min(1f, samples[position + j]));
                        short sample = (short) (f * 32767f);
                        pcm[j * 2] = (byte) sample;
                        pcm[j * 2 + 1] = (byte) (sample >> 8);
                    }
                    int len = frames * 2;
                    int written = 0;
                    while (written < len && !closed && generation.get() == id) {
                        int count = track.write(pcm, written, len - written, AudioTrack.WRITE_BLOCKING);
                        if (count <= 0) throw new IllegalStateException("Ошибка записи звука: " + count);
                        written += count;
                    }
                    position += frames;
                }
                if (closed || generation.get() != id) return;
                // The last PCM block is queued, but not necessarily yet played.
                long deadline = System.currentTimeMillis() +
                        Math.max(10_000L, samples.length * 2200L / rate);
                while (!closed && generation.get() == id
                        && (track.getPlaybackHeadPosition() & 0xffffffffL) < samples.length
                        && System.currentTimeMillis() < deadline) {
                    Thread.sleep(20);
                }
                if ((track.getPlaybackHeadPosition() & 0xffffffffL) < samples.length)
                    throw new IllegalStateException("Аудиоустройство не воспроизвело звук");
                succeeded = !closed && generation.get() == id;
            } catch (Exception | LinkageError ex) {
                failure = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                Log.e(TAG, "Unable to speak with Daniela", ex);
            } finally {
                if (track != null) {
                    if (currentTrack == track) currentTrack = null;
                    try { track.pause(); track.flush(); track.release(); }
                    catch (IllegalStateException ignored) {}
                }
                if (!closed && generation.get() == id) {
                    final boolean success = succeeded;
                    final String reason = failure;
                    main.post(() -> {
                        if (!closed && generation.get() == id) {
                            if (success) done.run();
                            else failed.accept(reason);
                        }
                    });
                }
            }
        });
    }

    // A bare word is not a sentence: append a period for Piper's phonemizer.
    // Never substitute ll/y or inject Cyrillic into Spanish spelling.
    static String speechText(String text) {
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.isEmpty()) return cleaned;
        char last = cleaned.charAt(cleaned.length() - 1);
        return ".!?…".indexOf(last) >= 0 ? cleaned : cleaned + ".";
    }

    void stop() {
        generation.incrementAndGet();
        stopTrack();
        if (ready) worker.getQueue().clear();
    }

    private void stopTrack() {
        AudioTrack track = currentTrack;
        currentTrack = null;
        if (track != null) {
            try { track.pause(); track.flush(); } catch (IllegalStateException ignored) {}
            // The worker owns and releases the AudioTrack.
        }
    }

    void close() {
        closed = true;
        stop();
        worker.execute(() -> {
            OfflineTts current = engine;
            engine = null;
            if (current != null) current.release();
        });
        worker.shutdown();
    }
}
