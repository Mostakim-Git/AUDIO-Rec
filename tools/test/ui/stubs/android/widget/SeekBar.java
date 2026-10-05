package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

public class SeekBar extends View {

    public interface OnSeekBarChangeListener {
        void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser);
        void onStartTrackingTouch(SeekBar seekBar);
        void onStopTrackingTouch(SeekBar seekBar);
    }

    private int mMax = 100;
    private int mProgress;
    private OnSeekBarChangeListener mListener;

    public SeekBar(Context c) { super(c); }
    public SeekBar(Context c, AttributeSet a) { super(c, a); }

    public void setMax(int max) { mMax = max; }
    public int getMax() { return mMax; }
    public void setProgress(int p) {
        mProgress = p;
        if (mListener != null) mListener.onProgressChanged(this, p, true);
    }
    public int getProgress() { return mProgress; }
    public void setOnSeekBarChangeListener(OnSeekBarChangeListener l) { mListener = l; }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = (int) (44 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(Math.max(h, getSuggestedMinimumHeight()), heightMeasureSpec));
    }
}
