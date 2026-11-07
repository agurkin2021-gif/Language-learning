package ru.spanish.learning;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Isolates all Spanish one-, two-, and three-letter words in any story, plus the
 * four-letter articles "unos" and "unas", for a slower TTS rate.
 * The segments concatenate exactly to the original story and do not match
 * inside longer words. "él" is slowed as a short word, not as an article.
 */
final class ArticleSlowSpeech {
    private static final Pattern SHORT_WORD = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{M}])(?:unos|unas|[\\p{L}]{1,3})(?![\\p{L}\\p{M}])");

    record Part(String text, boolean slow, int start) {}

    private ArticleSlowSpeech() {}

    static boolean appliesTo(String studyLanguage, boolean readingRussian, float shortWordSpeed) {
        return "es".equals(studyLanguage) && !readingRussian && shortWordSpeed > 0f;
    }

    static List<Part> split(String original) {
        List<Part> result = new ArrayList<>();
        Matcher matcher = SHORT_WORD.matcher(original);
        int cursor = 0;
        while (matcher.find()) {
            String between = original.substring(cursor, matcher.start());
            int articleStart = matcher.start();
            // Do not send whitespace-only utterances to Android TTS.
            if (!between.isEmpty()) {
                if (between.trim().isEmpty()) {
                    articleStart = cursor;
                } else {
                    result.add(new Part(between, false, cursor));
                }
            }
            result.add(new Part(original.substring(articleStart, matcher.end()), true, articleStart));
            cursor = matcher.end();
        }
        if (cursor < original.length()) {
            String rest = original.substring(cursor);
            if (!rest.trim().isEmpty() || result.isEmpty()) {
                result.add(new Part(rest, false, cursor));
            } else {
                // Preserve trailing whitespace for concatenation and offsets.
                int last = result.size() - 1;
                Part prior = result.get(last);
                result.set(last, new Part(prior.text() + rest, prior.slow(), prior.start()));
            }
        }
        if (result.isEmpty()) result.add(new Part(original, false, 0));
        return result;
    }
}
