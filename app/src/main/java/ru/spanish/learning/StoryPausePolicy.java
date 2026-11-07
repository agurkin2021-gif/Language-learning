package ru.spanish.learning;

/** Distinguish intentional user pause markers from the automatic story gap. */
final class StoryPausePolicy {
    private StoryPausePolicy() {}

    static int betweenSentences(int configuredMs, boolean manualMarkersActive) {
        return manualMarkersActive ? 0 : Math.max(0, configuredMs);
    }
}
