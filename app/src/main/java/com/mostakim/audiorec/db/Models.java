package com.mostakim.audiorec.db;

import android.content.ContentValues;
import android.database.Cursor;

import com.mostakim.audiorec.util.Fmt;
import com.mostakim.audiorec.util.Formats;

import java.io.Serializable;

/** Plain data objects + cursor/ContentValues mapping for the four resources. */
public final class Models {

    private Models() {
    }

    // ======================================================== SESSION =======
    public static class Session implements Serializable {
        public long id = -1;
        public String name = "";
        public String artist = "";
        public String venue = "";
        public String notes = "";
        public String status = "open";           // open | archived
        public int sampleRate = 48000;
        public int bitDepth = 24;
        public int channels = 2;
        public String container = Formats.WAV;
        public int trackCount = 0;
        public long totalBytes = 0;
        public long createdAt = 0;
        public long updatedAt = 0;

        public String formatSummary() {
            return Fmt.khz(sampleRate) + "  \u00b7  " + bitDepth + "-bit  \u00b7  "
                    + channels + " ch  \u00b7  " + container.toUpperCase();
        }

        public ContentValues toValues() {
            ContentValues v = new ContentValues();
            v.put("name", name);
            v.put("artist", artist);
            v.put("venue", venue);
            v.put("notes", notes);
            v.put("status", status);
            v.put("sample_rate", sampleRate);
            v.put("bit_depth", bitDepth);
            v.put("channels", channels);
            v.put("container", container);
            v.put("updated_at", System.currentTimeMillis());
            return v;
        }

        public static Session from(Cursor c) {
            Session s = new Session();
            s.id = c.getLong(c.getColumnIndexOrThrow("_id"));
            s.name = str(c, "name");
            s.artist = str(c, "artist");
            s.venue = str(c, "venue");
            s.notes = str(c, "notes");
            s.status = str(c, "status");
            s.sampleRate = num(c, "sample_rate", 48000);
            s.bitDepth = num(c, "bit_depth", 24);
            s.channels = num(c, "channels", 2);
            s.container = str(c, "container");
            s.trackCount = num(c, "track_count", 0);
            s.totalBytes = lon(c, "total_bytes", 0);
            s.createdAt = lon(c, "created_at", 0);
            s.updatedAt = lon(c, "updated_at", 0);
            return s;
        }
    }

    // ========================================================== TRACK =======
    public static class Track implements Serializable {
        public long id = -1;
        public long sessionId = -1;
        public String title = "";
        public String filePath = "";
        public String container = Formats.WAV;
        public int sampleRate = 48000;
        public int bitDepth = 24;
        public int channels = 2;
        public long durationMs = 0;
        public long sizeBytes = 0;
        public float peakDb = -120f;
        public float rmsDb = -120f;
        public String deviceName = "";
        public String channelMap = "";
        public int takeNo = 1;
        public boolean starred = false;
        public String notes = "";
        public long createdAt = 0;
        public long updatedAt = 0;

        /** transient: file is known to be gone (deleted outside the app) */
        public transient boolean missing = false;
        public transient boolean playing = false;

        public String formatSummary() {
            return Fmt.khz(sampleRate) + " / " + bitDepth + "-bit / "
                    + channels + " ch / " + container.toUpperCase();
        }

        public ContentValues toValues() {
            ContentValues v = new ContentValues();
            v.put("session_id", sessionId);
            v.put("title", title);
            v.put("file_path", filePath);
            v.put("container", container);
            v.put("sample_rate", sampleRate);
            v.put("bit_depth", bitDepth);
            v.put("channels", channels);
            v.put("duration_ms", durationMs);
            v.put("size_bytes", sizeBytes);
            v.put("peak_db", peakDb);
            v.put("rms_db", rmsDb);
            v.put("device_name", deviceName);
            v.put("channel_map", channelMap);
            v.put("take_no", takeNo);
            v.put("starred", starred ? 1 : 0);
            v.put("notes", notes);
            v.put("updated_at", System.currentTimeMillis());
            return v;
        }

        public static Track from(Cursor c) {
            Track t = new Track();
            t.id = c.getLong(c.getColumnIndexOrThrow("_id"));
            t.sessionId = c.getLong(c.getColumnIndexOrThrow("session_id"));
            t.title = str(c, "title");
            t.filePath = str(c, "file_path");
            t.container = str(c, "container");
            t.sampleRate = num(c, "sample_rate", 48000);
            t.bitDepth = num(c, "bit_depth", 24);
            t.channels = num(c, "channels", 2);
            t.durationMs = lon(c, "duration_ms", 0);
            t.sizeBytes = lon(c, "size_bytes", 0);
            t.peakDb = flt(c, "peak_db", -120f);
            t.rmsDb = flt(c, "rms_db", -120f);
            t.deviceName = str(c, "device_name");
            t.channelMap = str(c, "channel_map");
            t.takeNo = num(c, "take_no", 1);
            t.starred = num(c, "starred", 0) == 1;
            t.notes = str(c, "notes");
            t.createdAt = lon(c, "created_at", 0);
            t.updatedAt = lon(c, "updated_at", 0);
            return t;
        }
    }

    // ========================================================= PRESET =======
    public static class Preset implements Serializable {
        public long id = -1;
        public String name = "";
        public String deviceName = "";
        public int vendorId = 0;
        public int productId = 0;
        public int sampleRate = 48000;
        public int bitDepth = 24;
        public int channels = 2;
        public int bufferFrames = 2048;
        public String container = Formats.WAV;
        public float gainDb = 0f;
        public boolean monitor = false;
        public float monitorGainDb = -6f;
        public String notes = "";
        public boolean builtin = false;
        public int useCount = 0;
        public long createdAt = 0;
        public long updatedAt = 0;

        public String summary() {
            return Fmt.khz(sampleRate) + "  \u00b7  " + bitDepth + "-bit  \u00b7  "
                    + channels + " ch  \u00b7  " + bufferFrames + " fr  \u00b7  "
                    + container.toUpperCase();
        }

        /** true when this preset was authored for the given USB interface */
        public boolean matches(int vid, int pid) {
            if (vendorId == 0 && productId == 0) return false;
            if (vid != 0 && vendorId != 0 && vid != vendorId) return false;
            if (pid != 0 && productId != 0 && pid != productId) return false;
            return true;
        }

        public ContentValues toValues() {
            ContentValues v = new ContentValues();
            v.put("name", name);
            v.put("device_name", deviceName);
            v.put("vendor_id", vendorId);
            v.put("product_id", productId);
            v.put("sample_rate", sampleRate);
            v.put("bit_depth", bitDepth);
            v.put("channels", channels);
            v.put("buffer_frames", bufferFrames);
            v.put("container", container);
            v.put("gain_db", gainDb);
            v.put("monitor", monitor ? 1 : 0);
            v.put("monitor_gain_db", monitorGainDb);
            v.put("notes", notes);
            v.put("use_count", useCount);
            v.put("updated_at", System.currentTimeMillis());
            return v;
        }

        public static Preset from(Cursor c) {
            Preset p = new Preset();
            p.id = c.getLong(c.getColumnIndexOrThrow("_id"));
            p.name = str(c, "name");
            p.deviceName = str(c, "device_name");
            p.vendorId = num(c, "vendor_id", 0);
            p.productId = num(c, "product_id", 0);
            p.sampleRate = num(c, "sample_rate", 48000);
            p.bitDepth = num(c, "bit_depth", 24);
            p.channels = num(c, "channels", 2);
            p.bufferFrames = num(c, "buffer_frames", 2048);
            p.container = str(c, "container");
            p.gainDb = flt(c, "gain_db", 0f);
            p.monitor = num(c, "monitor", 0) == 1;
            p.monitorGainDb = flt(c, "monitor_gain_db", -6f);
            p.notes = str(c, "notes");
            p.builtin = num(c, "builtin", 0) == 1;
            p.useCount = num(c, "use_count", 0);
            p.createdAt = lon(c, "created_at", 0);
            p.updatedAt = lon(c, "updated_at", 0);
            return p;
        }
    }

    // ========================================================= EXPORT =======
    public static class Export implements Serializable {
        public long id = -1;
        public long trackId = -1;
        public String trackTitle = "";
        public String name = "";
        public String filePath = "";
        public String container = Formats.WAV;
        public int sampleRate = 48000;
        public int bitDepth = 24;
        public int channels = 2;
        public long sizeBytes = 0;
        public long durationMs = 0;
        public String status = "ready";       // ready | stale | missing
        public String note = "";
        public long createdAt = 0;
        public long updatedAt = 0;

        public String summary() {
            return Fmt.khz(sampleRate) + " / " + bitDepth + "-bit / "
                    + channels + " ch / " + container.toUpperCase();
        }

        public ContentValues toValues() {
            ContentValues v = new ContentValues();
            v.put("track_id", trackId);
            v.put("track_title", trackTitle);
            v.put("name", name);
            v.put("file_path", filePath);
            v.put("container", container);
            v.put("sample_rate", sampleRate);
            v.put("bit_depth", bitDepth);
            v.put("channels", channels);
            v.put("size_bytes", sizeBytes);
            v.put("duration_ms", durationMs);
            v.put("status", status);
            v.put("note", note);
            v.put("updated_at", System.currentTimeMillis());
            return v;
        }

        public static Export from(Cursor c) {
            Export e = new Export();
            e.id = c.getLong(c.getColumnIndexOrThrow("_id"));
            e.trackId = c.getLong(c.getColumnIndexOrThrow("track_id"));
            e.trackTitle = str(c, "track_title");
            e.name = str(c, "name");
            e.filePath = str(c, "file_path");
            e.container = str(c, "container");
            e.sampleRate = num(c, "sample_rate", 48000);
            e.bitDepth = num(c, "bit_depth", 24);
            e.channels = num(c, "channels", 2);
            e.sizeBytes = lon(c, "size_bytes", 0);
            e.durationMs = lon(c, "duration_ms", 0);
            e.status = str(c, "status");
            e.note = str(c, "note");
            e.createdAt = lon(c, "created_at", 0);
            e.updatedAt = lon(c, "updated_at", 0);
            return e;
        }
    }

    // ------------------------------------------------------------ helpers ---
    static String str(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        return i < 0 ? "" : (c.isNull(i) ? "" : c.getString(i));
    }

    static int num(Cursor c, String col, int def) {
        int i = c.getColumnIndex(col);
        return i < 0 || c.isNull(i) ? def : c.getInt(i);
    }

    static long lon(Cursor c, String col, long def) {
        int i = c.getColumnIndex(col);
        return i < 0 || c.isNull(i) ? def : c.getLong(i);
    }

    static float flt(Cursor c, String col, float def) {
        int i = c.getColumnIndex(col);
        return i < 0 || c.isNull(i) ? def : c.getFloat(i);
    }
}
