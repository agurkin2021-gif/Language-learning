package ru.spanish.learning;

/** Complete bilingual text for Android's share sheet; does not modify stored stories. */
final class StoryShareText {
    private StoryShareText() {}

    static String compose(String title, String original, String russian) {
        String safeTitle = title == null ? "" : title.trim();
        String safeOriginal = original == null ? "" : original.trim();
        String safeRussian = russian == null ? "" : russian.trim();
        StringBuilder result = new StringBuilder();
        if (!safeTitle.isEmpty()) result.append(safeTitle);
        if (!safeOriginal.isEmpty()) {
            if (result.length() > 0) result.append("\n\n");
            result.append(safeOriginal);
        }
        if (!safeRussian.isEmpty()) {
            if (result.length() > 0) result.append("\n\n");
            result.append("Перевод:\n").append(safeRussian);
        }
        return result.toString();
    }
}
