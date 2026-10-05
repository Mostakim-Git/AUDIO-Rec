package android.content.res;

import android.util.DisplayMetrics;

public class Resources {
    public final DisplayMetrics metrics = new DisplayMetrics();
    public final Configuration config = new Configuration();

    public DisplayMetrics getDisplayMetrics() {
        return metrics;
    }

    public Configuration getConfiguration() {
        return config;
    }

    public int getColor(int id) {
        // the harness only needs a stable, opaque value per resource id
        int r = 0x30 + (id & 0x3F);
        int g = 0x40 + ((id >> 6) & 0x3F);
        int b = 0x50 + ((id >> 12) & 0x3F);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public float getDimension(int id) {
        return 14f * metrics.density;
    }

    public int getDimensionPixelSize(int id) {
        return (int) getDimension(id);
    }

    public String getString(int id) {
        return "res-" + Integer.toHexString(id);
    }
}
