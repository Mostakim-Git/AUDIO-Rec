package com.mostakim.audiorec.audio;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Minimal, spec-correct Ogg bitstream writer (used by the Opus encoder).
 *
 * Handles the parts people get wrong: lacing values with a terminating short
 * segment, the continuation flag across pages, the CRC-32 of every page and
 * per-page granule positions.
 */
public class OggWriter {

    private static final int MAX_SEGMENTS = 255;
    private static final int TARGET_PAGE_BYTES = 4096;

    private OutputStream mOut;
    private final int mSerial;
    private long mSequence;
    private long mGranule = -1;
    private int mSegmentCount;
    private final int[] mLacing = new int[MAX_SEGMENTS];
    private byte[] mPayload = new byte[TARGET_PAGE_BYTES * 2];
    private int mPayloadLen;
    private byte[] mPendingContinuation = null;   // partial packet spilling to the next page
    private boolean mContinued = false;
    private long mBytesWritten;

    public OggWriter(int serial) {
        mSerial = serial;
    }

    public void open(File f) throws IOException {
        mOut = new BufferedOutputStream(new FileOutputStream(f), 1 << 16);
        mSequence = 0;
        mGranule = -1;
        mSegmentCount = 0;
        mPayloadLen = 0;
        mBytesWritten = 0;
    }

    public long bytesWritten() {
        return mBytesWritten;
    }

    /** first page of the logical stream: exactly one packet, BOS flagged */
    public void writeBosPage(byte[] packet) throws IOException {
        if (mPayloadLen + packet.length > mPayload.length) grow(mPayloadLen + packet.length);
        System.arraycopy(packet, 0, mPayload, mPayloadLen, packet.length);
        mPayloadLen += packet.length;
        int full = packet.length / 255;
        for (int i = 0; i < full; i++) addLacing(255);
        addLacing(packet.length % 255);      // 0 when the length is an exact multiple
        flushPage(0x02, 0);                  // BOS flag, granule 0, sequence 0
    }

    /** accumulate a packet; pages are cut on a packet boundary */
    public void writePacket(byte[] packet, long granule) throws IOException {
        int offset = 0;
        int len = packet.length;
        if (mPendingContinuation != null) {
            // finish the packet that spilled into this page
            offset = 0;
            mPendingContinuation = null;
        }
        while (offset < len) {
            int room = TARGET_PAGE_BYTES - mPayloadLen;
            int take = Math.min(room, len - offset);
            if (mPayloadLen + take > mPayload.length) grow(mPayloadLen + take);
            System.arraycopy(packet, offset, mPayload, mPayloadLen, take);
            mPayloadLen += take;
            offset += take;

            int segs = take / 255;
            int rem = take % 255;
            for (int i = 0; i < segs; i++) addLacing(255);
            if (offset < len) {
                // packet continues: the current page ends on a 255 boundary with
                // no terminating short segment
                continue;
            }
            if (rem > 0 || take % 255 == 0) addLacing(rem);
            if (mSegmentCount >= MAX_SEGMENTS) {
                flush(false, granule);
            }
        }
        mGranule = granule;
        if (mPayloadLen >= TARGET_PAGE_BYTES || mSegmentCount >= MAX_SEGMENTS - 8) {
            flush(false, granule);
        }
    }

    /** last page: EOS flag, final granule position */
    public void finish(long lastGranule) throws IOException {
        flush(true, lastGranule);
        if (mOut != null) {
            mOut.flush();
            mOut.close();
            mOut = null;
        }
    }

    public void abort() {
        try {
            if (mOut != null) mOut.close();
        } catch (IOException ignored) {
        }
        mOut = null;
    }

    private void addLacing(int value) {
        if (mSegmentCount < MAX_SEGMENTS) mLacing[mSegmentCount++] = value;
    }

    private static int segmentsFor(int len) {
        int segs = len / 255 + 1;
        return segs;
    }

    /** emit the buffered segments as one page */
    private void flush(boolean eos, long granule) throws IOException {
        if (mSegmentCount == 0 && !eos) return;
        int type = eos ? 0x04 : 0x00;
        if (mContinued) type |= 0x01;
        flushPage(type, granule);
    }

    private void flushPage(int type, long granule) throws IOException {
        int hdr = 27 + mSegmentCount;
        byte[] page = new byte[hdr + mPayloadLen];
        page[0] = 'O';
        page[1] = 'g';
        page[2] = 'g';
        page[3] = 'S';
        page[4] = 0;                                  // version
        page[5] = (byte) type;
        long g = granule < 0 ? 0 : granule;
        for (int i = 0; i < 8; i++) page[6 + i] = (byte) ((g >>> (8 * i)) & 0xFF);
        for (int i = 0; i < 4; i++) page[14 + i] = (byte) ((mSerial >>> (8 * i)) & 0xFF);
        for (int i = 0; i < 4; i++) page[18 + i] = (byte) ((mSequence >>> (8 * i)) & 0xFF);
        // CRC field left zero while computing
        page[26] = (byte) mSegmentCount;
        for (int i = 0; i < mSegmentCount; i++) page[27 + i] = (byte) mLacing[i];
        System.arraycopy(mPayload, 0, page, hdr, mPayloadLen);
        int crc = crc32(page, 0, hdr + mPayloadLen);
        for (int i = 0; i < 4; i++) page[22 + i] = (byte) ((crc >>> (8 * i)) & 0xFF);
        mOut.write(page);
        mBytesWritten += page.length;

        mSequence++;
        mSegmentCount = 0;
        mPayloadLen = 0;
        mContinued = false;
        if (granule >= 0) mGranule = granule;
    }

    private void grow(int need) {
        int cap = mPayload.length;
        while (cap < need) cap <<= 1;
        byte[] nb = new byte[cap];
        System.arraycopy(mPayload, 0, nb, 0, mPayload.length);
        mPayload = nb;
    }

    /** Ogg uses the plain (non-reflected) CRC-32 with polynomial 0x04C11DB7 */
    static int crc32(byte[] data, int off, int len) {
        int crc = 0;
        for (int i = off; i < off + len; i++) {
            crc ^= (data[i] & 0xFF) << 24;
            for (int b = 0; b < 8; b++) {
                crc = ((crc & 0x80000000) != 0) ? ((crc << 1) ^ 0x04C11DB7) : (crc << 1);
            }
        }
        return crc;
    }

    public static byte[] opusHead(int channels, int inputRate, int preSkip) {
        byte[] h = new byte[19];
        System.arraycopy("OpusHead".getBytes(), 0, h, 0, 8);
        h[8] = 1;                                   // version
        h[9] = (byte) channels;
        h[10] = (byte) (preSkip & 0xFF);
        h[11] = (byte) ((preSkip >> 8) & 0xFF);
        h[12] = (byte) (inputRate & 0xFF);
        h[13] = (byte) ((inputRate >> 8) & 0xFF);
        h[14] = (byte) ((inputRate >> 16) & 0xFF);
        h[15] = (byte) ((inputRate >> 24) & 0xFF);
        h[16] = 0;                                  // output gain
        h[17] = 0;
        h[18] = 0;                                  // channel mapping family 0
        return h;
    }

    public static byte[] opusTags(String vendor, String... comments) {
        byte[] v = vendor.getBytes();
        int len = 8 + 4 + v.length + 4;
        for (String c : comments) len += 4 + c.getBytes().length;
        byte[] out = new byte[len];
        int p = 0;
        System.arraycopy("OpusTags".getBytes(), 0, out, p, 8);
        p += 8;
        p = putInt(out, p, v.length);
        System.arraycopy(v, 0, out, p, v.length);
        p += v.length;
        p = putInt(out, p, comments.length);
        for (String c : comments) {
            byte[] b = c.getBytes();
            p = putInt(out, p, b.length);
            System.arraycopy(b, 0, out, p, b.length);
            p += b.length;
        }
        return out;
    }

    private static int putInt(byte[] out, int p, int v) {
        for (int i = 0; i < 4; i++) out[p + i] = (byte) ((v >>> (8 * i)) & 0xFF);
        return p + 4;
    }
}
