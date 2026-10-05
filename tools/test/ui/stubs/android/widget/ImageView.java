package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

public class ImageView extends View {

    public enum ScaleType { MATRIX, FIT_XY, FIT_START, FIT_CENTER, FIT_END, CENTER, CENTER_CROP, CENTER_INSIDE }

    private int mImageResource;
    private int mColorFilter;
    private boolean mHasColorFilter;
    private ScaleType mScaleType = ScaleType.FIT_CENTER;

    public ImageView(Context c) { super(c); }
    public ImageView(Context c, AttributeSet a) { super(c, a); }

    public void setImageResource(int resId) { mImageResource = resId; requestLayout(); }
    public int getImageResource() { return mImageResource; }
    public void setImageDrawable(android.graphics.drawable.Drawable d) { }
    public void setColorFilter(int color) { mColorFilter = color; mHasColorFilter = true; }
    public int getColorFilter() { return mHasColorFilter ? mColorFilter : 0; }
    public boolean hasColorFilter() { return mHasColorFilter; }
    public void setScaleType(ScaleType type) { mScaleType = type; }
    public ScaleType getScaleType() { return mScaleType; }
    public void setAdjustViewBounds(boolean b) { }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int intrinsic = (int) (24 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(resolveSize(Math.max(intrinsic, getSuggestedMinimumWidth()), widthMeasureSpec),
                resolveSize(Math.max(intrinsic, getSuggestedMinimumHeight()), heightMeasureSpec));
    }
}
