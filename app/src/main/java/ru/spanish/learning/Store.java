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
    private final boolean english;
    private final String profileId;
    public record Card(long id, String spanish, String meaning, int kind, int category, boolean mastered, int lastResult, int reviewCount, long nextDue) {}
    public record Story(long id, String title, String body, String translation, String source, int position) {}
    public enum StoryWordSave { ADDED, ALREADY_STUDYING, RESTORED }

    public Store(Context context) { this(context, "learning.db", false, StudyProfile.PAPA); }
    Store(Context context, String databaseName) { this(context, databaseName, false, StudyProfile.PAPA); }
    static Store english(Context context) { return new Store(context, "learning_en.db", true, StudyProfile.PAPA); }
    static Store englishForTest(Context context, String databaseName) {
        return new Store(context, databaseName, true, StudyProfile.PAPA);
    }
    static Store forProfile(Context context, String id, boolean english) {
        String profile = StudyProfile.normalized(id);
        return new Store(context, StudyProfile.databaseName(profile, english), english, profile);
    }
    static Store forProfileTest(Context context, String dbName, String id, boolean english) {
        return new Store(context, dbName, english, StudyProfile.normalized(id));
    }
    private Store(Context context, String databaseName, boolean english, String profileId) {
        super(context, databaseName, null, 10);
        this.context = context.getApplicationContext();
        this.english = english;
        this.profileId = profileId;
    }
    boolean isEnglish() { return english; }
    String profileId() { return profileId; }

    private void changed() {
        new BackupManager(context).dataChanged();
        // Sync only a private, explicitly configured snapshot; never modify SQLite from the cloud.
        PrivateVocabularySync.schedule(context);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE cards(id INTEGER PRIMARY KEY, spanish TEXT NOT NULL, meaning TEXT NOT NULL, kind INTEGER NOT NULL CHECK(kind IN(0,1)), category INTEGER NOT NULL DEFAULT 0 CHECK(category IN(0,1,2,3)), mastered INTEGER NOT NULL DEFAULT 0 CHECK(mastered IN(0,1)), last_result INTEGER NOT NULL DEFAULT 0 CHECK(last_result IN(0,1,2)), review_count INTEGER NOT NULL DEFAULT 0, next_due INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE stories(id INTEGER PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL, translation TEXT NOT NULL DEFAULT '', source TEXT NOT NULL, position INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE remote_items(external_id TEXT PRIMARY KEY NOT NULL, item_type TEXT NOT NULL, local_id INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE app_state(key TEXT PRIMARY KEY NOT NULL, value INTEGER NOT NULL)");
        if (english || !StudyProfile.isPapa(profileId)) {
            // Mother and Dima start completely empty in both languages.
            // Papa's existing SQLite databases and seed are never migrated or overwritten.
            ContentValues state = new ContentValues();
            state.put("key", "shared_content_disabled");
            state.put("value", 1);
            db.insertOrThrow("app_state", null, state);
            // A0-A1 starter words are seeded ONLY for a new Mama/Dima Spanish DB.
            // Their English DBs remain empty; Papa keeps the old exact data.
            if (!english) {
                BeginnerCards.seed(db);
                setAppFlag(db, "beginner_700_seeded");
                FamilyStory.seed(db);
                if (StudyProfile.DIMA.equals(profileId)) {
                    DimaExtraCards.seed(db);
                    setAppFlag(db, "dima_extra_300_seeded");
                }
            }
            return;
        }
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
        if (oldVersion < 7) {
            db.execSQL("CREATE TABLE IF NOT EXISTS app_state(key TEXT PRIMARY KEY NOT NULL, value INTEGER NOT NULL)");
        }
        // Existing installations already have their initial cards. Future schema
        // upgrades must never restore words that a user deliberately deleted.
        if (oldVersion < 6 && !english && StudyProfile.isPapa(profileId)) DefaultCards.seed(db);
        if (oldVersion < 8 && !english && !StudyProfile.isPapa(profileId)) {
            // Users on the previous empty family profile release receive the pack
            // exactly once. An explicit database reset must NOT refill deleted data.
            if (!appFlag(db, "beginner_700_seeded") && !appFlag(db, "personal_story_reset"))
                BeginnerCards.seed(db);
            setAppFlag(db, "beginner_700_seeded");
        }
        if (oldVersion < 9 && !english && !StudyProfile.isPapa(profileId)) {
            // Install the family story on existing Mama/Dima profiles once,
            // without overwriting user stories or restoring a deleted story.
            FamilyStory.seed(db);
        }
        if (oldVersion < 10) upgradeDimaCards(db);
    }

    // Existing Dima profiles receive the pack once. Never reseed after a deliberate reset.
    private void upgradeDimaCards(SQLiteDatabase db) {
        if (english || !StudyProfile.DIMA.equals(profileId)) return;
        if (!appFlag(db, "dima_extra_300_seeded") && !appFlag(db, "personal_story_reset"))
            DimaExtraCards.seed(db);
        setAppFlag(db, "dima_extra_300_seeded");
    }

    private static boolean appFlag(SQLiteDatabase db, String key) {
        try (Cursor cursor = db.rawQuery("SELECT value FROM app_state WHERE key=?",
                new String[]{key})) {
            return cursor.moveToFirst() && cursor.getInt(0) != 0;
        }
    }

    private static void setAppFlag(SQLiteDatabase db, String key) {
        ContentValues state = new ContentValues();
        state.put("key", key);
        state.put("value", 1);
        db.insertWithOnConflict("app_state", null, state, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private boolean sharedContentDisabled(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("SELECT value FROM app_state WHERE key=?",
                new String[]{"shared_content_disabled"})) {
            return cursor.moveToFirst() && cursor.getInt(0) != 0;
        }
    }

    public boolean sharedContentDisabled() {
        return sharedContentDisabled(getReadableDatabase());
    }

    /** Clear learning data without deleting the database file, which would re-seed starter cards. */
    public void resetLearningData() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("cards", null, null);
            db.delete("stories", null, null);
            db.delete("remote_items", null, null);

            // Keep this phone independent: the shared GitHub feed must not
            // silently repopulate it after an explicit full reset.
            ContentValues state = new ContentValues();
            state.put("key", "shared_content_disabled");
            state.put("value", 1);
            db.insertWithOnConflict("app_state", null, state, SQLiteDatabase.CONFLICT_REPLACE);
            // A deliberate reset must also block private personal feeds, in both languages.
            ContentValues personal = new ContentValues();
            personal.put("key", "personal_story_reset");
            personal.put("value", 1);
            db.insertWithOnConflict("app_state", null, personal, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        changed();
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

    /** Recheck mastery before saving newly generated pictures. */
    boolean isInStudy(long id) {
        try (Cursor c = getReadableDatabase().query("cards", new String[]{"id"},
                "id=? AND mastered=0", new String[]{Long.toString(id)},
                null, null, null, "1")) {
            return c.moveToFirst();
        }
    }

    public List<Card> mastered() {
        List<Card> result = new ArrayList<>(cards(true, 0));
        result.addAll(cards(true, 1));
        return result;
    }

    /** Learned WORDS for this local language database; expressions are excluded. */
    public int learnedWordCount() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM cards WHERE mastered=1 AND kind=0", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** Complete local dictionary, independent of mastery status and review scheduling. */
    public List<Card> vocabulary() {
        List<Card> result = new ArrayList<>();
        String[] columns = {"id","spanish","meaning","kind","category","mastered","last_result","review_count","next_due"};
        try (Cursor c = getReadableDatabase().query("cards", columns, null, null, null, null, "id ASC")) {
            while (c.moveToNext()) {
                result.add(new Card(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4), c.getInt(5) == 1, c.getInt(6), c.getInt(7), c.getLong(8)));
            }
        }
        return result;
    }

    public void addCard(String spanish, String meaning, int kind) {
        addCard(spanish, meaning, kind, kind == 0 ? 1 : 0);
    }

    public void addCard(String spanish, String meaning, int kind, int category) {
        if (kind == 0 && (category < 1 || category > 3)) throw new IllegalArgumentException("category");
        if (kind == 1 && !english) SpanishPhrasePolicy.requireConversationalForm(spanish);
        if (kind == 1) category = 0;
        ContentValues v = new ContentValues();
        v.put("spanish", spanish);
        v.put("meaning", meaning);
        v.put("kind", kind);
        v.put("category", category);
        getWritableDatabase().insertOrThrow("cards", null, v);
        changed();
    }

    /** Keep the exact inflected form, including irregular verbs, as a separate learning card. */
    public StoryWordSave addStoryWord(String spanish, String meaning, int category) {
        if (spanish == null || spanish.trim().isEmpty() || meaning == null || meaning.trim().isEmpty()
                || category < 0 || category > 3) throw new IllegalArgumentException("Неверная карточка");
        StoryVocabulary.requireVerified(spanish, meaning);
        String printed = spanish.trim();
        for (Card card : vocabulary()) {
            if (card.kind() != 0 || !card.spanish().equalsIgnoreCase(printed)) continue;
            if (card.mastered()) {
                setMastered(card.id(), false);
                return StoryWordSave.RESTORED;
            }
            return StoryWordSave.ALREADY_STUDYING;
        }
        ContentValues values = new ContentValues();
        values.put("spanish", printed);
        values.put("meaning", meaning.trim());
        values.put("kind", 0);
        values.put("category", category); // 0 means not classified; shown under "Все слова".
        getWritableDatabase().insertOrThrow("cards", null, values);
        changed();
        return StoryWordSave.ADDED;
    }

    /** Duplicate check includes the hidden mastered archive; comparisons ignore case. */
    boolean containsCard(String text, int kind) {
        String candidate = text == null ? "" : text.trim();
        for (Card card : vocabulary()) {
            if (card.kind() == kind && card.spanish().trim().equalsIgnoreCase(candidate))
                return true;
        }
        return false;
    }

    public boolean addCardIfMissing(String spanish, String meaning, int kind, int category) {
        String cleanSpanish = spanish == null ? "" : spanish.trim();
        String cleanMeaning = meaning == null ? "" : meaning.trim();
        if (cleanSpanish.isEmpty() || cleanMeaning.isEmpty()) throw new IllegalArgumentException("Пустая карточка");
        if (kind == 1 && !english) SpanishPhrasePolicy.requireConversationalForm(cleanSpanish);
        if (kind == 0 && (category < 1 || category > 3)) throw new IllegalArgumentException("category");
        if (kind == 1) category = 0;
        if (containsCard(cleanSpanish, kind)) return false;
        addCard(cleanSpanish, cleanMeaning, kind, category);
        return true;
    }

    /** Permanently remove a card, including from the mastered archive.
     * Remote import receipts remain as tombstones so a future sync cannot restore it.
     */
    public boolean deleteCard(long id) {
        if (id <= 0) return false;
        int removed = getWritableDatabase().delete("cards", "id=?",
                new String[]{Long.toString(id)});
        if (removed > 0) changed();
        return removed > 0;
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
        if (kind == 1 && !english) SpanishPhrasePolicy.requireConversationalForm(spanish);
        if (spanish == null || spanish.trim().isEmpty() || meaning == null || meaning.trim().isEmpty())
            throw new IllegalArgumentException("Пустая карточка");
        if (kind < 0 || kind > 1) throw new IllegalArgumentException("kind");
        if (kind == 0 && (category < 1 || category > 3)) throw new IllegalArgumentException("category");
        if (kind == 1) category = 0;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (sharedContentDisabled(db) || remoteImported(db, externalId)) {
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

    public boolean importPrivateCard(String externalId, String spanish, String meaning, int kind, int category) {
        if (externalId == null || externalId.isBlank() || spanish == null || spanish.isBlank()
                || meaning == null || meaning.isBlank()) throw new IllegalArgumentException("card");
        if (kind != 0 && kind != 1) throw new IllegalArgumentException("kind");
        if (kind == 0 && (category < 1 || category > 3)) throw new IllegalArgumentException("category");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        boolean inserted = false;
        try {
            if (personalStoriesDisabled(db) || remoteImported(db, externalId)) {
                db.setTransactionSuccessful();
                return false;
            }
            long localId = 0;
            try (Cursor c = db.rawQuery("SELECT id FROM cards WHERE kind=? AND LOWER(TRIM(spanish))=LOWER(?) LIMIT 1",
                    new String[]{Integer.toString(kind), spanish.trim()})) {
                if (c.moveToFirst()) localId = c.getLong(0);
            }
            if (localId == 0) {
                ContentValues v = new ContentValues();
                v.put("spanish", spanish.trim()); v.put("meaning", meaning.trim());
                v.put("kind", kind); v.put("category", kind == 1 ? 0 : category);
                localId = db.insertOrThrow("cards", null, v);
                inserted = true;
            }
            markRemoteImported(db, externalId, kind == 1 ? "phrase" : "word", localId);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        if (inserted) changed();
        return inserted;
    }

    /** English is shared-content-disabled by default, but private stories are allowed
     * until the user deliberately resets that local database.
     */
    public boolean personalStoriesDisabled() {
        return personalStoriesDisabled(getReadableDatabase());
    }

    private boolean personalStoriesDisabled(SQLiteDatabase db) {
        if (!english && StudyProfile.isPapa(profileId) && sharedContentDisabled(db)) return true;
        try (Cursor c = db.rawQuery("SELECT value FROM app_state WHERE key=?",
                new String[]{"personal_story_reset"})) {
            return c.moveToFirst() && c.getInt(0) != 0;
        }
    }

    public boolean importPrivateStory(String externalId, String title, String body, String translation) {
        return importStoryInternal(externalId, title, body, translation, true);
    }

    public boolean importRemoteStory(String externalId, String title, String body, String translation) {
        return importStoryInternal(externalId, title, body, translation, false);
    }

    private boolean importStoryInternal(String externalId, String title, String body,
                                        String translation, boolean privateFeed) {
        if (externalId == null || externalId.trim().isEmpty()) throw new IllegalArgumentException("externalId");
        if (title == null || title.trim().isEmpty() || body == null || Sentences.split(body).isEmpty())
            throw new IllegalArgumentException("Пустой рассказ");
        String cleanTranslation = translation == null ? "" : translation.trim();
        if (!cleanTranslation.isEmpty() && Sentences.split(body).size() != Sentences.split(cleanTranslation).size())
            throw new IllegalArgumentException("Количество предложений в переводе не совпадает");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if ((privateFeed ? personalStoriesDisabled(db) : sharedContentDisabled(db))
                    || remoteImported(db, externalId)) {
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

    /** Retain remote_items tombstones to prevent restoration by the next sync. */
    public boolean deleteStory(long id) {
        if (id <= 0) return false;
        int deleted = getWritableDatabase().delete("stories", "id=?", new String[]{Long.toString(id)});
        if (deleted > 0) changed();
        return deleted > 0;
    }

    public void position(long id, int position) {
        ContentValues v = new ContentValues();
        v.put("position", position);
        getWritableDatabase().update("stories", v, "id=?", new String[]{Long.toString(id)});
        changed();
    }
}
