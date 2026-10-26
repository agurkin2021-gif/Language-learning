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

public final class MainActivity extends Activity {
    private Store store;
    private RemoteContentSync contentSync;
    private ChatGptAssistant assistant;
    private AlertDialog assistantDialog;
    private EditText assistantInput;
    private TextView assistantHistory;
    private TextView assistantStatus;
    private SpeechRecognizer speechRecognizer;
    private AssistantVoiceOutput assistantVoiceOutput;
    private Button assistantVoiceButton, assistantSendButton;
    private boolean assistantVoiceActive, assistantVoiceProcessing, assistantVoiceReviewPending;
    private int assistantVoiceEpoch, assistantVoiceErrors;
    private final Handler assistantVoiceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingVoiceRestart;
    private static final int MIC_PERMISSION = 7301;
    private boolean syncingContent;
    private SpeechPlayer player;
    private SharedPreferences prefs;
    private LinearLayout content;
    private TextView sentence, sentenceRu, playbackStatus;
    private Button toggle, repeat;
    private List<String> storyTranslations = new java.util.ArrayList<>();
    private boolean darkTheme;
    private UiStyle ui;
    private int kind;
    private int category;
    private int reviewIndex;
    private long reviewStep;
    private boolean cardRevealed;
    private static final long CARD_PRONUNCIATION_DELAY_MS = 1000L;
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
        store = new Store(this); prefs = getSharedPreferences("ui", MODE_PRIVATE);
        contentSync = new RemoteContentSync();
        assistant = new ChatGptAssistant(this);
        player = new SpeechPlayer(this, this::updatePlayer);
        player.speed = prefs.getFloat("speed", .85f); player.pauseMs = prefs.getInt("pause", 1500); player.sentenceMode = prefs.getBoolean("sentenceMode", true);
        screen = prefs.getString("screen", "cards"); kind = prefs.getInt("kind", 0); category = prefs.getInt("category", 0); reviewIndex = prefs.getInt("reviewIndex", 0); reviewStep = prefs.getLong("reviewStep", 0);
        long last = prefs.getLong("story", -1);
        if ("player".equals(screen)) {
            for (Store.Story story : store.stories()) if (story.id() == last) { openStory(story); return; }
            screen = "stories";
        }
        render();
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
        cancelCardSpeech();
        Runnable speech = () -> {
            pendingCardSpeech = null;
            if (!"cards".equals(screen) || isFinishing() || isDestroyed()
                    || !word.isAttachedToWindow() || !Boolean.TRUE.equals(word.getTag())) return;
            player.speakCard(spanish);
        };
        pendingCardSpeech = speech;
        cardSpeechHandler.postDelayed(speech, CARD_PRONUNCIATION_DELAY_MS);
    }
    private void remember() {
        prefs.edit().putString("screen", screen).putLong("story", storyId).putInt("kind", kind).remove("archive")
                .putInt("category", category).putInt("reviewIndex", reviewIndex).putLong("reviewStep", reviewStep).putFloat("speed", player.speed).putInt("pause", player.pauseMs).putBoolean("sentenceMode", player.sentenceMode).apply();
        new BackupManager(this).dataChanged();
        if (storyId >= 0 && player.state.count > 0) store.position(storyId, player.state.index);
    }
    private void navigate(String next) { player.pause(); remember(); storyId = -1; screen = next; render(); }
    private void render() {
        cancelCardSpeech();
        rendering = true; sentence = null; sentenceRu = null; playbackStatus = null; toggle = null; repeat = null;
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(pageBg());
        root.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets; });
        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(dp(16), dp(12), dp(16), dp(8));
        for (String label : new String[]{"Карточки", "Сказки"}) {
            boolean selected = label.equals("Карточки") ? screen.equals("cards") : !screen.equals("cards");
            Button tab = rowButton(label, () -> navigate(label.equals("Карточки") ? "cards" : "stories"));
            ui.style(tab, selected ? UiStyle.Tone.SELECTED : UiStyle.Tone.TAB);
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(0, dp(52), 1);
            tabParams.setMarginEnd(dp(7));
            tabs.addView(tab, tabParams);
        }
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

        Button speed = rowButton("Скорость речи: " + player.speed + "× (сказки и карточки)", null);
        speed.setOnClickListener(v ->
                new AlertDialog.Builder(this).setTitle("Скорость речи — сказки и карточки")
                        .setItems(new String[]{"0.6×", "0.75×", "0.85×", "1.0×", "1.2×"}, (d,w) -> {
                            player.pause();
                            player.speed = new float[]{.6f,.75f,.85f,1f,1.2f}[w];
                            speed.setText("Скорость речи: " + player.speed + "× (сказки и карточки)");
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

    private void confirmResetDatabase(AlertDialog settingsDialog) {
        new AlertDialog.Builder(this)
                .setTitle("Сбросить базу?")
                .setMessage("Будут удалены все слова, устойчивые выражения, сказки и прогресс изучения на этом телефоне. Отменить удаление после подтверждения нельзя.")
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
        assistantHistory.setText(assistant.historyText().isEmpty()
                ? "Здесь можно говорить или писать. Контекст приложения уже передаётся автоматически."
                : assistant.historyText());
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
            send.setEnabled(connected);
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
                .setTitle("ChatGPT • Изучение испанского")
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
            if (!isFinishing() && !isDestroyed() && !"player".equals(screen)) render();
        });
        assistantDialog.show();
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

    private void sendAssistantMessage(String message, boolean fromVoice) {
        if (assistantInput == null) return;
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
        if (assistantSendButton != null) assistantSendButton.setEnabled(false);
        assistantVoiceProcessing = true;
        final int epoch = assistantVoiceEpoch;
        assistantStatus.setText("Отправляю в ChatGPT…");
        assistant.send(store, message, (ok, answer) -> runOnUiThread(() -> {
            if (assistantDialog == null || !assistantDialog.isShowing()
                    || isFinishing() || isDestroyed()) return;
            if (fromVoice && (!assistantVoiceActive || epoch != assistantVoiceEpoch)) return;
            assistantVoiceProcessing = false;
            if (assistantSendButton != null) assistantSendButton.setEnabled(assistant.isConnected());
            if (!ok) {
                if (fromVoice) stopAssistantVoice();
                assistantStatus.setText(answer);
                return;
            }
            if (assistantInput != null) assistantInput.setText("");
            if (assistantHistory != null) assistantHistory.setText(assistant.historyText());
            if (!fromVoice) {
                assistantStatus.setText("Готово");
                return;
            }
            if (assistantVoiceOutput == null) assistantVoiceOutput = new AssistantVoiceOutput(this);
            assistantStatus.setText("ChatGPT отвечает вслух…");
            assistantVoiceOutput.speak(answer, spoken -> {
                if (!assistantVoiceActive || epoch != assistantVoiceEpoch) return;
                if (!spoken) {
                    stopAssistantVoice();
                    assistantStatus.setText("Нет русского голоса синтеза Android. Установите русский голос в настройках телефона.");
                    return;
                }
                assistantStatus.setText("Продолжаем разговор…");
                pendingVoiceRestart = () -> {
                    pendingVoiceRestart = null;
                    if (assistantVoiceActive && epoch == assistantVoiceEpoch) startAssistantVoice();
                };
                assistantVoiceHandler.postDelayed(pendingVoiceRestart, 650);
            });
        }));
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
            ArrayList<String> hints = new ArrayList<>(List.of(
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
        text("Карточки", 28);

        TextView hint = text("Нажмите для перевода · листайте в обе стороны · × — освоено", 15);
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

        TextView confirmKnown = new TextView(this);
        confirmKnown.setText("УДАЛИТЬ");
        confirmKnown.setTextSize(15);
        confirmKnown.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        confirmKnown.setTextColor(ui.danger);
        confirmKnown.setGravity(Gravity.CENTER);
        confirmKnown.setPadding(dp(18), 0, dp(18), 0);
        confirmKnown.setContentDescription("Подтвердить: убрать из изучения, сохранить в словаре");
        confirmKnown.setBackground(ui.ripple(ui.dangerSoft, 14, ui.danger));
        confirmKnown.setElevation(dp(12));
        confirmKnown.setVisibility(View.GONE);
        confirmKnown.setOnClickListener(v -> {
            cancelCardSpeech();
            player.pause();
            store.review(cards.get(reviewIndex).id(), 2, 0);
            if (reviewIndex >= cards.size() - 1) reviewIndex = 0;
            cardRevealed = false;
            render();
        });

        TextView known = new TextView(this);
        known.setText("×");
        known.setTextSize(27);
        known.setTextColor(ui.danger);
        known.setGravity(Gravity.CENTER);
        known.setContentDescription("Открыть подтверждение удаления из изучения");
        known.setBackground(ui.ripple(ui.dangerSoft, 25, Color.TRANSPARENT));
        known.setElevation(dp(12));
        known.setOnClickListener(v -> confirmKnown.setVisibility(
                confirmKnown.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        CardSwipePager strip = new CardSwipePager(this,
                previous.view(), front.view(), next.view(), size > 1,
                new CardSwipePager.Listener() {
                    @Override public void onTap() {
                        if (confirmKnown.getVisibility() == View.VISIBLE) {
                            confirmKnown.setVisibility(View.GONE);
                            return;
                        }
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
                        player.pause();
                        confirmKnown.setVisibility(View.GONE);
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
                        prefs.edit().putInt("reviewIndex", reviewIndex).apply();
                        scheduleCardSpeech(cards.get(reviewIndex).spanish(), visibleFaces[1].word());
                    }
                });
        strip.setTag("card-swipe-viewport");
        cardFrame.addView(strip, new FrameLayout.LayoutParams(-1, dp(315)));

        FrameLayout.LayoutParams confirmParams =
                new FrameLayout.LayoutParams(-2, dp(48), Gravity.TOP | Gravity.END);
        confirmParams.topMargin = dp(62);
        confirmParams.rightMargin = dp(10);
        cardFrame.addView(confirmKnown, confirmParams);
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
        CardImageLoader.load(this, picture, fallback, card.spanish());

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
            CardImageLoader.load(this, face.picture(), face.fallback(), card.spanish());
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

    }

    private EditText field(LinearLayout box, String hint, int max, boolean multiline) {
        EditText e = new EditText(this); e.setHint(hint); e.setTextSize(18); e.setSingleLine(!multiline); e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)}); if(multiline) e.setMinLines(3); ui.styleInput(e); box.addView(e, spaced(10)); return e;
    }
    private LinearLayout form() { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20),dp(8),dp(20),dp(8)); return box; }
    private void addCard() { addCard(kind); }
    private void addCard(int requestedKind) {
        cancelCardSpeech();
        if (cardDialog != null && cardDialog.isShowing()) return;
        LinearLayout box = form(); EditText es = field(box, "По-испански", 500, false); EditText ru = field(box, "Русский смысл", 1000, true);
        boolean restoring = prefs.getBoolean("cardDraftOpen", false);
        draftKind = restoring ? prefs.getInt("cardDraftKind", requestedKind) : requestedKind;
        int defaultCategory = category >= 1 && category <= 3 ? category : 1;
        draftCategory = restoring ? prefs.getInt("cardDraftCategory", defaultCategory) : defaultCategory;
        if (restoring) { es.setText(prefs.getString("cardDraftSpanish", "")); ru.setText(prefs.getString("cardDraftMeaning", "")); }
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
            if (es.getText().toString().trim().isEmpty()) { es.setError("Введите испанский текст"); return; }
            if (ru.getText().toString().trim().isEmpty()) { ru.setError("Введите русский смысл"); return; }
            draftCategory = draftKind == 0 && draftCategorySpinner != null ? draftCategorySpinner.getSelectedItemPosition() + 1 : 0;
            store.addCard(es.getText().toString().trim(), ru.getText().toString().trim(), draftKind, draftCategory);
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
            prefs.edit().putBoolean("cardDraftOpen", true).putInt("cardDraftKind", draftKind).putInt("cardDraftCategory", draftCategory)
                    .putString("cardDraftSpanish", draftSpanish.getText().toString())
                    .putString("cardDraftMeaning", draftMeaning.getText().toString()).commit();
        }
    }
    private void clearCardDraft() {
        prefs.edit().remove("cardDraftOpen").remove("cardDraftKind").remove("cardDraftCategory").remove("cardDraftSpanish").remove("cardDraftMeaning").commit();
        draftSpanish = null; draftMeaning = null; draftCategorySpinner = null;
    }
    private void stories() {
        text("Библиотека сказок", 28);
        TextView subtitle = text("Слушайте целиком или по одному предложению.", 16);
        subtitle.setTextColor(secondaryText());
        subtitle.setPadding(0, dp(3), 0, dp(12));
        Button chat = button("ChatGPT — голос / текст", this::showAssistant);
        ui.style(chat, UiStyle.Tone.PRIMARY);
        for (Store.Story story : store.stories()) {
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(dp(17), dp(15), dp(17), dp(16));
            tile.setBackground(ui.cardSurface());
            tile.setElevation(dp(4));
            TextView name = textInto(tile, story.title(), 22);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            TextView source = textInto(tile, story.source(), 14);
            source.setTextColor(secondaryText());
            source.setPadding(0, dp(3), 0, dp(12));
            Button open = rowButton("Открыть и слушать", () -> openStory(story));
            ui.style(open, UiStyle.Tone.OUTLINE);
            tile.addView(open, spaced(0));
            LinearLayout.LayoutParams tileParams = spaced(14);
            tileParams.topMargin = dp(2);
            content.addView(tile, tileParams);
        }
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
        TextView hint = text("Удерживайте незнакомое испанское слово, чтобы добавить его в карточки.", 14);
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

        button("▶ Весь рассказ с начала", () -> {
            player.playAllFromStart();
            remember();
            updatePlayer();
        });
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
        try {
            Store.StoryWordSave result = StoryVocabulary.add(this, store, match.word(), source, russian);
            label.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            SpannableString highlighted = new SpannableString(source);
            highlighted.setSpan(new BackgroundColorSpan(darkTheme ? 0xFF526A66 : 0xFFFFE39A),
                    match.start(), match.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            label.setText(highlighted);
            cardSpeechHandler.postDelayed(() -> {
                if (label.getText() == highlighted) label.setText(source);
            }, 600);
            String message = switch (result) {
                case ADDED -> "В изучение: " + match.word();
                case RESTORED -> "Снова в изучении: " + match.word();
                case ALREADY_STUDYING -> "Уже в карточках: " + match.word();
            };
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось добавить слово в карточки", Toast.LENGTH_SHORT).show();
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
        hint.setText("Удерживайте испанское слово для добавления в карточки.");
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
        if (contentSync == null || syncingContent) return;
        syncingContent = true;
        contentSync.sync(store, (added, rejected, error) -> runOnUiThread(() -> {
            syncingContent = false;
            if (isFinishing() || isDestroyed()) return;
            if (added > 0) {
                Toast.makeText(this, "Добавлено из ChatGPT: " + added, Toast.LENGTH_SHORT).show();
                if ((cardDialog == null || !cardDialog.isShowing()) && !"player".equals(screen)) render();
            }
        }));
    }

    @Override public void onBackPressed() { if (screen.equals("player")) navigate("stories"); else super.onBackPressed(); }
    @Override protected void onResume() {
        super.onResume();
        if (prefs.getBoolean("cardDraftOpen", false) && (cardDialog == null || !cardDialog.isShowing())) addCard();
        syncContent();
    }
    @Override protected void onPause() { stopAssistantVoice(); cancelCardSpeech(); player.pause(); remember(); rememberCardDraft(); super.onPause(); }
    @Override protected void onDestroy() {
        cancelCardSpeech();
        if (contentSync != null) contentSync.close();
        if (assistant != null) assistant.close();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (assistantVoiceOutput != null) assistantVoiceOutput.close();
        player.close();
        store.close();
        super.onDestroy();
    }
}
