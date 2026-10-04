package android.graphics;

public class Typeface {
    public static final Typeface DEFAULT = new Typeface();
    public static final Typeface DEFAULT_BOLD = new Typeface();
    public static final Typeface MONOSPACE = new Typeface();
    public static final Typeface SANS_SERIF = new Typeface();
    public static final int NORMAL = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;

    public static Typeface create(String family, int style) { return new Typeface(); }
    public static Typeface create(Typeface base, int style) { return new Typeface(); }
    public Typeface derive(int style) { return this; }
}
