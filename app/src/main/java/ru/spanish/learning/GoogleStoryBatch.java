package ru.spanish.learning;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Create reusable, per-sentence recordings on the phone from Google's installed
 * Android TTS engine. No ChatGPT and no unofficial translate.google.com requests.
 * Runs only while the user keeps this screen open.
 */
final class GoogleStoryBatch {
    interface Listener {
        void progress(int done, int total, String title);
        void finished(int total);
        void failed(String explanation);
    }

    private static final String GOOGLE_ENGINE = "com.google.android.tts";
    private static final long CLIP_TIMEOUT_MS = 45000L;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Clip> clips = new ArrayList<>();
    private final Listener listener;
    private TextToSpeech tts;
    private File pending;
    private String currentToken = "";
    private String chosenLanguage = "";
    private boolean stopped;
    private int index;
    private Runnable timeout;

    private record Clip(String language, String title, GoogleStoryAudio audio, int sentence) {}

    GoogleStoryBatch(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        for (String profile : StudyProfile.IDS) {
            for (String language : new String[]{"es", "en"}) {
                Store profileStore = Store.forProfile(this.context, profile, "en".equals(language));
                try {
                    for (Store.Story story : profileStore.stories()) {
                        GoogleStoryAudio archive = new GoogleStoryAudio(this.context, profile, language,
                                story.id(), story.body());
                        for (int i = 0; i < archive.count(); i++)
                            clips.add(new Clip(language, story.title(), archive, i));
                    }
                } finally { profileStore.close(); }
            }
        }
    }

    void start() {
        if (stopped) return;
        if (clips.isEmpty()) {
            listener.finished(0);
            return;
        }
        try {
            tts = new TextToSpeech(context,
                    status -> main.post(() -> initialized(status)), GOOGLE_ENGINE);
        } catch (RuntimeException error) {
            fail("Не удалось запустить Сервисы синтеза речи Google.");
        }
    }

    private void initialized(int status) {
        if (stopped) return;
        if (status != TextToSpeech.SUCCESS || tts == null) {
            fail("Google Speech Services недоступны на телефоне.");
            return;
        }
        boolean googleInstalled = false;
        for (TextToSpeech.EngineInfo engine : tts.getEngines()) {
            if (GOOGLE_ENGINE.equals(engine.name)) { googleInstalled = true; break; }
        }
        if (!googleInstalled) {
            fail("Установите «Сервисы синтеза речи Google» в Android.");
            return;
        }
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) {
                main.post(() -> completed(id));
            }
            @Override public void onError(String id) {
                main.post(() -> error(id));
            }
            @Override public void onError(String id, int code) { onError(id); }
        });
        next();
    }

    private static int regionRank(Voice voice, String lang) {
        if (voice.getLocale() == null ||
                !lang.equals(voice.getLocale().getLanguage())) return 100;
        String country = voice.getLocale().getCountry();
        if ("en".equals(lang)) return "US".equals(country) ? 0 : "GB".equals(country) ? 1 : 3;
        return switch (country) {
            case "AR" -> 0;
            case "UY" -> 1;
            case "US", "MX" -> 2;
            case "CO", "CL", "PE" -> 3;
            case "ES" -> 20;
            default -> 5;
        };
    }

    private boolean selectVoice(String language) {
        List<Voice> candidates = new ArrayList<>();
        if (tts.getVoices() == null) return false;
        for (Voice voice : tts.getVoices()) {
            if (regionRank(voice, language) >= 20 ||
                    voice.getFeatures() != null && voice.getFeatures()
                        .contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
            candidates.add(voice);
        }
        candidates.sort((first, second) -> {
            int order = "es".equals(language)
                    ? SpanishVoicePreference.compareQualityFirst(
                            first.getQuality(), first.getLocale(),
                            second.getQuality(), second.getLocale())
                    : Integer.compare(regionRank(first, language),
                            regionRank(second, language));
            if (order != 0) return order;
            order = Integer.compare(second.getQuality(), first.getQuality());
            if (order != 0) return order;
            order = Boolean.compare(second.isNetworkConnectionRequired(),
                    first.isNetworkConnectionRequired());
            return order != 0 ? order : first.getName().compareTo(second.getName());
        });
        for (Voice voice : candidates) {
            try {
                if (tts.setVoice(voice) == TextToSpeech.SUCCESS &&
                        tts.setSpeechRate(1.0f) == TextToSpeech.SUCCESS) return true;
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    private void next() {
        if (stopped) return;
        while (index < clips.size() && clips.get(index).audio().recorded(clips.get(index).sentence()))
            index++;
        if (index == clips.size()) {
            listener.progress(index, clips.size(), "");
            listener.finished(clips.size());
            shutdown();
            return;
        }
        Clip clip = clips.get(index);
        if (!chosenLanguage.equals(clip.language())) {
            if (!selectVoice(clip.language())) {
                fail("Google не предоставляет установленный голос для языка " +
                        clip.language().toUpperCase(Locale.ROOT) + ". Проверьте голоса Google в Android.");
                return;
            }
            chosenLanguage = clip.language();
        }
        try {
            pending = clip.audio().staging(clip.sentence());
            if (pending.exists() && !pending.delete()) {
                fail("Не удалось очистить временное аудио.");
                return;
            }
            currentToken = "google:" + index + ":" + System.nanoTime();
            String speech = RioplatensePronunciation.forSpeech(
                    clip.audio().sentence(clip.sentence()), clip.language());
            if (tts.synthesizeToFile(speech, new Bundle(), pending, currentToken)
                    != TextToSpeech.SUCCESS) {
                fail("Сервис Google отказался создавать аудиозапись.");
                return;
            }
            String token = currentToken;
            timeout = () -> {
                if (!stopped && token.equals(currentToken))
                    fail("Google не ответил за 45 секунд. Проверяйте сеть и голосовые данные.");
            };
            main.postDelayed(timeout, CLIP_TIMEOUT_MS);
            listener.progress(index, clips.size(), clip.title());
        } catch (Exception error) {
            fail("Не удалось сохранить Google-аудио: " + error.getMessage());
        }
    }

    private void completed(String token) {
        if (stopped || !token.equals(currentToken) || index >= clips.size()) return;
        clearTimeout();
        Clip clip = clips.get(index);
        try {
            clip.audio().publish(clip.sentence(), pending);
            pending = null;
            currentToken = "";
            index++;
            next();
        } catch (Exception error) {
            fail("Не удалось проверить запись Google: " + error.getMessage());
        }
    }

    private void error(String token) {
        if (stopped || !token.equals(currentToken)) return;
        fail("Google Speech Services не смог создать аудио. Проверьте сеть и установленные голоса.");
    }

    private void clearTimeout() {
        if (timeout != null) main.removeCallbacks(timeout);
        timeout = null;
    }

    private void shutdown() {
        stopped = true;
        clearTimeout();
        currentToken = "";
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (pending != null) pending.delete();
        pending = null;
    }

    void cancel() { shutdown(); }

    private void fail(String reason) {
        if (stopped) return;
        shutdown();
        listener.failed(reason);
    }
}
