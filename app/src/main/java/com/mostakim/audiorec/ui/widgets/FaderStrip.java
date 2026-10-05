package com.mostakim.audiorec.ui.widgets;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mostakim.audiorec.App;
import com.mostakim.audiorec.R;
import com.mostakim.audiorec.ui.kit.SliderMath;
import com.mostakim.audiorec.ui.kit.Ui;

/**
 * A gain control the way a desk has one: a fader for the feel, and two buttons
 * that move it by exactly one step.
 *
 * The step is 0.1 dB in both directions, the value is snapped to it, and a drag
 * that is held still for a moment turns into a fine adjustment (one eighth of the
 * travel) so a tenth of a decibel is reachable with a finger.  The nudge buttons
 * exist because 0.1 dB is far below one pixel of fader travel on a phone.
 */
public class FaderStrip extends LinearLayout {

    private final FaderView mFader;
    private final TextView mMinus, mPlus;
    private final TextView mStepLabel;
    private float mLo = -60f, mHi = 12f;
    private float mStep = SliderMath.STEP_DB;
    private int mFaderHeight;
    private FaderView.OnValueChanged mListener;

    /**
     * The strip is stacked, not split: the fader gets the full width of the
     * column and the nudge buttons sit under it, sharing that width evenly.
     *
     * The old side-by-side arrangement reserved a fixed 62 dp for the buttons;
     * on a narrow column - a two-up fader row on a 360 dp phone, a mixer strip
     * on a folding cover screen - that reserve was wider than the strip itself,
     * so the fader was squeezed to nothing.  Stacked, neither part can starve
     * the other.
     */
    public FaderStrip(Context c) {
        super(c);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);

        mFader = new FaderView(c);
        mFader.setStep(mStep);
        mFader.setRange(mLo, mHi);
        mFader.setOnValueChanged((db, done) -> {
            if (mListener != null) mListener.onValue(db, done);
        });
        mFaderHeight = Ui.dp(c, 172);
        mFader.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, mFaderHeight));
        addView(mFader);

        mMinus = Ui.button(c, "\u2212" + SliderMath.stepLabel(mStep), R.style.Btn_Small,
                v -> nudge(-mStep));
        mPlus = Ui.button(c, "+" + SliderMath.stepLabel(mStep), R.style.Btn_Small,
                v -> nudge(mStep));
        LinearLayout buttons = Ui.row(c);
        buttons.addView(mMinus, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        buttons.addView(Ui.spacer(c, 6));
        buttons.addView(mPlus, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(c, 6);
        buttons.setLayoutParams(blp);
        addView(buttons);

        mStepLabel = Ui.caption(c, "step " + SliderMath.stepLabel(mStep) + " dB");
        mStepLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(c, 2);
        mStepLabel.setLayoutParams(slp);
        addView(mStepLabel);
    }

    /** label shown above the fader, and the value it starts at */
    public FaderStrip configure(String label, float valueDb, float defaultDb) {
        mFader.setLabel(label);
        mFader.setDefault(defaultDb);
        mFader.setValue(valueDb);
        return this;
    }

    /** how tall the fader itself is: 172 dp by default, less on a short screen */
    public FaderStrip setFaderHeight(int px) {
        mFaderHeight = px;
        ViewGroup.LayoutParams lp = mFader.getLayoutParams();
        if (lp != null) {
            lp.height = px;
            mFader.setLayoutParams(lp);
        }
        return this;
    }

    public FaderView fader() {
        return mFader;
    }

    public void setRange(float lo, float hi) {
        mLo = lo;
        mHi = hi;
        mFader.setRange(lo, hi);
    }

    public void setStep(float step) {
        mStep = step;
        mFader.setStep(step);
        mMinus.setText("\u2212" + SliderMath.stepLabel(step));
        mPlus.setText("+" + SliderMath.stepLabel(step));
        mStepLabel.setText("step " + SliderMath.stepLabel(step) + " dB");
    }

    public float getValue() {
        return mFader.getValue();
    }

    public void setValue(float db) {
        mFader.setValue(db);
    }

    public void setOnValueChanged(FaderView.OnValueChanged l) {
        mListener = l;
    }

    /** moves by one step (or several) and reports it, exactly like a drag ending */
    public void nudge(float deltaDb) {
        float next = SliderMath.quantize(SliderMath.clamp(mFader.getValue() + deltaDb, mLo, mHi),
                mStep);
        mFader.setValue(next);
        if (mListener != null) mListener.onValue(next, true);
    }

    /** the persisted input gain, shared by every screen that shows one */
    public static FaderStrip gain(Context c) {
        FaderStrip strip = new FaderStrip(c);
        strip.configure("GAIN", App.get().prefs().gainDb(), 0f);
        strip.setRange(-24f, 24f);
        strip.setOnValueChanged((db, done) -> App.get().prefs().setGainDb(db));
        return strip;
    }

    /** the monitoring level of the fold-back to the interface's outputs */
    public static FaderStrip monitor(Context c) {
        FaderStrip strip = new FaderStrip(c);
        strip.configure("MONITOR", App.get().prefs().monitorGainDb(), -6f);
        strip.setRange(-60f, 12f);
        strip.setOnValueChanged((db, done) -> {
            App.get().prefs().setMonitorGainDb(db);
            if (App.get().audio() != null) App.get().audio().setMonitorGainDb(db);
        });
        return strip;
    }
}
