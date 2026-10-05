package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

public class FrameLayout extends ViewGroup {

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public int gravity = -1;

        public LayoutParams(int w, int h) { super(w, h); }
        public LayoutParams(ViewGroup.LayoutParams source) { super(source); }
    }

    public FrameLayout(Context c) { super(c); }
    public FrameLayout(Context c, AttributeSet a) { super(c, a); }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxW = 0, maxH = 0;
        for (View child : children()) {
            if (child.getVisibility() == GONE) continue;
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            maxW = Math.max(maxW, child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
            maxH = Math.max(maxH, child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
        }
        setMeasuredDimension(
                resolveSize(maxW + getPaddingLeft() + getPaddingRight(), widthMeasureSpec),
                resolveSize(maxH + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t;
        for (View child : children()) {
            if (child.getVisibility() == GONE) continue;
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            int cw = child.getMeasuredWidth();
            int chh = child.getMeasuredHeight();
            int left = getPaddingLeft() + lp.leftMargin;
            int top = getPaddingTop() + lp.topMargin;
            int availW = w - getPaddingLeft() - getPaddingRight() - lp.leftMargin - lp.rightMargin;
            int availH = h - getPaddingTop() - getPaddingBottom() - lp.topMargin - lp.bottomMargin;
            if (cw < availW && (lp.gravity & Gravity.RIGHT) == Gravity.RIGHT) left += availW - cw;
            else if (cw < availW
                    && (lp.gravity & Gravity.CENTER_HORIZONTAL) == Gravity.CENTER_HORIZONTAL) {
                left += (availW - cw) / 2;
            }
            if (chh < availH && (lp.gravity & Gravity.BOTTOM) == Gravity.BOTTOM) top += availH - chh;
            else if (chh < availH
                    && (lp.gravity & Gravity.CENTER_VERTICAL) == Gravity.CENTER_VERTICAL) {
                top += (availH - chh) / 2;
            }
            child.layout(left, top, left + cw, top + chh);
        }
    }
}
