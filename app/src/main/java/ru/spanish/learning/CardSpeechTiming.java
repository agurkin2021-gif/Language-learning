package ru.spanish.learning;

/** Count the page-settling animation toward the flashcard speech wait. */
final class CardSpeechTiming {
    private CardSpeechTiming() {}

    static long remainingAfterRelease(long releasedAtMs, long nowMs, long waitMs) {
        long safeWait = Math.max(0L, waitMs);
        if (releasedAtMs < 0L || nowMs < releasedAtMs) return safeWait;
        return Math.max(0L, safeWait - (nowMs - releasedAtMs));
    }
}
