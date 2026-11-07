package ru.spanish.learning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** User-owned, non-destructive pause marks, indexed by sentence and original UTF-16 offset. */
final class ManualStoryPauses {
    static final String SYMBOL = " ⏸ ";
    private ManualStoryPauses() {}

    static int nearestGap(String source, int offset) {
        if (source == null || source.isEmpty()) return -1;
        int nearest = -1;
        int distance = Integer.MAX_VALUE;
        for (int i = 1; i < source.length(); i++) {
            if (!Character.isWhitespace(source.charAt(i))
                    || Character.isWhitespace(source.charAt(i - 1))) continue;
            int next = i;
            while (next < source.length() && Character.isWhitespace(source.charAt(next))) next++;
            if (next == source.length()) continue;
            if (!Character.isLetterOrDigit(source.charAt(i - 1))
                    && !isPunctuation(source.charAt(i - 1))) continue;
            if (!Character.isLetterOrDigit(source.charAt(next))
                    && !Character.isLetter(source.charAt(next))) continue;
            int gapDistance = offset < i ? i - offset : offset > next ? offset - next : 0;
            if (gapDistance < distance) { distance = gapDistance; nearest = i; }
        }
        return nearest;
    }

    private static boolean isPunctuation(char ch) {
        return ",.;:!?»”'".indexOf(ch) >= 0;
    }

    static void toggle(Map<Integer, List<Integer>> marks, int sentence, int gap) {
        if (gap < 1 || sentence < 0) return;
        List<Integer> updated = new ArrayList<>(marks.getOrDefault(sentence, List.of()));
        if (updated.contains(gap)) updated.remove(Integer.valueOf(gap));
        else updated.add(gap);
        Collections.sort(updated);
        if (updated.isEmpty()) marks.remove(sentence);
        else marks.put(sentence, updated);
    }

    static String encode(Map<Integer, List<Integer>> marks) {
        List<String> values = new ArrayList<>();
        List<Integer> sentences = new ArrayList<>(marks.keySet());
        Collections.sort(sentences);
        for (int sentence : sentences)
            for (int gap : marks.get(sentence))
                if (sentence >= 0 && gap >= 1) values.add(sentence + ":" + gap);
        return String.join(",", values);
    }

    static Map<Integer, List<Integer>> decode(String value, List<String> sentences) {
        Map<Integer, List<Integer>> result = new HashMap<>();
        if (value == null || value.isBlank() || sentences == null) return result;
        for (String part : value.split(",")) {
            String[] fields = part.split(":");
            if (fields.length != 2) continue;
            try {
                int sentence = Integer.parseInt(fields[0]);
                int gap = Integer.parseInt(fields[1]);
                if (sentence < 0 || sentence >= sentences.size()) continue;
                String text = sentences.get(sentence);
                if (gap < 1 || gap >= text.length() || !Character.isWhitespace(text.charAt(gap))) continue;
                List<Integer> places = result.computeIfAbsent(sentence, k -> new ArrayList<>());
                if (!places.contains(gap)) places.add(gap);
            } catch (NumberFormatException ignored) {}
        }
        for (List<Integer> gaps : result.values()) Collections.sort(gaps);
        return result;
    }

    static List<Integer> forSentence(Map<Integer, List<Integer>> marks, int sentence) {
        return marks.getOrDefault(sentence, List.of());
    }

    /** Display text includes bright ⏸ symbols; original words and audio content never change. */
    static String display(String original, List<Integer> gaps) {
        StringBuilder result = new StringBuilder(original);
        for (int i = gaps.size() - 1; i >= 0; i--) {
            int index = gaps.get(i);
            if (index > 0 && index < original.length()) result.insert(index, SYMBOL);
        }
        return result.toString();
    }

    /** Convert a touch offset in marked text back into the unmodified sentence. */
    static int originalOffset(int displayed, List<Integer> gaps) {
        int shift = 0;
        for (int gap : gaps) {
            int left = gap + shift;
            int right = left + SYMBOL.length();
            if (displayed < left) break;
            if (displayed < right) return gap;
            shift += SYMBOL.length();
        }
        return Math.max(0, displayed - shift);
    }

    record Piece(String text, boolean slow, int sourceStart, boolean silence) {}
    /** Combine per-word-rate pieces with explicit silence at user-defined boundaries. */
    static List<Piece> pieces(String original, List<Integer> gaps, boolean slowWords) {
        List<Piece> output = new ArrayList<>();
        int previous = 0;
        for (int gap : gaps) {
            if (gap <= previous || gap >= original.length()
                    || !Character.isWhitespace(original.charAt(gap))) continue;
            addWords(output, original.substring(previous, gap), previous, slowWords);
            output.add(new Piece("", false, gap, true));
            previous = gap;
        }
        addWords(output, original.substring(previous), previous, slowWords);
        return output;
    }

    private static void addWords(List<Piece> output, String text, int offset, boolean slowWords) {
        if (text.isEmpty()) return;
        if (!slowWords) {
            output.add(new Piece(text, false, offset, false));
            return;
        }
        for (ArticleSlowSpeech.Part part : ArticleSlowSpeech.split(text)) {
            if (!part.text().trim().isEmpty())
                output.add(new Piece(part.text(), part.slow(), offset + part.start(), false));
        }
    }
}
