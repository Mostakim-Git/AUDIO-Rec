package com.mostakim.audiorec;

import android.app.Application;
import android.content.Context;

import com.mostakim.audiorec.audio.AudioEngine;
import com.mostakim.audiorec.db.Db;
import com.mostakim.audiorec.util.Prefs;

import java.io.File;

/** Harness App: same surface as the real one, no SQLite and no audio hardware. */
public class App extends Application {

    private static App sInstance;

    private Prefs mPrefs;
    private Db mDb;
    private AudioEngine mEngine;

    public static App get() { return sInstance; }

    public static Context ctx() { return sInstance; }

    public void initForHarness() {
        sInstance = this;
        mPrefs = new Prefs(this);
        mDb = new Db(this);
        mEngine = new AudioEngine(this);
        onCreate();
    }

    @Override
    public void onCreate() {
    }

    public Prefs prefs() { return mPrefs; }

    public Db db() { return mDb; }

    public AudioEngine audio() { return mEngine; }

    public boolean storageReady() { return true; }

    public static File defaultRecordDir(Context c) {
        File base = c.getExternalFilesDir(null);
        if (base == null) base = new File(c.getFilesDir(), "rec");
        return new File(base, "Recordings");
    }

    public static boolean isExternalVolumeMounted() { return true; }
}
