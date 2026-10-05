package com.mostakim.audiorec.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Formatting shared by every screen: timecode, sample math, file sizes. */
public final class Fmt {

    private Fmt() {
    }

    private static final SimpleDateFormat STAMP =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    private static final SimpleDateFormat STAMP_SHORT =
            new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.US);
    private static final SimpleDateFormat FILE_STAMP =
            new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);

    public static String stamp(long millis) {
        return STAMP.format(new Date(millis));
    }

    public static String stampShort(long millis) {
        return STAMP_SHORT.format(new Date(millis));
    }

    public static String fileStamp(long millis) {
        return FILE_STAMP.format(new Date(millis));
    }

    /** mm:ss.S or h:mm:ss.S with tenths - the recorder's running time */
    public static String timecode(long millis) {
        if (millis < 0) millis = 0;
        long h = millis / 3600000L;
        long m = (millis / 60000L) % 60L;
        long s = (millis / 1000L) % 60L;
        long t = (millis / 100L) % 10L;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d.%d", h, m, s, t);
        return String.format(Locale.US, "%02d:%02d.%d", m, s, t);
    }

    /** hh:mm:ss for header tags and long durations */
    public static String clock(long millis) {
        if (millis < 0) millis = 0;
        long h = millis / 3600000L;
        long m = (millis / 60000L) % 60L;
        long s = (millis / 1000L) % 60L;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    public static String meterTime(long millis) {
        if (millis < 0) millis = 0;
        long h = millis / 3600000L;
        long m = (millis / 60000L) % 60L;
        long s = (millis / 1000L) % 60L;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    /** bytes -> 1.4 GB / 812 MB / 44 KB */
    public static String size(long bytes) {
        if (bytes < 0) bytes = 0;
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        double gb = mb / 1024.0;
        if (gb < 1024) return String.format(Locale.US, "%.2f GB", gb);
        return String.format(Locale.US, "%.2f TB", gb / 1024.0);
    }

    public static String khz(int hz) {
        if (hz % 1000 == 0) return (hz / 1000) + " kHz";
        return String.format(Locale.US, "%.1f kHz", hz / 1000.0);
    }

    public static String db(float db) {
        if (db <= -100f) return "-\u221E dB";
        return String.format(Locale.US, "%+.1f dB", db);
    }

    public static String dbShort(float db) {
        if (db <= -100f) return "-\u221E";
        return String.format(Locale.US, "%.1f", db);
    }

    /** bytes per second of *stored* audio, used for the space estimate */
    public static long bytesPerSecond(int channels, int bitDepth, int sampleRate, String container) {
        double raw = (double) channels * sampleRate * bitDepth / 8.0;
        if (Formats.WAV.equals(container) || Formats.AIFF.equals(container)) return (long) raw;
        // measured-ish ratios: FLAC ~0.62, OGG/Opus ~0.085 of 16-bit PCM
        if (Formats.FLAC.equals(container)) return (long) (raw * 0.62);
        if (Formats.OGG.equals(container)) {
            return (long) (channels * sampleRate * 0.085 * (160.0 / 320.0) * 2.0);
        }
        return (long) raw;
    }

    /** remaining recording time for the chosen format on `freeBytes` */
    public static long recordableSeconds(long freeBytes, int channels, int bitDepth,
                                         int sampleRate, String container) {
        long bps = Math.max(1, bytesPerSecond(channels, bitDepth, sampleRate, container));
        return freeBytes / bps;
    }

    public static String bytes(long n, long total) {
        return size(n) + " / " + size(total);
    }

    public static String percent(float fraction) {
        return String.format(Locale.US, "%.0f%%", fraction * 100f);
    }

    public static String pct0(double v) {
        return String.format(Locale.US, "%.0f", v);
    }

    /** "2 ch" / "1 ch" without pluralising surprises */
    public static String ch(int n) {
        return n + (n == 1 ? " ch" : " ch");
    }

    public static String label(String s) {
        return s == null ? "\u2014" : s;
    }

    public static String duration(int seconds) {
        if (seconds < 60) return seconds + "s";
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }
}
