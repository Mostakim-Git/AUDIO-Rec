package com.mostakim.audiorec.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.mostakim.audiorec.db.Models.Export;
import com.mostakim.audiorec.db.Models.Preset;
import com.mostakim.audiorec.db.Models.Session;
import com.mostakim.audiorec.db.Models.Track;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import com.mostakim.audiorec.util.Seed;

/**
 * All CRUD for the four core resources.
 *
 * Writes go straight to SQLite (WAL) - the app is single-process, and every
 * screen re-reads on resume, so there is no cache to invalidate.
 */
public class Store {

    private final Context mContext;
    private final Db mDb;

    public Store(Context c, Db db) {
        mContext = c.getApplicationContext();
        mDb = db;
    }

    private SQLiteDatabase r() {
        return mDb.getReadableDatabase();
    }

    private SQLiteDatabase w() {
        return mDb.getWritableDatabase();
    }

    /** re-add the presets that ship with the app (keeps user rows) */
    public int restoreBuiltinPresets() {
        return Seed.installPresets(w(), Db.T_PRESETS);
    }

    /** downgrade-safe: counts rows per table for the diagnostics block */
    public int[] tableCounts() {
        return new int[]{sessionCount(), trackCount(), presetCount(), exportCount()};
    }

    // ============================================================ SESSIONS ==
    public List<Session> sessions(String orderBy) {
        List<Session> out = new ArrayList<>();
        Cursor c = r().query(Db.T_SESSIONS, null, null, null, null, null,
                orderBy == null ? "updated_at DESC" : orderBy);
        try {
            while (c.moveToNext()) out.add(Session.from(c));
        } finally {
            c.close();
        }
        return out;
    }

    public Session session(long id) {
        Cursor c = r().query(Db.T_SESSIONS, null, "_id=?", new String[]{String.valueOf(id)},
                null, null, null);
        try {
            return c.moveToFirst() ? Session.from(c) : null;
        } finally {
            c.close();
        }
    }

    public long insert(Session s) {
        long now = System.currentTimeMillis();
        s.createdAt = now;
        s.updatedAt = now;
        ContentValues v = s.toValues();
        v.put("created_at", now);
        return w().insert(Db.T_SESSIONS, null, v);
    }

    public int update(Session s) {
        return w().update(Db.T_SESSIONS, s.toValues(), "_id=?", new String[]{String.valueOf(s.id)});
    }

    /** deletes the session row and every track it owns (files are caller's choice) */
    public int delete(long id, boolean deleteFiles) {
        if (deleteFiles) {
            for (Track t : tracks(id)) deleteFileQuietly(t.filePath);
        }
        w().delete(Db.T_TRACKS, "session_id=?", new String[]{String.valueOf(id)});
        w().delete(Db.T_EXPORTS, "track_id IN (SELECT _id FROM " + Db.T_TRACKS + " WHERE session_id=?)",
                new String[]{String.valueOf(id)});
        return w().delete(Db.T_SESSIONS, "_id=?", new String[]{String.valueOf(id)});
    }

    /** refresh the denormalised counters shown on the session cards */
    public void refreshSessionStats(long sessionId) {
        long bytes = 0;
        int count = 0;
        Cursor c = r().rawQuery("SELECT COUNT(*), IFNULL(SUM(size_bytes),0) FROM " + Db.T_TRACKS
                + " WHERE session_id=?", new String[]{String.valueOf(sessionId)});
        try {
            if (c.moveToFirst()) {
                count = c.getInt(0);
                bytes = c.getLong(1);
            }
        } finally {
            c.close();
        }
        ContentValues v = new ContentValues();
        v.put("track_count", count);
        v.put("total_bytes", bytes);
        w().update(Db.T_SESSIONS, v, "_id=?", new String[]{String.valueOf(sessionId)});
    }

    // ============================================================== TRACKS ==
    public List<Track> tracks(long sessionId) {
        List<Track> out = new ArrayList<>();
        Cursor c = r().query(Db.T_TRACKS, null, "session_id=?",
                new String[]{String.valueOf(sessionId)}, null, null, "created_at DESC");
        try {
            while (c.moveToNext()) out.add(Track.from(c));
        } finally {
            c.close();
        }
        return out;
    }

    public List<Track> allTracks(int limit) {
        List<Track> out = new ArrayList<>();
        Cursor c = r().query(Db.T_TRACKS, null, null, null, null, null,
                "created_at DESC", limit > 0 ? String.valueOf(limit) : null);
        try {
            while (c.moveToNext()) out.add(Track.from(c));
        } finally {
            c.close();
        }
        return out;
    }

    public List<Track> searchTracks(String q) {
        List<Track> out = new ArrayList<>();
        String like = "%" + q + "%";
        Cursor c = r().query(Db.T_TRACKS, null,
                "title LIKE ? OR notes LIKE ? OR device_name LIKE ? OR file_path LIKE ?",
                new String[]{like, like, like, like}, null, null, "created_at DESC");
        try {
            while (c.moveToNext()) out.add(Track.from(c));
        } finally {
            c.close();
        }
        return out;
    }

    /**
     * true when a take or an export points at this file.  Both the absolute path
     * (what we stored) and the canonical one (what the filesystem reports) are
     * matched, because Android hands out /storage/... paths that are symlinks to
     * /mnt/media_rw/... - the same file under two names.
     */
    public boolean isKnownFile(String absolute, String canonical) {
        Cursor c = r().rawQuery("SELECT 1 FROM " + Db.T_TRACKS
                + " WHERE file_path=? OR file_path=?"
                + " UNION ALL SELECT 1 FROM " + Db.T_EXPORTS
                + " WHERE file_path=? OR file_path=? LIMIT 1",
                new String[]{absolute, canonical, absolute, canonical});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    public Track track(long id) {
        Cursor c = r().query(Db.T_TRACKS, null, "_id=?", new String[]{String.valueOf(id)},
                null, null, null);
        try {
            return c.moveToFirst() ? Track.from(c) : null;
        } finally {
            c.close();
        }
    }

    public long insert(Track t) {
        long now = System.currentTimeMillis();
        t.createdAt = now;
        t.updatedAt = now;
        ContentValues v = t.toValues();
        v.put("created_at", now);
        long id = w().insert(Db.T_TRACKS, null, v);
        if (t.sessionId > 0) refreshSessionStats(t.sessionId);
        return id;
    }

    public int update(Track t) {
        int n = w().update(Db.T_TRACKS, t.toValues(), "_id=?", new String[]{String.valueOf(t.id)});
        if (t.sessionId > 0) refreshSessionStats(t.sessionId);
        return n;
    }

    public int rename(Track t, String title) {
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("updated_at", System.currentTimeMillis());
        return w().update(Db.T_TRACKS, v, "_id=?", new String[]{String.valueOf(t.id)});
    }

    public int setStarred(long trackId, boolean starred) {
        ContentValues v = new ContentValues();
        v.put("starred", starred ? 1 : 0);
        v.put("updated_at", System.currentTimeMillis());
        return w().update(Db.T_TRACKS, v, "_id=?", new String[]{String.valueOf(trackId)});
    }

    public int moveToSession(long trackId, long sessionId) {
        ContentValues v = new ContentValues();
        v.put("session_id", sessionId);
        v.put("updated_at", System.currentTimeMillis());
        return w().update(Db.T_TRACKS, v, "_id=?", new String[]{String.valueOf(trackId)});
    }

    public int delete(Track t, boolean deleteFile) {
        if (deleteFile) deleteFileQuietly(t.filePath);
        w().delete(Db.T_EXPORTS, "track_id=?", new String[]{String.valueOf(t.id)});
        int n = w().delete(Db.T_TRACKS, "_id=?", new String[]{String.valueOf(t.id)});
        if (t.sessionId > 0) refreshSessionStats(t.sessionId);
        return n;
    }

    public int nextTakeNo(long sessionId) {
        Cursor c = r().rawQuery("SELECT IFNULL(MAX(take_no),0)+1 FROM " + Db.T_TRACKS
                + " WHERE session_id=?", new String[]{String.valueOf(sessionId)});
        try {
            return c.moveToFirst() ? c.getInt(0) : 1;
        } finally {
            c.close();
        }
    }

    public int trackCount() {
        Cursor c = r().rawQuery("SELECT COUNT(*) FROM " + Db.T_TRACKS, null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    // ============================================================= PRESETS ==
    public List<Preset> presets() {
        List<Preset> out = new ArrayList<>();
        Cursor c = r().query(Db.T_PRESETS, null, null, null, null, null,
                "builtin DESC, use_count DESC, name ASC");
        try {
            while (c.moveToNext()) out.add(Preset.from(c));
        } finally {
            c.close();
        }
        return out;
    }

    /** presets that make sense for the interface currently attached */
    public List<Preset> presetsFor(String deviceName, int vid, int pid) {
        List<Preset> out = new ArrayList<>();
        for (Preset p : presets()) {
            boolean vendorHit = vid != 0 && p.vendorId == vid;
            boolean nameHit = deviceName != null && p.deviceName != null
                    && !deviceName.isEmpty() && p.deviceName.length() > 2
                    && (deviceName.toLowerCase().contains(p.deviceName.toLowerCase())
                        || p.deviceName.toLowerCase().contains(deviceName.toLowerCase()));
            if (vendorHit || nameHit) out.add(p);
        }
        return out;
    }

    public Preset preset(long id) {
        Cursor c = r().query(Db.T_PRESETS, null, "_id=?", new String[]{String.valueOf(id)},
                null, null, null);
        try {
            return c.moveToFirst() ? Preset.from(c) : null;
        } finally {
            c.close();
        }
    }

    public long insert(Preset p) {
        long now = System.currentTimeMillis();
        p.createdAt = now;
        p.updatedAt = now;
        ContentValues v = p.toValues();
        v.put("created_at", now);
        v.put("builtin", p.builtin ? 1 : 0);
        return w().insert(Db.T_PRESETS, null, v);
    }

    public int update(Preset p) {
        return w().update(Db.T_PRESETS, p.toValues(), "_id=?", new String[]{String.valueOf(p.id)});
    }

    public int delete(long id) {
        return w().delete(Db.T_PRESETS, "_id=?", new String[]{String.valueOf(id)});
    }

    public int bumpUse(long id) {
        w().execSQL("UPDATE " + Db.T_PRESETS + " SET use_count=use_count+1 WHERE _id=?",
                new Object[]{id});
        return 0;
    }

    // ============================================================= EXPORTS ==
    public List<Export> exports() {
        List<Export> out = new ArrayList<>();
        Cursor c = r().query(Db.T_EXPORTS, null, null, null, null, null, "created_at DESC");
        try {
            while (c.moveToNext()) out.add(Export.from(c));
        } finally {
            c.close();
        }
        return out;
    }

    public List<Export> exportsForTrack(long trackId) {
        List<Export> out = new ArrayList<>();
        Cursor c = r().query(Db.T_EXPORTS, null, "track_id=?",
                new String[]{String.valueOf(trackId)}, null, null, "created_at DESC");
        try {
            while (c.moveToNext()) out.add(Export.from(c));
        } finally {
            c.close();
        }
        return out;
    }

    public long insert(Export e) {
        long now = System.currentTimeMillis();
        e.createdAt = now;
        e.updatedAt = now;
        ContentValues v = e.toValues();
        v.put("created_at", now);
        return w().insert(Db.T_EXPORTS, null, v);
    }

    public int update(Export e) {
        return w().update(Db.T_EXPORTS, e.toValues(), "_id=?", new String[]{String.valueOf(e.id)});
    }

    public int delete(Export e, boolean deleteFile) {
        if (deleteFile) deleteFileQuietly(e.filePath);
        return w().delete(Db.T_EXPORTS, "_id=?", new String[]{String.valueOf(e.id)});
    }

    // ========================================================== AGGREGATES ==
    /** {tracks, bytes, durationMs} for the dashboard */
    public long[] libraryTotals() {
        long[] out = new long[3];
        Cursor c = r().rawQuery("SELECT COUNT(*), IFNULL(SUM(size_bytes),0), "
                + "IFNULL(SUM(duration_ms),0) FROM " + Db.T_TRACKS, null);
        try {
            if (c.moveToFirst()) {
                out[0] = c.getLong(0);
                out[1] = c.getLong(1);
                out[2] = c.getLong(2);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public int exportCount() {
        Cursor c = r().rawQuery("SELECT COUNT(*) FROM " + Db.T_EXPORTS, null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public int presetCount() {
        Cursor c = r().rawQuery("SELECT COUNT(*) FROM " + Db.T_PRESETS, null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public int sessionCount() {
        Cursor c = r().rawQuery("SELECT COUNT(*) FROM " + Db.T_SESSIONS, null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    /** per-day track counts for the dashboard activity strip (last `days` days) */
    public int[] tracksPerDay(int days) {
        int[] out = new int[days];
        long dayMs = 86400000L;
        long today = System.currentTimeMillis() / dayMs;
        Cursor c = r().rawQuery("SELECT created_at FROM " + Db.T_TRACKS
                + " WHERE created_at > ?", new String[]{String.valueOf((today - days + 1) * dayMs)});
        try {
            while (c.moveToNext()) {
                long d = c.getLong(0) / dayMs;
                int idx = (int) (d - (today - days + 1));
                if (idx >= 0 && idx < days) out[idx]++;
            }
        } finally {
            c.close();
        }
        return out;
    }

    private void deleteFileQuietly(String path) {
        if (path == null || path.isEmpty()) return;
        try {
            File f = new File(path);
            if (f.exists()) //noinspection ResultOfMethodCallIgnored
                f.delete();
        } catch (Exception ignored) {
        }
    }
}
