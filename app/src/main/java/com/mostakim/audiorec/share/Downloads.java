package com.mostakim.audiorec.share;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Publishing a finished file where the rest of the phone can see it.
 *
 * Everything AUDIO-rec records or renders lives in its own folder. That is the
 * right place for the originals, but it is invisible to a file manager, to a USB
 * transfer, to Drive and to WhatsApp - which is what "I exported it but I never
 * got the file" looks like: the app plays its copy happily while the file the
 * operator was expecting is nowhere they can reach it.
 *
 * So a finished take or export is also written to the public Downloads
 * collection, under <code>Download/AUDIO-rec/</code>, through MediaStore on
 * Android 10+ - the file shows up in Downloads, in any file manager, in the
 * system picker, and the URI it comes back with can be handed to any other app
 * with a per-URI read grant.
 */
public final class Downloads {

    private static final String TAG = "Downloads";

    /** the visible folder every copy lands in: <Download>/AUDIO-rec */
    public static final String SUBDIR = "AUDIO-rec";

    public interface Done {
        /** {@code uri} is null when the copy failed; {@code visible} is the shown path */
        void onDone(Uri uri, String visible, String error);
    }

    private Downloads() {
    }

    /** runs the copy off the UI thread and reports on it */
    public static void save(final Context context, final File src, final String mime,
                            final String displayName, final Done done) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Uri uri = null;
            String error = null;
            try {
                uri = saveNow(app, src, mime, displayName);
            } catch (Exception e) {
                Log.w(TAG, "could not publish " + src, e);
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final Uri furi = uri;
            final String ferr = error;
            if (done != null) {
                main.post(() -> done.onDone(furi, visiblePath(displayName), ferr));
            }
        }, "audiorec-downloads").start();
    }

    /** where the operator will find the copy, in words */
    public static String visiblePath(String fileName) {
        return Environment.DIRECTORY_DOWNLOADS + "/" + SUBDIR + "/" + fileName;
    }

    /**
     * Copies {@code src} into the public Downloads collection and returns the URI
     * of the copy.  On Android 10+ this goes through MediaStore, so no storage
     * permission is involved; on anything older it falls back to a plain copy
     * into the same folder on the shared volume.
     */
    public static Uri saveNow(Context c, File src, String mime, String displayName)
            throws IOException {
        if (src == null || !src.isFile()) throw new IOException("the file is missing");
        String name = displayName == null || displayName.isEmpty() ? src.getName() : displayName;
        String type = mime == null || mime.isEmpty() ? "audio/*" : mime;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return throughMediaStore(c, src, type, name);
            } catch (Exception e) {
                Log.w(TAG, "MediaStore refused the copy, falling back to a plain file", e);
            }
        }
        return plainCopy(src, name);
    }

    private static Uri throughMediaStore(Context c, File src, String mime, String name)
            throws IOException {
        ContentResolver cr = c.getContentResolver();
        ContentValues row = new ContentValues();
        row.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        row.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        row.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + SUBDIR);
        row.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, row);
        if (uri == null) throw new IOException("Downloads refused the entry");
        try {
            copy(src, cr.openOutputStream(uri));
        } catch (IOException e) {
            cr.delete(uri, null, null);
            throw e;
        }
        // the file stays hidden until the pending flag is cleared
        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        cr.update(uri, done, null, null);
        return uri;
    }

    /** pre-Q (never shipped, kept for completeness): a plain copy into Download/ */
    private static Uri plainCopy(File src, String name) throws IOException {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), SUBDIR);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File out = new File(dir, name);
        int n = 2;
        while (out.exists()) {
            out = new File(dir, stem(name) + "-" + (n++) + ext(name));
        }
        copy(src, new FileOutputStream(out));
        return Uri.fromFile(out);
    }

    private static void copy(File src, OutputStream out) throws IOException {
        if (out == null) throw new IOException("no output stream");
        InputStream in = new FileInputStream(src);
        try {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String stem(String name) {
        int d = name.lastIndexOf('.');
        return d > 0 ? name.substring(0, d) : name;
    }

    private static String ext(String name) {
        int d = name.lastIndexOf('.');
        return d > 0 ? name.substring(d) : "";
    }
}
