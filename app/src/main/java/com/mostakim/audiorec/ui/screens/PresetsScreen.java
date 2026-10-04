package com.mostakim.audiorec.ui.screens;

import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.mostakim.audiorec.App;
import com.mostakim.audiorec.R;
import com.mostakim.audiorec.audio.UsbAudioProbe;
import com.mostakim.audiorec.db.Models.Preset;
import com.mostakim.audiorec.ui.Dialogs;
import com.mostakim.audiorec.ui.MainActivity;
import com.mostakim.audiorec.ui.kit.Ui;
import com.mostakim.audiorec.util.Fmt;
import com.mostakim.audiorec.util.Formats;
import com.mostakim.audiorec.util.Prefs;

import java.util.List;

/**
 * Device presets: capture format, buffer, gain and monitoring for an interface.
 *
 * Built-ins ship with the app as starting points and behave like any other row.
 * Applying a preset writes the preferences the recorder reads, so the very next
 * take uses it.
 */
public class PresetsScreen extends Screen {

    public PresetsScreen(MainActivity a) {
        super(a, "Presets", "One tap per interface and format", true);
    }

    @Override
    protected void build(LinearLayout col) {
        List<Preset> presets = act.store().presets();
        long lastId = App.get().prefs().lastPresetId();
        UsbAudioProbe.UsbFacts attached = attachedInterface();

        LinearLayout actions = Ui.row(act);
        actions.addView(Ui.button(act, "+  New preset", R.style.Btn_Primary,
                        v -> Dialogs.presetEditor(act, act.store(), null, () -> refresh())),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(Ui.spacer(act, 8));
        actions.addView(Ui.button(act, "Save current setup", R.style.Btn,
                        v -> saveCurrentAsNew()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        col.addView(actions);
        col.addView(Ui.spacer(act, 12));

        Prefs p = App.get().prefs();
        LinearLayout current = card("Current setup", null);
        current.addView(Ui.caption(act, Fmt.khz(p.sampleRate()) + "  \u00b7  " + p.bitDepth()
                + "-bit  \u00b7  " + Fmt.ch(p.channels()) + "  \u00b7  "
                + Formats.displayName(p.container()) + "  \u00b7  " + p.bufferFrames()
                + "-frame buffer"));
        current.addView(Ui.caption(act, "gain " + Fmt.db(p.gainDb()) + " dB  \u00b7  monitoring "
                + (p.monitor() ? "on at " + Fmt.db(p.monitorGainDb()) + " dB" : "off")
                + (p.mute() ? "  \u00b7  output muted" : "")));
        if (attached != null) {
            current.addView(Ui.caption(act, "attached: " + attached.describe()));
        }

        if (presets.isEmpty()) {
            empty("No presets. Build one for each interface you use - the format, buffer and "
                    + "monitor level get applied together.",
                    "Create a preset",
                    v -> Dialogs.presetEditor(act, act.store(), null, () -> refresh()));
            return;
        }

        col.addView(section(presets.size() + (presets.size() == 1 ? " PRESET" : " PRESETS")));
        for (final Preset pr : presets) {
            boolean isLast = pr.id == lastId;
            boolean matches = attached != null && pr.matches(attached.vendorId, attached.productId);
            LinearLayout card = Ui.card(act);
            card.setBackgroundResource(matches || isLast ? R.drawable.bg_selected : R.drawable.bg_card);

            LinearLayout head = Ui.row(act);
            head.addView(Ui.head(act, pr.name),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (isLast) head.addView(Ui.pill(act, "IN USE", R.drawable.bg_pill, th.accent));
            else if (pr.builtin) head.addView(Ui.badge(act, "built-in"));
            card.addView(head);

            if (!pr.deviceName.isEmpty()) card.addView(Ui.caption(act, pr.deviceName));
            card.addView(Ui.caption(act, pr.summary()));
            card.addView(Ui.caption(act, "gain " + Fmt.db(pr.gainDb) + " dB  \u00b7  monitor "
                    + (pr.monitor ? "on at " + Fmt.db(pr.monitorGainDb) + " dB" : "off")
                    + (pr.vendorId != 0 ? "  \u00b7  USB " + String.format("%04x:%04x",
                    pr.vendorId, pr.productId) : "")
                    + (pr.useCount > 0 ? "  \u00b7  used " + pr.useCount + "\u00d7" : "")));
            if (matches) {
                matchLine(card, "matches the attached interface");
            }
            if (!pr.notes.isEmpty()) {
                android.widget.TextView n = Ui.caption(act, pr.notes);
                n.setTextColor(th.textTertiary);
                card.addView(n);
            }

            card.addView(Ui.spacer(act, 8));
            LinearLayout row = Ui.row(act);
            row.addView(Ui.button(act, "Apply", R.style.Btn_Primary, v -> apply(pr)),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.spacer(act, 6));
            row.addView(Ui.button(act, "Edit", R.style.Btn_Small,
                            v -> Dialogs.presetEditor(act, act.store(), pr, () -> refresh())),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.spacer(act, 6));
            row.addView(Ui.button(act, "Copy", R.style.Btn_Small, v -> duplicate(pr)),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.spacer(act, 6));
            row.addView(Ui.button(act, "Delete", R.style.Btn_Small, v ->
                            Ui.confirm(act, "Delete preset?",
                                    "\"" + pr.name + "\" will be removed.",
                                    "Delete", () -> {
                                        act.store().delete(pr.id);
                                        if (App.get().prefs().lastPresetId() == pr.id) {
                                            App.get().prefs().setLastPresetId(-1);
                                        }
                                        refresh();
                                        toast("Preset deleted");
                                    })),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            card.addView(row);
        }

        col.addView(Ui.spacer(act, 8));
        LinearLayout note = card("How presets are matched", null);
        note.addView(Ui.caption(act, "A preset carries a device name and, when known, the USB "
                + "vendor and product ids. Apply sets the capture format, the buffer size, the "
                + "input gain and the monitor level in one go. Presets never touch your audio - "
                + "they only move the dials."));
    }

    private void matchLine(LinearLayout card, String text) {
        android.widget.TextView t = Ui.caption(act, "\u2713 " + text);
        t.setTextColor(th.ok);
        card.addView(t);
    }

    private UsbAudioProbe.UsbFacts attachedInterface() {
        UsbAudioProbe.UsbFacts best = null;
        for (UsbAudioProbe.UsbFacts f : act.engine().usbDevices()) {
            if (f.audioInterfaceCount > 0) {
                if (best == null || f.endpointCount > best.endpointCount) best = f;
            }
        }
        return best;
    }

    private void apply(Preset pr) {
        Prefs p = App.get().prefs();
        p.setSampleRate(pr.sampleRate);
        p.setBitDepth(pr.bitDepth);
        p.setChannels(pr.channels);
        p.setBufferFrames(pr.bufferFrames);
        p.setContainer(pr.container);
        p.setGainDb(pr.gainDb);
        p.setMonitorGainDb(pr.monitorGainDb);
        p.setMonitor(pr.monitor);
        p.setLastPresetId(pr.id);
        act.store().bumpUse(pr.id);
        act.engine().setMonitorGainDb(pr.monitorGainDb);
        act.engine().setMonitoring(pr.monitor);
        act.refreshTopbar();
        toast("Applied " + pr.name + "  \u00b7  " + pr.summary());
        refresh();
    }

    private void duplicate(Preset pr) {
        Preset copy = new Preset();
        copy.name = pr.name + " copy";
        copy.deviceName = pr.deviceName;
        copy.vendorId = pr.vendorId;
        copy.productId = pr.productId;
        copy.sampleRate = pr.sampleRate;
        copy.bitDepth = pr.bitDepth;
        copy.channels = pr.channels;
        copy.bufferFrames = pr.bufferFrames;
        copy.container = pr.container;
        copy.gainDb = pr.gainDb;
        copy.monitor = pr.monitor;
        copy.monitorGainDb = pr.monitorGainDb;
        copy.notes = pr.notes;
        copy.builtin = false;
        copy.createdAt = System.currentTimeMillis();
        long id = act.store().insert(copy);
        copy.id = id;
        refresh();
        toast("Duplicated as \"" + copy.name + "\"");
    }

    private void saveCurrentAsNew() {
        Prefs p = App.get().prefs();
        Preset pr = new Preset();
        pr.name = "My " + Fmt.khz(p.sampleRate()) + " " + p.bitDepth() + "-bit";
        pr.sampleRate = p.sampleRate();
        pr.bitDepth = p.bitDepth();
        pr.channels = p.channels();
        pr.bufferFrames = p.bufferFrames();
        pr.container = p.container();
        pr.gainDb = p.gainDb();
        pr.monitor = p.monitor();
        pr.monitorGainDb = p.monitorGainDb();
        UsbAudioProbe.UsbFacts f = attachedInterface();
        if (f != null) {
            pr.deviceName = f.productName;
            pr.vendorId = f.vendorId;
            pr.productId = f.productId;
        }
        pr.notes = "Captured from the current setup.";
        pr.createdAt = System.currentTimeMillis();
        act.store().insert(pr);
        refresh();
        act.refreshTopbar();
        toast("Preset saved from the current setup");
    }
}
