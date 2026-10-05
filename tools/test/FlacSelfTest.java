import com.mostakim.audiorec.audio.FlacWriter;
import com.mostakim.audiorec.audio.WavWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;

/**
 * AUDIO-rec encoder self-test.
 *
 * Renders a deterministic, deliberately awkward signal (silence, DC, a full
 * scale sweep, noise, a clipped burst and a square wave) into WAV and FLAC and
 * writes the reference PCM to a raw file.  tools/flac_check.py then decodes the
 * FLAC with an independently written decoder and compares sample-for-sample,
 * so both the container and the entropy coder are verified.
 *
 *   java -cp out FlacSelfTest <out-dir> [rate] [channels] [depth]
 */
public class FlacSelfTest {

    static int RATE = 48000, CH = 2, DEPTH = 16;

    /** identical generator on the Python side: xorshift32 */
    static int seed = 0x12345678;

    static int xs() {
        seed ^= seed << 13;
        seed ^= seed >>> 17;
        seed ^= seed << 5;
        return seed;
    }

    static float nextSample(int i, int c) {
        int total = RATE * 3;
        int seg = i / (RATE / 2);
        double t = (i % (RATE / 2)) / (double) RATE;
        double v;
        switch (seg % 6) {
            case 0: v = 0; break;                                        // digital silence
            case 1: v = 0.5 * Math.sin(2 * Math.PI * 440 * t); break;    // steady tone
            case 2: v = 0.8 * Math.sin(2 * Math.PI * (200 + 3000 * t) * t); break;
            case 3: v = 0.25; break;                                     // DC offset
            case 4: v = (xs() / 2147483648.0) * 0.7; break;              // noise
            default:
                v = Math.sin(2 * Math.PI * 100 * t) * (t < 0.1 ? 1.9 : 0.3); // clipped burst
                break;
        }
        // a second channel that is deliberately correlated (stereo-ish) 
        if (c == 1) v = v * 0.92 + 0.03 * Math.sin(2 * Math.PI * 660 * t);
        if (i >= total) return 0;
        return (float) v;
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "/tmp/flactest");
        if (args.length > 1) RATE = Integer.parseInt(args[1]);
        if (args.length > 2) CH = Integer.parseInt(args[2]);
        if (args.length > 3) DEPTH = Integer.parseInt(args[3]);
        dir.mkdirs();

        int totalFrames = RATE * 3;
        int block = 2048;
        File wav = new File(dir, "ref.wav");
        File flac = new File(dir, "test.flac");
        File raw = new File(dir, "ref.pcm");

        WavWriter ww = new WavWriter();
        ww.open(wav, RATE, CH, DEPTH);
        FlacWriter fw = new FlacWriter();
        fw.open(flac, RATE, CH, DEPTH);

        RandomAccessFile raf = new RandomAccessFile(raw, "rw");
        float[] buf = new float[block * CH];
        int written = 0;
        while (written < totalFrames) {
            int frames = Math.min(block, totalFrames - written);
            for (int f = 0; f < frames; f++) {
                for (int c = 0; c < CH; c++) {
                    buf[f * CH + c] = nextSample(written + f, c);
                }
            }
            int samples = frames * CH;
            ww.write(buf, samples);
            fw.write(buf, samples);
            written += frames;
        }
        ww.close(totalFrames);
        fw.close(totalFrames);
        raf.close();

        // report the raw float signal so Python can check the exact scaling
        FileOutputStream fos = new FileOutputStream(new File(dir, "signal.raw"));
        java.io.DataOutputStream dos = new java.io.DataOutputStream(fos);
        for (int i = 0; i < totalFrames; i++) {
            for (int c = 0; c < CH; c++) dos.writeFloat(nextSample(i, c));
        }
        dos.close();

        System.out.println("wrote " + wav.length() + " B wav, " + flac.length()
                + " B flac  (ratio " + String.format(java.util.Locale.US, "%.1f%%",
                100.0 * flac.length() / wav.length()) + ")");
        System.out.println("rate=" + RATE + " ch=" + CH + " depth=" + DEPTH
                + " frames=" + totalFrames);
    }
}
