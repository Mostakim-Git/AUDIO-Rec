package com.mostakim.audiorec.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.mostakim.audiorec.ui.kit.Theme;

import java.util.Arrays;

/**
 * Segmented peak/RMS level meter with peak-hold.
 *
 *  • one column per channel (mono, stereo or up to 8 routed inputs)
 *  • green / amber / red zones with a dBFS scale,
 *  • peak hold marker that latches the loudest sample and can be tapped away
 *    (the whole view is one big "clear peaks" target, exactly like the brief
 *    asks: tap on the level meter to clear).
 */
public class LevelMeterView extends View {

    private static final float DB_MIN = -60f;
    private static final float DB_MAX = 0f;

    private Theme mTheme;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private int mChannels = 2;
    private float[] mRms;         // smoothed rms, dB
    private float[] mPeak;        // instantaneous peak, dB
    private float[] mHold;        // latched peak, dB
    private long[] mHoldUntil;    // ms timestamp until the latch starts falling

    private boolean mHorizontal = false;
    private boolean mShowScale = true;
    private boolean mClip = false;              // any channel has hit 0 dBFS
    private long mClipUntil = 0;
    private int mPeakHoldMs = 1800;

    private float[] mSegTops = new float[0];
    private int mSegCount = 0;

    private OnPeakClearedListener mListener;

    public interface OnPeakClearedListener {
        void onPeaksCleared();
    }

    public LevelMeterView(Context c) {
        this(c, null);
    }

    public LevelMeterView(Context c, AttributeSet a) {
        super(c, a);
        init(c, a);
    }

    private void init(Context c, AttributeSet a) {
        if (isInEditMode()) return;
        mTheme = new Theme(c);
        mRms = new float[mChannels];
        mPeak = new float[mChannels];
        mHold = new float[mChannels];
        mHoldUntil = new long[mChannels];
        Arrays.fill(mRms, DB_MIN);
        Arrays.fill(mPeak, DB_MIN);
        Arrays.fill(mHold, DB_MIN);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        setClickable(true);
        if (a != null) {
            setHorizontal(a.getAttributeBooleanValue(
                    "http://schemas.android.com/apk/res/android", "lmHorizontal", false));
        }
    }

    public void setOnPeaksCleared(OnPeakClearedListener l) {
        mListener = l;
    }

    public void setChannelCount(int n) {
        n = Math.max(1, Math.min(8, n));
        if (n == mChannels) return;
        mChannels = n;
        mRms = new float[n];
        mPeak = new float[n];
        mHold = new float[n];
        mHoldUntil = new long[n];
        Arrays.fill(mRms, DB_MIN);
        Arrays.fill(mPeak, DB_MIN);
        Arrays.fill(mHold, DB_MIN);
        requestLayout();
        invalidate();
    }

    public int getChannelCount() {
        return mChannels;
    }

    public void setHorizontal(boolean h) {
        mHorizontal = h;
        requestLayout();
        invalidate();
    }

    public void setShowScale(boolean s) {
        mShowScale = s;
        invalidate();
    }

    public void setPeakHoldMs(int ms) {
        mPeakHoldMs = ms;
    }

    /**
     * Push a new frame.  Values are dBFS (<= -100 means silence).
     * Called from the capture thread; the view takes care of invalidating.
     */
    public void setLevels(float[] rmsDb, float[] peakDb) {
        long now = System.currentTimeMillis();
        int n = Math.min(mChannels, Math.min(rmsDb.length, peakDb.length));
        boolean dirty = false;
        for (int i = 0; i < n; i++) {
            float p = Math.max(DB_MIN, peakDb[i]);
            float r = Math.max(DB_MIN, rmsDb[i]);
            // fall time: rms eases down, peak drops to the rms quickly
            float dr = mRms[i] - getFallStep(getHeight(), mHorizontal);
            mRms[i] = r > dr ? r : Math.max(DB_MIN, dr);
            float dpk = mPeak[i] - (getHeight() > 0 ? 1.2f : 1f);
            mPeak[i] = Math.max(r, Math.max(DB_MIN, dpk));

            if (p >= mHold[i] - 0.05f) {
                mHold[i] = p;
                mHoldUntil[i] = now + mPeakHoldMs;
            } else if (now > mHoldUntil[i] && mHold[i] > DB_MIN + 0.5f) {
                mHold[i] -= 0.6f;
                dirty = true;
            }
            if (r > DB_MIN + 0.25f || mRms[i] > DB_MIN + 0.25f) dirty = true;
            if (p >= -0.2f) {
                mClip = true;
                mClipUntil = now + 1400;
            }
        }
        if (mClip && now > mClipUntil) {
            mClip = false;
            dirty = true;
        }
        if (dirty) postInvalidateOnAnimation();
    }

    private float getFallStep(int px, boolean horizontal) {
        int span = Math.max(1, horizontal ? getWidth() : px);
        return 220f / span + 0.6f;
    }

    /** forget the latched peaks (also called by tapping the meter) */
    public void clearPeaks() {
        for (int i = 0; i < mChannels; i++) {
            mHold[i] = DB_MIN;
            mPeak[i] = DB_MIN;
            mHoldUntil[i] = 0;
        }
        mClip = false;
        invalidate();
        if (mListener != null) mListener.onPeaksCleared();
    }

    public boolean hasClipLatched() {
        for (int i = 0; i < mChannels; i++) {
            if (mHold[i] >= -0.2f) return true;
        }
        return mClip;
    }

    public float holdOf(int channel) {
        return channel >= 0 && channel < mChannels ? mHold[channel] : DB_MIN;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            performClick();
            clearPeaks();
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    // ------------------------------------------------------------- drawing --
    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        int seg = Math.max(1, mTheme == null ? 2 : mTheme.dp(3));
        int gap = Math.max(1, (mTheme == null ? 1 : mTheme.dp(1)));
        int span = mHorizontal ? w : h;
        mSegCount = Math.max(4, span / (seg + gap));
        mSegTops = new float[mSegCount];
        for (int i = 0; i < mSegCount; i++) mSegTops[i] = i * 1f / mSegCount;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isInEditMode() || mTheme == null) return;
        final int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        int pad = mTheme.dp(2);
        int channels = mChannels;

        if (mHorizontal) {
            float chH = (h - pad * 2) / (float) channels;
            for (int ch = 0; ch < channels; ch++) {
                float top = pad + ch * chH;
                drawChannel(canvas, pad, top, w - pad * 2, chH - mTheme.dp(2), ch, true);
            }
        } else {
            // optional scale gutter on the left
            float gutter = mShowScale ? mTheme.dp(30) : 0;
            float chW = (w - gutter - pad * 2) / (float) channels;
            if (mShowScale) drawScale(canvas, h, pad, gutter);
            for (int ch = 0; ch < channels; ch++) {
                float left = pad + gutter + ch * chW;
                drawChannel(canvas, left, pad, chW - mTheme.dp(2), h - pad * 2, ch, false);
            }
        }

        if (mClip) {
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(mTheme.dp(2));
            mPaint.setColor(mTheme.rec);
            mRect.set(1, 1, w - 1, h - 1);
            float r = mTheme.dp(6);
            canvas.drawRoundRect(mRect, r, r, mPaint);
        }
    }

    private void drawScale(Canvas canvas, int h, int pad, float gutter) {
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mTheme.textTertiary);
        mPaint.setTextSize(mTheme.textTiny);
        mPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        float top = pad, bottom = h - pad;
        for (int db = 0; db >= (int) DB_MIN; db -= 6) {
            float y = dbToY(db, top, bottom);
            mPaint.setColor(db == 0 ? mTheme.textSecondary : mTheme.textTertiary);
            canvas.drawText(db == 0 ? "0" : String.valueOf(db), mTheme.dp(2), y + mTheme.dp(3), mPaint);
            mPaint.setStrokeWidth(1f);
            canvas.drawLine(gutter - mTheme.dp(7), y, gutter - mTheme.dp(2), y, mPaint);
        }
        mPaint.setTypeface(null);
    }

    private void drawChannel(Canvas canvas, float left, float top, float w, float h,
                             int ch, boolean horizontal) {
        float r = mTheme.dp(4);

        // trough
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mTheme.meterBg);
        mRect.set(left, top, left + w, top + h);
        canvas.drawRoundRect(mRect, r, r, mPaint);

        // segments
        float gap = Math.max(1f, mTheme.dp(1));
        float segH = (h - gap * (mSegCount - 1)) / mSegCount;
        float rms = mRms[ch], hold = mHold[ch];
        for (int i = 0; i < mSegCount; i++) {
            float frac = (i + 0.5f) / mSegCount;            // 0 at the bottom
            float segDb = DB_MIN + frac * (DB_MAX - DB_MIN);
            float segTop = top + h - (i + 1) * segH - i * gap;
            boolean on = rms >= segDb;
            if (on) {
                mPaint.setColor(zoneColor(segDb));
            } else {
                mPaint.setColor(Theme.alpha(mTheme.meterBg, 0.85f));
            }
            mRect.set(left + mTheme.dp(1), segTop, left + w - mTheme.dp(1), segTop + segH);
            canvas.drawRoundRect(mRect, segH * 0.35f, segH * 0.35f, mPaint);
        }

        if (hold > DB_MIN + 0.4f) {
            float y = dbToY(hold, top, top + h);
            mPaint.setColor(mTheme.meterPeak);
            mPaint.setStrokeWidth(Math.max(2f, mTheme.dp(2)));
            canvas.drawLine(left + mTheme.dp(1), y, left + w - mTheme.dp(1), y, mPaint);
        }

        if (mChannels > 1) {
            mPaint.setColor(mTheme.textTertiary);
            mPaint.setTextSize(mTheme.textTiny);
            canvas.drawText(channelLabel(ch), left + mTheme.dp(2), top + mTheme.dp(10), mPaint);
        }
    }

    private String channelLabel(int ch) {
        return String.valueOf(ch + 1);
    }

    private int zoneColor(float db) {
        if (db >= -0.5f) return mTheme.meterHigh;
        if (db >= -6f) return mTheme.meterMid;
        return mTheme.meterLow;
    }

    private float dbToY(float db, float top, float bottom) {
        float t = (Math.max(DB_MIN, Math.min(DB_MAX, db)) - DB_MIN) / (DB_MAX - DB_MIN);
        return bottom - t * (bottom - top);
    }
}
