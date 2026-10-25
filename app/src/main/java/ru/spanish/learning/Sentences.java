package ru.spanish.learning;

import java.util.ArrayList;
import java.util.List;

/** Small spoken chunks, including a hard limit for TTS engines. */
public final class Sentences {
    private Sentences() {}
    public static List<String> split(String text) {
        List<String> result = new ArrayList<>();
        for (String line : text.trim().split("(?<=[.!?…])\\s+|[\\r\\n]+")) {
            String rest = line.trim();
            while (rest.length() > 240) {
                int cut = rest.lastIndexOf(' ', 240);
                if (cut < 1) cut = 240;
                result.add(rest.substring(0, cut).trim());
                rest = rest.substring(cut).trim();
            }
            if (!rest.isEmpty()) result.add(rest);
        }
        return result;
    }
}
