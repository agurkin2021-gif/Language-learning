package ru.spanish.learning;

import android.content.Context;

/** Fixed profiles. The legacy databases and preferences always belong to Papa. */
public final class StudyProfile {
    public static final String PAPA = "papa";
    public static final String MAMA = "mama";
    public static final String DIMA = "dima";
    public static final String[] IDS = {PAPA, MAMA, DIMA};
    public static final String[] LABELS = {"Папа", "Мама", "Дима"};

    private StudyProfile() {}

    public static String normalized(String value) {
        return MAMA.equals(value) ? MAMA : DIMA.equals(value) ? DIMA : PAPA;
    }

    public static String active(Context context) {
        return normalized(context.getSharedPreferences("ui", Context.MODE_PRIVATE)
                .getString("activeProfile", PAPA));
    }

    public static boolean isPapa(String id) { return PAPA.equals(normalized(id)); }

    public static String name(String id) {
        return switch (normalized(id)) {
            case MAMA -> "Мама";
            case DIMA -> "Дима";
            default -> "Папа";
        };
    }

    public static String databaseName(String id, boolean english) {
        String base = english ? "learning_en" : "learning";
        return base + (isPapa(id) ? "" : "_" + normalized(id)) + ".db";
    }

    public static String preferenceKey(String id, String language, String key) {
        String oldKey = "en".equals(language) ? "en_" + key : key;
        return isPapa(id) ? oldKey : normalized(id) + "_" + oldKey;
    }

    public static String languageKey(String id) {
        return isPapa(id) ? "studyLanguage" : normalized(id) + "_studyLanguage";
    }

    public static String imageDirectory(String id, boolean english) {
        String base = english ? "card-images-en" : "card-images";
        return base + (isPapa(id) ? "" : "-" + normalized(id));
    }

    public static String storyFeedPath(String id, String language) {
        return "content/" + (isPapa(id) ? "" : normalized(id) + "/")
                + "stories-" + language + ".json";
    }

    public static String vocabularyPath(String id, String language, String deviceId) {
        return "vocabulary/" + (isPapa(id) ? "" : normalized(id) + "/")
                + language + "-" + deviceId + ".json";
    }
}
