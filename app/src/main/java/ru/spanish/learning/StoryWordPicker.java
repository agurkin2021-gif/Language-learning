package ru.spanish.learning;

import java.text.Normalizer;

/** Identifies exactly one orthographic Spanish word; no Android selection handles. */
public final class StoryWordPicker {
    private StoryWordPicker() {}

    public record Match(String word, int start, int end) {}

    public static Match at(String text, int offset) {
        if (text == null || offset < 0 || offset >= text.length() || !partAt(text, offset)) return null;
        int start = offset;
        int end = offset + 1;
        while (start > 0 && partAt(text, start - 1)) start--;
        while (end < text.length() && partAt(text, end)) end++;
        String word = Normalizer.normalize(text.substring(start, end), Normalizer.Form.NFC);
        if (word.isEmpty() || word.length() > 80) return null;
        return new Match(word, start, end);
    }

    private static boolean partAt(String text, int index) {
        char c = text.charAt(index);
        if (Character.isLetter(c) || Character.getType(c) == Character.NON_SPACING_MARK) return true;
        if (c != '-' && c != '\'' && c != '\u2019') return false;
        return index > 0 && index + 1 < text.length()
                && Character.isLetter(text.charAt(index - 1))
                && Character.isLetter(text.charAt(index + 1));
    }
}
