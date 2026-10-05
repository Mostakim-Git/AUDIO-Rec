package com.mostakim.audiorec.share;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import com.mostakim.audiorec.App;
import com.mostakim.audiorec.db.Store;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Shares a take or an export with anything that accepts a content:// stream -
 * Drive, WhatsApp, Telegram, a NAS sync client.
 *
 * Deliberately locked down: only files that live inside AUDIO-rec's own
 * directories - or that the database knows we recorded or exported - can be
 * served, the provider is not exported, and read access is granted per-URI, so
 * the rest of the filesystem stays out of reach.  The rules themselves live in
 * ShareRules, which has no framework dependencies and is covered by
 * tools/test/ShareCheck.java.
 */
public class ExportProvider extends ContentProvider {

    public static final String AUTHORITY = "com.mostakim.audiorec.files";

    public static Uri uriFor(File f) {
        return Uri.parse("content://" + AUTHORITY + "/"
                + Uri.encode(f.getAbsolutePath(), "/"));
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        String path = uri.getPath();
        if (path == null || path.isEmpty()) throw new FileNotFoundException("empty path");
        File f = new File(path);
        if (!f.isFile()) throw new FileNotFoundException(path);
        if (!isAllowed(f)) throw new FileNotFoundException("outside the app sandbox");
        return f;
    }

    private boolean isAllowed(File f) {
        String canonical = canonical(f);
        if (canonical == null) return false;
        boolean known = false;
        App app = App.get();
        if (app != null && app.db() != null) {
            try {
                // absolute as stored (a /storage/... path) and canonical (the same
                // file below /mnt/media_rw/...): Android's storage paths are symlinks
                known = new Store(app, app.db()).isKnownFile(f.getAbsolutePath(), canonical);
            } catch (Exception ignored) {
            }
        }
        return ShareRules.allowedPath(canonical, allowedRoots(), known);
    }

    private String[] allowedRoots() {
        App app = App.get();
        if (app == null) return new String[0];
        return ShareRules.roots(
                canonical(app.getFilesDir()),
                canonical(app.getExternalFilesDir(null)),
                canonical(app.prefs().recordDir()),
                canonical(new File(app.getCacheDir(), "share")));
    }

    /** each directory resolves on its own, so one broken path cannot hide the rest */
    private static String canonical(File f) {
        if (f == null) return null;
        try {
            return f.getCanonicalPath();
        } catch (Exception e) {
            return f.getAbsolutePath();
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = resolve(uri);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return ShareRules.mimeFor(uri.getLastPathSegment());
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            File f = resolve(uri);
            String[] cols = projection != null ? projection
                    : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
            MatrixCursor c = new MatrixCursor(cols, 1);
            Object[] row = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
                else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
                else row[i] = null;
            }
            c.addRow(row);
            return c;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
