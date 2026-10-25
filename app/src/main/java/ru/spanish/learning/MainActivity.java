package ru.spanish.learning;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.graphics.Color;
import android.os.Bundle;
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
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        store = new Store(this); prefs = getSharedPreferences("ui", MODE_PRIVATE);
        player = new SpeechPlayer(this, this::updatePlayer);
        player.speed = prefs.getFloat("speed", .85f); player.pauseMs = prefs.getInt("pause", 1500); player.sentenceMode = prefs.getBoolean("sentenceMode", true);
        screen = prefs.getString("screen", "cards"); kind = prefs.getInt("kind", 0); archive = prefs.getBoolean("archive", false);
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
                .putFloat("speed", player.speed).putInt("pause", player.pauseMs).putBoolean("sentenceMode", player.sentenceMode).apply();
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
        update.setOnClickListener(v -> downloadUpdate());
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
                .setPositiveButton("Обновить", (dialog, which) -> downloadUpdate()).show();
    }
    private void downloadUpdate() {
        player.pause();
        if (BuildConfig.DEBUG) {
            new AlertDialog.Builder(this).setTitle("Служебная сборка")
                    .setMessage("Эта сборка предназначена для проверки. Первую версию с постоянной подписью нужно установить отдельно; обновление поверх старой тестовой сборки может быть несовместимо.")
                    .setPositiveButton("Понятно", null).show();
            return;
        }
        openBrowser("https://github.com/agurkin2021-gif/Language-learning/raw/refs/heads/phone-builds/SpanishLearning.apk");
    }
    private void openBrowser(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (ActivityNotFoundException e) {
            Toast.makeText(this, "На телефоне нет браузера для открытия GitHub", Toast.LENGTH_LONG).show();
        }
    }
    private void cards() {
        text(archive ? "Освоенное" : "Мои карточки", 28);
        text("В архив попадает только то, что вы отметили кнопкой «Знаю».", 16);
        button(kind == 0 ? "Слова ▾" : "Устойчивые выражения ▾", () -> new AlertDialog.Builder(this).setTitle("Тип карточек").setItems(new String[]{"Слова", "Устойчивые выражения"}, (d,w) -> { kind = w; render(); }).show());
        button(archive ? "← Вернуться к изучению" : "Освоенное", () -> { archive = !archive; render(); });
        if (!archive) button("＋ Добавить карточку", this::addCard);
        List<Store.Card> cards = store.cards(archive, kind);
        text(cards.isEmpty() ? "Здесь пока нет карточек" : "Карточек: " + cards.size(), 16);
        for (Store.Card card : cards) {
            text(card.spanish(), 25); text(card.meaning(), 18);
            button(archive ? "Вернуть к изучению" : "Знаю", () -> { store.setMastered(card.id(), !archive); render(); });
            View line = new View(this); line.setBackgroundColor(0xFFD7DDD6); content.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        }
    }
    private EditText field(LinearLayout box, String hint, int max, boolean multiline) {
        EditText e = new EditText(this); e.setHint(hint); e.setTextSize(18); e.setSingleLine(!multiline); e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)}); if(multiline) e.setMinLines(3); box.addView(e); return e;
    }
    private LinearLayout form() { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20),dp(8),dp(20),dp(8)); return box; }
    private void addCard() {
        LinearLayout box = form(); EditText es = field(box, "По-испански", 500, false); EditText ru = field(box, "Русский смысл", 1000, true);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(kind == 0 ? "Новое слово" : "Новое выражение").setView(box).setNegativeButton("Отмена", null).setPositiveButton("Сохранить", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            if (es.getText().toString().trim().isEmpty()) { es.setError("Введите испанский текст"); return; }
            if (ru.getText().toString().trim().isEmpty()) { ru.setError("Введите русский смысл"); return; }
            store.addCard(es.getText().toString().trim(), ru.getText().toString().trim(), kind); dialog.dismiss(); render();
        })); dialog.show();
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
    @Override protected void onPause() { player.pause(); remember(); super.onPause(); }
    @Override protected void onDestroy() { player.close(); store.close(); super.onDestroy(); }
}
