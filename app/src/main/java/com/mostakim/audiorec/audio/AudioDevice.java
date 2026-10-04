package com.mostakim.audiorec.audio;

import android.media.AudioDeviceInfo;
import android.os.Build;
import android.media.AudioFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One selectable endpoint (input or output), merging what Android reports about
 * its routing with what we can see of the actual USB descriptor.
 *
 * Android exposes a USB interface as an ordinary AudioDeviceInfo; the USB
 * manager tells us *which* unit is behind it, whether it advertises a
 * USB-Audio-Class interface, and whether the vendor overloaded the descriptor
 * with a proprietary control interface (Focusrite/Zoom/RME do this even on
 * class-compliant boxes).
 */
public class AudioDevice {

    public int id = -1;                     // AudioDeviceInfo id, -1 for "system default"
    public String key = "";                 // stable id used in prefs
    public String name = "System default";
    public String productName = "";
    public String vendor = "";
    public String address = "";
    public int type = 0;                    // AudioDeviceInfo type constant
    public boolean isInput = true;
    public boolean isUsb = false;
    public boolean isDefault = false;

    // USB descriptor facts (null/false for the built-in endpoints)
    public int vendorId = 0;
    public int productId = 0;
    public int usbClass = 0, usbSubclass = 0, usbProtocol = 0;
    public int interfaceCount = 0;
    public int audioInterfaceCount = 0;
    public boolean uacOne = false;
    public boolean uacTwo = false;
    public boolean vendorSpecificControl = false;
    public boolean hasSyncEndpoint = false;
    public boolean directClaimed = false;
    public String serial = "";
    public String usbVersion = "";

    // audio capabilities
    public int[] sampleRates = new int[0];
    public int[] channelCounts = new int[0];
    public boolean supports16 = false, supports24 = false, supports32 = false, supportsFloat = false;

    public int maxSampleRate() {
        int max = 0;
        for (int r : sampleRates) if (r > max) max = r;
        return max;
    }

    public int maxChannels() {
        int max = 0;
        for (int c : channelCounts) if (c > max) max = c;
        return max;
    }

    /** the deepest bit depth this endpoint can actually carry */
    public int maxBitDepth() {
        if (supports32) return 32;
        if (supports24) return 24;
        return 16;
    }

    public String bitDepthLabel() {
        List<String> depths = new ArrayList<>();
        if (supports16) depths.add("16");
        if (supports24) depths.add("24");
        if (supports32) depths.add("32");
        if (depths.isEmpty()) return "16";
        return join(depths, "/") + "-bit";
    }

    public String shortSpec() {
        if (sampleRates.length == 0) return name;
        return maxChannels() + " ch \u00b7 " + (maxSampleRate() / 1000) + " kHz \u00b7 " + bitDepthLabel();
    }

    public String usbSpec() {
        if (!isUsb) return "built-in";
        String cls = uacTwo ? "USB Audio Class 2" : (uacOne ? "USB Audio Class 1" : "USB Audio (vendor)");
        return cls + (vendorSpecificControl ? " + vendor control" : "");
    }

    public String id() {
        return String.format(java.util.Locale.US, "%04x:%04x", vendorId, productId);
    }

    public static String join(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    public String typeName() {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC: return "Built-in microphone";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "Built-in speaker";
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE: return "Earpiece";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "Wired headset";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return "Headphones";
            case AudioDeviceInfo.TYPE_USB_DEVICE: return "USB audio device";
            case AudioDeviceInfo.TYPE_USB_HEADSET: return "USB headset";
            case AudioDeviceInfo.TYPE_USB_ACCESSORY: return "USB accessory";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "Bluetooth SCO";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "Bluetooth A2DP";
            case AudioDeviceInfo.TYPE_HDMI: return "HDMI";
            default: return "Audio endpoint";
        }
    }

    /** build from a platform descriptor, merging USB facts when available */
    public static AudioDevice from(AudioDeviceInfo info, UsbAudioProbe.UsbFacts usb) {
        AudioDevice d = new AudioDevice();
        d.id = info.getId();
        d.type = info.getType();
        CharSequence pn = info.getProductName();
        d.productName = pn == null ? "" : pn.toString();
        d.name = d.productName.isEmpty() ? d.typeName() : d.productName;
        CharSequence ad = info.getAddress();
        d.address = ad == null ? "" : ad.toString();
        d.isInput = info.isSource();
        d.isUsb = d.type == AudioDeviceInfo.TYPE_USB_DEVICE
                || d.type == AudioDeviceInfo.TYPE_USB_HEADSET
                || d.type == AudioDeviceInfo.TYPE_USB_ACCESSORY;
        d.isDefault = info.isSink() && d.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;

        int[] rates = info.getSampleRates();
        d.sampleRates = rates == null ? new int[0] : rates;
        int[] chans = info.getChannelCounts();
        d.channelCounts = chans == null ? new int[0] : chans;

        int[] enc = info.getEncodings();
        if (enc != null) {
            for (int e : enc) {
                if (e == AudioFormat.ENCODING_PCM_16BIT) d.supports16 = true;
                else if (e == AudioFormat.ENCODING_PCM_24BIT_PACKED) d.supports24 = true;
                else if (e == AudioFormat.ENCODING_PCM_32BIT) d.supports32 = true;
                else if (e == AudioFormat.ENCODING_PCM_FLOAT) {
                    d.supportsFloat = true;
                    d.supports32 = true;   // float path lands in 32-bit files
                }
            }
        }
        if (Build.VERSION.SDK_INT < 31) {
            // ENCODING_PCM_24BIT_PACKED / ENCODING_PCM_32BIT arrived with API 31;
            // letting them through on Android 10/11 would make AudioRecord throw
            d.supports24 = false;
            d.supports32 = false;
        }
        if (!d.supports16 && !d.supports24 && !d.supports32) d.supports16 = true;
        if (d.sampleRates.length == 0) d.sampleRates = new int[]{48000};

        if (usb != null) {
            d.vendorId = usb.vendorId;
            d.productId = usb.productId;
            d.vendor = usb.vendor;
            if (!usb.productName.isEmpty()) d.productName = usb.productName;
            d.serial = usb.serial;
            d.usbClass = usb.usbClass;
            d.usbSubclass = usb.usbSubclass;
            d.usbProtocol = usb.usbProtocol;
            d.interfaceCount = usb.interfaceCount;
            d.audioInterfaceCount = usb.audioInterfaceCount;
            d.uacOne = usb.uacOne;
            d.uacTwo = usb.uacTwo;
            d.vendorSpecificControl = usb.vendorSpecificControl;
            d.hasSyncEndpoint = usb.hasSyncEndpoint;
            d.directClaimed = usb.directClaimed;
            d.usbVersion = usb.usbVersion;
            // a vendor-specific audio box often hides extra channels from the
            // generic descriptor; trust the interface count when it is larger
            if (d.isUsb && d.audioInterfaceCount > 1 && d.maxChannels() < 2) {
                d.channelCounts = new int[]{2};
            }
            Arrays.sort(d.sampleRates);
        }
        d.key = d.isUsb && d.vendorId != 0
                ? String.format(java.util.Locale.US, "usb-%04x-%04x-%s", d.vendorId, d.productId,
                        d.isInput ? "in" : "out")
                : "sys-" + d.id + (d.isInput ? "-in" : "-out");
        return d;
    }

    public static AudioDevice systemDefault(boolean input) {
        AudioDevice d = new AudioDevice();
        d.id = -1;
        d.name = input ? "System default input" : "System default output";
        d.isInput = input;
        d.isDefault = true;
        d.key = input ? "default-in" : "default-out";
        d.sampleRates = new int[]{44100, 48000, 96000, 192000};
        d.channelCounts = new int[]{1, 2};
        d.supports16 = true;
        d.supports24 = Build.VERSION.SDK_INT >= 31;
        d.supports32 = Build.VERSION.SDK_INT >= 31;
        d.supportsFloat = true;
        return d;
    }

    @Override
    public String toString() {
        return name;
    }
}
