package com.mostakim.audiorec.audio;

import java.io.File;
import java.io.IOException;

import com.mostakim.audiorec.util.Fmt;

/** Harness Recorder: same shapes as the real one, writes nothing. */
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
            return sampleRate / 1000 + " kHz \u00b7 " + bitDepth + "-bit \u00b7 "
                    + channels + " ch \u00b7 " + container.toUpperCase()
                    + " \u00b7 " + Fmt.size(bytes);
        }
    }

    public File file;
    private Config mConfig = new Config();
    private Result mResult = new Result();
    private boolean mOpen, mPaused;

    public boolean isOpen() { return mOpen; }

    public boolean isPaused() { return mPaused; }

    public Result result() { return mResult; }

    public Config config() { return mConfig; }

    public String encoderStats() { return ""; }

    public void start(Config cfg) throws IOException {
        mConfig = cfg;
        file = cfg.file;
        mOpen = true;
    }

    public void write(float[] interleaved, int samples, float gainDb) throws IOException {
    }

    public void pause() { mPaused = true; }

    public void resume() { mPaused = false; }

    public File file() { return file; }

    public long bytesWritten() { return mResult.bytes; }

    public void abort() { mOpen = false; }

    public void countDrop() { mResult.droppedBlocks++; }

    public void countXrun() { mResult.xruns++; }

    public Result stop() {
        mOpen = false;
        mResult.file = file;
        return mResult;
    }
}
