package android.view;

import android.content.Context;
import android.graphics.drawable.Drawable;

public class Window {
    private final WindowManager.LayoutParams mParams = new WindowManager.LayoutParams();
    private int mStatusBarColor, mNavigationBarColor;
    private Drawable mBackground;
    private View mDecor;

    public WindowManager.LayoutParams getAttributes() { return mParams; }

    public void setStatusBarColor(int color) { mStatusBarColor = color; }
    public void setNavigationBarColor(int color) { mNavigationBarColor = color; }
    public int getStatusBarColor() { return mStatusBarColor; }
    public int getNavigationBarColor() { return mNavigationBarColor; }
    public void setBackgroundDrawable(Drawable d) { mBackground = d; }

    public void setBackgroundDrawableResource(int resId) {
        mBackground = new Drawable();
    }
    public Drawable getBackground() { return mBackground; }
    public void addFlags(int flags) { mParams.flags |= flags; }
    public void clearFlags(int flags) { mParams.flags &= ~flags; }
    public void setLayout(int w, int h) { }
    public View getDecorView() { return mDecor; }
    public void setDecorView(View v) { mDecor = v; }
    public void setSoftInputMode(int mode) { }
}
