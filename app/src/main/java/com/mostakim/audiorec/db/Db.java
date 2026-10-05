package com.mostakim.audiorec.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.mostakim.audiorec.util.Seed;

/**
 * AUDIO-rec local store.
 *
 * Four core resources, all created/read/updated/deleted from the UI:
 *   sessions       - a production session (artist, venue, target format)
 *   tracks         - captured audio files living inside a session
 *   device_presets - interface + format + gain recipes
 *   export_files   - rendered/bounced deliverables
 */
public class Db extends SQLiteOpenHelper {

    public static final String NAME = "audiorec.db";
    public static final int VERSION = 1;

    public static final String T_SESSIONS = "sessions";
    public static final String T_TRACKS = "tracks";
    public static final String T_PRESETS = "device_presets";
    public static final String T_EXPORTS = "export_files";

    public Db(Context context) {
        super(context, NAME, null, VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_SESSIONS + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "artist TEXT,"
                + "venue TEXT,"
                + "notes TEXT,"
                + "status TEXT DEFAULT 'open',"
                + "sample_rate INTEGER DEFAULT 48000,"
                + "bit_depth INTEGER DEFAULT 24,"
                + "channels INTEGER DEFAULT 2,"
                + "container TEXT DEFAULT 'wav',"
                + "track_count INTEGER DEFAULT 0,"
                + "total_bytes INTEGER DEFAULT 0,"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + T_TRACKS + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "session_id INTEGER,"
                + "title TEXT NOT NULL,"
                + "file_path TEXT NOT NULL,"
                + "container TEXT,"
                + "sample_rate INTEGER,"
                + "bit_depth INTEGER,"
                + "channels INTEGER,"
                + "duration_ms INTEGER DEFAULT 0,"
                + "size_bytes INTEGER DEFAULT 0,"
                + "peak_db REAL DEFAULT -120,"
                + "rms_db REAL DEFAULT -120,"
                + "device_name TEXT,"
                + "channel_map TEXT,"
                + "take_no INTEGER DEFAULT 1,"
                + "starred INTEGER DEFAULT 0,"
                + "notes TEXT,"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + T_PRESETS + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "device_name TEXT,"
                + "vendor_id INTEGER DEFAULT 0,"
                + "product_id INTEGER DEFAULT 0,"
                + "sample_rate INTEGER DEFAULT 48000,"
                + "bit_depth INTEGER DEFAULT 24,"
                + "channels INTEGER DEFAULT 2,"
                + "buffer_frames INTEGER DEFAULT 2048,"
                + "container TEXT DEFAULT 'wav',"
                + "gain_db REAL DEFAULT 0,"
                + "monitor INTEGER DEFAULT 0,"
                + "monitor_gain_db REAL DEFAULT -6,"
                + "notes TEXT,"
                + "builtin INTEGER DEFAULT 0,"
                + "use_count INTEGER DEFAULT 0,"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + T_EXPORTS + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "track_id INTEGER,"
                + "track_title TEXT,"
                + "name TEXT NOT NULL,"
                + "file_path TEXT,"
                + "container TEXT,"
                + "sample_rate INTEGER,"
                + "bit_depth INTEGER,"
                + "channels INTEGER,"
                + "size_bytes INTEGER DEFAULT 0,"
                + "duration_ms INTEGER DEFAULT 0,"
                + "status TEXT DEFAULT 'ready',"
                + "note TEXT,"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX idx_tracks_session ON " + T_TRACKS + "(session_id)");
        db.execSQL("CREATE INDEX idx_exports_track ON " + T_EXPORTS + "(track_id)");

        Seed.install(db, T_PRESETS, T_SESSIONS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v1 ships the full schema; later versions migrate in place.
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }
}
