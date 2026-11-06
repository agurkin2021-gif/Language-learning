package ru.spanish.learning;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keep Spanish/English spelling unchanged. For Russian TTS only, pronounce
 * numeric biblical references as plain numbers, never as clock times.
 * Displayed story text and sentence boundaries are not modified.
 */
final class RioplatensePronunciation {
    private static final Pattern RUSSIAN_VERSE_REFERENCE = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(\\d{1,3}):(\\d{1,3})(?:\\s*[-–—]\\s*(\\d{1,3}))?(?!\\d)");
    private static final String[] UNITS = {"ноль", "один", "два", "три", "четыре",
            "пять", "шесть", "семь", "восемь", "девять"};
    private static final String[] TEENS = {"десять", "одиннадцать", "двенадцать",
            "тринадцать", "четырнадцать", "пятнадцать", "шестнадцать",
            "семнадцать", "восемнадцать", "девятнадцать"};
    private static final String[] TENS = {"", "", "двадцать", "тридцать", "сорок",
            "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят", "девяносто"};
    private static final String[] HUNDREDS = {"", "сто", "двести", "триста",
            "четыреста", "пятьсот", "шестьсот", "семьсот", "восемьсот", "девятьсот"};

    private RioplatensePronunciation() {}

    static String forSpeech(String original, String language) {
        if (original == null || !"ru".equals(language)) return original;
        Matcher matches = RUSSIAN_VERSE_REFERENCE.matcher(original);
        StringBuffer spoken = new StringBuffer();
        while (matches.find()) {
            StringBuilder numbers = new StringBuilder(cardinal(Integer.parseInt(matches.group(1))))
                    .append(' ').append(cardinal(Integer.parseInt(matches.group(2))));
            if (matches.group(3) != null) {
                numbers.append(' ').append(cardinal(Integer.parseInt(matches.group(3))));
            }
            matches.appendReplacement(spoken, Matcher.quoteReplacement(numbers.toString()));
        }
        matches.appendTail(spoken);
        return spoken.toString();
    }

    private static String cardinal(int number) {
        if (number == 0) return UNITS[0];
        StringBuilder words = new StringBuilder();
        int hundreds = number / 100;
        if (hundreds > 0) words.append(HUNDREDS[hundreds]);
        int remainder = number % 100;
        if (remainder >= 10 && remainder <= 19) {
            appendWord(words, TEENS[remainder - 10]);
        } else {
            if (remainder >= 20) appendWord(words, TENS[remainder / 10]);
            if (remainder % 10 > 0) appendWord(words, UNITS[remainder % 10]);
        }
        return words.toString();
    }

    private static void appendWord(StringBuilder words, String word) {
        if (words.length() > 0) words.append(' ');
        words.append(word);
    }
}
