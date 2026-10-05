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
 *
 * It also reads the WAV/AIFF corners the platform extractors mangle or refuse:
 * 8-bit unsigned PCM, plain 32-bit ints, IEEE float, 20-in-24 bit EXTENSIBLE
 * files, RF64 whose sizes live in ds64 (with the data size left at -1), AIFF-C
 * sowt/fl32, odd-sized chunks and pad bytes.  Anything it cannot hand to an
 * AudioTrack - mu-law, a-law, ADPCM, 64-bit float, ima4 - returns null so the
 * MediaPlayer/MediaCodec path gets its turn instead of us playing noise.
 * tools/test/ReaderCheck.java exercises all of that against files built by
 * tools/test/gen_foreign.py.
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

    private static final int S8 = 0, U8 = 1, S16 = 2, S24 = 3, S32 = 4, F32 = 5;
    private int mKind;
    private boolean mBigEndian;

    /** sample formats we can hand to AudioTrack; anything else goes to the platform */
    static boolean supportedDepth(int bits) {
        return bits == 8 || bits == 16 || bits == 24 || bits == 32;
    }

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
        byte[] form = new byte[4];
        mRaf.readFully(form);
        String type = new String(form, "US-ASCII");
        boolean compressed = type.equals("AIFC");          // AIFF-C carries a codec tag
        if (!compressed && !type.equals("AIFF")) return false;   // e.g. an IFF image
        mRaf.seek(12);
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
                if (channels <= 0) return false;
                byte[] ext = new byte[10];
                mRaf.readFully(ext);
                sampleRate = (int) FormatProbe.extended80ToDouble(ext);
                if (compressed) {
                    // AIFF-C: the compression type follows the 80-bit sample rate
                    if (size < 22) return false;
                    byte[] ct = new byte[4];
                    mRaf.readFully(ct);
                    String codec = new String(ct, "US-ASCII");
                    if (codec.equals("sowt")) {
                        mBigEndian = false;               // little-endian PCM (what Macs write)
                    } else if (codec.equals("fl32")) {
                        mKind = F32;                      // 32-bit IEEE float, big-endian
                        bitDepth = 32;
                    } else if (!codec.equals("NONE") && !codec.equals("none")) {
                        return false;                     // ima4/ulaw/alaw/...: not ours to decode
                    }
                }
                if (!supportedDepth(bitDepth)) return false;
            } else if (chunk.equals("SSND")) {
                long offset = readIntBE() & 0xFFFFFFFFL;
                readIntBE();                       // block size
                long start = mRaf.getFilePointer() + offset;
                dataOffset = start;
                dataBytes = Math.max(0, size - 8 - offset);
                if (mKind != F32) mKind = bitDepth == 8 ? S8 : (bitDepth == 16 ? S16 : (bitDepth == 24 ? S24 : S32));
                if (frames <= 0) frames = dataBytes / (channels * (bitDepth / 8L));
                return channels > 0 && sampleRate > 0;
            }
            mRaf.seek(next);
        }
        return false;
    }

    private boolean parseWav() throws IOException {
        container = "wav";
        mBigEndian = false;
        boolean floatFormat = false;
        boolean unsupported = false;
        long rf64Data = -1;
        mRaf.seek(12);
        byte[] id = new byte[4];
        while (mRaf.getFilePointer() < mRaf.length() - 8) {
            mRaf.readFully(id);
            String chunk = new String(id, "US-ASCII");
            long size = readIntLE() & 0xFFFFFFFFL;
            long next = mRaf.getFilePointer() + size + (size & 1);
            if (chunk.equals("ds64")) {
                // RF64 keeps real 64-bit sizes here; the data chunk size stays -1
                readLongLE();                      // riffSize
                rf64Data = readLongLE();           // dataSize
            } else if (chunk.equals("fmt ")) {
                int tag = readShortLE();
                channels = readShortLE();
                sampleRate = readIntLE();
                readIntLE();                       // byte rate
                readShortLE();                     // block align
                bitDepth = readShortLE();
                if (tag == 3) {
                    floatFormat = true;
                } else if (tag == 0xFFFE) {
                    if (size < 40) return false;
                    mRaf.seek(mRaf.getFilePointer() + 8);      // cbSize, valid bits, channel mask
                    byte[] guid = new byte[16];
                    mRaf.readFully(guid);
                    if (guid[0] == 3) floatFormat = true;
                    else if (guid[0] != 1) unsupported = true; // not PCM inside the wrapper
                } else if (tag != 1) {
                    unsupported = true;            // adpcm / a-law / mu-law: let MediaPlayer try
                }
                if (unsupported) return false;
                if (channels <= 0 || !supportedDepth(bitDepth)) return false;
                if (floatFormat && bitDepth != 32) return false;
            } else if (chunk.equals("data")) {
                dataOffset = mRaf.getFilePointer();
                dataBytes = size;
                if (dataBytes == 0xFFFFFFFFL) {
                    dataBytes = rf64Data >= 0 ? Math.min(rf64Data, mRaf.length() - dataOffset)
                            : mRaf.length() - dataOffset;
                }
                mKind = floatFormat ? F32
                        : (bitDepth == 32 ? S32 : (bitDepth == 24 ? S24
                        : (bitDepth == 16 ? S16 : U8)));
                if (frames <= 0) {
                    frames = dataBytes / (channels * (bitDepth / 8L));
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
            case S8:
                for (int i = 0; i < samples; i++) {
                    out[i] = mScratch[i] * (1f / 128f);
                }
                break;
            case U8:
                for (int i = 0; i < samples; i++) {
                    out[i] = ((mScratch[i] & 0xFF) - 128) * (1f / 128f);
                }
                break;
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

    private long readLongLE() throws IOException {
        long lo = readIntLE() & 0xFFFFFFFFL, hi = readIntLE() & 0xFFFFFFFFL;
        return (hi << 32) | lo;
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
