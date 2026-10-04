package android.net;

import java.io.File;

public class Uri {
    private final String mValue;

    private Uri(String value) { mValue = value; }

    public static Uri parse(String s) { return new Uri(s); }
    public static Uri fromFile(File f) { return new Uri("file://" + f.getAbsolutePath()); }
    public static Uri fromParts(String scheme, String ssp, String fragment) {
        return new Uri(scheme + ":" + ssp);
    }

    public String getScheme() { return "content"; }
    public String getLastPathSegment() { return mValue; }
    public String getPath() { return mValue; }
    public String toString() { return mValue; }
}
