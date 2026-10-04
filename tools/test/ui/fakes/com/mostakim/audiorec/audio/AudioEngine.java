package com.mostakim.audiorec.audio;

import android.content.Context;
import android.media.AudioDeviceInfo;

import com.mostakim.audiorec.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Harness AudioEngine: reports a plausible USB interface and a stopped
 * transport so every screen renders its populated state.
 */
public class AudioEngine {

    public enum State { IDLE, MONITORING, RECORDING, PAUSED }

    public interface Listener {
        default void onEngineState(State s) { }

        default void onDevicesChanged() { }

        default void onLevels(float[] rmsDb, float[] peakDb, int channels) { }

        default void onScope(float[] interleaved, int frames, int channels) { }

        default void onPlaybackLevels(float[] rmsDb, float[] peakDb, int channels) { }

        default void onPlaybackPosition(long positionMs, long durationMs) { }

        default void onRecordingTick(long frames, long bytes, long elapsedMs) { }

        default void onRecordingFinished(Recorder.Result result) { }

        default void onPlaybackState(PlaybackEngine.State state, String title) { }

        default void onError(String message) { }
    }

    private final Context mContext;
    private final List<Listener> mListeners = new ArrayList<>();
    private final Recorder mRecorder = new Recorder();
    private final PlaybackEngine mPlayback = new PlaybackEngine();
    private final List<AudioDevice> mInputs = new ArrayList<>();
    private final List<AudioDevice> mOutputs = new ArrayList<>();
    private final List<UsbAudioProbe.UsbFacts> mUsb = new ArrayList<>();
    private final float[] mTrims = new float[8];
    private State mState = State.IDLE;
    private AudioDevice mInput, mOutput;

    public AudioEngine(Context c) {
        mContext = c;
        AudioDevice usb = new AudioDevice();
        usb.id = 11;
        usb.key = "usb:0x0763:0x2012";
        usb.name = "M-Audio Fast Track Pro";
        usb.productName = "Fast Track Pro";
        usb.vendor = "M-Audio";
        usb.type = AudioDeviceInfo.TYPE_USB_DEVICE;
        usb.isInput = true;
        usb.isUsb = true;
        usb.vendorId = 0x0763;
        usb.productId = 0x2012;
        usb.usbClass = 0x01;
        usb.usbSubclass = 0x02;
        usb.interfaceCount = 4;
        usb.audioInterfaceCount = 3;
        usb.uacOne = true;
        usb.usbVersion = "2.00";
        usb.serial = "FTPro-0001";
        usb.sampleRates = new int[]{44100, 48000, 88200, 96000, 176400, 192000};
        usb.channelCounts = new int[]{2, 4};
        usb.supports16 = true;
        usb.supports24 = true;
        usb.supports32 = true;
        mInputs.add(usb);
        mInput = usb;

        AudioDevice out = new AudioDevice();
        out.id = 12;
        out.key = "usb-out:0x0763:0x2012";
        out.name = "Fast Track Pro outputs 1-2";
        out.isInput = false;
        out.isUsb = true;
        out.sampleRates = new int[]{44100, 48000, 96000, 192000};
        out.channelCounts = new int[]{2, 4};
        out.supports16 = true;
        out.supports24 = true;
        mOutputs.add(out);
        mOutput = out;

        AudioDevice builtin = new AudioDevice();
        builtin.id = 1;
        builtin.key = "builtin-mic";
        builtin.name = "Built-in microphone";
        builtin.type = AudioDeviceInfo.TYPE_BUILTIN_MIC;
        builtin.isInput = true;
        builtin.isDefault = true;
        builtin.sampleRates = new int[]{48000};
        builtin.channelCounts = new int[]{1, 2};
        builtin.supports16 = true;
        mInputs.add(builtin);

        AudioDevice speaker = new AudioDevice();
        speaker.id = 2;
        speaker.key = "builtin-speaker";
        speaker.name = "Built-in speaker";
        speaker.type = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
        speaker.isInput = false;
        speaker.isDefault = true;
        speaker.sampleRates = new int[]{48000};
        speaker.channelCounts = new int[]{2};
        mOutputs.add(speaker);

        UsbAudioProbe.UsbFacts f = new UsbAudioProbe.UsbFacts();
        f.vendorId = 0x0763;
        f.productId = 0x2012;
        f.productName = "Fast Track Pro";
        f.manufacturer = "M-Audio";
        f.vendor = "M-Audio";
        f.serial = "FTPro-0001";
        f.usbVersion = "2.00";
        f.usbClass = 0x01;
        f.usbSubclass = 0x02;
        f.interfaceCount = 4;
        f.audioInterfaceCount = 3;
        f.streamingInterfaces = 2;
        f.controlInterfaces = 1;
        f.endpointCount = 4;
        f.isoEndpoints = 2;
        f.uacOne = true;
        f.permissionGranted = true;
        mUsb.add(f);
    }

    public void addListener(Listener l) {
        if (l != null && !mListeners.contains(l)) mListeners.add(l);
    }

    public void removeListener(Listener l) { mListeners.remove(l); }

    public State state() { return mState; }

    public boolean isCapturing() { return mState == State.MONITORING || mState == State.RECORDING
            || mState == State.PAUSED; }

    public int recordingElapsedMs() { return 0; }

    public AudioDevice input() { return mInput; }

    public AudioDevice output() { return mOutput; }

    public List<AudioDevice> inputs() { return new ArrayList<>(mInputs); }

    public List<AudioDevice> outputs() { return new ArrayList<>(mOutputs); }

    public List<UsbAudioProbe.UsbFacts> usbDevices() { return new ArrayList<>(mUsb); }

    public int[] availableRates() { return new int[]{44100, 48000, 88200, 96000, 176400, 192000}; }

    public int[] availableDepths() { return new int[]{16, 24, 32}; }

    public int[] availableChannels() { return new int[]{1, 2, 4}; }

    public float channelTrim(int channel) {
        return channel >= 0 && channel < mTrims.length ? mTrims[channel] : 0f;
    }

    public void setChannelTrim(int channel, float db) {
        if (channel >= 0 && channel < mTrims.length) mTrims[channel] = db;
    }

    public void resetChannelTrims() {
        for (int i = 0; i < mTrims.length; i++) mTrims[i] = 0f;
    }

    public void refreshDevices() {
        for (Listener l : new ArrayList<>(mListeners)) l.onDevicesChanged();
    }

    public void selectInput(AudioDevice d) { mInput = d; }

    public void selectOutput(AudioDevice d) { mOutput = d; }

    public boolean startMonitor() {
        mState = State.MONITORING;
        for (Listener l : new ArrayList<>(mListeners)) l.onEngineState(mState);
        return true;
    }

    public void stopCapture(boolean notify) {
        mState = State.IDLE;
        if (notify) {
            for (Listener l : new ArrayList<>(mListeners)) l.onEngineState(mState);
        }
    }

    public void setMonitoring(boolean on) {
        mState = on ? State.MONITORING : State.IDLE;
    }

    public void setMute(boolean mute) { }

    public void setMonitorGainDb(float db) { }

    public boolean startRecording(Recorder.Config cfg) {
        try {
            mRecorder.start(cfg);
        } catch (Exception e) {
            for (Listener l : new ArrayList<>(mListeners)) l.onError(e.getMessage());
            return false;
        }
        mState = State.RECORDING;
        for (Listener l : new ArrayList<>(mListeners)) l.onEngineState(mState);
        return true;
    }

    public void pauseRecording() { mRecorder.pause(); mState = State.PAUSED; }

    public void resumeRecording() { mRecorder.resume(); mState = State.RECORDING; }

    public Recorder.Result stopRecording() {
        Recorder.Result r = mRecorder.stop();
        mState = State.MONITORING;
        return r;
    }

    public Recorder recorder() { return mRecorder; }

    public PlaybackEngine playback() { return mPlayback; }

    public void play(File f, String title) { }

    public void stopPlayback() { mPlayback.stop(); }
}
