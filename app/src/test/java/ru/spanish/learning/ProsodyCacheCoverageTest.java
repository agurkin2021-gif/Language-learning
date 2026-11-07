package ru.spanish.learning;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;

public final class ProsodyCacheCoverageTest {
    private static final String TEXT = "Uno dos tres cuatro";
    private static final List<Integer> GAPS =
            List.of(TEXT.indexOf(" dos"), TEXT.indexOf(" cuatro"));

    @Test public void requiresRealOrderedFramesForEveryPause() {
        List<ProsodyPauseAudio.WordTiming> all = List.of(
                new ProsodyPauseAudio.WordTiming(4, 7, 120),
                new ProsodyPauseAudio.WordTiming(13, 19, 325));
        assertTrue(ProsodyTimingCache.covers(TEXT, GAPS, all));
        assertFalse(ProsodyTimingCache.covers(TEXT, GAPS, all.subList(0, 1)));
        assertFalse(ProsodyTimingCache.covers(TEXT, GAPS, List.of()));
        assertFalse(ProsodyTimingCache.covers(TEXT, GAPS, List.of(
                new ProsodyPauseAudio.WordTiming(4, 7, 325),
                new ProsodyPauseAudio.WordTiming(13, 19, 120))));
        assertFalse(ProsodyTimingCache.covers(TEXT, GAPS, List.of(
                new ProsodyPauseAudio.WordTiming(4, 7, 120),
                new ProsodyPauseAudio.WordTiming(13, 19, 120))));
    }

    @Test public void corruptedTimingCacheMustNotMoveMarkers() {
        String broken = "4:7:120,13:19:0,broken";
        List<ProsodyPauseAudio.WordTiming> restored =
                ProsodyTimingCache.decode(broken, TEXT);
        assertFalse(ProsodyTimingCache.covers(TEXT, GAPS, restored));
        assertTrue(ProsodyTimingCache.covers(TEXT, List.of(GAPS.get(0)), restored));
    }
}
