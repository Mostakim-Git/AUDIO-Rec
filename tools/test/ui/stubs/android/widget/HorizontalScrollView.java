package android.widget;

import android.content.Context;

/**
 * Harness stand-in for android.widget.HorizontalScrollView.
 *
 * It lays its child out at its measured width (which may be wider than the
 * window - that is the point of the control), so the harness can tell the
 * difference between content that is meant to scroll sideways and content that
 * is simply misaligned.
 */
public class HorizontalScrollView extends FrameLayout {

    private boolean mFillViewport;

    public HorizontalScrollView(Context c) {
        super(c);
    }

    public HorizontalScrollView(Context c, android.util.AttributeSet a) {
        super(c);
    }

    public void setFillViewport(boolean fill) {
        mFillViewport = fill;
    }

    public boolean isFillViewport() {
        return mFillViewport;
    }

    public void setHorizontalScrollBarEnabled(boolean enabled) {
    }

    public int getMaxScrollAmount() {
        return 0;
    }
}
