package ru.spanish.learning;

import static org.junit.Assert.*;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.List;
import org.junit.Test;

/** Reproduces the unwanted silence before "Dios" with a marker AFTER "caminos". */
public final class NoPhantomStartPauseTest {
    private static void writeInt(RandomAccessFile file, int value, int n)
            throws Exception {
        for (int i = 0; i < n; i++) file.writeByte((value >>> (8 * i)) & 255);
    }

    @Test public void onlyOneInsertedPauseAfterCaminosNoPhantomBeforeDios()
            throws Exception {
        String sentence = "Dios les mostró dos caminos y explicó qué pasaría con cada uno.";
        int gap = sentence.indexOf(" y explicó");
        assertTrue(gap > 0);
        assertEquals(gap, ManualStoryPauses.nearestGap(sentence, gap));
        assertEquals(List.of(gap), SpokenGapMapper.map(sentence,
                RioplatensePronunciation.forSpeech(sentence, "es"),
                List.of(gap), "es"));

        File original = File.createTempFile("dios-leading-", ".wav");
        File paused = File.createTempFile("dios-after-caminos-", ".wav");
        int total = 900, initialSilentFrames = 120, wordFrame = 600;
        int actualTrim = 110; // preserve exactly 10ms of leading PCM silence
        int insertedFrames = 200;
        try {
            try (RandomAccessFile file = new RandomAccessFile(original, "rw")) {
                file.writeBytes("RIFF"); writeInt(file, 36 + total * 2, 4);
                file.writeBytes("WAVEfmt "); writeInt(file, 16, 4);
                writeInt(file, 1, 2); writeInt(file, 1, 2);
                writeInt(file, 1000, 4); writeInt(file, 2000, 4);
                writeInt(file, 2, 2); writeInt(file, 16, 2);
                file.writeBytes("data"); writeInt(file, total * 2, 4);
                for (int i = 0; i < total; i++)
                    writeInt(file, i < initialSilentFrames ? 0 : (1200 + i % 99), 2);
            }

            ProsodyPauseAudio.insertSilenceExact(original, paused, sentence,
                    List.of(gap),
                    List.of(new ProsodyPauseAudio.WordTiming(gap + 1,
                            gap + 2, wordFrame)), insertedFrames);

            byte[] before = Files.readAllBytes(original.toPath());
            byte[] after = Files.readAllBytes(paused.toPath());
            int originalEnd = total * 2;
            int beforeCut = (wordFrame - actualTrim) * 2;
            int pauseLengthBytes = insertedFrames * 2;
            assertEquals(44 + originalEnd - actualTrim * 2 + pauseLengthBytes,
                    after.length);
            // There is no new inserted silence before "Dios":
            // just 10ms retained original pre-roll. Every following speech
            // sample must be IDENTICAL, including the end of "caminos".
            for (int i = 0; i < beforeCut; i++)
                assertEquals("Original pre-marker audio " + i,
                        before[44 + actualTrim * 2 + i], after[44 + i]);
            assertEquals(0, after[44]); // 10ms natural padding preserved
            assertNotEquals(0, after[44 + 10 * 2]); // "Dios" starts immediately after
            for (int i = 0; i < pauseLengthBytes; i++)
                assertEquals("Only explicitly marked gap should be silent", 0,
                        after[44 + beforeCut + i]);
            for (int i = wordFrame * 2; i < originalEnd; i++)
                assertEquals("Unmodified audio after manual pause " + i,
                        before[44 + i],
                        after[44 + i - actualTrim * 2 + pauseLengthBytes]);
        } finally {
            original.delete();
            paused.delete();
        }
    }

    @Test public void noAutoDelayBetweenSentencesWhenUserHasMarkers() {
        assertEquals(0, StoryPausePolicy.betweenSentences(1500, true));
        assertEquals(0, StoryPausePolicy.betweenSentences(5000, true));
        assertEquals(1500, StoryPausePolicy.betweenSentences(1500, false));
        assertEquals(0, StoryPausePolicy.betweenSentences(0, false));
    }
}
