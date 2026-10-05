package com.mostakim.audiorec.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import com.mostakim.audiorec.ui.kit.Theme;

/**
 * Rolling waveform ("confidence scope") fed straight from the capture ring
 * buffer.  Shows what is actually arriving from the interface - the fastest way
 * to see clipping, DC offset or a channel that is simply not wired up.
 */
public class WaveScopeView extends View {

    private Theme mTheme;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();

    private float[] mWindow = new float[4096];
    private int mCount = 0;
    private int mChannels = 2;
    private boolean mShowGrid = true;
    private float mGain = 1f;
    private boolean mFrozen = false;

    public WaveScopeView(Context c) {
        this(c, null);
    }

    public WaveScopeView(Context c, AttributeSet a) {
        super(c, a);
        if (!isInEditMode()) {
            mTheme = new Theme(c);
            mPaint.setStrokeWidth(Math.max(2f, mTheme.dp(1.6f)));
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeJoin(Paint.Join.ROUND);
        }
    }

    public void setChannelCount(int n) {
        mChannels = Math.max(1, n);
        invalidate();
    }

    public void setGain(float g) {
        mGain = g;
        invalidate();
    }

    public void setFrozen(boolean f) {
        mFrozen = f;
    }

    public void clear() {
        mCount = 0;
        invalidate();
    }

    /** interleaved samples in [-1,1]; only the first channel is traced */
    public void push(float[] interleaved, int frames, int channels) {
        if (mFrozen || mTheme == null) return;
        int n = Math.min(frames, mWindow.length);
        for (int i = 0; i < n; i++) {
            mWindow[i] = interleaved[i * channels];
        }
        mCount = n;
        postInvalidateOnAnimation();
    }

    /** decimated min/max pairs -> full peak envelope even on tiny screens */
    public void pushEnvelope(float[] frame, int frames, int channels, int step) {
        if (mFrozen || mTheme == null) return;
        if (step < 1) step = 1;
        int out = 0;
        for (int i = 0; i < frames && out < mWindow.length; i += step) {
            float mn = 1f, mx = -1f;
            for (int j = i; j < Math.min(i + step, frames); j++) {
                float v = frame[j * channels];
                if (v < mn) mn = v;
                if (v > mx) mx = v;
            }
            mWindow[out++] = (mn + mx) * 0.5f;
        }
        mCount = out;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isInEditMode() || mTheme == null) return;
        final int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mTheme.bgSunken);
        canvas.drawRect(0, 0, w, h, mPaint);

        float mid = h / 2f;
        if (mShowGrid) {
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(1f);
            mPaint.setColor(mTheme.scopeGrid);
            for (int i = 1; i < 4; i++) {
                float y = h * i / 4f;
                canvas.drawLine(0, y, w, y, mPaint);
            }
            for (int i = 1; i < 8; i++) {
                float x = w * i / 8f;
                canvas.drawLine(x, 0, x, h, mPaint);
            }
            mPaint.setColor(Theme.alpha(mTheme.stroke, 0.7f));
            canvas.drawLine(0, mid, w, mid, mPaint);
        }

        if (mCount < 2) {
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(mTheme.textTertiary);
            mPaint.setTextSize(mTheme.textSmall);
            canvas.drawText("no signal", mTheme.dp(10), mid - mTheme.dp(8), mPaint);
            return;
        }

        mPath.reset();
        float sx = w / (float) (mCount - 1);
        float amp = h * 0.46f;
        mPath.moveTo(0, mid - clamp(mWindow[0] * mGain) * amp);
        for (int i = 1; i < mCount; i++) {
            mPath.lineTo(i * sx, mid - clamp(mWindow[i] * mGain) * amp);
        }
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(Math.max(2f, mTheme.dp(1.6f)));
        mPaint.setColor(mTheme.scopeTrace);
        canvas.drawPath(mPath, mPaint);
    }

    private float clamp(float v) {
        return v > 1f ? 1f : (v < -1f ? -1f : v);
    }
}
