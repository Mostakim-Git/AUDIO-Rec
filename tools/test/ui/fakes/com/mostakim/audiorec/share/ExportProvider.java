package com.mostakim.audiorec.share;

import android.net.Uri;

import java.io.File;

/** Harness content-provider helper: builds the same-looking URI. */
public final class ExportProvider {

    private ExportProvider() { }

    public static final String AUTHORITY = "com.mostakim.audiorec.files";

    public static Uri uriFor(File f) {
        return Uri.parse("content://" + AUTHORITY + "/export/" + f.getName());
    }
}
