package ru.spanish.learning;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.app.backup.BackupManager;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private Store store;
    private Store spanishStore, englishStore;
    private final java.util.Map<String, Store> profileStores = new java.util.HashMap<>();
    private String activeProfile = StudyProfile.PAPA;
    private String studyLanguage = "es";
    private RemoteContentSync contentSync;
    private PersonalStorySync personalStorySync;
    private ChatGptAssistant assistant;
    private AlertDialog assistantDialog;
    private EditText assistantInput;
    private TextView assistantHistory;
    private TextView assistantStatus;
    private SpeechRecognizer speechRecognizer;
    private AssistantVoiceOutput assistantVoiceOutput;
    private Button assistantVoiceButton, assistantSendButton, assistantGapButton;
    private LinearLayout assistantProgressRow;
    private TextView assistantProgressLabel;
    private boolean assistantRequestInFlight;
    private boolean storyWordRequestInFlight;
    private int assistantRequestId;
    private String assistantRequestStage = "";
    private ChatGptAssistant.SavedStory pendingSavedStory;
    private boolean assistantVoiceActive, assistantVoiceProcessing, assistantVoiceReviewPending;
    private int assistantVoiceEpoch, assistantVoiceErrors;
    private final Handler assistantVoiceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingVoiceRestart;
    private static final int MIC_PERMISSION = 7301;
    private static final int STORY_RECORD_PERMISSION = 7302;
    private GoogleStoryBatch googleBatch;
    private AlertDialog googleBatchDialog;
    private HumanStoryAudio humanRecording;
    private AlertDialog humanRecordDialog;
    private TextView humanRecordText, humanRecordStatus;
    private Button humanRecordToggle, humanRecordPrevious, humanRecordNext, humanRecordDelete;
    private Button humanVoiceButton;
    private int humanRecordIndex;
    private boolean syncingContent;
    private final AtomicBoolean manualVocabularySyncing = new AtomicBoolean();
    private final AtomicBoolean familyTransferRunning = new AtomicBoolean();
    private boolean imageRepairRunning;
    private final AtomicBoolean imageRepairCancel = new AtomicBoolean();
    private String imageRepairMessage = "";
    private TextView imageRepairStatus;
    private Button imageRepairButton;
    private SpeechPlayer player;
    private SharedPreferences prefs;
    private LinearLayout content;
    private TextView sentence, sentenceRu, playbackStatus, cardVoiceStatus;
    private Button toggle, repeat;
    private List<String> storyTranslations = new java.util.ArrayList<>();
    private List<String> storyOriginalSentences = new java.util.ArrayList<>();
    private boolean storyReadRussian;
    private boolean darkTheme;
    private UiStyle ui;
    private int kind;
    private int category;
    private int reviewIndex;
    private long reviewStep;
    private boolean cardRevealed;
    private static final long CARD_PRONUNCIATION_DELAY_MS = 250L;
    private long swipeReleasedAtMs = -1L;
    private final Handler cardSpeechHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingCardSpeech;
    private String screen = "cards";
    private long storyId = -1;
    // Rendering-only Bible markers. The story text and TTS input remain unmodified.
    private boolean[] bibleRedChunks = new boolean[0];
    private boolean rendering;
    private AlertDialog cardDialog;
    private EditText draftSpanish, draftMeaning;
    private Spinner draftCategorySpinner;
    private int draftKind;
    private int draftCategory;
    @Override public void onCreate(Bundle saved) {
        darkTheme = getSharedPreferences("ui", MODE_PRIVATE).getBoolean("darkTheme", false);
        setTheme(darkTheme ? R.style.AppThemeDark : R.style.AppTheme);
        super.onCreate(saved);
        ui = new UiStyle(this, darkTheme);
        android.app.job.JobScheduler legacyUpdater = getSystemService(android.app.job.JobScheduler.class);
        if (legacyUpdater != null) {
            legacyUpdater.cancel(72101);
            legacyUpdater.cancel(72102);
        }
        prefs = getSharedPreferences("ui", MODE_PRIVATE);
        activeProfile = StudyProfile.active(this);
        studyLanguage = "en".equals(prefs.getString(StudyProfile.languageKey(activeProfile), "es")) ? "en" : "es";
        spanishStore = profileStore(activeProfile, false);
        if ("en".equals(studyLanguage)) englishStore = profileStore(activeProfile, true);
        store = "en".equals(studyLanguage) ? englishStore : spanishStore;
        contentSync = new RemoteContentSync();
        personalStorySync = new PersonalStorySync();
        assistant = new ChatGptAssistant(this);
        player = new SpeechPlayer(this, this::updatePlayer);
        player.setLearningLanguage(studyLanguage);
        player.speed = prefs.getFloat(prefKey("speed"), .85f); player.pauseMs = prefs.getInt(prefKey("pause"), 1500); player.sentenceMode = prefs.getBoolean(prefKey("sentenceMode"), true);
        screen = prefs.getString(prefKey("screen"), "cards");
        kind = prefs.getInt(prefKey("kind"), 0);
        category = prefs.getInt(prefKey("category"), 0);
        reviewIndex = prefs.getInt(prefKey("reviewIndex"), 0);
        reviewStep = prefs.getLong(prefKey("reviewStep"), 0);
        long last = prefs.getLong(prefKey("story"), -1);
        if ("player".equals(screen)) {
            for (Store.Story story : store.stories()) if (story.id() == last) { openStory(story); return; }
            screen = "stories";
        }
        render();
    }
    private boolean studyingEnglish() { return "en".equals(studyLanguage); }
    private Store profileStore(String profile, boolean english) {
        String key = StudyProfile.normalized(profile) + (english ? "-en" : "-es");
        Store existing = profileStores.get(key);
        if (existing != null) return existing;
        Store created = Store.forProfile(this, profile, english);
        profileStores.put(key, created);
        return created;
    }
    private String prefKey(String key) {
        return StudyProfile.preferenceKey(activeProfile, studyLanguage, key);
    }

    /** The only profile selector is inside Settings; no extra top-bar control. */
    private void chooseProfile(AlertDialog settingsDialog) {
        String[] choices = StudyProfile.LABELS;
        int selected = java.util.Arrays.asList(StudyProfile.IDS).indexOf(activeProfile);
        new AlertDialog.Builder(this).setTitle("Профиль")
                .setSingleChoiceItems(choices, selected, (dialog, which) -> {
                    dialog.dismiss();
                    String next = StudyProfile.IDS[which];
                    if (next.equals(activeProfile)) return;
                    if (imageRepairRunning || familyTransferRunning.get() || assistantRequestInFlight || storyWordRequestInFlight
                            || assistantVoiceProcessing) {
                        Toast.makeText(this, "Завершите текущую операцию перед сменой профиля",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    settingsDialog.dismiss();
                    selectProfile(next);
                }).setNegativeButton("Отмена", null).show();
    }

    private void selectProfile(String next) {
        next = StudyProfile.normalized(next);
        if (next.equals(activeProfile)) return;
        stopAssistantVoice();
        player.pause();
        cancelCardSpeech();
        remember();
        if (cardDialog != null && cardDialog.isShowing()) cardDialog.dismiss();
        pendingSavedStory = null;
        storyId = -1;
        activeProfile = next;
        // Persist selection before any image or sync operation reads active profile.
        prefs.edit().putString("activeProfile", next).commit();
        studyLanguage = "en".equals(prefs.getString(StudyProfile.languageKey(next), "es")) ? "en" : "es";
        spanishStore = profileStore(next, false);
        englishStore = studyingEnglish() ? profileStore(next, true) : null;
        store = studyingEnglish() ? englishStore : spanishStore;
        player.setLearningLanguage(studyLanguage);
        player.speed = prefs.getFloat(prefKey("speed"), .85f);
        player.pauseMs = prefs.getInt(prefKey("pause"), 1500);
        player.sentenceMode = prefs.getBoolean(prefKey("sentenceMode"), true);
        kind = prefs.getInt(prefKey("kind"), 0);
        category = prefs.getInt(prefKey("category"), 0);
        reviewIndex = prefs.getInt(prefKey("reviewIndex"), 0);
        reviewStep = prefs.getLong(prefKey("reviewStep"), 0);
        screen = prefs.getString(prefKey("screen"), "cards");
        if ("player".equals(screen)) screen = "stories";
        cardRevealed = false;
        render();
        syncContent();
        PrivateVocabularySync.schedule(this);
    }

    private void selectStudyLanguage(String next) {
        if (!"en".equals(next) && !"es".equals(next)) return;
        if (next.equals(studyLanguage)) return;
        if (familyTransferRunning.get()) {
            Toast.makeText(this, "Дождитесь завершения переноса слов", Toast.LENGTH_LONG).show();
            return;
        }
        if (imageRepairRunning) {
            Toast.makeText(this, "Сначала завершите или остановите создание картинок",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (assistantRequestInFlight || storyWordRequestInFlight) {
            Toast.makeText(this, "Дождитесь завершения проверки ChatGPT",
                    Toast.LENGTH_LONG).show();
            return;
        }
        player.pause();
        cancelCardSpeech();
        remember();
        // Reset the old story ID before changing stores or TTS callbacks:
        // story row IDs can overlap between the Spanish and English databases.
        storyId = -1;
        if (cardDialog != null && cardDialog.isShowing()) cardDialog.dismiss();
        pendingSavedStory = null;
        studyLanguage = next;
        prefs.edit().putString(StudyProfile.languageKey(activeProfile), studyLanguage).commit();
        if (studyingEnglish() && englishStore == null) englishStore = profileStore(activeProfile, true);
        store = studyingEnglish() ? englishStore : spanishStore;
        player.setLearningLanguage(studyLanguage);
        player.speed = prefs.getFloat(prefKey("speed"), .85f);
        player.pauseMs = prefs.getInt(prefKey("pause"), 1500);
        player.sentenceMode = prefs.getBoolean(prefKey("sentenceMode"), true);
        kind = prefs.getInt(prefKey("kind"), 0);
        category = prefs.getInt(prefKey("category"), 0);
        reviewIndex = prefs.getInt(prefKey("reviewIndex"), 0);
        reviewStep = prefs.getLong(prefKey("reviewStep"), 0);
        screen = prefs.getString(prefKey("screen"), "cards");
        if ("player".equals(screen)) screen = "stories";
        storyId = -1;
        cardRevealed = false;
        render();
        syncContent();
        PrivateVocabularySync.schedule(this);
    }

    private void chooseStudyLanguage() {
        new AlertDialog.Builder(this)
                .setTitle("Язык изучения")
                .setSingleChoiceItems(new String[]{"Испанский (ES)", "Английский (EN)"},
                        studyingEnglish() ? 1 : 0, (dialog, which) -> {
                            dialog.dismiss();
                            selectStudyLanguage(which == 1 ? "en" : "es");
                        })
                .setNegativeButton("Отмена", null).show();
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private int pageBg() { return ui.background; }
    private int primaryText() { return ui.foreground; }
    private int secondaryText() { return ui.muted; }
    private int cardBg() { return ui.surface; }
    private int borderColor() { return ui.outline; }

    private TextView textInto(LinearLayout parent, String value, int size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(primaryText());
        t.setLineSpacing(dp(2), 1.04f);
        if (size >= 23) {
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setLetterSpacing(-0.01f);
        }
        t.setPadding(0, dp(size >= 23 ? 8 : 5), 0, dp(size >= 23 ? 7 : 5));
        parent.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }
    private TextView text(String value, int size) { return textInto(content, value, size); }

    private LinearLayout.LayoutParams spaced(int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(bottom);
        return lp;
    }

    private Button button(String value, Runnable action) {
        Button b = rowButton(value, action);
        content.addView(b, spaced(10));
        return b;
    }
    private Button rowButton(String value, Runnable action) {
        Button b = new Button(this);
        b.setText(value);
        ui.style(b, UiStyle.Tone.SECONDARY);
        if (action != null) b.setOnClickListener(v -> action.run());
        return b;
    }
    private void cancelCardSpeech() {
        if (pendingCardSpeech != null) {
            cardSpeechHandler.removeCallbacks(pendingCardSpeech);
            pendingCardSpeech = null;
        }
    }
    private void scheduleCardSpeech(String spanish, TextView word) {
        scheduleCardSpeech(spanish, word, CARD_PRONUNCIATION_DELAY_MS);
    }
    private void scheduleCardSpeech(String spanish, TextView word, long delayMs) {
        cancelCardSpeech();
        Runnable speech = () -> {
            pendingCardSpeech = null;
            if (!"cards".equals(screen) || isFinishing() || isDestroyed()
                    || !word.isAttachedToWindow() || !Boolean.TRUE.equals(word.getTag())) return;
            player.speakCard(spanish);
        };
        pendingCardSpeech = speech;
        cardSpeechHandler.postDelayed(speech, Math.max(0L, delayMs));
    }
    private void remember() {
        prefs.edit().putString(prefKey("screen"), screen)
                .putLong(prefKey("story"), storyId).putInt(prefKey("kind"), kind)
                .remove(prefKey("archive")).putInt(prefKey("category"), category)
                .putInt(prefKey("reviewIndex"), reviewIndex)
                .putLong(prefKey("reviewStep"), reviewStep)
                .putFloat(prefKey("speed"), player.speed).putInt(prefKey("pause"), player.pauseMs)
                .putBoolean(prefKey("sentenceMode"), player.sentenceMode).apply();
        new BackupManager(this).dataChanged();
        if (storyId >= 0 && player.state.count > 0) store.position(storyId, player.state.index);
    }
    private void navigate(String next) {
        if (imageRepairRunning) {
            Toast.makeText(this, "Создаются картинки. Дождитесь завершения или нажмите «Остановить».",
                    Toast.LENGTH_LONG).show();
            return;
        }
        player.pause(); remember();
        if ("player".equals(screen) && !"player".equals(next))
            player.setLearningLanguage(studyLanguage);
        storyId = -1; screen = next; render();
    }
    private void render() {
        cancelCardSpeech();
        rendering = true; sentence = null; sentenceRu = null; playbackStatus = null;
        cardVoiceStatus = null; imageRepairStatus = null; imageRepairButton = null;
        toggle = null; repeat = null; humanVoiceButton = null;
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(pageBg());
        root.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets; });
        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(dp(16), dp(12), dp(16), dp(8));
        for (String label : new String[]{"Карточки", "Рассказы"}) {
            boolean selected = label.equals("Карточки") ? screen.equals("cards") : !screen.equals("cards");
            Button tab = rowButton(label, () -> navigate(label.equals("Карточки") ? "cards" : "stories"));
            ui.style(tab, selected ? UiStyle.Tone.SELECTED : UiStyle.Tone.TAB);
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(0, dp(52), 1);
            tabParams.setMarginEnd(dp(7));
            tabs.addView(tab, tabParams);
        }
        Button languageButton = rowButton(studyingEnglish() ? "EN ▾" : "ES ▾",
                this::chooseStudyLanguage);
        ui.style(languageButton, UiStyle.Tone.OUTLINE);
        languageButton.setTextSize(12);
        languageButton.setPadding(dp(2), 0, dp(2), 0);
        languageButton.setContentDescription(studyingEnglish()
                ? "Английский. Выбрать язык изучения"
                : "Испанский. Выбрать язык изучения");
        LinearLayout.LayoutParams languageParams = new LinearLayout.LayoutParams(dp(53), dp(52));
        languageParams.setMarginEnd(dp(6));
        tabs.addView(languageButton, languageParams);
        if (!screen.equals("cards")) {
            Button settingsButton = rowButton("⚙", this::showSettings);
            ui.style(settingsButton, UiStyle.Tone.ICON);
            tabs.addView(settingsButton, new LinearLayout.LayoutParams(dp(52), dp(52)));
        }
        root.addView(tabs);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(dp(20), dp(8), dp(20), dp(26));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        if (screen.equals("cards")) cards(); else if (screen.equals("player")) storyPlayer(); else stories();
        rendering = false; remember(); updatePlayer();
    }
    private void showSettings() {
        player.pause();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(4), dp(18), dp(4));

        Switch darkSwitch = new Switch(this);
        darkSwitch.setText("Тёмная тема");
        darkSwitch.setTextSize(18);
        darkSwitch.setTextColor(primaryText());
        darkSwitch.setMinHeight(dp(56));
        darkSwitch.setChecked(darkTheme);
        box.addView(darkSwitch, new LinearLayout.LayoutParams(-1, -2));

        Button mode = rowButton("Прослушивание: " + (player.sentenceMode ? "по одному предложению" : "весь рассказ без остановок"), () ->
                new AlertDialog.Builder(this).setTitle("Режим прослушивания")
                        .setItems(new String[]{"Весь рассказ без остановок", "По одному предложению"}, (d,w) -> {
                            player.pause();
                            player.sentenceMode = w == 1;
                            remember();
                            updatePlayer();
                        }).show());
        box.addView(mode, spaced(10));

        TextView selectedVoice = textInto(box, player.voiceDescription, 14);
        selectedVoice.setTextColor(secondaryText());
        selectedVoice.setLayoutParams(spaced(studyingEnglish() ? 12 : 8));
        if (!studyingEnglish()) {
            Button voicePreview = rowButton("Проверить ll: talle, calle, lluvia", () -> {
                if (player.ready()) {
                    player.speakCard("El talle de la calle. La lluvia llegó ayer.");
                } else {
                    Toast.makeText(this, player.voiceDescription, Toast.LENGTH_LONG).show();
                }
            });
            box.addView(voicePreview, spaced(8));
            Button selectVoice = rowButton("Выбрать испанский голос", () -> {
                List<SpeechPlayer.VoiceChoice> choices = player.spanishVoiceChoices();
                if (choices.isEmpty()) {
                    new AlertDialog.Builder(this).setTitle("Нет голоса Латинской Америки")
                            .setMessage("Установите испанский голос Аргентины (es-AR) или Мексики (es-MX) в настройках синтеза речи Android. Европейский es-ES не используется.")
                            .setPositiveButton("Закрыть", null).show();
                    return;
                }
                String[] labels = new String[choices.size()];
                int selected = -1;
                for (int i = 0; i < choices.size(); i++) {
                    labels[i] = choices.get(i).label;
                    if (choices.get(i).name.equals(player.selectedSpanishVoiceName()))
                        selected = i;
                }
                new AlertDialog.Builder(this)
                        .setTitle("Выбор испанского голоса")
                        .setSingleChoiceItems(labels, selected, (dialog, index) -> {
                            if (player.selectSpanishVoice(choices.get(index).name)) {
                                selectedVoice.setText(player.voiceDescription);
                                dialog.dismiss();
                                player.speakCard("El talle de la calle. La lluvia llegó ayer.");
                            } else {
                                Toast.makeText(this, "Не удалось выбрать голос", Toast.LENGTH_LONG).show();
                            }
                        }).setNegativeButton("Отмена", null).show();
            });
            box.addView(selectVoice, spaced(12));
        }

        Button speed = rowButton("Скорость речи: " + player.speed + "× (рассказы и карточки)", null);
        speed.setOnClickListener(v ->
                new AlertDialog.Builder(this).setTitle("Скорость речи — рассказы и карточки")
                        .setItems(new String[]{"0.3×", "0.4×", "0.5×", "0.6×", "0.75×", "0.85×", "1.0×", "1.2×"}, (d,w) -> {
                            player.pause();
                            player.speed = new float[]{.3f,.4f,.5f,.6f,.75f,.85f,1f,1.2f}[w];
                            speed.setText("Скорость речи: " + player.speed + "× (рассказы и карточки)");
                            remember();
                            updatePlayer();
                        }).show());
        box.addView(speed, spaced(10));

        Button pause = rowButton("Пауза между предложениями: " + player.pauseMs / 1000f + " с", () ->
                new AlertDialog.Builder(this).setTitle("Пауза в непрерывном режиме")
                        .setItems(new String[]{"0 с", "1 с", "1.5 с", "3 с", "5 с"}, (d,w) -> {
                            player.pause();
                            player.pauseMs = new int[]{0,1000,1500,3000,5000}[w];
                            remember();
                            updatePlayer();
                        }).show());
        box.addView(pause, spaced(10));

        Button profileButton = rowButton("Профиль: " + StudyProfile.name(activeProfile), null);
        box.addView(profileButton, spaced(10));
        box.addView(rowButton("Синхронизация", this::syncAllFamilyProfiles), spaced(10));
        // The transfer button exists only for the selected recipient; Papa is source only.
        if (!StudyProfile.isPapa(activeProfile)) {
            box.addView(rowButton("Добавить слова Папы → " + StudyProfile.name(activeProfile),
                    this::confirmFamilyTransfer), spaced(10));
        }
        box.addView(rowButton("Google — сохранить озвучку всех рассказов",
                this::startGoogleStoryRecording), spaced(10));
        box.addView(rowButton("Обновление", this::startUpdateNow), spaced(10));
        box.addView(rowButton("Версия / сборка", this::showBuild), spaced(10));

        Button resetButton = rowButton("Сброс базы", null);
        ui.style(resetButton, UiStyle.Tone.OUTLINE);
        resetButton.setTextColor(ui.danger);
        box.addView(resetButton, spaced(18));

        // Keep the destructive action reachable even on small screens.
        ScrollView settingsScroll = new ScrollView(this);
        settingsScroll.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Настройки")
                .setView(settingsScroll)
                .setNegativeButton("Закрыть", null)
                .create();

        profileButton.setOnClickListener(v -> chooseProfile(dialog));
        resetButton.setOnClickListener(v -> confirmResetDatabase(dialog));
        darkSwitch.setOnCheckedChangeListener((v, checked) -> {
            if (checked == darkTheme) return;
            darkTheme = checked;
            prefs.edit().putBoolean("darkTheme", checked).commit();
            new BackupManager(this).dataChanged();
            dialog.dismiss();
            recreate();
        });
        dialog.show();
    }

    /** Explicit local transfer from Papa's SQLite and private picture folders. */
    private void confirmFamilyTransfer() {
        if (StudyProfile.isPapa(activeProfile)) return;
        if (familyTransferRunning.get() || manualVocabularySyncing.get() || imageRepairRunning
                || assistantRequestInFlight || storyWordRequestInFlight) {
            Toast.makeText(this, "Завершите текущую операцию", Toast.LENGTH_LONG).show();
            return;
        }
        // Freeze recipient and language before opening either dialog.
        final String targetProfile = activeProfile;
        final boolean english = studyingEnglish();
        new AlertDialog.Builder(this)
                .setTitle("Слова Папы → " + StudyProfile.name(targetProfile))
                .setItems(new String[]{
                        "Указать количество вручную",
                        "Добавить все отсутствующие слова"
                }, (dialog, which) -> {
                    if (which == 0) showFamilyTransferQuantity(targetProfile, english);
                    else confirmFamilyTransferAll(targetProfile, english);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showFamilyTransferQuantity(String targetProfile, boolean english) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        textInto(box, "Сколько новых слов Папы добавить в профиль "
                + StudyProfile.name(targetProfile) + "? "
                + "Выученные, изучаемые и ранее удалённые слова получателя пропускаются. "
                + "Переводы и готовые картинки копируются. Другие профили не изменяются.", 16);
        EditText amount = new EditText(this);
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        amount.setSingleLine(true);
        amount.setHint("Количество, например 20");
        amount.setText("20");
        amount.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        ui.styleInput(amount);
        box.addView(amount, spaced(8));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Слова Папы → " + StudyProfile.name(targetProfile))
                .setView(box)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Добавить", null)
                .create();
        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    int count;
                    try {
                        count = Integer.parseInt(amount.getText().toString().trim());
                    } catch (NumberFormatException error) {
                        amount.setError("Введите количество от 1 до 100000");
                        return;
                    }
                    if (count < 1 || count > 100000) {
                        amount.setError("Введите количество от 1 до 100000");
                        return;
                    }
                    dialog.dismiss();
                    runFamilyTransfer(targetProfile, count, english);
                }));
        dialog.show();
    }

    private void confirmFamilyTransferAll(String targetProfile, boolean english) {
        new AlertDialog.Builder(this)
                .setTitle("Добавить все отсутствующие слова?")
                .setMessage("Профиль: " + StudyProfile.name(targetProfile)
                        + ". Будут скопированы все слова Папы с переводами и готовыми "
                        + "картинками, которых ещё нет в выученных и изучаемых карточках "
                        + "этого профиля. Ранее удалённые слова не восстановятся. "
                        + "Папа и другие профили останутся без изменений.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Добавить все", (dialog, which) ->
                        runFamilyTransfer(targetProfile, Integer.MAX_VALUE, english))
                .show();
    }

    private void runFamilyTransfer(String targetProfile, int maxWords, boolean english) {
        if (!StudyProfile.MAMA.equals(targetProfile) && !StudyProfile.DIMA.equals(targetProfile))
            return;
        if (!familyTransferRunning.compareAndSet(false, true)) return;
        new Thread(() -> {
            FamilyVocabularyTransfer.Result result = null;
            Exception failure = null;
            try {
                result = FamilyVocabularyTransfer.copyFromPapa(
                        getApplicationContext(), targetProfile, maxWords, english);
            } catch (Exception error) {
                failure = error;
            } finally {
                familyTransferRunning.set(false);
            }
            final FamilyVocabularyTransfer.Result done = result;
            final Exception error = failure;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (done == null) {
                    new AlertDialog.Builder(this).setTitle("Копирование не завершено")
                            .setMessage("Перенос остановлен: " + (error == null ? "неизвестная ошибка" : error.getMessage())
                                    + ". Уже перенесённые данные сохранены; повторный запуск не создаст дублей.")
                            .setPositiveButton("Закрыть", null).show();
                } else {
                    render();
                    new AlertDialog.Builder(this).setTitle("Копирование завершено · " + (english ? "EN" : "ES"))
                            .setMessage(StudyProfile.name(done.targetProfile())
                                    + ": добавлено карточек " + done.cards()
                                    + ", картинок " + done.pictures() + ".\n"
                                    + "Папа и остальные профили без изменений. "
                                    + "Существующие карточки и прогресс сохранены.")
                            .setPositiveButton("Закрыть", null).show();
                }
            });
        }, "family-vocabulary-transfer").start();
    }

    /** One tap imports public vocabularies or performs full bidirectional sync when authorized. */
    private void syncAllFamilyProfiles() {
        if (!manualVocabularySyncing.compareAndSet(false, true)) {
            Toast.makeText(this, "Синхронизация уже выполняется", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean twoWay = PrivateVocabularySync.isConfigured(this);
        Toast.makeText(this, twoWay
                ? "Синхронизация: Папа, Мама, Дима"
                : "Загрузка словарей из GitHub: Папа, Мама, Дима", Toast.LENGTH_SHORT).show();
        java.util.function.Consumer<Boolean> completed = success -> {
            manualVocabularySyncing.set(false);
            if (isFinishing() || isDestroyed()) return;
            if (success) {
                syncContent();
                if ("cards".equals(screen) && (cardDialog == null || !cardDialog.isShowing()))
                    render();
            }
            new AlertDialog.Builder(this)
                    .setTitle(success
                            ? twoWay ? "Синхронизация завершена" : "Импорт завершён"
                            : "Синхронизация не выполнена")
                    .setMessage(success
                            ? twoWay
                              ? "Папа, Мама, Дима: оба языка синхронизированы с GitHub."
                              : PrivateVocabularySync.status(this)
                                + "\n\nЧтобы изменения с телефона тоже сохранялись в GitHub, "
                                + "необходимо один раз предоставить разрешение на запись."
                            : PrivateVocabularySync.status(this))
                    .setPositiveButton("Закрыть", null)
                    .setNeutralButton(twoWay ? (success ? null : "Настроить GitHub")
                            : "Подключить запись",
                            twoWay && success ? null
                            : (dialog, which) -> showVocabularySyncSettings())
                    .show();
        };
        if (twoWay) {
            if (!PrivateVocabularySync.syncNow(this, completed)) {
                manualVocabularySyncing.set(false);
                showVocabularySyncSettings();
            }
        } else {
            PrivateVocabularySync.importPublicNow(this, completed);
        }
    }

    private void showVocabularySyncSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(12), dp(22), dp(6));
        TextView explanation = new TextView(this);
        explanation.setText("Единый репозиторий Language-learning: отдельные полные словари " +
                "Папа / Мама / Дима, слова и устойчивые выражения, освоенные карточки. " +
                "Синхронизация в ОБЕ стороны выполняется после изменения слов и " +
                "периодически при наличии сети. Удалённые карточки не восстанавливаются. " +
                "Внешний ChatGPT должен проверять словарь профиля перед добавлением новых " +
                "фраз и созданием рассказа. ВАЖНО: репозиторий публичный, " +
                "поэтому словарные карточки и отметки освоения доступны по ссылке. " +
                "Для выгрузки нужен GitHub token с Contents: read/write на Language-learning; " +
                "токен в чат не отправлять.\n\n" +
                "Репозиторий: " + PrivateVocabularySync.repository(this) + "\n" +
                "Словарь: " + PrivateVocabularySync.status(this) + "\n" +
                "Рассказы: " + PersonalStorySync.status(this));
        explanation.setTextColor(primaryText());
        explanation.setTextSize(14);
        box.addView(explanation, spaced(12));

        EditText repo = new EditText(this);
        repo.setSingleLine(true);
        repo.setHint("agurkin2021-gif/Language-learning");
        repo.setText(PrivateVocabularySync.repository(this));
        ui.styleInput(repo);
        box.addView(repo, spaced(12));

        EditText token = new EditText(this);
        token.setSingleLine(true);
        token.setHint("GitHub fine-grained token");
        token.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        ui.styleInput(token);
        box.addView(token, spaced(12));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        AlertDialog connectionDialog = new AlertDialog.Builder(this)
                .setTitle("Синхронизация словаря — Language-learning")
                .setView(scroll)
                .setNegativeButton("Отмена", null)
                .setNeutralButton("Отключить", (dialog, which) -> {
                    PrivateVocabularySync.disable(this);
                    Toast.makeText(this, "Синхронизация словаря отключена", Toast.LENGTH_LONG).show();
                })
                .setPositiveButton("Подключить", null)
                .create();
        connectionDialog.setOnShowListener(dialog ->
                connectionDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    String authorization = token.getText().toString().trim();
                    if (authorization.isEmpty()) {
                        token.setError("Нужно разрешение GitHub на запись в Language-learning");
                        token.requestFocus();
                        return;
                    }
                    try {
                        PrivateVocabularySync.configure(this,
                                repo.getText().toString(), authorization);
                        connectionDialog.dismiss();
                        Toast.makeText(this, "Подключено. Выгружаю все словари...",
                                Toast.LENGTH_LONG).show();
                        syncAllFamilyProfiles();
                    } catch (Exception error) {
                        token.setError("Не удалось сохранить подключение GitHub");
                        token.requestFocus();
                    }
                }));
        connectionDialog.show();
    }

    private void confirmResetDatabase(AlertDialog settingsDialog) {
        if (imageRepairRunning) {
            Toast.makeText(this, "Остановите создание картинок перед сбросом базы", Toast.LENGTH_LONG).show();
            return;
        }
        if (assistantRequestInFlight) {
            Toast.makeText(this, "Дождитесь завершения ChatGPT перед сбросом базы", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Сбросить базу?")
                .setMessage("Профиль «" + StudyProfile.name(activeProfile)
                        + "». Будут удалены слова, выражения, рассказы и прогресс "
                        + (studyingEnglish() ? "АНГЛИЙСКОГО" : "ИСПАНСКОГО")
                        + " языка и его история ChatGPT. Остальные профили и языки не изменятся. Отменить удаление нельзя.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Продолжить", (first, which) ->
                        new AlertDialog.Builder(this)
                                .setTitle("Точно удалить всё?")
                                .setMessage("Это второе и последнее подтверждение. База останется пустой после перезапуска и обновления приложения. Общий контент из GitHub больше не будет автоматически загружаться на этом телефоне.")
                                .setNegativeButton("Отмена", null)
                                .setPositiveButton("Удалить всё", (second, selected) -> resetDatabase(settingsDialog))
                                .show())
                .show();
    }

    private void resetDatabase(AlertDialog settingsDialog) {
        try {
            store.resetLearningData();
            assistant.clearHistory(store);
            HumanStoryAudio.deleteLanguage(this, activeProfile, studyLanguage);
            GoogleStoryAudio.deleteLanguage(this, activeProfile, studyLanguage);
        } catch (RuntimeException e) {
            new AlertDialog.Builder(this)
                    .setTitle("База не сброшена")
                    .setMessage("Не удалось удалить данные. Ничего не изменено.")
                    .setPositiveButton("Закрыть", null)
                    .show();
            return;
        }
        settingsDialog.dismiss();
        stopAssistantVoice();
        cancelCardSpeech();
        player.pause();
        clearCardDraft();
        storyId = -1;
        reviewIndex = 0;
        reviewStep = 0;
        kind = 0;
        category = 0;
        cardRevealed = false;
        screen = "cards";
        render();
        Toast.makeText(this, "База очищена", Toast.LENGTH_LONG).show();
    }

    private void showBuild() {
        cancelCardSpeech();
        player.pause();
        String commit = BuildConfig.SOURCE_REVISION;
        if (commit.length() > 7) commit = commit.substring(0, 7);
        new AlertDialog.Builder(this).setTitle("Версия и сборка")
                .setMessage(getString(BuildConfig.DEBUG ? R.string.build_details_debug : R.string.build_details, BuildConfig.VERSION_NAME, commit))
                .setNegativeButton("Закрыть", null)
                .setNeutralButton("Сборка", (dialog, which) -> {
                    String base = "https://github.com/agurkin2021-gif/Language-learning/actions/";
                    String url = BuildConfig.BUILD_RUN_ID.isEmpty() ? base : base + "runs/" + BuildConfig.BUILD_RUN_ID;
                    openBrowser(url);
                }).show();
    }
    /**
     * Prefer the distinct family updater v2 package, never the legacy updater.
     * If not installed, use the built-in signed family-builds channel.
     */
    private void startUpdateNow() {
        cancelCardSpeech();
        player.pause();
        remember();

        // A dedicated, differently named family updater can be installed next to
        // the legacy updater. Only the new package is eligible for this action.
        Intent familyUpdater = new Intent("ru.spanish.learning.familyupdater.UPDATE")
                .setPackage("ru.spanish.learning.familyupdater");
        if (BuildConfig.DEBUG) {
            String testPath = getIntent().getStringExtra("updater_test_path");
            if (testPath != null && !testPath.isEmpty()) {
                familyUpdater.putExtra("test_apk_path", testPath);
            }
        }
        try {
            // Android 11+ queries are declared in AndroidManifest.xml.
            if (getPackageManager().resolveActivity(familyUpdater, 0) != null) {
                startActivity(familyUpdater);
                return;
            }
        } catch (ActivityNotFoundException ignored) {
            // The signed in-app updater is the safe fallback.
        }

        if (Build.VERSION.SDK_INT >= 26
                && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this)
                    .setTitle("Разрешение на обновление")
                    .setMessage("Разрешите приложению «Изучение испанского» устанавливать обновления. " +
                            "После возвращения нажмите «Обновление» ещё раз.")
                    .setPositiveButton("Открыть настройки", (dialog, which) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(this, "Разрешите установку из приложения в настройках Android",
                                    Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Отмена", null).show();
            return;
        }

        // Fallback: check family-builds and verify the signed update directly.
        AppUpdater.updateNow(this);
        Toast.makeText(this, "Проверяю обновления семейной версии…", Toast.LENGTH_LONG).show();
    }
    private void openBrowser(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (ActivityNotFoundException e) {
            Toast.makeText(this, "На телефоне нет браузера для открытия GitHub", Toast.LENGTH_LONG).show();
        }
    }
    private void showAssistant() {
        if (imageRepairRunning) {
            Toast.makeText(this, "Пока создаются картинки, ChatGPT занят. Сначала остановите процесс.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        cancelCardSpeech();
        player.pause();
        remember();
        if (assistantDialog != null && assistantDialog.isShowing()) return;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(6), dp(18), dp(6));

        assistantStatus = new TextView(this);
        assistantStatus.setTextSize(14);
        assistantStatus.setTextColor(secondaryText());
        assistantStatus.setPadding(0, dp(6), 0, dp(8));
        box.addView(assistantStatus);

        Button connect = new Button(this);
        connect.setText("Подключить ChatGPT");
        ui.style(connect, UiStyle.Tone.PRIMARY);
        box.addView(connect, spaced(12));

        ScrollView historyScroll = new ScrollView(this);
        assistantHistory = new TextView(this);
        assistantHistory.setTextSize(16);
        assistantHistory.setTextColor(primaryText());
        assistantHistory.setPadding(0, dp(10), 0, dp(10));
        assistantHistory.setText(assistant.historyText(store).isEmpty()
                ? "Здесь можно говорить или писать. Контекст приложения уже передаётся автоматически."
                : assistant.historyText(store));
        assistantHistory.setPadding(dp(14), dp(14), dp(14), dp(14));
        historyScroll.setBackground(ui.rounded(ui.soft, 15, ui.outline));
        historyScroll.addView(assistantHistory);
        LinearLayout.LayoutParams historyParams = new LinearLayout.LayoutParams(-1, dp(240));
        historyParams.bottomMargin = dp(12);
        box.addView(historyScroll, historyParams);

        assistantInput = new EditText(this);
        assistantInput.setHint("Напишите или наговорите задачу…");
        assistantInput.setMinLines(2);
        assistantInput.setMaxLines(5);
        assistantInput.setTextSize(17);
        ui.styleInput(assistantInput);
        box.addView(assistantInput, spaced(12));

        // Optional editable presets: no network request occurs until "Отправить".
        HorizontalScrollView presetScroll = new HorizontalScrollView(this);
        presetScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        String lang = studyingEnglish() ? "английских" : "испанских";
        String region = studyingEnglish() ? "" : " для повседневного общения в Буэнос-Айресе";
        String[][] presets = {
                {"10 слов A1", "Добавь 10 новых частотных " + lang
                        + " слов уровня A1" + region + ". Проверь русский перевод и категорию каждого. "
                        + "Не добавляй уже изученные или имеющиеся слова. Для каждого нужна точная картинка или эмодзи."},
                {"10 глаголов", "Добавь 10 новых " + lang
                        + " глаголов уровня A1" + region + " с точным русским переводом и иллюстрацией. "
                        + "Не повторяй слова из моего словаря."},
                {"10 существительных", "Добавь 10 новых " + lang
                        + " существительных уровня A1" + region
                        + " с точным русским переводом и подходящим эмодзи либо рисунком. Без дублей."},
                {"Кафе", "Добавь 10 новых " + lang + " слов A1 по теме кафе" + region
                        + ". С точным русским переводом и подходящей иллюстрацией. Без дублей."},
                {"Улица", "Добавь 10 новых " + lang + " слов A1 по теме улицы и транспорта"
                        + region + ". С точным русским переводом и подходящей иллюстрацией. Без дублей."},
                {"Магазин", "Добавь 10 новых " + lang + " слов A1 по теме магазина"
                        + region + ". С точным русским переводом и подходящей иллюстрацией. Без дублей."}
        };
        // The first action in the preset row opens a topic picker and starts
        // a single capped gap-analysis request on selection.
        Button gapButton = new Button(this);
        gapButton.setText("Пробелы в словаре ▾");
        gapButton.setTextSize(13);
        gapButton.setAllCaps(false);
        gapButton.setContentDescription("Анализ недостающих слов и выражений по темам");
        ui.style(gapButton, UiStyle.Tone.SELECTED);
        gapButton.setOnClickListener(v -> chooseVocabularyGapTopic());
        LinearLayout.LayoutParams gapSlot = new LinearLayout.LayoutParams(-2, dp(42));
        gapSlot.rightMargin = dp(8);
        presetRow.addView(gapButton, gapSlot);
        assistantGapButton = gapButton;

        for (String[] preset : presets) {
            Button choice = new Button(this);
            choice.setText(preset[0]);
            choice.setTextSize(12);
            choice.setAllCaps(false);
            ui.style(choice, UiStyle.Tone.OUTLINE);
            choice.setOnClickListener(v -> {
                if (assistantRequestInFlight) return;
                assistantInput.setText(preset[1]);
                assistantInput.setSelection(assistantInput.length());
            });
            LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(-2, dp(42));
            slot.rightMargin = dp(5);
            presetRow.addView(choice, slot);
        }
        presetScroll.addView(presetRow);
        LinearLayout.LayoutParams presetParams = new LinearLayout.LayoutParams(-1, dp(44));
        presetParams.bottomMargin = dp(7);
        box.addView(presetScroll, presetParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button mic = new Button(this);
        mic.setText("🎙 Разговор");
        ui.style(mic, UiStyle.Tone.OUTLINE);
        Button send = new Button(this);
        send.setText("Отправить");
        ui.style(send, UiStyle.Tone.PRIMARY);
        actions.addView(mic, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(0, -2, 1);
        sendParams.setMarginStart(dp(6));
        actions.addView(send, sendParams);
        box.addView(actions);
        assistantVoiceButton = mic;
        assistantSendButton = send;

        // In-place, indeterminate feedback for real model work: stays visible
        // until either the answer or a verified database save is received.
        assistantProgressRow = new LinearLayout(this);
        assistantProgressRow.setOrientation(LinearLayout.HORIZONTAL);
        assistantProgressRow.setGravity(Gravity.CENTER_VERTICAL);
        assistantProgressRow.setPadding(0, dp(8), 0, dp(3));
        ProgressBar spinner = new ProgressBar(this, null,
                android.R.attr.progressBarStyleSmall);
        spinner.setIndeterminate(true);
        spinner.getIndeterminateDrawable().setTint(ui.accent);
        assistantProgressRow.addView(spinner, new LinearLayout.LayoutParams(dp(30), dp(30)));
        assistantProgressLabel = new TextView(this);
        assistantProgressLabel.setTextColor(ui.accent);
        assistantProgressLabel.setTextSize(14);
        assistantProgressLabel.setPadding(dp(7), 0, 0, 0);
        assistantProgressRow.addView(assistantProgressLabel,
                new LinearLayout.LayoutParams(0, -2, 1));
        assistantProgressRow.setVisibility(View.GONE);
        box.addView(assistantProgressRow);

        TextView voiceHelp = new TextView(this);
        voiceHelp.setText("Скажите задачу — ИИ проверит распознавание. Сомнительную фразу можно исправить перед отправкой.");
        voiceHelp.setTextColor(secondaryText());
        voiceHelp.setTextSize(13);
        voiceHelp.setPadding(0, dp(8), 0, dp(6));
        box.addView(voiceHelp);

        Runnable refreshConnection = () -> {
            boolean connected = assistant.isConnected();
            String account = assistant.accountLabel();
            assistantStatus.setText(connected
                    ? "ChatGPT подключён" + (account.isEmpty() ? "" : " • " + account)
                    : "Нужно один раз подключить этот ChatGPT-аккаунт.");
            connect.setVisibility(connected ? View.GONE : View.VISIBLE);
            send.setEnabled(connected && !assistantRequestInFlight);
        };
        refreshConnection.run();

        connect.setOnClickListener(v -> {
            connect.setEnabled(false);
            assistantStatus.setText("Открываю одноразовое подключение ChatGPT…");
            assistant.connect(this, (ok, message) -> {
                connect.setEnabled(true);
                assistantStatus.setText(message);
                refreshConnection.run();
            });
        });

        mic.setOnClickListener(v -> {
            if (assistantVoiceActive) stopAssistantVoice();
            else {
                if (!assistant.isConnected()) {
                    assistantStatus.setText("Сначала подключите ChatGPT.");
                    return;
                }
                assistantVoiceActive = true;
                assistantVoiceReviewPending = false;
                assistantVoiceErrors = 0;
                updateAssistantVoiceButton();
                startAssistantVoice();
            }
        });
        send.setOnClickListener(v -> {
            if (assistantRequestInFlight) return; // never send the same request twice
            boolean voice = assistantVoiceReviewPending;
            if (assistantVoiceActive) stopAssistantVoice();
            assistantVoiceReviewPending = false;
            if (voice) {
                assistantVoiceActive = true;
                updateAssistantVoiceButton();
            }
            sendAssistantMessage(assistantInput.getText().toString().trim(), voice);
        });

        assistantDialog = new AlertDialog.Builder(this)
                .setTitle(studyingEnglish()
                        ? "ChatGPT • Изучение английского"
                        : "ChatGPT • Изучение испанского")
                .setView(box)
                .setNegativeButton("Закрыть", null)
                .create();
        assistantDialog.setOnDismissListener(d -> {
            stopAssistantVoice();
            assistantInput = null;
            assistantHistory = null;
            assistantStatus = null;
            assistantVoiceButton = null;
            assistantSendButton = null;
            assistantGapButton = null;
            assistantProgressRow = null;
            assistantProgressLabel = null;
            if (!isFinishing() && !isDestroyed() && !"player".equals(screen)) render();
        });
        assistantDialog.show();
        if (assistantRequestInFlight) updateAssistantBusy(true, assistantRequestStage);
        else if (pendingSavedStory != null) {
            ChatGptAssistant.SavedStory ready = pendingSavedStory;
            pendingSavedStory = null;
            showStoryReadyDialog(ready);
        }
    }

    private void chooseVocabularyGapTopic() {
        if (assistantRequestInFlight || assistantInput == null
                || assistantDialog == null || !assistantDialog.isShowing()) return;
        new AlertDialog.Builder(this)
                .setTitle("Пробелы в словаре · до 10 карточек")
                .setItems(VocabularyGapTopics.names(), (dialog, index) -> {
                    if (assistantRequestInFlight || assistantInput == null) return;
                    String request = VocabularyGapTopics.prompt(index, studyingEnglish());
                    assistantInput.setText(request);
                    assistantInput.setSelection(assistantInput.length());
                    if (!assistant.isConnected()) {
                        if (assistantStatus != null)
                            assistantStatus.setText("Подключите ChatGPT, затем нажмите «Отправить».");
                        return;
                    }
                    sendAssistantMessage(request, false);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void updateAssistantVoiceButton() {
        if (assistantVoiceButton != null)
            assistantVoiceButton.setText(assistantVoiceActive ? "■ Остановить" : "🎙 Разговор");
    }

    private void stopAssistantVoice() {
        assistantVoiceActive = false;
        assistantVoiceProcessing = false;
        assistantVoiceReviewPending = false;
        assistantVoiceEpoch++;
        if (pendingVoiceRestart != null) {
            assistantVoiceHandler.removeCallbacks(pendingVoiceRestart);
            pendingVoiceRestart = null;
        }
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (assistantVoiceOutput != null) assistantVoiceOutput.stop();
        updateAssistantVoiceButton();
    }

    /** Show a spinner and a changed Send button while a request is genuinely running. */
    void updateAssistantBusy(boolean busy, String stage) {
        assistantRequestInFlight = busy;
        if (stage != null && !stage.trim().isEmpty()) assistantRequestStage = stage;
        if (assistantProgressLabel != null) assistantProgressLabel.setText(assistantRequestStage);
        if (assistantProgressRow != null)
            assistantProgressRow.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (assistantSendButton != null) {
            assistantSendButton.setText(busy ? "⏳ Генерирую…" : "Отправить");
            assistantSendButton.setEnabled(!busy && assistant.isConnected());
        }
        if (assistantInput != null) assistantInput.setEnabled(!busy);
        if (assistantGapButton != null) assistantGapButton.setEnabled(!busy);
        if (assistantVoiceButton != null)
            assistantVoiceButton.setEnabled(!busy || assistantVoiceActive);
        if (busy && assistantStatus != null) assistantStatus.setText(assistantRequestStage);
    }

    /** The popup is based on the database ID returned from successful add_story. */
    private void showStoryReadyDialog(ChatGptAssistant.SavedStory saved) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle("РАССКАЗ ГОТОВ")
                .setMessage("«" + saved.title() + "»\n\nРассказ сохранён в библиотеке.")
                .setPositiveButton("Открыть рассказ", (dialog, which) -> {
                    Store.Story selected = null;
                    for (Store.Story story : store.stories()) {
                        if (story.id() == saved.id()) {
                            selected = story;
                            break;
                        }
                    }
                    if (selected == null) {
                        Toast.makeText(this, "Не удалось найти сохранённый рассказ",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (assistantDialog != null && assistantDialog.isShowing())
                        assistantDialog.dismiss();
                    openStory(selected);
                })
                .setNegativeButton("Позже", null)
                .show();
    }

    private void sendAssistantMessage(String message, boolean fromVoice) {
        if (assistantInput == null || assistantRequestInFlight) return;
        if (message.isEmpty()) {
            assistantInput.setError("Введите сообщение");
            if (fromVoice) stopAssistantVoice();
            return;
        }
        if (!assistant.isConnected()) {
            assistantStatus.setText("Сначала подключите ChatGPT.");
            if (fromVoice) stopAssistantVoice();
            return;
        }
        assistantVoiceProcessing = true;
        final int voiceEpoch = assistantVoiceEpoch;
        final int requestId = ++assistantRequestId;
        updateAssistantBusy(true, "Отправляю запрос в ChatGPT…");

        assistant.sendWithProgress(store, message, new ChatGptAssistant.SendUpdates() {
            @Override public void progress(String stage) {
                runOnUiThread(() -> {
                    if (requestId != assistantRequestId || isFinishing() || isDestroyed()) return;
                    updateAssistantBusy(true, stage);
                });
            }

            @Override public void finished(boolean ok, String answer,
                                           ChatGptAssistant.SavedStory savedStory) {
                runOnUiThread(() -> {
                    if (requestId != assistantRequestId || isFinishing() || isDestroyed()) return;
                    assistantVoiceProcessing = false;
                    updateAssistantBusy(false, null);
                    boolean visible = assistantDialog != null && assistantDialog.isShowing();
                    boolean storedStory = savedStory != null;

                    if (!visible) {
                        if (storedStory) {
                            pendingSavedStory = savedStory;
                            Toast.makeText(MainActivity.this,
                                    "Рассказ «" + savedStory.title() + "» готова",
                                    Toast.LENGTH_LONG).show();
                        }
                        return;
                    }
                    if (!ok && !storedStory) {
                        if (fromVoice) stopAssistantVoice();
                        assistantStatus.setText(answer);
                        return;
                    }
                    if (assistantInput != null) assistantInput.setText("");
                    if (assistantHistory != null)
                        assistantHistory.setText(assistant.historyText(store));
                    if (storedStory) {
                        if (fromVoice) stopAssistantVoice();
                        assistantStatus.setText("Рассказ сохранён: " + savedStory.title());
                        showStoryReadyDialog(savedStory);
                        return;
                    }
                    if (!ok) {
                        assistantStatus.setText(answer);
                        return;
                    }
                    if (!fromVoice) {
                        assistantStatus.setText("Готово");
                        return;
                    }
                    if (!assistantVoiceActive || voiceEpoch != assistantVoiceEpoch) {
                        assistantStatus.setText("Готово");
                        return;
                    }
                    if (assistantVoiceOutput == null)
                        assistantVoiceOutput = new AssistantVoiceOutput(MainActivity.this);
                    assistantStatus.setText("ChatGPT отвечает вслух…");
                    assistantVoiceOutput.speak(answer, spoken -> {
                        if (!assistantVoiceActive || voiceEpoch != assistantVoiceEpoch) return;
                        if (!spoken) {
                            stopAssistantVoice();
                            assistantStatus.setText("Нет русского голоса синтеза Android. Установите русский голос в настройках телефона.");
                            return;
                        }
                        assistantStatus.setText("Продолжаем разговор…");
                        pendingVoiceRestart = () -> {
                            pendingVoiceRestart = null;
                            if (assistantVoiceActive && voiceEpoch == assistantVoiceEpoch)
                                startAssistantVoice();
                        };
                        assistantVoiceHandler.postDelayed(pendingVoiceRestart, 650);
                    });
                });
            }
        });
    }

    private void startAssistantVoice() {
        if (!assistantVoiceActive || assistantInput == null || isFinishing() || isDestroyed()) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopAssistantVoice();
            assistantStatus.setText("На этом телефоне недоступен сервис распознавания речи Android.");
            return;
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (assistantVoiceOutput != null) assistantVoiceOutput.stop();
        assistantVoiceProcessing = false;
        final int epoch = ++assistantVoiceEpoch;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            private boolean active() {
                return assistantVoiceActive && assistantVoiceEpoch == epoch
                        && assistantDialog != null && assistantDialog.isShowing();
            }
            @Override public void onReadyForSpeech(Bundle p) {
                if (active()) assistantStatus.setText("Слушаю. Говорите задачу…");
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float value) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() {
                if (active()) assistantStatus.setText("Распознаю речь…");
            }
            @Override public void onError(int error) {
                if (!active() || assistantVoiceProcessing) return;
                if ((error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                        && assistantVoiceErrors++ < 2) {
                    assistantStatus.setText("Не расслышала. Повторите ещё раз…");
                    pendingVoiceRestart = () -> {
                        pendingVoiceRestart = null;
                        if (assistantVoiceActive && epoch == assistantVoiceEpoch) startAssistantVoice();
                    };
                    assistantVoiceHandler.postDelayed(pendingVoiceRestart, 550);
                } else {
                    stopAssistantVoice();
                    assistantStatus.setText("Распознавание не удалось. Нажмите «Разговор», чтобы повторить.");
                }
            }
            @Override public void onResults(Bundle results) {
                if (!active() || assistantVoiceProcessing) return;
                List<String> heard = VoiceTranscript.candidates(
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
                if (heard.isEmpty()) {
                    onError(SpeechRecognizer.ERROR_NO_MATCH);
                    return;
                }
                assistantVoiceErrors = 0;
                assistantVoiceProcessing = true;
                float[] confidence = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
                final float score = confidence != null && confidence.length > 0 ? confidence[0] : -1f;
                assistantInput.setText(heard.get(0));
                assistantInput.setSelection(assistantInput.length());
                assistantStatus.setText("ИИ проверяет, что именно вы сказали…");
                assistant.refineVoiceTranscript(store, heard, (ok, revision) -> runOnUiThread(() -> {
                    if (!active()) return;
                    assistantVoiceProcessing = false;
                    String corrected = ok ? VoiceTranscript.modelReply(revision) : "";
                    if (corrected.isEmpty()) corrected = heard.get(0);
                    assistantInput.setText(corrected);
                    assistantInput.setSelection(assistantInput.length());
                    if (ok && VoiceTranscript.safeToAutoSend(heard.get(0), corrected, score)) {
                        sendAssistantMessage(corrected, true);
                    } else {
                        stopAssistantVoice();
                        assistantVoiceReviewPending = true;
                        assistantStatus.setText(ok
                                ? "Проверьте текст: ИИ обнаружил неоднозначность. Исправьте и нажмите «Отправить»."
                                : "ИИ не смог проверить услышанное. Проверьте текст и нажмите «Отправить».");
                    }
                }));
            }
            @Override public void onPartialResults(Bundle partial) {
                if (!active() || assistantVoiceProcessing) return;
                List<String> words = VoiceTranscript.candidates(
                        partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
                if (!words.isEmpty()) {
                    assistantInput.setText(words.get(0));
                    assistantInput.setSelection(assistantInput.length());
                }
            }
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        if (Build.VERSION.SDK_INT >= 33) {
            ArrayList<String> hints = studyingEnglish()
                    ? new ArrayList<>(java.util.Arrays.asList(
                    "English", "word", "phrase", "story", "сказку", "словарь",
                    "карточки", "английский", "выражение"))
                    : new ArrayList<>(java.util.Arrays.asList(
                    "vos", "tenés", "querés", "fui", "dijo", "mate", "сказку",
                    "карточки", "словарь", "испанский", "вспомнил", "выражение"));
            for (Store.Card card : store.vocabulary()) {
                if (hints.size() >= 35) break;
                if (card.mastered() && card.spanish().length() >= 3
                        && card.spanish().length() < 35) hints.add(card.spanish());
            }
            intent.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, hints);
        }
        try {
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            stopAssistantVoice();
            assistantStatus.setText("Не удалось открыть микрофон Android.");
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == MIC_PERMISSION) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED
                    && assistantVoiceActive) startAssistantVoice();
            else {
                stopAssistantVoice();
                if (assistantStatus != null) assistantStatus.setText("Разрешите доступ к микрофону для голосового разговора.");
            }
        } else if (requestCode == STORY_RECORD_PERMISSION) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
                beginHumanRecording();
            else Toast.makeText(this, "Для записи голоса нужен доступ к микрофону",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    private void cards() {
        player.pause();
        LinearLayout headingRow = new LinearLayout(this);
        headingRow.setOrientation(LinearLayout.HORIZONTAL);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);

        // Same title line, with a small live total from the active language DB.
        TextView cardsHeading = textInto(headingRow, "Карточки", 28);
        cardsHeading.setTag("cards-heading");
        cardsHeading.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));

        TextView learnedCount = textInto(headingRow,
                "Выучено слов: " + store.learnedWordCount(), 11);
        learnedCount.setTag("learned-word-count");
        learnedCount.setTextColor(secondaryText());
        learnedCount.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        learnedCount.setSingleLine(true);
        learnedCount.setPadding(dp(4), 0, 0, 0);
        learnedCount.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        content.addView(headingRow, new LinearLayout.LayoutParams(-1, -2));

        TextView label = text(studyingEnglish()
                ? "Английский • личный словарь" : "Испанский • аргентинский вариант", 13);
        label.setTextColor(secondaryText());
        cardVoiceStatus = text(player.voiceDescription, 12);
        cardVoiceStatus.setTextColor(secondaryText());

        TextView hint = text("Нажмите для перевода · листайте в обе стороны · × — удалить · ✓ — знаю", 15);
        hint.setTextColor(secondaryText());
        hint.setPadding(0, dp(2), 0, dp(12));

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        Button typeButton = rowButton(kind == 0 ? "Слова ▾" : "Устойчивые выражения ▾", () ->
                new AlertDialog.Builder(this).setTitle("Тип карточек")
                        .setItems(new String[]{"Слова", "Устойчивые выражения"}, (d,w) -> {
                            kind = w;
                            if (kind == 1) category = 0;
                            reviewIndex = 0;
                            cardRevealed = false;
                            render();
                        }).show());
        ui.style(typeButton, UiStyle.Tone.OUTLINE);
        filters.addView(typeButton, new LinearLayout.LayoutParams(0, -2, 1));

        if (kind == 0) {
            String[] labels = {"Все слова", "Глаголы", "Существительные", "Прилагательные"};
            Button categoryButton = rowButton(labels[Math.max(0, Math.min(category, 3))] + " ▾", () ->
                    new AlertDialog.Builder(this).setTitle("Категория слов")
                            .setItems(labels, (d,w) -> {
                                category = w;
                                reviewIndex = 0;
                                cardRevealed = false;
                                render();
                            }).show());
            ui.style(categoryButton, UiStyle.Tone.OUTLINE);
            LinearLayout.LayoutParams categoryParams = new LinearLayout.LayoutParams(0, -2, 1);
            categoryParams.setMarginStart(dp(6));
            filters.addView(categoryButton, categoryParams);
        }
        content.addView(filters, spaced(10));

        // Each profile/language/category or phrase type has a separate active window.
        // The numeric control itself is rendered at the BOTTOM via showCardTools().
        int studyLimit = Math.max(0,
                prefs.getInt(prefKey(StudyBatch.preferenceKey(kind, kind == 0 ? category : 0)), 0));

        // Query the ENTIRE unmastered pool, then show only the selected window.
        // As soon as ✓ archives one of the window's words, the next waiting
        // word joins automatically on render without resetting any other data.
        List<Store.Card> unmastered = store.cards(false, kind, kind == 0 ? category : 0);
        List<Store.Card> cards = StudyBatch.active(unmastered, studyLimit);
        String countLabel = cards.isEmpty() ? "Здесь пока нет карточек"
                : studyLimit > 0
                    ? "В изучении: " + cards.size() + " / " + studyLimit
                      + " · В резерве: " + (unmastered.size() - cards.size())
                    : "Карточек: " + cards.size();
        TextView count = text(countLabel, 16);
        count.setTextColor(secondaryText());
        count.setPadding(0, dp(2), 0, dp(10));
        if (cards.isEmpty()) {
            TextView emptyVersion = text("Версия " + BuildConfig.VERSION_NAME, 11);
            emptyVersion.setTextColor(secondaryText());
            emptyVersion.setGravity(Gravity.CENTER);
            showCardTools();
            return;
        }

        if (reviewIndex < 0 || reviewIndex >= cards.size()) reviewIndex = 0;
        Store.Card card = cards.get(reviewIndex);

        FrameLayout cardFrame = new FrameLayout(this);
        cardFrame.setClipChildren(false);
        cardFrame.setClipToPadding(false);
        cardFrame.setPadding(dp(3), dp(3), dp(3), dp(3));
        content.addView(cardFrame, spaced(5));

        int size = cards.size();
        CardFace previous = makeCardFace(cards.get((reviewIndex + size - 1) % size), false);
        CardFace front = makeCardFace(card, cardRevealed);
        CardFace next = makeCardFace(cards.get((reviewIndex + 1) % size), false);

        final CardFace[] visibleFaces = {previous, front, next};
        // Create the counter now so the swipe listener can update only its
        // text, without reconstructing the entire screen after every gesture.
        TextView counter = new TextView(this);
        counter.setText((reviewIndex + 1) + " / " + cards.size());
        counter.setTextSize(14);
        counter.setGravity(Gravity.CENTER);
        counter.setTextColor(ui.accent);
        counter.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        counter.setPadding(0, dp(2), 0, dp(10));

        // Two distinct actions: red left × physically deletes the card; green right ✓ archives it.
        TextView deleteForever = new TextView(this);
        deleteForever.setText("×");
        deleteForever.setTextSize(29);
        deleteForever.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        deleteForever.setTextColor(Color.WHITE);
        deleteForever.setGravity(Gravity.CENTER);
        deleteForever.setTag("card-delete-forever");
        deleteForever.setContentDescription("Удалить карточку навсегда");
        deleteForever.setBackground(ui.ripple(ui.danger, 24, Color.TRANSPARENT));
        deleteForever.setElevation(dp(12));
        deleteForever.setOnClickListener(v -> {
            cancelCardSpeech();
            player.pause();
            Store.Card selected = cards.get(reviewIndex);
            new AlertDialog.Builder(this)
                    .setTitle("Удалить навсегда?")
                    .setMessage("Карточка «" + selected.spanish()
                            + "» будет удалена из словаря без возможности восстановления. "
                            + "В освоенное она не попадёт.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Удалить", (dialog, which) -> {
                        if (store.deleteCard(selected.id())) {
                            if (reviewIndex >= cards.size() - 1) reviewIndex = 0;
                            cardRevealed = false;
                            render();
                        }
                    })
                    .show();
        });

        TextView known = new TextView(this);
        known.setText("✓");
        known.setTextSize(28);
        known.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        known.setTextColor(Color.WHITE);
        known.setGravity(Gravity.CENTER);
        known.setTag("card-mark-known");
        known.setContentDescription("Знаю — перенести карточку в освоенное");
        known.setBackground(ui.ripple(darkTheme ? 0xFF257D50 : 0xFF19824B,
                24, Color.TRANSPARENT));
        known.setElevation(dp(12));
        known.setOnClickListener(v -> {
            cancelCardSpeech();
            player.pause();
            store.review(cards.get(reviewIndex).id(), 2, 0);
            if (reviewIndex >= cards.size() - 1) reviewIndex = 0;
            cardRevealed = false;
            render();
        });

        CardSwipePager strip = new CardSwipePager(this,
                previous.view(), front.view(), next.view(), size > 1,
                new CardSwipePager.Listener() {
                    @Override public void onTap() {
                        cancelCardSpeech();
                        player.pause();
                        Store.Card visible = cards.get(reviewIndex);
                        TextView visibleWord = visibleFaces[1].word();
                        cardRevealed = !cardRevealed;
                        visibleWord.setText(cardRevealed ? visible.meaning() : visible.spanish());
                        visibleWord.setTag(!cardRevealed);
                        if (!cardRevealed) scheduleCardSpeech(visible.spanish(), visibleWord);
                    }
                    @Override public void onDragStarted() {
                        cancelCardSpeech();
                        swipeReleasedAtMs = -1L;
                        player.pause();
                    }
                    @Override public void onPageSettling(int delta) {
                        // Count the 205 ms swipe animation as part of the wait.
                        swipeReleasedAtMs = SystemClock.uptimeMillis();
                    }
                    @Override public void onOffset(float dx) {
                        // Keep the known action anchored to the card viewport:
                        // it must not jump back from the edge at the end.
                    }
                    @Override public void onPageChanged(int delta) {
                        final int oldIndex = reviewIndex;
                        final int n = cards.size();
                        CardFace recycled;
                        CardFace outgoing;
                        if (delta > 0) {
                            recycled = visibleFaces[0];
                            outgoing = visibleFaces[1];
                            visibleFaces[0] = outgoing;
                            visibleFaces[1] = visibleFaces[2];
                            visibleFaces[2] = recycled;
                        } else {
                            recycled = visibleFaces[2];
                            outgoing = visibleFaces[1];
                            visibleFaces[2] = outgoing;
                            visibleFaces[1] = visibleFaces[0];
                            visibleFaces[0] = recycled;
                        }
                        reviewIndex = (reviewIndex + delta + n) % n;
                        cardRevealed = false;
                        // The outgoing page is now fully outside the viewport.
                        // Restore Spanish there before a possible reverse swipe.
                        outgoing.word().setText(cards.get(oldIndex).spanish());
                        outgoing.word().setTag(true);
                        // Only this far-away page needs a new image. The visible
                        // page is a preloaded, persistent WebView: no white blink.
                        int farIndex = delta > 0 ? (reviewIndex + 1) % n
                                : (reviewIndex + n - 1) % n;
                        bindCardFace(recycled, cards.get(farIndex));
                        counter.setText((reviewIndex + 1) + " / " + n);
                        prefs.edit().putInt(prefKey("reviewIndex"), reviewIndex).apply();
                        long remaining = CardSpeechTiming.remainingAfterRelease(
                                swipeReleasedAtMs, SystemClock.uptimeMillis(),
                                CARD_PRONUNCIATION_DELAY_MS);
                        swipeReleasedAtMs = -1L;
                        scheduleCardSpeech(cards.get(reviewIndex).spanish(),
                                visibleFaces[1].word(), remaining);
                    }
                });
        strip.setTag("card-swipe-viewport");
        cardFrame.addView(strip, new FrameLayout.LayoutParams(-1, dp(315)));

        // Edit the currently centered word or phrase without leaving the cards screen.
        TextView more = new TextView(this);
        more.setText("⋮");
        more.setTextSize(29);
        more.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        more.setTextColor(primaryText());
        more.setGravity(Gravity.CENTER);
        more.setTag("card-edit-menu");
        more.setContentDescription("Изменить карточку");
        // Plain three-dot glyph: no circle, border, ripple background or shadow.
        more.setBackground(null);
        more.setElevation(0f);
        more.setPadding(0, 0, 0, 0);
        more.setOnClickListener(v -> {
            cancelCardSpeech();
            player.pause();
            android.widget.PopupMenu menu = new android.widget.PopupMenu(this, more);
            menu.getMenu().add("Изменить карточку").setOnMenuItemClickListener(item -> {
                editExistingCard(cards.get(reviewIndex));
                return true;
            });
            menu.show();
        });
        FrameLayout.LayoutParams editParams =
                new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM | Gravity.END);
        editParams.bottomMargin = dp(10);
        editParams.rightMargin = dp(10);
        cardFrame.addView(more, editParams);

        FrameLayout.LayoutParams deleteParams =
                new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.START);
        deleteParams.topMargin = dp(9);
        deleteParams.leftMargin = dp(10);
        cardFrame.addView(deleteForever, deleteParams);
        FrameLayout.LayoutParams knownParams =
                new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.END);
        knownParams.topMargin = dp(9);
        knownParams.rightMargin = dp(10);
        cardFrame.addView(known, knownParams);

        TextView version = text("Версия " + BuildConfig.VERSION_NAME, 11);
        version.setTextColor(secondaryText());
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, dp(2), 0, dp(2));
        content.addView(counter, new LinearLayout.LayoutParams(-1, -2));
        if (!cardRevealed) scheduleCardSpeech(card.spanish(), visibleFaces[1].word());
        showCardTools();
    }

    private record CardFace(LinearLayout view, TextView fallback,
                            android.webkit.WebView picture, TextView word) {}

    private CardFace makeCardFace(Store.Card card, boolean revealed) {
        LinearLayout face = new LinearLayout(this);
        face.setOrientation(LinearLayout.VERTICAL);
        face.setGravity(Gravity.CENTER_HORIZONTAL);
        face.setPadding(dp(12), dp(12), dp(12), dp(12));
        face.setBackground(ui.cardSurface());
        face.setElevation(dp(5));

        FrameLayout visual = new FrameLayout(this);
        LinearLayout.LayoutParams pictureParams = new LinearLayout.LayoutParams(-1, dp(150));
        pictureParams.bottomMargin = dp(6);
        face.addView(visual, pictureParams);
        TextView fallback = new TextView(this);
        fallback.setText(CardVisualHint.forCard(card));
        fallback.setTextSize(54);
        fallback.setGravity(Gravity.CENTER);
        visual.addView(fallback, new FrameLayout.LayoutParams(-1, -1));
        android.webkit.WebView picture = new android.webkit.WebView(this);
        visual.addView(picture, new FrameLayout.LayoutParams(-1, -1));
        CardImageLoader.load(this, picture, fallback, card.spanish(), studyingEnglish());

        TextView word = new TextView(this);
        word.setText(revealed ? card.meaning() : card.spanish());
        word.setTag(!revealed);
        word.setTextSize(31);
        word.setMaxLines(5);
        word.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        word.setLineSpacing(dp(3), 1.06f);
        word.setTextColor(primaryText());
        word.setGravity(Gravity.CENTER);
        word.setPadding(0, dp(11), 0, dp(11));
        face.addView(word, new LinearLayout.LayoutParams(-1, -2));
        picture.setTag(card.spanish());
        return new CardFace(face, fallback, picture, word);
    }

    /** Rebind only an off-screen recycled page. Never reload the centered image. */
    private void bindCardFace(CardFace face, Store.Card card) {
        if (!card.spanish().equals(face.picture().getTag())) {
            face.picture().setTag(card.spanish());
            face.fallback().setText(CardVisualHint.forCard(card));
            CardImageLoader.load(this, face.picture(), face.fallback(), card.spanish(), studyingEnglish());
        }
        face.word().setText(card.spanish());
        face.word().setTag(true);
    }

    /** Secondary actions are intentionally below the card, keeping study in the first viewport. */
    private void showCardTools() {
        Button chat = button("ChatGPT — голос / текст", this::showAssistant);
        ui.style(chat, UiStyle.Tone.PRIMARY);
        Button googleRecord = button("Google — озвучить все рассказы", this::startGoogleStoryRecording);
        ui.style(googleRecord, UiStyle.Tone.OUTLINE);
        LinearLayout addActions = new LinearLayout(this);
        addActions.setOrientation(LinearLayout.HORIZONTAL);
        addActions.setBaselineAligned(false); // Two-line label must not shift the second button vertically.
        Button addWordButton = rowButton("＋ Слово", () -> addCard(0));
        Button addPhraseButton = rowButton("＋ Устойчивое выражение", () -> addCard(1));
        ui.style(addWordButton, UiStyle.Tone.OUTLINE);
        ui.style(addPhraseButton, UiStyle.Tone.OUTLINE);
        addWordButton.setMinHeight(dp(70));
        addPhraseButton.setMinHeight(dp(70));
        addActions.addView(addWordButton, new LinearLayout.LayoutParams(0, dp(70), 1));
        LinearLayout.LayoutParams phraseParams = new LinearLayout.LayoutParams(0, dp(70), 1);
        phraseParams.setMarginStart(dp(6));
        addActions.addView(addPhraseButton, phraseParams);
        content.addView(addActions, spaced(10));

        List<Store.Card> imagesMissing = MissingCardIllustrations.pending(this, store);
        imageRepairStatus = text(imageRepairRunning ? imageRepairMessage
                : imagesMissing.isEmpty()
                    ? "У всех изучаемых карточек есть рисунки"
                    : "Изучаемых карточек без рисунка или с эмодзи-заглушкой: " + imagesMissing.size(), 14);
        imageRepairStatus.setTextColor(secondaryText());
        if (!imageRepairRunning && !imageRepairMessage.isEmpty()) {
            TextView lastRun = text(imageRepairMessage, 13);
            lastRun.setTextColor(secondaryText());
        }
        if (imageRepairRunning || !imagesMissing.isEmpty()) {
            imageRepairButton = button(imageRepairRunning ? "Остановить создание картинок"
                    : "Дорисовать недостающие (" + imagesMissing.size() + ")",
                    this::startImageRepair);
            ui.style(imageRepairButton, UiStyle.Tone.OUTLINE);
        }

        // Lowest control on both the words and expressions screens.
        showStudyBatchControl();
    }

    /** Choose an active study-set size without removing cards from the database. */
    private void showStudyBatchControl() {
        final int selectedKind = kind;
        final int selectedCategory = selectedKind == 0 ? category : 0;
        final String key = prefKey(StudyBatch.preferenceKey(selectedKind, selectedCategory));
        int currentLimit = Math.max(0, prefs.getInt(key, 0));
        String label = selectedKind == 1 ? "устойчивых выражений" : "слов";
        TextView title = text("Взять для изучения — количество " + label + ":", 15);
        title.setTextColor(secondaryText());

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        EditText amount = new EditText(this);
        amount.setTag("study-batch-amount");
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        amount.setSingleLine(true);
        amount.setText(Integer.toString(currentLimit > 0 ? currentLimit : 10));
        amount.setSelectAllOnFocus(true);
        amount.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        amount.setContentDescription("Количество " + label + " для изучения");
        ui.styleInput(amount);
        controls.addView(amount, new LinearLayout.LayoutParams(0, dp(52), 1));

        Button take = rowButton("Взять", () -> {
            int requested;
            try {
                requested = Integer.parseInt(amount.getText().toString().trim());
            } catch (NumberFormatException ignored) {
                amount.setError("Введите число от 1 до 100000");
                return;
            }
            if (requested < 1 || requested > 100000) {
                amount.setError("Введите число от 1 до 100000");
                return;
            }
            prefs.edit().putInt(key, requested).apply();
            reviewIndex = 0;
            cardRevealed = false;
            render();
        });
        take.setTag("study-batch-apply");
        LinearLayout.LayoutParams takeParams = new LinearLayout.LayoutParams(dp(105), dp(52));
        takeParams.setMarginStart(dp(7));
        controls.addView(take, takeParams);

        if (currentLimit > 0) {
            Button all = rowButton("Все", () -> {
                prefs.edit().remove(key).apply();
                reviewIndex = 0;
                cardRevealed = false;
                render();
            });
            all.setTag("study-batch-all");
            LinearLayout.LayoutParams allParams = new LinearLayout.LayoutParams(dp(75), dp(52));
            allParams.setMarginStart(dp(5));
            controls.addView(all, allParams);
        }
        content.addView(controls, spaced(12));
    }


    /** Creates pictures only for the unmastered cards with no existing SVG. */
    private void startImageRepair() {
        if (imageRepairRunning) {
            imageRepairCancel.set(true);
            imageRepairMessage = "Останавливаю создание картинок после текущего запроса…";
            if (imageRepairStatus != null) imageRepairStatus.setText(imageRepairMessage);
            if (imageRepairButton != null) imageRepairButton.setEnabled(false);
            return;
        }
        if (assistantRequestInFlight) {
            Toast.makeText(this, "Дождитесь завершения текущего запроса ChatGPT",
                    Toast.LENGTH_LONG).show();
            return;
        }
        List<Store.Card> missing = MissingCardIllustrations.pending(this, store);
        if (missing.isEmpty()) {
            imageRepairMessage = "";
            render();
            return;
        }
        if (!assistant.isConnected()) {
            new AlertDialog.Builder(this).setTitle("Нужен ChatGPT")
                    .setMessage("Создание рисунков выполняется через подключённый ChatGPT. Для этого нужно подключить аккаунт внутри приложения.")
                    .setPositiveButton("Подключить", (d, w) -> showAssistant())
                    .setNegativeButton("Закрыть", null).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Дорисовать " + missing.size() + " карточек?")
                .setMessage("Создам настоящие SVG-иллюстрации только для неосвоенных слов и выражений без рисунков или с эмодзи-заглушками. Освоенные карточки и готовые полноценные изображения не изменятся. При большом количестве генерация через ChatGPT займёт значительное время.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Создать", (d, w) -> runImageRepair())
                .show();
    }

    private void runImageRepair() {
        if (imageRepairRunning || isFinishing() || isDestroyed()) return;
        Store target = store;
        imageRepairCancel.set(false);
        imageRepairRunning = true;
        imageRepairMessage = "Проверяю, каким карточкам не хватает рисунков…";
        if (imageRepairStatus != null) imageRepairStatus.setText(imageRepairMessage);
        if (imageRepairButton != null) imageRepairButton.setText("Остановить создание картинок");

        assistant.createMissingIllustrations(target, imageRepairCancel::get,
                new ChatGptAssistant.ImageRepairUpdates() {
                    @Override public void progress(int processed, int total, int created,
                                                   int errors, String word) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed() || store != target) return;
                            imageRepairMessage = "Картинки: обработано " + processed + " из " + total
                                    + " • создано " + created
                                    + (errors == 0 ? "" : " • ошибок " + errors)
                                    + (word.isEmpty() ? "" : "\n" + word);
                            if (imageRepairStatus != null)
                                imageRepairStatus.setText(imageRepairMessage);
                        });
                    }
                    @Override public void finished(int processed, int total, int created,
                                                   int skipped, int errors, boolean cancelled,
                                                   String error) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed() || store != target) return;
                            imageRepairRunning = false;
                            imageRepairMessage = (cancelled ? "Создание остановлено." : "Создание завершено.")
                                    + " Нарисовано: " + created
                                    + (skipped > 0 ? ", пропущено: " + skipped : "")
                                    + (errors > 0 ? ", ошибок: " + errors : "")
                                    + (error.isEmpty() ? "" : ". " + error);
                            if ("cards".equals(screen)) render(); // Show newly saved SVGs.
                        });
                    }
                });
    }

    /** Edit only the selected card; keep its ID, mastery state and review history. */
    private void editExistingCard(Store.Card card) {
        if (card == null || isFinishing() || isDestroyed()) return;
        cancelCardSpeech();
        player.pause();
        LinearLayout box = form();
        EditText original = field(box, studyingEnglish()
                ? "Английское слово или выражение" : "Испанское слово или выражение", 500, false);
        EditText translation = field(box, "Перевод на русский", 1000, true);
        original.setText(card.spanish());
        translation.setText(card.meaning());
        original.setSelectAllOnFocus(true);
        AlertDialog editDialog = new AlertDialog.Builder(this)
                .setTitle(card.kind() == 1 ? "Изменить выражение" : "Изменить слово")
                .setView(box)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", null)
                .create();
        editDialog.setOnShowListener(d ->
                editDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String newText = original.getText().toString().trim();
                    String newMeaning = translation.getText().toString().trim();
                    if (newText.isEmpty()) {
                        original.setError("Введите слово или выражение");
                        return;
                    }
                    if (newMeaning.isEmpty()) {
                        translation.setError("Введите перевод");
                        return;
                    }
                    try {
                        if (!store.editCard(card.id(), newText, newMeaning)) {
                            Toast.makeText(this, "Карточка не найдена", Toast.LENGTH_LONG).show();
                            return;
                        }
                        boolean artCopied = true;
                        if (!card.spanish().equals(newText)) {
                            try {
                                CardImageMigration.copyOnEdit(this, card.spanish(), newText, studyingEnglish());
                            } catch (java.io.IOException pictureError) {
                                artCopied = false;
                            }
                        }
                        editDialog.dismiss();
                        cardRevealed = false;
                        render();
                        if (!artCopied)
                            Toast.makeText(this, "Карточка сохранена, но рисунок не удалось скопировать",
                                    Toast.LENGTH_LONG).show();
                    } catch (IllegalArgumentException invalid) {
                        original.setError(invalid.getMessage());
                    } catch (Exception error) {
                        Toast.makeText(this, "Не удалось сохранить изменения", Toast.LENGTH_LONG).show();
                    }
                }));
        editDialog.show();
    }

    private EditText field(LinearLayout box, String hint, int max, boolean multiline) {
        EditText e = new EditText(this); e.setHint(hint); e.setTextSize(18); e.setSingleLine(!multiline); e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)}); if(multiline) e.setMinLines(3); ui.styleInput(e); box.addView(e, spaced(10)); return e;
    }
    private LinearLayout form() { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20),dp(8),dp(20),dp(8)); return box; }
    private void addCard() { addCard(kind); }
    private void addCard(int requestedKind) {
        cancelCardSpeech();
        if (cardDialog != null && cardDialog.isShowing()) return;
        LinearLayout box = form();
        EditText es = field(box, studyingEnglish() ? "По-английски" : "По-испански", 500, false);
        EditText ru = field(box, "Русский смысл", 1000, true);
        boolean restoring = prefs.getBoolean(prefKey("cardDraftOpen"), false);
        draftKind = restoring ? prefs.getInt(prefKey("cardDraftKind"), requestedKind) : requestedKind;
        int defaultCategory = category >= 1 && category <= 3 ? category : 1;
        draftCategory = restoring ? prefs.getInt(prefKey("cardDraftCategory"), defaultCategory) : defaultCategory;
        if (restoring) { es.setText(prefs.getString(prefKey("cardDraftSpanish"), "")); ru.setText(prefs.getString(prefKey("cardDraftMeaning"), "")); }
        draftSpanish = es; draftMeaning = ru;
        draftCategorySpinner = null;
        if (draftKind == 0) {
            TextView label = new TextView(this);
            label.setText("Категория");
            label.setTextSize(16);
            label.setPadding(0, dp(10), 0, 0);
            box.addView(label);
            draftCategorySpinner = new Spinner(this);
            draftCategorySpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                    new String[]{"Глагол", "Существительное", "Прилагательное"}));
            draftCategorySpinner.setSelection(Math.max(0, Math.min(2, draftCategory - 1)));
            box.addView(draftCategorySpinner);
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(draftKind == 0 ? "Новое слово" : "Новое устойчивое выражение").setView(box).setNegativeButton("Отмена", (d, w) -> clearCardDraft()).setPositiveButton("Сохранить", null).create();
        cardDialog = dialog;
        dialog.setOnCancelListener(d -> clearCardDraft());
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            if (es.getText().toString().trim().isEmpty()) {
                es.setError(studyingEnglish() ? "Введите английский текст" : "Введите испанский текст");
                return;
            }
            if (ru.getText().toString().trim().isEmpty()) { ru.setError("Введите русский смысл"); return; }
            draftCategory = draftKind == 0 && draftCategorySpinner != null ? draftCategorySpinner.getSelectedItemPosition() + 1 : 0;
            try {
                store.addCard(es.getText().toString().trim(), ru.getText().toString().trim(), draftKind, draftCategory);
            } catch (IllegalArgumentException invalidPhrase) {
                es.setError(invalidPhrase.getMessage());
                es.requestFocus();
                return;
            }
            kind = draftKind;
            if (kind == 1) category = 0;
            reviewIndex = 0;
            cardRevealed = false;
            clearCardDraft(); dialog.dismiss(); render();
        })); dialog.show();
    }
    private void rememberCardDraft() {
        if (cardDialog != null && cardDialog.isShowing() && draftSpanish != null && draftMeaning != null) {
            if (draftKind == 0 && draftCategorySpinner != null)
                draftCategory = draftCategorySpinner.getSelectedItemPosition() + 1;
            prefs.edit().putBoolean(prefKey("cardDraftOpen"), true).putInt(prefKey("cardDraftKind"), draftKind).putInt(prefKey("cardDraftCategory"), draftCategory)
                    .putString(prefKey("cardDraftSpanish"), draftSpanish.getText().toString())
                    .putString(prefKey("cardDraftMeaning"), draftMeaning.getText().toString()).commit();
        }
    }
    private void clearCardDraft() {
        prefs.edit().remove(prefKey("cardDraftOpen")).remove(prefKey("cardDraftKind")).remove(prefKey("cardDraftCategory")).remove(prefKey("cardDraftSpanish")).remove(prefKey("cardDraftMeaning")).commit();
        draftSpanish = null; draftMeaning = null; draftCategorySpinner = null;
    }
    private void stories() {
        text(studyingEnglish() ? "Библиотека английских рассказов" : "Библиотека рассказов", 28);
        TextView subtitle = text("Слушайте целиком или по одному предложению.", 16);
        subtitle.setTextColor(secondaryText());
        subtitle.setPadding(0, dp(3), 0, dp(12));
        Button chat = button("ChatGPT — голос / текст", this::showAssistant);
        ui.style(chat, UiStyle.Tone.PRIMARY);
        for (Store.Story story : store.stories()) {
            // One 50dp line per story: the full row opens the existing player.
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), 0, dp(2), 0);
            row.setBackground(ui.ripple(ui.surface, 10, ui.outline));
            row.setClickable(true);
            row.setFocusable(true);
            row.setContentDescription("Открыть рассказ: " + story.title());
            row.setOnClickListener(v -> openStory(story));

            TextView name = new TextView(this);
            name.setText(story.title());
            name.setTextSize(16);
            name.setTextColor(primaryText());
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(name, new LinearLayout.LayoutParams(0, -1, 1));

            // A separate delete action: tapping × never opens the story player.
            TextView delete = new TextView(this);
            delete.setText("×");
            delete.setTextColor(ui.danger);
            delete.setTextSize(24);
            delete.setGravity(Gravity.CENTER);
            delete.setBackground(ui.ripple(ui.surface, 10, Color.TRANSPARENT));
            delete.setContentDescription("Удалить рассказ: " + story.title());
            delete.setFocusable(true);
            delete.setOnClickListener(v -> confirmDeleteStory(story));
            row.addView(delete, new LinearLayout.LayoutParams(dp(44), dp(48)));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
            params.bottomMargin = dp(5);
            content.addView(row, params);
        }
    }
    private void confirmDeleteStory(Store.Story story) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle("Удалить рассказ?")
                .setMessage("«" + story.title() + "» будет удалён без возможности восстановления.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (dialog, which) -> {
                    if (!store.deleteStory(story.id())) {
                        Toast.makeText(this, "Рассказ уже удалён", Toast.LENGTH_SHORT).show();
                    } else {
                        new HumanStoryAudio(this, activeProfile, studyLanguage, story.id(), story.body()).deleteAll();
                        new GoogleStoryAudio(this, activeProfile, studyLanguage, story.id(), story.body()).deleteAll();
                    }
                    render();
                })
                .show();
    }

    private void openStory(Store.Story story) {
        player.pause();
        remember();
        storyId = story.id();
        bibleRedChunks = BibleStoryHighlight.appliesTo(story.title())
                ? BibleStoryHighlight.mark(story.body()) : new boolean[0];
        screen = "player";
        storyOriginalSentences = Sentences.split(story.body());
        storyTranslations = Sentences.split(story.translation());
        // Never try to read a missing or non-aligned translation.
        storyReadRussian = prefs.getBoolean(prefKey("storyReadRussian"), false)
                && storyTranslations.size() == storyOriginalSentences.size()
                && !storyTranslations.isEmpty();
        player.setLearningLanguage(storyReadRussian ? "ru" : studyLanguage);
        HumanStoryAudio recorded = new HumanStoryAudio(this, activeProfile, studyLanguage,
                story.id(), story.body());
        GoogleStoryAudio google = new GoogleStoryAudio(this, activeProfile, studyLanguage,
                story.id(), story.body());
        String source = prefs.getString(prefKey("narrationSource"),
                prefs.getBoolean(prefKey("humanVoice"), false) ? "human" : "tts");
        player.attachHumanStory(recorded, !storyReadRussian && "human".equals(source));
        player.attachGoogleStory(google, !storyReadRussian && "google".equals(source));
        player.load(storyReadRussian ? story.translation() : story.body(), story.position());
        render();
    }

    /** Switch only the spoken story text, not the language of study cards. */
    boolean selectStoryPlaybackLanguage(boolean russian) {
        Store.Story selected = null;
        for (Store.Story story : store.stories()) if (story.id() == storyId) {
            selected = story;
            break;
        }
        if (selected == null || !"player".equals(screen)) return false;
        List<String> original = Sentences.split(selected.body());
        List<String> translated = Sentences.split(selected.translation());
        if (russian && (translated.isEmpty() || original.size() != translated.size())) {
            Toast.makeText(this, "Русская озвучка недоступна: перевод неполный или не совпадают предложения",
                    Toast.LENGTH_LONG).show();
            return false;
        }
        if (storyReadRussian == russian) return true;
        int position = player.state.index;
        player.pause();
        storyOriginalSentences = original;
        storyTranslations = translated;
        storyReadRussian = russian;
        prefs.edit().putBoolean(prefKey("storyReadRussian"), russian).apply();
        player.setLearningLanguage(russian ? "ru" : studyLanguage);
        if (russian) {
            // Stored Spanish/English recordings must NEVER play over Russian text.
            player.useHumanVoice(false);
            player.useGoogleVoice(false);
        } else {
            String source = prefs.getString(prefKey("narrationSource"), "tts");
            player.useHumanVoice("human".equals(source));
            player.useGoogleVoice("google".equals(source));
        }
        player.load(russian ? selected.translation() : selected.body(), position);
        render();
        return true;
    }

    private void chooseStoryPlaybackLanguage() {
        new AlertDialog.Builder(this)
                .setTitle("Язык озвучки рассказа")
                .setSingleChoiceItems(new String[]{studyingEnglish() ? "Английский" : "Испанский",
                        "Русский"}, storyReadRussian ? 1 : 0, (dialog, which) -> {
                    if (selectStoryPlaybackLanguage(which == 1)) dialog.dismiss();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void storyPlayer() {
        Store.Story selected = null; for(Store.Story story : store.stories()) if(story.id() == storyId) selected = story;
        if(selected == null) { text("Рассказ не найден", 22); return; }
        // Single-handed navigation: immediately below the Cards/Stories tabs,
        // left aligned and before the large reading panel.
        repeat = rowButton("← Назад", player::backOneSentence);
        repeat.setTag("story-back-sentence");
        repeat.setContentDescription("Назад по предложениям: первое нажатие повторяет, каждое следующее — на одно предложение назад");
        ui.style(repeat, UiStyle.Tone.OUTLINE);
        LinearLayout.LayoutParams rewindParams = new LinearLayout.LayoutParams(dp(126), dp(50));
        rewindParams.bottomMargin = dp(8);
        content.addView(repeat, rewindParams);

        text(selected.title(), 25);
        storyOriginalSentences = Sentences.split(selected.body());
        storyTranslations = Sentences.split(selected.translation());

        // Primary playback control is directly ABOVE the story reading panel.
        toggle = button("Слушать", () -> {
            if (player.state.phase == PlaybackState.Phase.SPEAKING ||
                    (!player.sentenceMode && player.state.phase == PlaybackState.Phase.WAITING))
                player.pause();
            else player.play();
        });
        ui.style(toggle, UiStyle.Tone.PRIMARY);
        toggle.setTextSize(20);
        toggle.setMinHeight(dp(70));
        toggle.setMinimumHeight(dp(70));

        LinearLayout readingPanel = new LinearLayout(this);
        readingPanel.setOrientation(LinearLayout.VERTICAL);
        readingPanel.setPadding(dp(18), dp(16), dp(18), dp(16));
        readingPanel.setBackground(ui.cardSurface());
        readingPanel.setElevation(dp(4));
        content.addView(readingPanel, spaced(6));

        sentence = textInto(readingPanel, currentOriginalSentence(), 26);
        sentence.setTextColor(storyReadingTextColor(player.state.index));
        sentence.setLineSpacing(dp(5), 1.07f);
        sentence.setPadding(0, dp(3), 0, dp(13));
        attachStoryWordGesture(sentence, this::currentTranslation);

        View divider = new View(this);
        divider.setBackgroundColor(borderColor());
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.bottomMargin = dp(10);
        readingPanel.addView(divider, dividerParams);

        sentenceRu = textInto(readingPanel, currentTranslation(), 19);
        sentenceRu.setTextIsSelectable(true);
        sentenceRu.setTextColor(secondaryText());
        sentenceRu.setLineSpacing(dp(4), 1.05f);

        playbackStatus = text("", 15);
        playbackStatus.setTextColor(ui.accent);
        playbackStatus.setBackground(ui.rounded(ui.soft, 13, Color.TRANSPARENT));
        playbackStatus.setPadding(dp(14), dp(10), dp(14), dp(10));
        playbackStatus.setLayoutParams(spaced(12));

        humanVoiceButton = button("Источник: " + narrationLabel(), this::chooseNarrationSource);
        ui.style(humanVoiceButton, UiStyle.Tone.OUTLINE);
        humanVoiceButton.setEnabled(!storyReadRussian);
        Button googleAudio = button("Google — озвучить все рассказы", this::startGoogleStoryRecording);
        ui.style(googleAudio, UiStyle.Tone.OUTLINE);
        googleAudio.setEnabled(!storyReadRussian);
        Button recordHuman = button("Записать живой голос", this::openHumanRecorder);
        ui.style(recordHuman, UiStyle.Tone.OUTLINE);
        recordHuman.setEnabled(!storyReadRussian);

        Button readAll = button("Читать весь рассказ заново", player::playAllFromStart);
        ui.style(readAll, UiStyle.Tone.OUTLINE);
        Button share = button("Отправить аудио", this::shareCurrentStory);
        ui.style(share, UiStyle.Tone.OUTLINE);
        Button back = button("← Библиотека", () -> navigate("stories"));
        ui.style(back, UiStyle.Tone.OUTLINE);

        // Less frequently used controls sit below the playback actions.
        Button storySpeechLanguage = button("Язык озвучки: "
                + (storyReadRussian ? "Русский" : studyingEnglish() ? "Английский" : "Испанский")
                + " ▾", this::chooseStoryPlaybackLanguage);
        storySpeechLanguage.setTag("story-speech-language");
        ui.style(storySpeechLanguage, UiStyle.Tone.OUTLINE);
        storySpeechLanguage.setTextSize(13);
        storySpeechLanguage.setMinHeight(dp(48));

        TextView source = text(selected.source(), 11);
        source.setTag("story-source-footer");
        source.setTextColor(secondaryText());
        source.setPadding(0, dp(3), 0, dp(8));
    }
    private String narrationLabel() {
        if (storyReadRussian) return "Системный голос (русский)";
        return player.googleSelected() ? "Google (сохранённая запись)"
                : player.humanSelected() ? "Живой голос" : "Системный голос";
    }

    private void chooseNarrationSource() {
        if (storyReadRussian) {
            Toast.makeText(this, "Русский текст озвучивает системный русский голос", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Источник озвучки")
                .setItems(new String[]{"Системный голос", "Google (сохранённая запись)",
                        "Живой голос"}, (dialog, option) -> {
                    if (option == 1 && !player.googleAvailable()) {
                        Toast.makeText(this, "Сначала сохраните Google-озвучку этого рассказа",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (option == 2 && !player.humanAvailable()) {
                        Toast.makeText(this, "Сначала запишите все предложения живым голосом",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    String source = option == 1 ? "google" : option == 2 ? "human" : "tts";
                    if (option == 1) player.useGoogleVoice(true);
                    else if (option == 2) player.useHumanVoice(true);
                    else {
                        player.useHumanVoice(false);
                        player.useGoogleVoice(false);
                    }
                    prefs.edit().putString(prefKey("narrationSource"), source)
                            .putBoolean(prefKey("humanVoice"), option == 2).apply();
                    updatePlayer();
                }).setNegativeButton("Отмена", null).show();
    }

    /**
     * Google Android TextToSpeech, not the private Google Translate web voice.
     * Generate all phone-local stories per sentence without ChatGPT calls.
     */
    private void startGoogleStoryRecording() {
        if (googleBatch != null) {
            Toast.makeText(this, "Google уже озвучивает рассказы", Toast.LENGTH_SHORT).show();
            return;
        }
        player.pause();
        final TextView status = new TextView(this);
        status.setText("Проверка голосового движка Google…");
        status.setTextColor(primaryText());
        status.setTextSize(16);
        status.setPadding(dp(22), dp(20), dp(22), dp(20));
        googleBatchDialog = new AlertDialog.Builder(this)
                .setTitle("Google — запись всех рассказов")
                .setMessage(null)
                .setView(status)
                .setNegativeButton("Остановить", (d, w) -> {
                    if (googleBatch != null) googleBatch.cancel();
                    googleBatch = null;
                    googleBatchDialog = null;
                })
                .setCancelable(false)
                .create();
        try {
            googleBatch = new GoogleStoryBatch(this, new GoogleStoryBatch.Listener() {
                @Override public void progress(int completed, int total, String title) {
                    if (googleBatchDialog != null && googleBatchDialog.isShowing()) {
                        status.setText("Предложения: " + completed + " / " + total +
                                (title.isEmpty() ? "" : "\\n" + title));
                    }
                }
                @Override public void finished(int total) {
                    googleBatch = null;
                    if (googleBatchDialog != null) googleBatchDialog.dismiss();
                    googleBatchDialog = null;
                    if (isFinishing() || isDestroyed()) return;
                    if ("player".equals(screen)) {
                        for (Store.Story story : store.stories()) {
                            if (story.id() != storyId) continue;
                            GoogleStoryAudio archived = new GoogleStoryAudio(MainActivity.this,
                                    activeProfile, studyLanguage, story.id(), story.body());
                            if (!storyReadRussian && archived.complete()) {
                                player.attachGoogleStory(archived, true);
                                prefs.edit().putString(prefKey("narrationSource"), "google")
                                        .putBoolean(prefKey("humanVoice"), false).apply();
                            }
                            break;
                        }
                    }
                    render();
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Google: озвучивание завершено")
                            .setMessage("Сохранено или ранее уже было записано " + total +
                                    " предложений во всех профилях на этом телефоне.")
                            .setPositiveButton("Закрыть", null).show();
                }
                @Override public void failed(String reason) {
                    googleBatch = null;
                    if (googleBatchDialog != null) googleBatchDialog.dismiss();
                    googleBatchDialog = null;
                    if (isFinishing() || isDestroyed()) return;
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Google: озвучивание не завершено")
                            .setMessage(reason + "\\n\\nУже готовые предложения сохранены. " +
                                    "Повторный запуск продолжит с незаписанных.")
                            .setPositiveButton("Закрыть", null).show();
                }
            });
            googleBatchDialog.show();
            googleBatch.start();
        } catch (RuntimeException error) {
            if (googleBatch != null) googleBatch.cancel();
            googleBatch = null;
            if (googleBatchDialog != null) googleBatchDialog.dismiss();
            googleBatchDialog = null;
            new AlertDialog.Builder(this)
                    .setTitle("Google: ошибка подготовки")
                    .setMessage("Не удалось получить список локальных рассказов.")
                    .setPositiveButton("Закрыть", null).show();
        }
    }

    /** The recorder works locally and does not use ChatGPT, tokens or network requests. */
    private void openHumanRecorder() {
        if (humanRecordDialog != null && humanRecordDialog.isShowing()) return;
        Store.Story story = null;
        for (Store.Story item : store.stories()) if (item.id() == storyId) { story = item; break; }
        if (story == null) return;
        player.pause();
        stopAssistantVoice();
        humanRecording = new HumanStoryAudio(this, activeProfile, studyLanguage, story.id(), story.body());
        if (humanRecording.count() == 0) {
            humanRecording = null;
            Toast.makeText(this, "В рассказе нет предложений", Toast.LENGTH_LONG).show();
            return;
        }
        humanRecordIndex = Math.min(player.state.index, humanRecording.count() - 1);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(12), dp(18), dp(12));
        humanRecordText = textInto(panel, "", 22);
        humanRecordStatus = textInto(panel, "", 15);
        humanRecordStatus.setTextColor(secondaryText());
        humanRecordToggle = rowButton("Записать предложение", this::toggleHumanRecording);
        ui.style(humanRecordToggle, UiStyle.Tone.PRIMARY);
        panel.addView(humanRecordToggle, spaced(10));
        humanRecordPrevious = rowButton("← Предыдущее предложение", () -> {
            if (humanRecording.isRecording()) return;
            humanRecordIndex = Math.max(0, humanRecordIndex - 1);
            refreshHumanRecorder();
        });
        panel.addView(humanRecordPrevious, spaced(6));
        humanRecordNext = rowButton("Следующее предложение →", () -> {
            if (humanRecording.isRecording()) return;
            humanRecordIndex = Math.min(humanRecording.count() - 1, humanRecordIndex + 1);
            refreshHumanRecorder();
        });
        panel.addView(humanRecordNext, spaced(6));
        humanRecordDelete = rowButton("Удалить запись этого предложения", () -> {
            try {
                humanRecording.deleteSentence(humanRecordIndex);
                if (player.humanSelected()) {
                    player.useHumanVoice(false);
                    prefs.edit().putString(prefKey("narrationSource"), "tts")
                            .putBoolean(prefKey("humanVoice"), false).apply();
                }
                refreshHumanRecorder();
            } catch (Exception error) {
                Toast.makeText(this, "Не удалось удалить запись", Toast.LENGTH_LONG).show();
            }
        });
        ui.style(humanRecordDelete, UiStyle.Tone.OUTLINE);
        panel.addView(humanRecordDelete, spaced(8));
        HumanStoryAudio session = humanRecording;
        humanRecordDialog = new AlertDialog.Builder(this)
                .setTitle("Запись живого голоса")
                .setView(panel)
                .setPositiveButton("Закрыть", null)
                .create();
        humanRecordDialog.setOnDismissListener(dialog -> {
            if (session.isRecording()) {
                try { session.stop(false); } catch (Exception ignored) {}
            }
            if (humanRecording == session) {
                if (!storyReadRussian && session.complete()) {
                    player.useHumanVoice(true);
                    prefs.edit().putString(prefKey("narrationSource"), "human")
                            .putBoolean(prefKey("humanVoice"), true).apply();
                } else if (player.humanSelected()) {
                    player.useHumanVoice(false);
                }
                humanRecording = null;
                humanRecordDialog = null;
                humanRecordText = null;
                humanRecordStatus = null;
                humanRecordToggle = null;
                humanRecordPrevious = null;
                humanRecordNext = null;
                humanRecordDelete = null;
                if (!isFinishing() && !isDestroyed() && "player".equals(screen)) render();
            }
        });
        humanRecordDialog.show();
        refreshHumanRecorder();
    }

    private void refreshHumanRecorder() {
        if (humanRecording == null || humanRecordText == null) return;
        boolean recording = humanRecording.isRecording();
        humanRecordText.setText(humanRecording.sentence(humanRecordIndex));
        humanRecordStatus.setText("Предложение " + (humanRecordIndex + 1) + " / "
                + humanRecording.count() + " • Записано " + humanRecording.recordedCount()
                + " / " + humanRecording.count()
                + (recording ? "\nИдёт запись с микрофона…"
                : humanRecording.recorded(humanRecordIndex) ? "\nЭто предложение уже записано"
                : "\nНажмите кнопку записи и произнесите предложение"));
        humanRecordToggle.setText(recording ? "Стоп — сохранить запись"
                : humanRecording.recorded(humanRecordIndex) ? "Перезаписать предложение"
                : "Записать предложение");
        humanRecordPrevious.setEnabled(!recording && humanRecordIndex > 0);
        humanRecordNext.setEnabled(!recording && humanRecordIndex + 1 < humanRecording.count());
        humanRecordDelete.setEnabled(!recording && humanRecording.recorded(humanRecordIndex));
    }

    private void toggleHumanRecording() {
        if (humanRecording == null) return;
        if (humanRecording.isRecording()) {
            try {
                humanRecording.stop(true);
                if (humanRecordIndex + 1 < humanRecording.count()) humanRecordIndex++;
                refreshHumanRecorder();
            } catch (Exception error) {
                Toast.makeText(this, "Запись не сохранена: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
                refreshHumanRecorder();
            }
        } else {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, STORY_RECORD_PERMISSION);
                return;
            }
            beginHumanRecording();
        }
    }

    private void beginHumanRecording() {
        if (humanRecording == null || humanRecordDialog == null
                || !humanRecordDialog.isShowing()) return;
        player.pause();
        stopAssistantVoice();
        try {
            humanRecording.start(humanRecordIndex);
            refreshHumanRecorder();
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось начать запись: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private StoryAudioExporter audioExporter;
    private AlertDialog audioExportDialog;

    private void shareCurrentStory() {
        if (audioExporter != null) return;
        Store.Story selected = null;
        for (Store.Story story : store.stories()) {
            if (story.id() == storyId) { selected = story; break; }
        }
        String exportBody = selected == null ? "" :
                storyReadRussian ? selected.translation() : selected.body();
        if (exportBody.trim().isEmpty()) {
            Toast.makeText(this, "Нет текста для озвучивания", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!player.ready() || player.selectedVoiceNameForExport().isEmpty()) {
            Toast.makeText(this, "Голос Android ещё не готов. Проверьте настройки озвучивания.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        player.pause();
        final String title = selected.title();
        StoryAudioExporter exporter = new StoryAudioExporter(this, title, exportBody,
                storyReadRussian ? "ru" : studyLanguage, player.selectedVoiceNameForExport(), player.speed,
                new StoryAudioExporter.Listener() {
                    @Override public void progress(int done, int total) {
                        if (audioExporter == null || audioExportDialog == null
                                || !audioExportDialog.isShowing()) return;
                        audioExportDialog.setMessage("Создаю аудио: " + done + " из " + total);
                    }
                    @Override public void finished(Uri uri) {
                        if (audioExporter == null) return;
                        audioExporter = null;
                        if (audioExportDialog != null) audioExportDialog.dismiss();
                        audioExportDialog = null;
                        if (isFinishing() || isDestroyed()) return;
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType("audio/wav");
                        send.putExtra(Intent.EXTRA_STREAM, uri);
                        send.putExtra(Intent.EXTRA_SUBJECT, title);
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        send.setClipData(android.content.ClipData.newUri(
                                getContentResolver(), "Аудиорассказ", uri));
                        try {
                            startActivity(Intent.createChooser(send, "Отправить аудио"));
                        } catch (ActivityNotFoundException noMessenger) {
                            Toast.makeText(MainActivity.this,
                                    "Нет приложения для отправки аудио", Toast.LENGTH_LONG).show();
                        }
                    }
                    @Override public void failed(String problem) {
                        if (audioExporter == null) return;
                        audioExporter = null;
                        if (audioExportDialog != null) audioExportDialog.dismiss();
                        audioExportDialog = null;
                        if (!isFinishing() && !isDestroyed())
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Не удалось создать аудио")
                                    .setMessage(problem).setPositiveButton("Закрыть", null).show();
                    }
                });
        audioExporter = exporter;
        audioExportDialog = new AlertDialog.Builder(this)
                .setTitle("Отправить аудиорассказ")
                .setMessage("Подготавливаю голос Android…")
                .setNegativeButton("Отмена", (dialog, which) -> {
                    if (audioExporter == exporter) {
                        audioExporter.cancel();
                        audioExporter = null;
                        audioExportDialog = null;
                    }
                })
                .setCancelable(false).create();
        audioExportDialog.show();
        exporter.start();
    }
    private interface TranslationText { String get(); }

    private void attachStoryWordGesture(TextView label, TranslationText translation) {
        label.setTextIsSelectable(false);
        GestureDetector gesture = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public void onLongPress(MotionEvent event) {
                captureStoryWord(label, translation.get(), event.getX(), event.getY());
            }
        });
        label.setOnTouchListener((view, event) -> {
            gesture.onTouchEvent(event);
            return true; // no Android selection/copy handles; parent ScrollView still handles scroll
        });
    }

    private void captureStoryWord(TextView label, String russian, float x, float y) {
        Layout layout = label.getLayout();
        if (layout == null || label.getText() == null) return;
        String source = label.getText().toString();
        float insideY = y - label.getTotalPaddingTop() + label.getScrollY();
        if (insideY < 0 || insideY >= layout.getHeight()) return;
        int line = layout.getLineForVertical((int) insideY);
        float insideX = x - label.getTotalPaddingLeft() + label.getScrollX();
        if (insideX < layout.getLineLeft(line) || insideX > layout.getLineRight(line)) return;
        int position = layout.getOffsetForHorizontal(line, insideX);
        StoryWordPicker.Match match = StoryWordPicker.at(source, position);
        if (match == null) return;
        if (storyWordRequestInFlight) {
            Toast.makeText(this, "Дождитесь проверки предыдущего слова", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Store targetStore = store;
            Store.StoryWordSave known = StoryVocabulary.existing(targetStore, match.word());
            if (known != null) {
                Toast.makeText(this, known == Store.StoryWordSave.RESTORED
                        ? "Снова в изучении: " + match.word()
                        : "Уже в карточках: " + match.word(), Toast.LENGTH_SHORT).show();
                return;
            }
            if (!assistant.isConnected()) {
                Toast.makeText(this, "Для точного перевода подключите ChatGPT в разделе «Рассказы»",
                        Toast.LENGTH_LONG).show();
                return;
            }
            label.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            SpannableString highlighted = new SpannableString(source);
            highlighted.setSpan(new BackgroundColorSpan(darkTheme ? 0xFF526A66 : 0xFFFFE39A),
                    match.start(), match.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            label.setText(highlighted);
            cardSpeechHandler.postDelayed(() -> {
                if (label.getText() == highlighted) label.setText(source);
            }, 600);

            storyWordRequestInFlight = true;
            AtomicBoolean cancelled = new AtomicBoolean(false);
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Перевод: " + match.word())
                    .setMessage("Проверяю значение и грамматику…")
                    .setNegativeButton("Отмена", (d, w) -> cancelled.set(true))
                    .setCancelable(false).create();
            dialog.show();
            assistant.translateStoryWord(targetStore, match.word(), source, russian,
                    cancelled::get, new ChatGptAssistant.StoryWordUpdates() {
                @Override public void progress(String stage) {
                    runOnUiThread(() -> {
                        if (!cancelled.get() && dialog.isShowing() && !isFinishing())
                            dialog.setMessage(stage);
                    });
                }
                @Override public void finished(boolean ok, String message) {
                    runOnUiThread(() -> {
                        storyWordRequestInFlight = false;
                        if (dialog.isShowing()) dialog.dismiss();
                        if (!cancelled.get() && !isFinishing())
                            Toast.makeText(MainActivity.this, message,
                                    Toast.LENGTH_LONG).show();
                    });
                }
            });
        } catch (Exception e) {
            storyWordRequestInFlight = false;
            Toast.makeText(this, "Не удалось проверить перевод слова", Toast.LENGTH_LONG).show();
        }
    }

    private void showFullStory() {
        Store.Story selected = null;
        for (Store.Story story : store.stories()) if (story.id() == storyId) selected = story;
        if (selected == null) return;
        ScrollView scroll = new ScrollView(this);
        LinearLayout lines = new LinearLayout(this);
        lines.setPadding(dp(18), dp(8), dp(18), dp(16));
        lines.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(lines);
        TextView hint = new TextView(this);
        hint.setText(studyingEnglish() ? "Удерживайте английское слово для добавления в карточки." : "Удерживайте испанское слово для добавления в карточки.");
        hint.setTextSize(14);
        hint.setTextColor(secondaryText());
        lines.addView(hint);
        List<String> spanish = Sentences.split(selected.body());
        List<String> russian = Sentences.split(selected.translation());
        boolean[] redChunks = BibleStoryHighlight.appliesTo(selected.title())
                ? BibleStoryHighlight.mark(selected.body()) : new boolean[0];
        for (int index = 0; index < spanish.size(); index++) {
            String es = spanish.get(index);
            String ru = index < russian.size() ? russian.get(index) : "";
            LinearLayout paragraph = new LinearLayout(this);
            paragraph.setOrientation(LinearLayout.VERTICAL);
            paragraph.setPadding(dp(14), dp(9), dp(14), dp(11));
            paragraph.setBackground(ui.rounded(ui.surface, 16, ui.outline));
            lines.addView(paragraph, spaced(10));
            TextView original = new TextView(this);
            original.setText(es);
            original.setTextSize(22);
            original.setTextColor(index < redChunks.length && redChunks[index]
                    ? bibleTextRed() : primaryText());
            original.setLineSpacing(dp(3), 1.06f);
            original.setPadding(0, dp(6), 0, dp(5));
            attachStoryWordGesture(original, () -> ru);
            paragraph.addView(original, new LinearLayout.LayoutParams(-1, -2));
            TextView translated = new TextView(this);
            translated.setText(ru);
            translated.setTextSize(17);
            translated.setTextColor(secondaryText());
            translated.setLineSpacing(dp(2), 1.04f);
            translated.setPadding(0, dp(4), 0, dp(5));
            paragraph.addView(translated, new LinearLayout.LayoutParams(-1, -2));
        }
        new AlertDialog.Builder(this).setTitle("Текст рассказа")
                .setView(scroll).setPositiveButton("Закрыть", null).show();
    }

    private String findBody() { for(Store.Story s : store.stories()) if(s.id() == storyId) return s.body(); return ""; }
    private String currentOriginalSentence() {
        if (storyOriginalSentences.isEmpty()) return "";
        int i = Math.max(0, Math.min(player.state.index, storyOriginalSentences.size() - 1));
        return storyOriginalSentences.get(i);
    }
    private String currentTranslation() {
        if (storyTranslations.isEmpty()) return "";
        int i = Math.max(0, Math.min(player.state.index, storyTranslations.size() - 1));
        return storyTranslations.get(i);
    }
    private String fullStoryText() {
        for (Store.Story story : store.stories()) {
            if (story.id() != storyId) continue;
            List<String> spanish = Sentences.split(story.body());
            List<String> russian = Sentences.split(story.translation());
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < spanish.size(); i++) {
                if (out.length() > 0) out.append("\n\n");
                out.append(spanish.get(i));
                if (i < russian.size() && !russian.get(i).trim().isEmpty()) out.append("\n").append(russian.get(i));
            }
            return out.toString();
        }
        return "";
    }
    private int bibleTextRed() {
        // Readable red on both light and dark story backgrounds.
        return darkTheme ? 0xFFFF7777 : 0xFFB71C1C;
    }

    private int storyReadingTextColor(int index) {
        return index >= 0 && index < bibleRedChunks.length && bibleRedChunks[index]
                ? bibleTextRed() : primaryText();
    }

    private void updatePlayer() {
        if (player == null || rendering) return;
        if (cardVoiceStatus != null) cardVoiceStatus.setText(player.voiceDescription);
        if (storyId >= 0 && player.state.count > 0) store.position(storyId, player.state.index);
        if (sentence == null) return;
        sentence.setText(currentOriginalSentence());
        sentence.setTextColor(storyReadingTextColor(player.state.index));
        if (sentenceRu != null) sentenceRu.setText(currentTranslation());
        boolean active = player.state.phase == PlaybackState.Phase.SPEAKING || (!player.sentenceMode && player.state.phase == PlaybackState.Phase.WAITING);
        toggle.setText(active ? "Ⅱ Пауза" : player.state.phase == PlaybackState.Phase.FINISHED ? "Слушать сначала" : "▶ Продолжить");
        toggle.setEnabled(player.canPlay()); repeat.setEnabled(player.canPlay());
        if (humanVoiceButton != null)
            humanVoiceButton.setText("Источник: " + narrationLabel());
        String status = switch(player.state.phase) { case SPEAKING -> "Звучит"; case WAITING -> player.sentenceMode ? "Нажмите «Продолжить»" : "Пауза между предложениями"; case FINISHED -> "Рассказ закончен"; case PAUSED -> "Остановлено"; };
        playbackStatus.setText((player.state.index + 1) + " / " + player.state.count + " • " + status
                + (player.error.isEmpty() ? "" : "\n" + player.error)
                + (!player.canPlay() ? "\n" + player.voiceDescription : ""));
    }
    private void syncContent() {
        if (personalStorySync == null || syncingContent) return;
        syncingContent = true;
        Store syncedStore = store;
        personalStorySync.sync(this, syncedStore, studyLanguage, (added, rejected, error) ->
                runOnUiThread(() -> {
                    syncingContent = false;
                    if (isFinishing() || isDestroyed()) return;
                    if (store != syncedStore) { syncContent(); return; }
                    if (added > 0) {
                        Toast.makeText(this, "Добавлено из GitHub: " + added, Toast.LENGTH_SHORT).show();
                        if ((cardDialog == null || !cardDialog.isShowing()) && !"player".equals(screen)) render();
                    }
                }));
    }

    @Override public void onBackPressed() { if (screen.equals("player")) navigate("stories"); else super.onBackPressed(); }
    @Override protected void onResume() {
        super.onResume();
        if (prefs.getBoolean(prefKey("cardDraftOpen"), false) && (cardDialog == null || !cardDialog.isShowing())) addCard();
        syncContent();
        PrivateVocabularySync.schedule(this);
    }
    @Override protected void onPause() {
        if (humanRecording != null && humanRecording.isRecording()) {
            try { humanRecording.stop(false); } catch (Exception ignored) {}
            refreshHumanRecorder();
        }
        stopAssistantVoice();
        cancelCardSpeech();
        player.pause();
        remember();
        rememberCardDraft();
        super.onPause();
    }
    @Override protected void onDestroy() {
        imageRepairCancel.set(true);
        cancelCardSpeech();
        if (contentSync != null) contentSync.close();
        if (personalStorySync != null) personalStorySync.close();
        if (audioExporter != null) { audioExporter.cancel(); audioExporter = null; }
        if (googleBatch != null) { googleBatch.cancel(); googleBatch = null; }
        if (humanRecording != null && humanRecording.isRecording()) {
            try { humanRecording.stop(false); } catch (Exception ignored) {}
        }
        if (assistant != null) assistant.close();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (assistantVoiceOutput != null) assistantVoiceOutput.close();
        player.close();
        for (Store saved : profileStores.values()) saved.close();
        profileStores.clear();
        super.onDestroy();
    }
}
