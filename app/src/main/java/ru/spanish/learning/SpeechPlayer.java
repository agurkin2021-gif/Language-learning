package ru.spanish.learning;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

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
    private HumanStoryAudio humanAudio;
    private GoogleStoryAudio googleAudio;
    private boolean googleSelected;
    private boolean humanSelected;
    private MediaPlayer humanPlayer;
    private int humanResumeMs;
    private int humanResumeIndex = -1;
    /** Most recent position chosen through the Back button, until playback advances normally. */
    private int lastBackIndex = -1;
    private boolean ready, closed, ttsInitialized;
    private String learningLanguage = "es";
    private int resumeOffset;
    private int utteranceBaseOffset;
    private boolean spokenTextChangesOffsets;
    private String activeToken = "";
    private boolean slowArticles;
    private List<ManualStoryPauses.Piece> articleParts = List.of();
    private Map<Integer, List<Integer>> manualPositions = Map.of();
    private int manualPauseMs;
    private int articleFullOffset;
    private String articleLastId = "";
    public boolean sentenceMode = true;
    public float speed = 0.85f;
    /** Separate rate for the six requested words in all Spanish stories. */
    public float shortWordSpeed = 0.45f;
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
                    handler.post(() -> {
                        if (isArticlePart(id)) rememberArticleRange(id, start);
                        else rememberRange(id, start);
                    });
                }
                @Override public void onDone(String id) {
                    handler.post(() -> {
                        if (id != null && id.startsWith("card:")) finishCardSpeech();
                        else if (isArticlePart(id)) {
                            if (id.equals(articleLastId)) {
                                String sentenceToken = activeToken;
                                clearArticleParts();
                                done(sentenceToken);
                            }
                            // All fragments were queued up front. Never wait for callbacks
                            // between words: Android TTS advances through its own queue.
                        } else done(id);
                    });
                }
                @Override public void onError(String id) {
                    handler.post(() -> {
                        if (id != null && id.startsWith("card:")) finishCardSpeech();
                        else if (isArticlePart(id)) {
                            String originalToken = activeToken;
                            clearArticleParts();
                            if (tts != null) tts.stop(); // Cancel remaining queued pieces.
                            failed(originalToken);
                        } else failed(id);
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
        String normalized = "ru".equals(code) ? "ru" : "en".equals(code) ? "en" : "es";
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
            } else if ("en".equals(learningLanguage) && voice.isNetworkConnectionRequired()) {
                // Retain the existing offline-only English behavior.
                continue;
            }
            voices.add(voice);
        }
        voices.sort((first, second) -> {
            int order;
            if ("es".equals(learningLanguage)) {
                order = SpanishVoicePreference.compareQualityFirst(
                        first.getQuality(), first.getLocale(),
                        second.getQuality(), second.getLocale());
            } else {
                int firstRegion = "en".equals(learningLanguage)
                        ? ("US".equals(first.getLocale().getCountry()) ? 0
                        : "GB".equals(first.getLocale().getCountry()) ? 1 : 2) : 0;
                int secondRegion = "en".equals(learningLanguage)
                        ? ("US".equals(second.getLocale().getCountry()) ? 0
                        : "GB".equals(second.getLocale().getCountry()) ? 1 : 2) : 0;
                order = Integer.compare(firstRegion, secondRegion);
            }
            if (order != 0) return order;
            order = Boolean.compare(first.isNetworkConnectionRequired(),
                    second.isNetworkConnectionRequired());
            if (order != 0) return order;
            order = Integer.compare(second.getQuality(), first.getQuality());
            return order != 0 ? order : first.getName().compareTo(second.getName());
        });
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
        if ("ru".equals(learningLanguage))
            voiceDescription = "Русский голос: " + locale + " • " + connection;
        else if ("en".equals(learningLanguage))
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
                + ("ru".equals(learningLanguage)
                    ? "русского голоса. Установите русский голос в настройках синтеза речи Android."
                    : "en".equals(learningLanguage)
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

    /** Snapshot the same TTS voice the listener currently hears. */
    public String selectedVoiceNameForExport() {
        Voice active = ready && tts != null ? tts.getVoice() : null;
        return active == null ? "" : active.getName();
    }
    public boolean ready() { return ready; }

    public void speakCard(String spanish) {
        if (closed || !ready || spanish == null || spanish.trim().isEmpty()) return;
        pause();
        handler.removeCallbacks(autoPlay);
        if (tts != null) tts.stop();
        audio.abandonAudioFocusRequest(focus);
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return;
        String token = "card:" + System.nanoTime();
        tts.setSpeechRate(speed);
        if (tts.speak(RioplatensePronunciation.forSpeech(spanish.trim(), learningLanguage), TextToSpeech.QUEUE_FLUSH, null, token) == TextToSpeech.ERROR)
            finishCardSpeech();
    }

    private void finishCardSpeech() {
        audio.abandonAudioFocusRequest(focus);
    }
    /** Select recordings for exactly this story and no other family profile. */
    public void attachHumanStory(HumanStoryAudio recordings, boolean preferHuman) {
        pause();
        humanAudio = recordings;
        humanSelected = preferHuman && recordings != null && recordings.complete();
        if (humanSelected) googleSelected = false;
        humanResumeMs = 0;
        humanResumeIndex = -1;
        listener.changed();
    }

    public void attachGoogleStory(GoogleStoryAudio recordings, boolean preferGoogle) {
        pause();
        googleAudio = recordings;
        googleSelected = preferGoogle && recordings != null && recordings.complete();
        if (googleSelected) humanSelected = false;
        humanResumeMs = 0;
        humanResumeIndex = -1;
        listener.changed();
    }

    public boolean googleAvailable() { return googleAudio != null && googleAudio.complete(); }
    public boolean googleSelected() { return googleSelected && googleAvailable(); }

    public void useGoogleVoice(boolean enabled) {
        pause();
        googleSelected = enabled && googleAvailable();
        if (googleSelected) humanSelected = false;
        humanResumeMs = 0;
        humanResumeIndex = -1;
        resumeOffset = 0;
        listener.changed();
    }

    public boolean humanAvailable() { return humanAudio != null && humanAudio.complete(); }
    public boolean humanSelected() { return humanSelected && humanAvailable(); }
    public boolean canPlay() { return humanSelected() || googleSelected() || ready; }

    public void useHumanVoice(boolean enabled) {
        pause();
        humanSelected = enabled && humanAvailable();
        if (humanSelected) googleSelected = false;
        humanResumeMs = 0;
        humanResumeIndex = -1;
        resumeOffset = 0;
        listener.changed();
    }

    /** This mode is shared by every Spanish story, independent of its title. */
    public void setSlowArticles(boolean enabled) {
        slowArticles = enabled;
    }

    public boolean slowArticlesEnabled() { return slowArticles; }

    /** Set markers for this specific story only. Zero duration disables playback of markers. */
    public void setManualPauses(Map<Integer, List<Integer>> positions, int millis) {
        Map<Integer, List<Integer>> copy = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> item : positions.entrySet())
            copy.put(item.getKey(), List.copyOf(item.getValue()));
        manualPositions = copy;
        manualPauseMs = Math.max(0, Math.min(5000, millis));
    }

    public boolean manualPausesActive() {
        return manualPauseMs > 0 && !manualPositions.isEmpty()
                && !"ru".equals(learningLanguage);
    }

    public void load(String body, int position) {
        pause();
        lastBackIndex = -1;
        resumeOffset = 0;
        humanResumeMs = 0;
        humanResumeIndex = -1;
        activeToken = "";
        sentences = Sentences.split(body);
        state.load(sentences.size(), position);
        listener.changed();
    }
    public String current() { return sentences.isEmpty() ? "" : sentences.get(state.index); }
    public void playAllFromStart() {
        pause();
        lastBackIndex = -1;
        resumeOffset = 0;
        activeToken = "";
        sentenceMode = false;
        state.load(sentences.size(), 0);
        listener.changed();
        play();
    }
    /** A normal Play/Continue starts a fresh backwards-navigation sequence. */
    public void play() {
        lastBackIndex = -1;
        speakCurrent();
    }

    /**
     * First tap replays the last/current sentence. Each following tap walks
     * exactly one earlier sentence (never before sentence zero), including
     * when the TTS engine is still speaking the sentence just requested.
     */
    public void backOneSentence() {
        if (closed || sentences.isEmpty()) return;
        int target = lastBackIndex >= 0 ? Math.max(0, lastBackIndex - 1)
                : state.phase == PlaybackState.Phase.WAITING ? Math.max(0, state.index - 1)
                : state.index;
        pause();
        resumeOffset = 0;
        humanResumeMs = 0;
        humanResumeIndex = -1;
        state.load(sentences.size(), target);
        lastBackIndex = target;
        listener.changed();
        speakCurrent();
    }

    private void speakCurrent() {
        if (closed || !canPlay() || state.phase == PlaybackState.Phase.SPEAKING) return;
        handler.removeCallbacks(autoPlay);
        boolean restart = state.phase == PlaybackState.Phase.FINISHED;
        if (restart) { resumeOffset = 0; humanResumeMs = 0; humanResumeIndex = -1; }
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { error = "Звук занят другим приложением"; listener.changed(); return; }
        String token = state.play();
        if (token == null) return;
        if (humanSelected() || googleSelected()) {
            playRecording(token);
            listener.changed();
            return;
        }
        String full = current();
        if (resumeOffset < 0 || resumeOffset >= full.length()) resumeOffset = 0;
        utteranceBaseOffset = resumeOffset;
        activeToken = token;
        String originalSpeech = full.substring(utteranceBaseOffset);
        String spoken = RioplatensePronunciation.forSpeech(originalSpeech, learningLanguage);
        // Expanding 11:26–28 into Russian words changes TTS range offsets.
        // Do not mistake the spoken positions for positions in the displayed text.
        spokenTextChangesOffsets = !spoken.equals(originalSpeech);
        error = "";
        // Build a complete queue once: slow words and user-marked silence coexist.
        // When both features are off, retain a single natural-prosody TTS utterance.
        boolean slowWords = slowArticles && "es".equals(learningLanguage)
                && Math.abs(shortWordSpeed - speed) > 0.001f;
        List<Integer> gaps = new ArrayList<>();
        if (manualPauseMs > 0 && !"ru".equals(learningLanguage)) {
            for (int gap : manualPositions.getOrDefault(state.index, List.of()))
                if (gap > utteranceBaseOffset && gap < full.length())
                    gaps.add(gap - utteranceBaseOffset);
        }
        if (slowWords || !gaps.isEmpty()) {
            articleParts = ManualStoryPauses.pieces(originalSpeech, gaps, slowWords);
            boolean segmented = false;
            for (ManualStoryPauses.Piece part : articleParts)
                if (part.slow() || part.silence()) { segmented = true; break; }
            if (segmented) {
                articleFullOffset = utteranceBaseOffset;
                queueArticleSentence(token);
                listener.changed();
                return;
            }
            articleParts = List.of();
        }
        tts.setSpeechRate(speed);
        if (tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, token) == TextToSpeech.ERROR) failed(token);
        listener.changed();
    }

    /**
     * Queue the COMPLETE sentence before playback begins. The old implementation
     * waited for each onDone callback and issued QUEUE_FLUSH per word; on some
     * engines that created audible gaps even at a high normal speech rate.
     * Android copies the requested rate into each utterance when enqueued.
     */
    private void queueArticleSentence(String token) {
        if (closed || !token.equals(activeToken)
                || state.phase != PlaybackState.Phase.SPEAKING) return;
        for (int i = 0; i < articleParts.size(); i++) {
            ManualStoryPauses.Piece part = articleParts.get(i);
            String id = token + ":article:" + i;
            articleLastId = id;
            int queue = i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            int result;
            if (part.silence()) {
                // An actual silent interval, not an artificial change of word speed.
                result = tts.playSilentUtterance(manualPauseMs, queue, id);
            } else {
                String spoken = RioplatensePronunciation.forSpeech(
                        part.text(), learningLanguage).trim();
                tts.setSpeechRate(part.slow() ? shortWordSpeed : speed);
                Bundle params = new Bundle();
                params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
                result = spoken.isEmpty() ? TextToSpeech.ERROR
                        : tts.speak(spoken, queue, params, id);
            }
            if (result == TextToSpeech.ERROR) {
                clearArticleParts();
                tts.stop();
                failed(token);
                return;
            }
        }
    }

    private boolean isArticlePart(String id) {
        return id != null && !activeToken.isEmpty() && !articleParts.isEmpty()
                && id.startsWith(activeToken + ":article:");
    }

    private void rememberArticleRange(String id, int start) {
        if (closed || state.phase != PlaybackState.Phase.SPEAKING || !isArticlePart(id)) return;
        try {
            int position = Integer.parseInt(id.substring((activeToken + ":article:").length()));
            if (position < 0 || position >= articleParts.size()) return;
            ManualStoryPauses.Piece part = articleParts.get(position);
            if (part.silence()) return;
            // Keep original-string offsets intact for pause/resume.
            if (!RioplatensePronunciation.forSpeech(part.text(), learningLanguage)
                    .equals(part.text())) return;
            String full = current();
            resumeOffset = Math.max(0, Math.min(full.length(),
                    articleFullOffset + part.sourceStart() + start));
        } catch (NumberFormatException ignored) {
            // Stale callbacks must never move the resume position.
        }
    }

    private void clearArticleParts() {
        articleLastId = "";
        articleParts = List.of();
    }

    private void releaseHumanPlayer() {
        MediaPlayer old = humanPlayer;
        humanPlayer = null;
        if (old != null) {
            old.setOnCompletionListener(null);
            old.setOnErrorListener(null);
            old.setOnPreparedListener(null);
            try { old.release(); } catch (RuntimeException ignored) {}
        }
    }

    private void playRecording(String token) {
        java.io.File file = humanSelected() && humanAudio != null && humanAudio.recorded(state.index)
                ? humanAudio.segment(state.index)
                : googleSelected() && googleAudio != null && googleAudio.recorded(state.index)
                ? googleAudio.segment(state.index) : null;
        if (file == null) {
            failed(token);
            return;
        }
        final int index = state.index;
        final int startMs = humanResumeIndex == index ? humanResumeMs : 0;
        activeToken = token;
        error = "";
        releaseHumanPlayer();
        MediaPlayer mp = new MediaPlayer();
        humanPlayer = mp;
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            mp.setDataSource(file.getAbsolutePath());
            mp.setOnPreparedListener(prepared -> {
                if (closed || prepared != humanPlayer
                        || !token.equals(activeToken)
                        || state.phase != PlaybackState.Phase.SPEAKING) return;
                try {
                    if (startMs > 0) prepared.seekTo(startMs);
                    try {
                        prepared.setPlaybackParams(new PlaybackParams().setSpeed(speed));
                    } catch (RuntimeException unsupported) {
                        // Some devices cannot apply the lowest playback rates.
                        prepared.setPlaybackParams(new PlaybackParams().setSpeed(1.0f));
                        error = "Устройство не поддерживает выбранную скорость записи";
                    }
                    prepared.start();
                } catch (RuntimeException playbackError) {
                    releaseHumanPlayer();
                    failed(token);
                }
                listener.changed();
            });
            mp.setOnCompletionListener(donePlayer -> {
                if (donePlayer != humanPlayer || !token.equals(activeToken)) return;
                releaseHumanPlayer();
                humanResumeMs = 0;
                humanResumeIndex = -1;
                done(token);
            });
            mp.setOnErrorListener((badPlayer, what, extra) -> {
                if (badPlayer == humanPlayer) {
                    releaseHumanPlayer();
                    failed(token);
                }
                return true;
            });
            mp.prepareAsync();
        } catch (Exception playbackError) {
            releaseHumanPlayer();
            failed(token);
        }
    }

    private void rememberRange(String token, int start) {
        if (closed || !token.equals(activeToken) || state.phase != PlaybackState.Phase.SPEAKING
                || spokenTextChangesOffsets) return;
        String full = current();
        resumeOffset = Math.max(0, Math.min(full.length(), utteranceBaseOffset + start));
    }
    private void done(String token) {
        if (closed || !state.complete(token)) return;
        resumeOffset = 0;
        humanResumeMs = 0;
        humanResumeIndex = -1;
        activeToken = "";
        audio.abandonAudioFocusRequest(focus);
        listener.changed();
        if (!sentenceMode && state.phase == PlaybackState.Phase.WAITING) handler.postDelayed(autoPlay, pauseMs);
    }
    private void failed(String token) {
        if (closed || !state.complete(token)) return;
        // Completion advances; restore the failed sentence so retry cannot skip it.
        if (state.phase == PlaybackState.Phase.WAITING) state.index--;
        state.pause(); state.phase = PlaybackState.Phase.PAUSED;
        error = (humanSelected() || googleSelected()) ?
                "Не удалось воспроизвести сохранённое аудио." :
                "Не удалось озвучить. Проверьте установленный офлайн-голос.";
        audio.abandonAudioFocusRequest(focus); listener.changed();
    }
    public void pause() {
        handler.removeCallbacks(autoPlay);
        PlaybackState.Phase previous = state.phase;
        if (humanPlayer != null && previous == PlaybackState.Phase.SPEAKING) {
            try {
                humanResumeMs = humanPlayer.getCurrentPosition();
                humanResumeIndex = state.index;
            } catch (RuntimeException ignored) {
                humanResumeMs = 0;
                humanResumeIndex = -1;
            }
        }
        state.pause();
        clearArticleParts();
        releaseHumanPlayer();
        if (previous != PlaybackState.Phase.SPEAKING) {
            resumeOffset = 0;
            activeToken = "";
        }
        if (tts != null) tts.stop();
        audio.abandonAudioFocusRequest(focus);
        listener.changed();
    }
    public void repeat() {
        lastBackIndex = -1;
        boolean previous = state.phase == PlaybackState.Phase.WAITING;
        pause();
        resumeOffset = 0;
        humanResumeMs = 0;
        humanResumeIndex = -1;
        activeToken = "";
        if (previous && state.index > 0) state.index--;
        state.repeat(); play();
    }
    public void close() { pause(); closed = true; if (tts != null) tts.shutdown(); }
}
