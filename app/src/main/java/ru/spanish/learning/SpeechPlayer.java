package ru.spanish.learning;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SpeechPlayer {
    public interface Listener { void changed(); }
    public final PlaybackState state = new PlaybackState();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final Runnable autoPlay = this::play;
    private final AudioManager audio;
    private final AudioFocusRequest focus;
    private TextToSpeech tts;
    private List<String> sentences = new ArrayList<>();
    private boolean ready, closed;
    public boolean sentenceMode = true;
    public float speed = 0.85f;
    public int pauseMs = 1500;
    public String voiceDescription = "Подготовка голоса…";
    public String error = "";
    public SpeechPlayer(Context context, Listener listener) {
        this.listener = listener;
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener(change -> { if (change < 0) pause(); }, handler).build();
        tts = new TextToSpeech(context.getApplicationContext(), status -> handler.post(() -> {
            if (closed) return;
            if (status != TextToSpeech.SUCCESS) { voiceDescription = "Синтезатор речи недоступен"; listener.changed(); return; }
            tts.setAudioAttributes(attributes);
            List<Voice> voices = new ArrayList<>();
            if (tts.getVoices() != null) for (Voice v : tts.getVoices()) {
                if ("es".equals(v.getLocale().getLanguage()) && !v.isNetworkConnectionRequired()
                        && !v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) voices.add(v);
            }
            voices.sort(Comparator.comparingInt((Voice v) -> "AR".equals(v.getLocale().getCountry()) ? 0 : "MX".equals(v.getLocale().getCountry()) ? 1 : 2).thenComparing(Voice::getName));
            for (Voice v : voices) if (tts.setVoice(v) == TextToSpeech.SUCCESS) {
                ready = true;
                voiceDescription = "Офлайн-голос: " + v.getLocale().toLanguageTag() + ("AR".equals(v.getLocale().getCountry()) ? " • Аргентина" : " • аргентинский голос недоступен");
                break;
            }
            if (!ready) voiceDescription = "Нет установленного офлайн-голоса испанского. Установите его в настройках синтеза речи Android и откройте приложение заново.";
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) { handler.post(() -> done(id)); }
                @Override public void onError(String id) { handler.post(() -> failed(id)); }
            });
            listener.changed();
        }));
    }
    public boolean ready() { return ready; }
    public void load(String body, int position) { pause(); sentences = Sentences.split(body); state.load(sentences.size(), position); listener.changed(); }
    public String current() { return sentences.isEmpty() ? "" : sentences.get(state.index); }
    public void playAllFromStart() {
        pause();
        sentenceMode = false;
        state.load(sentences.size(), 0);
        listener.changed();
        play();
    }
    public void play() {
        if (closed || !ready || state.phase == PlaybackState.Phase.SPEAKING) return;
        handler.removeCallbacks(autoPlay);
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { error = "Звук занят другим приложением"; listener.changed(); return; }
        String token = state.play();
        if (token == null) return;
        error = "";
        tts.setSpeechRate(speed);
        if (tts.speak(current(), TextToSpeech.QUEUE_FLUSH, null, token) == TextToSpeech.ERROR) failed(token);
        listener.changed();
    }
    private void done(String token) {
        if (closed || !state.complete(token)) return;
        audio.abandonAudioFocusRequest(focus);
        listener.changed();
        if (!sentenceMode && state.phase == PlaybackState.Phase.WAITING) handler.postDelayed(autoPlay, pauseMs);
    }
    private void failed(String token) {
        if (closed || !state.complete(token)) return;
        // Completion advances; restore the failed sentence so retry cannot skip it.
        if (state.phase == PlaybackState.Phase.WAITING) state.index--;
        state.pause(); state.phase = PlaybackState.Phase.PAUSED; error = "Не удалось озвучить. Проверьте установленный офлайн-голос.";
        audio.abandonAudioFocusRequest(focus); listener.changed();
    }
    public void pause() {
        handler.removeCallbacks(autoPlay);
        state.pause();
        if (tts != null) tts.stop();
        audio.abandonAudioFocusRequest(focus);
        listener.changed();
    }
    public void repeat() {
        boolean previous = state.phase == PlaybackState.Phase.WAITING;
        pause();
        if (previous && state.index > 0) state.index--;
        state.repeat(); play();
    }
    public void close() { pause(); closed = true; if (tts != null) tts.shutdown(); }
}
