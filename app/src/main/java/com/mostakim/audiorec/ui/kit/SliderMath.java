package com.mostakim.audiorec.ui.kit;

import java.util.Locale;

/**
 * The arithmetic behind the gain and monitor faders, kept free of the Android
 * framework so it can be tested off-device.
 *
 * Two things the operator asked for live here:
 *
 *  1. a step of exactly 0.1 dB, in both directions, and
 *  2. a value that does not jump when a drag starts - the handle follows the
 *     finger from wherever it was touched, and a slow hold turns into a fine
 *     adjustment instead of a coarse one.
 */
public final class SliderMath {

    /** the step every gain/monitor control moves in when nudged */
    public static final float STEP_DB = 0.1f;

    /** how much slower a fine drag moves: one eighth of the coarse travel */
    public static final float FINE_FACTOR = 0.125f;

    /** how long a touch has to be held before it counts as a fine drag */
    public static final long FINE_AFTER_MS = 320;

    private SliderMath() {
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /**
     * Rounds to the nearest multiple of {@code step}, so 0.1 dB really is the
     * smallest change the control can produce.  Always returns a value with one
     * decimal for a 0.1 step (0.1f * 3 is 0.30000001 in binary floating point).
     */
    public static float quantize(float value, float step) {
        if (step <= 0f) return value;
        double steps = Math.round((double) value / step);
        double snapped = steps * step;
        // one decimal is the resolution the UI shows; keep it exact in decimal
        return (float) (Math.round(snapped * 10d) / 10d);
    }

    /** the same, for the default 0.1 dB gain controls */
    public static float quantizeDb(float value) {
        return quantize(value, STEP_DB);
    }

    /** the value a fader handle shows at vertical position {@code y} */
    public static float valueAt(float y, float top, float bottom, float lo, float hi) {
        if (bottom - top <= 0f) return lo;
        float t = clamp((bottom - y) / (bottom - top), 0f, 1f);
        return lo + t * (hi - lo);
    }

    /**
     * How far a drag moves the value: relative to where the finger went down, so
     * the handle never jumps to the touch point, scaled down once the touch has
     * been held long enough to be deliberate.
     */
    public static float dragValue(float downValue, float downY, float y,
                                 float top, float bottom, float lo, float hi, boolean fine) {
        float span = bottom - top;
        if (span <= 0f) return clamp(downValue, lo, hi);
        float perPixel = (hi - lo) / span;
        float moved = (downY - y) * perPixel;
        if (fine) moved *= FINE_FACTOR;
        return clamp(downValue + moved, lo, hi);
    }

    /** true once the touch has been held long enough to count as fine */
    public static boolean isFine(long downAtMs, long nowMs) {
        return nowMs - downAtMs >= FINE_AFTER_MS;
    }

    /** sign + one decimal, the way a mixing desk shows a trim */
    public static String formatDb(float db, String unit) {
        String v = String.format(Locale.US, "%.1f", db);
        if (db > 0f) v = "+" + v;
        return v + " " + unit;
    }

    /** +0.1 / -0.1 style label for the nudge buttons */
    public static String stepLabel(float step) {
        return String.format(Locale.US, "%.1f", step);
    }
}
