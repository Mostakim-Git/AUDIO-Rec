package com.mostakim.audiorec.audio;

import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.File;
import java.io.RandomAccessFile;

/**
 * Reads the actual header of an audio file so the library can show real
 * numbers even for files that were dropped into the folder from somewhere else
 * (a card reader, a DAW export, another recorder).
 *
 * Supports WAV/RF64, AIFF, FLAC and Ogg (Opus/Vorbis), and falls back to the
 * platform extractor for anything else it can open.
 */
public final class FormatProbe {

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

        public boolean valid() {
            return sampleRate > 0 && channels > 0;
        }

        public String describe() {
            if (!valid()) return container.toUpperCase() + " \u00b7 unreadable";
            return sampleRate / 1000 + " kHz  \u00b7  " + bitDepth + "-bit  \u00b7  "
                    + channels + " ch  \u00b7  " + container.toUpperCase()
                    + (rf64 ? " (RF64)" : "");
        }
    }

    private FormatProbe() {
    }

    public static Info probe(File f) {
        Info info = new Info();
        if (f == null || !f.exists()) return info;
        info.sizeBytes = f.length();
        try {
            byte[] head = new byte[64];
            RandomAccessFile raf = new RandomAccessFile(f, "r");
            try {
                raf.readFully(head, 0, (int) Math.min(head.length, raf.length()));
            } finally {
                raf.close();
            }
            String magic = new String(head, 0, 4, "US-ASCII");
            if (magic.equals("RIFF") || magic.equals("RF64")) {
                parseWav(f, info);
            } else if (magic.equals("FORM")) {
                parseAiff(f, info);
            } else if (magic.equals("fLaC")) {
                parseFlac(f, info);
            } else if (magic.equals("OggS")) {
                parseOgg(f, info);
            } else {
                parseWithPlatform(f, info);
            }
        } catch (Exception e) {
            parseWithPlatform(f, info);
        }
        if (!info.valid()) parseWithPlatform(f, info);
        if (info.durationMs == 0 && info.sampleRate > 0 && info.frames > 0) {
            info.durationMs = info.frames * 1000L / info.sampleRate;
        }
        return info;
    }

    // ---------------------------------------------------------------- WAV ---
    private static void parseWav(File f, Info info) throws Exception {
        RandomAccessFile r = new RandomAccessFile(f, "r");
        try {
            byte[] b4 = new byte[4];
            r.readFully(b4);
            String magic = new String(b4, "US-ASCII");
            info.container = "wav";
            info.rf64 = magic.equals("RF64");
            r.skipBytes(4);                       // size (may be -1 for RF64)
            r.readFully(b4);                      // WAVE
            long riffSize = -1, dataSize = -1, sampleCount = -1;
            while (r.getFilePointer() < r.length() - 8) {
                r.readFully(b4);
                String id = new String(b4, "US-ASCII");
                long size = readIntLE(r) & 0xFFFFFFFFL;
                long next = r.getFilePointer() + size + (size & 1);
                if (id.equals("ds64")) {
                    riffSize = readLongLE(r);
                    dataSize = readLongLE(r);
                    sampleCount = readLongLE(r);
                } else if (id.equals("fmt ")) {
                    int tag = readShortLE(r);
                    info.channels = readShortLE(r);
                    info.sampleRate = (int) (readIntLE(r) & 0xFFFFFFFFL);
                    r.skipBytes(6);
                    info.bitDepth = readShortLE(r);
                    info.codec = (tag == 3 || tag == 0xFFFE) ? "PCM/float" : "PCM";
                    if (tag == 0xFFFE && size >= 40) {
                        r.skipBytes(2 + 2 + 4);   // cbSize, validBits, mask
                        byte[] guid = new byte[16];
                        r.readFully(guid);
                        if (guid[0] == 3) info.codec = "PCM/float";
                    }
                } else if (id.equals("data")) {
                    if (size == 0xFFFFFFFFL && dataSize > 0) size = dataSize;
                    info.frames = info.channels > 0
                            ? size / (info.channels * Math.max(1, info.bitDepth / 8)) : 0;
                    if (sampleCount > 0) info.frames = sampleCount;
                    break;
                }
                if (next <= r.getFilePointer()) break;
                r.seek(next);
            }
        } finally {
            r.close();
        }
    }

    // --------------------------------------------------------------- AIFF ---
    private static void parseAiff(File f, Info info) throws Exception {
        RandomAccessFile r = new RandomAccessFile(f, "r");
        try {
            byte[] b4 = new byte[4];
            r.skipBytes(8);                       // FORM + size
            r.readFully(b4);
            info.container = "aiff";
            info.codec = "PCM";
            while (r.getFilePointer() < r.length() - 8) {
                r.readFully(b4);
                String id = new String(b4, "US-ASCII");
                long size = readIntBE(r) & 0xFFFFFFFFL;
                long next = r.getFilePointer() + size + (size & 1);
                if (id.equals("COMM")) {
                    info.channels = readShortBE(r);
                    info.frames = readIntBE(r) & 0xFFFFFFFFL;
                    info.bitDepth = readShortBE(r);
                    byte[] ext = new byte[10];
                    r.readFully(ext);
                    info.sampleRate = (int) extended80ToDouble(ext);
                    break;
                }
                if (next <= r.getFilePointer()) break;
                r.seek(next);
            }
        } finally {
            r.close();
        }
    }

    static double extended80ToDouble(byte[] b) {
        int exp = ((b[0] & 0x7F) << 8) | (b[1] & 0xFF);
        long mant = 0;
        for (int i = 2; i < 10; i++) mant = (mant << 8) | (b[i] & 0xFF);
        if (exp == 0 && mant == 0) return 0;
        boolean neg = (b[0] & 0x80) != 0;
        // the mantissa is unsigned: bit 63 is the explicit integer bit, so the
        // fraction has to be taken without it and added back as a power of two
        long frac = mant & 0x7FFFFFFFFFFFFFFFL;
        double v = Math.scalb((double) frac, exp - 16383 - 63);
        if ((mant & 0x8000000000000000L) != 0) v += Math.pow(2.0, exp - 16383);
        return neg ? -v : v;
    }

    // --------------------------------------------------------------- FLAC ---
    private static void parseFlac(File f, Info info) throws Exception {
        RandomAccessFile r = new RandomAccessFile(f, "r");
        try {
            r.skipBytes(4);
            info.container = "flac";
            info.codec = "FLAC";
            while (r.getFilePointer() < r.length()) {
                int h = r.read();
                if (h < 0) break;
                boolean last = (h & 0x80) != 0;
                int type = h & 0x7F;
                int len = (r.read() << 16) | (r.read() << 8) | r.read();
                if (type == 0) {
                    byte[] si = new byte[len];
                    r.readFully(si);
                    long bits = ((long) (si[10] & 0xFF) << 32) | ((long) (si[11] & 0xFF) << 24)
                            | ((long) (si[12] & 0xFF) << 16) | ((long) (si[13] & 0xFF) << 8)
                            | (si[14] & 0xFF);
                    int rate = (int) ((bits >> 44) & 0xFFFFF);
                    int chans = (int) ((bits >> 41) & 0x7) + 1;
                    int depth = (int) ((bits >> 36) & 0x1F) + 1;
                    long total = bits & 0xFFFFFFFFFL;
                    info.sampleRate = rate;
                    info.channels = chans;
                    info.bitDepth = depth;
                    info.frames = total;
                    break;
                }
                r.skipBytes(len);
                if (last) break;
            }
        } finally {
            r.close();
        }
    }

    // ---------------------------------------------------------------- Ogg ---
    private static void parseOgg(File f, Info info) throws Exception {
        RandomAccessFile r = new RandomAccessFile(f, "r");
        try {
            info.container = "ogg";
            byte[] head = new byte[(int) Math.min(4096, r.length())];
            r.readFully(head);
            String s = new String(head, "ISO-8859-1");
            if (s.contains("OpusHead")) {
                int p = s.indexOf("OpusHead") + 8;
                int version = head[p] & 0xFF;
                info.channels = head[p + 1] & 0xFF;
                info.sampleRate = 48000;          // Opus always decodes at 48 kHz
                info.bitDepth = 32;               // float output
                info.codec = "Opus";
            } else if (s.contains("vorbis")) {
                info.codec = "Vorbis";
                int p = s.indexOf("\u0001vorbis");
                if (p > 0 && p + 16 < head.length) {
                    info.channels = head[p + 11] & 0xFF;
                    info.sampleRate = (int) ((head[p + 12] & 0xFF) | ((head[p + 13] & 0xFF) << 8)
                            | ((head[p + 14] & 0xFF) << 16) | ((head[p + 15] & 0xFF) << 24));
                    info.bitDepth = 16;
                }
            }
            // duration: the granule position of the final page
            long granule = lastOggGranule(f);
            if (granule > 0 && info.sampleRate > 0) {
                long samples = granule - 312;      // pre-skip
                if (samples > 0) info.durationMs = samples * 1000L / info.sampleRate;
                info.frames = info.sampleRate > 0 ? info.durationMs * info.sampleRate / 1000 : 0;
            }
        } finally {
            r.close();
        }
    }

    private static long lastOggGranule(File f) throws Exception {
        RandomAccessFile r = new RandomAccessFile(f, "r");
        try {
            long len = r.length();
            int window = (int) Math.min(65536, len);
            byte[] buf = new byte[window];
            r.seek(len - window);
            r.readFully(buf);
            for (int i = window - 27; i >= 0; i--) {
                if (buf[i] == 'O' && buf[i + 1] == 'g' && buf[i + 2] == 'g' && buf[i + 3] == 'S') {
                    long g = 0;
                    for (int k = 7; k >= 0; k--) g = (g << 8) | (buf[i + 6 + k] & 0xFF);
                    if (g > 0) return g;
                }
            }
            return 0;
        } finally {
            r.close();
        }
    }

    // ------------------------------------------------------------ fallback --
    private static void parseWithPlatform(File f, Info info) {
        MediaExtractor ex = null;
        try {
            ex = new MediaExtractor();
            ex.setDataSource(f.getAbsolutePath());
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat fmt = ex.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME);
                if (mime == null || !mime.startsWith("audio/")) continue;
                info.container = mime.substring(6);
                info.codec = mime;
                if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                    info.sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                    info.channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                info.bitDepth = 16;
                if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                    long us = fmt.getLong(MediaFormat.KEY_DURATION);
                    info.durationMs = us / 1000;
                    info.frames = info.sampleRate > 0 ? us * info.sampleRate / 1_000_000L : 0;
                } else {
                    long ms = ex.getTrackFormat(i).containsKey("durationUs")
                            ? fmt.getLong("durationUs") / 1000 : 0;
                    info.durationMs = ms;
                }
                break;
            }
        } catch (Exception ignored) {
        } finally {
            if (ex != null) try {
                ex.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static int readShortLE(RandomAccessFile r) throws Exception {
        int a = r.read(), b = r.read();
        return (b << 8) | a;
    }

    private static int readShortBE(RandomAccessFile r) throws Exception {
        int a = r.read(), b = r.read();
        return (a << 8) | b;
    }

    private static int readIntLE(RandomAccessFile r) throws Exception {
        int a = r.read(), b = r.read(), c = r.read(), d = r.read();
        return (d << 24) | (c << 16) | (b << 8) | a;
    }

    private static long readLongLE(RandomAccessFile r) throws Exception {
        long v = 0;
        for (int i = 0; i < 8; i++) v |= ((long) r.read() & 0xFF) << (8 * i);
        return v;
    }

    private static int readIntBE(RandomAccessFile r) throws Exception {
        int a = r.read(), b = r.read(), c = r.read(), d = r.read();
        return (a << 24) | (b << 16) | (c << 8) | d;
    }
}
