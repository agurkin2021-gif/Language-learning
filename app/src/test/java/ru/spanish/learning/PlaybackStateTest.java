package ru.spanish.learning;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlaybackStateTest {
    @Test public void duplicateTapAndStaleCallbackDoNotSkip() {
        PlaybackState s = new PlaybackState(); s.load(3,0);
        String first = s.play(); assertNull(s.play()); s.pause(); String second=s.play();
        assertFalse(s.complete(first)); assertEquals(0,s.index); assertTrue(s.complete(second));
        assertEquals(1,s.index); assertFalse(s.complete(second)); assertEquals(1,s.index);
    }
    @Test public void waitsForExplicitContinueAndRepeatsPrevious() {
        PlaybackState s=new PlaybackState(); s.load(3,0); s.complete(s.play());
        assertEquals(PlaybackState.Phase.WAITING,s.phase); assertEquals(1,s.index);
        s.repeat(); assertEquals(0,s.index); assertNotNull(s.play());
    }
    @Test public void finishAndRestart() {
        PlaybackState s=new PlaybackState(); s.load(1,99); assertEquals(0,s.index);
        s.complete(s.play()); assertEquals(PlaybackState.Phase.FINISHED,s.phase);
        s.play(); assertEquals(0,s.index); assertEquals(PlaybackState.Phase.SPEAKING,s.phase);
    }
    @Test public void screenChangeInvalidatesSpeech() {
        PlaybackState s=new PlaybackState(); s.load(3,0); String old=s.play(); s.load(2,1);
        assertFalse(s.complete(old)); assertEquals(1,s.index);
    }
    @Test public void emptyCannotSpeak() { PlaybackState s=new PlaybackState(); s.load(0,0); assertNull(s.play()); }
}
