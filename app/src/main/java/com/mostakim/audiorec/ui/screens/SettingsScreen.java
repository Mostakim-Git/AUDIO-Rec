package com.mostakim.audiorec.ui.screens;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.mostakim.audiorec.App;
import com.mostakim.audiorec.R;
import com.mostakim.audiorec.audio.AudioDevice;
import com.mostakim.audiorec.ui.Dialogs;
import com.mostakim.audiorec.ui.MainActivity;
import com.mostakim.audiorec.ui.kit.Ui;
import com.mostakim.audiorec.util.Fmt;
import com.mostakim.audiorec.util.Formats;
import com.mostakim.audiorec.util.Prefs;

import java.util.List;

/**
 * Settings: the defaults every new take starts from, plus the two toggles that
 * decide how the app behaves when an interface is plugged in.
 *
 * Everything here is local; nothing leaves the device and nothing requires an
 * account.
 */
public class SettingsScreen extends Screen {

    public SettingsScreen(MainActivity a) {
        super(a, "Settings", "Capture defaults and behaviour", true);
    }

    @Override
    protected void build(LinearLayout col) {
        Prefs p = App.get().prefs();

        // ---------------------------------------------------------- capture
        LinearLayout capture = card("Capture defaults", null);
        capture.addView(valueRow("Sample rate", Fmt.khz(p.sampleRate()),
                "Highest the interface supports is offered first", v -> pickRate()));
        capture.addView(valueRow("Bit depth", p.bitDepth() + "-bit",
                "16 integer / 24 integer / 32 float", v -> pickDepth()));
        capture.addView(valueRow("Channels", Fmt.ch(p.channels()),
                "Mono, stereo or the interface's multichannel stream", v -> pickChannels()));
        capture.addView(valueRow("Buffer", p.bufferFrames() + " frames",
                "1024 - 16384; larger buffers survive slower storage", v -> pickBuffer()));
        capture.addView(valueRow("Container", Formats.displayName(p.container()),
                "WAV / FLAC / AIFF / OGG-Opus (no MP3)", v -> pickContainer()));

        // ------------------------------------------------------------- levels
        LinearLayout levels = card("Levels", null);
        levels.addView(Ui.caption(act, "Input gain is a digital trim applied before the meters "
                + "and the file, so the display always matches what is recorded. Hardware gain "
                + "on the interface itself is set on the interface."));
        levels.addView(sliderRow("Input gain", p.gainDb(), -24f, 24f, db -> {
            App.get().prefs().setGainDb(db);
            act.refreshHeader();
        }));
        levels.addView(toggleRow("Monitoring", "Fold the input back to the first two outputs",
                p.monitor(), on -> {
                    App.get().prefs().setMonitor(on);
                    act.engine().setMonitoring(on);
                }));
        levels.addView(sliderRow("Monitor level", p.monitorGainDb(), -40f, 6f, db -> {
            App.get().prefs().setMonitorGainDb(db);
            act.engine().setMonitorGainDb(db);
        }));
        levels.addView(toggleRow("Mute output", "Silences monitoring while the take keeps running",
                p.mute(), on -> {
                    App.get().prefs().setMute(on);
                    act.engine().setMute(on);
                }));
        levels.addView(sliderRow("Peak warning", p.peakWarnDb(), -24f, 0f, db -> {
            App.get().prefs().setPeakWarnDb(db);
            act.refreshPage(MainActivity.PAGE_RECORDER);
        }));

        // ------------------------------------------------------------ devices
        LinearLayout devices = card("Devices", null);
        AudioDevice in = act.engine().input();
        AudioDevice out = act.engine().output();
        devices.addView(valueRow("Input", in == null ? "system default" : in.name,
                in == null ? "" : in.shortSpec(), v -> pickDevice(true)));
        devices.addView(valueRow("Output", out == null ? "system default" : out.name,
                out == null ? "" : out.shortSpec(), v -> pickDevice(false)));
        devices.addView(toggleRow("Direct USB claim", "Try to claim the USB audio interfaces "
                        + "directly for lower latency capture",
                p.directUsbClaim(), on -> {
                    App.get().prefs().setDirectUsbClaim(on);
                    act.engine().refreshDevices();
                }));
        devices.addView(toggleRow("Arm on attach", "Start monitoring when an interface is plugged in",
                p.autoArmOnAttach(), on -> App.get().prefs().setAutoArmOnAttach(on)));
        devices.addView(Ui.spacer(act, 6));
        devices.addView(Ui.caption(act, "A USB request is sent when the app is already open; "
                + "an attached interface is remembered for next time."));
        devices.addView(Ui.button(act, "Re-scan USB and audio devices", R.style.Btn, v -> {
            act.engine().refreshDevices();
            toast("Rescanned audio and USB devices");
            refresh();
        }));

        // ----------------------------------------------------------- behaviour
        LinearLayout behave = card("Behaviour", null);
        behave.addView(toggleRow("Dither", "Adds shaped noise when writing 16-bit integer "
                        + "from the 32-bit internal path",
                p.dither(), on -> App.get().prefs().setDither(on)));
        behave.addView(toggleRow("Keep screen on", "Stops the display sleeping while armed",
                p.keepScreenOn(), on -> {
                    App.get().prefs().setKeepScreenOn(on);
                    act.refreshHeader();
                }));
        behave.addView(toggleRow("Split mono inputs", "Treat each input of an interface as an "
                        + "independent mono track name",
                p.splitMonoInputs(), on -> App.get().prefs().setSplitMonoInputs(on)));
        behave.addView(valueRow("Recording folder",
                p.recordDir() == null ? "app default" : p.recordDir().getName(),
                p.recordDir() == null ? "" : p.recordDir().getAbsolutePath(),
                v -> navigate(MainActivity.PAGE_STORAGE)));

        // ------------------------------------------------------------- danger
        LinearLayout danger = card("Reset", null);
        danger.addView(Ui.caption(act, "Resetting settings keeps every recording, session and "
                + "preset. Erasing app data removes them."));
        LinearLayout row = Ui.row(act);
        row.addView(Ui.button(act, "Reset settings", R.style.Btn, v ->
                        Ui.confirm(act, "Reset settings?",
                                "Capture defaults and toggles go back to their factory values. "
                                        + "Your recordings stay where they are.",
                                "Reset", this::resetSettings)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.spacer(act, 8));
        row.addView(Ui.button(act, "Diagnostics", R.style.Btn, v -> diagnostics()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Ui.addWide(danger, row);
        danger.addView(Ui.spacer(act, 8));
        danger.addView(Ui.button(act, "Restore the built-in presets", R.style.Btn_Danger,
                v -> Ui.confirm(act, "Restore built-in presets?",
                        "The shipped presets are re-added under a fresh id; your own presets are "
                                + "untouched.",
                        "Restore", () -> {
                            int n = act.store().restoreBuiltinPresets();
                            toast(n + " built-in presets restored");
                            act.refreshHeader();
                            refresh();
                        })));
    }

    // ------------------------------------------------------------------ helpers
    private LinearLayout valueRow(String label, String value, String hint, View.OnClickListener click) {
        TextView v = Ui.button(act, value, R.style.Btn_Small, click);
        LinearLayout row = Ui.settingRow(act, label, v, hint);
        row.setClickable(true);
        row.setOnClickListener(click);
        return row;
    }

    private LinearLayout toggleRow(String label, String hint, boolean on, final OnToggle cb) {
        final boolean[] state = {on};
        final TextView pill = Ui.pill(act, on ? "ON" : "OFF",
                on ? R.drawable.bg_pill : R.drawable.bg_badge, on ? th.ok : th.textTertiary);
        LinearLayout row = Ui.settingRow(act, label, pill, hint);
        row.setClickable(true);
        row.setOnClickListener(v -> {
            state[0] = !state[0];
            pill.setText(state[0] ? "ON" : "OFF");
            pill.setTextColor(state[0] ? th.ok : th.textTertiary);
            pill.setBackgroundResource(state[0] ? R.drawable.bg_pill : R.drawable.bg_badge);
            cb.onToggle(state[0]);
        });
        return row;
    }

    private interface OnToggle {
        void onToggle(boolean on);
    }

    private interface OnDb {
        void onDb(float db);
    }

    private LinearLayout sliderRow(String label, float value, final float min, final float max,
                                   final OnDb cb) {
        LinearLayout wrap = Ui.column(act);
        final TextView readout = Ui.body(act, label + "   " + Fmt.db(value) + " dB");
        Ui.addWide(wrap, readout);
        SeekBar bar = new SeekBar(act);
        bar.setMax(1000);
        bar.setProgress((int) (1000f * (value - min) / (max - min)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                float db = min + (max - min) * progress / 1000f;
                readout.setText(label + "   " + Fmt.db(db) + " dB");
                if (fromUser) cb.onDb(db);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        Ui.addWide(wrap, bar);
        wrap.setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 6));
        return wrap;
    }

    // -------------------------------------------------------------- pickers
    private int[] rates() {
        int[] r = act.engine().availableRates();
        return r == null || r.length == 0 ? Formats.ALL_RATES : r;
    }

    private void pickRate() {
        final int[] r = rates();
        String[] labels = new String[r.length];
        for (int i = 0; i < r.length; i++) labels[i] = r[i] + " Hz";
        Dialogs.choose(act, "Sample rate", labels,
                Math.max(0, Formats.indexOf(r, App.get().prefs().sampleRate())),
                idx -> {
                    App.get().prefs().setSampleRate(r[idx]);
                    act.engine().refreshDevices();
                    refresh();
                });
    }

    private void pickDepth() {
        final int[] d = act.engine().availableDepths();
        final int[] list = d == null || d.length == 0 ? Formats.BIT_DEPTHS : d;
        String[] labels = new String[list.length];
        for (int i = 0; i < list.length; i++) {
            labels[i] = list[i] + (list[i] == 32 ? "-bit float" : "-bit integer");
        }
        Dialogs.choose(act, "Bit depth", labels,
                Math.max(0, Formats.indexOf(list, App.get().prefs().bitDepth())),
                idx -> {
                    App.get().prefs().setBitDepth(list[idx]);
                    refresh();
                });
    }

    private void pickChannels() {
        final int[] c = {1, 2, 4, 6, 8};
        String[] labels = new String[c.length];
        for (int i = 0; i < c.length; i++) labels[i] = Fmt.ch(c[i]);
        Dialogs.choose(act, "Channels", labels,
                Math.max(0, Formats.indexOf(c, App.get().prefs().channels())),
                idx -> {
                    App.get().prefs().setChannels(c[idx]);
                    act.engine().refreshDevices();
                    refresh();
                });
    }

    private void pickBuffer() {
        String[] labels = new String[Formats.BUFFER_SIZES.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Formats.BUFFER_SIZES[i] + " frames  ("
                    + String.format("%.1f", Formats.BUFFER_SIZES[i] * 1000.0 / 48000) + " ms @48k)";
        }
        Dialogs.choose(act, "Buffer size", labels,
                Math.max(0, Formats.indexOf(Formats.BUFFER_SIZES, App.get().prefs().bufferFrames())),
                idx -> {
                    App.get().prefs().setBufferFrames(Formats.BUFFER_SIZES[idx]);
                    refresh();
                });
    }

    private void pickContainer() {
        String[] labels = new String[Formats.CONTAINERS.size()];
        for (int i = 0; i < labels.length; i++) {
            String c = Formats.CONTAINERS.get(i);
            labels[i] = Formats.displayName(c) + "  " + Formats.ext(c)
                    + (Formats.isLossless(c) ? "   lossless" : "   lossy, smaller files");
        }
        Dialogs.choose(act, "Container", labels,
                Math.max(0, Formats.CONTAINERS.indexOf(App.get().prefs().container())),
                idx -> {
                    App.get().prefs().setContainer(Formats.CONTAINERS.get(idx));
                    refresh();
                });
    }

    private void pickDevice(final boolean input) {
        List<AudioDevice> list = input ? act.engine().inputs() : act.engine().outputs();
        if (list.isEmpty()) {
            toast("No devices reported");
            return;
        }
        String[] labels = new String[list.size() + 1];
        labels[0] = "System default";
        for (int i = 0; i < list.size(); i++) {
            AudioDevice d = list.get(i);
            labels[i + 1] = d.name + "   " + d.shortSpec() + (d.isUsb ? "   USB" : "");
        }
        Dialogs.choose(act, input ? "Input device" : "Output device", labels, 0, idx -> {
            if (idx == 0) {
                if (input) App.get().prefs().setInputDeviceId("");
                else App.get().prefs().setOutputDeviceId("");
            } else if (input) {
                act.engine().selectInput(list.get(idx - 1));
            } else {
                act.engine().selectOutput(list.get(idx - 1));
            }
            refresh();
            act.refreshHeader();
        });
    }

    // ------------------------------------------------------------- actions
    private void resetSettings() {
        Prefs p = App.get().prefs();
        p.setSampleRate(48000);
        p.setBitDepth(24);
        p.setChannels(2);
        p.setBufferFrames(2048);
        p.setContainer(Formats.WAV);
        p.setGainDb(0f);
        p.setMonitor(false);
        p.setMonitorGainDb(-6f);
        p.setMute(false);
        p.setDither(false);
        p.setKeepScreenOn(true);
        p.setSplitMonoInputs(false);
        p.setPeakWarnDb(-1f);
        p.setDirectUsbClaim(false);
        p.setAutoArmOnAttach(true);
        p.setInputDeviceId("");
        p.setOutputDeviceId("");
        p.setChannelTrims(new float[8]);
        act.engine().resetChannelTrims();
        act.engine().refreshDevices();
        act.refreshHeader();
        toast("Settings reset");
        refresh();
    }

    private void diagnostics() {
        Prefs p = App.get().prefs();
        StringBuilder b = new StringBuilder();
        b.append("AUDIO-rec diagnostics\n");
        b.append("app       AUDIO-rec 1.0.0 (Mostakim Billah)\n");
        b.append("android   ").append(android.os.Build.VERSION.RELEASE)
                .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
        b.append("device    ").append(android.os.Build.MANUFACTURER).append(' ')
                .append(android.os.Build.MODEL).append('\n');
        b.append("capture   ").append(p.sampleRate()).append(" Hz ").append(p.bitDepth())
                .append("-bit ").append(p.channels()).append(" ch ").append(p.container())
                .append(" buffer ").append(p.bufferFrames()).append('\n');
        b.append("gain      ").append(Fmt.db(p.gainDb())).append(" dB, monitor ")
                .append(p.monitor() ? "on " + Fmt.db(p.monitorGainDb()) + " dB" : "off")
                .append(p.mute() ? ", muted" : "").append('\n');
        float[] trims = p.channelTrims();
        StringBuilder t = new StringBuilder();
        for (int i = 0; i < trims.length; i++) {
            if (trims[i] != 0f) t.append(i + 1).append(':').append(Fmt.db(trims[i])).append(' ');
        }
        b.append("trims     ").append(t.length() == 0 ? "flat" : t.toString().trim()).append('\n');
        AudioDevice in = act.engine().input();
        AudioDevice out = act.engine().output();
        b.append("input     ").append(in == null ? "system default" : in.name + " " + in.shortSpec())
                .append('\n');
        b.append("output    ").append(out == null ? "system default" : out.name + " " + out.shortSpec())
                .append('\n');
        b.append("folder    ").append(p.recordDir() == null ? "app default"
                : p.recordDir().getAbsolutePath()).append('\n');
        b.append("usb\n");
        for (com.mostakim.audiorec.audio.UsbAudioProbe.UsbFacts f : act.engine().usbDevices()) {
            b.append("  ").append(f.productName).append("  ").append(f.describe()).append('\n');
        }
        b.append("library   ").append(act.store().trackCount()).append(" tracks, ")
                .append(act.store().sessionCount()).append(" sessions, ")
                .append(act.store().presetCount()).append(" presets, ")
                .append(act.store().exportCount()).append(" exports\n");
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                act.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("AUDIO-rec diagnostics",
                    b.toString()));
            toast("Diagnostics copied to the clipboard");
        }
        Ui.dialog(act, "Diagnostics").setMessage(b.toString()).setPositiveButton("Close", null).show();
    }

}
