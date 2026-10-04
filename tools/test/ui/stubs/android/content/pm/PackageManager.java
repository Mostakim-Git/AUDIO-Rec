package android.content.pm;

public class PackageManager {
    public static final int PERMISSION_GRANTED = 0;
    public static final int PERMISSION_DENIED = -1;
    public static final int GET_PERMISSIONS = 0x1000;
    public static final int GET_META_DATA = 0x80;

    public PackageInfo getPackageInfo(String packageName, int flags) {
        return new PackageInfo();
    }

    public java.util.List<PackageInfo> getInstalledPackages(int flags) {
        return new java.util.ArrayList<>();
    }
}
