package ru.spanish.learning;

import java.util.ArrayList;
import java.util.List;

/** Persist TTS word/frame alignment for the exact cached whole-sentence WAV. */
final class ProsodyTimingCache {
    private ProsodyTimingCache() {}

    static String encode(List<ProsodyPauseAudio.WordTiming> ranges) {
        StringBuilder value = new StringBuilder();
        for (ProsodyPauseAudio.WordTiming timing : ranges) {
            if (timing.start() < 0 || timing.end() <= timing.start() || timing.frame() <= 0)
                continue;
            if (value.length() > 0) value.append(',');
            value.append(timing.start()).append(':')
                    .append(timing.end()).append(':').append(timing.frame());
        }
        return value.toString();
    }

    static List<ProsodyPauseAudio.WordTiming> decode(String encoded, String spoken) {
        List<ProsodyPauseAudio.WordTiming> ranges = new ArrayList<>();
        if (encoded == null || encoded.isEmpty() || spoken == null) return ranges;
        for (String entry : encoded.split(",")) {
            String[] fields = entry.split(":");
            if (fields.length != 3) continue;
            try {
                int start = Integer.parseInt(fields[0]);
                int end = Integer.parseInt(fields[1]);
                int frame = Integer.parseInt(fields[2]);
                if (start >= 0 && end > start && end <= spoken.length() && frame > 0)
                    ranges.add(new ProsodyPauseAudio.WordTiming(start, end, frame));
            } catch (NumberFormatException ignored) {
                // Ignore malformed or obsolete metadata rather than moving a pause.
            }
        }
        return ranges;
    }
}
