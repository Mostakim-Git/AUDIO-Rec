package android.graphics.drawable;

public class Drawable {
    private int mTint;
    private boolean mHasTint;

    public void setTint(int color) { mTint = color; mHasTint = true; }
    public void setTintList(Object list) { }
    public void setAlpha(int a) { }
    public int getAlpha() { return 255; }
    public void setBounds(int l, int t, int r, int b) { }
    public int getIntrinsicWidth() { return 24; }
    public int getIntrinsicHeight() { return 24; }
    public int getTint() { return mHasTint ? mTint : 0; }
    public boolean hasTint() { return mHasTint; }
    public void invalidateSelf() { }
}
