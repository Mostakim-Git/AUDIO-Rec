package com.mostakim.audiorec.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.widget.Toast;

import com.mostakim.audiorec.App;
import com.mostakim.audiorec.audio.AudioDevice;
import com.mostakim.audiorec.audio.AudioEngine;
import com.mostakim.audiorec.audio.UsbAudioProbe;

/**
 * Reacts to the interface being plugged in or pulled out.
 *
 * Detaching mid-take is the one failure mode a USB recorder *must* survive: the
 * take is closed and kept, the engine goes idle and the operator is told, rather
 * than losing the file.
 */
public class UsbStateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        App app = App.get();
        if (app == null) return;
        AudioEngine engine = app.audio();

        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            UsbDevice d = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            engine.refreshDevices();
            AudioDevice in = engine.input();
            String name = d != null && d.getProductName() != null
                    ? d.getProductName().toString() : "USB audio interface";
            Toast.makeText(context, "Interface connected: " + name
                    + (in != null && in.isUsb ? "  \u00b7  " + in.shortSpec() : ""),
                    Toast.LENGTH_SHORT).show();
            if (app.prefs().autoArmOnAttach() && !engine.isCapturing()) {
                engine.startMonitor();
            }
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            boolean wasRecording = engine.state() == AudioEngine.State.RECORDING
                    || engine.state() == AudioEngine.State.PAUSED;
            engine.refreshDevices();
            if (engine.isCapturing()) {
                engine.stopCapture(true);
                Toast.makeText(context, wasRecording
                        ? "Interface removed - take saved and capture stopped"
                        : "Interface removed", Toast.LENGTH_LONG).show();
            }
        } else if ("android.hardware.usb.action.USB_STATE".equals(action)) {
            engine.refreshDevices();
        } else if (UsbAudioProbe.ACTION_USB_PERMISSION.equals(action)) {
            engine.refreshDevices();
        }
    }
}
