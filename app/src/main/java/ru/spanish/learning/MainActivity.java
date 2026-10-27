package ru.spanish.learning;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import androidx.browser.customtabs.CustomTabsIntent;
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
    private boolean syncingContent;
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
        studyLanguage = "en".equals(prefs.getString("studyLanguage", "es")) ? "en" : "es";
        spanishStore = new Store(this);
        if ("en".equals(studyLanguage)) englishStore = Store.english(this);
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
    private String prefKey(String key) { return studyingEnglish() ? "en_" + key : key; }

    private void selectStudyLanguage(String next) {
        if (!"en".equals(next) && !"es".equals(next)) return;
        if (next.equals(studyLanguage)) return;
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
        prefs.edit().putString("studyLanguage", studyLanguage).commit();
        if (studyingEnglish() && englishStore == null) englishStore = Store.english(this);
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
        player.pause(); remember(); storyId = -1; screen = next; render();
    }
    private void render() {
        cancelCardSpeech();
        rendering = true; sentence = null; sentenceRu = null; playbackStatus = null;
        cardVoiceStatus = null; imageRepairStatus = null; imageRepairButton = null;
        toggle = null; repeat = null;
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
                        .setItems(new String[]{"0.6×", "0.75×", "0.85×", "1.0×", "1.2×"}, (d,w) -> {
                            player.pause();
                            player.speed = new float[]{.6f,.75f,.85f,1f,1.2f}[w];
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

        box.addView(rowButton("Личный словарь и рассказы — GitHub", this::showVocabularySyncSettings), spaced(10));
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

    private void showVocabularySyncSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(12), dp(22), dp(6));
        TextView explanation = new TextView(this);
        explanation.setText("Нужен отдельный приватный репозиторий GitHub и fine-grained token " +
                "с правом Contents: Read and write только для него. " +
                "Слова и устойчивые выражения будут выгружаться автоматически при изменениях и запуске приложения. " +
                "Личные рассказы загружаются из этого же приватного репозитория при открытии: " +
                "content/stories-es.json и content/stories-en.json. " +
                "У каждого человека должен быть СВОЙ приватный репозиторий. " +
                "Не вставляйте токен в чат.\n\n" +
                "Словарь: " + PrivateVocabularySync.status(this) + "\n" +
                "Рассказы: " + PersonalStorySync.status(this));
        explanation.setTextColor(primaryText());
        explanation.setTextSize(14);
        box.addView(explanation, spaced(12));

        EditText repo = new EditText(this);
        repo.setSingleLine(true);
        repo.setHint("owner/private-repository");
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
        new AlertDialog.Builder(this)
                .setTitle("Личный словарь — приватный GitHub")
                .setView(scroll)
                .setNegativeButton("Отмена", null)
                .setNeutralButton("Отключить", (dialog, which) -> {
                    PrivateVocabularySync.disable(this);
                    Toast.makeText(this, "Синхронизация словаря отключена", Toast.LENGTH_LONG).show();
                })
                .setPositiveButton("Подключить", (dialog, which) -> {
                    try {
                        PrivateVocabularySync.configure(this,
                                repo.getText().toString(), token.getText().toString());
                        syncContent();
                        Toast.makeText(this, "Сохранено. Синхронизация выполняется в фоне.",
                                Toast.LENGTH_LONG).show();
                    } catch (Exception error) {
                        new AlertDialog.Builder(this)
                                .setTitle("Не удалось сохранить")
                                .setMessage("Проверьте название приватного репозитория и токен. " +
                                        "Существующие слова не изменены.")
                                .setPositiveButton("Закрыть", null).show();
                    }
                })
                .show();
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
                .setMessage("Будут удалены слова, выражения, рассказы и прогресс "
                        + (studyingEnglish() ? "АНГЛИЙСКОГО" : "ИСПАНСКОГО")
                        + " языка и его история ChatGPT на этом телефоне. Другая база и её история останутся нетронутыми. Отменить удаление нельзя.")
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
    private void startUpdateNow() {
        cancelCardSpeech();
        player.pause();
        remember();
        Intent updateIntent = new Intent("ru.spanish.learning.updater.UPDATE")
                .setPackage("ru.spanish.learning.updater");
        if (BuildConfig.DEBUG) {
            String testPath = getIntent().getStringExtra("updater_test_path");
            if (testPath != null && !testPath.isEmpty())
                updateIntent.putExtra("test_apk_path", testPath);
        }
        try {
            startActivity(updateIntent);
        } catch (ActivityNotFoundException e) {
            new AlertDialog.Builder(this)
                    .setTitle("Нужен Updater")
                    .setMessage("Для обновлений установите приложение «Обновление испанского» один раз. После этого кнопка «Обновление» будет передавать установку ему.")
                    .setPositiveButton("Понятно", null)
                    .show();
        }
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
        connect.setText("Старое подключение ChatGPT (лимитировано)");
        connect.setVisibility(View.GONE);
        ui.style(connect, UiStyle.Tone.PRIMARY);
        box.addView(connect, spaced(12));

        ScrollView historyScroll = new ScrollView(this);
        assistantHistory = new TextView(this);
        assistantHistory.setTextSize(16);
        assistantHistory.setTextColor(primaryText());
        assistantHistory.setPadding(0, dp(10), 0, dp(10));
        assistantHistory.setText("Режим обычного ChatGPT — без Subscription Sharing. " +
                "При отправке приложение скопирует задание со всем текущим словарём и откроет ChatGPT. " +
                "Вставьте текст и нажмите отправить в ChatGPT. " +
                "Для рассказа скопируйте готовый JSON-ответ и нажмите «Забрать рассказ».");
        assistantHistory.setPadding(dp(14), dp(14), dp(14), dp(14));
        historyScroll.setBackground(ui.rounded(ui.soft, 15, ui.outline));
        historyScroll.addView(assistantHistory);
        LinearLayout.LayoutParams historyParams = new LinearLayout.LayoutParams(-1, dp(145));
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
        mic.setText("🎙 Голос ChatGPT");
        ui.style(mic, UiStyle.Tone.OUTLINE);
        Button send = new Button(this);
        send.setText("Обычный ChatGPT");
        ui.style(send, UiStyle.Tone.PRIMARY);
        actions.addView(mic, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(0, -2, 1);
        sendParams.setMarginStart(dp(6));
        actions.addView(send, sendParams);
        box.addView(actions);
        Button receive = new Button(this);
        receive.setText("Забрать рассказ из ChatGPT");
        receive.setAllCaps(false);
        ui.style(receive, UiStyle.Tone.OUTLINE);
        box.addView(receive, spaced(8));
        receive.setOnClickListener(v -> importRegularChatGptStory());
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
        voiceHelp.setText("В обычном ChatGPT вставьте скопированный текст. После ответа нажмите «Копировать» и вернитесь сюда.");
        voiceHelp.setTextColor(secondaryText());
        voiceHelp.setTextSize(13);
        voiceHelp.setPadding(0, dp(8), 0, dp(6));
        box.addView(voiceHelp);

        Runnable refreshConnection = () -> {
            assistantStatus.setText("Официальный ChatGPT: без отдельного Subscription Sharing. " +
                    "Контекст словаря подготовится при отправке.");
            connect.setVisibility(View.GONE);
            send.setEnabled(!assistantRequestInFlight);
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

        mic.setOnClickListener(v -> openRegularChatGpt(true));
        send.setOnClickListener(v -> openRegularChatGpt(false));

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

    /**
     * Official ChatGPT in a trusted browser tab: no app subscription sharing,
     * OAuth token or paid API key. Browsers do not expose ChatGPT's composer,
     * so the user explicitly pastes the prepared context and sends the message.
     */
    private void openRegularChatGpt(boolean voice) {
        if (assistantRequestInFlight) {
            Toast.makeText(this, "Сначала завершите текущую операцию", Toast.LENGTH_LONG).show();
            return;
        }
        String text = assistantInput == null ? "" : assistantInput.getText().toString().trim();
        if (text.isEmpty()) text = voice
                ? "Помогай мне учить язык. Я задам вопрос голосом в чате."
                : "Напиши короткий рассказ с использованием освоенных слов из моего словаря.";
        String prepared;
        try {
            prepared = RegularChatGptBridge.prompt(store, text);
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null) throw new IllegalStateException("Буфер обмена недоступен");
            clipboard.setPrimaryClip(ClipData.newPlainText("Задание и мой словарь", prepared));
            if (assistantStatus != null) {
                assistantStatus.setText("Контекст и задача скопированы. В открывшемся ChatGPT " +
                        "вставьте текст в поле ввода и нажмите отправку. После ответа " +
                        "скопируйте JSON рассказа и нажмите «Забрать рассказ».");
            }
            new CustomTabsIntent.Builder().setShowTitle(true).build()
                    .launchUrl(this, Uri.parse("https://chatgpt.com/"));
        } catch (Exception error) {
            String message = error.getMessage() == null ? "Не удалось открыть ChatGPT" : error.getMessage();
            if (assistantStatus != null) assistantStatus.setText(message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    /** Imports only a user-approved story from the clipboard, not arbitrary cards. */
    private void importRegularChatGptStory() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() < 1)
                throw new IllegalArgumentException("Сначала скопируйте JSON-ответ ChatGPT");
            CharSequence raw = clip.getItemAt(0).coerceToText(this);
            RegularChatGptBridge.StoryDraft draft =
                    RegularChatGptBridge.parseStory(raw == null ? "" : raw.toString());
            if (!studyLanguage.equals(draft.language()))
                throw new IllegalArgumentException("Этот рассказ для " +
                        ("en".equals(draft.language()) ? "английского" : "испанского") +
                        ". Переключите язык приложения.");
            final Store current = store;
            new AlertDialog.Builder(this)
                    .setTitle("Сохранить рассказ?")
                    .setMessage("«" + draft.title() + "»\n\n" +
                            draft.body().substring(0, Math.min(260, draft.body().length())) +
                            (draft.body().length() > 260 ? "…" : "") +
                            "\n\nСохраним с русским переводом в текущий язык.")
                    .setPositiveButton("Сохранить", (dialog, which) -> {
                        if (store != current) {
                            Toast.makeText(this, "Язык изменён, повторите импорт", Toast.LENGTH_LONG).show();
                            return;
                        }
                        try {
                            if (!current.addStoryIfMissing(draft.title(), draft.body(),
                                    draft.translation(), "ChatGPT • обычный чат")) {
                                Toast.makeText(this, "Рассказ с таким названием уже есть", Toast.LENGTH_LONG).show();
                                return;
                            }
                            long id = -1;
                            for (Store.Story story : current.stories()) {
                                if (draft.title().equals(story.title())) { id = story.id(); break; }
                            }
                            if (assistantStatus != null) assistantStatus.setText("Сохранено: " + draft.title());
                            if (id > 0) showStoryReadyDialog(new ChatGptAssistant.SavedStory(id, draft.title()));
                            else Toast.makeText(this, "Рассказ сохранён", Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            new AlertDialog.Builder(this).setTitle("Не удалось сохранить")
                                    .setMessage(e.getMessage()).setPositiveButton("OK", null).show();
                        }
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        } catch (Exception e) {
            String message = e.getMessage() == null ? "Не удалось разобрать ответ ChatGPT" : e.getMessage();
            new AlertDialog.Builder(this).setTitle("Рассказ не импортирован")
                    .setMessage(message + "\n\nСкопируйте весь JSON с полями format, language, title, body, translation.")
                    .setPositiveButton("OK", null).show();
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
                    openRegularChatGpt(false);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void updateAssistantVoiceButton() {
        if (assistantVoiceButton != null)
            assistantVoiceButton.setText("🎙 Голос ChatGPT");
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
            assistantSendButton.setText(busy ? "⏳ Генерирую…" : "Обычный ChatGPT");
            assistantSendButton.setEnabled(!busy);
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

        // All unmastered words remain available for bidirectional swiping.
        // Marking a card known changes its status but never deletes it from the AI vocabulary.
        List<Store.Card> cards = store.cards(false, kind, kind == 0 ? category : 0);
        TextView count = text(cards.isEmpty() ? "Здесь пока нет карточек" : "Карточек: " + cards.size(), 16);
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
                    }
                    render();
                })
                .show();
    }

    private void openStory(Store.Story story) {
        player.pause(); remember(); storyId = story.id(); screen = "player"; player.load(story.body(), story.position()); render();
    }
    private void storyPlayer() {
        Store.Story selected = null; for(Store.Story story : store.stories()) if(story.id() == storyId) selected = story;
        if(selected == null) { text("Рассказ не найден", 22); return; }
        text(selected.title(), 25);
        TextView source = text(selected.source(), 14);
        source.setTextColor(secondaryText());
        TextView hint = text(studyingEnglish() ? "Удерживайте незнакомое английское слово, чтобы добавить его в карточки." : "Удерживайте незнакомое испанское слово, чтобы добавить его в карточки.", 14);
        hint.setTextColor(secondaryText());
        hint.setPadding(0, dp(4), 0, dp(12));

        storyTranslations = Sentences.split(selected.translation());
        LinearLayout readingPanel = new LinearLayout(this);
        readingPanel.setOrientation(LinearLayout.VERTICAL);
        readingPanel.setPadding(dp(18), dp(16), dp(18), dp(16));
        readingPanel.setBackground(ui.cardSurface());
        readingPanel.setElevation(dp(4));
        content.addView(readingPanel, spaced(15));

        sentence = textInto(readingPanel, player.current(), 26);
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

        toggle = button("Слушать", () -> {
            if (player.state.phase == PlaybackState.Phase.SPEAKING ||
                    (!player.sentenceMode && player.state.phase == PlaybackState.Phase.WAITING))
                player.pause();
            else player.play();
        });
        ui.style(toggle, UiStyle.Tone.PRIMARY);
        toggle.setTextSize(20);
        toggle.setMinHeight(dp(70));

        repeat = button("Повторить предложение", player::repeat);
        Button readAll = button("Читать весь рассказ", () -> { player.pause(); showFullStory(); });
        ui.style(readAll, UiStyle.Tone.OUTLINE);
        Button back = button("← Библиотека", () -> navigate("stories"));
        ui.style(back, UiStyle.Tone.OUTLINE);
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
            original.setTextColor(primaryText());
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
    private void updatePlayer() {
        if (player == null || rendering) return;
        if (cardVoiceStatus != null) cardVoiceStatus.setText(player.voiceDescription);
        if (storyId >= 0 && player.state.count > 0) store.position(storyId, player.state.index);
        if (sentence == null) return;
        sentence.setText(player.current()); if (sentenceRu != null) sentenceRu.setText(currentTranslation());
        boolean active = player.state.phase == PlaybackState.Phase.SPEAKING || (!player.sentenceMode && player.state.phase == PlaybackState.Phase.WAITING);
        toggle.setText(active ? "Ⅱ Пауза" : player.state.phase == PlaybackState.Phase.FINISHED ? "Слушать сначала" : "▶ Продолжить");
        toggle.setEnabled(player.ready()); repeat.setEnabled(player.ready());
        String status = switch(player.state.phase) { case SPEAKING -> "Звучит"; case WAITING -> player.sentenceMode ? "Нажмите «Продолжить»" : "Пауза между предложениями"; case FINISHED -> "Рассказ закончен"; case PAUSED -> "Остановлено"; };
        playbackStatus.setText((player.state.index + 1) + " / " + player.state.count + " • " + status + (player.error.isEmpty() ? "" : "\n" + player.error));
    }
    private void syncContent() {
        if (contentSync == null || personalStorySync == null || syncingContent) return;
        boolean personal = PrivateVocabularySync.isConfigured(this);
        if (!personal && studyingEnglish()) return;
        syncingContent = true;
        Store syncedStore = store;
        String syncedProfile = PrivateVocabularySync.profileRepository(this);
        RemoteContentSync.Callback callback = (added, rejected, error) -> runOnUiThread(() -> {
            syncingContent = false;
            if (isFinishing() || isDestroyed()) return;
            if (store != syncedStore
                    || !syncedProfile.equals(PrivateVocabularySync.profileRepository(this))) {
                syncContent();
                return;
            }
            if (added > 0) {
                Toast.makeText(this, "Добавлено из ChatGPT: " + added, Toast.LENGTH_SHORT).show();
                if ((cardDialog == null || !cardDialog.isShowing()) && !"player".equals(screen)) render();
            }
        });
        if (personal) {
            personalStorySync.sync(this, syncedStore, studyLanguage,
                    (added, rejected, error) -> callback.done(added, rejected, error));
        } else {
            contentSync.sync(syncedStore, callback);
        }
    }

    @Override public void onBackPressed() { if (screen.equals("player")) navigate("stories"); else super.onBackPressed(); }
    @Override protected void onResume() {
        super.onResume();
        if (prefs.getBoolean(prefKey("cardDraftOpen"), false) && (cardDialog == null || !cardDialog.isShowing())) addCard();
        syncContent();
        PrivateVocabularySync.schedule(this);
    }
    @Override protected void onPause() { stopAssistantVoice(); cancelCardSpeech(); player.pause(); remember(); rememberCardDraft(); super.onPause(); }
    @Override protected void onDestroy() {
        imageRepairCancel.set(true);
        cancelCardSpeech();
        if (contentSync != null) contentSync.close();
        if (personalStorySync != null) personalStorySync.close();
        if (assistant != null) assistant.close();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (assistantVoiceOutput != null) assistantVoiceOutput.close();
        player.close();
        if (englishStore != null) englishStore.close();
        if (spanishStore != null) spanishStore.close();
        super.onDestroy();
    }
}
