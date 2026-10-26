package ru.spanish.learning;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class CardSpeechTimingTest {
    @Test public void animation205msLeavesOnly45ms() {
        assertEquals(45L, CardSpeechTiming.remainingAfterRelease(1000L, 1205L, 250L));
    }
    @Test public void slowAnimationAddsNoSecondWait() {
        assertEquals(0L, CardSpeechTiming.remainingAfterRelease(1000L, 1400L, 250L));
    }
    @Test public void initialCardWaitIs250ms() {
        assertEquals(250L, CardSpeechTiming.remainingAfterRelease(-1L, 1100L, 250L));
    }
    @Test public void timerNeverNegative() {
        assertEquals(0L, CardSpeechTiming.remainingAfterRelease(1000L, 1300L, 250L));
        assertEquals(250L, CardSpeechTiming.remainingAfterRelease(1000L, 900L, 250L));
    }
}
