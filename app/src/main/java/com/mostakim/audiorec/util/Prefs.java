package com.mostakim.audiorec.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/**
 * AUDIO-rec settings: one small SharedPreferences file.
 *
 * Recording configuration lives here (rather than only in the UI) so the
 * foreground service, the widgets and the exporter all agree on the same
 * numbers even if the activity is destroyed mid-take.
 */
public final class Prefs {

    private static final String FILE = "audiorec";

    // capture
    public static final String K_SAMPLE_RATE = "sample_rate";
    public static final String K_BIT_DEPTH = "bit_depth";
    public static final String K_CHANNELS = "channels";
    public static final String K_BUFFER = "buffer_frames";
    public static final String K_FORMAT = "container";
    public static final String K_INPUT_ID = "input_device_id";
    public static final String K_OUTPUT_ID = "output_device_id";
    public static final String K_GAIN = "input_gain";
    public static final String K_MONITOR = "monitor_enabled";
    public static final String K_MONITOR_GAIN = "monitor_gain";
    public static final String K_MUTE = "output_mute";
    public static final String K_REC_DIR = "record_dir";
    public static final String K_DITHER = "dither";
    public static final String K_KEEP_SCREEN = "keep_screen_on";
    public static final String K_SPLIT_MONO = "split_mono_inputs";
    public static final String K_PEAK_WARN = "peak_warn_db";
    public static final String K_LAST_SESSION = "last_session_id";
    public static final String K_LAST_PRESET = "last_preset_id";
    public static final String K_DIRECT_USB = "direct_usb_claim";
    public static final String K_AUTO_ARM = "auto_arm_on_attach";

    private final SharedPreferences p;

    public Prefs(Context c) {
        p = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public SharedPreferences raw() {
        return p;
    }

    // ------------------------------------------------------------- capture --
    public int sampleRate() {
        return p.getInt(K_SAMPLE_RATE, 48000);
    }

    public void setSampleRate(int v) {
        p.edit().putInt(K_SAMPLE_RATE, v).apply();
    }

    public int bitDepth() {
        return p.getInt(K_BIT_DEPTH, 24);
    }

    public void setBitDepth(int v) {
        p.edit().putInt(K_BIT_DEPTH, v).apply();
    }

    public int channels() {
        return p.getInt(K_CHANNELS, 2);
    }

    public void setChannels(int v) {
        p.edit().putInt(K_CHANNELS, v).apply();
    }

    public int bufferFrames() {
        return p.getInt(K_BUFFER, 2048);
    }

    public void setBufferFrames(int v) {
        p.edit().putInt(K_BUFFER, v).apply();
    }

    public String container() {
        return p.getString(K_FORMAT, Formats.WAV);
    }

    public void setContainer(String ext) {
        p.edit().putString(K_FORMAT, ext).apply();
    }

    public String inputDeviceId() {
        return p.getString(K_INPUT_ID, null);
    }

    public void setInputDeviceId(String id) {
        p.edit().putString(K_INPUT_ID, id).apply();
    }

    public String outputDeviceId() {
        return p.getString(K_OUTPUT_ID, null);
    }

    public void setOutputDeviceId(String id) {
        p.edit().putString(K_OUTPUT_ID, id).apply();
    }

    public float gainDb() {
        return p.getFloat(K_GAIN, 0f);
    }

    public void setGainDb(float v) {
        p.edit().putFloat(K_GAIN, v).apply();
    }

    public boolean monitor() {
        return p.getBoolean(K_MONITOR, false);
    }

    public void setMonitor(boolean v) {
        p.edit().putBoolean(K_MONITOR, v).apply();
    }

    public float monitorGainDb() {
        return p.getFloat(K_MONITOR_GAIN, -6f);
    }

    public void setMonitorGainDb(float v) {
        p.edit().putFloat(K_MONITOR_GAIN, v).apply();
    }

    public boolean mute() {
        return p.getBoolean(K_MUTE, false);
    }

    public void setMute(boolean v) {
        p.edit().putBoolean(K_MUTE, v).apply();
    }

    public boolean dither() {
        return p.getBoolean(K_DITHER, true);
    }

    public void setDither(boolean v) {
        p.edit().putBoolean(K_DITHER, v).apply();
    }

    public boolean keepScreenOn() {
        return p.getBoolean(K_KEEP_SCREEN, true);
    }

    public void setKeepScreenOn(boolean v) {
        p.edit().putBoolean(K_KEEP_SCREEN, v).apply();
    }

    public boolean splitMonoInputs() {
        return p.getBoolean(K_SPLIT_MONO, false);
    }

    public void setSplitMonoInputs(boolean v) {
        p.edit().putBoolean(K_SPLIT_MONO, v).apply();
    }

    public float peakWarnDb() {
        return p.getFloat(K_PEAK_WARN, -1f);
    }

    public void setPeakWarnDb(float v) {
        p.edit().putFloat(K_PEAK_WARN, v).apply();
    }

    public boolean directUsbClaim() {
        return p.getBoolean(K_DIRECT_USB, true);
    }

    public void setDirectUsbClaim(boolean v) {
        p.edit().putBoolean(K_DIRECT_USB, v).apply();
    }

    /** per-channel trim in dB, CSV, 8 slots */
    public float[] channelTrims() {
        float[] out = new float[8];
        String csv = p.getString("channel_trims", "");
        if (!csv.isEmpty()) {
            String[] parts = csv.split(",");
            for (int i = 0; i < Math.min(parts.length, out.length); i++) {
                try {
                    out[i] = Float.parseFloat(parts[i]);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }

    public void setChannelTrims(float[] trims) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < trims.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(trims[i]);
        }
        p.edit().putString("channel_trims", sb.toString()).apply();
    }

    public boolean autoArmOnAttach() {
        return p.getBoolean(K_AUTO_ARM, false);
    }

    public void setAutoArmOnAttach(boolean v) {
        p.edit().putBoolean(K_AUTO_ARM, v).apply();
    }

    // -------------------------------------------------------------- paths ---
    public File recordDir() {
        String s = p.getString(K_REC_DIR, null);
        return s == null ? null : new File(s);
    }

    public void setRecordDir(File f) {
        p.edit().putString(K_REC_DIR, f.getAbsolutePath()).apply();
    }

    public long lastSessionId() {
        return p.getLong(K_LAST_SESSION, -1L);
    }

    public void setLastSessionId(long id) {
        p.edit().putLong(K_LAST_SESSION, id).apply();
    }

    public long lastPresetId() {
        return p.getLong(K_LAST_PRESET, -1L);
    }

    public void setLastPresetId(long id) {
        p.edit().putLong(K_LAST_PRESET, id).apply();
    }
}
