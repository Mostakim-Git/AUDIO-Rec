package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

/**
 * Harness stand-in for android.widget.LinearLayout that really measures and
 * lays out: orientation, margins, padding, gravity on the cross axis, and
 * layout_weight along the main axis.  That is what makes it possible to assert
 * "this page is not blank" and to catch a child that would come out 0 x 0.
 */
public class LinearLayout extends ViewGroup {

    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public float weight;
        public int gravity = -1;

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(int width, int height, float weight) {
            super(width, height);
            this.weight = weight;
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
            if (source instanceof LayoutParams) {
                weight = ((LayoutParams) source).weight;
                gravity = ((LayoutParams) source).gravity;
            }
        }
    }

    private int mOrientation = HORIZONTAL;
    private int mGravity = Gravity.START | Gravity.TOP;

    public LinearLayout(Context c) {
        super(c);
    }

    public LinearLayout(Context c, AttributeSet a) {
        super(c, a);
    }

    public void setOrientation(int orientation) {
        mOrientation = orientation;
        requestLayout();
    }

    public int getOrientation() {
        return mOrientation;
    }

    public void setGravity(int gravity) {
        mGravity = gravity;
    }

    public int getGravity() {
        return mGravity;
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return mOrientation == HORIZONTAL
                ? new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                : new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    // -------------------------------------------------------------- measuring
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final boolean vertical = mOrientation == VERTICAL;
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        int baseMain = 0;      // children along the main axis, weights excluded
        int maxCross = 0;      // biggest child on the cross axis
        float totalWeight = 0f;
        int weightedCount = 0;

        for (View child : children()) {
            if (child.getVisibility() == GONE) continue;
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            boolean weightedZero = lp.weight > 0
                    && ((vertical && lp.height == 0) || (!vertical && lp.width == 0));
            if (weightedZero) {
                // measured with 0 first, the leftover is handed over below
                child.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY));
                totalWeight += lp.weight;
                weightedCount++;
                continue;
            }
            if (vertical) {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, baseMain);
                baseMain += child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
                maxCross = Math.max(maxCross, child.getMeasuredWidth()
                        + lp.leftMargin + lp.rightMargin);
            } else {
                measureChildWithMargins(child, widthMeasureSpec, baseMain, heightMeasureSpec, 0);
                baseMain += child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
                maxCross = Math.max(maxCross, child.getMeasuredHeight()
                        + lp.topMargin + lp.bottomMargin);
            }
            if (lp.weight > 0) {
                totalWeight += lp.weight;
                weightedCount++;
            }
        }

        int paddingMain = vertical ? getPaddingTop() + getPaddingBottom()
                : getPaddingLeft() + getPaddingRight();
        int paddingCross = vertical ? getPaddingLeft() + getPaddingRight()
                : getPaddingTop() + getPaddingBottom();
        int mainSize = vertical ? heightSize : widthSize;
        int mainMode = vertical ? heightMode : widthMode;
        int crossSize = vertical ? widthSize : heightSize;
        int crossMode = vertical ? widthMode : heightMode;

        int available = Math.max(0, mainSize - paddingMain);
        int main;
        if (mainMode == MeasureSpec.EXACTLY) {
            main = available;
        } else if (mainMode == MeasureSpec.AT_MOST) {
            main = Math.min(baseMain, available);
        } else {
            main = baseMain;
        }

        if (totalWeight > 0 && weightedCount > 0) {
            // a weighted child that asked for 0 starts at nothing; the leftover
            // is split by weight, exactly like the platform does
            int leftover = mainMode == MeasureSpec.EXACTLY ? Math.max(0, available - baseMain) : 0;
            float used = 0f;
            for (View child : children()) {
                if (child.getVisibility() == GONE) continue;
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (lp.weight <= 0) continue;
                boolean zeroMain = vertical ? lp.height == 0 : lp.width == 0;
                if (!zeroMain) continue;
                float share = leftover * (lp.weight / totalWeight);
                if (used + share > leftover) share = Math.max(0, leftover - used);
                used += share;
                int mainPx = (int) share;
                // the weighted pass still honours the child's own cross dimension,
                // exactly like the platform: a fader that asked for 168dp keeps it
                if (vertical) {
                    int wSpec = getChildMeasureSpec(widthMeasureSpec,
                            getPaddingLeft() + getPaddingRight()
                                    + lp.leftMargin + lp.rightMargin, lp.width);
                    child.measure(wSpec, MeasureSpec.makeMeasureSpec(mainPx, MeasureSpec.EXACTLY));
                    maxCross = Math.max(maxCross, child.getMeasuredWidth()
                            + lp.leftMargin + lp.rightMargin);
                } else {
                    int hSpec = getChildMeasureSpec(heightMeasureSpec,
                            getPaddingTop() + getPaddingBottom()
                                    + lp.topMargin + lp.bottomMargin, lp.height);
                    child.measure(MeasureSpec.makeMeasureSpec(mainPx, MeasureSpec.EXACTLY), hSpec);
                    maxCross = Math.max(maxCross, child.getMeasuredHeight()
                            + lp.topMargin + lp.bottomMargin);
                }
            }
            if (mainMode == MeasureSpec.EXACTLY) main = available;
            else {
                int sum = 0;
                for (View child : children()) {
                    if (child.getVisibility() == GONE) continue;
                    LayoutParams lp = (LayoutParams) child.getLayoutParams();
                    sum += (vertical ? child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin
                            : child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
                }
                main = Math.min(sum, available);
            }
        }

        int cross = Math.max(maxCross + paddingCross, getSuggestedMinimumWidth());
        if (crossMode == MeasureSpec.EXACTLY) cross = crossSize;
        else if (crossMode == MeasureSpec.AT_MOST) cross = Math.min(cross, crossSize);

        setMeasuredDimension(vertical ? cross : main + paddingMain,
                vertical ? main + paddingMain : cross);
    }

    // --------------------------------------------------------------- layout
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final boolean vertical = mOrientation == VERTICAL;
        final int width = r - l;
        final int height = b - t;

        int totalMain = 0;
        int maxCross = 0;
        for (View child : children()) {
            if (child.getVisibility() == GONE) continue;
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            totalMain += vertical
                    ? child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin
                    : child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            maxCross = Math.max(maxCross, vertical
                    ? child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin
                    : child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
        }

        int cursor = vertical ? getPaddingTop() : getPaddingLeft();
        // main-axis gravity: shift the whole block if the parent is bigger
        int slackMain = (vertical ? height - getPaddingTop() - getPaddingBottom()
                : width - getPaddingLeft() - getPaddingRight()) - totalMain;
        if (slackMain > 0) {
            int g = mGravity & Gravity.FILL_VERTICAL;
            if (vertical && (mGravity & Gravity.BOTTOM) == Gravity.BOTTOM) cursor += slackMain;
            else if (vertical && (mGravity & Gravity.CENTER_VERTICAL) == Gravity.CENTER_VERTICAL) {
                cursor += slackMain / 2;
            }
            if (!vertical && (mGravity & Gravity.RIGHT) == Gravity.RIGHT) cursor += slackMain;
            else if (!vertical
                    && (mGravity & Gravity.CENTER_HORIZONTAL) == Gravity.CENTER_HORIZONTAL) {
                cursor += slackMain / 2;
            }
        }

        for (View child : children()) {
            if (child.getVisibility() == GONE) continue;
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            int cw = child.getMeasuredWidth();
            int chh = child.getMeasuredHeight();
            if (vertical) {
                int left = getPaddingLeft() + lp.leftMargin;
                int avail = width - getPaddingLeft() - getPaddingRight()
                        - lp.leftMargin - lp.rightMargin;
                if (cw < avail && (mGravity & Gravity.CENTER_HORIZONTAL) == Gravity.CENTER_HORIZONTAL) {
                    left += (avail - cw) / 2;
                } else if (cw < avail && (mGravity & Gravity.RIGHT) == Gravity.RIGHT) {
                    left += avail - cw;
                }
                int top = cursor + lp.topMargin;
                child.layout(left, top, left + cw, top + chh);
                cursor = top + chh + lp.bottomMargin;
            } else {
                int top = getPaddingTop() + lp.topMargin;
                int avail = height - getPaddingTop() - getPaddingBottom()
                        - lp.topMargin - lp.bottomMargin;
                if (chh < avail && (mGravity & Gravity.CENTER_VERTICAL) == Gravity.CENTER_VERTICAL) {
                    top += (avail - chh) / 2;
                } else if (chh < avail && (mGravity & Gravity.BOTTOM) == Gravity.BOTTOM) {
                    top += avail - chh;
                }
                int left = cursor + lp.leftMargin;
                child.layout(left, top, left + cw, top + chh);
                cursor = left + cw + lp.rightMargin;
            }
        }
    }
}
