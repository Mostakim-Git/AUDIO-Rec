package android.view;

import android.content.Context;
import android.util.AttributeSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Harness stand-in for android.view.ViewGroup.
 *
 * Children are tracked for real, and the children of custom ViewGroups are
 * discovered the way the platform does it - from the child list, through
 * addView().  onFinishInflate() is only ever called by the XML inflater, which
 * this harness does not have: that is the whole point, because a ViewGroup that
 * binds its panes in onFinishInflate() renders nothing here (and rendered
 * nothing on the phone either).
 */
public class ViewGroup extends View {

    public static class LayoutParams {
        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;

        public int width;
        public int height;

        public LayoutParams(int w, int h) {
            width = w;
            height = h;
        }

        public LayoutParams(LayoutParams source) {
            width = source.width;
            height = source.height;
        }

        public String toString() {
            return "LayoutParams(" + width + "x" + height + ")";
        }
    }

    public static class MarginLayoutParams extends LayoutParams {
        public int leftMargin, topMargin, rightMargin, bottomMargin;

        public MarginLayoutParams(int w, int h) {
            super(w, h);
        }

        public MarginLayoutParams(LayoutParams source) {
            super(source);
        }

        public void setMargins(int l, int t, int r, int b) {
            leftMargin = l;
            topMargin = t;
            rightMargin = r;
            bottomMargin = b;
        }
    }

    private final List<View> mChildren = new ArrayList<>();
    private boolean mClipChildren = true;

    public ViewGroup(Context context) {
        super(context);
    }

    public ViewGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    // ---------------------------------------------------------------- children
    public void addView(View child) {
        addView(child, -1, child.getLayoutParams());
    }

    public void addView(View child, LayoutParams params) {
        addView(child, -1, params);
    }

    public void addView(View child, int index) {
        addView(child, index, child.getLayoutParams());
    }

    public void addView(View child, int width, int height) {
        LayoutParams p = generateDefaultLayoutParams();
        p.width = width;
        p.height = height;
        addView(child, -1, p);
    }

    public void addView(View child, int index, LayoutParams params) {
        if (child == null) throw new IllegalArgumentException("child is null");
        if (child.getParent() != null) {
            throw new IllegalStateException("child already has a parent: " + child
                    + " -> " + child.getParent());
        }
        if (params == null) params = generateDefaultLayoutParams();
        if (!checkLayoutParams(params)) params = generateLayoutParams(params);
        child.setLayoutParams(params);
        if (index < 0 || index >= mChildren.size()) {
            mChildren.add(child);
        } else {
            mChildren.add(index, child);
        }
        child.assignParent(this);
        onViewAdded(child);
        requestLayout();
        invalidate();
    }

    /** the platform calls this for programmatic additions (API 21+) */
    public void onViewAdded(View child) {
    }

    public void onViewRemoved(View child) {
    }

    public void removeView(View child) {
        if (mChildren.remove(child)) {
            child.assignParent(null);
            onViewRemoved(child);
            requestLayout();
        }
    }

    public void removeViewAt(int index) {
        if (index >= 0 && index < mChildren.size()) removeView(mChildren.get(index));
    }

    public void removeAllViews() {
        while (!mChildren.isEmpty()) removeViewAt(mChildren.size() - 1);
    }

    public int getChildCount() {
        return mChildren.size();
    }

    public View getChildAt(int index) {
        return mChildren.get(index);
    }

    public int indexOfChild(View child) {
        return mChildren.indexOf(child);
    }

    public List<View> children() {
        return mChildren;
    }

    public boolean isClipChildren() {
        return mClipChildren;
    }

    public void setClipChildren(boolean clip) {
        mClipChildren = clip;
    }

    public void onChildLayoutParamsChanged() {
    }

    public View findViewWithTag(Object tag) {
        View own = super.findViewWithTag(tag);
        if (own != null) return own;
        for (View c : mChildren) {
            View hit = c.findViewWithTag(tag);
            if (hit != null) return hit;
        }
        return null;
    }

    // ------------------------------------------------------- measure / layout
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        measureChildren(widthMeasureSpec, heightMeasureSpec);
        int w = View.getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        int h = View.getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        for (View c : mChildren) {
            if (c.getVisibility() == GONE) continue;
            ViewGroup.LayoutParams p = c.getLayoutParams();
            MarginLayoutParams mlp = p instanceof MarginLayoutParams ? (MarginLayoutParams) p : null;
            int mw = mlp == null ? 0 : mlp.leftMargin + mlp.rightMargin;
            int mh = mlp == null ? 0 : mlp.topMargin + mlp.bottomMargin;
            w = Math.max(w, c.getMeasuredWidth() + mw + getPaddingLeft() + getPaddingRight());
            h = Math.max(h, c.getMeasuredHeight() + mh + getPaddingTop() + getPaddingBottom());
        }
        setMeasuredDimension(View.resolveSize(w, widthMeasureSpec),
                View.resolveSize(h, heightMeasureSpec));
    }

    protected void measureChildren(int widthMeasureSpec, int heightMeasureSpec) {
        for (View c : mChildren) {
            if (c.getVisibility() == GONE) continue;
            measureChild(c, widthMeasureSpec, heightMeasureSpec);
        }
    }

    protected void measureChild(View child, int parentWidthMeasureSpec,
                                int parentHeightMeasureSpec) {
        LayoutParams lp = child.getLayoutParams();
        int childWidthSpec = getChildMeasureSpec(parentWidthMeasureSpec,
                getPaddingLeft() + getPaddingRight(), lp.width);
        int childHeightSpec = getChildMeasureSpec(parentHeightMeasureSpec,
                getPaddingTop() + getPaddingBottom(), lp.height);
        child.measure(childWidthSpec, childHeightSpec);
    }

    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
                                           int parentHeightMeasureSpec, int heightUsed) {
        MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        int childWidthSpec = getChildMeasureSpec(parentWidthMeasureSpec,
                getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin + widthUsed,
                lp.width);
        int childHeightSpec = getChildMeasureSpec(parentHeightMeasureSpec,
                getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin + heightUsed,
                lp.height);
        child.measure(childWidthSpec, childHeightSpec);
    }

    public static int getChildMeasureSpec(int spec, int padding, int childDimension) {
        int specMode = MeasureSpec.getMode(spec);
        int specSize = MeasureSpec.getSize(spec);
        int size = Math.max(0, specSize - padding);
        int resultSize = 0;
        int resultMode = 0;
        switch (specMode) {
            case MeasureSpec.EXACTLY:
                if (childDimension >= 0) {
                    resultSize = childDimension;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childDimension == LayoutParams.MATCH_PARENT) {
                    resultSize = size;
                    resultMode = MeasureSpec.EXACTLY;
                } else {
                    resultSize = size;
                    resultMode = MeasureSpec.AT_MOST;
                }
                break;
            case MeasureSpec.AT_MOST:
                if (childDimension >= 0) {
                    resultSize = childDimension;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childDimension == LayoutParams.MATCH_PARENT) {
                    resultSize = size;
                    resultMode = MeasureSpec.AT_MOST;
                } else {
                    resultSize = size;
                    resultMode = MeasureSpec.AT_MOST;
                }
                break;
            default:
                if (childDimension >= 0) {
                    resultSize = childDimension;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childDimension == LayoutParams.MATCH_PARENT) {
                    resultSize = size;
                    resultMode = MeasureSpec.UNSPECIFIED;
                } else {
                    resultSize = 0;
                    resultMode = MeasureSpec.UNSPECIFIED;
                }
                break;
        }
        return MeasureSpec.makeMeasureSpec(resultSize, resultMode);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (View c : mChildren) {
            if (c.getVisibility() == GONE) continue;
            LayoutParams p = c.getLayoutParams();
            c.layout(getPaddingLeft(), getPaddingTop(),
                    getPaddingLeft() + c.getMeasuredWidth(),
                    getPaddingTop() + c.getMeasuredHeight());
        }
    }

    // ------------------------------------------------------------ params pools
    protected boolean checkLayoutParams(LayoutParams p) {
        return p != null;
    }

    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return new LayoutParams(p);
    }

    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    /** the XML inflater is the only caller of this - the app never inflates */
    protected void onFinishInflate() {
    }

    public void requestLayout() {
        super.requestLayout();
    }
}
