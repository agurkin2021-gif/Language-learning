package ru.spanish.learning;

import static org.junit.Assert.*;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.List;
import org.junit.Test;

public final class ProsodyPauseAudioTest {
    private static void little(RandomAccessFile f, int value, int length) throws Exception {
        for (int i=0; i<length; i++) f.writeByte(value >>> (i*8));
    }
    @Test public void insertsSilenceWithoutChangingPitchSamplesOrReplacingSpaces() throws Exception {
        File input = File.createTempFile("native-voice-", ".wav");
        File output = File.createTempFile("native-voice-marked-", ".wav");
        try {
            try (RandomAccessFile f = new RandomAccessFile(input, "rw")) {
                f.setLength(0);
                f.writeBytes("RIFF"); little(f, 36+100, 4); f.writeBytes("WAVEfmt ");
                little(f, 16, 4); little(f, 1, 2); little(f, 1, 2);
                little(f, 1000, 4); little(f, 2000, 4);
                little(f, 2, 2); little(f, 16, 2);
                f.writeBytes("data"); little(f, 100, 4);
                for (int k = 0; k < 50; k++) little(f, 1000 + k, 2);
            }
            String text = "Mantener la voz estable";
            int gap = text.indexOf(" voz");
            ProsodyPauseAudio.insertSilence(input, output, text, List.of(gap),
                    List.of(new ProsodyPauseAudio.WordTiming(12,15,24)), 300);
            byte[] bytes = Files.readAllBytes(output.toPath());
            assertEquals(44+100+600, bytes.length);
            byte[] original = Files.readAllBytes(input.toPath());
            for (int i=0; i<24*2; i++) assertEquals(original[44+i], bytes[44+i]);
            for (int i=0; i<600; i++) assertEquals(0, bytes[44+48+i]);
            for (int i=24*2; i<100; i++)
                assertEquals("Speech must be bit identical: "+i,
                        original[44+i], bytes[44+600+i]);
        } finally {
            input.delete(); output.delete();
        }
    }
    @Test public void fallsBackWhenVoiceHasNoRangeTiming() throws Exception {
        File input = File.createTempFile("native-voice-", ".wav");
        File output = File.createTempFile("native-voice-marked-", ".wav");
        try {
            try (RandomAccessFile f = new RandomAccessFile(input,"rw")) {
                f.writeBytes("RIFF"); little(f, 36+20, 4); f.writeBytes("WAVEfmt ");
                little(f, 16, 4); little(f, 1, 2); little(f, 1, 2);
                little(f, 1000, 4); little(f, 2000, 4);
                little(f, 2, 2); little(f, 16, 2);
                f.writeBytes("data"); little(f, 20, 4);
                for(int i=0;i<10;i++) little(f, 123, 2);
            }
            ProsodyPauseAudio.insertSilence(input,output,"La luz",List.of(2),List.of(),500);
            assertEquals(44+20+1000,output.length());
        } finally { input.delete();output.delete(); }
    }
}
