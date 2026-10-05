package com.mostakim.audiorec.audio;

import java.io.File;
import java.io.IOException;

/**
 * A single take.
 *
 * Owns the sink (WAV / FLAC / AIFF / OGG), counts frames, keeps the peak and
 * RMS of everything written, and reports the take summary when the operator
 * stops.  The audio itself is pushed in by AudioEngine's capture thread, so
 * there is exactly one place where samples reach the disk.
 */
public class Recorder {

    public static class Config {
        public File file;
        public String title = "";
        public int sampleRate = 48000;
        public int channels = 2;
        public int bitDepth = 24;
        public String container = "wav";
        public float gainDb = 0f;
        public boolean dither = true;
        public long sessionId = -1;
        public String deviceName = "";
        public String channelMap = "";
    }

    public static class Result {
        public File file;
        public String container;
        public int sampleRate, channels, bitDepth;
        public long frames;
        public long bytes;
        public long durationMs;
        public float peakDb = Pcm.FLOOR_DB;
        public float rmsDb = Pcm.FLOOR_DB;
        public boolean wroteAnything;
        public boolean fellBackToWav;
        public String fallbackReason;
        public String encoderStats;
        public long startedAt, stoppedAt;
        public int droppedBlocks;
        public long xruns;

        public String summary() {
            return Fmt0() + "  \u00b7  " + sizeLabel(bytes);
        }

        private String Fmt0() {
            return sampleRate / 1000 + " kHz \u00b7 " + bitDepth + "-bit \u00b7 "
                    + channels + " ch \u00b7 " + container.toUpperCase();
        }

        private static String sizeLabel(long b) {
            if (b < 1024) return b + " B";
            if (b < 1024 * 1024) return (b / 1024) + " KB";
            return String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0);
        }
    }

    private AudioSink mSink;
    private Config mConfig;
    private Result mResult = new Result();
    private boolean mOpen;
    private boolean mPaused;
    private double mSumSquares;
    private long mSumFrames;
    private float mPeakLinear;

    public boolean isOpen() {
        return mOpen;
    }

    public boolean isPaused() {
        return mPaused;
    }

    public Result result() {
        return mResult;
    }

    public Config config() {
        return mConfig;
    }

    public String encoderStats() {
        return mSink == null ? "" : mSink.stats();
    }

    /** open the file; Opus falls back to WAV when the device has no encoder */
    public void start(Config cfg) throws IOException {
        mConfig = cfg;
        mResult = new Result();
        mResult.file = cfg.file;
        mResult.container = cfg.container;
        mResult.sampleRate = cfg.sampleRate;
        mResult.channels = cfg.channels;
        mResult.bitDepth = cfg.bitDepth;
        mResult.startedAt = System.currentTimeMillis();
        mSink = openSink(cfg, mResult);
        mOpen = true;
        mPaused = false;
        mSumSquares = 0;
        mSumFrames = 0;
        mPeakLinear = 0f;
    }

    private static AudioSink openSink(Config cfg, Result result) throws IOException {
        if ("flac".equals(cfg.container)) {
            FlacWriter w = new FlacWriter();
            w.setDither(cfg.dither);
            w.open(cfg.file, cfg.sampleRate, cfg.channels, cfg.bitDepth);
            return w;
        }
        if ("aiff".equals(cfg.container)) {
            AiffWriter w = new AiffWriter();
            w.setDither(cfg.dither);
            w.open(cfg.file, cfg.sampleRate, cfg.channels, cfg.bitDepth);
            return w;
        }
        if ("ogg".equals(cfg.container)) {
            if (!OggOpusWriter.encoderAvailable()) {
                result.fellBackToWav = true;
                result.fallbackReason = "no Opus encoder on this device";
                return openWav(cfg, new File(stem(cfg.file) + ".wav"), result);
            }
            try {
                OggOpusWriter w = new OggOpusWriter();
                w.open(cfg.file, cfg.sampleRate, cfg.channels, cfg.bitDepth);
                return w;
            } catch (IOException e) {
                result.fellBackToWav = true;
                result.fallbackReason = e.getMessage();
                return openWav(cfg, new File(stem(cfg.file) + ".wav"), result);
            }
        }
        return openWav(cfg, cfg.file, result);
    }

    private static AudioSink openWav(Config cfg, File f, Result result) throws IOException {
        WavWriter w = new WavWriter();
        w.setDither(cfg.dither);
        w.open(f, cfg.sampleRate, cfg.channels, cfg.bitDepth);
        result.file = f;
        result.container = "wav";
        return w;
    }

    private static String stem(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? f.getParent() + File.separator + n.substring(0, dot) : f.getAbsolutePath();
    }

    /**
     * Feed one block of captured audio (already floated, gain applied here so the
     * meters and the file agree).
     */
    public void write(float[] interleaved, int samples, float gainDb) throws IOException {
        if (!mOpen || mPaused) return;
        float gain = Pcm.dbToLinear(gainDb);
        if (gain != 1f) Pcm.applyGain(interleaved, samples, gain);
        Pcm.clampUnit(interleaved, samples);

        // running peak / rms for the take report
        for (int i = 0; i < samples; i++) {
            float v = interleaved[i];
            float a = v < 0 ? -v : v;
            if (a > mPeakLinear) mPeakLinear = a;
            mSumSquares += (double) v * v;
        }
        mSumFrames += samples;
        mResult.frames += samples / Math.max(1, mConfig.channels);

        mSink.write(interleaved, samples);
        mResult.bytes = mSink.bytesWritten();
    }

    public void pause() {
        mPaused = true;
    }

    public void resume() {
        mPaused = false;
    }

    /** finish the take and produce the report */
    public Result stop() {
        if (!mOpen) return mResult;
        mOpen = false;
        long frames = mResult.frames;
        try {
            mSink.close(frames);
        } catch (IOException ignored) {
        }
        mResult.stoppedAt = System.currentTimeMillis();
        mResult.bytes = mSink.file().exists() ? mSink.file().length() : mSink.bytesWritten();
        mResult.file = mSink.file();
        mResult.container = mSink.container();
        mResult.encoderStats = mSink.stats();
        mResult.durationMs = mResult.sampleRate > 0
                ? frames * 1000L / mResult.sampleRate : 0;
        mResult.wroteAnything = frames > 0;
        mResult.peakDb = Pcm.linearToDb(mPeakLinear);
        double rms = mSumFrames > 0 ? Math.sqrt(mSumSquares / mSumFrames) : 0;
        mResult.rmsDb = Pcm.linearToDb((float) rms);
        return mResult;
    }

    /** abort without pretending the take is complete (file is still kept) */
    public void abort() {
        try {
            stop();
        } catch (Exception ignored) {
        }
        mOpen = false;
    }

    public long bytesWritten() {
        return mSink == null ? 0 : mSink.bytesWritten();
    }

    public File file() {
        return mSink == null ? null : mSink.file();
    }

    public void countDrop() {
        mResult.droppedBlocks++;
    }

    public void countXrun() {
        mResult.xruns++;
    }
}
