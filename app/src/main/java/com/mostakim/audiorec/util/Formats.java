package com.mostakim.audiorec.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Static format knowledge: containers, sample rates, depths, buffers. */
public final class Formats {

    private Formats() {
    }

    public static final String WAV = "wav";
    public static final String FLAC = "flac";
    public static final String AIFF = "aiff";
    public static final String OGG = "ogg";

    public static final List<String> CONTAINERS =
            Arrays.asList(WAV, FLAC, AIFF, OGG);

    /** professional + consumer rates; the recorder only offers what the unit reports */
    public static final int[] ALL_RATES = {
            8000, 11025, 16000, 22050, 32000, 44100, 48000,
            88200, 96000, 176400, 192000, 352800, 384000
    };

    public static final int[] BIT_DEPTHS = {16, 24, 32};

    /** buffer size selection, in frames (as offered by the recorder) */
    public static final int[] BUFFER_SIZES = {1024, 2048, 4096, 8192, 16384};

    public static String displayName(String container) {
        if (WAV.equals(container)) return "WAV  \u00b7  uncompressed PCM";
        if (FLAC.equals(container)) return "FLAC  \u00b7  lossless (native encoder)";
        if (AIFF.equals(container)) return "AIFF  \u00b7  uncompressed PCM (big endian)";
        if (OGG.equals(container)) return "OGG  \u00b7  Opus, patent-free";
        return container.toUpperCase();
    }

    public static String ext(String container) {
        return container;
    }

    public static boolean isLossless(String container) {
        return !OGG.equals(container);
    }

    public static boolean supportsDepth(String container, int depth) {
        if (OGG.equals(container)) return depth == 32; // always 32f source, encoder takes it
        if (FLAC.equals(container)) return depth != 32; // FLAC has no 32-bit integer PCM
        return true;
    }

    public static String mimeFor(String container) {
        if (WAV.equals(container)) return "audio/wav";
        if (FLAC.equals(container)) return "audio/flac";
        if (AIFF.equals(container)) return "audio/x-aiff";
        if (OGG.equals(container)) return "audio/ogg";
        return "application/octet-stream";
    }

    public static List<String> playbackExtensions() {
        return new ArrayList<>(Arrays.asList("wav", "aif", "aiff", "flac", "ogg", "oga"));
    }

    public static boolean isPlayable(String name) {
        String n = name.toLowerCase();
        for (String e : playbackExtensions()) {
            if (n.endsWith("." + e)) return true;
        }
        return false;
    }

    /** the sample rates we can *record* at, filtered against what a device reports */
    public static int[] ratesUpTo(int maxRate) {
        List<Integer> out = new ArrayList<>();
        for (int r : ALL_RATES) {
            if (r <= maxRate) out.add(r);
        }
        if (out.isEmpty()) out.add(maxRate);
        int[] a = new int[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    public static int indexOf(int[] arr, int value) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value) return i;
        }
        return -1;
    }
}
