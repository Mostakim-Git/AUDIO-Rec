import com.mostakim.audiorec.audio.AiffWriter;
import com.mostakim.audiorec.audio.AudioSink;
import com.mostakim.audiorec.audio.FlacWriter;
import com.mostakim.audiorec.audio.OggWriter;
import com.mostakim.audiorec.audio.Pcm;
import com.mostakim.audiorec.ui.kit.SliderMath;
import com.mostakim.audiorec.audio.RawPcmReader;
import com.mostakim.audiorec.audio.WavWriter;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Random;

/**
 * AUDIO-rec :: container + reader self-test.
 *
 * The capture layer is plain Java (only the Opus path needs the platform), so it
 * can be exercised on a desktop JVM.  This harness renders one deterministic,
 * deliberately awkward signal into every container the app can record, then:
 *
 *   1. writes the *reference* PCM next to each file (the exact bytes the sink
 *      was supposed to produce),
 *   2. reads WAV/AIFF back through RawPcmReader and compares sample-for-sample
 *      against the reference,
 *   3. writes a synthetic Ogg stream (packets of every awkward length) plus the
 *      packet list it expects a reader to reassemble.
 *
 * tools/format_check.py then re-decodes every container from the specification
 * side and fails the run if a byte differs, so headers, endianness, chunk sizes,
 * lacing, CRCs and granule positions are all verified independently.
 *
 *   java -cp <classes> FormatSelfTest <out-dir>
 */
public class FormatSelfTest {

    static final int SECONDS = 2;

    // xorshift32, mirrored in the Python verifier for the noise segment
    static int seed = 0x12345678;

    static int xs() {
        seed ^= seed << 13;
        seed ^= seed >>> 17;
        seed ^= seed << 5;
        return seed;
    }

    /** silence, tone, sweep, DC, noise, clipped burst - per channel variants */
    static float sample(int i, int c, int rate, int total, int channels) {
        if (i >= total) return 0f;
        int half = rate / 2;
        int seg = i / half;
        double t = (i % half) / (double) rate;
        double v;
        switch (seg % 6) {
            case 0: v = 0; break;
            case 1: v = 0.5 * Math.sin(2 * Math.PI * (440 + 37 * c) * t); break;
            case 2: v = 0.8 * Math.sin(2 * Math.PI * (200 + 3000 * t) * t + c); break;
            case 3: v = 0.25 - 0.05 * c; break;
            case 4: v = (xs() / 2147483648.0) * (0.7 - 0.05 * c); break;
            default:
                v = Math.sin(2 * Math.PI * (100 + 11 * c) * t) * (t < 0.1 ? 1.9 : 0.3);
                break;
        }
        if (c == channels - 1 && channels > 1) v = v * 0.9 + 0.02;
        return (float) v;
    }

    static float[] render(int rate, int channels, int total) {
        float[] pcm = new float[total * channels];
        seed = 0x12345678;
        for (int i = 0; i < total; i++) {
            for (int c = 0; c < channels; c++) pcm[i * channels + c] = sample(i, c, rate, total, channels);
        }
        return pcm;
    }

    // ------------------------------------------------------------------ refs --
    /** the exact payload the writer is expected to emit, in little-endian form */
    static byte[] refLe(float[] pcm, int samples, int depth) {
        byte[] out = new byte[samples * (depth / 8)];
        if (depth == 32) {
            for (int i = 0; i < samples; i++) {
                int v = Float.floatToIntBits(pcm[i]);
                out[i * 4] = (byte) (v & 0xFF);
                out[i * 4 + 1] = (byte) ((v >> 8) & 0xFF);
                out[i * 4 + 2] = (byte) ((v >> 16) & 0xFF);
                out[i * 4 + 3] = (byte) ((v >> 24) & 0xFF);
            }
        } else if (depth == 16) {
            byte[] tmp = new byte[samples * 2];
            for (int i = 0; i < samples; i++) {
                int v = Pcm.clampShort((int) Math.rint(pcm[i] * 32768.0));
                tmp[i * 2] = (byte) (v & 0xFF);
                tmp[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
            }
            return tmp;
        } else {
            Pcm.floatToInt24(pcm, samples, out, false, null);
        }
        return out;
    }

    static byte[] refBe(float[] pcm, int samples, int depth) {
        byte[] out = new byte[samples * (depth / 8)];
        if (depth == 16) {
            Pcm.floatToShortBigEndian(pcm, samples, out, false, null);
        } else {
            Pcm.floatToInt24BE(pcm, samples, out, false, null);
        }
        return out;
    }

    static void write(File f, byte[] data) throws IOException {
        FileOutputStream fos = new FileOutputStream(f);
        try {
            fos.write(data);
        } finally {
            fos.close();
        }
    }

    static boolean same(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    static int failures = 0;
    static int checks = 0;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL  " + what);
        }
    }

    // ------------------------------------------------------------------ sink --
    static void testSink(String label, AudioSink sink, int rate, int channels, int depth,
                         boolean bigEndian, File dir) throws IOException {
        int total = rate * SECONDS;
        float[] pcm = render(rate, channels, total);
        int samples = total * channels;

        File out = new File(dir, label + ".bin");
        sink.open(out, rate, channels, depth);
        // write in awkward block sizes - the capture path never uses one big chunk
        int pos = 0;
        Random rnd = new Random(7);
        // the sink contract is whole frames: samples = frames * channels
        int frameCap = 65536 / channels;
        while (pos < samples) {
            int chunk = Math.min(samples - pos, channels * (1 + rnd.nextInt(Math.min(4096, frameCap))));
            float[] part = new float[chunk];
            System.arraycopy(pcm, pos, part, 0, chunk);
            sink.write(part, chunk);
            pos += chunk;
        }
        long beforeClose = sink.bytesWritten();
        sink.close(total);

        int wroteDepth = sink instanceof FlacWriter ? FlacWriter.clampDepth(depth) : depth;
        byte[] expect = bigEndian ? refBe(pcm, samples, wroteDepth) : refLe(pcm, samples, wroteDepth);
        write(new File(dir, label + (bigEndian ? ".be.pcm" : ".pcm")), expect);

        System.out.printf("  %-26s %-6s %5d Hz %dch %2d-bit  %8d bytes on disk  %s%n",
                label, sink.container(), rate, channels, depth, out.length(),
                sink.stats() == null ? "" : sink.stats());
        // WAV/AIFF stream every byte as it goes, so their counter must already
        // equal the file; FLAC still holds the last block back at that point and
        // only catches up in close() (where it also patches STREAMINFO).
        if (sink instanceof WavWriter || sink instanceof AiffWriter) {
            check(beforeClose == out.length() || (sink instanceof AiffWriter
                            && out.length() - beforeClose == 1),
                  label + ": bytesWritten " + beforeClose + " != file length " + out.length()
                          + (sink instanceof AiffWriter ? " (odd-payload pad)" : ""));
        } else {
            check(sink.bytesWritten() == out.length(),
                  label + ": FLAC counter " + sink.bytesWritten() + " != " + out.length());
        }
        check(out.length() > 44, label + ": file not empty");

        // ---- read back through the app's own reader (WAV/AIFF only)
        if (sink instanceof WavWriter || sink instanceof AiffWriter) {
            RawPcmReader r = RawPcmReader.open(out);
            check(r != null, label + ": RawPcmReader refused the file");
            if (r != null) {
                check(r.sampleRate == rate, label + ": reader rate " + r.sampleRate);
                check(r.channels == channels, label + ": reader channels " + r.channels);
                check(r.bitDepth == depth, label + ": reader depth " + r.bitDepth);
                check(r.frames == total, label + ": reader frames " + r.frames + " != " + total);
                check(r.dataBytes == (long) samples * (depth / 8),
                        label + ": data size " + r.dataBytes);
                // the reader hands back at most one scratch buffer per call, so a
                // caller loops - exactly like ExportTask and PlaybackEngine do
                float[] got = new float[samples];
                int readCap = Math.max(1, 65536 / (channels * (depth / 8)));
                float[] buf = new float[readCap * channels];
                int gotFrames = 0, n;
                while (gotFrames < total && (n = r.read(buf, total - gotFrames)) > 0) {
                    System.arraycopy(buf, 0, got, gotFrames * channels, n * channels);
                    gotFrames += n;
                }
                check(gotFrames == total, label + ": reader returned " + gotFrames + " frames");
                double scale = depth == 16 ? 32768.0 : (depth == 24 ? 8388608.0 : 1.0);
                double worst = 0;
                for (int i = 0; i < samples; i++) {
                    float want;
                    if (depth == 32) {
                        want = pcm[i];
                    } else {
                        int b = depth / 8;
                        int iv = 0;
                        for (int k = 0; k < b; k++) {
                            int by = expect[i * b + k] & 0xFF;
                            iv |= bigEndian ? by << (8 * (b - 1 - k)) : by << (8 * k);
                        }
                        if (depth == 24 && (iv & 0x800000) != 0) iv |= 0xFF000000;
                        if (depth == 16) iv = (short) iv;
                        want = (float) (iv / scale);
                    }
                    double d = Math.abs(got[i] - want);
                    if (d > worst) worst = d;
                }
                check(worst == 0.0, label + ": reader samples differ (worst " + worst + ")");
                r.close();
            }
        }
    }

    // ------------------------------------------------------------------- ogg --
    static void testOgg(File dir) throws IOException {
        File out = new File(dir, "opus-shape.ogg");
        File refFile = new File(dir, "opus-shape.oggpkts");
        DataOutputStream ref = new DataOutputStream(new FileOutputStream(refFile));
        OggWriter w = new OggWriter(0x5ADEC0DE);
        w.open(out);

        int[] sizes = {19, 120, 0, 1, 254, 255, 256, 509, 1275, 1275, 40, 3, 6000, 255};
        byte[][] packets = new byte[sizes.length][];
        for (int i = 0; i < sizes.length; i++) {
            packets[i] = new byte[sizes[i]];
            Random rnd = new Random(1000 + i);
            rnd.nextBytes(packets[i]);
        }
        w.writeBosPage(packets[0]);
        long granule = 0;
        for (int i = 1; i < packets.length; i++) {
            granule += 960;
            w.writePacket(packets[i], granule);
        }
        w.finish(granule);

        for (byte[] p : packets) {
            ref.writeInt(p.length);
            ref.write(p);
        }
        ref.close();
        System.out.printf("  %-26s %-6s %5d packets %8d bytes%n", "opus-shape", "OGG",
                packets.length, w.bytesWritten());
        check(out.length() > 100, "ogg: file not empty");
    }

    // ------------------------------------------------------------------ main --
    /**
     * Turning a device read into frames.
     *
     * A PCM read counts bytes while a float read counts samples, and the capture
     * loop used to divide the count by the channel count in both cases.  For
     * every byte-based encoding that inflated the frame count by the sample
     * width, so the block that had just been read was indexed past its end and
     * the capture thread died - which on Android takes the whole process down.
     * This is the arithmetic that has to stay boring.
     */
    static void testReadArithmetic() {
        for (int ch = 1; ch <= 8; ch++) {
            for (int width = 2; width <= 4; width++) {
                int frames = 1024;
                int bytes = frames * ch * width;
                int blockSamples = frames * ch;
                String what = width + "-byte, " + ch + " ch";
                check(Pcm.framesFromBytes(bytes, ch, width) == frames,
                        "a full read of " + what + " is " + frames + " frames");
                check(Pcm.samplesFromBytes(bytes, width) == blockSamples,
                        "a full read of " + what + " is " + blockSamples + " samples");
                check(Pcm.samplesFromBytes(bytes, width) <= blockSamples,
                        "a full read of " + what + " fits the block");
                // every partial read must stay inside the block too
                for (int missing = 1; missing < width * ch; missing++) {
                    check(Pcm.samplesFromBytes(bytes - missing, width) <= blockSamples,
                            "a short read of " + what + " (" + missing + " bytes missing) fits");
                    check(Pcm.framesFromBytes(bytes - missing, ch, width) ==
                                    (bytes - missing) / (ch * width),
                            "a short read of " + what + " rounds down to whole frames");
                }
            }
        }

        // the exact shape that crashed: 4096 bytes is one 1024-frame 16-bit stereo read
        check(Pcm.framesFromBytes(4096, 2, 2) == 1024,
                "4096 bytes of 16-bit stereo is 1024 frames, not " + (4096 / 2));
        check(Pcm.samplesFromBytes(4096, 2) == 2048, "4096 bytes of 16-bit audio is 2048 samples");
        check(Pcm.samplesFromBytes(4096, 3) == 1365, "4096 bytes of 24-bit audio is 1365 samples");
        check(Pcm.samplesFromBytes(4096, 4) == 1024, "4096 bytes of 32-bit audio is 1024 samples");
        check(Pcm.framesFromBytes(4096, 6, 3) == 227, "4096 bytes of 24-bit 6-channel is 227 frames");

        // nothing negative or partial may ever become a frame count
        check(Pcm.samplesFromBytes(0, 2) == 0, "an empty read is zero samples");
        check(Pcm.framesFromBytes(0, 2, 2) == 0, "an empty read is zero frames");
        check(Pcm.framesFromBytes(-2, 2, 2) == 0, "an error result is zero frames");
        check(Pcm.samplesFromBytes(-16, 4) == 0, "a negative read is zero samples");
        check(Pcm.samplesFromBytes(3, 4) == 0, "a partial 32-bit sample is dropped");
        check(Pcm.samplesFromBytes(2, 3) == 0, "a partial 24-bit sample is dropped");
        check(Pcm.framesFromBytes(5, 2, 2) == 1, "a partial frame still yields whole frames");
    }

    /**
     * The gain and monitor faders.
     *
     * The operator asked for controls that move in steps of exactly 0.1 dB in
     * both directions, so the step is checked in both directions, from a range of
     * starting points, and around the values a mixing desk actually sits at.
     */
    static void testSliderMath() {
        // a step is a step, whichever way it goes and wherever it starts
        // every tenth of a decibel near unity, a coarser sweep across the rest
        for (float start = -24f; start <= 24f; start += (start >= -1f && start <= 1f ? 0.1f : 0.7f)) {
            float up = SliderMath.quantizeDb(SliderMath.quantizeDb(start) + 0.1f);
            float down = SliderMath.quantizeDb(SliderMath.quantizeDb(start) - 0.1f);
            check(Math.abs((up - SliderMath.quantizeDb(start)) - 0.1f) < 1e-5f,
                    "a +0.1 step from " + start + " moves by a tenth");
            check(Math.abs((SliderMath.quantizeDb(start) - down) - 0.1f) < 1e-5f,
                    "a -0.1 step from " + start + " moves by a tenth");
        }
        // 0.1 is representable in the readout, not 0.09999999
        check(SliderMath.quantizeDb(0.1f) == 0.1f, "0.1 dB survives quantisation");
        check(SliderMath.quantizeDb(0.04f) == 0f, "0.04 dB snaps down to unity");
        check(SliderMath.quantizeDb(0.06f) == 0.1f, "0.06 dB snaps up to +0.1");
        check(SliderMath.quantizeDb(-0.06f) == -0.1f, "a nudge down snaps to -0.1");
        check(Math.abs(SliderMath.quantizeDb(0.3f) - 0.3f) < 1e-6f,
                "three steps land on exactly 0.3");
        // a nudge down and back up returns to where it started
        float v = 0f;
        for (int i = 0; i < 7; i++) v = SliderMath.quantizeDb(v + 0.1f);
        for (int i = 0; i < 7; i++) v = SliderMath.quantizeDb(v - 0.1f);
        check(v == 0f, "seven steps up and seven back is exactly unity, got " + v);

        // the range is honoured at both ends
        check(SliderMath.clamp(99f, -24f, 24f) == 24f, "a fader cannot exceed its top");
        check(SliderMath.clamp(-99f, -60f, 12f) == -60f, "a fader cannot go below its floor");
        check(SliderMath.quantize(1000f, 0.1f) == 1000f, "quantise leaves an exact value alone");
        check(SliderMath.quantize(0f, 0f) == 0f, "a zero step is not a division by zero");

        // the position maths: top is the maximum, bottom is the minimum
        check(SliderMath.valueAt(0f, 0f, 100f, -24f, 24f) == 24f, "the top of the fader is the top value");
        check(SliderMath.valueAt(100f, 0f, 100f, -24f, 24f) == -24f, "the bottom of the fader is the floor");
        check(Math.abs(SliderMath.valueAt(50f, 0f, 100f, -24f, 24f)) < 1e-5f, "the middle is unity");
        check(SliderMath.valueAt(50f, 100f, 100f, -24f, 24f) == -24f,
                "a fader with no room left reports the floor, not NaN");

        // dragging: up is louder, down is quieter, and fine mode is one eighth
        // a small movement, so neither drag reaches the clamp
        float coarse = SliderMath.dragValue(0f, 100f, 98f, 0f, 100f, -60f, 12f, false);
        float fine = SliderMath.dragValue(0f, 100f, 98f, 0f, 100f, -60f, 12f, true);
        check(coarse > 0f, "dragging up raises the gain");
        check(fine > 0f && Math.abs(fine - coarse * SliderMath.FINE_FACTOR) < 1e-4f,
                "fine mode moves an eighth as far (" + fine + " vs " + coarse + ")");
        // and a big drag stops at the top of the range instead of running away
        check(SliderMath.dragValue(0f, 100f, 0f, 0f, 100f, -60f, 12f, false) == 12f,
                "a drag past the top pins at +12 dB");
        check(SliderMath.dragValue(0f, 0f, 100f, 0f, 100f, -60f, 12f, false) == -60f,
                "a drag past the bottom pins at the floor");
        check(SliderMath.dragValue(0f, 50f, 60f, 0f, 100f, -60f, 12f, false) < 0f,
                "dragging down lowers the gain");
        check(!SliderMath.isFine(0L, SliderMath.FINE_AFTER_MS - 1),
                "a quick touch is a coarse drag");
        check(SliderMath.isFine(0L, SliderMath.FINE_AFTER_MS),
                "a held touch turns into a fine drag");

        // the readout a mixing desk shows
        check("+0.1 dB".equals(SliderMath.formatDb(0.1f, "dB")), "+0.1 dB reads with its sign");
        check("0.0 dB".equals(SliderMath.formatDb(0f, "dB")), "unity reads without a sign");
        check("-6.0 dB".equals(SliderMath.formatDb(-6f, "dB")), "-6 dB reads with one decimal");
        System.out.println("slider checks: " + (checks - sliderMark) + " passed");
    }

    private static int sliderMark;

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "/tmp/audiorec-fmt");
        dir.mkdirs();
        System.out.println("AUDIO-rec :: container self-test -> " + dir);

        sliderMark = checks;
        testSliderMath();

        int[][] wav = {{44100, 1, 16}, {48000, 2, 16}, {48000, 2, 24}, {96000, 2, 24},
                {48000, 4, 24}, {48000, 8, 24}, {48000, 2, 32}, {384000, 2, 16}};
        for (int[] c : wav) {
            WavWriter s = new WavWriter();
            s.setDither(false);
            testSink(String.format("wav-%d-%d-%d", c[0], c[1], c[2]), s, c[0], c[1], c[2], false, dir);
        }

        int[][] aiff = {{44100, 1, 16}, {48000, 2, 16}, {48000, 2, 24}, {96000, 4, 24}};
        for (int[] c : aiff) {
            AiffWriter s = new AiffWriter();
            s.setDither(false);
            testSink(String.format("aiff-%d-%d-%d", c[0], c[1], c[2]), s, c[0], c[1], c[2], true, dir);
        }

        int[][] flac = {{44100, 1, 16}, {48000, 2, 16}, {48000, 2, 24}, {96000, 2, 24},
                {48000, 8, 24}, {384000, 2, 16}};
        for (int[] c : flac) {
            FlacWriter s = new FlacWriter();
            s.setDither(false);
            testSink(String.format("flac-%d-%d-%d", c[0], c[1], c[2]), s, c[0], c[1], c[2], false, dir);
        }

        testOgg(dir);
        testReadArithmetic();

        System.out.println();
        System.out.println(failures == 0
                ? "in-process checks: " + checks + " passed"
                : "in-process checks: " + (checks - failures) + "/" + checks + " passed, "
                  + failures + " FAILED");

        // the Ogg/Opus resampler: pure maths, no MediaCodec needed
        failures += ResamplerCheck.run();

        // the rules that decide what may be shared through the content provider
        failures += ShareCheck.run();

        // the reader side: files written by tools/test/gen_foreign.py, which no
        // AUDIO-rec writer ever touched
        File foreign = new File(dir, "foreign");
        if (!new File(foreign, "expected.txt").isFile()) {
            foreign = new File(dir.getParentFile() == null ? dir : dir.getParentFile(), "foreign");
        }
        if (new File(foreign, "expected.txt").isFile()) {
            int readerFailures = ReaderCheck.run(foreign);
            if (readerFailures != 0) failures += readerFailures;
            ReaderCheck.printSummary();
            System.out.println(readerFailures == 0
                    ? "combined: in-process + resampler + foreign reader checks passed"
                    : "combined: " + readerFailures + " foreign reader check(s) FAILED");
        } else {
            System.out.println("foreign reader corpus not found at " + foreign
                    + " - build it with: python3 tools/test/gen_foreign.py " + foreign);
        }

        if (failures != 0) System.exit(1);
    }
}
