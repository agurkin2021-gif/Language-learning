package ru.spanish.learning;

import android.content.Context;
import android.content.SharedPreferences;
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
    private final SharedPreferences voicePrefs;
    private List<String> sentences = new ArrayList<>();
    private boolean ready, closed, ttsInitialized;
    private String learningLanguage = "es";
    private int resumeOffset;
    private int utteranceBaseOffset;
    private String activeToken = "";
    public boolean sentenceMode = true;
    public float speed = 0.85f;
    public int pauseMs = 1500;
    public String voiceDescription = "Подготовка голоса…";
    public String error = "";
    public SpeechPlayer(Context context, Listener listener) {
        this.listener = listener;
        voicePrefs = context.getSharedPreferences("tts_voice_settings", Context.MODE_PRIVATE);
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener(change -> { if (change < 0) pause(); }, handler).build();
        tts = new TextToSpeech(context.getApplicationContext(), status -> handler.post(() -> {
            if (closed) return;
            if (status != TextToSpeech.SUCCESS) { voiceDescription = "Синтезатор речи недоступен"; listener.changed(); return; }
            tts.setAudioAttributes(attributes);
            ttsInitialized = true;
            chooseVoice();
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onRangeStart(String id, int start, int end, int frame) {
                    handler.post(() -> rememberRange(id, start));
                }
                @Override public void onDone(String id) {
                    handler.post(() -> {
                        if (id != null && id.startsWith("card:")) finishCardSpeech();
                        else done(id);
                    });
                }
                @Override public void onError(String id) {
                    handler.post(() -> {
                        if (id != null && id.startsWith("card:")) finishCardSpeech();
                        else failed(id);
                    });
                }
            });
            listener.changed();
        }));
    }
    /**
     * Prefer Argentina (es-AR), then Uruguay (es-UY), then Latin American
     * Spanish. Never silently select es-ES or an undifferentiated es voice.
     * The actual ll/y sound depends on the installed TTS voice, not only
     * its BCP-47 locale. Let learners audition and choose the exact voice.
     */
    void setLearningLanguage(String code) {
        String normalized = "en".equals(code) ? "en" : "es";
        if (learningLanguage.equals(normalized) && ttsInitialized) return;
        pause();
        learningLanguage = normalized;
        if (ttsInitialized) chooseVoice();
    }

    String learningLanguage() { return learningLanguage; }

    static final class VoiceChoice {
        final String name;
        final String label;
        VoiceChoice(String name, String label) {
            this.name = name;
            this.label = label;
        }
    }

    private List<Voice> availableVoices() {
        List<Voice> voices = new ArrayList<>();
        if (closed || tts == null || tts.getVoices() == null) return voices;
        for (Voice voice : tts.getVoices()) {
            if (voice.getLocale() == null
                    || !learningLanguage.equals(voice.getLocale().getLanguage())
                    || (voice.getFeatures() != null
                        && voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)))
                continue;
            if ("es".equals(learningLanguage)) {
                if (SpanishVoicePreference.regionRank(voice.getLocale()) < 0) continue;
            } else if (voice.isNetworkConnectionRequired()) {
                // Retain the existing offline-only English behavior.
                continue;
            }
            voices.add(voice);
        }
        voices.sort(Comparator.comparingInt((Voice voice) -> {
            if ("en".equals(learningLanguage))
                return "US".equals(voice.getLocale().getCountry()) ? 0
                        : "GB".equals(voice.getLocale().getCountry()) ? 1 : 2;
            return SpanishVoicePreference.regionRank(voice.getLocale());
        }).thenComparingInt(voice -> voice.isNetworkConnectionRequired() ? 1 : 0)
          .thenComparing((Voice voice) -> -voice.getQuality())
          .thenComparing(Voice::getName));
        return voices;
    }

    private boolean activateVoice(Voice voice) {
        try {
            if (tts.setVoice(voice) != TextToSpeech.SUCCESS) return false;
        } catch (RuntimeException ignored) {
            return false;
        }
        ready = true;
        String locale = voice.getLocale().toLanguageTag();
        String connection = voice.isNetworkConnectionRequired() ? "через интернет" : "офлайн";
        if ("en".equals(learningLanguage))
            voiceDescription = "Английский голос: " + locale + " • " + connection;
        else if ("AR".equals(voice.getLocale().getCountry()))
            voiceDescription = "Голос Аргентины: " + locale + " • " + connection;
        else if ("UY".equals(voice.getLocale().getCountry()))
            voiceDescription = "Голос Уругвая (Рио-де-ла-Плата): " + locale
                    + " • " + connection;
        else
            voiceDescription = "Латиноамериканский голос (не Аргентина): "
                    + locale + " • " + connection;
        listener.changed();
        return true;
    }

    private void chooseVoice() {
        ready = false;
        if (closed || tts == null) return;
        List<Voice> voices = availableVoices();
        String saved = "es".equals(learningLanguage)
                ? voicePrefs.getString("selected_es_voice", "") : "";
        if (!saved.isEmpty()) {
            for (Voice voice : voices) {
                if (saved.equals(voice.getName()) && activateVoice(voice)) return;
            }
            voicePrefs.edit().remove("selected_es_voice").apply();
        }
        for (Voice voice : voices) {
            if (activateVoice(voice)) return;
        }
        voiceDescription = "Нет доступного "
                + ("en".equals(learningLanguage)
                    ? "английского офлайн-голоса."
                    : "голоса Аргентины или Латинской Америки. Установите es-AR либо es-MX в настройках синтеза Android.");
        listener.changed();
    }

    List<VoiceChoice> spanishVoiceChoices() {
        List<VoiceChoice> choices = new ArrayList<>();
        if (!"es".equals(learningLanguage) || !ttsInitialized) return choices;
        for (Voice voice : availableVoices()) {
            choices.add(new VoiceChoice(voice.getName(),
                    SpanishVoicePreference.regionLabel(voice.getLocale())
                            + " (" + voice.getLocale().toLanguageTag() + ")"
                            + (voice.isNetworkConnectionRequired()
                                ? " • интернет" : " • офлайн")
                            + " • " + voice.getName()));
        }
        return choices;
    }

    String selectedSpanishVoiceName() {
        Voice current = ready && tts != null ? tts.getVoice() : null;
        return "es".equals(learningLanguage) && current != null
                ? current.getName() : "";
    }

    boolean selectSpanishVoice(String voiceName) {
        if (!ttsInitialized || !"es".equals(learningLanguage)
                || voiceName == null || voiceName.isEmpty()) return false;
        for (Voice voice : availableVoices()) {
            if (!voiceName.equals(voice.getName())) continue;
            pause();
            if (!activateVoice(voice)) return false;
            voicePrefs.edit().putString("selected_es_voice", voiceName).apply();
            return true;
        }
        return false;
    }

    public boolean ready() { return ready; }

    public void speakCard(String spanish) {
        if (closed || !ready || spanish == null || spanish.trim().isEmpty()) return;
        handler.removeCallbacks(autoPlay);
        if (tts != null) tts.stop();
        audio.abandonAudioFocusRequest(focus);
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return;
        String token = "card:" + System.nanoTime();
        tts.setSpeechRate(speed);
        if (tts.speak(spanish.trim(), TextToSpeech.QUEUE_FLUSH, null, token) == TextToSpeech.ERROR)
            finishCardSpeech();
    }

    private void finishCardSpeech() {
        audio.abandonAudioFocusRequest(focus);
    }
    public void load(String body, int position) { pause(); resumeOffset = 0; activeToken = ""; sentences = Sentences.split(body); state.load(sentences.size(), position); listener.changed(); }
    public String current() { return sentences.isEmpty() ? "" : sentences.get(state.index); }
    public void playAllFromStart() {
        pause();
        resumeOffset = 0;
        activeToken = "";
        sentenceMode = false;
        state.load(sentences.size(), 0);
        listener.changed();
        play();
    }
    public void play() {
        if (closed || !ready || state.phase == PlaybackState.Phase.SPEAKING) return;
        handler.removeCallbacks(autoPlay);
        boolean restart = state.phase == PlaybackState.Phase.FINISHED;
        if (restart) resumeOffset = 0;
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { error = "Звук занят другим приложением"; listener.changed(); return; }
        String token = state.play();
        if (token == null) return;
        String full = current();
        if (resumeOffset < 0 || resumeOffset >= full.length()) resumeOffset = 0;
        utteranceBaseOffset = resumeOffset;
        activeToken = token;
        String spoken = full.substring(utteranceBaseOffset);
        error = "";
        tts.setSpeechRate(speed);
        if (tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, token) == TextToSpeech.ERROR) failed(token);
        listener.changed();
    }
    private void rememberRange(String token, int start) {
        if (closed || !token.equals(activeToken) || state.phase != PlaybackState.Phase.SPEAKING) return;
        String full = current();
        resumeOffset = Math.max(0, Math.min(full.length(), utteranceBaseOffset + start));
    }
    private void done(String token) {
        if (closed || !state.complete(token)) return;
        resumeOffset = 0;
        activeToken = "";
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
        PlaybackState.Phase previous = state.phase;
        state.pause();
        if (previous != PlaybackState.Phase.SPEAKING) {
            resumeOffset = 0;
            activeToken = "";
        }
        if (tts != null) tts.stop();
        audio.abandonAudioFocusRequest(focus);
        listener.changed();
    }
    public void repeat() {
        boolean previous = state.phase == PlaybackState.Phase.WAITING;
        pause();
        resumeOffset = 0;
        activeToken = "";
        if (previous && state.index > 0) state.index--;
        state.repeat(); play();
    }
    public void close() { pause(); closed = true; if (tts != null) tts.shutdown(); }
}
