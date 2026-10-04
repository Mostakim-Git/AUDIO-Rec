package com.mostakim.audiorec.audio;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.mostakim.audiorec.App;
import com.mostakim.audiorec.db.Models.Export;
import com.mostakim.audiorec.db.Models.Track;
import com.mostakim.audiorec.db.Store;
import com.mostakim.audiorec.util.Ids;

import java.io.File;

/**
 * Renders a take into an export file and records it in the database.
 *
 * Shared by the library and the exports page so both produce identical rows:
 * output lands in &lt;recordings&gt;/Exports with a unique name, the format is read
 * back from the finished file, and the database only ever points at audio that
 * really exists.
 */
public final class Exporter {

    private Exporter() {
    }

    public interface Callback {
        void onExportDone(Export e);

        void onExportFailed(String message);
    }

    public static File exportsDir(Context c) {
        File dir = App.get().prefs().recordDir();
        if (dir == null) dir = App.defaultRecordDir(c);
        File out = new File(dir, "Exports");
        if (!out.exists()) //noinspection ResultOfMethodCallIgnored
            out.mkdirs();
        return out;
    }

    public static void start(Context ctx, final Store store, final Track t, final String container,
                             final int depth, final int rate, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        final File out = Ids.uniqueFile(exportsDir(app),
                t.title + " (" + depth + "bit-" + (rate / 1000) + "k)", container);
        new ExportTask(app, t, out, container, depth, rate,
                result -> {
                    Export e = new Export();
                    e.trackId = t.id;
                    e.trackTitle = t.title;
                    e.name = out.getName();
                    e.filePath = out.getAbsolutePath();
                    e.container = result.container;
                    e.sampleRate = result.sampleRate;
                    e.bitDepth = result.bitDepth;
                    e.channels = result.channels;
                    e.sizeBytes = result.bytes;
                    e.durationMs = result.durationMs;
                    e.status = "ready";
                    e.note = result.note == null ? "" : result.note;
                    store.insert(e);
                    if (cb != null) main.post(() -> cb.onExportDone(e));
                },
                error -> {
                    if (cb != null) main.post(() -> cb.onExportFailed(error));
                }).start();
    }

    /** free-form note shown on the exports page for a finishing job */
    public static String describe(Export e) {
        return e.container.toUpperCase() + " \u00b7 " + e.bitDepth + "-bit \u00b7 "
                + (e.sampleRate / 1000) + " kHz \u00b7 " + e.channels + " ch";
    }
}
