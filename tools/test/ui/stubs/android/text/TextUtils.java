package android.text;

public class TextUtils {

    /** the ellipsis modes TextView.setEllipsize() accepts */
    public enum TruncateAt {
        START, MIDDLE, END, MARQUEE
    }

    public static boolean isEmpty(CharSequence s) {
        return s == null || s.length() == 0;
    }
}
