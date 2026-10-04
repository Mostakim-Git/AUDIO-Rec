package com.mostakim.audiorec.audio;

import android.media.MediaCodec;
import android.media.MediaFormat;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * OGG/Opus sink - the patent-free equivalent of MP3, as requested.
 *
 * Opus itself is coded by the platform's own encoder (c2.android.opus.encoder,
 * present on every Android 10+ build) while the Ogg container is written here:
 * OpusHead + OpusTags, pages cut on packet boundaries, continuation flags and
 * correct granule positions.
 *
 * Opus is a 48 kHz codec, so when the interface runs at another rate the capture
 * is linearly resampled block by block (see Pcm.Resampler - the result does not
 * depend on where the capture blocks are cut), and multi-channel captures are
 * folded down to stereo.  WAV/FLAC/AIFF always stay at the native capture rate and depth -
 * this is the only lossy path in the app, and the UI says so.
 */
public class OggOpusWriter implements AudioSink {

    private static final int OPUS_RATE = 48000;
    private static final int PRE_SKIP = 312;           // libopus default: 6.5 ms
    private static final int FRAME_MS = 20;
    private static final int FRAME_SAMPLES = OPUS_RATE * FRAME_MS / 1000;   // 960

    private File mFile;
    private int mSampleRate, mChannels, mBitDepth;
    private OggWriter mOgg;
    private MediaCodec mEncoder;
    private final MediaCodec.BufferInfo mInfo = new MediaCodec.BufferInfo();

    private int mOutChannels;
    private float[] mPending;
    private int mPendingFrames;
    private long mInputSamples;                        // frames at the capture rate
    private boolean mEos;
    private long mFramesTotal;
    private long mPackets;
    private Pcm.Resampler mResampler;                  // capture rate -> 48 kHz, block safe
    private byte[] mHeldPacket;
    private byte[] mHeldTags;
    private int mBitrate;

    /** thrown when the device ships without an Opus encoder */
    public static class NoOpusEncoderException extends IOException {
        public NoOpusEncoderException(String m) {
            super(m);
        }
    }

    @Override
    public void open(File file, int sampleRate, int channels, int bitDepth) throws IOException {
        mFile = file;
        mSampleRate = sampleRate;
        mChannels = channels;
        mBitDepth = bitDepth;
        mOutChannels = Math.min(2, Math.max(1, channels));
        mBitrate = mOutChannels == 1 ? 96000 : 160000;
        mPendingFrames = 0;
        mInputSamples = 0;
        mFramesTotal = 0;
        mPackets = 0;
        mEos = false;
        mHeldPacket = null;

        MediaFormat fmt = MediaFormat.createAudioFormat("audio/opus", OPUS_RATE, mOutChannels);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, mBitrate);
        fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, FRAME_SAMPLES * mOutChannels * 2 * 2);
        try {
            mEncoder = MediaCodec.createEncoderByType("audio/opus");
        } catch (Exception e) {
            throw new NoOpusEncoderException("no Opus encoder on this device");
        }
        try {
            mEncoder.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mEncoder.start();
        } catch (Exception e) {
            safeRelease();
            throw new IOException("Opus encoder refused configuration: " + e.getMessage());
        }

        mResampler = new Pcm.Resampler(mOutChannels, (double) sampleRate / OPUS_RATE);
        mPending = new float[FRAME_SAMPLES * mOutChannels];
        mOgg = new OggWriter((int) (System.nanoTime() & 0x7FFFFFFF));
        mOgg.open(file);
        mOgg.writeBosPage(OggWriter.opusHead(mOutChannels, OPUS_RATE, PRE_SKIP));
        mHeldTags = OggWriter.opusTags("AUDIO-rec 1.0 (Mostakim Billah)",
                "ENCODER=AUDIO-rec native Ogg muxer",
                "BITRATE=" + mBitrate,
                "SOURCE_RATE=" + sampleRate);
        mOgg.writePacket(mHeldTags, 0);                // must be the second packet
    }

    @Override
    public void write(float[] interleaved, int samples) throws IOException {
        int frames = samples / mChannels;
        mInputSamples += frames;
        mResampler.feed(Pcm.toStereo(interleaved, frames, mChannels), frames);
        drainResampler();
    }

    /** move everything the resampler can give us into the encoder, packet by packet */
    private void drainResampler() throws IOException {
        while (true) {
            if (mPendingFrames == FRAME_SAMPLES) {
                encodeFrame(mPending, FRAME_SAMPLES);
                mPendingFrames = 0;
            }
            int n = mResampler.read(mPending, mPendingFrames, FRAME_SAMPLES - mPendingFrames);
            if (n == 0) return;
            mPendingFrames += n;
        }
    }


    private void encodeFrame(float[] pcm, int frames) throws IOException {
        if (mEncoder == null) return;
        int samples = frames * mOutChannels;
        int idx = mEncoder.dequeueInputBuffer(20000);
        if (idx < 0) return;                     // encoder behind: drop rather than stall
        ByteBuffer in = mEncoder.getInputBuffer(idx);
        if (in == null) return;
        in.clear();
        byte[] bytes = Pcm.floatToByte16(pcm, samples);
        int need = Math.min(bytes.length, in.capacity());
        in.put(bytes, 0, need);
        long ptsUs = mQueuedSamples * 1_000_000L / OPUS_RATE;
        mEncoder.queueInputBuffer(idx, 0, need, ptsUs, 0);
        mQueuedSamples += frames;
        drain(false);
    }

    private long mQueuedSamples;

    private void drain(boolean endOfStream) throws IOException {
        if (endOfStream && mEncoder != null) {
            int idx = mEncoder.dequeueInputBuffer(20000);
            if (idx >= 0) {
                mEncoder.queueInputBuffer(idx, 0, 0,
                        mQueuedSamples * 1_000_000L / OPUS_RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
        }
        while (mEncoder != null) {
            int outIdx = mEncoder.dequeueOutputBuffer(mInfo, endOfStream ? 20000 : 0);
            if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue;
            if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) return;
            if (outIdx < 0) return;
            boolean config = (mInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
            boolean eos = (mInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            ByteBuffer buf = mEncoder.getOutputBuffer(outIdx);
            if (buf != null && mInfo.size > 0 && !config) {
                buf.position(mInfo.offset);
                buf.limit(mInfo.offset + mInfo.size);
                byte[] packet = new byte[mInfo.size];
                buf.get(packet);
                long granule = mInfo.presentationTimeUs > 0
                        ? mInfo.presentationTimeUs * OPUS_RATE / 1_000_000L + PRE_SKIP
                        : PRE_SKIP + mPackets * FRAME_SAMPLES;
                if (mHeldPacket != null) mOgg.writePacket(mHeldPacket, granule);
                mHeldPacket = packet;
                mPackets++;
            }
            mEncoder.releaseOutputBuffer(outIdx, false);
            if (eos) {
                mEos = true;
                return;
            }
        }
    }

    @Override
    public void close(long frames) throws IOException {
        drainResampler();                      // the resampler may still hold a partial span
        if (mPendingFrames > 0) {
            encodeFrame(mPending, mPendingFrames);
            mPendingFrames = 0;
        }
        mFramesTotal = frames > 0 ? frames : mInputSamples;
        try {
            drain(true);
        } catch (Exception ignored) {
        }
        int guard = 0;
        while (!mEos && guard++ < 200) {
            try {
                drain(false);
            } catch (Exception e) {
                break;
            }
            if (mEos) break;
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                break;
            }
        }
        final long finalGranule = PRE_SKIP + mFramesTotal * OPUS_RATE / Math.max(1, mSampleRate);
        if (mHeldPacket != null) {
            mOgg.writePacket(mHeldPacket, finalGranule);
            mHeldPacket = null;
        }
        mOgg.finish(finalGranule);
        safeRelease();
    }

    private void safeRelease() {
        if (mEncoder != null) {
            try {
                mEncoder.stop();
            } catch (Exception ignored) {
            }
            try {
                mEncoder.release();
            } catch (Exception ignored) {
            }
            mEncoder = null;
        }
    }

    @Override
    public long bytesWritten() {
        return mOgg == null ? 0 : mOgg.bytesWritten();
    }

    @Override
    public File file() {
        return mFile;
    }

    @Override
    public String container() {
        return "ogg";
    }

    @Override
    public String stats() {
        return "Opus " + (mOutChannels == 1 ? "mono" : "stereo") + " @ "
                + (mBitrate / 1000) + " kbps \u00b7 " + mPackets + " packets";
    }

    public static boolean encoderAvailable() {
        MediaCodec c = null;
        try {
            c = MediaCodec.createEncoderByType("audio/opus");
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) try {
                c.release();
            } catch (Exception ignored) {
            }
        }
    }
}
