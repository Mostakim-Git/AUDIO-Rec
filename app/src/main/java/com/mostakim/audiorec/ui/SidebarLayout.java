package com.mostakim.audiorec.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import com.mostakim.audiorec.ui.kit.Theme;

/**
 * Sidebar + content shell.
 *
 * On a tablet or a landscape phone the navigation rail is always visible and the
 * content sits next to it.  On a portrait phone the same rail becomes a drawer:
 * it slides over the content, dims what is behind it, and can be dragged out
 * from the left edge or flicked away.
 */
public class SidebarLayout extends ViewGroup {

    private static final int SIDEBAR_DP = 286;
    private static final int SCRIM = 0xCC000000;

    private final Paint mScrimPaint = new Paint();
    private final Theme mTheme;

    private View mSidebar, mContent, mScrim;
    private int mSidebarWidth;
    private boolean mDrawerMode;
    private float mSlide;                     // 0 = closed, 1 = open
    private boolean mAnimating;
    private final int mTouchSlop;
    private final int mMinFling;
    private float mDownX, mDownY;
    private boolean mDragging;
    private VelocityTracker mVelocity;

    private Runnable mAnimator;

    public SidebarLayout(Context c) {
        this(c, null);
    }

    public SidebarLayout(Context c, AttributeSet a) {
        super(c, a);
        mTheme = new Theme(c);
        mSidebarWidth = (int) (SIDEBAR_DP * getResources().getDisplayMetrics().density);
        final ViewConfiguration vc = ViewConfiguration.get(c);
        mTouchSlop = vc.getScaledTouchSlop();
        mMinFling = vc.getScaledMinimumFlingVelocity();
        setWillNotDraw(false);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() >= 2) {
            mSidebar = getChildAt(0);
            mContent = getChildAt(1);
        }
        if (getChildCount() >= 3) mScrim = getChildAt(2);
    }

    public View content() {
        return mContent;
    }

    public boolean isDrawerMode() {
        return mDrawerMode;
    }

    public boolean isOpen() {
        return !mDrawerMode || mSlide > 0.6f;
    }

    /** called by the activity when the width class changes */
    public void setDrawerMode(boolean drawer) {
        if (drawer == mDrawerMode) return;
        mDrawerMode = drawer;
        mSlide = drawer ? 0f : 1f;
        requestLayout();
        invalidate();
    }

    public void openDrawer() {
        animateTo(1f);
    }

    public void closeDrawer() {
        animateTo(0f);
    }

    public void toggleDrawer() {
        if (mDrawerMode) {
            if (mSlide > 0.5f) closeDrawer();
            else openDrawer();
        }
    }

    private void animateTo(float target) {
        if (Math.abs(mSlide - target) < 0.01f) {
            mSlide = target;
            invalidate();
            return;
        }
        if (mAnimator != null) removeCallbacks(mAnimator);
        final float start = mSlide;
        final long startTime = System.currentTimeMillis();
        final long duration = (long) (180 * Math.abs(target - start)) + 60;
        mAnimating = true;
        mAnimator = new Runnable() {
            @Override
            public void run() {
                float t = Math.min(1f, (System.currentTimeMillis() - startTime) / (float) duration);
                float eased = t * t * (3 - 2 * t);
                mSlide = start + (target - start) * eased;
                invalidate();
                if (t < 1f) {
                    postOnAnimation(this);
                } else {
                    mSlide = target;
                    mAnimating = false;
                    invalidate();
                }
            }
        };
        postOnAnimation(mAnimator);
    }

    // ------------------------------------------------------------------ size
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        if (mSidebar == null) {
            super.onMeasure(widthSpec, heightSpec);
            return;
        }
        mSidebarWidth = Math.min(mSidebarWidth, (int) (width * 0.86f));
        mSidebar.measure(MeasureSpec.makeMeasureSpec(mSidebarWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        int contentWidth = mDrawerMode ? width : Math.max(0, width - mSidebarWidth);
        mContent.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        if (mScrim != null) {
            mScrim.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (mSidebar == null) return;
        int h = b - t;
        int sidebarX = mDrawerMode
                ? (int) (-mSidebarWidth + mSlide * mSidebarWidth)
                : 0;
        mSidebar.layout(sidebarX, 0, sidebarX + mSidebarWidth, h);
        int contentX = mDrawerMode ? 0 : mSidebarWidth;
        mContent.layout(contentX, 0, contentX + mContent.getMeasuredWidth(), h);
        if (mScrim != null) {
            mScrim.layout(contentX, 0, contentX + mScrim.getMeasuredWidth(), h);
        }
    }

    // ----------------------------------------------------------- draw order
    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mContent != null) drawChild(canvas, mContent, getDrawingTime());
        if (mDrawerMode && mSlide > 0.001f) {
            mScrimPaint.setColor(mTheme.scrim);
            mScrimPaint.setAlpha((int) (0xCC * Math.min(1f, mSlide)));
            canvas.drawRect(0, 0, getWidth(), getHeight(), mScrimPaint);
        }
        if (mSidebar != null) drawChild(canvas, mSidebar, getDrawingTime());
        if (mDrawerMode && mSlide > 0.001f && mSlide < 0.999f) {
            // edge shadow while the drawer is in motion
            mScrimPaint.setColor(Color.BLACK);
            mScrimPaint.setAlpha(80);
            canvas.drawRect(mSidebar.getRight(), 0, mSidebar.getRight() + mTheme.dp(6), getHeight(),
                    mScrimPaint);
        }
    }

    // --------------------------------------------------------------- touch
    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (!mDrawerMode) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = e.getX();
                mDownY = e.getY();
                mDragging = false;
                obtainVelocity();
                mVelocity.addMovement(e);
                // a touch on the dimmed area closes the drawer
                if (mSlide > 0.5f && e.getX() > mSidebar.getRight()) {
                    closeDrawer();
                    return true;
                }
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (mVelocity != null) mVelocity.addMovement(e);
                float dx = e.getX() - mDownX;
                float dy = e.getY() - mDownY;
                if (Math.abs(dx) > mTouchSlop && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                    if (dx > 0 && mDownX < mTheme.dp(28)) {
                        mDragging = true;              // pull from the left edge
                        return true;
                    }
                    if (dx < 0 && mSlide > 0.02f) {
                        mDragging = true;
                        return true;
                    }
                }
                return false;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                recycleVelocity();
                return false;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!mDrawerMode) return false;
        obtainVelocity();
        mVelocity.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - mDownX;
                float slide = Math.max(0f, Math.min(1f, dx / mSidebarWidth));
                mSlide = slide;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float vx = mVelocity == null ? 0 : mVelocity.getXVelocity();
                if (Math.abs(vx) > mMinFling) {
                    if (vx > 0) openDrawer();
                    else closeDrawer();
                } else if (mSlide > 0.5f) {
                    openDrawer();
                } else {
                    closeDrawer();
                }
                recycleVelocity();
                mDragging = false;
                return true;
            }
        }
        return true;
    }

    private void obtainVelocity() {
        if (mVelocity == null) {
            mVelocity = VelocityTracker.obtain();
        }
    }

    private void recycleVelocity() {
        if (mVelocity != null) {
            mVelocity.recycle();
            mVelocity = null;
        }
    }
}
