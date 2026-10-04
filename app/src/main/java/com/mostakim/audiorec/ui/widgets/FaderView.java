package com.mostakim.audiorec.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.mostakim.audiorec.ui.kit.Theme;

/**
 * Vertical dB fader (-60 … +12 dB) used for input gain and monitor level.
 * Dragging is relative to the touch-down point so the handle never jumps.
 */
public class FaderView extends View {

    public interface OnValueChanged {
        void onValue(float db, boolean done);
    }

    private static final float MIN = -60f;
    private static final float MAX = 12f;

    private Theme mTheme;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private String mLabel = "";
    private String mUnit = "dB";
    private float mValue = 0f;
    private float mDefault = 0f;
    private float mDownY = 0f;
    private float mDownValue = 0f;
    private boolean mDragging = false;
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

    public void setDefault(float db) {
        mDefault = db;
        invalidate();
    }

    public float getValue() {
        return mValue;
    }

    public void setValue(float db) {
        mValue = clamp(db);
        invalidate();
    }

    public void setOnValueChanged(OnValueChanged l) {
        mListener = l;
    }

    private float clamp(float v) {
        return v < MIN ? MIN : (v > MAX ? MAX : v);
    }

    private float valueToY(float v, float top, float bottom) {
        float t = (v - MIN) / (MAX - MIN);
        return bottom - t * (bottom - top);
    }

    private float yToValue(float y, float top, float bottom) {
        float t = (bottom - y) / (bottom - top);
        return clamp(MIN + t * (MAX - MIN));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        final float top = mTheme.dp(26), bottom = getHeight() - mTheme.dp(22);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownY = e.getY();
                mDownValue = mValue;
                mDragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mDragging) {
                    float span = bottom - top;
                    float delta = (mDownY - e.getY()) / span * (MAX - MIN);
                    float nv = clamp(mDownValue + delta);
                    if (Math.abs(nv - mValue) > 0.01f) {
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
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isInEditMode() || mTheme == null) return;
        final int w = getWidth(), h = getHeight();
        float cx = w / 2f;
        float top = mTheme.dp(26), bottom = h - mTheme.dp(22);

        // label + value
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setColor(mTheme.textTertiary);
        mPaint.setTextSize(mTheme.textTiny);
        canvas.drawText(mLabel, cx, mTheme.dp(11), mPaint);

        mPaint.setColor(mValue >= 0.01f ? mTheme.accent : mTheme.textPrimary);
        mPaint.setTextSize(mTheme.textSmall);
        mPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        String v = (mValue > MIN + 0.5f)
                ? (mValue > 0 ? "+" : "") + String.format(java.util.Locale.US, "%.1f", mValue) + " " + mUnit
                : "-\u221E";
        canvas.drawText(v, cx, h - mTheme.dp(5), mPaint);
        mPaint.setTypeface(null);

        // rail
        float railW = mTheme.dp(4);
        mPaint.setColor(mTheme.bgSunken);
        mRect.set(cx - railW / 2, top, cx + railW / 2, bottom);
        canvas.drawRoundRect(mRect, railW / 2, railW / 2, mPaint);

        // unity tick
        mPaint.setColor(mTheme.stroke);
        mPaint.setStrokeWidth(1f);
        float unityY = valueToY(0, top, bottom);
        canvas.drawLine(cx - mTheme.dp(9), unityY, cx + mTheme.dp(9), unityY, mPaint);

        // filled part
        float vy = valueToY(mValue, top, bottom);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mValue > 0.01f ? Theme.alpha(mTheme.accent, 0.8f)
                : Theme.alpha(mTheme.busy, 0.65f));
        mRect.set(cx - railW / 2, vy, cx + railW / 2, unityY);
        if (unityY > vy) canvas.drawRoundRect(mRect, railW / 2, railW / 2, mPaint);

        // scale ticks
        mPaint.setColor(mTheme.textTertiary);
        mPaint.setTextSize(mTheme.textTiny);
        for (int db = 12; db >= -60; db -= 12) {
            float y = valueToY(db, top, bottom);
            mPaint.setStrokeWidth(1f);
            canvas.drawLine(cx + mTheme.dp(6), y, cx + (db % 24 == 0 ? mTheme.dp(12) : mTheme.dp(9)), y, mPaint);
        }

        // handle
        float hw = mTheme.dp(18), hh = mTheme.dp(11);
        boolean hot = mDragging || Math.abs(mValue - mDefault) < 0.05f;
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
