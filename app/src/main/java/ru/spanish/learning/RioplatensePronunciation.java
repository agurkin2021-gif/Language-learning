package ru.spanish.learning;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalize speech ONLY, never the displayed story or sentence boundaries.
 * Bible references must be cardinal numbers, not times or spoken punctuation.
 * No sheismo/orthography substitutions are applied to Spanish words.
 */
final class RioplatensePronunciation {
    // Spaces after the colon and all common dash types are valid in scripture references.
    // Do not accidentally consume the first part of a timestamp with seconds.
    private static final Pattern BIBLE_REFERENCE = Pattern.compile(
            "(?<![\\p{L}\\p{N}:])(\\d{1,3})\\s*:\\s*(\\d{1,3})"
                    + "(?:\\s*[-–—−]\\s*(\\d{1,3}))?"
                    + "(?![\\p{L}\\p{N}]|\\s*:\\s*\\d)");

    // Some existing stories already spell out a verse range as "once-doce"
    // or "одиннадцать — двенадцать". Do not read the hyphen aloud there either.
    private static final Pattern WORD_RANGE = Pattern.compile(
            "([\\p{L}]+)\\s*[-–—−]\\s*([\\p{L}]+)");
    private static final Set<String> NUMBER_WORDS = Set.of(
            "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete",
            "ocho", "nueve", "diez", "once", "doce", "trece", "catorce",
            "quince", "dieciséis", "dieciseis", "diecisiete", "dieciocho",
            "diecinueve", "veinte", "veintiuno", "veintidós", "veintidos",
            "veintitrés", "veintitres", "veinticuatro", "veinticinco",
            "veintiséis", "veintiseis", "veintisiete", "veintiocho",
            "veintinueve", "treinta", "cuarenta", "cincuenta", "sesenta",
            "setenta", "ochenta", "noventa", "cien", "ciento",
            "ноль", "один", "одна", "два", "две", "три", "четыре",
            "пять", "шесть", "семь", "восемь", "девять", "десять",
            "одиннадцать", "двенадцать", "тринадцать", "четырнадцать",
            "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать",
            "девятнадцать", "двадцать", "тридцать", "сорок",
            "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят",
            "девяносто", "сто");

    private static final String[] RU_UNITS = {"ноль", "один", "два", "три",
            "четыре", "пять", "шесть", "семь", "восемь", "девять"};
    private static final String[] RU_TEENS = {"десять", "одиннадцать", "двенадцать",
            "тринадцать", "четырнадцать", "пятнадцать", "шестнадцать",
            "семнадцать", "восемнадцать", "девятнадцать"};
    private static final String[] RU_TENS = {"", "", "двадцать", "тридцать", "сорок",
            "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят", "девяносто"};
    private static final String[] RU_HUNDREDS = {"", "сто", "двести", "триста",
            "четыреста", "пятьсот", "шестьсот", "семьсот", "восемьсот", "девятьсот"};

    private static final String[] ES_UNITS = {"cero", "uno", "dos", "tres", "cuatro",
            "cinco", "seis", "siete", "ocho", "nueve"};
    private static final String[] ES_TEENS = {"diez", "once", "doce", "trece",
            "catorce", "quince", "dieciséis", "diecisiete",
            "dieciocho", "diecinueve"};
    private static final String[] ES_TWENTIES = {"veinte", "veintiuno", "veintidós",
            "veintitrés", "veinticuatro", "veinticinco", "veintiséis",
            "veintisiete", "veintiocho", "veintinueve"};
    private static final String[] ES_TENS = {"", "", "veinte", "treinta", "cuarenta",
            "cincuenta", "sesenta", "setenta", "ochenta", "noventa"};
    private static final String[] ES_HUNDREDS = {"", "ciento", "doscientos",
            "trescientos", "cuatrocientos", "quinientos", "seiscientos",
            "setecientos", "ochocientos", "novecientos"};

    private RioplatensePronunciation() {}

    static String forSpeech(String original, String language) {
        if (original == null || (!"ru".equals(language) && !"es".equals(language)))
            return original;
        Matcher matches = BIBLE_REFERENCE.matcher(original);
        StringBuffer result = new StringBuffer();
        while (matches.find()) {
            StringBuilder words = new StringBuilder(cardinal(Integer.parseInt(matches.group(1)), language))
                    .append(' ').append(cardinal(Integer.parseInt(matches.group(2)), language));
            if (matches.group(3) != null) {
                words.append(' ').append(cardinal(Integer.parseInt(matches.group(3)), language));
            }
            matches.appendReplacement(result, Matcher.quoteReplacement(words.toString()));
        }
        matches.appendTail(result);

        Matcher writtenRange = WORD_RANGE.matcher(result.toString());
        StringBuffer normalized = new StringBuffer();
        while (writtenRange.find()) {
            String first = writtenRange.group(1).toLowerCase(Locale.ROOT);
            String second = writtenRange.group(2).toLowerCase(Locale.ROOT);
            if (NUMBER_WORDS.contains(first) && NUMBER_WORDS.contains(second))
                writtenRange.appendReplacement(normalized,
                        Matcher.quoteReplacement(writtenRange.group(1) + " " + writtenRange.group(2)));
            else
                writtenRange.appendReplacement(normalized,
                        Matcher.quoteReplacement(writtenRange.group()));
        }
        writtenRange.appendTail(normalized);
        return normalized.toString();
    }

    private static String cardinal(int number, String language) {
        return "es".equals(language) ? spanishCardinal(number) : russianCardinal(number);
    }

    private static String russianCardinal(int number) {
        if (number == 0) return RU_UNITS[0];
        StringBuilder words = new StringBuilder();
        int hundreds = number / 100;
        if (hundreds > 0) words.append(RU_HUNDREDS[hundreds]);
        int rest = number % 100;
        if (rest >= 10 && rest <= 19) {
            appendWord(words, RU_TEENS[rest - 10]);
        } else {
            if (rest >= 20) appendWord(words, RU_TENS[rest / 10]);
            if (rest % 10 > 0) appendWord(words, RU_UNITS[rest % 10]);
        }
        return words.toString();
    }

    private static String spanishCardinal(int number) {
        if (number == 0) return ES_UNITS[0];
        if (number == 100) return "cien";
        StringBuilder words = new StringBuilder();
        if (number >= 100) words.append(ES_HUNDREDS[number / 100]);
        int rest = number % 100;
        if (rest == 0) return words.toString();
        if (rest < 10) {
            appendWord(words, ES_UNITS[rest]);
        } else if (rest < 20) {
            appendWord(words, ES_TEENS[rest - 10]);
        } else if (rest < 30) {
            appendWord(words, ES_TWENTIES[rest - 20]);
        } else {
            String tens = ES_TENS[rest / 10];
            appendWord(words, rest % 10 == 0 ? tens : tens + " y " + ES_UNITS[rest % 10]);
        }
        return words.toString();
    }

    private static void appendWord(StringBuilder words, String word) {
        if (words.length() > 0) words.append(' ');
        words.append(word);
    }
}
