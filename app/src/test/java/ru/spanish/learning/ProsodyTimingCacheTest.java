package ru.spanish.learning;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;

public final class ProsodyTimingCacheTest {
    @Test public void preservesWordFramesForCachedSentenceAfterPauseMarkerMoves() {
        String sentence = "Uno dos tres cuatro";
        List<ProsodyPauseAudio.WordTiming> captured = List.of(
                new ProsodyPauseAudio.WordTiming(4, 7, 1200),
                new ProsodyPauseAudio.WordTiming(8, 12, 2410),
                new ProsodyPauseAudio.WordTiming(13, 19, 3400));
        String saved = ProsodyTimingCache.encode(captured);
        assertEquals(captured, ProsodyTimingCache.decode(saved, sentence));
        // No marker positions are serialized: a marker edit cannot invalidate
        // the word/frame mapping of the identical original speech recording.
        assertFalse(saved.contains("pause"));
    }

    @Test public void ignoresInvalidOrObsoleteFrameMetadata() {
        String sentence = "Uno dos";
        List<ProsodyPauseAudio.WordTiming> restored = ProsodyTimingCache.decode(
                "4:7:120,0:100:500,4:7:-1,bad,0:3:10", sentence);
        assertEquals(List.of(new ProsodyPauseAudio.WordTiming(4, 7, 120),
                new ProsodyPauseAudio.WordTiming(0, 3, 10)), restored);
    }
}
