package ru.spanish.learning;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.graphics.Color;\nimport android.graphics.drawable.GradientDrawable;\nimport android.view.Gravity;
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
    private TextView sentence, playbackStatus, voice;
    private Button toggle, repeat;
    private boolean archive;
    private int kind;
    private String screen = "cards";
    private long storyId = -1;
    private boolean rendering;
    private AlertDialog cardDialog;
    private EditText draftSpanish, draftMeaning;
    private int draftKind;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        AppUpdater.setForeground(true);
        AppUpdater.configureSchedule(this);
        store = new Store(this); prefs = getSharedPreferences("ui", MODE_PRIVATE);
        player = new SpeechPlayer(this, this::updatePlayer);
        player.speed = prefs.getFloat("speed", .85f); player.pauseMs = prefs.getInt("pause", 1500); player.sentenceMode = prefs.getBoolean("sentenceMode", true);
        screen = prefs.getString("screen", "cards"); kind = prefs.getInt("kind", 0); category = prefs.getInt("category", 0); reviewIndex = prefs.getInt("reviewIndex", 0); archive = prefs.getBoolean("archive", false);
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
    private void remember() {
        prefs.edit().putString("screen", screen).putLong("story", storyId).putInt("kind", kind).putBoolean("archive", archive)
                .putInt("category", category).putInt("reviewIndex", reviewIndex).putFloat("speed", player.speed).putInt("pause", player.pauseMs).putBoolean("sentenceMode", player.sentenceMode).apply();
        if (storyId >= 0 && player.state.count > 0) store.position(storyId, player.state.index);
    }
    private void navigate(String next) { player.pause(); remember(); storyId = -1; screen = next; render(); }
    private void render() {
        rendering = true; sentence = null; playbackStatus = null; voice = null; toggle = null; repeat = null;
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(245,243,237));
        root.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets; });
        LinearLayout tabs = new LinearLayout(this);
        for (String label : new String[]{"Карточки", "Сказки"}) {
            Button tab = new Button(this); tab.setText(label); tab.setAllCaps(false); tabs.addView(tab, new LinearLayout.LayoutParams(0, dp(56), 1));
            tab.setOnClickListener(v -> navigate(label.equals("Карточки") ? "cards" : "stories"));
        }
        root.addView(tabs);
        ScrollView scroll = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20), dp(8), dp(20), dp(24)); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout updates = new LinearLayout(this);
        Button buildInfo = new Button(this);
        buildInfo.setText(getString(R.string.build_button, BuildConfig.VERSION_NAME));
        buildInfo.setAllCaps(false);
        buildInfo.setTextSize(13);
        buildInfo.setOnClickListener(v -> showBuild());
        updates.addView(buildInfo, new LinearLayout.LayoutParams(0, dp(60), 1));
        Button update = new Button(this);
        update.setText(R.string.update_button);
        update.setAllCaps(false);
        update.setTextSize(15);
        update.setOnClickListener(v -> showAutoUpdates());
        updates.addView(update, new LinearLayout.LayoutParams(0, dp(60), 1));
        root.addView(updates);
        setContentView(root);
        if (screen.equals("cards")) cards(); else if (screen.equals("player")) storyPlayer(); else stories();
        rendering = false; remember(); updatePlayer();
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
                })
                .setPositiveButton("Автообновления", (dialog, which) -> showAutoUpdates()).show();
    }
    private void showAutoUpdates() {
        player.pause();
        remember();
        if (BuildConfig.DEBUG) {
            new AlertDialog.Builder(this).setTitle("Служебная сборка")
                    .setMessage("Эта сборка предназначена для проверки. Автообновления доступны в версии для телефона с постоянной подписью.")
                    .setPositiveButton("Понятно", null).show();
            return;
        }
        boolean allowed = getPackageManager().canRequestPackageInstalls();
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("Автоматические обновления")
                .setMessage(AppUpdater.status(this) + "\n\nНовые версии загружаются сами. Установка происходит, когда вы переходите из приложения. После обновления откройте приложение снова.")
                .setNegativeButton("Закрыть", null);
        if (!allowed) {
            dialog.setPositiveButton("Разрешить обновления", (d, w) -> {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); }
                catch (ActivityNotFoundException e) { Toast.makeText(this, "Откройте разрешение установки для этого приложения в настройках Android", Toast.LENGTH_LONG).show(); }
            });
        } else if (AppUpdater.hasPendingConfirmation(this)) {
            dialog.setPositiveButton("Подтвердить в Android", (d, w) -> AppUpdater.continuePending(this));
        }
        dialog.show();
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

        button(kind == 0 ? "Слова ▾" : "Устойчивые выражения ▾", () ->
                new AlertDialog.Builder(this).setTitle("Тип карточек")
                        .setItems(new String[]{"Слова", "Устойчивые выражения"}, (d,w) -> {
                            kind = w;
                            if (kind == 1) category = 0;
                            reviewIndex = 0;
                            cardRevealed = false;
                            render();
                        }).show());

        if (kind == 0) {
            String[] labels = {"Все слова", "Глаголы", "Существительные", "Прилагательные"};
            button(labels[Math.max(0, Math.min(category, 3))] + " ▾", () ->
                    new AlertDialog.Builder(this).setTitle("Категория слов")
                            .setItems(labels, (d,w) -> {
                                category = w;
                                reviewIndex = 0;
                                cardRevealed = false;
                                render();
                            }).show());
        }

        button(archive ? "← Вернуться к изучению" : "Освоенное", () -> {
            archive = !archive;
            reviewIndex = 0;
            cardRevealed = false;
            render();
        });

        if (!archive) button("＋ Добавить карточку", this::addCard);

        List<Store.Card> cards = store.cards(archive, kind, kind == 0 ? category : 0);
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
        cardBox.setPadding(dp(20), dp(20), dp(20), dp(20));
        cardBox.setMinimumHeight(dp(360));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), 0xFFD7DDD6);
        cardBox.setBackground(bg);
        content.addView(cardBox, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout visual = new FrameLayout(this);
        LinearLayout.LayoutParams visualParams = new LinearLayout.LayoutParams(-1, dp(180));
        visualParams.bottomMargin = dp(10);
        cardBox.addView(visual, visualParams);

        TextView visualHint = new TextView(this);
        visualHint.setText(CardVisualHint.forCard(card));
        visualHint.setTextSize(78);
        visualHint.setGravity(Gravity.CENTER);
        visual.addView(visualHint, new FrameLayout.LayoutParams(-1, -1));

        ImageView picture = new ImageView(this);
        picture.setAdjustViewBounds(true);
        picture.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        visual.addView(picture, new FrameLayout.LayoutParams(-1, -1));
        CardImageLoader.load(this, picture, visualHint, card.spanish());

        TextView spanish = new TextView(this);
        spanish.setText(card.spanish());
        spanish.setTextSize(34);
        spanish.setTextColor(Color.rgb(29, 47, 44));
        spanish.setGravity(Gravity.CENTER);
        spanish.setPadding(0, dp(12), 0, dp(12));
        cardBox.addView(spanish, new LinearLayout.LayoutParams(-1, -2));

        if (!cardRevealed) {
            TextView prompt = new TextView(this);
            prompt.setText("Нажмите на карточку, чтобы открыть перевод");
            prompt.setTextSize(14);
            prompt.setTextColor(0xFF66736F);
            prompt.setGravity(Gravity.CENTER);
            prompt.setPadding(0, dp(18), 0, dp(6));
            cardBox.addView(prompt, new LinearLayout.LayoutParams(-1, -2));
            cardBox.setOnClickListener(v -> {
                cardRevealed = true;
                render();
            });
        } else {
            TextView meaning = new TextView(this);
            meaning.setText(card.meaning());
            meaning.setTextSize(24);
            meaning.setTextColor(Color.rgb(29, 47, 44));
            meaning.setGravity(Gravity.CENTER);
            meaning.setPadding(0, dp(18), 0, dp(18));
            cardBox.addView(meaning, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            String[] labels = {"Не знаю", "Сомневаюсь", "Знаю"};
            for (int i = 0; i < labels.length; i++) {
                final int result = i;
                Button b = new Button(this);
                b.setText(labels[i]);
                b.setAllCaps(false);
                b.setMinHeight(dp(56));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
                if (i > 0) p.setMarginStart(dp(6));
                actions.addView(b, p);
                b.setOnClickListener(v -> {
                    store.review(card.id(), result);
                    if (result != 2) reviewIndex++;
                    cardRevealed = false;
                    render();
                });
            }
            cardBox.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        }

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
        if (restoring) { es.setText(prefs.getString("cardDraftSpanish", "")); ru.setText(prefs.getString("cardDraftMeaning", "")); }
        draftSpanish = es; draftMeaning = ru;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(draftKind == 0 ? "Новое слово" : "Новое выражение").setView(box).setNegativeButton("Отмена", (d, w) -> clearCardDraft()).setPositiveButton("Сохранить", null).create();
        cardDialog = dialog;
        dialog.setOnCancelListener(d -> clearCardDraft());
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            if (es.getText().toString().trim().isEmpty()) { es.setError("Введите испанский текст"); return; }
            if (ru.getText().toString().trim().isEmpty()) { ru.setError("Введите русский смысл"); return; }
            store.addCard(es.getText().toString().trim(), ru.getText().toString().trim(), draftKind); clearCardDraft(); dialog.dismiss(); render();
        })); dialog.show();
    }
    private void rememberCardDraft() {
        if (cardDialog != null && cardDialog.isShowing() && draftSpanish != null && draftMeaning != null) {
            prefs.edit().putBoolean("cardDraftOpen", true).putInt("cardDraftKind", draftKind)
                    .putString("cardDraftSpanish", draftSpanish.getText().toString())
                    .putString("cardDraftMeaning", draftMeaning.getText().toString()).commit();
        }
    }
    private void clearCardDraft() {
        prefs.edit().remove("cardDraftOpen").remove("cardDraftKind").remove("cardDraftSpanish").remove("cardDraftMeaning").commit();
        draftSpanish = null; draftMeaning = null;
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
        voice = text(player.voiceDescription, 14);
        sentence = text(player.current(), 27); sentence.setTextIsSelectable(true);
        playbackStatus = text("", 16);
        toggle = button("Слушать", () -> { if(player.state.phase == PlaybackState.Phase.SPEAKING || (!player.sentenceMode && player.state.phase == PlaybackState.Phase.WAITING)) player.pause(); else player.play(); });
        toggle.setTextSize(24); toggle.setMinHeight(dp(76));
        repeat = button("Повторить предложение", player::repeat);
        text("Пауза останавливает речь сразу. Продолжение начинает остановленное предложение заново.", 14);
        Switch mode = new Switch(this); mode.setText("По одному предложению"); mode.setTextSize(18); mode.setMinHeight(dp(56)); mode.setChecked(player.sentenceMode); content.addView(mode);
        mode.setOnCheckedChangeListener((v, checked) -> { player.pause(); player.sentenceMode = checked; remember(); updatePlayer(); });
        Button speedButton = button("Скорость произношения: " + player.speed + "×", () -> new AlertDialog.Builder(this).setTitle("Скорость произношения").setItems(new String[]{"0.6×", "0.75×", "0.85×", "1.0×", "1.2×"}, (d,w) -> { player.pause(); player.speed = new float[]{.6f,.75f,.85f,1f,1.2f}[w]; render(); }).show());
        button("Пауза между предложениями: " + player.pauseMs / 1000f + " с", () -> new AlertDialog.Builder(this).setTitle("Пауза в непрерывном режиме").setItems(new String[]{"0 с", "1 с", "1.5 с", "3 с", "5 с"}, (d,w) -> { player.pause(); player.pauseMs = new int[]{0,1000,1500,3000,5000}[w]; render(); }).show());
        text("В режиме по одному предложению ожидание не ограничено: нажмите «Продолжить».",14);
        button("Читать весь рассказ", () -> { player.pause(); new AlertDialog.Builder(this).setTitle("Текст рассказа").setMessage(String.join("\n\n", Sentences.split(findBody()))).setPositiveButton("Закрыть",null).show(); });
        button("← Библиотека", () -> navigate("stories"));
    }
    private String findBody() { for(Store.Story s : store.stories()) if(s.id() == storyId) return s.body(); return ""; }
    private void updatePlayer() {
        if (player == null || rendering) return;
        if (storyId >= 0 && player.state.count > 0) store.position(storyId, player.state.index);
        if (sentence == null) return;
        sentence.setText(player.current()); voice.setText(player.voiceDescription);
        boolean active = player.state.phase == PlaybackState.Phase.SPEAKING || (!player.sentenceMode && player.state.phase == PlaybackState.Phase.WAITING);
        toggle.setText(active ? "Ⅱ Пауза" : player.state.phase == PlaybackState.Phase.FINISHED ? "Слушать сначала" : "▶ Продолжить");
        toggle.setEnabled(player.ready()); repeat.setEnabled(player.ready());
        String status = switch(player.state.phase) { case SPEAKING -> "Звучит"; case WAITING -> player.sentenceMode ? "Нажмите «Продолжить»" : "Пауза между предложениями"; case FINISHED -> "Рассказ закончен"; case PAUSED -> "Остановлено"; };
        playbackStatus.setText((player.state.index + 1) + " / " + player.state.count + " • " + status + (player.error.isEmpty() ? "" : "\n" + player.error));
    }
    @Override public void onBackPressed() { if (screen.equals("player")) navigate("stories"); else super.onBackPressed(); }
    @Override protected void onResume() {
        super.onResume();
        AppUpdater.setForeground(true);
        if (prefs.getBoolean("cardDraftOpen", false) && (cardDialog == null || !cardDialog.isShowing())) addCard();
        AppUpdater.check(this);
        if (!BuildConfig.DEBUG && !getPackageManager().canRequestPackageInstalls() && !prefs.getBoolean("autoUpdatePromptShown", false)) {
            prefs.edit().putBoolean("autoUpdatePromptShown", true).apply();
            showAutoUpdates();
        }
    }
    @Override protected void onPause() { player.pause(); remember(); rememberCardDraft(); super.onPause(); }
    @Override protected void onStop() { super.onStop(); if (!isChangingConfigurations()) AppUpdater.setForeground(false); }
    @Override protected void onDestroy() { player.close(); store.close(); super.onDestroy(); }
}
