package com.mostakim.audiorec.util;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

/**
 * First-run content.  Presets ship as *starting points* for the interfaces the
 * app is commonly used with; every value is editable and the built-ins can be
 * deleted like any other row.
 */
public final class Seed {

    private Seed() {
    }

    private static class P {
        String name, device, notes;
        int vid, pid, rate, depth, ch, buf;
        String container;
        float gain, mon;
        boolean monitor;

        P(String n, String d, int vid, int pid, int rate, int depth, int ch, int buf,
          String container, float gain, boolean monitor, float mon, String notes) {
            this.name = n;
            this.device = d;
            this.vid = vid;
            this.pid = pid;
            this.rate = rate;
            this.depth = depth;
            this.ch = ch;
            this.buf = buf;
            this.container = container;
            this.gain = gain;
            this.monitor = monitor;
            this.mon = mon;
            this.notes = notes;
        }
    }

    // Vendor ids from USB-IF; product ids intentionally 0 where a vendor ships
    // many revisions under one name (the preset still matches by name).
    private static final P[] PRESETS = {
            new P("Focusrite Scarlett 2i2 \u00b7 24/96",
                    "Focusrite Scarlett 2i2", 0x1235, 0x8016, 96000, 24, 2, 2048, Formats.WAV,
                    0f, false, -6f, "Class-compliant UAC2. Direct Monitor knob sets latency-free foldback."),
            new P("Focusrite Scarlett Solo \u00b7 24/192",
                    "Focusrite Scarlett Solo", 0x1235, 0x8004, 192000, 24, 2, 4096, Formats.FLAC,
                    0f, false, -6f, "Use FLAC at 192 kHz for long acoustic takes."),
            new P("Zoom H2n \u00b7 USB mic 16/44.1",
                    "Zoom H2n", 0x1686, 0x0045, 44100, 16, 2, 1024, Formats.WAV,
                    0f, false, -9f, "Handy recorder in USB-audio mode; lowest buffer for tight monitoring."),
            new P("Zoom H4 \u00b7 24/48",
                    "Zoom H4", 0x1686, 0x002F, 48000, 24, 2, 2048, Formats.WAV,
                    0f, false, -6f, "Classic field recorder."),
            new P("RME Babyface \u00b7 32/192",
                    "RME Babyface", 0x2A39, 0x3F80, 192000, 32, 2, 1024, Formats.WAV,
                    0f, true, -8f, "TotalMix: set the phone out to the monitor path before recording."),
            new P("M-Audio Fast Track Pro \u00b7 24/48",
                    "M-Audio Fast Track Pro", 0x0763, 0x2012, 48000, 24, 2, 4096, Formats.WAV,
                    0f, false, -6f, "UAC1 unit: buffers below 2048 frames can glitch on some hosts."),
            new P("Blue Yeti Pro \u00b7 24/48 (multichannel)",
                    "Blue Yeti Pro", 0x1235, 0x8010, 48000, 24, 4, 4096, Formats.WAV,
                    0f, false, -9f, "Stereo pattern + raw capsules: routes 4 channels."),
            new P("PreSonus 22VSL \u00b7 24/96",
                    "PreSonus 22VSL", 0x194F, 0x0101, 96000, 24, 2, 2048, Formats.WAV,
                    0f, false, -6f, "Set the mixer to 'Main Out' so the DAW path is USB."),
            new P("HiFi DAC (UAC2) \u00b7 32/384 playback",
                    "USB DAC", 0, 0, 384000, 32, 2, 8192, Formats.WAV,
                    0f, false, -3f, "Playback-only DAC: capture stays on the internal mic unless a USB input is attached."),
            new P("Field stereo pair \u00b7 24/96 split mono",
                    "Any UAC2 interface", 0, 0, 96000, 24, 2, 8192, Formats.FLAC,
                    6f, true, -12f, "Gain +6 dB staged for quiet condensers; monitoring on."),
    };

    public static void install(SQLiteDatabase db, String presetsTable, String sessionsTable) {
        installPresets(db, presetsTable);
        installFirstSession(db, sessionsTable);
    }

    /** the shipped starting points; safe to call again, rows are appended */
    public static int installPresets(SQLiteDatabase db, String presetsTable) {
        long now = System.currentTimeMillis();
        int n = 0;
        for (P p : PRESETS) {
            ContentValues v = new ContentValues();
            v.put("name", p.name);
            v.put("device_name", p.device);
            v.put("vendor_id", p.vid);
            v.put("product_id", p.pid);
            v.put("sample_rate", p.rate);
            v.put("bit_depth", p.depth);
            v.put("channels", p.ch);
            v.put("buffer_frames", p.buf);
            v.put("container", p.container);
            v.put("gain_db", p.gain);
            v.put("monitor", p.monitor ? 1 : 0);
            v.put("monitor_gain_db", p.mon);
            v.put("notes", p.notes);
            v.put("builtin", 1);
            v.put("use_count", 0);
            v.put("created_at", now);
            v.put("updated_at", now);
            db.insert(presetsTable, null, v);
            n++;
        }
        return n;
    }

    private static void installFirstSession(SQLiteDatabase db, String sessionsTable) {
        long now = System.currentTimeMillis();
        ContentValues s = new ContentValues();
        s.put("name", "First Session");
        s.put("artist", "Mostakim Billah");
        s.put("venue", "");
        s.put("notes", "Default session created on install. Rename it or create your own "
                + "from Sessions \u2192 New. Everything stays on this device.");
        s.put("status", "open");
        s.put("sample_rate", 48000);
        s.put("bit_depth", 24);
        s.put("channels", 2);
        s.put("container", Formats.WAV);
        s.put("track_count", 0);
        s.put("total_bytes", 0);
        s.put("created_at", now);
        s.put("updated_at", now);
        db.insert(sessionsTable, null, s);
    }
}
