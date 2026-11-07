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

    /**
     * Exact MANUALLY calibrated audio coordinates. The user taps while the
     * whole sentence plays; each timestamp is captured with MediaPlayer's
     * getCurrentPosition() in the original, unmodified WAV timeline.
     * No text proportions, energy peaks or TTS word ranges are involved.
     */
    static void insertSilenceAtMs(File input, File output, List<Integer> momentsMs,
                                  int silenceMs) throws IOException {
        if (silenceMs <= 0 || momentsMs == null || momentsMs.isEmpty())
            throw new IOException("No manual pause timing");
        splice(input, output, "", List.of(), List.of(), silenceMs, momentsMs, false);
    }

    static void insertSilence(File input, File output, String spoken,
                              List<Integer> gaps, List<WordTiming> timing,
                              int silenceMs) throws IOException {
        if (silenceMs <= 0 || gaps.isEmpty()) throw new IOException("No manual pauses");
        splice(input, output, spoken, gaps, timing, silenceMs, null, false);
    }

    /**
     * Strict alignment for automatically placed text markers. Only use engine-
     * reported word frames from the very same TTS PCM request. If the chosen
     * voice omitted a marker, fail rather than cutting at an estimated point.
     */
    static void insertSilenceExact(File input, File output, String spoken,
                                   List<Integer> gaps, List<WordTiming> timing,
                                   int silenceMs) throws IOException {
        if (silenceMs <= 0 || gaps.isEmpty()) throw new IOException("No pause markers");
        splice(input, output, spoken, gaps, timing, silenceMs, null, true);
    }

    private static void splice(File input, File output, String spoken,
                               List<Integer> gaps, List<WordTiming> timing,
                               int silenceMs, List<Integer> momentsMs,
                               boolean requireWordFrames) throws IOException {
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
            if (momentsMs != null) {
                long previousFrame = -1;
                for (int ms : momentsMs) {
                    long frame = Math.round(sampleRate * (ms / 1000.0));
                    if (ms <= 0 || frame <= 0 || frame >= totalFrames)
                        throw new IOException("Manual timestamp outside audio");
                    if (frame <= previousFrame)
                        throw new IOException("Manual timestamps are not in text order");
                    cuts.add(new Cut(frame, silenceMs));
                    previousFrame = frame;
                }
            } else {
            List<Long> acousticFallback = null;
            int gapIndex = -1;
            for (int gap : gaps) {
                gapIndex++;
                if (gap < 1 || gap >= spoken.length()) continue;
                int next = gap;
                while (next < spoken.length() && Character.isWhitespace(spoken.charAt(next))) next++;
                if (next >= spoken.length()) continue;
                // TTS may report just one range for an entire sentence, return
                // -1/zero for every frame, or use a timebase incompatible with
                // this WAV file. Never clamp those values to the start/end of
                // the block: that moves the user's pause to the wrong place.
                while (next < spoken.length()
                        && !Character.isLetterOrDigit(spoken.charAt(next))) next++;
                if (next >= spoken.length())
                    throw new IOException("No word after the pause marker");
                long frame = findExactWordStart(spoken, next, timing, totalFrames, sampleRate);
                if (frame < 0 && requireWordFrames)
                    throw new IOException("Selected voice did not provide a word frame at marker "
                            + gap);
                if (frame < 0) {
                    // Screenshot regression: some Android voices never call
                    // onRangeStart for synthesizeToFile. Do NOT stop playback.
                    // Find a low-energy internal word transition in the same
                    // uninterrupted speech recording, never at a block edge.
                    if (acousticFallback == null)
                        acousticFallback = ProsodyGapAligner.estimate(
                                wav, dataPos, channels, bits, sampleRate,
                                totalFrames, spoken, gaps);
                    frame = acousticFallback.get(gapIndex);
                }
                cuts.add(new Cut(frame, silenceMs));
            }
            }
            if (cuts.isEmpty()) throw new IOException("No valid pause positions");
            cuts.sort(Comparator.comparingLong(Cut::frame));
            for (int i = 1; i < cuts.size(); i++)
                if (cuts.get(i).frame() <= cuts.get(i-1).frame())
                    throw new IOException("TTS word timestamps are duplicated or unordered");

            // Some engines put a conspicuous block of DIGITAL silence before
            // the first spoken word. This is not a user marker. Remove only
            // verified silent PCM frames; keep 10 ms of pre-roll, and translate
            // every engine word coordinate into the shortened WAV timeline.
            long leadingFrames = silentLeadingFrames(
                    wav, dataPos, totalFrames, sampleRate, frameBytes, bits);
            for (Cut cut : cuts) {
                if (cut.frame() <= leadingFrames)
                    throw new IOException("Pause frame falls inside leading silence");
            }
            long audibleBytes = dataSize - leadingFrames * frameBytes;
            long silenceFrames = (long) sampleRate * silenceMs / 1000;
            long addedBytes = cuts.size() * silenceFrames * frameBytes;
            if (audibleBytes + addedBytes > Integer.MAX_VALUE - 44)
                throw new IOException("Audio too long");
            try (RandomAccessFile dest = new RandomAccessFile(output, "rw")) {
                dest.setLength(0);
                dest.write(new byte[44]);
                wav.seek(dataPos + leadingFrames * frameBytes);
                long position = 0;
                for (Cut cut : cuts) {
                    long upto = (cut.frame() - leadingFrames) * frameBytes;
                    if (upto > position) {
                        copy(wav, dest, upto - position);
                        position = upto;
                    }
                    writeSilence(dest, silenceFrames * frameBytes, bits == 8 ? 0x80 : 0);
                }
                if (position < audibleBytes) copy(wav, dest, audibleBytes - position);
                dest.seek(0);
                dest.writeBytes("RIFF");
                writeInt(dest, (int) (36 + audibleBytes + addedBytes));
                dest.writeBytes("WAVEfmt ");
                writeInt(dest, 16);
                writeShort(dest, 1);
                writeShort(dest, channels);
                writeInt(dest, sampleRate);
                writeInt(dest, sampleRate * frameBytes);
                writeShort(dest, frameBytes);
                writeShort(dest, bits);
                dest.writeBytes("data");
                writeInt(dest, (int) (audibleBytes + addedBytes));
            }
        }
    }

    /**
     * Return the exact start of the FIRST spoken word after the visible marker.
     * The only reliable alignment is onRangeStart with a matching UTF-16 word
     * offset AND a valid PCM frame. Character/time proportion is not alignment.
     */
    private static long findExactWordStart(String spoken, int nextWord,
                                           List<WordTiming> timing,
                                           long totalFrames, int sampleRate) throws IOException {
        long result = -1;
        for (WordTiming t : timing) {
            if (t.start() < 0 || t.end() <= t.start() || t.end() > spoken.length())
                continue;
            if (t.start() != nextWord) continue;
            long frame = t.frame();
            // Zero/-1, an end-of-file position and a frame from a different
            // clock are not usable boundaries. A 10 ms margin rejects
            // block-edge artifacts without rejecting short first words.
            long edge = Math.max(1, sampleRate / 100);
            if (frame <= edge || frame >= totalFrames - edge)
                continue;
            if (result >= 0 && result != frame)
                throw new IOException("Conflicting TTS word timestamps");
            result = frame;
        }
        // Missing/inaccurate word events are normal on some Android TTS
        // engines. Return a sentinel for acoustic alignment, not an error.
        return result;
    }

    /**
     * Drop only a long run of PCM frames that contain the exact digital zero
     * value (unsigned PCM8 uses 0x80). Speech consonants and normal low-level
     * breathing/noise are retained. Never trim a wholly silent WAV.
     */
    private static long silentLeadingFrames(RandomAccessFile wav, long start,
                                            long totalFrames, int sampleRate,
                                            int frameBytes, int bits) throws IOException {
        long scanFrames = Math.min(totalFrames, sampleRate * 2L);
        if (scanFrames <= sampleRate / 20) return 0;
        wav.seek(start);
        byte[] frame = new byte[frameBytes];
        int silence = bits == 8 ? 0x80 : 0;
        long firstSoundFrame = -1;
        for (long index = 0; index < scanFrames; index++) {
            wav.readFully(frame);
            boolean allSilent = true;
            for (byte sample : frame) {
                if ((sample & 255) != silence) {
                    allSilent = false;
                    break;
                }
            }
            if (!allSilent) {
                firstSoundFrame = index;
                break;
            }
        }
        if (firstSoundFrame < sampleRate / 20) return 0;
        return Math.max(0, firstSoundFrame - sampleRate / 100);
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
