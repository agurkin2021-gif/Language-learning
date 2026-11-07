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
    @Test public void insertsMultiplePausesAtTheirExactInteriorFrames() throws Exception {
        File input = File.createTempFile("manual-pause-full-", ".wav");
        File output = File.createTempFile("manual-pause-spliced-", ".wav");
        try {
            try (RandomAccessFile wav = new RandomAccessFile(input, "rw")) {
                wav.setLength(0);
                wav.writeBytes("RIFF"); little(wav, 36 + 200, 4);
                wav.writeBytes("WAVEfmt "); little(wav,16,4); little(wav,1,2);
                little(wav,1,2); little(wav,1000,4); little(wav,2000,4);
                little(wav,2,2); little(wav,16,2);
                wav.writeBytes("data"); little(wav,200,4);
                for(int i=0;i<100;i++) little(wav, 501+i,2);
            }
            String story="Uno dos tres cuatro";
            int first=story.indexOf(" dos");
            int second=story.indexOf(" cuatro");
            ProsodyPauseAudio.insertSilence(input,output,story,
                    List.of(first,second),
                    List.of(new ProsodyPauseAudio.WordTiming(4,7,20),
                            new ProsodyPauseAudio.WordTiming(13,19,70)), 100);
            byte[] original=Files.readAllBytes(input.toPath());
            byte[] marked=Files.readAllBytes(output.toPath());
            assertEquals(44+200+400,marked.length);
            // First pause at frame 20, second at frame 70 (not either edge).
            for(int i=0;i<40;i++) assertEquals(original[44+i],marked[44+i]);
            for(int i=40;i<240;i++) assertEquals(0,marked[44+i]);
            for(int i=40;i<140;i++) assertEquals(original[44+i],marked[44+i+200]);
            for(int i=340;i<540;i++) assertEquals(0,marked[44+i]);
            for(int i=140;i<200;i++) assertEquals(original[44+i],marked[44+i+400]);
        } finally { input.delete(); output.delete(); }
    }

    @Test public void zeroOrOutOfRangeFramesCannotPlacePausesAtEdges() throws Exception {
        for(int invalidFrame: new int[]{-1,0,1,100,10000}) {
            File input=File.createTempFile("bad-frame-",".wav");
            File output=File.createTempFile("bad-result-",".wav");
            try {
                try (RandomAccessFile wav=new RandomAccessFile(input,"rw")) {
                    wav.setLength(0); wav.writeBytes("RIFF"); little(wav,236,4);
                    wav.writeBytes("WAVEfmt "); little(wav,16,4); little(wav,1,2);
                    little(wav,1,2); little(wav,1000,4); little(wav,2000,4);
                    little(wav,2,2); little(wav,16,2);
                    wav.writeBytes("data"); little(wav,200,4);
                    for(int i=0;i<100;i++) little(wav, i+10,2);
                }
                // Bad timestamps must be ignored, but playback must still work.
                ProsodyPauseAudio.insertSilence(input,output,"Uno dos",List.of(3),
                        List.of(new ProsodyPauseAudio.WordTiming(4,7,invalidFrame)),200);
                byte[] result = Files.readAllBytes(output.toPath());
                assertEquals(44+200+400, result.length);
                byte[] original = Files.readAllBytes(input.toPath());
                for(int i=0;i<20;i++) assertEquals(original[44+i],result[44+i]);
                for(int i=180;i<200;i++)
                    assertEquals(original[44+i],result[44+i+400]);
            } finally {input.delete();output.delete();}
        }
    }

    @Test public void cannotUseTimingOfLaterWordAsPauseAtEndOfBlock() throws Exception {
        File input=File.createTempFile("wrong-word-",".wav");
        File output=File.createTempFile("wrong-result-",".wav");
        try {
            try (RandomAccessFile wav=new RandomAccessFile(input,"rw")) {
                wav.setLength(0); wav.writeBytes("RIFF"); little(wav,236,4);
                wav.writeBytes("WAVEfmt "); little(wav,16,4); little(wav,1,2);
                little(wav,1,2); little(wav,1000,4); little(wav,2000,4);
                little(wav,2,2); little(wav,16,2);
                wav.writeBytes("data"); little(wav,200,4);
                for(int i=0;i<100;i++) little(wav, i+20,2);
            }
            ProsodyPauseAudio.insertSilence(input,output,"Uno dos tres",List.of(3),
                    List.of(new ProsodyPauseAudio.WordTiming(8,12,70)),100);
            assertEquals(44+200+200,output.length());
        } finally {input.delete();output.delete();}
    }

    @Test public void missingTimestampsStillAllowStoryAudioWithInteriorPause() throws Exception {
        File input = File.createTempFile("native-voice-", ".wav");
        File output = File.createTempFile("native-voice-marked-", ".wav");
        try {
            try (RandomAccessFile f = new RandomAccessFile(input,"rw")) {
                f.writeBytes("RIFF"); little(f, 36+2000, 4); f.writeBytes("WAVEfmt ");
                little(f, 16, 4); little(f, 1, 2); little(f, 1, 2);
                little(f, 1000, 4); little(f, 2000, 4);
                little(f, 2, 2); little(f, 16, 2);
                f.writeBytes("data"); little(f, 2000, 4);
                for(int i=0;i<1000;i++) little(f, 123, 2);
            }
            ProsodyPauseAudio.insertSilence(input,output,"La luz",List.of(2),List.of(),500);
            assertEquals(44+2000+1000, output.length());
            byte[] result=Files.readAllBytes(output.toPath());
            byte[] original=Files.readAllBytes(input.toPath());
            assertEquals(original[44],result[44]); // not at audio start
            assertEquals(original[original.length-1], result[result.length-1]); // not at audio end
        } finally { input.delete();output.delete(); }
    }
}
