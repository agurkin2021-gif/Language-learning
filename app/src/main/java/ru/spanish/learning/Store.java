package ru.spanish.learning;

import android.app.backup.BackupManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public final class Store extends SQLiteOpenHelper {
    private final Context context;
    public record Card(long id, String spanish, String meaning, int kind, int category, boolean mastered, int lastResult, int reviewCount, long nextDue) {}
    public record Story(long id, String title, String body, String translation, String source, int position) {}

    public Store(Context context) { this(context, "learning.db"); }
    Store(Context context, String databaseName) {
        super(context, databaseName, null, 6);
        this.context = context.getApplicationContext();
    }

    private void changed() { new BackupManager(context).dataChanged(); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE cards(id INTEGER PRIMARY KEY, spanish TEXT NOT NULL, meaning TEXT NOT NULL, kind INTEGER NOT NULL CHECK(kind IN(0,1)), category INTEGER NOT NULL DEFAULT 0 CHECK(category IN(0,1,2,3)), mastered INTEGER NOT NULL DEFAULT 0 CHECK(mastered IN(0,1)), last_result INTEGER NOT NULL DEFAULT 0 CHECK(last_result IN(0,1,2)), review_count INTEGER NOT NULL DEFAULT 0, next_due INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE stories(id INTEGER PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL, translation TEXT NOT NULL DEFAULT '', source TEXT NOT NULL, position INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE remote_items(external_id TEXT PRIMARY KEY NOT NULL, item_type TEXT NOT NULL, local_id INTEGER NOT NULL)");
        ContentValues v = new ContentValues();
        v.put("title", "Una tarde en la plaza — пример");
        v.put("body", "Lucía vive en una casa pequeña. Una tarde, sale a caminar. En la plaza ve a su amigo Tomás.\n—¿Cómo estás? —pregunta Lucía.\n—Muy bien. ¿Querés tomar un mate?\nLucía dice que sí. Los dos se sientan bajo un árbol. Tomás comparte el mate con ella. Hablan de su día y miran los pájaros. Después, Lucía vuelve a casa contenta.");
        v.put("translation", "Лусия живёт в маленьком доме. Однажды вечером она выходит погулять. На площади она видит своего друга Томаса.\n—Как ты? —спрашивает Лусия.\n—Очень хорошо. Хочешь выпить мате?\nЛусия говорит, что да. Они вдвоём садятся под дерево. Томас делится с ней мате. Они разговаривают о своём дне и смотрят на птиц. Потом Лусия довольная возвращается домой.");
        v.put("source", "Встроенный пример • написан заранее, не ИИ-генерация по архиву");
        db.insertOrThrow("stories", null, v);
        DefaultCards.seed(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE cards ADD COLUMN category INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE cards ADD COLUMN last_result INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE cards ADD COLUMN review_count INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE cards ADD COLUMN next_due INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE stories ADD COLUMN translation TEXT NOT NULL DEFAULT ''");
            ContentValues story = new ContentValues();
            story.put("translation", "Лусия живёт в маленьком доме. Однажды вечером она выходит погулять. На площади она видит своего друга Томаса.\n—Как ты? —спрашивает Лусия.\n—Очень хорошо. Хочешь выпить мате?\nЛусия говорит, что да. Они вдвоём садятся под дерево. Томас делится с ней мате. Они разговаривают о своём дне и смотрят на птиц. Потом Лусия довольная возвращается домой.");
            db.update("stories", story, "title=?", new String[]{"Una tarde en la plaza — пример"});
        }
        if (oldVersion < 6) {
            db.execSQL("CREATE TABLE IF NOT EXISTS remote_items(external_id TEXT PRIMARY KEY NOT NULL, item_type TEXT NOT NULL, local_id INTEGER NOT NULL)");
        }
        DefaultCards.seed(db);
    }

    public List<Card> cards(boolean mastered, int kind) { return cards(mastered, kind, 0); }

    public List<Card> cards(boolean mastered, int kind, int category) {
        List<Card> result = new ArrayList<>();
        String where = category == 0 ? "mastered=? AND kind=?" : "mastered=? AND kind=? AND category=?";
        String[] args = category == 0
                ? new String[]{mastered ? "1" : "0", Integer.toString(kind)}
                : new String[]{mastered ? "1" : "0", Integer.toString(kind), Integer.toString(category)};
        String[] columns = {"id","spanish","meaning","kind","category","mastered","last_result","review_count","next_due"};
        try (Cursor c = getReadableDatabase().query("cards", columns, where, args, null, null, "id ASC")) {
            while (c.moveToNext()) {
                result.add(new Card(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4), c.getInt(5) == 1, c.getInt(6), c.getInt(7), c.getLong(8)));
            }
        }
        return result;
    }

    public List<Card> mastered() {
        List<Card> result = new ArrayList<>(cards(true, 0));
        result.addAll(cards(true, 1));
        return result;
    }

    public void addCard(String spanish, String meaning, int kind) {
        addCard(spanish, meaning, kind, kind == 0 ? 1 : 0);
    }

    public void addCard(String spanish, String meaning, int kind, int category) {
        if (kind == 0 && (category < 1 || category > 3)) throw new IllegalArgumentException("category");
        if (kind == 1) category = 0;
        ContentValues v = new ContentValues();
        v.put("spanish", spanish);
        v.put("meaning", meaning);
        v.put("kind", kind);
        v.put("category", category);
        getWritableDatabase().insertOrThrow("cards", null, v);
        changed();
    }

    public boolean addCardIfMissing(String spanish, String meaning, int kind, int category) {
        String cleanSpanish = spanish == null ? "" : spanish.trim();
        String cleanMeaning = meaning == null ? "" : meaning.trim();
        if (cleanSpanish.isEmpty() || cleanMeaning.isEmpty()) throw new IllegalArgumentException("Пустая карточка");
        if (kind == 0 && (category < 1 || category > 3)) throw new IllegalArgumentException("category");
        if (kind == 1) category = 0;
        try (Cursor c = getReadableDatabase().query("cards", new String[]{"id"},
                "spanish=? AND kind=?", new String[]{cleanSpanish, Integer.toString(kind)},
                null, null, null, "1")) {
            if (c.moveToFirst()) return false;
        }
        addCard(cleanSpanish, cleanMeaning, kind, category);
        return true;
    }

    public void review(long id, int result) {
        review(id, result, 0);
    }

    public void review(long id, int result, long nextDue) {
        if (result < 0 || result > 2) throw new IllegalArgumentException("result");
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE cards SET last_result=?, review_count=review_count+1, mastered=?, next_due=? WHERE id=?",
                new Object[]{result, result == 2 ? 1 : 0, result == 2 ? 0 : Math.max(0, nextDue), id});
        changed();
    }

    public List<Card> cardsForReview(int kind, int category, long step) {
        List<Card> result = new ArrayList<>();
        String categoryClause = category == 0 ? "" : " AND category=?";
        String where = "mastered=0 AND kind=?" + categoryClause + " AND (next_due=0 OR next_due<=?)";
        String[] args = category == 0
                ? new String[]{Integer.toString(kind), Long.toString(step)}
                : new String[]{Integer.toString(kind), Integer.toString(category), Long.toString(step)};
        String[] columns = {"id","spanish","meaning","kind","category","mastered","last_result","review_count","next_due"};
        try (Cursor c = getReadableDatabase().query("cards", columns, where, args, null, null,
                "CASE WHEN next_due>0 THEN 0 ELSE 1 END, next_due ASC, id ASC")) {
            while (c.moveToNext()) result.add(new Card(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4), c.getInt(5) == 1, c.getInt(6), c.getInt(7), c.getLong(8)));
        }
        if (!result.isEmpty()) return result;

        String fallbackWhere = "mastered=0 AND kind=?" + categoryClause;
        String[] fallbackArgs = category == 0
                ? new String[]{Integer.toString(kind)}
                : new String[]{Integer.toString(kind), Integer.toString(category)};
        try (Cursor c = getReadableDatabase().query("cards", columns, fallbackWhere, fallbackArgs, null, null,
                "CASE WHEN next_due=0 THEN 0 ELSE 1 END, next_due ASC, id ASC", "1")) {
            while (c.moveToNext()) result.add(new Card(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4), c.getInt(5) == 1, c.getInt(6), c.getInt(7), c.getLong(8)));
        }
        return result;
    }

    public void setMastered(long id, boolean mastered) {
        ContentValues v = new ContentValues();
        v.put("mastered", mastered ? 1 : 0);
        if (!mastered) {
            v.put("last_result", 0);
            v.put("next_due", 0);
        }
        getWritableDatabase().update("cards", v, "id=?", new String[]{Long.toString(id)});
        changed();
    }

    public List<Story> stories() {
        List<Story> result = new ArrayList<>();
        String[] columns = {"id", "title", "body", "translation", "source", "position"};
        try (Cursor c = getReadableDatabase().query("stories", columns, null, null, null, null, "id DESC")) {
            while (c.moveToNext()) result.add(new Story(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getInt(5)));
        }
        return result;
    }

    public long addStory(String title, String body, String source) {
        return addStory(title, body, "", source);
    }

    public long addStory(String title, String body, String translation, String source) {
        if (title.trim().isEmpty() || Sentences.split(body).isEmpty()) throw new IllegalArgumentException("Пустой рассказ");
        String cleanTranslation = translation == null ? "" : translation.trim();
        if (!cleanTranslation.isEmpty() && Sentences.split(body).size() != Sentences.split(cleanTranslation).size())
            throw new IllegalArgumentException("Количество предложений в переводе не совпадает");
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("body", body);
        v.put("translation", cleanTranslation);
        v.put("source", source);
        long id = getWritableDatabase().insertOrThrow("stories", null, v);
        changed();
        return id;
    }

    public boolean addStoryIfMissing(String title, String body, String translation, String source) {
        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.isEmpty()) throw new IllegalArgumentException("Пустой заголовок");
        try (Cursor c = getReadableDatabase().query("stories", new String[]{"id"},
                "title=?", new String[]{cleanTitle}, null, null, null, "1")) {
            if (c.moveToFirst()) return false;
        }
        addStory(cleanTitle, body, translation, source);
        return true;
    }

    private boolean remoteImported(SQLiteDatabase db, String externalId) {
        try (Cursor c = db.query("remote_items", new String[]{"external_id"}, "external_id=?",
                new String[]{externalId}, null, null, null, "1")) {
            return c.moveToFirst();
        }
    }

    private void markRemoteImported(SQLiteDatabase db, String externalId, String itemType, long localId) {
        ContentValues remote = new ContentValues();
        remote.put("external_id", externalId);
        remote.put("item_type", itemType);
        remote.put("local_id", localId);
        db.insertOrThrow("remote_items", null, remote);
    }

    public boolean importRemoteCard(String externalId, String spanish, String meaning, int kind, int category) {
        if (externalId == null || externalId.trim().isEmpty()) throw new IllegalArgumentException("externalId");
        if (spanish == null || spanish.trim().isEmpty() || meaning == null || meaning.trim().isEmpty())
            throw new IllegalArgumentException("Пустая карточка");
        if (kind < 0 || kind > 1) throw new IllegalArgumentException("kind");
        if (kind == 0 && (category < 1 || category > 3)) throw new IllegalArgumentException("category");
        if (kind == 1) category = 0;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (remoteImported(db, externalId)) {
                db.setTransactionSuccessful();
                return false;
            }
            ContentValues v = new ContentValues();
            v.put("spanish", spanish.trim());
            v.put("meaning", meaning.trim());
            v.put("kind", kind);
            v.put("category", category);
            long localId = db.insertOrThrow("cards", null, v);
            markRemoteImported(db, externalId, kind == 0 ? "word" : "phrase", localId);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        changed();
        return true;
    }

    public boolean importRemoteStory(String externalId, String title, String body, String translation) {
        if (externalId == null || externalId.trim().isEmpty()) throw new IllegalArgumentException("externalId");
        if (title == null || title.trim().isEmpty() || body == null || Sentences.split(body).isEmpty())
            throw new IllegalArgumentException("Пустой рассказ");
        String cleanTranslation = translation == null ? "" : translation.trim();
        if (!cleanTranslation.isEmpty() && Sentences.split(body).size() != Sentences.split(cleanTranslation).size())
            throw new IllegalArgumentException("Количество предложений в переводе не совпадает");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (remoteImported(db, externalId)) {
                db.setTransactionSuccessful();
                return false;
            }
            ContentValues v = new ContentValues();
            v.put("title", title.trim());
            v.put("body", body.trim());
            v.put("translation", cleanTranslation);
            v.put("source", "ChatGPT • синхронизировано");
            long localId = db.insertOrThrow("stories", null, v);
            markRemoteImported(db, externalId, "story", localId);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        changed();
        return true;
    }

    public void position(long id, int position) {
        ContentValues v = new ContentValues();
        v.put("position", position);
        getWritableDatabase().update("stories", v, "id=?", new String[]{Long.toString(id)});
        changed();
    }
}
