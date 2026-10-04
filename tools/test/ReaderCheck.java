import com.mostakim.audiorec.audio.RawPcmReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reader-only checks against the "foreign file" corpus built by
 * tools/test/gen_foreign.py.
 *
 * The generator writes files with struct, ReaderCheck has its own copy of the
 * sample formula, and nothing here touches WavWriter/AiffWriter - so a pass
 * means RawPcmReader agrees with an outside implementation, not with itself.
 *
 *   python3 tools/test/gen_foreign.py /tmp/jfmt/foreign
 *   java -cp /tmp/jfmt/classes ReaderCheck /tmp/jfmt/foreign
 */
public class ReaderCheck {

    private static int passed, failed;
    private static File dir;

    public static void main(String[] args) throws Exception {
        File d = new File(args.length > 0 ? args[0] : "/tmp/jfmt/foreign");
        int code = run(d);
        System.out.println();
        System.out.println("foreign reader checks: " + passed + " passed"
                + (failed > 0 ? ", " + failed + " FAILED" : ""));
        if (code != 0) System.exit(code);
    }

    /** entry point for FormatSelfTest */
    public static int run(File d) {
        dir = d;
        if (!new File(d, "expected.txt").isFile()) {
            System.out.println("foreign reader corpus missing - run:"
                    + " python3 tools/test/gen_foreign.py " + d);
            return 0;
        }
        try {
            for (String[] row : readIndex(new File(d, "expected.txt"))) {
                try {
                    one(row);
                } catch (Throwable t) {
                    fail(row[0] + ": threw " + t);
                }
            }
        } catch (IOException e) {
            fail("could not read the index: " + e);
        }
        return failed > 0 ? 1 : 0;
    }

    // ------------------------------------------------------------- machinery --
    private static List<String[]> readIndex(File f) throws IOException {
        List<String[]> out = new ArrayList<>();
        BufferedReader in = new BufferedReader(new FileReader(f));
        try {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                out.add(line.split("\\s+"));
            }
        } finally {
            in.close();
        }
        return out;
    }

    private static void one(String[] r) throws IOException {
        String name = r[0];
        int rate = Integer.parseInt(r[1]), channels = Integer.parseInt(r[2]);
        int depth = Integer.parseInt(r[3]);
        long frames = Long.parseLong(r[4]);
        String kind = r[5].equals("-") ? null : r[5];
        boolean expectNull = r[6].equals("1");
        boolean partial = r[7].equals("1");

        File file = new File(dir, name);
        RawPcmReader reader = RawPcmReader.open(file);

        if (expectNull) {
            check(reader == null, name + ": should be handed to the platform, got "
                    + describe(reader));
            if (reader != null) reader.close();
            return;
        }
        if (reader == null) {
            fail(name + ": could not be opened");
            return;
        }
        try {
            boolean headOk = reader.sampleRate == rate && reader.channels == channels
                    && reader.bitDepth == depth && reader.frames == frames;
            check(headOk, name + ": header " + describe(reader)
                    + " != " + rate + " Hz/" + channels + "ch/" + depth + "bit/" + frames + "f");
            String want = name.endsWith(".iff") ? "iff"
                    : (name.contains(".aif") ? "aiff" : "wav");
            check(want.equals(reader.container), name + ": container " + reader.container
                    + " != " + want);
            long ms = rate > 0 ? frames * 1000L / rate : 0;
            check(reader.durationMs == ms, name + ": duration " + reader.durationMs + " ms != " + ms);

            long got = 0;
            boolean stoppedClean = false;
            float[] buf = new float[4096 * Math.max(1, channels)];
            float worst = 0;
            String mismatch = null;
            try {
                int n;
                while ((n = reader.read(buf, 4096)) > 0) {
                    for (int i = 0; i < n * channels; i++) {
                        if (kind == null) continue;
                        float want2 = expected(kind, got + i / channels, i % channels);
                        float err = Math.abs(buf[i] - want2);
                        if (err > worst) worst = err;
                        if (err > tolerance(kind) && mismatch == null) {
                            mismatch = "frame " + (got + i / channels) + " ch " + (i % channels)
                                    + ": " + buf[i] + " != " + want2;
                        }
                    }
                    got += n;
                    if (got > frames) break;               // reader over-ran its own header
                }
                stoppedClean = true;
            } catch (IOException e) {
                stoppedClean = partial;                    // truncated payload: EOF is fine
                if (!partial) mismatch = mismatch == null ? "read failed: " + e : mismatch;
            }
            if (partial) {
                check(got <= 10, name + ": truncated file returned " + got
                        + " frames, more than the 10 that exist");
            } else {
                check(stoppedClean, name + ": read loop did not finish cleanly");
                check(got == frames, name + ": read " + got + " frames of " + frames);
            }
            if (kind != null) {
                check(mismatch == null, name + ": bad sample - " + mismatch);
                System.out.printf("  %-18s %-8s %5d frames  worst sample error %.3e%n",
                        name, kind, got, worst);
            } else {
                System.out.printf("  %-18s opened, no samples checked%n", name);
            }
        } finally {
            reader.close();
        }
    }

    private static String describe(RawPcmReader r) {
        if (r == null) return "null";
        return r.sampleRate + " Hz/" + r.channels + "ch/" + r.bitDepth + "bit/" + r.frames + "f";
    }

    /** same arithmetic as tools/test/gen_foreign.py */
    private static float expected(String kind, long frame, int ch) {
        long c = (frame * 7 + ch * 13) % 100;
        if (kind.equals("s16") || kind.equals("s16be")) return (c - 50) * 320 / 32768f;
        if (kind.equals("s24") || kind.equals("s24be")) return (c - 50) * 81920 / 8388608f;
        if (kind.equals("u8")) return ((128 + (c - 50) * 2) - 128) / 128f;
        if (kind.equals("f32")) return (c - 50) * 0.02f;
        if (kind.equals("raw_u8")) return (frame - 128) / 128f;
        if (kind.equals("raw_i32")) {
            if (frame == 0) return -1f;
            if (frame == 1) return 2147483647 / 2147483648f;
            return 0f;
        }
        throw new IllegalArgumentException(kind);
    }

    private static float tolerance(String kind) {
        return kind.equals("f32") ? 1e-6f : 0f;
    }

    private static void check(boolean ok, String what) {
        if (ok) {
            passed++;
        } else {
            fail(what);
        }
    }

    private static void fail(String what) {
        failed++;
        System.out.println("  FAIL " + what);
    }
}
