package android.util;

/** Harness stand-in for android.util.Log: records nothing, prints nothing. */
public class Log {
    public static int v(String tag, String msg) { return 0; }
    public static int v(String tag, String msg, Throwable t) { return 0; }
    public static int d(String tag, String msg) { return 0; }
    public static int d(String tag, String msg, Throwable t) { return 0; }
    public static int i(String tag, String msg) { return 0; }
    public static int i(String tag, String msg, Throwable t) { return 0; }
    public static int w(String tag, String msg) { return 0; }
    public static int w(String tag, String msg, Throwable t) { return 0; }
    public static int w(String tag, Throwable t) { return 0; }
    public static int e(String tag, String msg) { return 0; }
    public static int e(String tag, String msg, Throwable t) { return 0; }
    public static String getStackTraceString(Throwable t) { return String.valueOf(t); }
}
