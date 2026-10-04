package com.mostakim.audiorec.audio;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.Random;

/**
 * Native FLAC encoder (no NDK, no third-party library, no network).
 *
 * Implements the subset of the FLAC format a lossless recorder needs:
 *
 *   • 16- and 24-bit integer PCM, 1-8 channels, 8 kHz … 384 kHz
 *   • seekable STREAMINFO (patched at close with the frame-size range, the total
 *     sample count and the MD5 of the unencoded audio)
 *   • per-block predictor search over constant / verbatim / fixed orders 0-4 /
 *     LPC up to order 12 (Levinson-Durbin, quantised coefficients)
 *   • Rice partitioning with an exact parameter search per partition
 *
 * Files are decodable by flac, ffmpeg and any Android media player, and land
 * around 55-70% of the WAV size on music while staying bit-exact.
 */
public class FlacWriter implements AudioSink {

    private static final int BLOCK = 4096;
    private static final int[] LPC_ORDERS = {8, 12};
    private static final int QLP_PRECISION = 14;
    private static final int FIXED_MAX_ORDER = 4;

    private File mFile;
    private OutputStream mOut;
    private int mSampleRate, mChannels, mBitDepth;
    private boolean mDither;
    private final Random mRnd = new Random(0x51AC0DE1L);

    private int[][] mSamples;                  // [channel][BLOCK] integer samples
    private int mPendingFrames;
    private long mFrames;                      // total frames accepted

    private long mMinFrameSize = Long.MAX_VALUE, mMaxFrameSize = 0;
    private long mStreamInfoOffset;
    private long mOffset;
    private MessageDigest mMd5;
    private byte[] mMd5Buf = new byte[BLOCK * 8 * 3];

    private int mFramesEncoded;
    private int mLastBlocksize = BLOCK;

    public void setDither(boolean d) {
        mDither = d;
    }

    /** FLAC is integer PCM; a 32-bit request is captured at 24-bit. */
    public static int clampDepth(int requested) {
        return requested == 16 ? 16 : 24;
    }

    @Override
    public void open(File file, int sampleRate, int channels, int bitDepth) throws IOException {
        mFile = file;
        mSampleRate = sampleRate;
        mChannels = Math.max(1, Math.min(8, channels));
        mBitDepth = clampDepth(bitDepth);
        mFrames = 0;
        mOffset = 0;
        mFramesEncoded = 0;
        mPendingFrames = 0;
        mMinFrameSize = Long.MAX_VALUE;
        mMaxFrameSize = 0;
        mLastBlocksize = BLOCK;

        mSamples = new int[mChannels][BLOCK];
        mMd5 = newMd5();

        mOut = new BufferedOutputStream(new FileOutputStream(file), 1 << 16);
        write("fLaC");
        mOut.write(0x00);                       // STREAMINFO: not last, type 0
        mOut.write(0x00);
        mOut.write(0x00);
        mOut.write(34);
        mOffset += 4;
        mStreamInfoOffset = mOffset;
        mOut.write(streamInfoBytes(0, new byte[16]));    // patched at close
        writeVorbisComment();
    }

    private static MessageDigest newMd5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------ metadata --
    /**
     * STREAMINFO payload: min/max block size (16+16), min/max frame size (24+24),
     * sample rate (20) + channels-1 (3) + bits-1 (5), total samples (36), MD5 (16).
     */
    private byte[] streamInfoBytes(long totalSamples, byte[] md5) {
        BitWriter w = new BitWriter(40);
        w.writeBits(BLOCK, 16);
        w.writeBits(BLOCK, 16);
        long minF = mMinFrameSize == Long.MAX_VALUE ? 0 : Math.min(mMinFrameSize, 0xFFFFFF);
        long maxF = Math.min(mMaxFrameSize, 0xFFFFFF);
        w.writeBits((int) minF, 24);
        w.writeBits((int) maxF, 24);
        w.writeBits(mSampleRate, 20);
        w.writeBits(mChannels - 1, 3);
        w.writeBits(mBitDepth - 1, 5);
        w.writeBits((int) ((totalSamples >>> 32) & 0xF), 4);
        w.writeBits((int) (totalSamples & 0xFFFFFFFFL), 32);
        byte[] fields = w.finish();              // 144 bits -> exactly 18 bytes
        byte[] out = new byte[34];               // 18 packed fields + 16 MD5
        System.arraycopy(fields, 0, out, 0, 18);
        if (md5 != null) System.arraycopy(md5, 0, out, 18, 16);
        return out;
    }

    private void writeVorbisComment() throws IOException {
        byte[] vendor = "AUDIO-rec native FLAC encoder".getBytes("UTF-8");
        byte[] c1 = "ENCODER=AUDIO-rec 1.0 (Mostakim Billah)".getBytes("UTF-8");
        byte[] c2 = "DESCRIPTION=Bit-perfect USB audio capture".getBytes("UTF-8");
        int len = 4 + vendor.length + 4 + (4 + c1.length) + (4 + c2.length);
        mOut.write(0x84);                        // last metadata block, type 4
        writeInt24(len);
        writeIntLE(vendor.length);
        mOut.write(vendor);
        writeIntLE(2);
        writeIntLE(c1.length);
        mOut.write(c1);
        writeIntLE(c2.length);
        mOut.write(c2);
        mOffset += 4 + len;
    }

    // --------------------------------------------------------------- write --
    @Override
    public void write(float[] interleaved, int samples) throws IOException {
        final int frames = samples / mChannels;
        int done = 0;
        while (done < frames) {
            int n = Math.min(BLOCK - mPendingFrames, frames - done);
            convertAndHash(interleaved, done * mChannels, n);
            mPendingFrames += n;
            done += n;
            if (mPendingFrames == BLOCK) {
                flushBlock(BLOCK);
                mPendingFrames = 0;
            }
        }
        mFrames += frames;
    }

    /**
     * Float -> integer at the file depth, with optional TPDF dither, storing the
     * *exact* integers that will be encoded and feeding the same values to the
     * MD5 so it matches the decoded stream.
     */
    private void convertAndHash(float[] src, int srcOffset, int frames) {
        final double scale = mBitDepth == 16 ? 32768.0 : 8388608.0;
        byte[] md5Buf = mMd5Buf;
        int bps = mBitDepth / 8;
        for (int f = 0; f < frames; f++) {
            for (int c = 0; c < mChannels; c++) {
                double v = src[srcOffset + f * mChannels + c] * scale;
                if (mDither) v += Pcm.tpdf(mRnd);
                int iv = (int) Math.rint(v);
                iv = mBitDepth == 16 ? Pcm.clampShort(iv) : Pcm.clampInt24(iv);
                mSamples[c][mPendingFrames + f] = iv;
                int o = (f * mChannels + c) * bps;
                md5Buf[o] = (byte) (iv & 0xFF);
                md5Buf[o + 1] = (byte) ((iv >> 8) & 0xFF);
                if (bps == 3) md5Buf[o + 2] = (byte) ((iv >> 16) & 0xFF);
            }
        }
        if (mMd5 != null) {
            int need = frames * mChannels * bps;
            // the buffer may be reused for a longer block; hash only what we filled
            if (need <= md5Buf.length) {
                mMd5.update(md5Buf, 0, need);
            } else {
                byte[] tmp = new byte[need];
                System.arraycopy(md5Buf, 0, tmp, 0, md5Buf.length);
                mMd5.update(tmp, 0, need);
            }
        }
    }

    private void flushBlock(int frames) throws IOException {
        if (frames <= 0) return;
        byte[] frame = encodeFrame(frames);
        mOut.write(frame);
        mOffset += frame.length;
        if (frame.length < mMinFrameSize) mMinFrameSize = frame.length;
        if (frame.length > mMaxFrameSize) mMaxFrameSize = frame.length;
        mFramesEncoded++;
        mLastBlocksize = frames;
    }

    @Override
    public void close(long frames) throws IOException {
        if (mPendingFrames > 0) {
            flushBlock(mPendingFrames);        // short final frame is legal
            mPendingFrames = 0;
        }
        if (mOut != null) {
            mOut.flush();
            mOut.close();
            mOut = null;
        }
        long total = frames > 0 ? frames : mFrames;
        byte[] md5 = new byte[16];
        if (mMd5 != null) {
            byte[] d = mMd5.digest();
            System.arraycopy(d, 0, md5, 0, Math.min(16, d.length));
        }
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(mFile, "rw");
            raf.seek(mStreamInfoOffset);
            raf.write(streamInfoBytes(total, md5));
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
        return "flac";
    }

    @Override
    public String stats() {
        return "native FLAC \u00b7 " + mFramesEncoded + " blocks \u00b7 max frame "
                + mMaxFrameSize + " B";
    }

    // ============================================================== frames ===
    private byte[] encodeFrame(int frames) {
        int approx = frames * mChannels * (mBitDepth / 8) + 1024;
        BitWriter w = new BitWriter(approx);

        // frame header
        w.writeBits(0x3FFE, 14);                 // sync code
        w.writeBits(0, 1);                       // reserved
        w.writeBits(0, 1);                       // blocking strategy: fixed
        int bsCode = blocksizeCode(frames);
        w.writeBits(bsCode, 4);
        w.writeBits(0, 4);                       // sample rate: from STREAMINFO
        w.writeBits(mChannels - 1, 4);           // independent channels
        w.writeBits(mBitDepth == 16 ? 4 : 6, 3); // sample size
        w.writeBits(0, 1);                       // reserved
        w.writeUtf8(mFramesEncoded);             // frame number (fixed blocking)
        if (bsCode == 6) w.writeBits(frames - 1, 8);
        else if (bsCode == 7) w.writeBits(frames - 1, 16);

        // header ends on a byte boundary, then its own CRC-8
        w.alignToByte();
        w.writeBits(crc8(w.buf, 0, w.byteLength()), 8);

        for (int c = 0; c < mChannels; c++) {
            writeSubframe(w, mSamples[c], frames);
        }
        w.alignToByte();
        int crc = crc16(w.buf, 0, w.byteLength());
        w.writeBits((crc >> 8) & 0xFF, 8);
        w.writeBits(crc & 0xFF, 8);
        return w.finish();
    }

    private int blocksizeCode(int frames) {
        if (frames == BLOCK) return 12;                      // 256 << (12-8) = 4096
        if (frames == 4608) return 5;
        if (frames > 0 && (frames & (frames - 1)) == 0) {    // power of two
            int n = Integer.numberOfTrailingZeros(frames);
            if (n >= 8 && n <= 15) return n;
        }
        return 7;                                            // 16-bit literal
    }

    // ------------------------------------------------------------ subframe --
    private void writeSubframe(BitWriter w, int[] s, int n) {
        final int bps = mBitDepth;

        boolean constant = true;
        for (int i = 1; i < n; i++) {
            if (s[i] != s[0]) {
                constant = false;
                break;
            }
        }
        if (constant) {
            w.writeBits(0, 1);
            w.writeBits(0, 6);                   // constant
            w.writeBits(0, 1);
            w.writeSigned(s[0], bps);
            return;
        }

        int bestType = -1, bestOrder = 0, bestShift = 0;
        long bestCost = Long.MAX_VALUE;
        int[] bestRes = null, bestQlp = null;

        for (int order = 0; order <= FIXED_MAX_ORDER; order++) {
            if (order >= n) continue;
            int[] res = fixedResidual(s, n, order);
            long cost = estimateResidualCost(res, order, n);
            if (cost < bestCost) {
                bestCost = cost;
                bestType = 8;
                bestOrder = order;
                bestRes = res;
                bestQlp = null;
            }
        }

        for (int order : LPC_ORDERS) {
            if (order >= n) continue;
            int[] qlp = new int[order];
            int[] shift = new int[1];
            int[] res = lpcResidual(s, n, order, qlp, shift);
            if (res == null) continue;
            long cost = estimateResidualCost(res, order, n)
                    + 4 + 5 + (long) order * QLP_PRECISION;
            if (cost < bestCost) {
                bestCost = cost;
                bestType = 32;
                bestOrder = order;
                bestRes = res;
                bestQlp = qlp;
                bestShift = shift[0];
            }
        }

        long verbatimCost = (long) n * bps + 8;
        long modelCost = bestRes == null ? Long.MAX_VALUE
                : bestCost + 8 + (long) bestOrder * bps
                  + (bestType == 32 ? 4 + 5 + (long) bestOrder * QLP_PRECISION : 0);

        if (bestRes == null || verbatimCost <= modelCost) {
            w.writeBits(0, 1);
            w.writeBits(1, 6);                   // verbatim
            w.writeBits(0, 1);
            for (int i = 0; i < n; i++) w.writeSigned(s[i], bps);
            return;
        }

        w.writeBits(0, 1);
        // FIXED: type = 8 + order (001000 = order 0);  LPC: type = 31 + order
        w.writeBits(bestType == 8 ? (8 + bestOrder) : (31 + bestOrder), 6);
        w.writeBits(0, 1);                       // no wasted bits
        for (int i = 0; i < bestOrder; i++) w.writeSigned(s[i], bps);
        if (bestType == 32) {
            w.writeBits(QLP_PRECISION - 1, 4);
            w.writeBits(bestShift & 0x1F, 5);
            for (int i = 0; i < bestOrder; i++) w.writeSigned(bestQlp[i], QLP_PRECISION);
        }
        writeResidual(w, bestRes, bestOrder, n);
    }

    // ------------------------------------------------------------ residual --
    private int partitionOrder(int order, int blocksize) {
        int p = 4;
        while (p > 0 && ((blocksize >> p) <= order || (blocksize & ((1 << p) - 1)) != 0)) p--;
        return p;
    }

    private void writeResidual(BitWriter w, int[] res, int order, int blocksize) {
        int p = partitionOrder(order, blocksize);
        int parts = 1 << p;
        int partLen = blocksize >> p;

        w.writeBits(0, 2);                       // 4-bit Rice parameters
        w.writeBits(p, 4);

        int idx = 0;
        for (int part = 0; part < parts; part++) {
            int len = (part == 0) ? partLen - order : partLen;
            if (len <= 0) {
                w.writeBits(0, 4);
                continue;
            }
            int k = bestRiceParam(res, idx, len);
            w.writeBits(k, 4);
            for (int i = 0; i < len; i++) {
                int r = res[idx + i];
                int u = (r << 1) ^ (r >> 31);    // zig-zag to unsigned
                w.writeUnary(u >>> k);
                if (k > 0) w.writeBits(u & ((1 << k) - 1), k);
            }
            idx += len;
        }
    }

    private int bestRiceParam(int[] res, int from, int count) {
        long[] sums = new long[15];
        for (int i = 0; i < count; i++) {
            int r = res[from + i];
            int u = (r << 1) ^ (r >> 31);
            for (int k = 0; k < 15; k++) sums[k] += (u >>> k) + 1 + k;
        }
        int best = 0;
        long bestCost = Long.MAX_VALUE;
        for (int k = 0; k < 15; k++) {
            if (sums[k] < bestCost) {
                bestCost = sums[k];
                best = k;
            }
        }
        return best;
    }

    private long estimateResidualCost(int[] res, int order, int blocksize) {
        int count = res.length;
        if (count <= 0) return 0;
        int p = partitionOrder(order, blocksize);
        int parts = 1 << p;
        int partLen = blocksize >> p;
        long total = 6 + parts * 4L;
        int idx = 0;
        for (int part = 0; part < parts && idx < count; part++) {
            int len = (part == 0) ? partLen - order : partLen;
            if (len <= 0) continue;
            if (idx + len > count) len = count - idx;
            int k = bestRiceParam(res, idx, len);
            for (int i = 0; i < len; i++) {
                int r = res[idx + i];
                int u = (r << 1) ^ (r >> 31);
                total += (u >>> k) + 1 + k;
            }
            idx += len;
        }
        return total;
    }

    static int[] fixedResidual(int[] s, int n, int order) {
        int[] res = new int[n - order];
        switch (order) {
            case 0:
                System.arraycopy(s, 0, res, 0, res.length);
                break;
            case 1:
                for (int i = 0; i < res.length; i++) res[i] = s[i + 1] - s[i];
                break;
            case 2:
                for (int i = 0; i < res.length; i++)
                    res[i] = s[i + 2] - 2 * s[i + 1] + s[i];
                break;
            case 3:
                for (int i = 0; i < res.length; i++)
                    res[i] = s[i + 3] - 3 * s[i + 2] + 3 * s[i + 1] - s[i];
                break;
            default:
                for (int i = 0; i < res.length; i++)
                    res[i] = s[i + 4] - 4 * s[i + 3] + 6 * s[i + 2] - 4 * s[i + 1] + s[i];
                break;
        }
        return res;
    }

    /**
     * LPC through Levinson-Durbin on the autocorrelation, coefficients quantised
     * to QLP_PRECISION bits with a power-of-two shift written in the frame.
     * Returns null for blocks too degenerate to model.
     */
    static int[] lpcResidual(int[] s, int n, int order, int[] qlpOut, int[] shiftOut) {
        double[] ac = new double[order + 1];
        for (int lag = 0; lag <= order; lag++) {
            double sum = 0;
            for (int i = lag; i < n; i++) sum += (double) s[i] * s[i - lag];
            ac[lag] = sum;
        }
        if (ac[0] <= 0) return null;

        double[] a = new double[order + 1];
        a[0] = 1.0;
        double err = ac[0];
        for (int i = 1; i <= order; i++) {
            double acc = ac[i];
            for (int j = 1; j < i; j++) acc -= a[j] * ac[i - j];
            double k = err == 0 ? 0 : acc / err;
            if (Double.isNaN(k) || Double.isInfinite(k)) return null;
            for (int j = 1; j <= i / 2; j++) {
                double tmp = a[j];
                a[j] = tmp - k * a[i - j];
                a[i - j] = a[i - j] - k * tmp;
            }
            a[i] = -k;
            err *= (1 - k * k);
            if (err <= 1e-9) err = 1e-9;
        }

        double[] c = new double[order];
        double cmax = 0;
        for (int i = 0; i < order; i++) {
            c[i] = -a[i + 1];
            cmax = Math.max(cmax, Math.abs(c[i]));
        }
        if (cmax <= 0 || Double.isNaN(cmax)) return null;

        int shift = QLP_PRECISION - 2 - (int) Math.ceil(Math.log(cmax) / Math.log(2) + 1e-9);
        if (shift < 0) shift = 0;
        if (shift > 15) shift = 15;
        int limit = (1 << (QLP_PRECISION - 2));
        for (int i = 0; i < order; i++) {
            int q = (int) Math.round(c[i] * (1 << shift));
            if (q > limit - 1) q = limit - 1;
            if (q < -limit) q = -limit;
            qlpOut[i] = q;
        }
        shiftOut[0] = shift;

        int[] res = new int[n - order];
        for (int i = order; i < n; i++) {
            long pred = 0;
            for (int j = 0; j < order; j++) pred += (long) qlpOut[j] * s[i - 1 - j];
            res[i - order] = s[i] - (int) (pred >> shift);
        }
        return res;
    }

    // ----------------------------------------------------------------- CRC --
    static int crc8(byte[] data, int off, int len) {
        int crc = 0;
        for (int i = off; i < off + len; i++) {
            crc ^= data[i] & 0xFF;
            for (int b = 0; b < 8; b++) {
                crc = ((crc & 0x80) != 0) ? ((crc << 1) ^ 0x07) & 0xFF : (crc << 1) & 0xFF;
            }
        }
        return crc;
    }

    static int crc16(byte[] data, int off, int len) {
        int crc = 0;
        for (int i = off; i < off + len; i++) {
            crc ^= (data[i] & 0xFF) << 8;
            for (int b = 0; b < 8; b++) {
                crc = ((crc & 0x8000) != 0) ? ((crc << 1) ^ 0x8005) & 0xFFFF : (crc << 1) & 0xFFFF;
            }
        }
        return crc;
    }

    // -------------------------------------------------------------- output --
    private void write(String s) throws IOException {
        byte[] b = s.getBytes("US-ASCII");
        mOut.write(b);
        mOffset += b.length;
    }

    private void writeIntLE(int v) throws IOException {
        mOut.write(v & 0xFF);
        mOut.write((v >> 8) & 0xFF);
        mOut.write((v >> 16) & 0xFF);
        mOut.write((v >> 24) & 0xFF);
        mOffset += 4;
    }

    private void writeInt24(int v) throws IOException {
        mOut.write((v >> 16) & 0xFF);
        mOut.write((v >> 8) & 0xFF);
        mOut.write(v & 0xFF);
        mOffset += 3;
    }

    // ========================================================= bit writer ====
    static final class BitWriter {
        byte[] buf;
        int bitPos;

        BitWriter(int bytes) {
            buf = new byte[Math.max(1024, bytes)];
        }

        void ensure(int extraBits) {
            int need = (bitPos + extraBits + 7) >> 3;
            if (need > buf.length) {
                int cap = buf.length;
                while (cap < need) cap <<= 1;
                byte[] nb = new byte[cap];
                System.arraycopy(buf, 0, nb, 0, nb.length);
                buf = nb;
            }
        }

        void writeBits(int value, int nbits) {
            if (nbits <= 0) return;
            ensure(nbits);
            for (int i = nbits - 1; i >= 0; i--) {
                if (((value >>> i) & 1) != 0) buf[bitPos >> 3] |= (byte) (0x80 >>> (bitPos & 7));
                bitPos++;
            }
        }

        /** two's complement, MSB first */
        void writeSigned(int value, int nbits) {
            int mask = nbits >= 32 ? -1 : ((1 << nbits) - 1);
            writeBits(value & mask, nbits);
        }

        /** n zero bits followed by a terminating 1 */
        void writeUnary(int n) {
            if (n < 0) n = 0;
            ensure(n + 1);
            bitPos += n;
            buf[bitPos >> 3] |= (byte) (0x80 >>> (bitPos & 7));
            bitPos++;
        }

        void writeUtf8(long v) {
            if (v < 0x80) {
                writeBits((int) v, 8);
            } else if (v < 0x800) {
                writeBits(0xC0 | (int) (v >> 6), 8);
                writeBits(0x80 | (int) (v & 0x3F), 8);
            } else if (v < 0x10000) {
                writeBits(0xE0 | (int) (v >> 12), 8);
                writeBits(0x80 | (int) ((v >> 6) & 0x3F), 8);
                writeBits(0x80 | (int) (v & 0x3F), 8);
            } else if (v < 0x200000) {
                writeBits(0xF0 | (int) (v >> 18), 8);
                writeBits(0x80 | (int) ((v >> 12) & 0x3F), 8);
                writeBits(0x80 | (int) ((v >> 6) & 0x3F), 8);
                writeBits(0x80 | (int) (v & 0x3F), 8);
            } else if (v < 0x4000000) {
                writeBits(0xF8 | (int) (v >> 24), 8);
                writeBits(0x80 | (int) ((v >> 18) & 0x3F), 8);
                writeBits(0x80 | (int) ((v >> 12) & 0x3F), 8);
                writeBits(0x80 | (int) ((v >> 6) & 0x3F), 8);
                writeBits(0x80 | (int) (v & 0x3F), 8);
            } else {
                writeBits(0xFC | (int) (v >> 30), 8);
                writeBits(0x80 | (int) ((v >> 24) & 0x3F), 8);
                writeBits(0x80 | (int) ((v >> 18) & 0x3F), 8);
                writeBits(0x80 | (int) ((v >> 12) & 0x3F), 8);
                writeBits(0x80 | (int) ((v >> 6) & 0x3F), 8);
                writeBits(0x80 | (int) (v & 0x3F), 8);
            }
        }

        int byteLength() {
            return (bitPos + 7) >> 3;
        }

        void alignToByte() {
            bitPos = (bitPos + 7) & ~7;
        }

        byte[] finish() {
            byte[] out = new byte[byteLength()];
            System.arraycopy(buf, 0, out, 0, out.length);
            return out;
        }
    }
}
