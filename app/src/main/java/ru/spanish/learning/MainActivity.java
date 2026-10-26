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
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.os.Bundle;
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
    private static final int MIC_PERMISSION = 7301;
    private boolean syncingContent;
    private SpeechPlayer player;
    private SharedPreferences prefs;
    private LinearLayout content;
    private TextView sentence, sentenceRu, playbackStatus;
    private Button toggle, repeat;
    private List<String> storyTranslations = new java.util.ArrayList<>();
    private boolean darkTheme;
    private int kind;
    private int category;
    private int reviewIndex;
    private long reviewStep;
    private boolean cardRevealed;
    private static final long CARD_PRONUNCIATION_DELAY_MS = 2000L;
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
    private int pageBg() { return darkTheme ? Color.rgb(18, 23, 22) : Color.rgb(245, 243, 237); }
    private int primaryText() { return darkTheme ? Color.rgb(235, 240, 238) : Color.rgb(29, 47, 44); }
    private int secondaryText() { return darkTheme ? Color.rgb(174, 187, 183) : Color.rgb(76, 90, 87); }
    private int cardBg() { return darkTheme ? Color.rgb(31, 38, 36) : Color.WHITE; }
    private int borderColor() { return darkTheme ? Color.rgb(74, 86, 82) : 0xFFD7DDD6; }
    private TextView text(String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(primaryText()); t.setPadding(0, dp(8), 0, dp(8)); content.addView(t); return t;
    }
    private Button button(String value, Runnable action) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setMinHeight(dp(52)); content.addView(b, new LinearLayout.LayoutParams(-1, -2)); b.setOnClickListener(v -> action.run()); return b;
    }
    private Button rowButton(String value, Runnable action) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setMinHeight(dp(48));
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
        for (String label : new String[]{"Карточки", "Сказки"}) {
            Button tab = new Button(this); tab.setText(label); tab.setAllCaps(false); tabs.addView(tab, new LinearLayout.LayoutParams(0, dp(56), 1));
            tab.setOnClickListener(v -> navigate(label.equals("Карточки") ? "cards" : "stories"));
        }
        if (!screen.equals("cards")) {
            Button settingsButton = rowButton("⚙", this::showSettings);
            settingsButton.setTextSize(24);
            tabs.addView(settingsButton, new LinearLayout.LayoutParams(dp(64), dp(56)));
        }
        root.addView(tabs);
        ScrollView scroll = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20), dp(8), dp(20), dp(24)); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
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
        box.addView(mode, new LinearLayout.LayoutParams(-1, -2));

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
        box.addView(speed, new LinearLayout.LayoutParams(-1, -2));

        Button pause = rowButton("Пауза между предложениями: " + player.pauseMs / 1000f + " с", () ->
                new AlertDialog.Builder(this).setTitle("Пауза в непрерывном режиме")
                        .setItems(new String[]{"0 с", "1 с", "1.5 с", "3 с", "5 с"}, (d,w) -> {
                            player.pause();
                            player.pauseMs = new int[]{0,1000,1500,3000,5000}[w];
                            remember();
                            updatePlayer();
                        }).show());
        box.addView(pause, new LinearLayout.LayoutParams(-1, -2));

        box.addView(rowButton("Обновление", this::startUpdateNow), new LinearLayout.LayoutParams(-1, -2));
        box.addView(rowButton("Версия / сборка", this::showBuild), new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Настройки")
                .setView(box)
                .setNegativeButton("Закрыть", null)
                .create();

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
        assistantStatus.setPadding(0, dp(6), 0, dp(8));
        box.addView(assistantStatus);

        Button connect = new Button(this);
        connect.setAllCaps(false);
        connect.setText("Подключить ChatGPT");
        box.addView(connect, new LinearLayout.LayoutParams(-1, -2));

        ScrollView historyScroll = new ScrollView(this);
        assistantHistory = new TextView(this);
        assistantHistory.setTextSize(16);
        assistantHistory.setTextColor(primaryText());
        assistantHistory.setPadding(0, dp(10), 0, dp(10));
        assistantHistory.setText(assistant.historyText().isEmpty()
                ? "Здесь можно говорить или писать. Контекст приложения уже передаётся автоматически."
                : assistant.historyText());
        historyScroll.addView(assistantHistory);
        LinearLayout.LayoutParams historyParams = new LinearLayout.LayoutParams(-1, dp(240));
        box.addView(historyScroll, historyParams);

        assistantInput = new EditText(this);
        assistantInput.setHint("Напишите или наговорите задачу…");
        assistantInput.setMinLines(2);
        assistantInput.setMaxLines(5);
        assistantInput.setTextSize(17);
        box.addView(assistantInput, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button mic = new Button(this);
        mic.setText("🎤 Голос");
        mic.setAllCaps(false);
        Button send = new Button(this);
        send.setText("Отправить");
        send.setAllCaps(false);
        actions.addView(mic, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(0, -2, 1);
        sendParams.setMarginStart(dp(6));
        actions.addView(send, sendParams);
        box.addView(actions);

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

        mic.setOnClickListener(v -> startAssistantVoice());
        send.setOnClickListener(v -> {
            String message = assistantInput.getText().toString().trim();
            if (message.isEmpty()) {
                assistantInput.setError("Введите сообщение");
                return;
            }
            send.setEnabled(false);
            mic.setEnabled(false);
            assistantStatus.setText("Отправляю в ChatGPT…");
            assistant.send(store, message, (ok, answer) -> runOnUiThread(() -> {
                send.setEnabled(true);
                mic.setEnabled(true);
                assistantStatus.setText(ok ? "Готово" : answer);
                if (ok) {
                    assistantInput.setText("");
                    assistantHistory.setText(assistant.historyText());
                }
            }));
        });

        assistantDialog = new AlertDialog.Builder(this)
                .setTitle("ChatGPT • Изучение испанского")
                .setView(box)
                .setNegativeButton("Закрыть", null)
                .create();
        assistantDialog.setOnDismissListener(d -> {
            assistantInput = null;
            assistantHistory = null;
            assistantStatus = null;
            if (!isFinishing() && !isDestroyed() && !"player".equals(screen)) render();
        });
        assistantDialog.show();
    }

    private void startAssistantVoice() {
        if (assistantInput == null) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Голосовой набор недоступен на этом телефоне", Toast.LENGTH_LONG).show();
            return;
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    if (assistantStatus != null) assistantStatus.setText("Говорите…");
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {
                    if (assistantStatus != null) assistantStatus.setText("Распознаю…");
                }
                @Override public void onError(int error) {
                    if (assistantStatus != null) assistantStatus.setText("Не удалось распознать голос. Нажмите микрофон ещё раз.");
                }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> variants = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (assistantInput != null && variants != null && !variants.isEmpty()) {
                        assistantInput.setText(variants.get(0));
                        assistantInput.setSelection(assistantInput.length());
                    }
                    if (assistantStatus != null) assistantStatus.setText("Готово к отправке");
                }
                @Override public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> variants = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (assistantInput != null && variants != null && !variants.isEmpty()) {
                        assistantInput.setText(variants.get(0));
                        assistantInput.setSelection(assistantInput.length());
                    }
                }
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechRecognizer.startListening(intent);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MIC_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            startAssistantVoice();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    private void cards() {
        player.pause();
        text("Карточки", 28);

        LinearLayout maintenance = new LinearLayout(this);
        maintenance.setOrientation(LinearLayout.HORIZONTAL);
        Button updateButton = rowButton("Обновление", this::startUpdateNow);
        Button buildButton = rowButton("Версия / сборка", this::showBuild);
        maintenance.addView(updateButton, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams buildParams = new LinearLayout.LayoutParams(0, -2, 1);
        buildParams.setMarginStart(dp(6));
        maintenance.addView(buildButton, buildParams);
        content.addView(maintenance, new LinearLayout.LayoutParams(-1, -2));

        text("Сначала вспомните смысл. Перевод откроется только после нажатия на карточку.", 15);
        button("ChatGPT — голос / текст", this::showAssistant);

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
            LinearLayout.LayoutParams categoryParams = new LinearLayout.LayoutParams(0, -2, 1);
            categoryParams.setMarginStart(dp(6));
            filters.addView(categoryButton, categoryParams);
        }
        content.addView(filters, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout addActions = new LinearLayout(this);
        addActions.setOrientation(LinearLayout.HORIZONTAL);
        Button addWordButton = rowButton("＋ Слово", () -> addCard(0));
        Button addPhraseButton = rowButton("＋ Устойчивое выражение", () -> addCard(1));
        addActions.addView(addWordButton, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams phraseParams = new LinearLayout.LayoutParams(0, -2, 1);
        phraseParams.setMarginStart(dp(6));
        addActions.addView(addPhraseButton, phraseParams);
        content.addView(addActions, new LinearLayout.LayoutParams(-1, -2));

        // Study view shows only active cards; mastered items remain stored for the assistant.
        List<Store.Card> cards = store.cardsForReview(kind, kind == 0 ? category : 0, reviewStep);
        text(cards.isEmpty() ? "Здесь пока нет карточек" : "Карточек: " + cards.size(), 16);
        if (cards.isEmpty()) return;

        if (reviewIndex < 0 || reviewIndex >= cards.size()) reviewIndex = 0;
        Store.Card card = cards.get(reviewIndex);

        LinearLayout cardBox = new LinearLayout(this);
        cardBox.setOrientation(LinearLayout.VERTICAL);
        cardBox.setGravity(Gravity.CENTER_HORIZONTAL);
        cardBox.setPadding(dp(12), dp(12), dp(12), dp(12));
        cardBox.setMinimumHeight(dp(290));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardBg());
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), borderColor());
        cardBox.setBackground(bg);
        content.addView(cardBox, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout visual = new FrameLayout(this);
        LinearLayout.LayoutParams visualParams = new LinearLayout.LayoutParams(-1, dp(150));
        visualParams.bottomMargin = dp(4);
        cardBox.addView(visual, visualParams);

        TextView visualHint = new TextView(this);
        visualHint.setText(CardVisualHint.forCard(card));
        visualHint.setTextSize(54);
        visualHint.setGravity(Gravity.CENTER);
        visual.addView(visualHint, new FrameLayout.LayoutParams(-1, -1));

        android.webkit.WebView picture = new android.webkit.WebView(this);
        visual.addView(picture, new FrameLayout.LayoutParams(-1, -1));
        CardImageLoader.load(this, picture, visualHint, card.spanish());

        TextView word = new TextView(this);
        word.setText(cardRevealed ? card.meaning() : card.spanish());
        word.setTag(!cardRevealed);
        word.setTextSize(30);
        word.setTextColor(primaryText());
        word.setGravity(Gravity.CENTER);
        word.setPadding(0, dp(10), 0, dp(10));
        cardBox.addView(word, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"Не знаю", "Знаю"};
        for (int i = 0; i < labels.length; i++) {
            final int result = i == 0 ? 0 : 2;
            Button b = new Button(this);
            b.setText(labels[i]);
            b.setAllCaps(false);
            b.setMinHeight(dp(46));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) p.setMarginStart(dp(6));
            actions.addView(b, p);
            b.setOnClickListener(v -> {
                reviewStep++;
                long nextDue = result == 0 ? reviewStep + 4 : 0;
                store.review(card.id(), result, nextDue);
                reviewIndex = 0;
                cardRevealed = false;
                render();
            });
        }
        cardBox.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        if (!cardRevealed) actions.setVisibility(View.GONE);
        cardBox.setOnClickListener(v -> {
            if (!cardRevealed) {
                cancelCardSpeech();
                player.pause();
                cardRevealed = true;
                actions.setVisibility(View.VISIBLE);
                word.setText(card.meaning());
                word.setTag(false);
            } else if (!Boolean.TRUE.equals(word.getTag())) {
                word.setText(card.spanish());
                word.setTag(true);
                scheduleCardSpeech(card.spanish(), word);
            } else {
                cancelCardSpeech();
                player.pause();
                word.setText(card.meaning());
                word.setTag(false);
            }
        });

        TextView version = text("Версия " + BuildConfig.VERSION_NAME, 11);
        version.setTextColor(secondaryText());
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, dp(2), 0, dp(2));
        text((reviewIndex + 1) + " / " + cards.size(), 14);
        if (!cardRevealed) scheduleCardSpeech(card.spanish(), word);
    }

    private EditText field(LinearLayout box, String hint, int max, boolean multiline) {
        EditText e = new EditText(this); e.setHint(hint); e.setTextSize(18); e.setSingleLine(!multiline); e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)}); if(multiline) e.setMinLines(3); box.addView(e); return e;
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
        text("Слушайте целиком или по одному предложению.", 17);
        button("ChatGPT — голос / текст", this::showAssistant);
        for (Store.Story s : store.stories()) {
            text(s.title(), 23); text(s.source(), 14); button("Открыть и слушать", () -> openStory(s));
        }
    }
    private void openStory(Store.Story story) {
        player.pause(); remember(); storyId = story.id(); screen = "player"; player.load(story.body(), story.position()); render();
    }
    private void storyPlayer() {
        Store.Story selected = null; for(Store.Story s : store.stories()) if(s.id() == storyId) selected = s;
        if(selected == null) { text("Рассказ не найден", 22); return; }
        text(selected.title(), 25); text(selected.source(), 14);
        text("Удерживайте незнакомое испанское слово, чтобы добавить его в карточки.", 14);
        storyTranslations = Sentences.split(selected.translation());
        sentence = text(player.current(), 27);
        attachStoryWordGesture(sentence, this::currentTranslation);
        sentenceRu = text(currentTranslation(), 20); sentenceRu.setTextIsSelectable(true);
        sentenceRu.setTextColor(secondaryText());
        playbackStatus = text("", 16);
        toggle = button("Слушать", () -> { if(player.state.phase == PlaybackState.Phase.SPEAKING || (!player.sentenceMode && player.state.phase == PlaybackState.Phase.WAITING)) player.pause(); else player.play(); });
        toggle.setTextSize(24); toggle.setMinHeight(dp(76));
        button("▶ Весь рассказ с начала", () -> {
            player.playAllFromStart();
            remember();
            updatePlayer();
        });
        repeat = button("Повторить предложение", player::repeat);
        button("Читать весь рассказ", () -> { player.pause(); showFullStory(); });
        button("← Библиотека", () -> navigate("stories"));
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
            TextView original = new TextView(this);
            original.setText(es);
            original.setTextSize(22);
            original.setTextColor(primaryText());
            original.setPadding(0, dp(16), 0, dp(2));
            attachStoryWordGesture(original, () -> ru);
            lines.addView(original);
            TextView translated = new TextView(this);
            translated.setText(ru);
            translated.setTextSize(17);
            translated.setTextColor(secondaryText());
            translated.setPadding(0, dp(2), 0, dp(9));
            lines.addView(translated);
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
    @Override protected void onPause() { cancelCardSpeech(); player.pause(); remember(); rememberCardDraft(); super.onPause(); }
    @Override protected void onDestroy() {
        cancelCardSpeech();
        if (contentSync != null) contentSync.close();
        if (assistant != null) assistant.close();
        if (speechRecognizer != null) speechRecognizer.destroy();
        player.close();
        store.close();
        super.onDestroy();
    }
}
