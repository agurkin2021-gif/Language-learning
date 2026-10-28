package ru.spanish.learning;

import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

public class WavAssemblerTest {
    private static void put16(RandomAccessFile r, int n) throws IOException {
        r.write(n); r.write(n >> 8);
    }
    private static void put32(RandomAccessFile r, int n) throws IOException {
        for (int i=0;i<4;i++) r.write(n >> (8*i));
    }
    private static File part(File dir, String name, int rate, byte[] data) throws IOException {
        File file = new File(dir,name);
        try(RandomAccessFile w = new RandomAccessFile(file,"rw")) {
            w.writeBytes("RIFF");put32(w,36+data.length);w.writeBytes("WAVEfmt ");
            put32(w,16); put16(w,1); put16(w,1);put32(w,rate);
            put32(w,rate*2);put16(w,2);put16(w,16);
            w.writeBytes("data");put32(w,data.length);w.write(data);
        }
        return file;
    }
    @Test public void joinsValidWavSamplesWithOneHeader() throws Exception {
        File dir=Files.createTempDirectory("wavtest").toFile();
        File combined=new File(dir,"all.wav");
        WavAssembler assembler=new WavAssembler(combined);
        assembler.append(part(dir,"part1.wav",16000,new byte[]{1,2,3,4}));
        assembler.append(part(dir,"part2.wav",16000,new byte[]{5,6,7,8,9,10}));
        assembler.finish();
        try(RandomAccessFile r=new RandomAccessFile(combined,"r")) {
            assertEquals(54,r.length());
            r.seek(4);assertEquals(46,Integer.reverseBytes(r.readInt()));
            r.seek(40);assertEquals(10,Integer.reverseBytes(r.readInt()));
            r.seek(44);assertEquals(1,r.readUnsignedByte());
            r.seek(53);assertEquals(10,r.readUnsignedByte());
        }
    }
    @Test public void rejectsDifferentSampleRatesWithoutProducingFinishedAudio() throws Exception {
        File dir=Files.createTempDirectory("wavbad").toFile();
        File output=new File(dir,"all.wav");
        WavAssembler assembler=new WavAssembler(output);
        assembler.append(part(dir,"a.wav",16000,new byte[]{1,2}));
        try {
            assembler.append(part(dir,"b.wav",22050,new byte[]{3,4}));
            fail("Must reject a mid-story format change");
        } catch(IOException expected) { assertTrue(expected.getMessage().contains("changed")); }
        assembler.abort();
        assertFalse(output.exists());
    }
}
