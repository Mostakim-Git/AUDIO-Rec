package com.mostakim.audiorec.audio;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Raw USB layer: what is physically plugged in, and what its descriptors say.
 *
 * Android routes USB-Audio-Class interfaces through the normal audio stack
 * automatically, but for a *recording* workstation we also need to know:
 *   • which vendor/product is attached (to auto-select presets and to label takes)
 *   • whether the unit is UAC1 or UAC2 (buffer/jitter behaviour differs a lot)
 *   • whether it also exposes a vendor-specific control interface - the reason
 *     people say "xxxx needs a custom driver" - and whether we may claim it
 *   • whether an async feedback endpoint exists (helps decide safe buffer sizes)
 *
 * We probe (never write) descriptors, and we only ever claim an interface when
 * the operator enables "direct USB claim" and the interface is not one the
 * platform audio stack is using.  Everything else is read-only enumeration.
 */
public final class UsbAudioProbe {

    public static final String ACTION_USB_PERMISSION = "com.mostakim.audiorec.USB_PERMISSION";

    // USB class codes
    public static final int CLASS_AUDIO = 0x01;
    public static final int SUBCLASS_AUDIOCONTROL = 0x01;
    public static final int SUBCLASS_AUDIOSTREAMING = 0x02;
    public static final int SUBCLASS_MIDISTREAMING = 0x03;
    public static final int CLASS_VENDOR_SPECIFIC = 0xFF;

    /** descriptor snapshot of one attached unit */
    public static class UsbFacts {
        public int vendorId, productId;
        public String productName = "";
        public String manufacturer = "";
        public String vendor = "";
        public String serial = "";
        public String usbVersion = "";
        public int usbClass, usbSubclass, usbProtocol;
        public int interfaceCount;
        public int audioInterfaceCount;
        public int streamingInterfaces;
        public int controlInterfaces;
        public int endpointCount;
        public int isoEndpoints;
        public boolean uacOne, uacTwo;
        public boolean vendorSpecificControl;
        public boolean hasSyncEndpoint;
        public boolean directClaimed;
        public boolean permissionGranted;
        public UsbDevice device;

        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.US, "VID:PID %04X:%04X", vendorId, productId));
            if (!usbVersion.isEmpty()) sb.append("  USB ").append(usbVersion);
            sb.append("  ").append(interfaceCount).append(" if");
            if (audioInterfaceCount > 0) sb.append(" (").append(audioInterfaceCount).append(" audio)");
            if (vendorSpecificControl) sb.append(" + vendor if");
            if (isoEndpoints > 0) sb.append("  ").append(isoEndpoints).append(" iso ep");
            if (hasSyncEndpoint) sb.append("  async fb");
            return sb.toString();
        }
    }

    private UsbAudioProbe() {
    }

    /** known vendors, used for labelling and preset matching */
    public static String vendorName(int vid) {
        switch (vid) {
            case 0x1235: return "Focusrite / Novation";
            case 0x1686: return "Zoom";
            case 0x2A39: return "RME";
            case 0x0763: return "M-Audio";
            case 0x194F: return "PreSonus";
            case 0x046D: return "Logitech / Blue";
            case 0x0582: return "Roland";
            case 0x0499: return "Yamaha / Steinberg";
            case 0x1397: return "Behringer";
            case 0x05FC: return "Harman / AKG";
            case 0x0D8C: return "C-Media (generic USB audio)";
            case 0x08BB: return "Texas Instruments (PCM290x codec)";
            case 0x2708: return "Jcally";
            case 0x1B1C: return "Corsair";
            case 0x152A: return "Thesycon / generic UAC2 DAC";
            case 0x262A: return "SMSL";
            case 0x20B1: return "XMOS (generic UAC2)";
            case 0x2E88: return "iBasso";
            case 0x2912: return "FiiO";
            default: return "";
        }
    }

    /**
     * Snapshot every attached USB device.  Cheap enough to call on a USB
     * attach/detach broadcast and whenever a screen resumes.
     */
    public static List<UsbFacts> list(Context ctx) {
        List<UsbFacts> out = new ArrayList<>();
        UsbManager um = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
        if (um == null) return out;
        Map<String, UsbDevice> devices;
        try {
            devices = um.getDeviceList();
        } catch (Exception e) {
            return out;
        }
        if (devices == null) return out;
        for (UsbDevice d : devices.values()) {
            out.add(inspect(um, d, ctx));
        }
        return out;
    }

    public static Map<String, UsbFacts> byName(Context ctx) {
        Map<String, UsbFacts> map = new HashMap<>();
        for (UsbFacts f : list(ctx)) map.put(f.device.getDeviceName(), f);
        return map;
    }

    /** read the descriptor: class/subclass per interface, endpoints, sync */
    public static UsbFacts inspect(UsbManager um, UsbDevice d, Context ctx) {
        UsbFacts f = new UsbFacts();
        f.device = d;
        f.vendorId = d.getVendorId();
        f.productId = d.getProductId();
        f.usbClass = d.getDeviceClass();
        f.usbSubclass = d.getDeviceSubclass();
        f.usbProtocol = d.getDeviceProtocol();
        f.interfaceCount = d.getInterfaceCount();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String v = d.getVersion();            // BCD string, e.g. "2.10" or "1.10"
            f.usbVersion = v == null ? "" : v;
            if (v != null && v.startsWith("2")) f.uacTwo = true;
        }
        f.vendor = vendorName(f.vendorId);

        for (int i = 0; i < f.interfaceCount; i++) {
            UsbInterface intf = d.getInterface(i);
            if (intf == null) continue;
            int cls = intf.getInterfaceClass();
            int sub = intf.getInterfaceSubclass();
            f.endpointCount += intf.getEndpointCount();
            for (int e = 0; e < intf.getEndpointCount(); e++) {
                android.hardware.usb.UsbEndpoint ep = intf.getEndpoint(e);
                if (ep == null) continue;
                int type = ep.getType();
                if (type == UsbConstants.USB_ENDPOINT_XFER_ISOC) {
                    f.isoEndpoints++;
                    // a second isochronous OUT endpoint on a capture interface is
                    // the async feedback path most UAC2 boxes use
                    if (ep.getDirection() == UsbConstants.USB_DIR_OUT && intf.getEndpointCount() > 1) {
                        f.hasSyncEndpoint = true;
                    }
                }
            }
            if (cls == CLASS_AUDIO) {
                f.audioInterfaceCount++;
                if (sub == SUBCLASS_AUDIOCONTROL) f.controlInterfaces++;
                if (sub == SUBCLASS_AUDIOSTREAMING) f.streamingInterfaces++;
                if (sub == SUBCLASS_AUDIOCONTROL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // bcdADC appears in the class descriptor; the interface protocol
                    // byte is 0 for UAC1 and non-zero for UAC2 on several bridges
                    if (intf.getInterfaceProtocol() >= 0x20) f.uacTwo = true;
                    else f.uacOne = true;
                } else if (sub == SUBCLASS_AUDIOSTREAMING) {
                    f.uacOne = f.uacOne || !f.uacTwo;
                }
            } else if (cls == CLASS_VENDOR_SPECIFIC
                    || (cls == CLASS_AUDIO && sub == SUBCLASS_MIDISTREAMING && false)) {
                f.vendorSpecificControl = true;
            }
        }
        if (f.audioInterfaceCount == 0 && f.usbClass == CLASS_AUDIO) {
            f.audioInterfaceCount = Math.max(1, f.interfaceCount);
            f.uacOne = !f.uacTwo;
        }

        try {
            CharSequence pn = d.getProductName();
            CharSequence mn = d.getManufacturerName();
            f.productName = pn == null ? "" : pn.toString();
            f.manufacturer = mn == null ? "" : mn.toString();
        } catch (Exception ignored) {
        }

        f.permissionGranted = um.hasPermission(d);
        if (f.permissionGranted) {
            UsbDeviceConnection conn = null;
            try {
                conn = um.openDevice(d);
                if (conn != null) f.serial = conn.getSerial();
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.close();
            }
        }
        if (f.productName.isEmpty()) {
            f.productName = f.vendor.isEmpty()
                    ? String.format(Locale.US, "USB device %04x:%04x", f.vendorId, f.productId)
                    : f.vendor + " device";
        }
        return f;
    }

    /** true when the attached unit advertises USB audio streaming interfaces */
    public static boolean looksLikeAudioInterface(UsbFacts f) {
        return f != null && (f.audioInterfaceCount > 0 || f.streamingInterfaces > 0
                || f.isoEndpoints > 0 || f.usbClass == CLASS_AUDIO);
    }

    /**
     * Try to take exclusive ownership of a vendor-specific control interface -
     * the "custom driver" step.  Audio streaming interfaces are left to the
     * platform (claiming them would kill the very stream we want to record),
     * so this only ever grabs interfaces the audio stack is not using.
     *
     * Safe to call repeatedly; failures are reported, never thrown.
     */
    public static boolean claimControlInterfaces(Context ctx, UsbFacts facts) {
        if (facts == null || facts.device == null) return false;
        UsbManager um = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
        if (um == null || !um.hasPermission(facts.device)) return false;
        UsbDeviceConnection conn = null;
        boolean any = false;
        try {
            conn = um.openDevice(facts.device);
            if (conn == null) return false;
            UsbDevice d = facts.device;
            for (int i = 0; i < d.getInterfaceCount(); i++) {
                UsbInterface intf = d.getInterface(i);
                if (intf == null) continue;
                boolean isAudioStream = intf.getInterfaceClass() == CLASS_AUDIO
                        && intf.getInterfaceSubclass() == SUBCLASS_AUDIOSTREAMING;
                if (isAudioStream) continue;               // never steal the audio path
                if (intf.getInterfaceClass() == CLASS_VENDOR_SPECIFIC) {
                    try {
                        if (conn.claimInterface(intf, true)) any = true;
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) {
                // keep the connection only if we claimed something, otherwise release
                if (!any) conn.close();
            }
        }
        facts.directClaimed = any;
        return any;
    }

    /** ask the user to grant USB access to this device (system dialog) */
    public static void requestPermission(Context ctx, UsbDevice device) {
        UsbManager um = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
        if (um == null) return;
        // Android 12+ requires a MUTABLE PendingIntent here: the system fills the
        // grant result into the intent it hands back to us, which an immutable
        // one silently blocks (the permission would read as "denied" forever).
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        flags |= Build.VERSION.SDK_INT >= 31
                ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0,
                new Intent(ACTION_USB_PERMISSION).setPackage(ctx.getPackageName()), flags);
        um.requestPermission(device, pi);
    }

    /** helper the UI can register once for attach/detach/permission events */
    public abstract static class Listener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice d = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                boolean ok = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                onPermission(d, ok);
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                onAttached((UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                onDetached((UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
            }
        }

        public void onPermission(UsbDevice device, boolean granted) {
        }

        public void onAttached(UsbDevice device) {
        }

        public void onDetached(UsbDevice device) {
        }
    }

    public static IntentFilter filter() {
        IntentFilter f = new IntentFilter();
        f.addAction(ACTION_USB_PERMISSION);
        f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        f.addAction("android.hardware.usb.action.USB_STATE");
        return f;
    }
}
