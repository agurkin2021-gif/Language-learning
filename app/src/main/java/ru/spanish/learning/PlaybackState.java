package ru.spanish.learning;

/** Main-thread state machine. Tokens reject callbacks from stopped/replaced speech. */
public final class PlaybackState {
    public enum Phase { PAUSED, SPEAKING, WAITING, FINISHED }
    public int index;
    public int count;
    public Phase phase = Phase.PAUSED;
    private long generation;
    public void load(int size, int position) {
        generation++;
        count = size;
        index = Math.max(0, Math.min(position, Math.max(0, size - 1)));
        phase = Phase.PAUSED;
    }
    public String play() {
        if (count == 0 || phase == Phase.SPEAKING) return null;
        if (phase == Phase.FINISHED) index = 0;
        phase = Phase.SPEAKING;
        return Long.toString(++generation);
    }
    public boolean complete(String token) {
        if (!Long.toString(generation).equals(token) || phase != Phase.SPEAKING) return false;
        if (index + 1 < count) { index++; phase = Phase.WAITING; }
        else phase = Phase.FINISHED;
        return true;
    }
    public void pause() { generation++; if (phase != Phase.FINISHED) phase = Phase.PAUSED; }
    public void repeat() {
        if (phase == Phase.WAITING && index > 0) index--;
        generation++;
        phase = Phase.PAUSED;
    }
}
