package ru.spanish.learning;

import android.app.Activity;
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
import android.provider.Settings;
import android.text.InputFilter;
import android.view.View;
import android.widget.*;
import java.util.List;

public final class MainActivity extends Activity {
    private Store store;
    private SpeechPlayer player;
    private SharedPreferences prefs;
    private LinearLayout content;
    private TextView sentence, sentenceRu, playbackStatus;
    private Button toggle, repeat;
    private List<String> storyTranslations = new java.util.ArrayList<>();
    private boolean archive;
    private int kind;
    private int category;
    private int reviewIndex;
    private long reviewStep;
    private boolean cardRevealed;
    private String screen = "cards";
    private long storyId = -1;
    private boolean rendering;
    private AlertDialog cardDialog;
    private EditText draftSpanish, draftMeaning;
    private Spinner draftCategorySpinner;
    private int draftKind;
    private int draftCategory;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        android.app.job.JobScheduler legacyUpdater = getSystemService(android.app.job.JobScheduler.class);
        if (legacyUpdater != null) {
            legacyUpdater.cancel(72101);
            legacyUpdater.cancel(72102);
        }
        store = new Store(this); prefs = getSharedPreferences("ui", MODE_PRIVATE);
        player = new SpeechPlayer(this, this::updatePlayer);
        player.speed = prefs.getFloat("speed", .85f); player.pauseMs = prefs.getInt("pause", 1500); player.sentenceMode = prefs.getBoolean("sentenceMode", true);
        screen = prefs.getString("screen", "cards"); kind = prefs.getInt("kind", 0); category = prefs.getInt("category", 0); reviewIndex = prefs.getInt("reviewIndex", 0); reviewStep = prefs.getLong("reviewStep", 0); archive = prefs.getBoolean("archive", false);
        long last = prefs.getLong("story", -1);
        if ("player".equals(screen)) {
            for (Store.Story story : store.stories()) if (story.id() == last) { openStory(story); return; }
            screen = "stories";
        }
        render();
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(Color.rgb(29, 47, 44)); t.setPadding(0, dp(8), 0, dp(8)); content.addView(t); return t;
    }
    private Button button(String value, Runnable action) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setMinHeight(dp(52)); content.addView(b, new LinearLayout.LayoutParams(-1, -2)); b.setOnClickListener(v -> action.run()); return b;
    }
    private Button rowButton(String value, Runnable action) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setMinHeight(dp(48));
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private void remember() {
        prefs.edit().putString("screen", screen).putLong("story", storyId).putInt("kind", kind).putBoolean("archive", archive)
                .putInt("category", category).putInt("reviewIndex", reviewIndex).putLong("reviewStep", reviewStep).putFloat("speed", player.speed).putInt("pause", player.pauseMs).putBoolean("sentenceMode", player.sentenceMode).apply();
        new BackupManager(this).dataChanged();
        if (storyId >= 0 && player.state.count > 0) store.position(storyId, player.state.index);
    }
    private void navigate(String next) { player.pause(); remember(); storyId = -1; screen = next; render(); }
    private void render() {
        rendering = true; sentence = null; sentenceRu = null; playbackStatus = null; toggle = null; repeat = null;
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(245,243,237));
        root.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets; });
        LinearLayout tabs = new LinearLayout(this);
        for (String label : new String[]{"Карточки", "Сказки"}) {
            Button tab = new Button(this); tab.setText(label); tab.setAllCaps(false); tabs.addView(tab, new LinearLayout.LayoutParams(0, dp(56), 1));
            tab.setOnClickListener(v -> navigate(label.equals("Карточки") ? "cards" : "stories"));
        }
        Button settingsButton = rowButton("⚙", this::showSettings);
        settingsButton.setTextSize(24);
        tabs.addView(settingsButton, new LinearLayout.LayoutParams(dp(64), dp(56)));
        root.addView(tabs);
        ScrollView scroll = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20), dp(8), dp(20), dp(24)); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        if (screen.equals("cards")) cards(); else if (screen.equals("player")) storyPlayer(); else stories();
        rendering = false; remember(); updatePlayer();
    }
    private void showSettings() {
        player.pause();
        String[] items = {
                "Прослушивание: " + (player.sentenceMode ? "по одному предложению" : "весь рассказ без остановок"),
                "Скорость произношения: " + player.speed + "×",
                "Пауза между предложениями: " + player.pauseMs / 1000f + " с",
                "Обновление",
                "Версия / сборка"
        };
        new AlertDialog.Builder(this)
                .setTitle("Настройки")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        new AlertDialog.Builder(this).setTitle("Режим прослушивания")
                                .setItems(new String[]{"Весь рассказ без остановок", "По одному предложению"}, (d,w) -> {
                                    player.pause();
                                    player.sentenceMode = w == 1;
                                    remember();
                                    updatePlayer();
                                }).show();
                    } else if (which == 1) {
                        new AlertDialog.Builder(this).setTitle("Скорость произношения")
                                .setItems(new String[]{"0.6×", "0.75×", "0.85×", "1.0×", "1.2×"}, (d,w) -> {
                                    player.pause();
                                    player.speed = new float[]{.6f,.75f,.85f,1f,1.2f}[w];
                                    remember();
                                    updatePlayer();
                                }).show();
                    } else if (which == 2) {
                        new AlertDialog.Builder(this).setTitle("Пауза в непрерывном режиме")
                                .setItems(new String[]{"0 с", "1 с", "1.5 с", "3 с", "5 с"}, (d,w) -> {
                                    player.pause();
                                    player.pauseMs = new int[]{0,1000,1500,3000,5000}[w];
                                    remember();
                                    updatePlayer();
                                }).show();
                    } else if (which == 3) {
                        startUpdateNow();
                    } else {
                        showBuild();
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }
    private void showBuild() {
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
    private void cards() {
        text(archive ? "Освоенное" : "Карточки", 28);
        if (!archive) text("Сначала вспомните смысл. Перевод откроется только после нажатия на карточку.", 15);

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

        LinearLayout cardActions = new LinearLayout(this);
        cardActions.setOrientation(LinearLayout.HORIZONTAL);
        Button masteredButton = rowButton(archive ? "← К изучению" : "Освоенное", () -> {
            archive = !archive;
            reviewIndex = 0;
            cardRevealed = false;
            render();
        });
        cardActions.addView(masteredButton, new LinearLayout.LayoutParams(0, -2, 1));
        if (!archive) {
            Button addButton = rowButton("＋ Добавить", this::addCard);
            LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(0, -2, 1);
            addParams.setMarginStart(dp(6));
            cardActions.addView(addButton, addParams);
        }
        content.addView(cardActions, new LinearLayout.LayoutParams(-1, -2));

        List<Store.Card> cards = archive
                ? store.cards(true, kind, kind == 0 ? category : 0)
                : store.cardsForReview(kind, kind == 0 ? category : 0, reviewStep);
        text(cards.isEmpty() ? "Здесь пока нет карточек" : "Карточек: " + cards.size(), 16);
        if (cards.isEmpty()) return;

        if (archive) {
            for (Store.Card card : cards) {
                text(card.spanish(), 24);
                text(card.meaning(), 18);
                button("Вернуть к изучению", () -> {
                    store.setMastered(card.id(), false);
                    render();
                });
                View line = new View(this);
                line.setBackgroundColor(0xFFD7DDD6);
                content.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
            }
            return;
        }

        if (reviewIndex < 0 || reviewIndex >= cards.size()) reviewIndex = 0;
        Store.Card card = cards.get(reviewIndex);

        LinearLayout cardBox = new LinearLayout(this);
        cardBox.setOrientation(LinearLayout.VERTICAL);
        cardBox.setGravity(Gravity.CENTER_HORIZONTAL);
        cardBox.setPadding(dp(12), dp(12), dp(12), dp(12));
        cardBox.setMinimumHeight(dp(290));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), 0xFFD7DDD6);
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
        word.setTextSize(30);
        word.setTextColor(Color.rgb(29, 47, 44));
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
                cardRevealed = true;
                actions.setVisibility(View.VISIBLE);
                word.setText(card.meaning());
            } else if (word.getText().toString().equals(card.meaning())) {
                word.setText(card.spanish());
            } else {
                word.setText(card.meaning());
            }
        });

        text((reviewIndex + 1) + " / " + cards.size(), 14);
    }

    private EditText field(LinearLayout box, String hint, int max, boolean multiline) {
        EditText e = new EditText(this); e.setHint(hint); e.setTextSize(18); e.setSingleLine(!multiline); e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)}); if(multiline) e.setMinLines(3); box.addView(e); return e;
    }
    private LinearLayout form() { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20),dp(8),dp(20),dp(8)); return box; }
    private void addCard() {
        if (cardDialog != null && cardDialog.isShowing()) return;
        LinearLayout box = form(); EditText es = field(box, "По-испански", 500, false); EditText ru = field(box, "Русский смысл", 1000, true);
        boolean restoring = prefs.getBoolean("cardDraftOpen", false);
        draftKind = restoring ? prefs.getInt("cardDraftKind", kind) : kind;
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
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(draftKind == 0 ? "Новое слово" : "Новое выражение").setView(box).setNegativeButton("Отмена", (d, w) -> clearCardDraft()).setPositiveButton("Сохранить", null).create();
        cardDialog = dialog;
        dialog.setOnCancelListener(d -> clearCardDraft());
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            if (es.getText().toString().trim().isEmpty()) { es.setError("Введите испанский текст"); return; }
            if (ru.getText().toString().trim().isEmpty()) { ru.setError("Введите русский смысл"); return; }
            draftCategory = draftKind == 0 && draftCategorySpinner != null ? draftCategorySpinner.getSelectedItemPosition() + 1 : 0;
            store.addCard(es.getText().toString().trim(), ru.getText().toString().trim(), draftKind, draftCategory);
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
        button("Помощник: подключение не настроено", () -> new AlertDialog.Builder(this).setTitle("Генерация пока недоступна").setMessage("Освоенных карточек: " + store.mastered().size() + ".\n\nПриложение не читает этот чат. Для создания рассказов по архиву ещё нужно согласовать и подключить помощника. Сейчас данные никуда не отправляются, платные API не используются.\n\nНиже есть заранее написанный пример для прослушивания.").setPositiveButton("Понятно", null).show());
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
        storyTranslations = Sentences.split(selected.translation());
        sentence = text(player.current(), 27); sentence.setTextIsSelectable(true);
        sentenceRu = text(currentTranslation(), 20); sentenceRu.setTextIsSelectable(true);
        sentenceRu.setTextColor(Color.rgb(76, 90, 87));
        playbackStatus = text("", 16);
        toggle = button("Слушать", () -> { if(player.state.phase == PlaybackState.Phase.SPEAKING || (!player.sentenceMode && player.state.phase == PlaybackState.Phase.WAITING)) player.pause(); else player.play(); });
        toggle.setTextSize(24); toggle.setMinHeight(dp(76));
        button("▶ Весь рассказ с начала", () -> {
            player.playAllFromStart();
            remember();
            updatePlayer();
        });
        repeat = button("Повторить предложение", player::repeat);
        button("Читать весь рассказ", () -> { player.pause(); new AlertDialog.Builder(this).setTitle("Текст рассказа").setMessage(fullStoryText()).setPositiveButton("Закрыть",null).show(); });
        button("← Библиотека", () -> navigate("stories"));
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
    @Override public void onBackPressed() { if (screen.equals("player")) navigate("stories"); else super.onBackPressed(); }
    @Override protected void onResume() {
        super.onResume();
        if (prefs.getBoolean("cardDraftOpen", false) && (cardDialog == null || !cardDialog.isShowing())) addCard();
    }
    @Override protected void onPause() { player.pause(); remember(); rememberCardDraft(); super.onPause(); }
    @Override protected void onDestroy() { player.close(); store.close(); super.onDestroy(); }
}
