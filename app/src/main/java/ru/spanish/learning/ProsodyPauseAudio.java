package ru.spanish.learning;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Inserts digital silence into a WHOLE-SENTENCE synthesized PCM WAV.
 * The original speech samples are copied byte-for-byte, so the pitch contour
 * and non-final intonation before the inserted silence remain untouched.
 */
final class ProsodyPauseAudio {
    record WordTiming(int start, int end, int frame) {}
    private record Cut(long frame, int durationMs) {}
    private ProsodyPauseAudio() {}

    static void insertSilence(File input, File output, String spoken,
                              List<Integer> gaps, List<WordTiming> timing,
                              int silenceMs) throws IOException {
        if (silenceMs <= 0 || gaps.isEmpty()) throw new IOException("No manual pauses");
        try (RandomAccessFile wav = new RandomAccessFile(input, "r")) {
            if (wav.length() < 44 || !"RIFF".equals(readAscii(wav))
                    || readInt(wav) < 36 || !"WAVE".equals(readAscii(wav)))
                throw new IOException("TTS audio is not RIFF/WAVE");
            int channels = 0, sampleRate = 0, bits = 0, format = 0;
            long dataPos = -1, dataSize = -1, offset = 12;
            while (offset + 8 <= wav.length()) {
                wav.seek(offset);
                String id = readAscii(wav);
                long length = Integer.toUnsignedLong(readInt(wav));
                long body = offset + 8;
                if (body + length > wav.length()) throw new IOException("Invalid WAV chunk");
                if ("fmt ".equals(id)) {
                    if (length < 16) throw new IOException("Invalid WAV format");
                    format = readShort(wav);
                    channels = readShort(wav);
                    sampleRate = readInt(wav);
                    readInt(wav);
                    readShort(wav);
                    bits = readShort(wav);
                } else if ("data".equals(id)) {
                    dataPos = body;
                    dataSize = length;
                }
                offset = body + length + (length & 1);
            }
            if (format != 1 || channels < 1 || channels > 2 || sampleRate <= 0
                    || (bits != 8 && bits != 16 && bits != 24 && bits != 32)
                    || dataPos < 0 || dataSize <= 0)
                throw new IOException("Unsupported PCM WAV from speech engine");
            int frameBytes = channels * bits / 8;
            if (dataSize % frameBytes != 0) throw new IOException("Misaligned PCM data");
            long totalFrames = dataSize / frameBytes;
            List<Cut> cuts = new ArrayList<>();
            for (int gap : gaps) {
                if (gap < 1 || gap >= spoken.length()) continue;
                int next = gap;
                while (next < spoken.length() && Character.isWhitespace(spoken.charAt(next))) next++;
                if (next >= spoken.length()) continue;
                long frame = -1;
                for (WordTiming t : timing) {
                    if (t.start() >= next && (frame == -1 || t.frame() < frame))
                        frame = Math.max(0, t.frame());
                }
                if (frame < 0) {
                    // Some installed voices do not emit word-range events.
                    // Keep the utterance intact and estimate the word boundary.
                    frame = Math.round(totalFrames * (double) next / spoken.length());
                }
                frame = Math.max(0, Math.min(totalFrames, frame));
                cuts.add(new Cut(frame, silenceMs));
            }
            if (cuts.isEmpty()) throw new IOException("No valid pause positions");
            cuts.sort(Comparator.comparingLong(Cut::frame));
            long silenceFrames = (long) sampleRate * silenceMs / 1000;
            long addedBytes = cuts.size() * silenceFrames * frameBytes;
            if (dataSize + addedBytes > Integer.MAX_VALUE - 44)
                throw new IOException("Audio too long");
            try (RandomAccessFile dest = new RandomAccessFile(output, "rw")) {
                dest.setLength(0);
                dest.write(new byte[44]);
                wav.seek(dataPos);
                long position = 0;
                for (Cut cut : cuts) {
                    long upto = cut.frame() * frameBytes;
                    if (upto > position) {
                        copy(wav, dest, upto - position);
                        position = upto;
                    }
                    writeSilence(dest, silenceFrames * frameBytes, bits == 8 ? 0x80 : 0);
                }
                if (position < dataSize) copy(wav, dest, dataSize - position);
                dest.seek(0);
                dest.writeBytes("RIFF");
                writeInt(dest, (int) (36 + dataSize + addedBytes));
                dest.writeBytes("WAVEfmt ");
                writeInt(dest, 16);
                writeShort(dest, 1);
                writeShort(dest, channels);
                writeInt(dest, sampleRate);
                writeInt(dest, sampleRate * frameBytes);
                writeShort(dest, frameBytes);
                writeShort(dest, bits);
                dest.writeBytes("data");
                writeInt(dest, (int) (dataSize + addedBytes));
            }
        }
    }

    private static void copy(RandomAccessFile from, RandomAccessFile to, long bytes)
            throws IOException {
        byte[] buffer = new byte[8192];
        while (bytes > 0) {
            int count = (int) Math.min(bytes, buffer.length);
            from.readFully(buffer, 0, count);
            to.write(buffer, 0, count);
            bytes -= count;
        }
    }
    private static void writeSilence(RandomAccessFile to, long count, int value)
            throws IOException {
        byte[] buffer = new byte[8192];
        if (value != 0) java.util.Arrays.fill(buffer, (byte) value);
        while (count > 0) {
            int amount = (int) Math.min(count, buffer.length);
            to.write(buffer, 0, amount);
            count -= amount;
        }
    }
    private static String readAscii(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[4];
        file.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.US_ASCII);
    }
    private static int readShort(RandomAccessFile file) throws IOException {
        return file.readUnsignedByte() | file.readUnsignedByte() << 8;
    }
    private static int readInt(RandomAccessFile file) throws IOException {
        return file.readUnsignedByte() | file.readUnsignedByte() << 8
                | file.readUnsignedByte() << 16 | file.readUnsignedByte() << 24;
    }
    private static void writeShort(RandomAccessFile file, int value) throws IOException {
        file.writeByte(value); file.writeByte(value >>> 8);
    }
    private static void writeInt(RandomAccessFile file, int value) throws IOException {
        file.writeByte(value); file.writeByte(value >>> 8);
        file.writeByte(value >>> 16); file.writeByte(value >>> 24);
    }
}
