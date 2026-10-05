package com.mostakim.audiorec.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.mostakim.audiorec.ui.kit.Theme;

import java.util.Arrays;

/**
 * 1/6-octave-ish spectrum analyser with falling peak caps.
 * Radix-2 FFT with a Hann window; 64 bars over 40 Hz … 20 kHz.
 */
public class SpectrumView extends View {

    private static final int FFT = 2048;
    private static final int BARS = 48;

    private Theme mTheme;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private final float[] mRe = new float[FFT];
    private final float[] mIm = new float[FFT];
    private final float[] mWindow = new float[FFT];
    private final float[] mBars = new float[BARS];
    private final float[] mCaps = new float[BARS];
    private final int[] mBinStart = new int[BARS];
    private final int[] mBinEnd = new int[BARS];

    /** how fast the bars fall, and how fast they rise, in seconds */
    private static final float RELEASE_SECONDS = 0.22f;
    private static final float ATTACK_SECONDS = 0.02f;
    /** peak caps fall at 18 dB per second, on a scale that spans 80 dB */
    private static final float CAP_FALL_PER_SECOND = 18f / 80f;

    private int mSampleRate = 48000;
    private int mChannels = 2;
    private boolean mFrozen = false;
    private long mLastComputeNs = 0L;
    private int mComputes = 0;
    private final float[] mSource = new float[FFT];
    private int mSourceFilled = 0;

    public SpectrumView(Context c) {
        this(c, null);
    }

    public SpectrumView(Context c, AttributeSet a) {
        super(c, a);
        if (!isInEditMode()) mTheme = new Theme(c);
        for (int i = 0; i < FFT; i++) {
            mWindow[i] = (float) (0.5 * (1 - Math.cos(2 * Math.PI * i / (FFT - 1))));
        }
        Arrays.fill(mCaps, 0f);
        computeBins();
    }

    public void setSampleRate(int rate) {
        mSampleRate = rate;
        computeBins();
    }

    public void setChannelCount(int n) {
        mChannels = Math.max(1, n);
    }

    public void setFrozen(boolean f) {
        mFrozen = f;
    }

    public boolean isFrozen() {
        return mFrozen;
    }

    private void computeBins() {
        double fMin = 40, fMax = Math.min(20000, mSampleRate / 2.0);
        double logMin = Math.log10(fMin), logMax = Math.log10(fMax);
        double perBin = (double) mSampleRate / FFT;
        for (int i = 0; i < BARS; i++) {
            double f0 = Math.pow(10, logMin + (logMax - logMin) * i / BARS);
            double f1 = Math.pow(10, logMin + (logMax - logMin) * (i + 1) / BARS);
            mBinStart[i] = Math.max(1, (int) (f0 / perBin));
            mBinEnd[i] = Math.max(mBinStart[i] + 1, (int) (f1 / perBin));
        }
    }

    /** feed interleaved frames - only the first channel is analysed */
    public void push(float[] interleaved, int frames, int channels) {
        if (mFrozen || mTheme == null || interleaved == null || frames <= 0) return;
        int ch = Math.max(1, channels);
        int n = Math.min(frames, FFT);
        int off = frames - n;
        if (n < FFT) {
            // keep the window contiguous: shift what we have and append the rest
            int keep = Math.min(FFT - n, Math.max(0, mSourceFilled));
            System.arraycopy(mSource, mSourceFilled - keep, mSource, 0, keep);
            for (int i = 0; i < n; i++) mSource[keep + i] = interleaved[(off + i) * ch];
            mSourceFilled = keep + n;
        } else {
            for (int i = 0; i < n; i++) mSource[i] = interleaved[(off + i) * ch];
            mSourceFilled = FFT;
        }
        if (mSourceFilled < FFT / 8) return;      // not enough to say anything yet
        compute();
        postInvalidateOnAnimation();
    }

    /** feed a mono analyser window (already contiguous), e.g. from playback */
    public void pushWindow(float[] mono, int frames) {
        if (mFrozen || mTheme == null || mono == null || frames <= 0) return;
        int n = Math.min(frames, FFT);
        int off = frames - n;
        System.arraycopy(mono, off, mSource, 0, n);
        if (n < FFT) java.util.Arrays.fill(mSource, n, FFT, 0f);
        mSourceFilled = FFT;
        compute();
        postInvalidateOnAnimation();
    }

    private void compute() {
        Arrays.fill(mRe, 0f);
        Arrays.fill(mIm, 0f);
        for (int i = 0; i < FFT; i++) mRe[i] = mSource[i] * mWindow[i];
        fft(mRe, mIm);

        // the ballistics are timed, not per-frame: the bars fall at the same speed
        // whether the feed is 30 pushes a second or 120, which is what makes a
        // real-time display readable instead of twitchy
        long now = System.nanoTime();
        float dt = mLastComputeNs == 0 ? 0.03f
                : Math.max(0.001f, Math.min(0.5f, (now - mLastComputeNs) / 1e9f));
        mLastComputeNs = now;
        mComputes++;
        float attack = (float) (1 - Math.exp(-dt / ATTACK_SECONDS));
        float release = (float) (1 - Math.exp(-dt / RELEASE_SECONDS));

        for (int b = 0; b < BARS; b++) {
            float mx = 0f;
            for (int k = mBinStart[b]; k < Math.min(mBinEnd[b], FFT / 2); k++) {
                float mag = (float) Math.hypot(mRe[k], mIm[k]) / (FFT / 2f);
                if (mag > mx) mx = mag;
            }
            float db = mx <= 0f ? -100f : (float) (20 * Math.log10(mx));
            float norm = Math.max(0f, Math.min(1f, (db + 80f) / 80f));
            mBars[b] += (norm - mBars[b]) * (norm > mBars[b] ? attack : release);
            mCaps[b] = Math.max(mCaps[b] - CAP_FALL_PER_SECOND * dt, mBars[b]);
        }
    }

    /** clears the peak caps without disturbing the bars (tap the display) */
    public void clearCaps() {
        Arrays.fill(mCaps, 0f);
        invalidate();
    }

    public int barCount() {
        return BARS;
    }

    public float bar(int i) {
        return mBars[i];
    }

    public float cap(int i) {
        return mCaps[i];
    }

    /** how many FFTs have been drawn - proof the display is running, not stalled */
    public int computeCount() {
        return mComputes;
    }

    private static void fft(float[] re, float[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                float t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            float wr = (float) Math.cos(ang), wi = (float) Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                float cwr = 1f, cwi = 0f;
                for (int j = 0; j < len / 2; j++) {
                    int a = i + j, b = i + j + len / 2;
                    float xr = re[b] * cwr - im[b] * cwi;
                    float xi = re[b] * cwi + im[b] * cwr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr;        im[a] += xi;
                    float nwr = cwr * wr - cwi * wi;
                    cwi = cwr * wi + cwi * wr;
                    cwr = nwr;
                }
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isInEditMode() || mTheme == null) return;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mTheme.bgSunken);
        canvas.drawRect(0, 0, w, h, mPaint);

        mPaint.setColor(Theme.alpha(mTheme.scopeGrid, 0.9f));
        for (int i = 1; i < 4; i++) {
            float y = h * i / 4f;
            canvas.drawRect(0, y, w, y + 1, mPaint);
        }

        float gap = Math.max(1f, mTheme.dp(1.2f));
        float bw = (w - gap * (BARS + 1)) / BARS;
        for (int b = 0; b < BARS; b++) {
            float x = gap + b * (bw + gap);
            float bh = mBars[b] * (h - 2);
            float y = h - bh;
            mPaint.setColor(barColor(b / (float) (BARS - 1), mBars[b]));
            mRect.set(x, y, x + bw, h);
            canvas.drawRoundRect(mRect, bw * 0.28f, bw * 0.28f, mPaint);

            if (mCaps[b] > 0.02f) {
                float cy = h - mCaps[b] * (h - 2);
                mPaint.setColor(mTheme.meterPeak);
                canvas.drawRect(x, cy, x + bw, cy + Math.max(1.5f, mTheme.dp(1.6f)), mPaint);
            }
        }
    }

    private int barColor(float t, float level) {
        int base = t < 0.34f ? mTheme.accent : (t < 0.7f ? mTheme.busy : mTheme.brand);
        return Theme.alpha(base, 0.35f + 0.65f * Math.min(1f, level * 1.6f));
    }
}
