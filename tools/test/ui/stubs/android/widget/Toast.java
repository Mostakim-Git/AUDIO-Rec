package android.widget;

import android.content.Context;

public class Toast {

    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;

    private static String sLast;
    private static int sCount;

    public static Toast makeText(Context c, CharSequence text, int duration) {
        sLast = text == null ? "" : text.toString();
        sCount++;
        return new Toast();
    }

    public static Toast makeText(Context c, int resId, int duration) {
        return makeText(c, "res-" + resId, duration);
    }

    public void show() { }

    public void setDuration(int d) { }

    public void setGravity(int gravity, int x, int y) { }

    /** harness introspection */
    public static String lastText() { return sLast; }

    public static int shownCount() { return sCount; }

    public static void reset() { sLast = null; sCount = 0; }
}
