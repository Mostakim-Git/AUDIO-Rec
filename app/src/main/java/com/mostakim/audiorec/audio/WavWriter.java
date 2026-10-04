package com.mostakim.audiorec.audio;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Random;

/**
 * WAV writer: canonical RIFF/WAVE with PCM (16/24/32-bit) or IEEE float, and
 * automatic RF64 when a take would pass the 4 GiB RIFF limit - which at
 * 8 channels / 24-bit / 192 kHz happens after ~19 minutes.
 *
 *   ≤ 2 channels   classic 16-byte `fmt ` chunk (tag 1) or tag 3 for float
 *   > 2 channels   WAVE_FORMAT_EXTENSIBLE (0xFFFE) with a channel mask, so
 *                  the file opens correctly in DAWs that insist on it
 */
public class WavWriter implements AudioSink {

    private File mFile;
    private BufferedOutputStream mOut;
    private RandomAccessFile mPatch;

    private int mSampleRate, mChannels, mBitDepth;
    private boolean mFloat, mExtensible, mRf64;
    private long mDataOffset;          // byte offset where sample data starts
    private long mFrames;
    private long mDataBytes;
    private byte[] mScratch = new byte[0];
    private final Random mRnd = new Random(0xC0FFEE);
    private boolean mDither;

    /** enable TPDF dither when truncating to 16 bits */
    public void setDither(boolean d) {
        mDither = d;
    }

    public static boolean needsRf64(int channels, int bitDepth, int sampleRate) {
        double bytesPerSecond = (double) channels * sampleRate * bitDepth / 8.0;
        return bytesPerSecond * 3600 * 2 > 3.5e9;   // > 3.5 GB in a 2-hour take
    }

    @Override
    public void open(File file, int sampleRate, int channels, int bitDepth) throws IOException {
        mFile = file;
        mSampleRate = sampleRate;
        mChannels = channels;
        mBitDepth = bitDepth;
        // 32-bit WAV is written as IEEE float - that is what every DAW expects,
        // and it is the only 32-bit payload the platform guarantees.
        mFloat = bitDepth == 32;
        mExtensible = channels > 2;
        mRf64 = needsRf64(channels, bitDepth, sampleRate);
        mFrames = 0;
        mDataBytes = 0;

        mOut = new BufferedOutputStream(new FileOutputStream(file), 1 << 16);
        writeHeader();
    }

    private void writeHeader() throws IOException {
        int bits = mBitDepth;
        int blockAlign = mChannels * (bits / 8);
        long byteRate = (long) mSampleRate * blockAlign;

        if (mRf64) {
            // RF64 header; sizes patched at close
            write("RF64");
            writeInt(0xFFFFFFFF);            // stays -1: RF64 marks it as "see ds64"
            write("WAVE");
            write("ds64");
            writeInt(28);
            writeLong(0);                    // riffSize  (patched)
            writeLong(0);                    // dataSize  (patched)
            writeLong(0);                    // sampleCount (patched)
            writeInt(0);                     // table length
        } else {
            write("RIFF");
            writeInt(0);                     // patched
            write("WAVE");
        }

        int fmtSize = mExtensible ? 40 : 16;
        write("fmt ");
        writeInt(fmtSize);
        writeShort(mExtensible ? 0xFFFE : (mFloat ? 3 : 1));
        writeShort(mChannels);
        writeInt(mSampleRate);
        writeInt((int) byteRate);
        writeShort(blockAlign);
        writeShort(bits);
        if (mExtensible) {
            writeShort(22);                            // cbSize
            writeShort(bits);                          // valid bits per sample
            writeInt(channelMask(mChannels));          // dwChannelMask
            // KSDATAFORMAT_SUBTYPE_PCM / _IEEE_FLOAT
            byte[] guid = new byte[]{
                    0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10, 0x00,
                    (byte) 0x80, 0x00, 0x00, (byte) 0xAA, 0x00, 0x38, (byte) 0x9B, 0x71};
            if (mFloat) guid[0] = 0x03;
            mOut.write(guid);
            mOffset += guid.length;
        }

        write("data");
        writeInt(mRf64 ? 0xFFFFFFFF : 0);   // patched at close
        mDataOffset = currentOffset();
    }

    /** BufferedOutputStream has no tell(), so the byte count is tracked here. */
    private long currentOffset() {
        return mOffset;
    }

    private long mOffset = 0;

    /** standard channel masks for the layouts we can capture */
    public static int channelMask(int channels) {
        switch (channels) {
            case 1: return 0x4;                      // SPEAKER_FRONT_CENTER
            case 2: return 0x3;                      // FL | FR
            case 3: return 0x7;
            case 4: return 0x33;                     // FL FR BL BR (quad)
            case 6: return 0x3F;                     // 5.1
            case 8: return 0x63F;                    // 7.1
            default: return 0x0;                     // unspecified
        }
    }

    @Override
    public void write(float[] interleaved, int samples) throws IOException {
        if (samples <= 0) return;
        if (mScratch.length < samples * 4) mScratch = new byte[samples * 4];
        int bytes;
        switch (mBitDepth) {
            case 16:
                write16(interleaved, samples);
                bytes = samples * 2;
                break;
            case 24:
                Pcm.floatToInt24(interleaved, samples, mScratch, mDither, mRnd);
                bytes = samples * 3;
                break;
            default:
                if (mFloat) {
                    bytes = floatBytes(interleaved, samples);
                } else {
                    Pcm.floatToInt32(interleaved, samples, mScratch, mDither, mRnd);
                    bytes = samples * 4;
                }
                break;
        }
        mOut.write(mScratch, 0, bytes);
        mDataBytes += bytes;
        mOffset += bytes;
        mFrames += samples / mChannels;
    }

    private void write16(float[] in, int samples) throws IOException {
        for (int i = 0; i < samples; i++) {
            double s = in[i] * 32768.0 + (mDither ? Pcm.tpdf(mRnd) : 0);
            int v = Pcm.clampShort((int) Math.rint(s));
            mScratch[i * 2] = (byte) (v & 0xFF);
            mScratch[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
    }

    private int floatBytes(float[] in, int samples) {
        for (int i = 0; i < samples; i++) {
            int v = Float.floatToIntBits(in[i]);
            mScratch[i * 4] = (byte) (v & 0xFF);
            mScratch[i * 4 + 1] = (byte) ((v >> 8) & 0xFF);
            mScratch[i * 4 + 2] = (byte) ((v >> 16) & 0xFF);
            mScratch[i * 4 + 3] = (byte) ((v >> 24) & 0xFF);
        }
        return samples * 4;
    }

    @Override
    public void close(long frames) throws IOException {
        if (mOut != null) {
            mOut.flush();
            mOut.close();
            mOut = null;
        }
        if (frames > 0) mFrames = frames;
        patchHeader();
    }

    private void patchHeader() {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(mFile, "rw");
            long dataBytes = mDataOffset > 0 ? (mFile.length() - mDataOffset) : mDataBytes;
            long riffSize = mFile.length() - 8;
            if (mRf64) {
                // layout: RF64 | 0xFFFFFFFF | WAVE | ds64 | 28 | riffSize(8) |
                //         dataSize(8) | sampleCount(8) | tableLen(4) | ...
                raf.seek(4);
                raf.write(intLE(0xFFFFFFFF));       // RF64 keeps this marker
                raf.seek(20);
                raf.write(longLE(riffSize));
                raf.write(longLE(dataBytes));
                raf.write(longLE(mFrames));
                // the 'data' chunk size stays 0xFFFFFFFF; readers use ds64
            } else {
                raf.seek(4);
                raf.write(intLE((int) riffSize));
                raf.seek(dataSizeOffset());
                raf.write(intLE((int) dataBytes));
            }
        } catch (Exception ignored) {
        } finally {
            if (raf != null) try {
                raf.close();
            } catch (IOException ignored) {
            }
        }
        mOffset = 0;
    }

    /** byte offset of the `data` chunk's size field */
    private long dataSizeOffset() {
        long fmtSize = mExtensible ? 40 : 16;
        long base = mRf64 ? (12 + 8 + 28) : 12;      // RF64 carries a 36-byte ds64 chunk
        return base + 8 + fmtSize + 4;
    }

    @Override
    public long bytesWritten() {
        return mOffset;
    }

    @Override
    public File file() {
        return mFile;
    }

    @Override
    public String container() {
        return "wav";
    }

    @Override
    public String stats() {
        return (mRf64 ? "RF64" : "RIFF") + (mFloat ? " float" : " PCM");
    }

    // ------------------------------------------------------------ plumbing --
    private void write(String s) throws IOException {
        byte[] b = s.getBytes("US-ASCII");
        mOut.write(b);
        mOffset += b.length;
    }

    private void writeInt(int v) throws IOException {
        mOut.write(intLE(v));
        mOffset += 4;
    }

    private void writeLong(long v) throws IOException {
        mOut.write(longLE(v));
        mOffset += 8;
    }

    private void writeShort(int v) throws IOException {
        mOut.write(v & 0xFF);
        mOut.write((v >> 8) & 0xFF);
        mOffset += 2;
    }

    static byte[] intLE(int v) {
        return new byte[]{(byte) (v & 0xFF), (byte) ((v >> 8) & 0xFF),
                (byte) ((v >> 16) & 0xFF), (byte) ((v >> 24) & 0xFF)};
    }

    static byte[] longLE(long v) {
        byte[] b = new byte[8];
        for (int i = 0; i < 8; i++) b[i] = (byte) ((v >> (8 * i)) & 0xFF);
        return b;
    }
}
