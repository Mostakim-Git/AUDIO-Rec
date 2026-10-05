package android.os;

public class StatFs {
    private final long mTotal = 64L * 1024 * 1024 * 1024;
    private final long mFree = 21L * 1024 * 1024 * 1024;

    public StatFs(String path) { }

    public long getBlockSizeLong() { return 4096; }
    public long getBlockCountLong() { return mTotal / 4096; }
    public long getAvailableBlocksLong() { return mFree / 4096; }
    public long getFreeBlocksLong() { return mFree / 4096; }
    public long getAvailableBytes() { return mFree; }
    public long getFreeBytes() { return mFree; }
    public long getTotalBytes() { return mTotal; }

    @SuppressWarnings("deprecation")
    public int getBlockSize() { return 4096; }

    @SuppressWarnings("deprecation")
    public int getBlockCount() { return (int) (mTotal / 4096); }

    @SuppressWarnings("deprecation")
    public int getAvailableBlocks() { return (int) (mFree / 4096); }

    @SuppressWarnings("deprecation")
    public int getFreeBlocks() { return (int) (mFree / 4096); }

    public void restat(String path) { }
}
