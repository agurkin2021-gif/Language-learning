package ru.spanish.learning;
import org.junit.Test;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public final class ManualPauseTimingsTest {
    @Test public void capturesActualMillisecondsNotCharacterOffsets() {
        Map<Integer,Integer> a=Map.of(12,1691,3,421);
        assertEquals("3:421,12:1691",ManualPauseTimings.encode(a));
        assertEquals(a,ManualPauseTimings.decode("3:421,12:1691",List.of(3,12)));
        assertEquals(List.of(421,1691),
                ManualPauseTimings.orderedTimes(a,List.of(3,12)));
    }
    @Test public void staleMarkersAndReversedTimesNeverApply() {
        assertEquals(Map.of(3,790),
                ManualPauseTimings.decode("3:790,15:2000,17:-4,bad",List.of(3,18)));
        assertTrue(ManualPauseTimings.orderedTimes(Map.of(3,1800,12,700),
                List.of(3,12)).isEmpty());
        assertTrue(ManualPauseTimings.orderedTimes(Map.of(3,300),
                List.of(3,12)).isEmpty());
    }
}
