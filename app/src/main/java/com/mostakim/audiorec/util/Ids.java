package com.mostakim.audiorec.util;

import java.util.Random;

/** Small id/slug helpers (offline; java.util.UUID is available on Android). */
public final class Ids {

    private static final Random RNG = new Random();

    private Ids() {
    }

    public static String uuid() {
        return java.util.UUID.randomUUID().toString();
    }

    public static String shortId(int len) {
        final char[] alphabet = "abcdefghjkmnpqrstuvwxyz23456789".toCharArray();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(alphabet[RNG.nextInt(alphabet.length)]);
        return sb.toString();
    }

    /** "Take 3 — Voice" -> "take-3-voice" */
    public static String slug(String in, String fallback) {
        if (in == null) return fallback;
        String s = in.toLowerCase().trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (s.length() > 48) s = s.substring(0, 48);
        return s.isEmpty() ? fallback : s;
    }

    /** A filesystem-safe stem: keeps readable names, strips separators. */
    public static String safeName(String in, String fallback) {
        if (in == null) return fallback;
        String s = in.trim()
                .replace('/', '-').replace('\\', '-').replace(':', '-')
                .replace('*', '-').replace('?', '-').replace('"', '\'')
                .replace('<', '-').replace('>', '-').replace('|', '-')
                .replaceAll("\\s+", " ");
        if (s.isEmpty()) return fallback;
        if (s.length() > 64) s = s.substring(0, 64);
        return s;
    }

    /** find a free file name: name.wav, name-2.wav, name-3.wav ... */
    public static java.io.File uniqueFile(java.io.File dir, String stem, String ext) {
        java.io.File f = new java.io.File(dir, stem + "." + ext);
        int n = 2;
        while (f.exists()) {
            f = new java.io.File(dir, stem + "-" + n + "." + ext);
            n++;
        }
        return f;
    }
}
