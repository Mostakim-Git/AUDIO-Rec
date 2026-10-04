package com.mostakim.audiorec.audio;

import android.app.PendingIntent;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

/** Harness USB probe: reports facts, never touches hardware. */
public final class UsbAudioProbe {

    private UsbAudioProbe() { }

    public static final String ACTION_USB_PERMISSION = "com.mostakim.audiorec.USB_PERMISSION";
    public static final int CLASS_AUDIO = 0x01;
    public static final int SUBCLASS_AUDIOCONTROL = 0x01;
    public static final int SUBCLASS_AUDIOSTREAMING = 0x02;
    public static final int SUBCLASS_MIDISTREAMING = 0x03;
    public static final int CLASS_VENDOR_SPECIFIC = 0xFF;

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
            sb.append(productName == null ? "" : productName);
            if (manufacturer != null && manufacturer.length() > 0) {
                sb.append(" \u00b7 ").append(manufacturer);
            }
            sb.append(" \u00b7 ").append(uacTwo ? "UAC2" : uacOne ? "UAC1" : "vendor");
            sb.append(" ").append(String.format("0x%04x:0x%04x", vendorId, productId));
            return sb.toString();
        }
    }

    public static boolean looksLikeAudioInterface(UsbFacts f) {
        return f != null && (f.uacOne || f.uacTwo || f.audioInterfaceCount > 0);
    }

    public static String vendorName(int vid) {
        switch (vid) {
            case 0x0763: return "M-Audio";
            case 0x1235: return "Focusrite";
            case 0x194F: return "PreSonus";
            case 0x1686: return "Zoom";
            case 0x2A39: return "RME";
            case 0x0D8C: return "C-Media";
            default: return "";
        }
    }

    public static java.util.List<UsbFacts> list(Context ctx) {
        return new java.util.ArrayList<>();
    }

    public static java.util.Map<String, UsbFacts> byName(Context ctx) {
        return new java.util.HashMap<>();
    }

    public static UsbFacts inspect(UsbManager um, UsbDevice d, Context c) {
        return inspect(c, d);
    }

    public static UsbFacts inspect(Context c, UsbDevice d) {
        UsbFacts f = new UsbFacts();
        if (d != null) {
            f.vendorId = d.getVendorId();
            f.productId = d.getProductId();
            f.productName = d.getProductName();
            f.manufacturer = d.getManufacturerName();
        }
        f.uacOne = true;
        f.audioInterfaceCount = 3;
        f.interfaceCount = 4;
        f.device = d;
        return f;
    }

    public static void requestPermission(Context c, UsbDevice d) { }

    public static PendingIntent permissionIntent(Context c) { return null; }

    public static boolean claimControlInterfaces(Context c, UsbFacts facts) { return true; }

    public static android.content.IntentFilter filter() {
        return new android.content.IntentFilter(ACTION_USB_PERMISSION);
    }

    public static UsbManager manager(Context c) {
        return (UsbManager) c.getSystemService(Context.USB_SERVICE);
    }
}
