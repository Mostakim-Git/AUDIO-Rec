package com.mostakim.audiorec.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.mostakim.audiorec.ui.kit.Theme;

import java.util.Locale;

/** Rotary control (USB audio-unit gain, balance, …).  Vertical drag = turn. */
public class KnobView extends View {

    public interface OnValueChanged {
        void onValue(float value, boolean fromUser);
    }

    private Theme mTheme;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private float mMin = 0f, mMax = 1f, mValue = 0.5f, mDefault = 0.5f;
    private String mLabel = "";
    private String mValueFormat = "%.2f";
    private boolean mEnabled = true;
    private float mDownY, mDownValue;
    private boolean mDragging;
    private OnValueChanged mListener;

    public KnobView(Context c) {
        super(c);
        mTheme = new Theme(c);
    }

    public void configure(String label, float min, float max, float value, String fmt) {
        mLabel = label;
        mMin = min;
        mMax = max;
        mValue = value;
        mDefault = value;
        mValueFormat = fmt;
        invalidate();
    }

    public void setValue(float v) {
        mValue = Math.max(mMin, Math.min(mMax, v));
        invalidate();
    }

    public void setEnabledControl(boolean e) {
        mEnabled = e;
        setAlpha(e ? 1f : 0.45f);
        invalidate();
    }

    public float getValue() {
        return mValue;
    }

    public void setOnValueChanged(OnValueChanged l) {
        mListener = l;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!mEnabled) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownY = e.getY();
                mDownValue = mValue;
                mDragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float span = Math.max(1, getHeight() - mTheme.dp(30));
                float delta = (mDownY - e.getY()) / span * (mMax - mMin);
                float nv = Math.max(mMin, Math.min(mMax, mDownValue + delta));
                if (Math.abs(nv - mValue) > 1e-4f) {
                    mValue = nv;
                    invalidate();
                    if (mListener != null) mListener.onValue(mValue, false);
                }
                return true;
            }
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
        if (mTheme == null) return;
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f - mTheme.dp(4);
        float r = Math.min(w, h) / 2f - mTheme.dp(8);
        if (r <= 0) return;

        float sweep = 280f;
        float start = 130f;
        float t = (mValue - mMin) / (mMax - mMin == 0 ? 1 : (mMax - mMin));

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mTheme.dp(3));
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setColor(mTheme.bgSunken);
        mRect.set(cx - r, cy - r, cx + r, cy + r);
        canvas.drawArc(mRect, start, sweep, false, mPaint);

        mPaint.setColor(mEnabled ? mTheme.accent : mTheme.textDisabled);
        canvas.drawArc(mRect, start, sweep * t, false, mPaint);

        // body
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mTheme.bgCardHi);
        float br = r - mTheme.dp(5);
        mRect.set(cx - br, cy - br, cx + br, cy + br);
        canvas.drawCircle(cx, cy, br, mPaint);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(1f);
        mPaint.setColor(mTheme.stroke);
        canvas.drawCircle(cx, cy, br, mPaint);

        // pointer
        double ang = Math.toRadians(start + sweep * t);
        mPaint.setStrokeWidth(Math.max(2f, mTheme.dp(2)));
        mPaint.setColor(mEnabled ? mTheme.textPrimary : mTheme.textDisabled);
        canvas.drawLine(cx, cy, (float) (cx + Math.cos(ang) * br * 0.82f),
                (float) (cy + Math.sin(ang) * br * 0.82f), mPaint);

        // texts
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setColor(mTheme.textPrimary);
        mPaint.setTextSize(mTheme.textSmall);
        mPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        canvas.drawText(String.format(Locale.US, mValueFormat, mValue), cx, h - mTheme.dp(9), mPaint);
        mPaint.setTypeface(null);
        mPaint.setColor(mTheme.textTertiary);
        mPaint.setTextSize(mTheme.textTiny);
        canvas.drawText(mLabel, cx, h - mTheme.dp(0), mPaint);
        mPaint.setTextAlign(Paint.Align.LEFT);
    }
}
