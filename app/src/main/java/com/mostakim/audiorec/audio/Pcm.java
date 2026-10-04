package com.mostakim.audiorec.audio;

import java.util.Random;

/**
 * Sample-format maths for the capture path.
 *
 * The recorder always works on 32-bit float internally: whatever the interface
 * hands us (16-bit, 24-bit packed or float) is normalised to [-1,1], gain and
 * monitoring are applied once in float, and only the writer decides how the
 * samples are stored.  That keeps a single code path for 16/24/32-bit files and
 * makes the level meters independent of the chosen depth.
 */
public final class Pcm {

    private Pcm() {
    }

    /** dBFS floor used everywhere so silence is a finite number */
    public static final float FLOOR_DB = -120f;

    /**
     * Samples in one device read.
     *
     * A PCM read counts *bytes*, not samples: 4096 bytes of 16-bit audio are
     * 2048 samples, 1365 of 24-bit, 1024 of float.  Dividing the byte count by
     * the channel count instead - which is what the capture loop used to do -
     * overruns the block by the sample width, and the resulting
     * ArrayIndexOutOfBoundsException on the capture thread kills the process.
     */
    public static int samplesFromBytes(int byteCount, int bytesPerSample) {
        if (byteCount <= 0 || bytesPerSample <= 0) return 0;
        return byteCount / bytesPerSample;      // a partial sample is dropped
    }

    /** whole interleaved frames in one device read */
    public static int framesFromBytes(int byteCount, int channels, int bytesPerSample) {
        int perFrame = channels * bytesPerSample;
        if (byteCount <= 0 || perFrame <= 0) return 0;
        return byteCount / perFrame;
    }

    private static final float INV_32768 = 1f / 32768f;
    private static final float INV_8388608 = 1f / 8388608f;

    // ------------------------------------------------------------ decoding --
    public static void shortToFloat(short[] in, int n, float[] out) {
        for (int i = 0; i < n; i++) out[i] = in[i] * INV_32768;
    }

    /** packed little-endian 3-byte samples */
    public static void int24ToFloat(byte[] in, int nSamples, float[] out) {
        for (int i = 0; i < nSamples; i++) {
            int b0 = in[i * 3] & 0xFF;
            int b1 = in[i * 3 + 1] & 0xFF;
            int b2 = in[i * 3 + 2];
            int v = (b2 << 16) | (b1 << 8) | b0;
            out[i] = v * INV_8388608;
        }
    }

    public static void int32ToFloat(int[] in, int n, float[] out) {
        for (int i = 0; i < n; i++) out[i] = (float) (in[i] / 2147483648.0);
    }

    // ------------------------------------------------------------ encoding --
    public static void floatToShort(float[] in, int n, short[] out, boolean dither, Random rnd) {
        if (dither) {
            for (int i = 0; i < n; i++)
                out[i] = (short) clampShort((int) Math.rint(in[i] * 32768.0 + tpdf(rnd)));
        } else {
            for (int i = 0; i < n; i++)
                out[i] = (short) clampShort((int) Math.rint(in[i] * 32768.0));
        }
    }

    /** little-endian packed 24-bit */
    public static void floatToInt24(float[] in, int n, byte[] out, boolean dither, Random rnd) {
        for (int i = 0; i < n; i++) {
            double s = in[i] * 8388608.0 + (dither ? tpdf(rnd) : 0);
            int v = clampInt24((int) Math.rint(s));
            out[i * 3] = (byte) (v & 0xFF);
            out[i * 3 + 1] = (byte) ((v >> 8) & 0xFF);
            out[i * 3 + 2] = (byte) ((v >> 16) & 0xFF);
        }
    }

    /** big-endian packed 24-bit (AIFF) */
    public static void floatToInt24BE(float[] in, int n, byte[] out, boolean dither, Random rnd) {
        for (int i = 0; i < n; i++) {
            double s = in[i] * 8388608.0 + (dither ? tpdf(rnd) : 0);
            int v = clampInt24((int) Math.rint(s));
            out[i * 3] = (byte) ((v >> 16) & 0xFF);
            out[i * 3 + 1] = (byte) ((v >> 8) & 0xFF);
            out[i * 3 + 2] = (byte) (v & 0xFF);
        }
    }

    /** 32-bit integer PCM (little-endian) */
    public static void floatToInt32(float[] in, int n, byte[] out, boolean dither, Random rnd) {
        for (int i = 0; i < n; i++) {
            double s = in[i] * 2147483648.0 + (dither ? tpdf(rnd) * 65536.0 : 0);
            long v = Math.round(s);
            if (v > 2147483647L) v = 2147483647L;
            if (v < -2147483648L) v = -2147483648L;
            int iv = (int) v;
            out[i * 4] = (byte) (iv & 0xFF);
            out[i * 4 + 1] = (byte) ((iv >> 8) & 0xFF);
            out[i * 4 + 2] = (byte) ((iv >> 16) & 0xFF);
            out[i * 4 + 3] = (byte) ((iv >> 24) & 0xFF);
        }
    }

    public static void floatToShortBigEndian(float[] in, int n, byte[] out, boolean dither, Random rnd) {
        for (int i = 0; i < n; i++) {
            double s = in[i] * 32768.0 + (dither ? tpdf(rnd) : 0);
            int v = clampShort((int) Math.rint(s));
            out[i * 2] = (byte) ((v >> 8) & 0xFF);
            out[i * 2 + 1] = (byte) (v & 0xFF);
        }
    }

    /** signed 16-bit integers as ints, for the FLAC encoder */
    public static void floatToInt16(float[] in, int n, int[] out, boolean dither, Random rnd) {
        for (int i = 0; i < n; i++) {
            double s = in[i] * 32768.0 + (dither ? tpdf(rnd) : 0);
            out[i] = clampShort((int) Math.rint(s));
        }
    }

    /** signed 24-bit integers as ints, for the FLAC encoder */
    public static void floatToInt24AsInt(float[] in, int n, int[] out, boolean dither, Random rnd) {
        for (int i = 0; i < n; i++) {
            double s = in[i] * 8388608.0 + (dither ? tpdf(rnd) : 0);
            out[i] = clampInt24((int) Math.rint(s));
        }
    }

    /** triangular dither, +-1 LSB, zero mean: the classic 2-LSB TPDF */
    public static double tpdf(Random rnd) {
        return rnd.nextDouble() - rnd.nextDouble();
    }

    public static int clampShort(int v) {
        return v > 32767 ? 32767 : (v < -32768 ? -32768 : v);
    }

    public static int clampInt24(int v) {
        return v > 8388607 ? 8388607 : (v < -8388608 ? -8388608 : v);
    }

    // --------------------------------------------------------------- gain ---
    public static float dbToLinear(float db) {
        if (db <= -96f) return 0f;
        return (float) Math.pow(10.0, db / 20.0);
    }

    public static float linearToDb(float lin) {
        if (lin <= 1e-7f) return FLOOR_DB;
        float db = (float) (20.0 * Math.log10(lin));
        return db < FLOOR_DB ? FLOOR_DB : db;
    }

    /** multiply in place, with a soft limit so a hot fader cannot produce NaN */
    public static void applyGain(float[] buf, int n, float gain) {
        if (gain == 1f) return;
        for (int i = 0; i < n; i++) {
            float v = buf[i] * gain;
            buf[i] = v > 4f ? 4f : (v < -4f ? -4f : v);
        }
    }

    /** hard-clip guard at +-1 so files never wrap around on overflow */
    public static void clampUnit(float[] buf, int n) {
        for (int i = 0; i < n; i++) {
            if (buf[i] > 1f) buf[i] = 1f;
            else if (buf[i] < -1f) buf[i] = -1f;
        }
    }

    public static float clampUnit(float v) {
        return v > 1f ? 1f : (v < -1f ? -1f : v);
    }

    public static boolean isSilent(float[] buf, int n) {
        for (int i = 0; i < n; i++) {
            if (buf[i] > 1e-5f || buf[i] < -1e-5f) return false;
        }
        return true;
    }

    // -------------------------------------------------------------- meters --
    /** per-channel peak + rms (dBFS) for one capture block */
    public static void analyse(float[] buf, int frames, int channels,
                              float[] peakDb, float[] rmsDb) {
        for (int c = 0; c < channels; c++) {
            float peak = 0f;
            double sum = 0;
            for (int f = 0; f < frames; f++) {
                float v = buf[f * channels + c];
                float a = v < 0 ? -v : v;
                if (a > peak) peak = a;
                sum += (double) v * v;
            }
            peakDb[c] = linearToDb(peak);
            float rms = frames == 0 ? 0f : (float) Math.sqrt(sum / frames);
            rmsDb[c] = linearToDb(rms);
        }
    }

    // ---------------------------------------------------------- resampling --
    /**
     * Block-wise linear resampler for the 48 kHz Ogg/Opus path (Opus is a 48 kHz
     * codec; the recorded WAV/FLAC/AIFF always keep the native capture rate).
     *
     * The output does not depend on how the capture blocks are cut: the frame
     * before the current read position is carried across calls, so interpolation
     * continues through the joint instead of snapping back to the first sample of
     * the next block.  Feeding one big block, or the same signal in blocks of 1,
     * 1024 and 4096 frames, produces the same samples.
     *
     * `step` is input frames per output frame, i.e. captureRate / 48000.
     */
    public static final class Resampler {

        private final int mChannels;
        private final double mStep;
        private float[] mHist;                 // one frame of history
        private boolean mHaveHist;
        private float[] mIn = new float[0];    // fed but not consumed yet
        private int mInFrames;
        private double mPos;                   // next output position, in frames, into [hist + mIn]

        public Resampler(int channels, double step) {
            mChannels = channels;
            mStep = step > 0 ? step : 1.0;
            mHist = new float[channels];
        }

        public void reset() {
            mInFrames = 0;
            mHaveHist = false;
            mPos = 0;
        }

        /** append a capture block; anything that cannot come out yet is kept */
        public void feed(float[] in, int frames) {
            if (frames <= 0) return;
            int need = (mInFrames + frames) * mChannels;
            if (mIn.length < need) {
                float[] bigger = new float[Math.max(need, mIn.length * 2 + mChannels * 64)];
                System.arraycopy(mIn, 0, bigger, 0, mInFrames * mChannels);
                mIn = bigger;
            }
            System.arraycopy(in, 0, mIn, mInFrames * mChannels, frames * mChannels);
            mInFrames += frames;
        }

        /** output frames that can be produced right now */
        public int available() {
            int virt = mInFrames + histLen() ;
            if (virt < 2 || mPos > virt - 1) return 0;
            return (int) Math.floor((virt - 1 - mPos) / mStep) + 1;
        }

        /** write up to maxFrames output frames into out, starting at frameOff */
        public int read(float[] out, int frameOff, int maxFrames) {
            int virt = mInFrames + histLen();
            if (virt < 2 || maxFrames <= 0) return 0;
            int made = 0;
            while (made < maxFrames && mPos <= virt - 1) {
                int i0 = (int) mPos;
                double frac = mPos - i0;
                int i1 = Math.min(virt - 1, i0 + 1);
                int dst = (frameOff + made) * mChannels;
                for (int c = 0; c < mChannels; c++) {
                    float a = at(i0, c), b = at(i1, c);
                    out[dst + c] = (float) (a + (b - a) * frac);
                }
                made++;
                mPos += mStep;
            }
            if (made > 0) compact();
            return made;
        }

        private int histLen() {
            return mHaveHist ? 1 : 0;
        }

        private float at(int virtIndex, int c) {
            if (virtIndex == 0 && mHaveHist) return mHist[c];
            return mIn[(virtIndex - histLen()) * mChannels + c];
        }

        /**
         * Drop the input that is behind the read position.  The frame just before
         * it becomes the history, which is what keeps the position - and therefore
         * the interpolation - continuous into the next block.
         */
        private void compact() {
            // Never anchor past the frames we actually hold: when the read position
            // has moved beyond the last frame that has arrived (always the case when
            // downsampling), the last frame becomes the history and the position
            // keeps its offset relative to it, so nothing is skipped or snapped back.
            int keepFrom = (int) Math.min(mPos, mInFrames + histLen());
            if (keepFrom <= 0) return;
            for (int c = 0; c < mChannels; c++) mHist[c] = at(keepFrom - 1, c);
            int pKeep = keepFrom - histLen();
            if (pKeep >= mInFrames) {
                mInFrames = 0;
            } else if (pKeep > 0) {
                System.arraycopy(mIn, pKeep * mChannels, mIn, 0, (mInFrames - pKeep) * mChannels);
                mInFrames -= pKeep;
            }
            mHaveHist = true;
            mPos -= keepFrom - 1;              // i.e. keep the fractional part, offset by one frame
        }
    }

    /** first two source channels -> stereo (used by the Opus path and playback) */
    public static float[] toStereo(float[] in, int frames, int channels) {
        if (channels == 2) return in;
        float[] out = new float[frames * 2];
        if (channels == 1) {
            for (int i = 0; i < frames; i++) {
                out[i * 2] = in[i];
                out[i * 2 + 1] = in[i];
            }
        } else {
            for (int i = 0; i < frames; i++) {
                out[i * 2] = in[i * channels];
                out[i * 2 + 1] = in[i * channels + 1];
            }
        }
        return out;
    }

    /** interleaved float -> byte array, 16-bit little endian (MediaCodec input) */
    public static byte[] floatToByte16(float[] in, int samples) {
        byte[] out = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            int v = clampShort((int) Math.rint(in[i] * 32767.0));
            out[i * 2] = (byte) (v & 0xFF);
            out[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
        return out;
    }
}
