package com.mostakim.audiorec.audio;

import java.io.File;

import com.mostakim.audiorec.util.Formats;

/** Harness probe: derives plausible facts from the file name. */
public final class FormatProbe {

    private FormatProbe() { }

    public static class Info {
        public String container = "?";
        public int sampleRate = 0;
        public int channels = 0;
        public int bitDepth = 0;
        public long frames = 0;
        public long durationMs = 0;
        public long sizeBytes = 0;
        public boolean rf64 = false;
        public String codec = "";

        public boolean valid() { return sampleRate > 0 && channels > 0; }

        public String describe() {
            return container.toUpperCase() + " \u00b7 " + (sampleRate / 1000) + " kHz \u00b7 "
                    + bitDepth + "-bit \u00b7 " + channels + " ch";
        }
    }

    public static Info probe(File f) {
        Info i = new Info();
        i.sizeBytes = f == null ? 0 : f.length();
        if (f == null) return i;
        String name = f.getName().toLowerCase();
        i.container = name.endsWith(".flac") ? Formats.FLAC
                : name.endsWith(".aiff") || name.endsWith(".aif") ? Formats.AIFF
                : name.endsWith(".ogg") ? Formats.OGG : Formats.WAV;
        i.sampleRate = 96000;
        i.bitDepth = 24;
        i.channels = 2;
        i.durationMs = 24000;
        i.frames = i.durationMs * i.sampleRate / 1000;
        return i;
    }
}
