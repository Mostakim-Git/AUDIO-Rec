package com.mostakim.audiorec.ui.screens;

import android.content.pm.PackageInfo;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mostakim.audiorec.R;
import com.mostakim.audiorec.audio.UsbAudioProbe;
import com.mostakim.audiorec.ui.MainActivity;
import com.mostakim.audiorec.ui.kit.Ui;
import com.mostakim.audiorec.util.Fmt;
import com.mostakim.audiorec.util.Formats;

import java.util.List;

/**
 * About: what this is, who wrote it, what it runs on, and what it deliberately
 * does not do.
 *
 * A recorder that hides its limits is a trap, so the hardware notes below state
 * exactly which parts are bit-exact, which go through the platform mixer, and
 * where a vendor control panel is still needed.
 */
public class AboutScreen extends Screen {

    public AboutScreen(MainActivity a) {
        super(a, "About", "Version, author and device notes", true);
    }

    @Override
    protected void build(LinearLayout col) {
        // ------------------------------------------------------------- header
        LinearLayout hero = cardStyled(R.drawable.bg_tile);
        LinearLayout row = Ui.row(act);
        ImageView logo = new ImageView(act);
        logo.setImageResource(R.drawable.ic_logo);
        row.addView(logo, new LinearLayout.LayoutParams(Ui.dp(act, 64), Ui.dp(act, 64)));
        LinearLayout texts = Ui.column(act);
        texts.setPadding(Ui.dp(act, 14), 0, 0, 0);
        TextView title = Ui.title(act, "AUDIO-rec");
        texts.addView(title);
        texts.addView(Ui.caption(act, "USB Audio Recording & Production Workstation"));
        texts.addView(Ui.caption(act, "version " + versionName() + "  \u00b7  " + packageName()));
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hero.addView(row);
        hero.addView(Ui.spacer(act, 10));
        TextView author = Ui.body(act, "by Mostakim Billah");
        author.setTextColor(th.brand);
        hero.addView(author);
        hero.addView(Ui.caption(act, "MIT licensed, 2026. Offline by design: no account, no "
                + "telemetry, no network permission in the manifest."));

        // --------------------------------------------------------------- facts
        LinearLayout facts = card("At a glance", null);
        row(facts, "Capture", "USB audio class interfaces, UAC1 and UAC2");
        row(facts, "Depths", "16-bit integer, 24-bit integer, 32-bit float");
        row(facts, "Rates", "44.1 kHz up to the interface's maximum (up to 384 kHz)");
        row(facts, "Channels", "mono, stereo, and multichannel up to the device's stream");
        row(facts, "Containers", "WAV, FLAC, AIFF, OGG/Opus");
        row(facts, "Playback", "first two outputs of the selected device");
        row(facts, "Buffer", "1024 - 16384 frames");
        row(facts, "Storage", "internal, app folder, or SD card");
        row(facts, "Minimum", "Android 10 (API 29), no root");

        // ------------------------------------------------------------- formats
        LinearLayout formats = card("Why there is no MP3", null);
        formats.addView(Ui.caption(act, "AUDIO-rec records losslessly by default and compresses "
                + "with free codecs only. MP3 is covered by patents that make shipping an encoder "
                + "a licensing problem, so compressed takes use OGG/Opus instead - smaller than "
                + "MP3 at the same quality, and unencumbered."));
        formats.addView(Ui.spacer(act, 6));
        for (String c : Formats.CONTAINERS) {
            row(formats, Formats.displayName(c).split(" ")[0],
                    Formats.ext(c) + "  \u00b7  " + (Formats.isLossless(c)
                            ? "lossless, sample-exact" : "lossy, ~48 kbps/channel at 96-160 kbps"));
        }

        // ------------------------------------------------------------- devices
        LinearLayout devices = card("Known hardware", null);
        devices.addView(Ui.caption(act, "The app records through Android's USB audio path and, "
                + "when it can, claims the interface directly for lower latency. These units are "
                + "the ones it was built and tuned against:"));
        for (String d : new String[]{
                "Jcally JM6 Pro 2 (UAC1/2 DAC + mic input)",
                "M-Audio Duo, Fast Track, Fast Track Pro (UAC1)",
                "PreSonus AudioBox 22VSL / 44VSL (UAC1, class compliant mode)",
                "Blue Snowball, Snowball iCE, Yeti, Yeti Pro",
                "RME Babyface / Babyface Pro (UAC2, class compliant mode)",
                "Zoom H2, H2n, H4 (USB audio interface mode)",
                "Generic HiFi DACs and dongles (UAC1/UAC2)"}) {
            Text(devices, "\u2022  " + d);
        }
        devices.addView(Ui.spacer(act, 8));
        LinearLayout buttons = Ui.row(act);
        buttons.addView(Ui.button(act, "Attached devices", R.style.Btn,
                        v -> navigate(MainActivity.PAGE_DEVICES)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        buttons.addView(Ui.spacer(act, 8));
        buttons.addView(Ui.button(act, "Full descriptor report", R.style.Btn,
                        v -> descriptorReport()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        devices.addView(buttons);

        // --------------------------------------------------------------- limits
        LinearLayout limits = card("Honest limits", null);
        for (String line : new String[]{
                "Hardware gain, pad, phantom power and direct-monitor knobs live on the "
                        + "interface, not in this app - USB audio class has no standard control "
                        + "for them. The mixer's trims are digital, applied before the meters and "
                        + "the file so the display always matches the recording.",
                "If a vendor ships a proprietary control interface (Focusrite, PreSonus, RME "
                        + "TotalMix), the app can claim it so it does not sit in the way, but it "
                        + "does not write vendor registers.",
                "Playback and monitoring use the first two outputs of a multichannel device, as "
                        + "specified. Multichannel output routing is not exposed.",
                "Recording is live and one take at a time; the recorder never rewrites audio it "
                        + "has already written, so a full disk fails loudly instead of silently "
                        + "truncating."}) {
            Text(limits, "\u2022  " + line);
        }

        // -------------------------------------------------------------- license
        LinearLayout legal = card("License", null);
        legal.addView(Ui.caption(act, "MIT License  \u00b7  Copyright (c) 2026 Mostakim Billah"));
        legal.addView(Ui.caption(act, "Permission is hereby granted, free of charge, to any "
                + "person obtaining a copy of this software and associated documentation files, "
                + "to deal in the Software without restriction, including without limitation the "
                + "rights to use, copy, modify, merge, publish, distribute, sublicense, and/or "
                + "sell copies of the Software, subject to the copyright notice being included."));
        legal.addView(Ui.spacer(act, 8));
        LinearLayout legalRow = Ui.row(act);
        legalRow.addView(Ui.button(act, "Copy license", R.style.Btn_Small, v -> copy(
                        "MIT License - Copyright (c) 2026 Mostakim Billah (AUDIO-rec)",
                        "License text copied")),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        legalRow.addView(Ui.spacer(act, 8));
        legalRow.addView(Ui.button(act, "Copy build info", R.style.Btn_Small, v -> {
            copy(buildInfo(), "Build info copied");
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        legal.addView(legalRow);

        TextView sign = Ui.caption(act, "Built to record, not to sell you anything.  "
                + "\u2014  Mostakim Billah");
        sign.setGravity(Gravity.CENTER);
        sign.setPadding(0, Ui.dp(act, 8), 0, Ui.dp(act, 8));
        col.addView(sign);
    }

    // ----------------------------------------------------------------- helpers
    private void row(LinearLayout card, String key, String value) {
        LinearLayout r = Ui.row(act);
        TextView k = Ui.caption(act, key);
        k.setTextColor(th.textTertiary);
        r.addView(k, new LinearLayout.LayoutParams(Ui.dp(act, 96),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        r.addView(Ui.caption(act, value), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 4));
        card.addView(r);
    }

    private void Text(LinearLayout card, String text) {
        TextView t = Ui.caption(act, text);
        t.setPadding(0, Ui.dp(act, 3), 0, Ui.dp(act, 3));
        card.addView(t);
    }

    private String versionName() {
        try {
            PackageInfo i = act.getPackageManager().getPackageInfo(act.getPackageName(), 0);
            return i.versionName == null ? "1.0.0" : i.versionName;
        } catch (Exception e) {
            return "1.0.0";
        }
    }

    private String packageName() {
        return act.getPackageName();
    }

    private String buildInfo() {
        return "AUDIO-rec " + versionName() + " (" + packageName() + ")\n"
                + "minSdk 29 / targetSdk 34, framework APIs only (no Google libraries)\n"
                + "Android " + android.os.Build.VERSION.RELEASE + " (API "
                + android.os.Build.VERSION.SDK_INT + ") on " + android.os.Build.MANUFACTURER
                + " " + android.os.Build.MODEL + "\n"
                + "author Mostakim Billah  \u00b7  MIT License  \u00b7  offline build\n";
    }

    private void copy(String text, String toastMsg) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                act.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("AUDIO-rec", text));
            toast(toastMsg);
        }
    }

    private void descriptorReport() {
        List<UsbAudioProbe.UsbFacts> list = act.engine().usbDevices();
        if (list.isEmpty()) {
            Ui.longToast(act, "No USB device attached. Plug the interface in and tap Attached "
                    + "devices - the report lists class codes, endpoints and sync paths.");
            return;
        }
        StringBuilder b = new StringBuilder();
        for (UsbAudioProbe.UsbFacts f : list) {
            b.append(f.productName).append('\n');
            b.append("  ").append(f.describe()).append('\n');
            b.append("  class ").append(String.format("%02x", f.usbClass)).append('/')
                    .append(String.format("%02x", f.usbSubclass)).append("  audio interfaces ")
                    .append(f.audioInterfaceCount).append("  streaming ")
                    .append(f.streamingInterfaces).append("  control ")
                    .append(f.controlInterfaces).append('\n');
            b.append("  ").append(f.uacTwo ? "UAC2" : (f.uacOne ? "UAC1" : "class not advertised"))
                    .append(f.hasSyncEndpoint ? "  asynchronous feedback" : "")
                    .append(f.vendorSpecificControl ? "  vendor control interface" : "")
                    .append(f.permissionGranted ? "  permission granted" : "  permission pending")
                    .append(f.directClaimed ? "  claimed directly" : "").append('\n');
        }
        b.append("\nPlayback is stereo on the first two outputs; capture uses the isochronous IN "
                + "endpoint at the rate selected in the recorder.");
        Ui.dialog(act, "USB descriptor report").setMessage(b.toString())
                .setPositiveButton("Close", null).show();
    }
}
