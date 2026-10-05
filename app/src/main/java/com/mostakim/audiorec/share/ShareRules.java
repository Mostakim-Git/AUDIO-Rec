package com.mostakim.audiorec.share;

import java.util.Arrays;
import java.util.Locale;

/**
 * Path and MIME rules for sharing a take with Drive, WhatsApp, Telegram or a NAS
 * client, deliberately free of the Android framework so tools/test/ShareCheck.java
 * can exercise them off-device.
 *
 * The provider used to answer "is this file ours?" with a plain
 * {@code path.startsWith(root)} test, which also matches a sibling directory that
 * merely begins with the same characters ({@code .../files-old/x.wav} against the
 * root {@code .../files}).  Everything here compares whole path segments.
 */
public final class ShareRules {

    /** audio extension -> MIME, the table receivers actually look at */
    private static final String[][] TYPES = {
            {".wav", "audio/wav"},
            {".flac", "audio/flac"},
            {".aiff", "audio/x-aiff"},
            {".aif", "audio/x-aiff"},
            {".ogg", "audio/ogg"},
            {".oga", "audio/ogg"},
    };

    private ShareRules() {
    }

    /** true when {@code path} is {@code root} itself or lives inside it */
    public static boolean under(String path, String root) {
        if (path == null || root == null) return false;
        String p = strip(path), r = strip(root);
        if (p.isEmpty() || r.isEmpty()) return false;
        if (p.equals(r)) return true;
        return p.startsWith(r + "/");
    }

    /**
     * The directories a shareable file may live in: the app's own storage, the
     * folder recordings are written to (which the operator may have moved to an
     * SD card) and the cache area used for anything temporary.
     *
     * Null and empty entries are dropped and duplicates collapsed, so one
     * unresolved directory can no longer throw away the rest of the list.
     */
    public static String[] roots(String internalFiles, String externalFiles, String recordDir,
                                 String cacheShare) {
        String[] candidates = {internalFiles, externalFiles, recordDir, cacheShare};
        String[] out = new String[candidates.length];
        int n = 0;
        for (String candidate : candidates) {
            if (candidate == null) continue;
            String path = strip(candidate);
            if (path.isEmpty()) continue;
            boolean seen = false;
            for (int i = 0; i < n; i++) {
                if (out[i].equals(path)) seen = true;
            }
            if (!seen) out[n++] = path;
        }
        return Arrays.copyOf(out, n);
    }

    /**
     * Whether a canonical path may be served.
     *
     * @param knownFile true when the database has a take or export row for it,
     *                  which is how a file stays shareable after the recording
     *                  folder was moved somewhere else
     */
    public static boolean allowedPath(String path, String[] roots, boolean knownFile) {
        if (path == null) return false;
        if (knownFile) return true;
        if (roots != null) {
            for (String root : roots) {
                if (under(path, root)) return true;
            }
        }
        return false;
    }

    /** MIME type by file name; anything unknown is a plain byte stream */
    public static String mimeFor(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        for (String[] type : TYPES) {
            if (lower.endsWith(type[0])) return type[1];
        }
        return "application/octet-stream";
    }

    private static String strip(String path) {
        String p = path.trim();
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }
}
