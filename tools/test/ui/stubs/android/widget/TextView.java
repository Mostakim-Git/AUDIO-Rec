package android.widget;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;

/** Text metrics are estimated (no font engine here), which is all the harness needs. */
public class TextView extends View {

    private CharSequence mText = "";
    private CharSequence mHint;
    private float mTextSizePx = 14f * 3f;
    private int mTextColor = 0xFF000000;
    private int mHintTextColor = 0xFF888888;
    private int mGravity = Gravity.START | Gravity.TOP;
    private boolean mSingleLine;
    private int mMaxLines = Integer.MAX_VALUE;
    private int mMinLines;
    private Typeface mTypeface;
    private float mLetterSpacing;

    public TextView(Context c) { super(c); }
    public TextView(Context c, AttributeSet a) { super(c, a); }

    public void setText(CharSequence text) { mText = text == null ? "" : text; requestLayout(); }
    public CharSequence getText() { return mText; }
    public int length() { return mText.length(); }
    public void setHint(CharSequence hint) { mHint = hint; }
    public CharSequence getHint() { return mHint; }
    public void setTextSize(float sizePx) { mTextSizePx = sizePx; requestLayout(); }
    public void setTextSize(int unit, float size) {
        mTextSizePx = unit == TypedValue.COMPLEX_UNIT_PX ? size : size * getResources().getDisplayMetrics().density;
        requestLayout();
    }
    public float getTextSize() { return mTextSizePx; }
    public void setTextColor(int color) { mTextColor = color; }
    public int getTextColor() { return mTextColor; }
    public void setHintTextColor(int color) { mHintTextColor = color; }
    public void setGravity(int gravity) { mGravity = gravity; requestLayout(); }
    public int getGravity() { return mGravity; }
    public void setSingleLine(boolean single) { mSingleLine = single; if (single) mMaxLines = 1; requestLayout(); }
    public void setSingleLine() { setSingleLine(true); }
    public void setMaxLines(int max) { mMaxLines = max; requestLayout(); }
    public int getMaxLines() { return mMaxLines; }
    public void setMinLines(int min) { mMinLines = min; requestLayout(); }
    public void setLines(int lines) { mMinLines = lines; mMaxLines = lines; requestLayout(); }
    public void setTypeface(Typeface tf) { mTypeface = tf; }
    public Typeface getTypeface() { return mTypeface; }
    public void setLetterSpacing(float spacing) { mLetterSpacing = spacing; }
    public void setTextAppearance(Context c, int resId) { }
    public void setTextAppearance(int resId) { }
    public void setEllipsize(Object where) { }
    public void setHorizontallyScrolling(boolean b) { }
    public void setIncludeFontPadding(boolean b) { }
    public void setLineSpacing(float add, float mult) { }

    /** estimated text width at the current size */
    public float estimateWidth(CharSequence s) {
        if (s == null || s.length() == 0) return 0f;
        return s.length() * mTextSizePx * 0.52f;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availW = Math.max(0, MeasureSpec.getSize(widthMeasureSpec)
                - getPaddingLeft() - getPaddingRight());
        CharSequence text = mText.length() > 0 ? mText : (mHint == null ? "" : mHint);
        float lineW = estimateWidth(text);
        int lines;
        if (mSingleLine) {
            lines = 1;
        } else if (availW <= 0) {
            lines = 1;
        } else {
            lines = Math.max(1, (int) Math.ceil(lineW / availW));
            lines = Math.min(lines, mMaxLines);
        }
        if (mMinLines > 0) lines = Math.max(lines, mMinLines);
        float lineH = mTextSizePx * 1.25f;
        int w = (int) Math.min(lineW, availW <= 0 ? lineW : availW)
                + getPaddingLeft() + getPaddingRight();
        int h = (int) (lines * lineH) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(
                resolveSize(Math.max(w, getSuggestedMinimumWidth()), widthMeasureSpec),
                resolveSize(Math.max(h, getSuggestedMinimumHeight()), heightMeasureSpec));
    }
}
