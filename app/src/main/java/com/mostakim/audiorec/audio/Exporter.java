package com.mostakim.audiorec.audio;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.mostakim.audiorec.App;
import com.mostakim.audiorec.db.Models.Export;
import com.mostakim.audiorec.db.Models.Track;
import com.mostakim.audiorec.db.Store;
import com.mostakim.audiorec.share.Downloads;
import com.mostakim.audiorec.util.Formats;
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

        /**
         * The finished export has also been published to {@code Download/AUDIO-rec},
         * where a file manager, a USB cable, Drive or WhatsApp can reach it.
         */
        default void onExportPublished(Export e, String visiblePath) {
        }
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
                    // The rendered copy lives in the app's own folder, which no
                    // file manager shows.  A second copy in the public Downloads
                    // collection is what makes the export an actual file the
                    // operator can find, move, share or plug into a computer.
                    Downloads.save(app, out, Formats.mimeFor(result.container), out.getName(),
                            (uri, visible, error) -> {
                                if (cb != null) main.post(() -> {
                                    cb.onExportDone(e);
                                    if (uri != null) cb.onExportPublished(e, visible);
                                });
                            });
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
