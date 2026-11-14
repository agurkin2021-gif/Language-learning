package ru.spanish.learning;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Acoustic fallback for Android voices without onRangeStart word timings.
 *
 * Never replaces the sentence with TTS sub-utterances: we locate low-energy
 * word boundaries in the already synthesized whole-sentence PCM. This keeps
 * all intonation samples unchanged. Without word timing, positions are
 * estimates, not guaranteed frame-exact forced alignment.
 */
final class ProsodyGapAligner {
    private static final Pattern WORDS = Pattern.compile("[\\p{L}\\p{N}]+");
    private ProsodyGapAligner() {}

    static List<Long> estimate(RandomAccessFile wav, long dataPos,
                               int channels, int bits, int sampleRate, long totalFrames,
                               String spoken, List<Integer> gapPositions) throws IOException {
        if (totalFrames < 8 || sampleRate < 200 || gapPositions.isEmpty())
            throw new IOException("Audio too short for manual pauses");
        int frameBytes = channels * bits / 8;
        int windowFrames = Math.max(1, sampleRate / 100); // 10 ms energy bins
        int n = (int) Math.min(100_000, (totalFrames + windowFrames - 1) / windowFrames);
        if (n < 10) throw new IOException("Audio too short for alignment");
        float[] energy = new float[n];
        byte[] buffer = new byte[windowFrames * frameBytes];
        wav.seek(dataPos);
        for (int w = 0; w < n; w++) {
            int frames = (int) Math.min(windowFrames, totalFrames - (long) w * windowFrames);
            if (frames <= 0) break;
            wav.readFully(buffer, 0, frames * frameBytes);
            double squared = 0;
            for (int frame = 0; frame < frames; frame++) {
                for (int channel = 0; channel < channels; channel++) {
                    int idx = (frame * channels + channel) * bits / 8;
                    double value = pcmValue(buffer, idx, bits);
                    squared += value * value;
                }
            }
            energy[w] = (float) Math.sqrt(squared / (frames * channels));
        }
        float[] sorted = energy.clone();
        Arrays.sort(sorted);
        float reference = sorted[(int) (sorted.length * 0.85)];
        float gate = Math.max(.002f, reference * .06f);
        int first = 0, last = n - 1;
        while (first + 3 < last && energy[first] < gate) first++;
        while (last - 3 > first && energy[last] < gate) last--;
        if (last - first < 8) { first = 0; last = n - 1; }
        List<Word> words = words(spoken);
        if (words.size() < 2) throw new IOException("Not enough words to place silence");
        double sum = 0;
        for (Word word : words) sum += word.weight();
        List<Long> result = new ArrayList<>();
        int previousWindow = first;
        for (int k = 0; k < gapPositions.size(); k++) {
            int gap = gapPositions.get(k);
            double before = 0;
            int counted = 0;
            for (Word word : words) {
                if (word.end() <= gap) { before += word.weight(); counted++; }
            }
            if (counted == 0 || counted >= words.size())
                throw new IOException("Manual pause is not between spoken words");
            double fraction = before / sum;
            int expected = first + (int) Math.round(fraction * (last - first));

            // Neighboring markers have independent search intervals.
            int lower = Math.max(first + 2, previousWindow + 2);
            int upper = last - 2;
            if (k + 1 < gapPositions.size()) {
                double nextBefore = 0;
                for (Word word : words)
                    if (word.end() <= gapPositions.get(k + 1)) nextBefore += word.weight();
                double nextFraction = nextBefore / sum;
                int nextExpected = first + (int) Math.round(nextFraction * (last - first));
                upper = Math.min(upper, (expected + nextExpected) / 2);
            }
            int radius = Math.max(5, (int) Math.round((last - first) * .11));
            lower = Math.max(lower, expected - radius);
            upper = Math.min(upper, expected + radius);
            if (lower > upper)
                throw new IOException("No distinct acoustic window for manual pause");

            // Prefer local short silence / unvoiced gaps near the syllabically
            // weighted estimate. Reject start/end padding by construction.
            double best = -Double.MAX_VALUE;
            int bestWindow = Math.min(upper, Math.max(lower, expected));
            for (int w = lower; w <= upper; w++) {
                double middle = avg(energy, w - 1, w + 1);
                double left = avg(energy, w - 6, w - 3);
                double right = avg(energy, w + 3, w + 6);
                double context = (left + right) / 2;
                double dip = context > .001 ? Math.max(0, (context - middle) / context) : 0;
                double quiet = 1.0 - Math.min(1, middle / Math.max(.01, reference));
                double distance = Math.abs(w - expected) / (double) radius;
                double score = .6 * dip + .35 * quiet - .36 * distance;
                if (score > best) { best = score; bestWindow = w; }
            }
            long frame = Math.min(totalFrames - 1, (long) bestWindow * windowFrames);
            result.add(frame);
            previousWindow = bestWindow;
        }
        return result;
    }

    private record Word(int start, int end, double weight) {}
    private static List<Word> words(String spoken) {
        Matcher matcher = WORDS.matcher(spoken);
        List<Word> found = new ArrayList<>();
        while (matcher.find()) {
            String text = matcher.group().toLowerCase(java.util.Locale.ROOT);
            int vowels = 0;
            boolean wasVowel = false;
            for (int i=0; i<text.length(); i++) {
                char ch=text.charAt(i);
                boolean vowel = "aeiouáéíóúü".indexOf(ch) >= 0;
                if (vowel && !wasVowel) vowels++;
                wasVowel = vowel;
            }
            found.add(new Word(matcher.start(), matcher.end(), .28 + Math.max(1, vowels)));
        }
        return found;
    }

    private static double avg(float[] energies, int from, int to) {
        int begin = Math.max(0, from), end = Math.min(energies.length - 1, to);
        double sum = 0;
        for(int i=begin;i<=end;i++) sum += energies[i];
        return sum / Math.max(1, end - begin + 1);
    }

    private static double pcmValue(byte[] data, int start, int bits) {
        if (bits == 8) return ((data[start] & 255) - 128) / 128.0;
        int sample = 0;
        for (int i=0;i<bits/8;i++) sample |= (data[start+i] & 255) << (8*i);
        int shift = 32 - bits;
        sample = (sample << shift) >> shift; // sign extend 16/24/32-bit PCM
        return sample / Math.pow(2, bits-1);
    }
}
