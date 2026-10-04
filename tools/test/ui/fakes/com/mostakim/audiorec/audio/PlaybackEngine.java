package com.mostakim.audiorec.audio;

import java.io.File;

/** Harness PlaybackEngine: reports a stopped transport. */
public class PlaybackEngine {

    public enum State { STOPPED, PLAYING, PAUSED }

    public interface Levels {
        void on(float[] rmsDb, float[] peakDb, int channels);
    }

    public interface StateListener {
        void on(State state, String title);
    }

    private State mState = State.STOPPED;
    private String mTitle = "";
    private File mFile;

    public State state() { return mState; }

    public String title() { return mTitle; }

    public File file() { return mFile; }

    public boolean isPlaying() { return mState == State.PLAYING; }

    public long positionMs() { return 0; }

    public long durationMs() { return 0; }

    public void setLevelsListener(Levels l) { }

    public void setStateListener(StateListener l) { }

    public void stop() { mState = State.STOPPED; }

    public void pause() { mState = State.PAUSED; }

    public void resume() { mState = State.PLAYING; }
}
