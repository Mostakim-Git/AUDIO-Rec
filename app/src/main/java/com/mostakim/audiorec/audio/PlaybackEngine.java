package com.mostakim.audiorec.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Plays back anything the library holds.
 *
 * Two paths, because Android needs both:
 *   • platform decode (MediaExtractor + MediaCodec) for WAV, FLAC, Ogg/Opus,
 *     MP3 and whatever else the device ships,
 *   • a native reader for AIFF, which the platform cannot demux.
 *
 * Output always goes through the selected output endpoint (stereo; on a
 * multi-channel interface the first two outputs are used, exactly as specified)
 * and the playback level meters are fed from the same buffers that reach the
 * DAC, so what is metered is what is heard.
 */
public class PlaybackEngine {

    private static final String TAG = "PlaybackEngine";

    public enum State {STOPPED, PLAYING, PAUSED}

    public interface Levels {
        void on(float[] rmsDb, float[] peakDb, int channels);
    }

    public interface StateListener {
        void on(State state, String title);
    }

    public interface PositionListener {
        void on(long positionMs, long durationMs);
    }

    private final Context mContext;
    private final Levels mLevels;
    private final StateListener mStateListener;
    private final PositionListener mPositionListener;

    private Thread mThread;
    private volatile boolean mRunning;
    private volatile boolean mPaused;
    private volatile State mState = State.STOPPED;
    private volatile long mPositionMs;
    private volatile long mDurationMs;
    private volatile String mTitle = "";

    private final float[] mRms = new float[2];
    private final float[] mPeak = new float[2];

    public PlaybackEngine(Context ctx, Levels levels, StateListener state, PositionListener pos) {
        mContext = ctx.getApplicationContext();
        mLevels = levels;
        mStateListener = state;
        mPositionListener = pos;
        Arrays.fill(mRms, Pcm.FLOOR_DB);
        Arrays.fill(mPeak, Pcm.FLOOR_DB);
    }

    public State state() {
        return mState;
    }

    public String title() {
        return mTitle;
    }

    public long positionMs() {
        return mPositionMs;
    }

    public long durationMs() {
        return mDurationMs;
    }

    public boolean isPlaying() {
        return mState == State.PLAYING || mState == State.PAUSED;
    }

    public boolean play(File file, String title, int outputDeviceId) {
        stop();
        if (file == null || !file.exists()) return false;
        mTitle = title == null ? file.getName() : title;
        mPositionMs = 0;
        mDurationMs = 0;
        mPaused = false;
        mRunning = true;
        mThread = new Thread(() -> run(file, outputDeviceId), "audiorec-play");
        mThread.start();
        return true;
    }

    public void stop() {
        mRunning = false;
        Thread t = mThread;
        if (t != null) {
            try {
                t.join(1200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            mThread = null;
        }
        setState(State.STOPPED);
    }

    public void pause() {
        mPaused = true;
        setState(State.PAUSED);
    }

    public void resume() {
        mPaused = false;
        setState(State.PLAYING);
    }

    public void release() {
        stop();
    }

    private void setState(State s) {
        mState = s;
        if (mStateListener != null) mStateListener.on(s, mTitle);
    }

    // ------------------------------------------------------------ the thread
    private void run(File file, int outputDeviceId) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        RawPcmReader raw = RawPcmReader.open(file);
        if (raw != null) {
            runRaw(raw, outputDeviceId);
            return;
        }
        runPlatform(file, outputDeviceId);
    }

    private AudioTrack buildTrack(int sampleRate, int channels, int deviceId) {
        int outChannels = Math.min(2, channels);
        int mask = outChannels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
        int minBuf = AudioTrack.getMinBufferSize(sampleRate, mask, AudioFormat.ENCODING_PCM_FLOAT);
        if (minBuf <= 0) minBuf = sampleRate * outChannels * 4 / 5;
        int bufSize = Math.max(minBuf, sampleRate * outChannels * 4 / 10);
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(mask)
                        .build())
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release();
            return null;
        }
        if (deviceId >= 0) {
            try {
                AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                for (AudioDeviceInfo info : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                    if (info.getId() == deviceId) {
                        track.setPreferredDevice(info);
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        track.play();
        return track;
    }

    private void runRaw(RawPcmReader raw, int deviceId) {
        AudioTrack track = buildTrack(raw.sampleRate, raw.channels, deviceId);
        if (track == null) {
            raw.close();
            setState(State.STOPPED);
            return;
        }
        mDurationMs = raw.durationMs;
        setState(State.PLAYING);
        int frames = 2048;
        float[] buf = new float[frames * raw.channels];
        long positionFrames = 0;
        long lastReport = 0;
        try {
            while (mRunning) {
                if (mPaused) {
                    sleep(40);
                    continue;
                }
                int n = raw.read(buf, frames);
                if (n <= 0) break;
                int samples = n * raw.channels;
                float[] stereo = Pcm.toStereo(buf, n, raw.channels);
                int outChannels = Math.min(2, raw.channels);
                int outSamples = n * outChannels;
                track.write(stereo, 0, outSamples, AudioTrack.WRITE_BLOCKING);
                positionFrames += n;
                mPositionMs = raw.sampleRate > 0 ? positionFrames * 1000L / raw.sampleRate : 0;
                Pcm.analyse(stereo, n, outChannels, mPeak, mRms);
                if (mLevels != null) mLevels.on(mRms, mPeak, outChannels);
                long now = System.currentTimeMillis();
                if (mPositionListener != null && now - lastReport > 200) {
                    lastReport = now;
                    mPositionListener.on(mPositionMs, mDurationMs);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "raw playback failed", e);
        } finally {
            raw.close();
            try {
                track.stop();
            } catch (Exception ignored) {
            }
            track.release();
            Arrays.fill(mRms, Pcm.FLOOR_DB);
            Arrays.fill(mPeak, Pcm.FLOOR_DB);
            if (mLevels != null) mLevels.on(mRms, mPeak, 2);
            if (mRunning) setState(State.STOPPED);
        }
    }

    private void runPlatform(File file, int deviceId) {
        MediaExtractor ex = null;
        MediaCodec codec = null;
        AudioTrack track = null;
        try {
            ex = new MediaExtractor();
            ex.setDataSource(file.getAbsolutePath());
            int trackIndex = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    trackIndex = i;
                    fmt = f;
                    break;
                }
            }
            if (trackIndex < 0 || fmt == null) {
                setState(State.STOPPED);
                return;
            }
            ex.selectTrack(trackIndex);
            int sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                mDurationMs = fmt.getLong(MediaFormat.KEY_DURATION) / 1000;
            }

            String mime = fmt.getString(MediaFormat.KEY_MIME);
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(fmt, null, null, 0);
            codec.start();

            boolean trackReady = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false, outputDone = false;
            int outChannels = Math.min(2, channels);
            int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;

            while (mRunning && !outputDone) {
                if (mPaused) {
                    sleep(40);
                    continue;
                }
                if (!inputDone) {
                    int inIdx = codec.dequeueInputBuffer(10000);
                    if (inIdx >= 0) {
                        ByteBuffer in = codec.getInputBuffer(inIdx);
                        int size = in == null ? -1 : ex.readSampleData(in, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }

                int outIdx = codec.dequeueOutputBuffer(info, 10000);
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = codec.getOutputFormat();
                    int rate = of.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                            ? of.getInteger(MediaFormat.KEY_SAMPLE_RATE) : sampleRate;
                    int ch = of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                            ? of.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : channels;
                    outChannels = Math.min(2, ch);
                    if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = of.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    }
                    if (track != null) {
                        track.release();
                    }
                    track = buildTrack(rate, outChannels, deviceId);
                    trackReady = track != null;
                    if (!trackReady) {
                        setState(State.STOPPED);
                        return;
                    }
                    setState(State.PLAYING);
                } else if (outIdx >= 0) {
                    ByteBuffer out = codec.getOutputBuffer(outIdx);
                    boolean config = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                    if (out != null && info.size > 0 && !config && trackReady) {
                        out.position(info.offset);
                        out.limit(info.offset + info.size);
                        float[] floats = decodeToFloat(out, info.size, pcmEncoding, outChannels);
                        int framesOut = floats.length / outChannels;
                        track.write(floats, 0, floats.length, AudioTrack.WRITE_BLOCKING);
                        mPositionMs = info.presentationTimeUs / 1000;
                        Pcm.analyse(floats, framesOut, outChannels, mPeak, mRms);
                        if (mLevels != null) mLevels.on(mRms, mPeak, outChannels);
                        if (mPositionListener != null && mPositionMs % 500 < 40) {
                            mPositionListener.on(mPositionMs, mDurationMs);
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "playback failed", e);
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception ignored) {
                }
                codec.release();
            }
            if (ex != null) {
                try {
                    ex.release();
                } catch (Exception ignored) {
                }
            }
            if (track != null) {
                try {
                    track.stop();
                } catch (Exception ignored) {
                }
                track.release();
            }
            Arrays.fill(mRms, Pcm.FLOOR_DB);
            Arrays.fill(mPeak, Pcm.FLOOR_DB);
            if (mLevels != null) mLevels.on(mRms, mPeak, 2);
            if (mRunning) setState(State.STOPPED);
        }
    }

    /**
     * Decoders hand back 16-bit, float, 24-bit or 8-bit PCM depending on the file
     * and the device; everything is normalised to float so playback always uses a
     * single AudioTrack configuration.
     */
    private float[] decodeToFloat(ByteBuffer buf, int size, int encoding, int channels) {
        final int sampleBytes = encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4
                : (encoding == AudioFormat.ENCODING_PCM_24BIT_PACKED ? 3
                : (encoding == AudioFormat.ENCODING_PCM_8BIT ? 1 : 2));
        int outSamples = size / sampleBytes;
        float[] out = new float[outSamples];
        for (int i = 0; i < outSamples; i++) {
            switch (sampleBytes) {
                case 4: {
                    int b0 = buf.get() & 0xFF, b1 = buf.get() & 0xFF,
                            b2 = buf.get() & 0xFF, b3 = buf.get() & 0xFF;
                    int v = (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
                    out[i] = encoding == AudioFormat.ENCODING_PCM_FLOAT
                            ? Float.intBitsToFloat(v)
                            : (float) (v / 2147483648.0);
                    break;
                }
                case 3: {
                    int b0 = buf.get() & 0xFF, b1 = buf.get() & 0xFF, b2 = buf.get() & 0xFF;
                    int v = (b2 << 16) | (b1 << 8) | b0;
                    if ((v & 0x800000) != 0) v -= 0x1000000;
                    out[i] = v * (1f / 8388608f);
                    break;
                }
                case 1:
                    out[i] = ((buf.get() & 0xFF) - 128) * (1f / 128f);
                    break;
                default: {
                    int lo = buf.get() & 0xFF, hi = buf.get() & 0xFF;
                    int v = (hi << 8) | lo;
                    if (v > 32767) v -= 65536;
                    out[i] = v * (1f / 32768f);
                    break;
                }
            }
        }
        return out;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
