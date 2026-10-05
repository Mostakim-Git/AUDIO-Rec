package android.os;

import java.io.File;

public class Environment {
    public static final String MEDIA_MOUNTED = "mounted";
    public static final String DIRECTORY_DOWNLOADS = "Download";
    public static final String DIRECTORY_MUSIC = "Music";
    public static final String DIRECTORY_DOCUMENTS = "Documents";
    public static final String MEDIA_REMOVED = "removed";

    public static String getExternalStorageState() { return MEDIA_MOUNTED; }
    public static File getExternalStorageDirectory() { return new File("/sdcard"); }
    public static boolean isExternalStorageManager() { return true; }
    public static File getExternalStoragePublicDirectory(String type) {
        return new File("/sdcard/" + type);
    }
}
