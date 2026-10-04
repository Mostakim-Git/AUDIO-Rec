package com.mostakim.audiorec.ui.screens;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mostakim.audiorec.App;
import com.mostakim.audiorec.R;
import com.mostakim.audiorec.audio.AudioDevice;
import com.mostakim.audiorec.audio.AudioEngine;
import com.mostakim.audiorec.audio.UsbAudioProbe;
import com.mostakim.audiorec.ui.MainActivity;
import com.mostakim.audiorec.ui.kit.Ui;
import com.mostakim.audiorec.ui.widgets.FaderView;
import com.mostakim.audiorec.ui.widgets.KnobView;
import com.mostakim.audiorec.ui.widgets.LevelMeterView;
import com.mostakim.audiorec.util.Fmt;

/**
 * Mixer: per-channel trim, master input gain, monitor level and mute, plus the
 * input's own capabilities.
 *
 * The trims are applied in the capture path before the level meters and the
 * file, so what you see is what gets recorded - the meters move when you touch
 * a fader, which is the only behaviour that makes a mixer screen trustworthy.
 */
public class MixerScreen extends Screen implements AudioEngine.Listener {

    private LevelMeterView mMeters;
    private FaderView mMaster;
    private LinearLayout mStrips;
    private LinearLayout mUnitControls;
    private TextView mMuteBtn, mStateLine;

    public MixerScreen(MainActivity a) {
        super(a, "Mixer", "Gain, trims and monitoring", true);
    }

    @Override
    protected void build(LinearLayout col) {
        header();
        strips();
        master();
        unitControls();
    }

    private void header() {
        AudioDevice in = act.engine().input();
        LinearLayout card = cardStyled(R.drawable.bg_tile);
        LinearLayout row = Ui.row(act);
        row.addView(Ui.title(act, in == null ? "No input" : in.name),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (in != null && in.isUsb) row.addView(Ui.pill(act, "USB", R.drawable.bg_pill, th.accent));
        card.addView(row);
        card.addView(Ui.caption(act, in == null ? "\u2014"
                : in.usbSpec() + "  \u00b7  " + in.shortSpec()));
        mStateLine = Ui.caption(act, "");
        card.addView(mStateLine);

        mMeters = new LevelMeterView(act);
        mMeters.setChannelCount(App.get().prefs().channels());
        mMeters.setShowScale(true);
        mMeters.setPeakHoldMs(2500);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 132));
        lp.topMargin = Ui.dp(act, 10);
        mMeters.setLayoutParams(lp);
        card.addView(mMeters);
        card.addView(Ui.caption(act, "Tap the meter to clear peak holds."));
    }

    private void strips() {
        col.addView(section("CHANNEL TRIM"));
        LinearLayout card = card();
        mStrips = Ui.column(act);
        card.addView(mStrips);
        rebuildStrips();
    }

    private void rebuildStrips() {
        mStrips.removeAllViews();
        int channels = App.get().prefs().channels();
        mStrips.addView(Ui.caption(act, channels == 1 ? "Mono input"
                : channels + " channels from the interface"));
        mStrips.addView(Ui.spacer(act, 8));

        for (int c = 0; c < channels; c++) {
            final int channel = c;
            LinearLayout row = Ui.row(act);
            TextView label = Ui.body(act, channelName(c, channels));
            label.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(act, 78),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(label);

            TextView value = Ui.mono(act, trimLabel(Actr(channel)) + " dB");
            row.addView(value, new LinearLayout.LayoutParams(Ui.dp(act, 68),
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            android.widget.SeekBar sb = new android.widget.SeekBar(act);
            sb.setMax(48);
            sb.setProgress(Actr(channel) + 24);
            sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(android.widget.SeekBar s, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    float db = progress - 24;
                    value.setText(trimLabel(db) + " dB");
                    act.engine().setChannelTrim(channel, db);
                }

                @Override
                public void onStartTrackingTouch(android.widget.SeekBar s) {
                }

                @Override
                public void onStopTrackingTouch(android.widget.SeekBar s) {
                }
            });
            row.addView(sb, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 4));
            mStrips.addView(row);
        }
        mStrips.addView(Ui.button(act, "Reset trims", R.style.Btn_Small, v -> {
            act.engine().resetChannelTrims();
            rebuildStrips();
            toast("Trims reset");
        }));
    }

    private int Actr(int c) {
        return Math.round(act.engine().channelTrim(c));
    }

    private String trimLabel(float db) {
        return (db > 0 ? "+" : "") + String.format(java.util.Locale.US, "%.0f", db);
    }

    private String channelName(int i, int total) {
        if (total == 1) return "Input 1";
        if (total == 2) return i == 0 ? "Input 1 (L)" : "Input 2 (R)";
        return "Input " + (i + 1);
    }

    private void master() {
        col.addView(section("MASTER"));
        LinearLayout card = card();
        LinearLayout row = Ui.row(act);

        mMaster = new FaderView(act);
        mMaster.setLabel("INPUT GAIN");
        mMaster.setValue(App.get().prefs().gainDb());
        mMaster.setDefault(0f);
        mMaster.setOnValueChanged((db, done) -> {
            App.get().prefs().setGainDb(db);
            if (done) toast("Input gain " + Fmt.db(db));
        });
        row.addView(mMaster, new LinearLayout.LayoutParams(0, Ui.dp(act, 172), 1f));

        FaderView monitor = new FaderView(act);
        monitor.setLabel("MONITOR");
        monitor.setValue(App.get().prefs().monitorGainDb());
        monitor.setDefault(-6f);
        monitor.setOnValueChanged((db, done) -> {
            App.get().prefs().setMonitorGainDb(db);
            act.engine().setMonitorGainDb(db);
        });
        row.addView(monitor, new LinearLayout.LayoutParams(0, Ui.dp(act, 172), 1f));

        LinearLayout buttons = Ui.column(act);
        buttons.setPadding(Ui.dp(act, 8), Ui.dp(act, 30), 0, 0);
        mMuteBtn = Ui.button(act, "", R.style.Btn, v -> {
            boolean muted = !App.get().prefs().mute();
            App.get().prefs().setMute(muted);
            act.engine().setMute(muted);
            updateMute();
        });
        buttons.addView(mMuteBtn);
        buttons.addView(Ui.spacer(act, 8));
        buttons.addView(Ui.button(act, "Reset", R.style.Btn_Small, v -> {
            App.get().prefs().setGainDb(0f);
            App.get().prefs().setMonitorGainDb(-6f);
            App.get().prefs().setMute(false);
            act.engine().setMute(false);
            mMaster.setValue(0f);
            monitor.setValue(-6f);
            updateMute();
        }));
        row.addView(buttons);

        card.addView(row);
        card.addView(Ui.caption(act, "Gain and trims are applied once, in 32-bit float, before "
                + "the meters and the file - so the recording matches what you hear."));
        updateMute();
    }

    private void updateMute() {
        if (mMuteBtn == null) return;
        boolean muted = App.get().prefs().mute();
        mMuteBtn.setText(muted ? "MUTED" : "MUTE");
        mMuteBtn.setTextColor(muted ? th.rec : th.textPrimary);
    }

    private void unitControls() {
        col.addView(section("INTERFACE"));
        LinearLayout card = card("Hardware", null);
        mUnitControls = Ui.column(act);
        card.addView(mUnitControls);
        rebuildUnit();
    }

    private void rebuildUnit() {
        mUnitControls.removeAllViews();
        AudioDevice in = act.engine().input();
        if (in == null) {
            mUnitControls.addView(Ui.caption(act, "No input device."));
            return;
        }
        Dialogs_addRow("Device", in.name);
        Dialogs_addRow("Type", in.typeName());
        if (in.isUsb) {
            Dialogs_addRow("USB id", in.id());
            if (!in.vendor.isEmpty()) Dialogs_addRow("Vendor", in.vendor);
            Dialogs_addRow("Class", in.usbSpec());
            Dialogs_addRow("Interfaces", String.valueOf(in.interfaceCount));
            Dialogs_addRow("Formats", in.bitDepthLabel() + " \u00b7 "
                    + Fmt.khz(in.maxSampleRate()) + " max");
            Dialogs_addRow("Async feedback", in.hasSyncEndpoint ? "yes" : "no");
            Dialogs_addRow("Direct claim", in.directClaimed ? "held" : "platform-managed");
        }

        // hardware gain is only exposed by units that offer a UAC feature unit;
        // for the rest the app shows what the platform reports instead of
        // pretending a knob exists
        boolean hasKnob = in.isUsb && in.audioInterfaceCount > 0;
        if (hasKnob) {
            mUnitControls.addView(Ui.spacer(act, 8));
            mUnitControls.addView(Ui.caption(act, "This unit exposes USB-Audio controls. "
                    + "If the hardware is class-compliant the platform applies them; keep the "
                    + "physical gain knob at unity and ride the app's input gain instead."));
        } else {
            mUnitControls.addView(Ui.spacer(act, 8));
            mUnitControls.addView(Ui.caption(act, "No hardware gain control exposed by this "
                    + "endpoint - use the input gain fader above."));
        }
    }

    private void Dialogs_addRow(String key, String value) {
        LinearLayout r = Ui.row(act);
        TextView k = Ui.caption(act, key);
        k.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(act, 110),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        r.addView(k);
        TextView v = Ui.body(act, value);
        r.addView(v, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 4));
        mUnitControls.addView(r);
    }

    // -------------------------------------------------------------- listeners
    @Override
    public void onResume() {
        act.engine().addListener(this);
        rebuildStrips();
        rebuildUnit();
    }

    @Override
    public void onPause() {
        act.engine().removeListener(this);
    }

    @Override
    public void onLevels(float[] rmsDb, float[] peakDb, int channels) {
        act.runOnUiThread(() -> {
            if (mMeters == null) return;
            if (mMeters.getChannelCount() != channels) mMeters.setChannelCount(channels);
            mMeters.setLevels(rmsDb, peakDb);
        });
    }

    @Override
    public void onEngineState(AudioEngine.State s) {
        act.runOnUiThread(() -> {
            if (mStateLine == null) return;
            mStateLine.setText(s == AudioEngine.State.IDLE ? "Idle - nothing is being captured"
                    : "Capture running  \u00b7  " + s.name().toLowerCase());
            mStateLine.setTextColor(s == AudioEngine.State.IDLE ? th.textTertiary : th.ok);
        });
    }

    @Override
    public void onDevicesChanged() {
        act.runOnUiThread(() -> {
            rebuildUnit();
            act.refreshTopbar();
        });
    }
}
