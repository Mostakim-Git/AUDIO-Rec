package com.mostakim.audiorec.audio;

/**
 * JVM-test stand-in for the real FormatProbe.
 *
 * The shipping class probes containers with android.media.MediaExtractor, which
 * does not exist off-device; the only part the container readers need is the
 * AIFF 80-bit float conversion, so the harness compiles this instead.  The
 * implementation follows the IEEE 754 extended-precision layout AIFF uses
 * (sign, 15-bit exponent biased by 16383, 64-bit mantissa with an explicit
 * integer bit) and is cross-checked by tools/format_check.py, which reads the
 * same field straight from the file.
 */
public final class FormatProbe {

    public static double extended80ToDouble(byte[] b) {
        if (b == null || b.length < 10) return 0;
        int expon = ((b[0] & 0x7F) << 8) | (b[1] & 0xFF);
        long mant = 0;
        for (int i = 2; i < 10; i++) mant = (mant << 8) | (b[i] & 0xFF);
        boolean negative = (b[0] & 0x80) != 0;
        if (expon == 0 && mant == 0) return 0;
        long frac = mant & 0x7FFFFFFFFFFFFFFFL;
        double v = Math.scalb((double) frac, expon - 16383 - 63);
        if ((mant & 0x8000000000000000L) != 0) v += Math.pow(2.0, expon - 16383);
        return negative ? -v : v;
    }

    private FormatProbe() {
    }
}
