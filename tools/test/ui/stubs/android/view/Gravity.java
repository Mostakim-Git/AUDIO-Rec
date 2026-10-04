package android.view;

public class Gravity {
    public static final int NO_GRAVITY = 0x0000;
    public static final int CENTER_HORIZONTAL = 0x0001;
    public static final int CENTER_VERTICAL = 0x0010;
    public static final int CENTER = CENTER_HORIZONTAL | CENTER_VERTICAL;
    public static final int LEFT = 0x0003;
    public static final int RIGHT = 0x0005;
    public static final int TOP = 0x0030;
    public static final int BOTTOM = 0x0050;
    public static final int START = 0x00800003;
    public static final int END = 0x00800005;
    public static final int FILL = 0x00000077;
    public static final int FILL_HORIZONTAL = 0x00000007;
    public static final int FILL_VERTICAL = 0x00000070;

    public static boolean isHorizontal(int g) { return (g & FILL_HORIZONTAL) != 0; }
    public static int getAbsoluteGravity(int g, int layoutDirection) { return g; }
}
