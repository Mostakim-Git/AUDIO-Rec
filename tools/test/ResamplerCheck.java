import com.mostakim.audiorec.audio.Pcm;

import java.util.Arrays;
import java.util.Random;

/**
 * Pure-JVM checks for Pcm.Resampler, the block-wise resampler that feeds the
 * 48 kHz Ogg/Opus encoder when the interface runs at another rate.
 *
 * The property that matters is that the output must not depend on where the
 * capture blocks happen to be cut - AudioRecord hands us whatever the driver
 * gives - so every case is compared against a single-pass reference and against
 * the same signal fed in irregular chunks.
 *
 * The first case is a negative control: the per-block algorithm this code
 * replaced (snap the fractional position back to zero at every block joint)
 * must FAIL the same check, otherwise the test is not measuring anything.
 */
public class ResamplerCheck {

    private static int passed, failed;
    private static final double[] RATES = {8000, 11025, 16000, 22050, 32000, 37000,
            44100, 48000, 88200, 96000, 176400, 192000};
    private static final int OPUS_RATE = 48000;
    private static final int FRAMES = 40000;

    public static void main(String[] args) {
        if (run() != 0) System.exit(1);
    }

    /** called from FormatSelfTest; returns the number of failures */
    public static int run() {
        System.out.println("-- 48 kHz resampler (Ogg/Opus path) --");

        // ---- negative control: the algorithm this replaced -------------------
        double deficit = naiveDeficit(44100, 2, FRAMES, 1024);
        check(deficit > 0.0002, String.format(
                "negative control: the old per-block resampler should lose samples, lost %.5f%%",
                deficit * 100));

        // ---- the real one ---------------------------------------------------
        for (double rate : RATES) {
            one(rate, 2);
        }
        one(44100, 1);
        one(48000, 8);
        one(96000, 4);

        identity48k();
        channelIsolation();
        quietInputs();

        // ---- chunking in singles and odds and ends --------------------------
        double[] single = collect(44100, 2, 3000, new int[]{1});
        double[] reference = reference(signal(44100, 2, 3000), 3000, 2, 44100.0 / OPUS_RATE);
        double worst = worstDiff(single, reference);
        check(single.length == reference.length && worst <= 1e-6,
                String.format("44100 Hz in blocks of 1 frame: %d vs %d samples, worst %.3e",
                        single.length, reference.length, worst));
        System.out.println("resampler checks: " + passed + " passed"
                + (failed > 0 ? ", " + failed + " FAILED" : ""));
        return failed;
    }

    // ------------------------------------------------------------------ cases --
    private static void one(double rate, int channels) {
        float[] in = signal(rate, channels, FRAMES);
        double step = rate / (double) OPUS_RATE;
        double[] ref = reference(in, FRAMES, channels, step);
        int[] chunks = irregularChunks(FRAMES, 0xC0FFEE + channels);
        double[] got = collect(in, FRAMES, channels, step, chunks);

        // positions n*step that fit inside the input, plus the last frame held
        long ideal = (long) Math.floor((FRAMES - 1) / step) + 1;
        check(Math.abs(got.length / channels - ideal) <= 2L * channels, String.format(
                "%d Hz/%dch: %d output frames, expected about %d", (int) rate, channels,
                got.length / channels, ideal));
        check(Math.abs(got.length - ref.length) <= channels, String.format(
                "%d Hz/%dch: %d output samples, the single-pass resample gives %d",
                (int) rate, channels, got.length, ref.length));
        int n = Math.min(got.length, ref.length);
        double worst = worstDiff(Arrays.copyOf(got, n), Arrays.copyOf(ref, n));
        // 5 kHz at 192 kHz linear interpolation is the worst case; 2e-4 is still
        // two orders below one 16-bit LSB
        check(worst <= 2e-4, String.format(
                "%d Hz/%dch: differs from a single-pass resample by %.3e at sample %d (of %d/%d)",
                (int) rate, channels, worst, worstAt(got, ref), got.length, ref.length));
        System.out.printf("  %6d Hz %dch  %7d -> %7d frames  worst %.2e%n",
                (int) rate, channels, FRAMES, got.length / channels, worst);
    }

    private static void identity48k() {
        float[] in = signal(48000, 2, 5000);
        double[] got = collect(in, 5000, 2, 1.0, irregularChunks(5000, 7));
        int brokeAt = -1;
        for (int i = 0; i < 5000 && got.length == 10000; i++) {
            for (int c = 0; c < 2; c++) {
                if (got[i * 2 + c] != in[i * 2 + c]) {
                    brokeAt = i;
                    break;
                }
            }
            if (brokeAt >= 0) break;
        }
        check(got.length == 10000 && brokeAt < 0, "48000 Hz capture must pass through untouched ("
                + got.length / 2 + " frames" + (brokeAt < 0 ? "" : ", first difference at frame "
                + brokeAt + ": " + got[brokeAt * 2] + " != " + in[brokeAt * 2]) + ")");
    }

    private static void channelIsolation() {
        int channels = 8, frames = 2000;
        float[] in = new float[frames * channels];
        for (int i = 0; i < frames; i++) {
            for (int c = 0; c < channels; c++) in[i * channels + c] = c * 0.1f - 0.4f;
        }
        double[] got = collect(in, frames, channels, 44100.0 / OPUS_RATE, irregularChunks(frames, 3));
        double worst = 0;
        for (int i = 0; i < got.length / channels; i++) {
            for (int c = 0; c < channels; c++) {
                worst = Math.max(worst, Math.abs(got[i * channels + c] - (c * 0.1 - 0.4)));
            }
        }
        check(worst <= 1e-6, String.format("8-channel constants must stay in their channel (worst %.2e)", worst));
    }

    private static void quietInputs() {
        Pcm.Resampler r = new Pcm.Resampler(2, 44100.0 / OPUS_RATE);
        float[] out = new float[2048 * 2];
        check(r.available() == 0 && r.read(out, 0, 2048) == 0, "an empty resampler produces nothing");
        r.feed(new float[0], 0);
        check(r.read(out, 0, 2048) == 0, "feeding zero frames is a no-op");
        r.feed(new float[]{1f, -1f}, 1);           // a single frame: nothing to interpolate yet
        check(r.read(out, 0, 2048) == 0, "one frame is not enough to interpolate");
        r.feed(new float[]{0.5f, -0.5f}, 1);
        int n = r.read(out, 0, 2048);
        // step 0.91875: two input frames carry two output positions (0 and 0.91875)
        // frame 0 is (1, -1), frame 1 is (0.5, -0.5); at 0.91875 of the way:
        // 1 + (0.5 - 1) * 0.91875 = 0.540625
        check(n == 2 && out[0] == 1f && out[1] == -1f
                        && Math.abs(out[2] - 0.540625f) < 1e-6 && out[3] == -0.540625f,
                "two frames give the expected span (got " + n + " frames, " + out[0] + ","
                        + out[2] + ")");
        r.reset();
        check(r.available() == 0 && r.read(out, 0, 2048) == 0, "reset clears the carry state");
    }

    // ------------------------------------------------------------- machinery --
    private static float[] signal(double rate, int channels, int frames) {
        float[] out = new float[frames * channels];
        for (int i = 0; i < frames; i++) {
            for (int c = 0; c < channels; c++) {
                double t = i / rate;
                double v = 0.6 * Math.sin(2 * Math.PI * 1000 * t)
                        + 0.3 * Math.sin(2 * Math.PI * 997 * t + c * 0.5);
                out[i * channels + c] = (float) v;
            }
        }
        return out;
    }

    /** one block, the definitive answer for a given signal */
    private static double[] reference(float[] in, int inFrames, int channels, double step) {
        double[] out = new double[((int) (inFrames / step) + 4) * channels];
        int made = 0;
        double pos = 0;
        while (pos <= inFrames - 1) {
            int i0 = (int) pos;
            double frac = pos - i0;
            int i1 = Math.min(inFrames - 1, i0 + 1);
            for (int c = 0; c < channels; c++) {
                double a = in[i0 * channels + c], b = in[i1 * channels + c];
                out[made * channels + c] = a + (b - a) * frac;
            }
            made++;
            pos += step;
        }
        return Arrays.copyOf(out, made * channels);
    }

    /** the production resampler, fed in irregular blocks */
    private static double[] collect(double rate, int channels, int frames, int[] chunks) {
        return collect(signal(rate, channels, frames), frames, channels, rate / OPUS_RATE, chunks);
    }

    private static double[] collect(float[] in, int frames, int channels, double step, int[] chunks) {
        Pcm.Resampler r = new Pcm.Resampler(channels, step);
        int cap = (int) (frames / step) + 64;
        float[] out = new float[cap * channels];
        int made = 0, off = 0, ci = 0;
        while (off < frames) {
            int n = Math.min(chunks[ci++ % chunks.length], frames - off);
            r.feed(Arrays.copyOfRange(in, off * channels, (off + n) * channels), n);
            off += n;
            while (true) {
                int got = r.read(out, made, cap - made);
                if (got == 0) break;
                made += got;
            }
        }
        double[] res = new double[made * channels];
        for (int i = 0; i < res.length; i++) res[i] = out[i];
        return res;
    }

    private static int[] irregularChunks(int frames, int seed) {
        Random rnd = new Random(seed);
        Random big = new Random(seed * 31 + 7);
        int[] chunks = new int[frames / 64 + 64];
        for (int i = 0; i < chunks.length; i++) {
            // mostly 1024/2048/4096 like an AudioRecord buffer, sometimes tiny
            chunks[i] = (i % 5 == 4) ? 1 + rnd.nextInt(7) : 1024 << big.nextInt(3);
        }
        return chunks;
    }

    private static int worstAt(double[] a, double[] b) {
        int at = 0;
        double worst = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            double d = Math.abs(a[i] - b[i]);
            if (d > worst) {
                worst = d;
                at = i;
            }
        }
        return at;
    }

    private static double worstDiff(double[] a, double[] b) {
        double worst = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            worst = Math.max(worst, Math.abs(a[i] - b[i]));
        }
        return worst;
    }

    /**
     * The per-block algorithm that used to live in OggOpusWriter: emit while the
     * position is inside the block, then subtract the block length and clamp the
     * carry at zero.  Returns the fraction of output samples lost at that rate.
     */
    private static double naiveDeficit(double rate, int channels, int frames, int block) {
        double step = rate / OPUS_RATE;
        double pos = 0;
        long made = 0;
        for (int off = 0; off < frames; off += block) {
            int n = Math.min(block, frames - off);
            while (pos < n - 1) {
                made++;
                pos += step;
            }
            pos -= n;
            if (pos < 0) pos = 0;
        }
        double ideal = frames / step;
        return (ideal - made) / ideal;
    }

    private static void check(boolean ok, String what) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("  FAIL " + what);
        }
    }
}
