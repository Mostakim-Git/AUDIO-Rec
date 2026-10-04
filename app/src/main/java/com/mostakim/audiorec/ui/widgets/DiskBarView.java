package com.mostakim.audiorec.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.View.MeasureSpec;

import com.mostakim.audiorec.ui.kit.Theme;
import com.mostakim.audiorec.util.Fmt;

/** Horizontal storage gauge: used / free, with a colour knee near full. */
public class DiskBarView extends View {

    private Theme mTheme;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private long mUsed = 0, mTotal = 1;
    private String mLeft = "", mRight = "";
    private int mAccent = 0;

    public DiskBarView(Context c) {
        super(c);
        mTheme = new Theme(c);
    }

    public void setData(long used, long total, String left, String right) {
        mUsed = Math.max(0, used);
        mTotal = Math.max(1, total);
        mLeft = left;
        mRight = right;
        // the figures are painted on the canvas, so they have to be readable to
        // accessibility and to the tests as well
        setContentDescription(left + " of " + right);
        invalidate();
    }

    public void setAccent(int color) {
        mAccent = color;
        invalidate();
    }

    /**
     * A view with no measurement takes whatever the parent happens to offer; the
     * screens sit in a ScrollView, which offers wrap-content children nothing, so
     * this gauge used to lay out 0 x 0 with its bar and its figures outside the
     * box. It now asks for the height its drawing needs.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int wantedW = mTheme == null ? 240 : mTheme.dp(240);
        int wantedH = mTheme == null ? 40 : mTheme.dp(40);
        int wMode = MeasureSpec.getMode(widthMeasureSpec);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);
        int width = wMode == MeasureSpec.UNSPECIFIED
                ? wantedW : MeasureSpec.getSize(widthMeasureSpec);
        int height = hMode == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(heightMeasureSpec)
                : (hMode == MeasureSpec.AT_MOST
                        ? Math.min(wantedH, MeasureSpec.getSize(heightMeasureSpec))
                        : wantedH);
        setMeasuredDimension(width, Math.max(1, height));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mTheme == null) return;
        int w = getWidth(), h = getHeight();
        float barH = mTheme.dp(10);
        float top = h - barH - mTheme.dp(6);

        float frac = Math.max(0f, Math.min(1f, mUsed / (float) mTotal));
        int color = mAccent != 0 ? mAccent
                : (frac > 0.92f ? mTheme.rec : frac > 0.75f ? mTheme.warn : mTheme.accent);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mTheme.bgSunken);
        mRect.set(0, top, w, top + barH);
        canvas.drawRoundRect(mRect, barH / 2, barH / 2, mPaint);

        if (frac > 0.001f) {
            mPaint.setColor(color);
            mRect.set(0, top, Math.max(barH, w * frac), top + barH);
            canvas.drawRoundRect(mRect, barH / 2, barH / 2, mPaint);
        }

        mPaint.setTextSize(mTheme.textSmall);
        mPaint.setColor(mTheme.textSecondary);
        mPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(mLeft, 0, mTheme.dp(13), mPaint);
        mPaint.setColor(color);
        mPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(mRight, w, mTheme.dp(13), mPaint);
        mPaint.setTextAlign(Paint.Align.LEFT);
    }

    public static String usageText(long used, long total) {
        return Fmt.percent(total <= 0 ? 0 : used / (float) total) + " used";
    }
}
