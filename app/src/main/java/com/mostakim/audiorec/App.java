package com.mostakim.audiorec;

import android.app.Application;
import android.content.Context;
import android.os.Environment;

import com.mostakim.audiorec.audio.AudioEngine;
import com.mostakim.audiorec.db.Db;
import com.mostakim.audiorec.util.Prefs;

import java.io.File;

/**
 * AUDIO-rec process entry point.
 *
 * Everything is offline and local: a single SQLite database, a preferences file
 * and the recording folder on disk.  No accounts, no sign-in screen, no network.
 */
public class App extends Application {

    private static App sInstance;

    private Db mDb;
    private Prefs mPrefs;
    private AudioEngine mEngine;

    public static App get() {
        return sInstance;
    }

    public static Context ctx() {
        return sInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mPrefs = new Prefs(this);
        mDb = new Db(this);
        mEngine = new AudioEngine(this);
        ensureDefaultFolders();
    }

    /** the folder recordings land in unless the operator picked another one */
    public static File defaultRecordDir(Context c) {
        File base = c.getExternalFilesDir(null);
        if (base == null) base = new File(c.getFilesDir(), "rec");
        return new File(base, "Recordings");
    }

    private void ensureDefaultFolders() {
        File def = mPrefs.recordDir();
        if (def == null) {
            def = defaultRecordDir(this);
            mPrefs.setRecordDir(def);
        }
        if (!def.exists()) //noinspection ResultOfMethodCallIgnored
            def.mkdirs();
    }

    public Db db() {
        return mDb;
    }

    public Prefs prefs() {
        return mPrefs;
    }

    public AudioEngine audio() {
        return mEngine;
    }

    /** true when the volume holding the recording folder is mounted r/w */
    public boolean storageReady() {
        File d = mPrefs.recordDir();
        return d != null && (d.exists() || d.mkdirs());
    }

    public static boolean isExternalVolumeMounted() {
        String s = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(s);
    }
}
