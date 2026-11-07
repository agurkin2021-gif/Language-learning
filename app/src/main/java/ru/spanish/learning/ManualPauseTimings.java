package ru.spanish.learning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manual word-marker -> actual MediaPlayer playback position (milliseconds).
 * Time is captured when the user presses the calibration control, not guessed
 * from text length, acoustic silence or unavailable TTS word events.
 */
final class ManualPauseTimings {
    private ManualPauseTimings() {}

    static String encode(Map<Integer,Integer> times) {
        List<Integer> gaps = new ArrayList<>(times.keySet());
        Collections.sort(gaps);
        List<String> items = new ArrayList<>();
        for (int gap : gaps) {
            Integer ms = times.get(gap);
            if (gap > 0 && ms != null && ms > 0) items.add(gap + ":" + ms);
        }
        return String.join(",", items);
    }

    static Map<Integer,Integer> decode(String source, List<Integer> allowedGaps) {
        Map<Integer,Integer> result = new HashMap<>();
        if (source == null || allowedGaps == null) return result;
        for (String item : source.split(",")) {
            String[] fields = item.split(":");
            if (fields.length != 2) continue;
            try {
                int gap = Integer.parseInt(fields[0]);
                int ms = Integer.parseInt(fields[1]);
                if (allowedGaps.contains(gap) && ms > 0) result.put(gap, ms);
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    static List<Integer> orderedTimes(Map<Integer,Integer> times, List<Integer> gaps) {
        if (gaps.isEmpty()) return List.of();
        List<Integer> result = new ArrayList<>();
        int last = 0;
        for (int gap : gaps) {
            int ms = times.getOrDefault(gap, 0);
            if (ms <= last) return List.of();
            result.add(ms);
            last = ms;
        }
        return result;
    }
}
