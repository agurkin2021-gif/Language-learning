package ru.spanish.learning;

import java.util.Locale;

/** Locale preference only; the actual Rioplatense ll/y sound must be auditioned. */
final class SpanishVoicePreference {
    private SpanishVoicePreference() {}

    static int regionRank(Locale locale) {
        if (locale == null || !"es".equals(locale.getLanguage())) return -1;
        return switch (locale.getCountry().toUpperCase(Locale.ROOT)) {
            case "AR" -> 0;
            case "UY" -> 1;
            case "MX" -> 2;
            case "PY" -> 3;
            case "BO" -> 4;
            case "CL" -> 5;
            case "CO" -> 6;
            case "PE" -> 7;
            case "VE" -> 8;
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
