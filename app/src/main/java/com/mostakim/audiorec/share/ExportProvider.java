package com.mostakim.audiorec.share;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import com.mostakim.audiorec.App;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Shares a take or an export with anything that accepts a content:// stream -
 * Drive, WhatsApp, Telegram, a NAS sync client.
 *
 * Deliberately locked down: only files that live inside AUDIO-rec's own
 * directories can be served, the provider is not exported, and read access is
 * granted per-URI, so the rest of the filesystem stays out of reach.
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
        if (!f.exists()) throw new FileNotFoundException(path);
        if (!isAllowed(f)) throw new FileNotFoundException("outside the app sandbox");
        return f;
    }

    private boolean isAllowed(File f) {
        String p;
        try {
            p = f.getCanonicalPath();
        } catch (java.io.IOException e) {
            return false;
        }
        String[] roots = allowedRoots();
        for (String r : roots) {
            if (r != null && p.startsWith(r)) return true;
        }
        return false;
    }

    private String[] allowedRoots() {
        App app = App.get();
        if (app == null) return new String[0];
        String[] roots = new String[4];
        try {
            File ext = app.getExternalFilesDir(null);
            File internal = app.getFilesDir();
            File rec = app.prefs().recordDir();
            roots[0] = ext == null ? null : ext.getCanonicalPath();
            roots[1] = internal == null ? null : internal.getCanonicalPath();
            roots[2] = rec == null ? null : rec.getCanonicalPath();
            roots[3] = new File(app.getCacheDir(), "share").getCanonicalPath();
        } catch (Exception ignored) {
        }
        return roots;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = resolve(uri);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        String lower = name == null ? "" : name.toLowerCase();
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".aiff") || lower.endsWith(".aif")) return "audio/x-aiff";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        return "application/octet-stream";
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
