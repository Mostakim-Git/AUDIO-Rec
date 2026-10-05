package android.view;

import android.content.Context;
import android.graphics.Canvas;
import android.content.res.Resources;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

/**
 * Harness stand-in for android.view.View.
 *
 * It keeps the contracts that matter for a headless "does the window actually
 * have content" test: measure specs, visibility, padding, layout params,
 * click listeners, and the measured/laid-out bounds.  Anything the app never
 * calls is left out on purpose - a missing method is a compile error, which is
 * exactly how this file stays honest.
 */
public class View {

    public static final int VISIBLE = 0;
    public static final int INVISIBLE = 4;
    public static final int GONE = 8;
    public static final int NO_ID = -1;

    // ------------------------------------------------------------- MeasureSpec
    public static class MeasureSpec {
        public static final int UNSPECIFIED = 0;
        public static final int EXACTLY = 1 << 30;
        public static final int AT_MOST = 2 << 30;
        private static final int MODE_MASK = 0x3 << 30;

        public static int makeMeasureSpec(int size, int mode) {
            return (size & ~MODE_MASK) | (mode & MODE_MASK);
        }

        public static int getMode(int spec) {
            return spec & MODE_MASK;
        }

        public static int getSize(int spec) {
            return spec & ~MODE_MASK;
        }

        public static String toString(int spec) {
            int mode = getMode(spec);
            String m = mode == EXACTLY ? "EXACTLY" : mode == AT_MOST ? "AT_MOST" : "UNSPECIFIED";
            return m + "(" + getSize(spec) + ")";
        }
    }

    // --------------------------------------------------------------- listeners
    public interface OnClickListener {
        void onClick(View v);
    }

    public interface OnLongClickListener {
        boolean onLongClick(View v);
    }

    public interface OnTouchListener {
        boolean onTouch(View v, MotionEvent event);
    }

    public interface OnLayoutChangeListener {
        void onLayoutChange(View v, int left, int top, int right, int bottom,
                            int oldLeft, int oldTop, int oldRight, int oldBottom);
    }

    public interface OnFocusChangeListener {
        void onFocusChange(View v, boolean hasFocus);
    }

    private final Context mContext;
    private ViewGroup.LayoutParams mLayoutParams;
    private ViewGroup mParent;
    private int mId = NO_ID;
    private int mVisibility = VISIBLE;
    private int mLeft, mTop, mRight, mBottom;
    private int mMeasuredWidth, mMeasuredHeight;
    private int mPaddingLeft, mPaddingTop, mPaddingRight, mPaddingBottom;
    private Drawable mBackground;
    private int mBackgroundColor;
    private Object mTag;
    private boolean mClickable, mFocusable, mEnabled = true, mSelected;
    private OnClickListener mClickListener;
    private OnLongClickListener mLongClickListener;
    private OnTouchListener mTouchListener;
    private float mAlpha = 1f;
    private int mMinimumWidth, mMinimumHeight;
    private boolean mWillNotDraw;
    private CharSequence mContentDescription;

    public View(Context context) {
        mContext = context;
    }

    public View(Context context, AttributeSet attrs) {
        mContext = context;
    }

    public Context getContext() {
        return mContext;
    }

    public Resources getResources() {
        return mContext.getResources();
    }

    // ------------------------------------------------------------------ params
    public ViewGroup.LayoutParams getLayoutParams() {
        return mLayoutParams;
    }

    public void setLayoutParams(ViewGroup.LayoutParams params) {
        mLayoutParams = params;
        if (params != null && mParent != null) {
            mParent.onChildLayoutParamsChanged();
        }
        requestLayout();
    }

    void assignParent(ViewGroup parent) {
        mParent = parent;
    }

    public ViewGroup getParent() {
        return mParent;
    }

    public View getRootView() {
        View v = this;
        while (v.getParent() != null) v = v.getParent();
        return v;
    }

    public int getId() {
        return mId;
    }

    public void setId(int id) {
        mId = id;
    }

    public Object getTag() {
        return mTag;
    }

    public void setTag(Object tag) {
        mTag = tag;
    }

    public void setTag(int key, Object tag) {
        mTag = tag;
    }

    public Object getTag(int key) {
        return mTag;
    }

    public View findViewWithTag(Object tag) {
        if (tag != null && tag.equals(mTag)) return this;
        return null;
    }

    // ------------------------------------------------------------- appearance
    public void setVisibility(int visibility) {
        mVisibility = visibility;
        requestLayout();
        invalidate();
    }

    public int getVisibility() {
        return mVisibility;
    }

    public boolean isShown() {
        if (mVisibility != VISIBLE) return false;
        return mParent == null || mParent.isShown();
    }

    public void setBackgroundColor(int color) {
        mBackgroundColor = color;
        mBackground = null;
    }

    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    public void setBackgroundResource(int resId) {
        mBackground = new Drawable();
    }

    public void setBackgroundDrawable(Drawable d) {
        mBackground = d;
    }

    public Drawable getBackground() {
        return mBackground;
    }

    public void setPadding(int left, int top, int right, int bottom) {
        mPaddingLeft = left;
        mPaddingTop = top;
        mPaddingRight = right;
        mPaddingBottom = bottom;
        requestLayout();
    }

    public int getPaddingLeft() {
        return mPaddingLeft;
    }

    public int getPaddingTop() {
        return mPaddingTop;
    }

    public int getPaddingRight() {
        return mPaddingRight;
    }

    public int getPaddingBottom() {
        return mPaddingBottom;
    }

    public int getPaddingStart() {
        return mPaddingLeft;
    }

    public int getPaddingEnd() {
        return mPaddingRight;
    }

    public void setContentDescription(CharSequence cd) {
        mContentDescription = cd;
    }

    public CharSequence getContentDescription() {
        return mContentDescription;
    }

    public void setAlpha(float a) {
        mAlpha = a;
    }

    public float getAlpha() {
        return mAlpha;
    }

    public void setMinimumWidth(int w) {
        mMinimumWidth = w;
    }

    public void setMinimumHeight(int h) {
        mMinimumHeight = h;
    }

    public int getMinimumWidth() {
        return mMinimumWidth;
    }

    public int getMinimumHeight() {
        return mMinimumHeight;
    }

    public void setClipToPadding(boolean b) {
    }

    public void setClipChildren(boolean b) {
    }

    public void setWillNotDraw(boolean b) {
        mWillNotDraw = b;
    }

    public boolean willNotDraw() {
        return mWillNotDraw;
    }

    public void setLayerType(int type, Object paint) {
    }

    public void setElevation(float e) {
    }

    public void setSoundEffectsEnabled(boolean b) {
    }

    public void setKeepScreenOn(boolean b) {
    }

    public void setFitsSystemWindows(boolean b) {
    }

    public void setSaveEnabled(boolean b) {
    }

    public void setHapticFeedbackEnabled(boolean b) {
    }

    public void setImportantForAccessibility(int mode) {
    }

    // ------------------------------------------------------------- interaction
    public void setClickable(boolean clickable) {
        mClickable = clickable;
    }

    public boolean isClickable() {
        return mClickable;
    }

    public void setFocusable(boolean focusable) {
        mFocusable = focusable;
    }

    public boolean isFocusable() {
        return mFocusable;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public void setSelected(boolean selected) {
        mSelected = selected;
    }

    public boolean isSelected() {
        return mSelected;
    }

    public void setOnClickListener(OnClickListener l) {
        mClickListener = l;
        if (l != null) mClickable = true;
    }

    public OnClickListener getOnClickListener() {
        return mClickListener;
    }

    public void setOnLongClickListener(OnLongClickListener l) {
        mLongClickListener = l;
    }

    public OnLongClickListener getOnLongClickListener() {
        return mLongClickListener;
    }

    public void setOnTouchListener(OnTouchListener l) {
        mTouchListener = l;
    }

    public boolean performClick() {
        if (mClickListener != null) {
            mClickListener.onClick(this);
            return true;
        }
        return mClickable;
    }

    public boolean performLongClick() {
        return mLongClickListener != null && mLongClickListener.onLongClick(this);
    }

    public boolean callOnClick() {
        return performClick();
    }

    public boolean onTouchEvent(MotionEvent event) {
        return mClickable;
    }

    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mVisibility != VISIBLE) return false;
        if (mTouchListener != null && mTouchListener.onTouch(this, event)) return true;
        return onTouchEvent(event);
    }

    public boolean onInterceptTouchEvent(MotionEvent event) {
        return false;
    }

    public boolean isInEditMode() {
        return false;
    }

    public void setVerticalScrollBarEnabled(boolean enabled) {
    }

    public void setHorizontalScrollBarEnabled(boolean enabled) {
    }

    public void setScrollbarFadingEnabled(boolean enabled) {
    }

    public void setScrollContainer(boolean isScrollContainer) {
    }

    public void setOverScrollMode(int mode) {
    }

    public void setOnScrollChangeListener(Object l) {
    }

    public void removeCallbacks(Runnable r) {
        android.os.HarnessLoop.remove(r);
    }

    public boolean removeCallbacksAndMessages(Object token) {
        return true;
    }

    protected void onDraw(Canvas canvas) {
    }

    public void draw(Canvas canvas) {
        onDraw(canvas);
    }

    protected void dispatchDraw(Canvas canvas) {
        onDraw(canvas);
    }

    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        child.draw(canvas);
        return true;
    }

    public void drawableHotspotChanged(float x, float y) {
    }

    public boolean isAttachedToWindow() {
        return true;
    }

    public long getDrawingTime() {
        return 0L;
    }

    public void getLocationInWindow(int[] outLocation) {
        outLocation[0] = mLeft;
        outLocation[1] = mTop;
    }

    public void getLocationOnScreen(int[] outLocation) {
        outLocation[0] = mLeft;
        outLocation[1] = mTop;
    }

    public float getTranslationX() {
        return 0f;
    }

    public float getTranslationY() {
        return 0f;
    }

    public void setTranslationX(float x) {
    }

    public void setTranslationY(float y) {
    }

    public void setPivotX(float x) {
    }

    public void setPivotY(float y) {
    }

    public void setRotation(float degrees) {
    }

    public void setScaleX(float sx) {
    }

    public void setScaleY(float sy) {
    }

    public float getScaleX() {
        return 1f;
    }

    public float getScaleY() {
        return 1f;
    }

    public void setOutlineProvider(Object provider) {
    }

    public void setForeground(android.graphics.drawable.Drawable d) {
    }

    public void setStateListAnimator(Object animator) {
    }

    public int getPaddingTop2() {
        return mPaddingTop;
    }

    // ----------------------------------------------------------- measure/layout
    public final void measure(int widthMeasureSpec, int heightMeasureSpec) {
        onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
    }

    protected void setMeasuredDimension(int measuredWidth, int measuredHeight) {
        mMeasuredWidth = Math.max(0, measuredWidth);
        mMeasuredHeight = Math.max(0, measuredHeight);
    }

    public int getSuggestedMinimumWidth() {
        return mMinimumWidth;
    }

    public int getSuggestedMinimumHeight() {
        return mMinimumHeight;
    }

    public static int getDefaultSize(int size, int measureSpec) {
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        if (specMode == MeasureSpec.UNSPECIFIED) return size;
        if (specMode == MeasureSpec.AT_MOST) return Math.min(specSize, size);
        return specSize;
    }

    public static int resolveSize(int size, int measureSpec) {
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        if (specMode == MeasureSpec.AT_MOST) return Math.min(size, specSize);
        if (specMode == MeasureSpec.EXACTLY) return specSize;
        return size;
    }

    public final int getMeasuredWidth() {
        return mMeasuredWidth;
    }

    public final int getMeasuredHeight() {
        return mMeasuredHeight;
    }

    public void layout(int l, int t, int r, int b) {
        int oldL = mLeft, oldT = mTop, oldR = mRight, oldB = mBottom;
        mLeft = l;
        mTop = t;
        mRight = r;
        mBottom = b;
        onLayout(mLeft != oldL || mTop != oldT || mRight != oldR || mBottom != oldB,
                l, t, r, b);
        if (mLeft != oldL || mTop != oldT || mRight != oldR || mBottom != oldB) {
            onSizeChanged(mRight - mLeft, mBottom - mTop, oldR - oldL, oldB - oldT);
        }
    }

    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    }

    protected void onAttachedToWindow() {
    }

    protected void onDetachedFromWindow() {
    }

    public int getWidth() {
        return mRight - mLeft;
    }

    public int getHeight() {
        return mBottom - mTop;
    }

    public int getLeft() {
        return mLeft;
    }

    public int getTop() {
        return mTop;
    }

    public int getRight() {
        return mRight;
    }

    public int getBottom() {
        return mBottom;
    }

    public void offsetLeftAndRight(int dx) {
        mLeft += dx;
        mRight += dx;
    }

    public void offsetTopAndBottom(int dy) {
        mTop += dy;
        mBottom += dy;
    }

    // --------------------------------------------------------------- plumbing
    public void invalidate() {
    }

    public void requestLayout() {
        if (mParent != null) mParent.requestLayout();
    }

    public boolean post(Runnable r) {
        android.os.HarnessLoop.post(r);
        return true;
    }

    public boolean postDelayed(Runnable r, long delayMillis) {
        android.os.HarnessLoop.postDelayed(r, delayMillis);
        return true;
    }

    public void postOnAnimation(Runnable r) {
        android.os.HarnessLoop.postDelayed(r, 16);
    }

    public void postOnAnimationDelayed(Runnable r, long delayMillis) {
        android.os.HarnessLoop.postDelayed(r, delayMillis);
    }

    public void postInvalidateOnAnimation() {
    }

    public void addOnLayoutChangeListener(OnLayoutChangeListener l) {
    }

    public void removeOnLayoutChangeListener(OnLayoutChangeListener l) {
    }

    public void setOnFocusChangeListener(OnFocusChangeListener l) {
    }

    public void requestFocus() {
    }

    public void requestDisallowInterceptTouchEvent(boolean b) {
    }

    public void bringToFront() {
    }

    public void setSystemUiVisibility(int v) {
    }

    public void setOnGenericMotionListener(Object l) {
    }

    public void scrollTo(int x, int y) {
    }

    public int getScrollY() {
        return 0;
    }

    public int getScrollX() {
        return 0;
    }

    public void computeScroll() {
    }

    public RectF getBounds() {
        return new RectF(mLeft, mTop, mRight, mBottom);
    }

    public String toString() {
        return getClass().getSimpleName() + "@" + System.identityHashCode(this)
                + "(" + getWidth() + "x" + getHeight() + ")";
    }
}
