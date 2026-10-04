package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * The child of a ScrollView is measured with an unbounded height, so a page keeps
 * its full content height even when it is taller than the viewport - which is how
 * the harness can tell a populated page from a blank one.
 */
public class ScrollView extends FrameLayout {

    public static class LayoutParams extends FrameLayout.LayoutParams {
        public LayoutParams(int w, int h) { super(w, h); }
        public LayoutParams(ViewGroup.LayoutParams source) { super(source); }
    }

    private int mScrollY;
    private boolean mFillViewport;

    public ScrollView(Context c) { super(c); }
    public ScrollView(Context c, AttributeSet a) { super(c, a); }

    public void setFillViewport(boolean fill) { mFillViewport = fill; }
    public boolean isFillViewport() { return mFillViewport; }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxW = 0, maxH = 0;
        for (View child : children()) {
            if (child.getVisibility() == GONE) continue;
            ViewGroup.LayoutParams lp = child.getLayoutParams();
            int childWidthSpec = getChildMeasureSpec(widthMeasureSpec,
                    getPaddingLeft() + getPaddingRight(), lp.width);
            int childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            child.measure(childWidthSpec, childHeightSpec);
            maxW = Math.max(maxW, child.getMeasuredWidth());
            maxH = Math.max(maxH, child.getMeasuredHeight());
        }
        maxW += getPaddingLeft() + getPaddingRight();
        maxH += getPaddingTop() + getPaddingBottom();
        if (mFillViewport && MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            maxH = MeasureSpec.getSize(heightMeasureSpec);
        }
        setMeasuredDimension(resolveSize(maxW, widthMeasureSpec),
                resolveSize(maxH, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        for (View child : children()) {
            if (child.getVisibility() == GONE) continue;
            int cw = child.getMeasuredWidth();
            int left = getPaddingLeft();
            if (cw > w - getPaddingLeft() - getPaddingRight()) cw = w - getPaddingLeft() - getPaddingRight();
            child.layout(left, getPaddingTop() - mScrollY, left + cw,
                    getPaddingTop() - mScrollY + child.getMeasuredHeight());
        }
    }

    @Override
    public void scrollTo(int x, int y) {
        mScrollY = y;
        requestLayout();
    }

    @Override
    public int getScrollY() { return mScrollY; }
}
