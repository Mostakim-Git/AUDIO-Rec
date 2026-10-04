package com.mostakim.audiorec.audio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Reader for the containers Android itself cannot decode.
 *
 * AIFF is the important one: the platform ships extractors for WAV, FLAC, Ogg
 * and MP3 but *not* for AIFF, and the brief explicitly asks for AIFF playback.
 * This parses COMM/SSND and streams big-endian PCM as float, which the playback
 * engine feeds to an AudioTrack exactly like a decoded track.
 */
public class RawPcmReader {

    public String container;
    public int sampleRate;
    public int channels;
    public int bitDepth;
    public long frames;
    public long dataOffset;
    public long dataBytes;
    public long durationMs;

    private RandomAccessFile mRaf;
    private final byte[] mScratch = new byte[1 << 16];
    private long mRead;

    private static final int S16 = 0, S24 = 1, S32 = 2, F32 = 3;
    private int mKind;
    private boolean mBigEndian;

    /** returns null when the file is not a container we handle ourselves */
    public static RawPcmReader open(File f) {
        try {
            RawPcmReader r = new RawPcmReader();
            r.mRaf = new RandomAccessFile(f, "r");
            byte[] magic = new byte[4];
            r.mRaf.readFully(magic);
            String m = new String(magic, "US-ASCII");
            boolean ok = m.equals("FORM") ? r.parseAiff() : (m.equals("RIFF") || m.equals("RF64")) && r.parseWav();
            if (!ok) {
                r.close();
                return null;
            }
            r.durationMs = r.sampleRate > 0 ? r.frames * 1000L / r.sampleRate : 0;
            r.seekToFrame(0);
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean parseAiff() throws IOException {
        container = "aiff";
        mBigEndian = true;
        mRaf.seek(8);
        byte[] id = new byte[4];
        while (mRaf.getFilePointer() < mRaf.length() - 8) {
            mRaf.readFully(id);
            String chunk = new String(id, "US-ASCII");
            long size = readIntBE() & 0xFFFFFFFFL;
            long next = mRaf.getFilePointer() + size + (size & 1);
            if (chunk.equals("COMM")) {
                channels = readShortBE();
                frames = readIntBE() & 0xFFFFFFFFL;
                bitDepth = readShortBE();
                byte[] ext = new byte[10];
                mRaf.readFully(ext);
                sampleRate = (int) FormatProbe.extended80ToDouble(ext);
            } else if (chunk.equals("SSND")) {
                long offset = readIntBE() & 0xFFFFFFFFL;
                readIntBE();                       // block size
                dataOffset = mRaf.getFilePointer() + offset;
                dataBytes = size - 8 - offset;
                mKind = bitDepth == 16 ? S16 : (bitDepth == 24 ? S24 : S32);
                return channels > 0 && sampleRate > 0;
            }
            mRaf.seek(next);
        }
        return false;
    }

    private boolean parseWav() throws IOException {
        container = "wav";
        mBigEndian = false;
        mRaf.seek(12);
        byte[] id = new byte[4];
        boolean floatFormat = false;
        while (mRaf.getFilePointer() < mRaf.length() - 8) {
            mRaf.readFully(id);
            String chunk = new String(id, "US-ASCII");
            long size = readIntLE() & 0xFFFFFFFFL;
            long next = mRaf.getFilePointer() + size + (size & 1);
            if (chunk.equals("fmt ")) {
                int tag = readShortLE();
                channels = readShortLE();
                sampleRate = readIntLE();
                readIntLE();
                readShortLE();
                bitDepth = readShortLE();
                if (tag == 3) floatFormat = true;
                if (tag == 0xFFFE && size >= 40) {
                    mRaf.seek(mRaf.getFilePointer() + 8);
                    byte[] guid = new byte[16];
                    mRaf.readFully(guid);
                    if (guid[0] == 3) floatFormat = true;
                }
            } else if (chunk.equals("data")) {
                dataOffset = mRaf.getFilePointer();
                dataBytes = size;
                if (dataBytes == 0xFFFFFFFFL) dataBytes = mRaf.length() - dataOffset;
                mKind = floatFormat ? F32
                        : (bitDepth == 32 ? S32 : (bitDepth == 24 ? S24 : S16));
                if (frames == 0) {
                    frames = dataBytes / Math.max(1, channels * (bitDepth / 8));
                }
                return channels > 0 && sampleRate > 0;
            }
            mRaf.seek(next);
        }
        return false;
    }

    public void seekToFrame(long frame) throws IOException {
        long bytes = frame * channels * (bitDepth / 8L);
        mRaf.seek(dataOffset + Math.min(bytes, dataBytes));
        mRead = frame;
    }

    /**
     * Read up to `frames` frames into `out` (interleaved float).
     * Returns the number of frames actually read; -1 at end of stream.
     */
    public int read(float[] out, int frames) throws IOException {
        int channelsL = channels;
        int bps = bitDepth / 8;
        int maxFrames = Math.min(frames, out.length / channelsL);
        long remaining = (dataBytes - (mRead - 0) * channelsL * bps);
        if (remaining <= 0) return -1;
        maxFrames = (int) Math.min(maxFrames, remaining / (channelsL * bps));
        if (maxFrames <= 0) return -1;
        int need = maxFrames * channelsL * bps;
        if (mScratch.length < need) {
            // read in chunks instead of growing the scratch buffer
            maxFrames = mScratch.length / (channelsL * bps);
            need = maxFrames * channelsL * bps;
        }
        mRaf.readFully(mScratch, 0, need);
        mRead += maxFrames;
        int samples = maxFrames * channelsL;
        switch (mKind) {
            case S16:
                for (int i = 0; i < samples; i++) {
                    int b0 = mScratch[i * 2] & 0xFF, b1 = mScratch[i * 2 + 1] & 0xFF;
                    int v = mBigEndian ? ((b0 << 8) | b1) : ((b1 << 8) | b0);
                    if (v > 32767) v -= 65536;
                    out[i] = v * (1f / 32768f);
                }
                break;
            case S24:
                for (int i = 0; i < samples; i++) {
                    int b0 = mScratch[i * 3] & 0xFF, b1 = mScratch[i * 3 + 1] & 0xFF,
                            b2 = mScratch[i * 3 + 2] & 0xFF;
                    int v = mBigEndian ? ((b0 << 16) | (b1 << 8) | b2) : ((b2 << 16) | (b1 << 8) | b0);
                    if ((v & 0x800000) != 0) v -= 0x1000000;
                    out[i] = v * (1f / 8388608f);
                }
                break;
            case S32:
                for (int i = 0; i < samples; i++) {
                    int b0 = mScratch[i * 4] & 0xFF, b1 = mScratch[i * 4 + 1] & 0xFF,
                            b2 = mScratch[i * 4 + 2] & 0xFF, b3 = mScratch[i * 4 + 3] & 0xFF;
                    int v = mBigEndian ? ((b0 << 24) | (b1 << 16) | (b2 << 8) | b3)
                            : ((b3 << 24) | (b2 << 16) | (b1 << 8) | b0);
                    out[i] = (float) (v / 2147483648.0);
                }
                break;
            default:
                for (int i = 0; i < samples; i++) {
                    int b0 = mScratch[i * 4] & 0xFF, b1 = mScratch[i * 4 + 1] & 0xFF,
                            b2 = mScratch[i * 4 + 2] & 0xFF, b3 = mScratch[i * 4 + 3] & 0xFF;
                    int v = mBigEndian ? ((b0 << 24) | (b1 << 16) | (b2 << 8) | b3)
                            : ((b3 << 24) | (b2 << 16) | (b1 << 8) | b0);
                    out[i] = Float.intBitsToFloat(v);
                }
                break;
        }
        return maxFrames;
    }

    public void close() {
        try {
            if (mRaf != null) mRaf.close();
        } catch (IOException ignored) {
        }
        mRaf = null;
    }

    private int readShortLE() throws IOException {
        int a = mRaf.read(), b = mRaf.read();
        return (b << 8) | a;
    }

    private int readShortBE() throws IOException {
        int a = mRaf.read(), b = mRaf.read();
        return (a << 8) | b;
    }

    private int readIntLE() throws IOException {
        int a = mRaf.read(), b = mRaf.read(), c = mRaf.read(), d = mRaf.read();
        return (d << 24) | (c << 16) | (b << 8) | a;
    }

    private int readIntBE() throws IOException {
        int a = mRaf.read(), b = mRaf.read(), c = mRaf.read(), d = mRaf.read();
        return (a << 24) | (b << 16) | (c << 8) | d;
    }
}
