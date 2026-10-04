import com.mostakim.audiorec.audio.AiffWriter;
import com.mostakim.audiorec.audio.AudioSink;
import com.mostakim.audiorec.audio.FlacWriter;
import com.mostakim.audiorec.audio.OggWriter;
import com.mostakim.audiorec.audio.Pcm;
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
    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "/tmp/audiorec-fmt");
        dir.mkdirs();
        System.out.println("AUDIO-rec :: container self-test -> " + dir);

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

        System.out.println();
        System.out.println(failures == 0
                ? "in-process checks: " + checks + " passed"
                : "in-process checks: " + (checks - failures) + "/" + checks + " passed, "
                  + failures + " FAILED");

        // the Ogg/Opus resampler: pure maths, no MediaCodec needed
        failures += ResamplerCheck.run();

        // the reader side: files written by tools/test/gen_foreign.py, which no
        // AUDIO-rec writer ever touched
        File foreign = new File(dir, "foreign");
        if (!new File(foreign, "expected.txt").isFile()) {
            foreign = new File(dir.getParentFile() == null ? dir : dir.getParentFile(), "foreign");
        }
        if (new File(foreign, "expected.txt").isFile()) {
            int readerFailures = ReaderCheck.run(foreign);
            if (readerFailures != 0) failures += readerFailures;
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
