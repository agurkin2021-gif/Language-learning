package ru.spanish.learning;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Validates Android TTS WAV chunks and concatenates their PCM samples without truncation. */
final class WavAssembler {
    private final File output;
    private RandomAccessFile destination;
    private int format, channels, sampleRate, bitsPerSample;
    private long dataSize;
    private boolean completed;

    WavAssembler(File output) { this.output = output; }

    void append(File chunk) throws IOException {
        if (completed) throw new IOException("Audio is already complete");
        try (RandomAccessFile source = new RandomAccessFile(chunk, "r")) {
            if (source.length() < 44 || !"RIFF".equals(four(source))
                    || !(read32(source) >= 36) || !"WAVE".equals(four(source)))
                throw new IOException("The TTS engine did not produce a WAV file");
            boolean foundFormat = false, foundData = false;
            long offset = 12, start = 0, length = 0;
            int partFormat = 0, partChannels = 0, partRate = 0, partBits = 0;
            while (offset + 8 <= source.length()) {
                source.seek(offset);
                String id = four(source);
                long size = Integer.toUnsignedLong(read32(source));
                long body = offset + 8;
                if (size > source.length() - body)
                    throw new IOException("Malformed WAV chunk");
                if ("fmt ".equals(id)) {
                    if (size < 16) throw new IOException("Invalid WAV format");
                    partFormat = read16(source);
                    partChannels = read16(source);
                    partRate = read32(source);
                    read32(source); // Byte rate
                    read16(source); // Block align
                    partBits = read16(source);
                    foundFormat = true;
                } else if ("data".equals(id)) {
                    start = body;
                    length = size;
                    foundData = true;
                }
                offset = body + size + (size & 1);
            }
            if (!foundFormat || !foundData || partFormat != 1
                    || partChannels < 1 || partChannels > 2 || partRate <= 0
                    || (partBits != 8 && partBits != 16 && partBits != 24 && partBits != 32))
                throw new IOException("Unsupported PCM format from Android TTS");
            if (length == 0) throw new IOException("The voice produced no audio");
            if (dataSize + length > Integer.MAX_VALUE - 44)
                throw new IOException("The audio is too long for a WAV attachment");
            if (destination == null) {
                format = partFormat; channels = partChannels;
                sampleRate = partRate; bitsPerSample = partBits;
                destination = new RandomAccessFile(output, "rw");
                destination.setLength(0);
                destination.write(new byte[44]); // Patched after all chunks.
            } else if (format != partFormat || channels != partChannels
                    || sampleRate != partRate || bitsPerSample != partBits) {
                throw new IOException("The TTS voice changed audio format between chunks");
            }
            source.seek(start);
            destination.seek(44 + dataSize);
            byte[] buffer = new byte[8192];
            long remaining = length;
            while (remaining > 0) {
                int count = (int) Math.min(buffer.length, remaining);
                source.readFully(buffer, 0, count);
                destination.write(buffer, 0, count);
                remaining -= count;
            }
            dataSize += length;
        }
    }

    void finish() throws IOException {
        if (destination == null || dataSize == 0) throw new IOException("Empty audio");
        int blockAlign = channels * bitsPerSample / 8;
        destination.seek(0);
        destination.writeBytes("RIFF");
        write32(destination, (int) (36 + dataSize));
        destination.writeBytes("WAVEfmt ");
        write32(destination, 16);
        write16(destination, format);
        write16(destination, channels);
        write32(destination, sampleRate);
        write32(destination, sampleRate * blockAlign);
        write16(destination, blockAlign);
        write16(destination, bitsPerSample);
        destination.writeBytes("data");
        write32(destination, (int) dataSize);
        destination.close();
        destination = null;
        completed = true;
    }

    void abort() throws IOException {
        if (destination != null) { destination.close(); destination = null; }
        output.delete();
    }

    private static String four(RandomAccessFile file) throws IOException {
        byte[] id = new byte[4]; file.readFully(id);
        return new String(id, java.nio.charset.StandardCharsets.US_ASCII);
    }
    private static int read16(RandomAccessFile file) throws IOException {
        int a=file.readUnsignedByte(), b=file.readUnsignedByte();
        return a | b<<8;
    }
    private static int read32(RandomAccessFile file) throws IOException {
        int a=file.readUnsignedByte(), b=file.readUnsignedByte(),
                c=file.readUnsignedByte(), d=file.readUnsignedByte();
        return a | b<<8 | c<<16 | d<<24;
    }
    private static void write16(RandomAccessFile file, int value) throws IOException {
        file.writeByte(value); file.writeByte(value >>> 8);
    }
    private static void write32(RandomAccessFile file, int value) throws IOException {
        file.writeByte(value); file.writeByte(value >>> 8);
        file.writeByte(value >>> 16); file.writeByte(value >>> 24);
    }
}
