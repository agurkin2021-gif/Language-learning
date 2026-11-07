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
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Collections;

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
    private final File prosodyCache;
    /** Keep the natural full-sentence WAV across plays and marker edits. */
    private final File sentenceAudioCache;
    private final ExecutorService prosodyFiles = Executors.newSingleThreadExecutor();
    /** A canceled splice may still be writing; delete its output only after it exits. */
    private final java.util.Set<File> prosodyOutputsInFlight =
            Collections.synchronizedSet(new java.util.HashSet<>());
    private volatile String prosodyId = "";
    private volatile TimedStoryCapture prosodyCapture;
    private String prosodySpoken = "";
    private List<Integer> prosodyGaps = List.of();
    private final List<ProsodyPauseAudio.WordTiming> prosodyTiming =
            Collections.synchronizedList(new ArrayList<>());
    private File prosodyInput, prosodyOutput, prosodyStableInput;
    private boolean prosodyPlayback;
    private final SharedPreferences pausePrefs;
    private final StoryAudioPreparer storyPreparation;
    private boolean preparationRequested;
    private String manualStoryKey = "";
    public boolean sentenceMode = true;
    public float speed = 0.85f;
    /** Separate rate for the six requested words in all Spanish stories. */
    public float shortWordSpeed = 0.45f;
    public int pauseMs = 1500;
    public String voiceDescription = "Подготовка голоса…";
    public String error = "";
    public SpeechPlayer(Context context, Listener listener) {
        this.listener = listener;
        prosodyCache = context.getCacheDir();
        sentenceAudioCache = new File(context.getFilesDir(), "story_sentence_auto_pause_wav");
        pausePrefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE);
        storyPreparation = new StoryAudioPreparer(context, handler, listener::changed);
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
                @Override public void onBeginSynthesis(String id, int sampleRate,
                                                        int audioFormat, int channelCount) {
                    if (StoryAudioPreparer.isPreparationId(id)) {
                        storyPreparation.begin(id, sampleRate, audioFormat, channelCount);
                        return;
                    }
                    TimedStoryCapture capture = prosodyCapture;
                    if (capture != null && isProsodyId(id))
                        capture.begin(id, sampleRate, audioFormat, channelCount);
                }
                @Override public void onAudioAvailable(String id, byte[] audio) {
                    if (StoryAudioPreparer.isPreparationId(id)) {
                        storyPreparation.audio(id, audio);
                        return;
                    }
                    TimedStoryCapture capture = prosodyCapture;
                    if (capture != null && isProsodyId(id)) capture.append(id, audio);
                }
                @Override public void onRangeStart(String id, int start, int end, int frame) {
                    if (StoryAudioPreparer.isPreparationId(id)) {
                        storyPreparation.range(id, start, end, frame);
                        return;
                    }
                    if (isProsodyId(id)) {
                        TimedStoryCapture capture = prosodyCapture;
                        if (capture != null) capture.range(id, start, end, frame);
                        return;
                    }
                    handler.post(() -> {
                        if (isArticlePart(id)) rememberArticleRange(id, start);
                        else rememberRange(id, start);
                    });
                }
                @Override public void onDone(String id) {
                    handler.post(() -> {
                        if (StoryAudioPreparer.isPreparationId(id)) storyPreparation.done(id);
                        else if (id != null && id.startsWith("card:")) finishCardSpeech();
                        else if (isProsodyId(id)) finishProsodySynthesis(id);
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
                        if (StoryAudioPreparer.isPreparationId(id)) storyPreparation.error(id);
                        else if (id != null && id.startsWith("card:")) finishCardSpeech();
                        else if (isProsodyId(id)) {
                            String token = activeToken;
                            cleanupProsody();
                            failed(token);
                        } else if (isArticlePart(id)) {
                            String originalToken = activeToken;
                            clearArticleParts();
                            if (tts != null) tts.stop(); // Cancel remaining queued pieces.
                            failed(originalToken);
                        } else failed(id);
                    });
                }
            });
            if (preparationRequested) prepareWholeStory();
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
            if (preparationRequested) prepareWholeStory();
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
    public boolean canPlay() {
        return manualPausesActive() ? ready && storyPreparation.isReady()
                : humanSelected() || googleSelected() || ready;
    }

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

    public void setManualPauseStoryKey(String storyKey) {
        manualStoryKey = storyKey == null ? "" : storyKey;
    }

    /** Generate every sentence, including ones without markers, BEFORE recording begins. */
    public void prepareWholeStory() {
        preparationRequested = manualPausesActive();
        storyPreparation.start(ready ? tts : null, sentences, manualPositions,
                manualPauseMs, manualStoryKey, learningLanguage, speed,
                selectedVoiceNameForExport());
        listener.changed();
    }

    public boolean storyAudioReady() {
        return !manualPausesActive() || storyPreparation.isReady();
    }

    public String storyAudioStatus() { return storyPreparation.status(); }
    public boolean storyAudioFailed() { return storyPreparation.hasError(); }

    /** Leaving the story cancels its muted pre-generation immediately. */
    public void cancelWholeStoryPreparation() {
        boolean wasPreparing = storyPreparation.isPreparing();
        storyPreparation.cancel();
        preparationRequested = false;
        if (wasPreparing && tts != null) tts.stop();
    }

    /** Identity includes story, sentence, speech rate and voice, not marker positions. */
    private String speechCacheKey(int sentenceIndex) {
        return manualStoryKey + ":auto-speech-v2:" + sentenceIndex
                + ":speed:" + Float.floatToIntBits(speed)
                + ":voice:" + selectedVoiceNameForExport();
    }

    private static String rangeCacheKey(File wav) {
        return "auto-speech-ranges-v2:" + wav.getName();
    }

    public boolean hasManualPauseHere() {
        return manualPauseMs > 0
                && !manualPositions.getOrDefault(state.index, List.of()).isEmpty()
                && !"ru".equals(learningLanguage);
    }

    public void load(String body, int position) {
        pause();
        storyPreparation.cancel();
        preparationRequested = false;
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
        if (closed || state.phase == PlaybackState.Phase.SPEAKING) return;
        if (!canPlay()) {
            if (manualPausesActive())
                error = "Аудио рассказа ещё не готово. Подготовка выполняется заранее.";
            listener.changed();
            return;
        }
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
        // Automatic alignment uses the whole sentence WAV. Restart at its beginning.
        if (manualPausesActive()) resumeOffset = 0;
        utteranceBaseOffset = resumeOffset;
        activeToken = token;
        if (manualPausesActive()) {
            File alreadyPrepared = storyPreparation.audioAt(state.index);
            if (alreadyPrepared == null) {
                error = "Готовая озвучка предложения отсутствует.";
                failed(token);
                return;
            }
            error = "";
            playProsodyFile(token, alreadyPrepared);
            listener.changed();
            return;
        }
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
        if (!gaps.isEmpty()) {
            // A manual pause is not punctuation: synthesize the entire sentence
            // before inserting digital silence so that the pitch is untouched.
            // Map ALL displayed markers to the unchanged whole-sentence TTS text.
            // Ambiguous Bible-reference normalization must not move or drop a pause.
            try {
                beginProsodySynthesis(token, spoken,
                        SpokenGapMapper.map(originalSpeech, spoken, gaps, learningLanguage));
            } catch (IllegalArgumentException mappingError) {
                error = "Точную границу паузы нельзя сопоставить с произношением: "
                        + mappingError.getMessage();
                tts.setSpeechRate(speed);
                if (tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, token)
                        == TextToSpeech.ERROR) failed(token);
            }
            listener.changed();
            return;
        }
        if (slowWords) {
            articleParts = ManualStoryPauses.pieces(originalSpeech, List.of(), true);
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

    public boolean preparingTextPauses() {
        return storyPreparation.isPreparing();
    }

    private boolean isProsodyId(String id) {
        return id != null && !prosodyId.isEmpty() && id.equals(prosodyId);
    }

    private File sentenceOriginalFile(String spoken) {
        String identity = speechCacheKey(state.index) + ":text:" + spoken;
        String name = UUID.nameUUIDFromBytes(
                identity.getBytes(StandardCharsets.UTF_8)).toString() + ".wav";
        return new File(sentenceAudioCache, name);
    }

    private void beginProsodySynthesis(String token, String spoken, List<Integer> gaps) {
        try {
            cleanupProsody();
            if (!sentenceAudioCache.exists() && !sentenceAudioCache.mkdirs())
                throw new IOException("Cannot create audio cache directory");
            prosodyStableInput = sentenceOriginalFile(spoken);
            prosodyOutput = File.createTempFile("prosody-paused-", ".wav", prosodyCache);
            prosodySpoken = spoken;
            prosodyGaps = List.copyOf(gaps);
            prosodyTiming.clear();
            prosodyId = token + ":prosody";
            // The word-to-frame map belongs to this exact WAV, not to a tap event.
            if (prosodyStableInput.isFile() && prosodyStableInput.length() > 44) {
                List<ProsodyPauseAudio.WordTiming> cached = ProsodyTimingCache.decode(
                        pausePrefs.getString(rangeCacheKey(prosodyStableInput), ""), spoken);
                if (ProsodyTimingCache.covers(spoken, gaps, cached)) {
                    prosodyInput = prosodyStableInput;
                    prosodyTiming.addAll(cached);
                    finishProsodySynthesis(prosodyId);
                    return;
                }
            }
            // synthesizeToFile does not deliver onRangeStart for the AOSP
            // file-synthesis route. Collect both PCM and timing from one muted
            // speak() request, then play that exact WAV with inserted silence.
            pausePrefs.edit().remove(rangeCacheKey(prosodyStableInput)).apply();
            if (prosodyStableInput.exists()) prosodyStableInput.delete();
            prosodyCapture = new TimedStoryCapture(prosodyId);
            tts.setSpeechRate(speed);
            Bundle muted = new Bundle();
            muted.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0f);
            if (tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, muted, prosodyId)
                    == TextToSpeech.ERROR) {
                cleanupProsody();
                error = "Голос не поддерживает получение аудио с координатами слов.";
                failed(token);
            }
        } catch (IOException | RuntimeException failure) {
            cleanupProsody();
            error = "Не удалось подготовить цельную озвучку.";
            failed(token);
        }
    }

    private void finishProsodySynthesis(String id) {
        if (!isProsodyId(id) || closed
                || state.phase != PlaybackState.Phase.SPEAKING) return;

        // The muted speak request yields both PCM and onRangeStart frames.
        // Capture is cleared only after onDone, never when individual chunks arrive.
        TimedStoryCapture capture = prosodyCapture;
        if (capture != null) {
            prosodyCapture = null;
            try {
                TimedStoryCapture.Snapshot snapshot = capture.snapshot();
                if (!ProsodyTimingCache.covers(prosodySpoken, prosodyGaps, snapshot.ranges()))
                    throw new IOException("Движок не передал границы всех выбранных слов");
                if (prosodyStableInput == null)
                    throw new IOException("No persistent original WAV path");
                File pending = File.createTempFile("speech-captured-", ".wav", prosodyCache);
                prosodyInput = pending;
                snapshot.save(pending);
                Files.move(pending.toPath(), prosodyStableInput.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                prosodyInput = prosodyStableInput;
                prosodyTiming.clear();
                prosodyTiming.addAll(snapshot.ranges());
                // Both audio and word frames are from the SAME synthesis request.
                pausePrefs.edit().putString(rangeCacheKey(prosodyStableInput),
                        ProsodyTimingCache.encode(snapshot.ranges())).apply();
            } catch (IOException | RuntimeException failure) {
                // Do not silently resume at a guessed or wrong word boundary.
                // Fall back to natural continuous speech when audio capture is
                // unavailable on a particular Android voice.
                String original = prosodySpoken;
                String token = activeToken;
                cleanupProsody();
                error = "Точный тайминг слов недоступен для этого голоса: "
                        + failure.getMessage();
                tts.setSpeechRate(speed);
                if (tts.speak(original, TextToSpeech.QUEUE_FLUSH, null, token)
                        == TextToSpeech.ERROR) failed(token);
                listener.changed();
                return;
            }
        }

        final List<ProsodyPauseAudio.WordTiming> wordFrames;
        synchronized (prosodyTiming) {
            wordFrames = List.copyOf(prosodyTiming);
        }
        final String token = activeToken;
        final File input = prosodyInput;
        final File output = prosodyOutput;
        final String text = prosodySpoken;
        final List<Integer> gaps = prosodyGaps;
        final int duration = manualPauseMs;
        // Strict: each visible marker must match an actual onRangeStart frame.
        // Never use text-proportional or acoustic-gap estimates.
        prosodyOutputsInFlight.add(output);
        prosodyFiles.execute(() -> {
            String problem = null;
            try {
                ProsodyPauseAudio.insertSilenceExact(
                        input, output, text, gaps, wordFrames, duration);
            } catch (Exception failure) {
                problem = failure.getMessage();
            }
            final String failureReason = problem;
            handler.post(() -> {
                prosodyOutputsInFlight.remove(output);
                if (!isProsodyId(id) || closed
                        || state.phase != PlaybackState.Phase.SPEAKING
                        || !token.equals(activeToken)) {
                    output.delete();
                    return;
                }
                if (failureReason != null) {
                    error = "Этот голос не дал точной границы слова. "
                            + "Пауза не вставлена: " + failureReason;
                    playProsodyFile(token, input);
                } else {
                    playProsodyFile(token, output);
                }
            });
        });
    }

    private void playProsodyFile(String token, File file) {
        releaseHumanPlayer();
        MediaPlayer mp = new MediaPlayer();
        humanPlayer = mp;
        prosodyPlayback = true;
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            mp.setDataSource(file.getAbsolutePath());
            mp.setOnPreparedListener(prepared -> {
                if (closed || prepared != humanPlayer || !token.equals(activeToken)
                        || state.phase != PlaybackState.Phase.SPEAKING) return;
                try {
                    prepared.start();
                } catch (RuntimeException failure) {
                    releaseHumanPlayer();
                    cleanupProsody();
                    failed(token);
                }
                listener.changed();
            });
            mp.setOnCompletionListener(completed -> {
                if (completed != humanPlayer || !token.equals(activeToken)) return;
                releaseHumanPlayer();
                cleanupProsody();
                done(token);
            });
            mp.setOnErrorListener((bad, what, extra) -> {
                if (bad == humanPlayer) {
                    releaseHumanPlayer();
                    cleanupProsody();
                    failed(token);
                }
                return true;
            });
            mp.prepareAsync();
        } catch (Exception failure) {
            releaseHumanPlayer();
            cleanupProsody();
            failed(token);
        }
    }

    private void cleanupProsody() {
        prosodyId = "";
        prosodyCapture = null;
        prosodySpoken = "";
        prosodyGaps = List.of();
        prosodyTiming.clear();
        prosodyPlayback = false;
        // Keep the whole-sentence recording and its saved word-frame mapping.
        if (prosodyInput != null && !prosodyInput.equals(prosodyStableInput))
            prosodyInput.delete();
        if (prosodyOutput != null && !prosodyOutputsInFlight.contains(prosodyOutput))
            prosodyOutput.delete();
        prosodyInput = null;
        prosodyOutput = null;
        prosodyStableInput = null;
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
        if (!sentenceMode && state.phase == PlaybackState.Phase.WAITING) {
            // User-marked pauses are the ONLY inserted waits in this mode.
            // The independent inter-sentence delay must not make a phantom
            // pause before the first word of the next sentence.
            handler.postDelayed(autoPlay, StoryPausePolicy.betweenSentences(
                    pauseMs, manualPausesActive()));
        }
    }
    private void failed(String token) {
        if (closed || !state.complete(token)) return;
        // Completion advances; restore the failed sentence so retry cannot skip it.
        if (state.phase == PlaybackState.Phase.WAITING) state.index--;
        state.pause(); state.phase = PlaybackState.Phase.PAUSED;
        if (error == null || error.isEmpty()) {
            error = (humanSelected() || googleSelected()) ?
                    "Не удалось воспроизвести сохранённое аудио." :
                    "Не удалось озвучить. Проверьте установленный офлайн-голос.";
        }
        audio.abandonAudioFocusRequest(focus); listener.changed();
    }
    public void pause() {
        handler.removeCallbacks(autoPlay);
        // Stop any in-flight background TTS before accepting new settings.
        // Prepared recordings remain available across ordinary Pause/Resume.
        boolean preparingInBackground = storyPreparation.isPreparing();
        PlaybackState.Phase previous = state.phase;
        if (humanPlayer != null && !prosodyPlayback
                && previous == PlaybackState.Phase.SPEAKING) {
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
        cleanupProsody();
        if (previous != PlaybackState.Phase.SPEAKING) {
            resumeOffset = 0;
            activeToken = "";
        }
        // A user pause or settings dialog must NOT interrupt full-story
        // pre-generation. It may be running while no audio is audible.
        if (tts != null && !preparingInBackground) tts.stop();
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
    public void close() {
        pause(); closed = true;
        storyPreparation.close();
        if (tts != null) tts.stop();
        prosodyFiles.shutdownNow();
        if (tts != null) tts.shutdown();
    }
}
