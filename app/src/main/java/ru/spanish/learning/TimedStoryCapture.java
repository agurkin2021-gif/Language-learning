package ru.spanish.learning;

import android.media.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Collect the exact audio and frame coordinates from ONE Android TTS speak()
 * request. The request is muted through KEY_PARAM_VOLUME, not split by words.
 * synthesizeToFile does NOT dispatch onRangeStart on the AOSP service path.
 */
final class TimedStoryCapture {
    private static final int MAX_BYTES = 36 * 1024 * 1024;
    private final String utterance;
    private final ByteArrayOutputStream audio = new ByteArrayOutputStream();
    private final List<ProsodyPauseAudio.WordTiming> words = new ArrayList<>();
    private int rate, channels, encoding;
    private boolean begun, invalid;

    TimedStoryCapture(String utterance) { this.utterance = utterance; }

    synchronized void begin(String id, int sampleRate, int pcmEncoding, int channelCount) {
        if (!utterance.equals(id)) return;
        if (begun || sampleRate < 1000 || sampleRate > 192000
                || channelCount < 1 || channelCount > 2
                || (pcmEncoding != AudioFormat.ENCODING_PCM_8BIT
                && pcmEncoding != AudioFormat.ENCODING_PCM_16BIT
                && pcmEncoding != AudioFormat.ENCODING_PCM_FLOAT)) {
            invalid = true;
            return;
        }
        begun = true;
        rate = sampleRate;
        channels = channelCount;
        encoding = pcmEncoding;
    }

    synchronized void append(String id, byte[] data) {
        if (!utterance.equals(id) || invalid || !begun) return;
        if (data == null || data.length == 0 || audio.size() > MAX_BYTES - data.length) {
            invalid = true;
            return;
        }
        audio.write(data, 0, data.length);
    }

    synchronized void range(String id, int start, int end, int frame) {
        if (utterance.equals(id) && start >= 0 && end > start && frame > 0)
            words.add(new ProsodyPauseAudio.WordTiming(start, end, frame));
    }

    synchronized Snapshot snapshot() throws IOException {
        if (!begun || invalid || audio.size() == 0)
            throw new IOException("Android TTS did not expose its synthesized PCM audio");
        int bytesPerSample = encoding == AudioFormat.ENCODING_PCM_8BIT ? 1
                : encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2;
        byte[] input = audio.toByteArray();
        if (input.length % (channels * bytesPerSample) != 0)
            throw new IOException("TTS PCM samples are not frame-aligned");
        int frames = input.length / (channels * bytesPerSample);
        if (frames < 1) throw new IOException("TTS PCM is empty");
        byte[] pcm;
        int bits;
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            // RIFF PCM needs integer samples: convert FLOAT to 16-bit without
            // altering playback timing or word-frame coordinates.
            ByteBuffer floats = ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN);
            ByteBuffer shorts = ByteBuffer.allocate(input.length / 2)
                    .order(ByteOrder.LITTLE_ENDIAN);
            while (floats.hasRemaining()) {
                float sample = floats.getFloat();
                if (!Float.isFinite(sample)) sample = 0f;
                int signed = Math.round(Math.max(-1f, Math.min(1f, sample)) * 32767f);
                shorts.putShort((short) signed);
            }
            pcm = shorts.array();
            bits = 16;
        } else {
            pcm = input;
            bits = encoding == AudioFormat.ENCODING_PCM_8BIT ? 8 : 16;
        }
        return new Snapshot(rate, channels, bits, pcm, List.copyOf(words));
    }

    record Snapshot(int sampleRate, int channels, int bits, byte[] samples,
                    List<ProsodyPauseAudio.WordTiming> ranges) {
        void save(File destination) throws IOException {
            if (samples.length > Integer.MAX_VALUE - 44)
                throw new IOException("TTS WAV exceeds supported file length");
            try (FileOutputStream out = new FileOutputStream(destination)) {
                out.write(new byte[]{'R','I','F','F'});
                integer(out, 36 + samples.length, 4);
                out.write(new byte[]{'W','A','V','E','f','m','t',' '});
                integer(out, 16, 4);
                integer(out, 1, 2);
                integer(out, channels, 2);
                integer(out, sampleRate, 4);
                int frameBytes = channels * bits / 8;
                integer(out, sampleRate * frameBytes, 4);
                integer(out, frameBytes, 2);
                integer(out, bits, 2);
                out.write(new byte[]{'d','a','t','a'});
                integer(out, samples.length, 4);
                out.write(samples);
            }
        }
    }

    private static void integer(FileOutputStream out, int value, int bytes)
            throws IOException {
        for (int i = 0; i < bytes; i++) out.write(value >>> (8 * i) & 255);
    }
}
