package com.mostakim.audiorec.audio;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Random;

/**
 * AIFF writer (big-endian PCM, Apple/SGI extended sample rate).
 * Kept deliberately simple: 16- and 24-bit integer PCM, which is what the
 * format is actually used for in broadcast workflows.
 */
public class AiffWriter implements AudioSink {

    private File mFile;
    private BufferedOutputStream mOut;
    private RandomAccessFile mPatch;

    private int mSampleRate, mChannels, mBitDepth;
    private long mFrames, mDataBytes, mOffset, mDataOffset, mCommFramesOffset, mSsndSizeOffset;
    private byte[] mScratch = new byte[0];
    private final Random mRnd = new Random(0xBEEF);
    private boolean mDither;

    public void setDither(boolean d) {
        mDither = d;
    }

    @Override
    public void open(File file, int sampleRate, int channels, int bitDepth) throws IOException {
        mFile = file;
        mSampleRate = sampleRate;
        mChannels = channels;
        mBitDepth = bitDepth == 32 ? 24 : bitDepth;   // AIFF ships integer PCM here
        mFrames = 0;
        mDataBytes = 0;
        mOffset = 0;
        mOut = new BufferedOutputStream(new FileOutputStream(file), 1 << 16);
        writeHeader();
    }

    private void writeHeader() throws IOException {
        int blockAlign = mChannels * (mBitDepth / 8);
        write("FORM");
        writeInt(0);                       // patched
        write("AIFF");
        write("COMM");
        writeInt(18);
        writeShort(mChannels);
        mCommFramesOffset = mOffset;
        writeInt(0);                       // numSampleFrames, patched
        writeShort(mBitDepth);
        mOut.write(extended80(mSampleRate));
        mOffset += 10;
        write("SSND");
        mSsndSizeOffset = mOffset;
        writeInt(0);                       // patched
        writeInt(0);                       // offset
        writeInt(0);                       // blockSize
        mDataOffset = mOffset;
    }

    @Override
    public void write(float[] interleaved, int samples) throws IOException {
        if (samples <= 0) return;
        if (mScratch.length < samples * 3) mScratch = new byte[samples * 3];
        int bytes;
        if (mBitDepth == 16) {
            Pcm.floatToShortBigEndian(interleaved, samples, mScratch, mDither, mRnd);
            bytes = samples * 2;
        } else {
            Pcm.floatToInt24BE(interleaved, samples, mScratch, mDither, mRnd);
            bytes = samples * 3;
        }
        mOut.write(mScratch, 0, bytes);
        mDataBytes += bytes;
        mOffset += bytes;
        mFrames += samples / mChannels;
    }

    @Override
    public void close(long frames) throws IOException {
        if (mOut != null) {
            // AIFF chunks are padded to even lengths
            if ((mDataBytes & 1) == 1) {
                mOut.write(0);
                mOffset++;
            }
            mOut.flush();
            mOut.close();
            mOut = null;
        }
        if (frames > 0) mFrames = frames;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(mFile, "rw");
            raf.seek(4);
            raf.write(intBE((int) (mFile.length() - 8)));
            raf.seek(mCommFramesOffset);
            raf.write(intBE((int) mFrames));
            raf.seek(mSsndSizeOffset);
            raf.write(intBE((int) (mDataBytes + 8)));
        } catch (Exception ignored) {
        } finally {
            if (raf != null) try {
                raf.close();
            } catch (IOException ignored) {
            }
        }
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
        return "aiff";
    }

    @Override
    public String stats() {
        return "AIFF big-endian PCM";
    }

    /** 80-bit IEEE 754 extended float, as used for the AIFF sample rate */
    static byte[] extended80(int rate) {
        byte[] out = new byte[10];
        if (rate <= 0) return out;
        int exp = 16383 + 31;
        long mant = rate;
        while ((mant & 0x80000000L) == 0 && mant != 0) {
            mant <<= 1;
            exp--;
        }
        out[0] = (byte) ((exp >> 8) & 0xFF);
        out[1] = (byte) (exp & 0xFF);
        out[2] = (byte) ((mant >> 24) & 0xFF);
        out[3] = (byte) ((mant >> 16) & 0xFF);
        out[4] = (byte) ((mant >> 8) & 0xFF);
        out[5] = (byte) (mant & 0xFF);
        return out;
    }

    private void write(String s) throws IOException {
        byte[] b = s.getBytes("US-ASCII");
        mOut.write(b);
        mOffset += b.length;
    }

    private void writeInt(int v) throws IOException {
        mOut.write(intBE(v));
        mOffset += 4;
    }

    private void writeShort(int v) throws IOException {
        mOut.write((v >> 8) & 0xFF);
        mOut.write(v & 0xFF);
        mOffset += 2;
    }

    static byte[] intBE(int v) {
        return new byte[]{(byte) ((v >> 24) & 0xFF), (byte) ((v >> 16) & 0xFF),
                (byte) ((v >> 8) & 0xFF), (byte) (v & 0xFF)};
    }
}
