package com.mostakim.audiorec.ui.screens;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mostakim.audiorec.App;
import com.mostakim.audiorec.R;
import com.mostakim.audiorec.audio.AudioDevice;
import com.mostakim.audiorec.audio.UsbAudioProbe;
import com.mostakim.audiorec.ui.MainActivity;
import com.mostakim.audiorec.ui.kit.Ui;
import com.mostakim.audiorec.util.Fmt;

import java.util.List;

/**
 * Endpoint browser.
 *
 * Two lists: the USB units physically attached (with their descriptors) and every
 * audio endpoint Android will route to.  This is where input/output selection
 * happens, and where the interface's real capabilities are shown, including the
 * vendor-specific control interface that makes people think a custom driver is
 * needed.
 */
public class DevicesScreen extends Screen {

    public DevicesScreen(MainActivity a) {
        super(a, "Devices", "USB interfaces and audio endpoints", true);
    }

    @Override
    protected void build(LinearLayout col) {
        col.addView(Ui.caption(act, "AUDIO-rec records through Android's USB-audio stack, so "
                + "class-compliant interfaces (UAC1/UAC2) stream without root or extra drivers. "
                + "Vendor control interfaces are probed and can be claimed directly."));

        usbSection();
        endpointsSection(true);
        endpointsSection(false);
        notes();
    }

    // ------------------------------------------------------------------- USB
    private void usbSection() {
        col.addView(section("USB DEVICES"));
        List<UsbAudioProbe.UsbFacts> devices = act.engine().usbDevices();
        if (devices.isEmpty()) {
            empty("Nothing on the USB bus.\nPlug the interface in - it is detected "
                    + "automatically, and a take is never lost if it is pulled out.",
                    "Rescan", v -> {
                        act.engine().refreshDevices();
                        refresh();
                    });
            return;
        }
        for (final UsbAudioProbe.UsbFacts f : devices) {
            LinearLayout card = card();
            LinearLayout head = Ui.row(act);
            head.addView(Ui.head(act, f.productName.isEmpty() ? "USB device" : f.productName),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            boolean audio = UsbAudioProbe.looksLikeAudioInterface(f);
            head.addView(Ui.pill(act, audio ? "AUDIO" : "OTHER",
                    audio ? R.drawable.bg_pill : R.drawable.bg_badge,
                    audio ? th.accent : th.textTertiary));
            Ui.addWide(card, head);

            addRow(card, "USB id", String.format(java.util.Locale.US, "%04X:%04X",
                    f.vendorId, f.productId));
            if (!f.vendor.isEmpty()) addRow(card, "Vendor", f.vendor);
            if (!f.manufacturer.isEmpty()) addRow(card, "Maker", f.manufacturer);
            addRow(card, "USB version", f.usbVersion.isEmpty() ? "\u2014" : f.usbVersion);
            addRow(card, "Audio class", f.uacTwo ? "UAC2 (high speed)"
                    : (f.uacOne ? "UAC1 (full speed)" : "not advertised"));
            addRow(card, "Interfaces", f.interfaceCount + " total \u00b7 " + f.audioInterfaceCount
                    + " audio \u00b7 " + f.streamingInterfaces + " streaming");
            addRow(card, "Endpoints", f.endpointCount + " \u00b7 " + f.isoEndpoints + " isochronous");
            addRow(card, "Async feedback", f.hasSyncEndpoint ? "present" : "not present");
            addRow(card, "Vendor control", f.vendorSpecificControl ? "yes (claimable)" : "no");
            addRow(card, "Serial", f.serial == null || f.serial.isEmpty() ? "\u2014" : f.serial);
            addRow(card, "Permission", f.permissionGranted ? "granted" : "not granted");
            addRow(card, "Direct claim", f.directClaimed ? "held" : "off");

            card.addView(Ui.spacer(act, 8));
            LinearLayout actions = Ui.row(act);
            if (!f.permissionGranted) {
                actions.addView(Ui.button(act, "Authorize", R.style.Btn_Small, v -> {
                    UsbAudioProbe.requestPermission(act, f.device);
                    toast("Waiting for the system dialog\u2026");
                }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                actions.addView(Ui.spacer(act, 6));
            }
            actions.addView(Ui.button(act, "Claim control", R.style.Btn_Small, v -> {
                boolean ok = UsbAudioProbe.claimControlInterfaces(act, f);
                toast(ok ? "Vendor interface claimed"
                        : "Nothing to claim - the platform already owns this device's audio path");
                refresh();
            }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            actions.addView(Ui.spacer(act, 6));
            actions.addView(Ui.button(act, "Details", R.style.Btn_Small, v -> {
                Ui.prompt(act, "Descriptor report", "USB descriptor", buildReport(f),
                        true, false, value -> {
                        });
            }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Ui.addWide(card, actions);
        }
    }

    private String buildReport(UsbAudioProbe.UsbFacts f) {
        StringBuilder sb = new StringBuilder();
        sb.append("AUDIO-rec USB report\n\n");
        sb.append("Product: ").append(f.productName).append('\n');
        sb.append("Vendor id: ").append(String.format(java.util.Locale.US, "0x%04X", f.vendorId))
                .append('\n');
        sb.append("Product id: ").append(String.format(java.util.Locale.US, "0x%04X", f.productId))
                .append('\n');
        sb.append("Descriptor: ").append(f.describe()).append('\n');
        sb.append("Audio class: ").append(f.uacTwo ? "UAC2" : (f.uacOne ? "UAC1" : "vendor"))
                .append('\n');
        sb.append("Streaming interfaces: ").append(f.streamingInterfaces).append('\n');
        sb.append("Isochronous endpoints: ").append(f.isoEndpoints).append('\n');
        sb.append("Vendor-specific control interface: ")
                .append(f.vendorSpecificControl ? "yes" : "no").append('\n');
        sb.append("Direct claim: ").append(f.directClaimed ? "held" : "none").append('\n');
        if (f.device != null) {
            sb.append("Interfaces:\n");
            for (int i = 0; i < f.device.getInterfaceCount(); i++) {
                android.hardware.usb.UsbInterface itf = f.device.getInterface(i);
                if (itf == null) continue;
                sb.append("  #").append(i)
                        .append(" class=").append(itf.getInterfaceClass())
                        .append(" sub=").append(itf.getInterfaceSubclass())
                        .append(" proto=").append(itf.getInterfaceProtocol())
                        .append(" endpoints=").append(itf.getEndpointCount())
                        .append('\n');
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------- endpoints
    private void endpointsSection(final boolean input) {
        col.addView(section(input ? "INPUT ENDPOINTS" : "OUTPUT ENDPOINTS"));
        List<AudioDevice> list = input ? act.engine().inputs() : act.engine().outputs();
        AudioDevice selected = input ? act.engine().input() : act.engine().output();
        for (final AudioDevice d : list) {
            boolean isSel = selected != null && selected.key.equals(d.key);
            LinearLayout row = Ui.row(act);
            row.setBackgroundResource(isSel ? R.drawable.bg_selected : R.drawable.bg_list_row);
            row.setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 12), Ui.dp(act, 10));
            row.setClickable(true);

            LinearLayout texts = Ui.column(act);
            texts.addView(Ui.body(act, d.name));
            String spec = d.typeName();
            if (d.isUsb) spec += "  \u00b7  USB";
            if (d.sampleRates.length > 0) {
                spec += "  \u00b7  " + d.maxChannels() + " ch  \u00b7  "
                        + Fmt.khz(d.maxSampleRate()) + "  \u00b7  " + d.bitDepthLabel();
            }
            texts.addView(Ui.caption(act, spec));
            row.addView(texts, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (isSel) row.addView(Ui.pill(act, "IN USE", R.drawable.bg_pill, th.accent));

            row.setOnClickListener(v -> {
                if (input) act.engine().selectInput(d);
                else act.engine().selectOutput(d);
                refresh();
                act.refreshHeader();
                toast((input ? "Input: " : "Output: ") + d.name);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(act, 6);
            row.setLayoutParams(lp);
            col.addView(row);
        }
        col.addView(Ui.button(act, "Rescan endpoints", R.style.Btn_Small, v -> {
            act.engine().refreshDevices();
            refresh();
        }));
    }

    private void notes() {
        col.addView(section("HOW THE DIRECT PATH WORKS"));
        LinearLayout card = card();
        TextView t = Ui.caption(act, "On a class-compliant interface the kernel's USB audio driver "
                + "feeds AudioRecord directly, and AUDIO-rec opens that endpoint with the UNPROCESSED "
                + "source where the platform supports it - no AGC, no noise suppression, no echo "
                + "cancellation - which is as close to bit-perfect as Android allows without root.\n\n"
                + "Vendor-specific control interfaces (Focusrite, Zoom, RME, PreSonus and friends) "
                + "are enumerated here and can be claimed exclusively: that is the 'custom driver' "
                + "step. Audio streaming interfaces are deliberately left to the platform, because "
                + "claiming those would silence the very stream being recorded.\n\n"
                + "If an interface reports no channels but has UAC streaming endpoints, try "
                + "re-plugging it while AUDIO-rec is open: several bridges enumerate lazily.");
        t.setTextColor(th.textSecondary);
        Ui.addWide(card, t);
    }

    private void addRow(LinearLayout parent, String key, String value) {
        LinearLayout r = Ui.row(act);
        TextView k = Ui.caption(act, key);
        k.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(act, 116),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        r.addView(k);
        TextView v = Ui.body(act, value);
        v.setTextColor(th.textPrimary);
        r.addView(v, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.setPadding(0, Ui.dp(act, 3), 0, Ui.dp(act, 3));
        parent.addView(r);
    }

    @Override
    public void onResume() {
        act.engine().refreshDevices();
    }
}
