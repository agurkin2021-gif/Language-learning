package ru.spanish.learning;

import java.text.Normalizer;
import java.util.Locale;
import java.util.List;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Applies only to new Spanish study cards classified as fixed expressions. */
final class SpanishPhrasePolicy {
    private static final Pattern WORD = Pattern.compile("[\\p{L}]+");
    private static final Pattern INFINITIVE = Pattern.compile(
            "^(?:ir|[\\p{L}]+(?:ar|er|ir))(?:se|me|te|nos|lo|la|los|las|le|les){0,2}$");
    private static final List<String> LEADING_PARTICLES = Arrays.asList(
            "no", "nunca", "al", "a", "sin", "para", "por", "de");
    // Ordinary nouns and adjectives that happen to end in -ar/-er/-ir.
    private static final List<String> NON_VERBAL_HEADS = Arrays.asList(
            "lugar", "hogar", "mujer", "ayer", "taller", "placer", "azucar",
            "cualquier", "popular", "familiar", "similar", "particular", "regular",
            "singular", "escolar", "militar", "solar", "lunar", "collar", "dolar",
            "altar", "pilar", "alquiler");

    private SpanishPhrasePolicy() {}

    static boolean startsWithInfinitive(String phrase) {
        if (phrase == null || phrase.trim().isEmpty()) return false;
        String normalized = Normalizer.normalize(
                phrase.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        Matcher tokens = WORD.matcher(normalized);
        int particles = 0;
        while (tokens.find()) {
            String head = tokens.group();
            if (particles < 2 && LEADING_PARTICLES.contains(head)) {
                particles++;
                continue;
            }
            return !NON_VERBAL_HEADS.contains(head) && INFINITIVE.matcher(head).matches();
        }
        return false;
    }

    static void requireConversationalForm(String phrase) {
        if (startsWithInfinitive(phrase)) {
            throw new IllegalArgumentException(
                    "В устойчивых выражениях нужна разговорная личная форма: "
                    + "«tengo razón», «tenés razón», а не инфинитив «tener razón».");
        }
    }
}
