package com.mostakim.audiorec.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.mostakim.audiorec.ui.kit.SliderMath;
import com.mostakim.audiorec.ui.kit.Theme;

/**
 * Vertical dB fader used for input gain and monitor level.
 *
 * Dragging is relative to the touch-down point so the handle never jumps, and the
 * value is snapped to a step - 0.1 dB by default - so a nudge in either
 * direction is exactly one step.  A touch that is held still for a moment turns
 * into a fine adjustment (an eighth of the travel), which is what makes a tenth
 * of a decibel reachable with a fingertip.
 */
public class FaderView extends View {

    public interface OnValueChanged {
        void onValue(float db, boolean done);
    }

    private Theme mTheme;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private String mLabel = "";
    private String mUnit = "dB";
    private float mLo = -60f, mHi = 12f;
    private float mStep = SliderMath.STEP_DB;
    private float mValue = 0f;
    private float mDefault = 0f;
    private float mDownY = 0f;
    private float mDownValue = 0f;
    private long mDownAt = 0L;
    private boolean mDragging = false;
    private boolean mFine = false;
    private OnValueChanged mListener;

    public FaderView(Context c) {
        this(c, null);
    }

    public FaderView(Context c, AttributeSet a) {
        super(c, a);
        if (!isInEditMode()) mTheme = new Theme(c);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setLabel(String l) {
        mLabel = l;
        invalidate();
    }

    public void setUnit(String u) {
        mUnit = u;
        invalidate();
    }

    /** the smallest change a nudge or a drag can produce */
    public void setStep(float step) {
        mStep = step > 0f ? step : SliderMath.STEP_DB;
        mValue = snap(mValue);
        invalidate();
    }

    public float getStep() {
        return mStep;
    }

    public void setRange(float lo, float hi) {
        mLo = lo;
        mHi = Math.max(hi, lo + 1f);
        mValue = snap(mValue);
        invalidate();
    }

    public void setDefault(float db) {
        mDefault = db;
        invalidate();
    }

    public float getValue() {
        return mValue;
    }

    public void setValue(float db) {
        mValue = snap(db);
        invalidate();
    }

    public void setOnValueChanged(OnValueChanged l) {
        mListener = l;
    }

    private float snap(float v) {
        return SliderMath.quantize(SliderMath.clamp(v, mLo, mHi), mStep);
    }

    /** the rail's usable span, shrunk when the view is short (landscape phones) */
    private float top() {
        return Math.min(mTheme.dp(26), Math.max(1f, getHeight() * 0.18f));
    }

    private float bottom() {
        return Math.max(top() + 1f, getHeight() - Math.min(mTheme.dp(22), getHeight() * 0.16f));
    }

    private float valueToY(float v) {
        float top = top(), bottom = bottom();
        float t = (v - mLo) / (mHi - mLo);
        return bottom - t * (bottom - top);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownY = e.getY();
                mDownValue = mValue;
                mDownAt = e.getEventTime();
                mFine = false;
                mDragging = true;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mDragging) {
                    boolean fine = SliderMath.isFine(mDownAt, e.getEventTime());
                    if (fine != mFine) {
                        mFine = fine;
                        invalidate();
                    }
                    float nv = snap(SliderMath.dragValue(mDownValue, mDownY, e.getY(),
                            top(), bottom(), mLo, mHi, mFine));
                    if (Math.abs(nv - mValue) > mStep / 2f) {
                        mValue = nv;
                        invalidate();
                        if (mListener != null) mListener.onValue(mValue, false);
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mDragging && mListener != null) mListener.onValue(mValue, true);
                mDragging = false;
                mFine = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isInEditMode() || mTheme == null) return;
        final int w = getWidth(), h = getHeight();
        float cx = w / 2f;
        float top = top(), bottom = bottom();

        // label + value
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setColor(mFine ? mTheme.accent : mTheme.textTertiary);
        mPaint.setTextSize(mTheme.textTiny);
        canvas.drawText(mFine ? mLabel + "  \u00b7  FINE" : mLabel, cx, Math.max(mTheme.dp(11),
                top * 0.55f), mPaint);

        mPaint.setColor(mValue >= 0.05f ? mTheme.accent : mTheme.textPrimary);
        mPaint.setTextSize(mTheme.textSmall);
        mPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        String v = (mValue > mLo + 0.05f)
                ? SliderMath.formatDb(mValue, mUnit)
                : "-\u221E";
        canvas.drawText(v, cx, h - mTheme.dp(5), mPaint);
        mPaint.setTypeface(null);

        // rail
        float railW = mTheme.dp(4);
        mPaint.setColor(mTheme.bgSunken);
        mRect.set(cx - railW / 2, top, cx + railW / 2, bottom);
        canvas.drawRoundRect(mRect, railW / 2, railW / 2, mPaint);

        // unity tick, only when unity is inside the range
        mPaint.setColor(mTheme.stroke);
        mPaint.setStrokeWidth(1f);
        if (mLo < 0f && mHi > 0f) {
            float unityY = valueToY(0f);
            canvas.drawLine(cx - mTheme.dp(9), unityY, cx + mTheme.dp(9), unityY, mPaint);
        }

        // filled part, from unity (or from the bottom when unity is out of range)
        float vy = valueToY(mValue);
        float baseY = valueToY(mLo < 0f && mHi > 0f ? 0f : mLo);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mValue > 0.05f ? Theme.alpha(mTheme.accent, 0.8f)
                : Theme.alpha(mTheme.busy, 0.65f));
        mRect.set(cx - railW / 2, Math.min(vy, baseY), cx + railW / 2, Math.max(vy, baseY));
        canvas.drawRoundRect(mRect, railW / 2, railW / 2, mPaint);

        // scale ticks every 12 dB
        mPaint.setColor(mTheme.textTertiary);
        mPaint.setTextSize(mTheme.textTiny);
        int first = (int) Math.ceil(mLo / 12f) * 12;
        for (int db = first; db <= mHi; db += 12) {
            float y = valueToY(db);
            mPaint.setStrokeWidth(1f);
            canvas.drawLine(cx + mTheme.dp(6), y,
                    cx + (db % 24 == 0 ? mTheme.dp(12) : mTheme.dp(9)), y, mPaint);
        }

        // handle
        float hw = mTheme.dp(18), hh = mTheme.dp(11);
        boolean hot = mDragging || Math.abs(mValue - mDefault) < mStep / 2f;
        mPaint.setColor(mTheme.bgCardHi);
        mRect.set(cx - hw / 2, vy - hh / 2, cx + hw / 2, vy + hh / 2);
        canvas.drawRoundRect(mRect, mTheme.dp(3), mTheme.dp(3), mPaint);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(Math.max(2f, mTheme.dp(1.4f)));
        mPaint.setColor(hot ? mTheme.accent : mTheme.textSecondary);
        canvas.drawRoundRect(mRect, mTheme.dp(3), mTheme.dp(3), mPaint);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(hot ? mTheme.accent : mTheme.stroke);
        canvas.drawLine(cx - hw / 3, vy, cx + hw / 3, vy, mPaint);
        mPaint.setTextAlign(Paint.Align.LEFT);
    }
}
