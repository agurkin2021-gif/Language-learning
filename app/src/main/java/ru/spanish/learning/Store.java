package ru.spanish.learning;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public final class Store extends SQLiteOpenHelper {
    public record Card(long id, String spanish, String meaning, int kind, int category, boolean mastered, int lastResult, int reviewCount) {}
    public record Story(long id, String title, String body, String source, int position) {}

    public Store(Context context) { this(context, "learning.db"); }
    Store(Context context, String databaseName) { super(context, databaseName, null, 3); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE cards(id INTEGER PRIMARY KEY, spanish TEXT NOT NULL, meaning TEXT NOT NULL, kind INTEGER NOT NULL CHECK(kind IN(0,1)), category INTEGER NOT NULL DEFAULT 0 CHECK(category IN(0,1,2,3)), mastered INTEGER NOT NULL DEFAULT 0 CHECK(mastered IN(0,1)), last_result INTEGER NOT NULL DEFAULT 0 CHECK(last_result IN(0,1,2)), review_count INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE stories(id INTEGER PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL, source TEXT NOT NULL, position INTEGER NOT NULL DEFAULT 0)");
        ContentValues v = new ContentValues();
        v.put("title", "Una tarde en la plaza — пример");
        v.put("body", "Lucía vive en una casa pequeña. Una tarde, sale a caminar. En la plaza ve a su amigo Tomás.\n—¿Cómo estás? —pregunta Lucía.\n—Muy bien. ¿Querés tomar un mate?\nLucía dice que sí. Los dos se sientan bajo un árbol. Tomás comparte el mate con ella. Hablan de su día y miran los pájaros. Después, Lucía vuelve a casa contenta.");
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
        DefaultCards.seed(db);
    }

    public List<Card> cards(boolean mastered, int kind) { return cards(mastered, kind, 0); }

    public List<Card> cards(boolean mastered, int kind, int category) {
        List<Card> result = new ArrayList<>();
        String where = category == 0 ? "mastered=? AND kind=?" : "mastered=? AND kind=? AND category=?";
        String[] args = category == 0
                ? new String[]{mastered ? "1" : "0", Integer.toString(kind)}
                : new String[]{mastered ? "1" : "0", Integer.toString(kind), Integer.toString(category)};
        String[] columns = {"id","spanish","meaning","kind","category","mastered","last_result","review_count"};
        try (Cursor c = getReadableDatabase().query("cards", columns, where, args, null, null, "id ASC")) {
            while (c.moveToNext()) {
                result.add(new Card(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4), c.getInt(5) == 1, c.getInt(6), c.getInt(7)));
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
        ContentValues v = new ContentValues();
        v.put("spanish", spanish);
        v.put("meaning", meaning);
        v.put("kind", kind);
        v.put("category", 0);
        getWritableDatabase().insertOrThrow("cards", null, v);
    }

    public void review(long id, int result) {
        if (result < 0 || result > 2) throw new IllegalArgumentException("result");
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE cards SET last_result=?, review_count=review_count+1, mastered=? WHERE id=?",
                new Object[]{result, result == 2 ? 1 : 0, id});
    }

    public void setMastered(long id, boolean mastered) {
        ContentValues v = new ContentValues();
        v.put("mastered", mastered ? 1 : 0);
        if (!mastered) v.put("last_result", 0);
        getWritableDatabase().update("cards", v, "id=?", new String[]{Long.toString(id)});
    }

    public List<Story> stories() {
        List<Story> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("stories", null, null, null, null, null, "id DESC")) {
            while (c.moveToNext()) result.add(new Story(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4)));
        }
        return result;
    }

    public long addStory(String title, String body, String source) {
        if (title.trim().isEmpty() || Sentences.split(body).isEmpty()) throw new IllegalArgumentException("Пустой рассказ");
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("body", body);
        v.put("source", source);
        return getWritableDatabase().insertOrThrow("stories", null, v);
    }

    public void position(long id, int position) {
        ContentValues v = new ContentValues();
        v.put("position", position);
        getWritableDatabase().update("stories", v, "id=?", new String[]{Long.toString(id)});
    }
}
