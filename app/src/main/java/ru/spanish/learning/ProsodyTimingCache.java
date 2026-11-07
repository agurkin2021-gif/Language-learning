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

    /**
     * Every requested text gap must have a distinct, ordered word-start frame.
     * This is a fast structural check for cached TTS metadata; the WAV splicer
     * additionally checks the frame against the actual PCM duration.
     */
    static boolean covers(String spoken, List<Integer> gaps,
                          List<ProsodyPauseAudio.WordTiming> ranges) {
        if (spoken == null || gaps == null || gaps.isEmpty() || ranges == null)
            return false;
        int previousFrame = -1;
        for (int gap : gaps) {
            if (gap <= 0 || gap >= spoken.length()
                    || !Character.isWhitespace(spoken.charAt(gap))) return false;
            int next = gap;
            while (next < spoken.length() && Character.isWhitespace(spoken.charAt(next))) next++;
            while (next < spoken.length() && !Character.isLetterOrDigit(spoken.charAt(next))) next++;
            if (next >= spoken.length()) return false;
            int matchedFrame = -1;
            for (ProsodyPauseAudio.WordTiming range : ranges) {
                if (range.start() != next || range.end() <= next
                        || range.end() > spoken.length() || range.frame() <= 0)
                    continue;
                if (matchedFrame != -1 && matchedFrame != range.frame()) return false;
                matchedFrame = range.frame();
            }
            if (matchedFrame <= previousFrame) return false;
            previousFrame = matchedFrame;
        }
        return true;
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
