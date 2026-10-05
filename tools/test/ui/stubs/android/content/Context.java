package android.content;

import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.view.WindowManager;

import java.io.File;

/**
 * Harness stand-in for android.content.Context.
 *
 * getResources() hands out a real Resources object with density 3.0 and a
 * configuration whose screenWidthDp the test can set, so the shell can be driven
 * through both its drawer mode and its rail mode.
 */
public class Context {

    public static final String CLIPBOARD_SERVICE = "clipboard";
    public static final int MODE_PRIVATE = 0;
    public static final int RECEIVER_NOT_EXPORTED = 4;
    public static final int RECEIVER_EXPORTED = 2;
    public static final int BIND_AUTO_CREATE = 1;

    private final Resources mResources = new Resources();
    private final PackageManager mPackageManager = new PackageManager();
    private final ContentResolver mResolver = new ContentResolver();
    private final ClipboardManager mClipboard = new ClipboardManager();
    private final android.hardware.usb.UsbManager mUsb = new android.hardware.usb.UsbManager();
    private final File mFilesDir;
    private final File mExternalDir;
    private final SharedPreferences mPrefs = new SharedPreferences();

    public Context() {
        File base = new File(System.getProperty("java.io.tmpdir"), "audiorec-ui-harness");
        mFilesDir = new File(base, "files");
        mExternalDir = new File(base, "external");
        //noinspection ResultOfMethodCallIgnored
        mFilesDir.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        mExternalDir.mkdirs();
    }

    public Resources getResources() { return mResources; }

    public int getColor(int id) { return mResources.getColor(id); }

    public String getString(int id) { return mResources.getString(id); }

    public String getString(int id, Object... args) { return mResources.getString(id); }

    public CharSequence getText(int id) { return mResources.getString(id); }

    public android.graphics.drawable.Drawable getDrawable(int id) {
        return new android.graphics.drawable.Drawable();
    }

    public PackageManager getPackageManager() { return mPackageManager; }

    public String getPackageName() { return "com.mostakim.audiorec"; }

    public String getOpPackageName() { return getPackageName(); }

    public ContentResolver getContentResolver() { return mResolver; }

    public Context getApplicationContext() { return this; }

    public Object getSystemService(String name) {
        if (CLIPBOARD_SERVICE.equals(name)) return mClipboard;
        if (USB_SERVICE.equals(name)) return mUsb;
        if (WINDOW_SERVICE.equals(name)) return null;
        return new Object();
    }

    @SuppressWarnings("unchecked")
    public <T> T getSystemService(Class<T> cls) {
        return (T) getSystemService(cls.getSimpleName());
    }

    public static final String WINDOW_SERVICE = "window";
    public static final String USB_SERVICE = "usb";
    public static final String AUDIO_SERVICE = "audio";

    public File getFilesDir() { return mFilesDir; }

    public File getCacheDir() { return mFilesDir; }

    public File getExternalFilesDir(String type) { return mExternalDir; }

    public File[] getExternalFilesDirs(String type) {
        return new File[]{mExternalDir};
    }

    public File[] getExternalMediaDirs() {
        return new File[]{mExternalDir};
    }

    public SharedPreferences getSharedPreferences(String name, int mode) { return mPrefs; }

    public int checkSelfPermission(String permission) {
        return PackageManager.PERMISSION_GRANTED;
    }

    public boolean isDeviceProtectedStorage() { return false; }

    /** every intent the app sent out, so the harness can inspect it */
    public static final java.util.List<Intent> STARTED = new java.util.ArrayList<>();

    public void startActivity(Intent intent) {
        STARTED.add(intent);
    }

    public static Intent lastStarted() {
        return STARTED.isEmpty() ? null : STARTED.get(STARTED.size() - 1);
    }

    public static void forgetStarted() {
        STARTED.clear();
    }

    public void startActivityForResult(Intent intent, int requestCode) { }

    public void startService(Intent intent) { }

    public void stopService(Intent intent) { }

    public void startForegroundService(Intent intent) { }

    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) { return null; }

    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int flags) {
        return null;
    }

    public void unregisterReceiver(BroadcastReceiver receiver) { }

    public void sendBroadcast(Intent intent) { }

    public void sendBroadcast(Intent intent, String permission) { }

    public void revokeUriPermission(android.net.Uri uri, int modeFlags) { }

    public int checkUriPermission(android.net.Uri uri, int pid, int uid, int modeFlags) { return 0; }

    /** test hooks */
    public Object clipboard() { return mClipboard; }
}
