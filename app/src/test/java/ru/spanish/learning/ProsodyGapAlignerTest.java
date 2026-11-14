package ru.spanish.learning;

import static org.junit.Assert.*;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class ProsodyGapAlignerTest {
    private static void intLE(RandomAccessFile f, int value, int bytes) throws Exception {
        for(int i=0;i<bytes;i++) f.writeByte(value >>> (i*8));
    }

    @Test public void markersGoToMiddleWordBoundariesWithoutAndroidTimestamps() throws Exception {
        File src=File.createTempFile("whole-tts-sentence-", ".wav");
        File dst=File.createTempFile("whole-tts-marked-", ".wav");
        try {
            final int frames=20000, rate=10000;
            try (RandomAccessFile wav = new RandomAccessFile(src, "rw")) {
                wav.setLength(0);
                wav.writeBytes("RIFF"); intLE(wav,36+frames*2,4);
                wav.writeBytes("WAVEfmt "); intLE(wav,16,4);
                intLE(wav,1,2); intLE(wav,1,2);
                intLE(wav,rate,4); intLE(wav,rate*2,4);
                intLE(wav,2,2); intLE(wav,16,2);
                wav.writeBytes("data"); intLE(wav,frames*2,4);
                // "Los van sin mar" has four similar-duration monosyllables.
                // Quiet transitions before "van" and "mar" are IN THE MIDDLE.
                for(int frame=0;frame<frames;frame++) {
                    boolean gap1=Math.abs(frame-5000)<180;
                    boolean gap2=Math.abs(frame-15000)<180;
                    int signal=gap1 || gap2 ? 0 : (int)(10000*Math.sin(frame*.15));
                    intLE(wav, signal, 2);
                }
            }
            String story="Los van sin mar";
            ProsodyPauseAudio.insertSilence(src,dst,story,
                    List.of(story.indexOf(" van"),story.indexOf(" mar")),
                    List.of(),200);
            assertEquals(44+frames*2+2*rate*2/5,dst.length());

            // Find the TWO >100ms zero spans introduced by the user.
            List<Integer> starts=new ArrayList<>();
            try(RandomAccessFile result=new RandomAccessFile(dst,"r")) {
                result.seek(44);
                int zeroStart=-1;
                for(int frame=0;frame<(result.length()-44)/2;frame++) {
                    int sample=result.readUnsignedByte() | result.readUnsignedByte()<<8;
                    if(sample==0 && zeroStart<0) zeroStart=frame;
                    if(sample!=0 && zeroStart>=0) {
                        if(frame-zeroStart>1200) starts.add(zeroStart);
                        zeroStart=-1;
                    }
                }
            }
            assertEquals("Two separate interior user pauses",2,starts.size());
            assertTrue("First marker near 'van', not at block start",
                    Math.abs(starts.get(0)-5000)<550);
            assertTrue("Second marker near 'mar', not at block end",
                    Math.abs(starts.get(1)-17000)<550);
        } finally {src.delete();dst.delete();}
    }
}
