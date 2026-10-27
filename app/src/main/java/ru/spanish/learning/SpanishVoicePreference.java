package ru.spanish.learning;

import java.util.Locale;

/** Prefer Latin American Spanish voices; never alter written words for phonetic imitation. */
final class SpanishVoicePreference {
    private SpanishVoicePreference() {}

    static int regionRank(Locale locale) {
        if (locale == null || !"es".equals(locale.getLanguage())) return -1;
        return switch (locale.getCountry().toUpperCase(Locale.ROOT)) {
            case "UY" -> 0;
            case "CO" -> 1;
            case "VE" -> 2;
            case "MX" -> 3;
            case "AR" -> 4;
            case "CL" -> 5;
            case "BO" -> 6;
            case "PE" -> 7;
            case "PY" -> 8;
            case "EC" -> 9;
            case "CR" -> 10;
            case "DO" -> 11;
            case "GT" -> 12;
            case "HN" -> 13;
            case "NI" -> 14;
            case "PA" -> 15;
            case "PR" -> 16;
            case "SV" -> 17;
            case "CU" -> 18;
            case "US" -> 19;
            case "419" -> 20;
            default -> -1; // no European (es-ES) or countryless Spanish
        };
    }

    static String regionLabel(Locale locale) {
        return switch (locale.getCountry()) {
            case "AR" -> "Аргентина";
            case "UY" -> "Уругвай";
            case "MX" -> "Мексика";
            default -> "Латинская Америка";
        };
    }
}
