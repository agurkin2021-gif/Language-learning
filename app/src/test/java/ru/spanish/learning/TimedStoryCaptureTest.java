package ru.spanish.learning;

import static org.junit.Assert.*;

import android.media.AudioFormat;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.junit.Test;

public final class TimedStoryCaptureTest {
    @Test public void singleMutedSynthesisMatchesPauseMarkerToActualAudioFrame()
            throws Exception {
        String text = "Uno dos tres";
        TimedStoryCapture capture = new TimedStoryCapture("u:prosody");
        capture.begin("u:prosody", 1000, AudioFormat.ENCODING_PCM_16BIT, 1);
        byte[] pcm = new byte[400]; // 200 mono 16-bit frames
        for (int i = 0; i < pcm.length; i++) pcm[i] = (byte) (i % 127);
        capture.append("u:prosody", pcm);
        capture.range("u:prosody", 0, 3, 10);
        capture.range("u:prosody", 4, 7, 120);
        capture.range("u:prosody", 8, 12, 175);
        TimedStoryCapture.Snapshot snapshot = capture.snapshot();
        File input = File.createTempFile("tts-muted-source-", ".wav");
        File output = File.createTempFile("tts-muted-paused-", ".wav");
        try {
            snapshot.save(input);
            assertEquals(44 + pcm.length, input.length());
            ProsodyPauseAudio.insertSilenceExact(input, output, text,
                    List.of(text.indexOf(" dos")), snapshot.ranges(), 100);
            byte[] result = Files.readAllBytes(output.toPath());
            assertEquals(44 + pcm.length + 200, result.length);
            for (int i = 0; i < 240; i++) assertEquals(pcm[i], result[44 + i]);
            for (int i = 240; i < 440; i++) assertEquals(0, result[44 + i]);
            for (int i = 240; i < pcm.length; i++)
                assertEquals(pcm[i], result[44 + 200 + i]);
        } finally {
            input.delete();
            output.delete();
        }
    }

    @Test public void noEngineWordBoundaryNeverTriggersApproximatePause()
            throws Exception {
        TimedStoryCapture capture = new TimedStoryCapture("u");
        capture.begin("u", 1000, AudioFormat.ENCODING_PCM_16BIT, 1);
        capture.append("u", new byte[400]);
        File original = File.createTempFile("tts-no-range-", ".wav");
        File target = File.createTempFile("tts-no-range-out-", ".wav");
        try {
            capture.snapshot().save(original);
            try {
                ProsodyPauseAudio.insertSilenceExact(original, target,
                        "Uno dos", List.of(3), List.of(), 100);
                fail("Unverified text/audio positions may not move a pause");
            } catch (java.io.IOException expected) {
                assertTrue(expected.getMessage().contains("word frame"));
            }
        } finally {
            original.delete();
            target.delete();
        }
    }

    @Test public void rejectsWrongUtteranceAndBrokenPcmFrames() {
        TimedStoryCapture capture = new TimedStoryCapture("valid");
        capture.begin("different", 1000, AudioFormat.ENCODING_PCM_16BIT, 1);
        capture.begin("valid", 1000, AudioFormat.ENCODING_PCM_16BIT, 1);
        capture.append("different", new byte[8]);
        capture.append("valid", new byte[]{1,2,3});
        try {
            capture.snapshot();
            fail("Truncated samples must fail");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("frame-aligned"));
        }
    }
}
