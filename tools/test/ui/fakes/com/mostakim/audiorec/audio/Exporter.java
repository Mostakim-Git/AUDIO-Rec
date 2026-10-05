package com.mostakim.audiorec.audio;

import android.content.Context;

import com.mostakim.audiorec.db.Models.Export;
import com.mostakim.audiorec.db.Models.Track;
import com.mostakim.audiorec.db.Store;

import java.io.File;

/** Harness exporter: reports success without converting anything. */
public final class Exporter {

    private Exporter() { }

    public interface Callback {
        void onExportDone(Export e);

        void onExportFailed(String message);

        default void onExportPublished(Export e, String visiblePath) {
        }
    }

    public static File exportsDir(Context c) {
        File dir = com.mostakim.audiorec.App.defaultRecordDir(c);
        File out = new File(dir, "Exports");
        //noinspection ResultOfMethodCallIgnored
        out.mkdirs();
        return out;
    }

    public static void start(Context ctx, Store store, Track t, String container,
                             int depth, int rate, Callback cb) {
        Export e = new Export();
        e.trackId = t.id;
        e.trackTitle = t.title;
        e.container = container;
        e.bitDepth = depth;
        e.sampleRate = rate;
        e.channels = t.channels;
        e.status = "ready";
        if (cb != null) {
            cb.onExportDone(e);
            cb.onExportPublished(e, publishedPath(e.name));
        }
    }

    /** the harness pretends the copy landed in Downloads, like the real one */
    public static String publishedPath(String name) {
        return "Download/AUDIO-rec/" + name;
    }

    public static String describe(Export e) {
        return e.container.toUpperCase() + " \u00b7 " + e.bitDepth + "-bit \u00b7 "
                + (e.sampleRate / 1000) + " kHz \u00b7 " + e.channels + " ch";
    }
}
