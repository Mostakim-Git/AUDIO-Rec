package com.mostakim.audiorec.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import com.mostakim.audiorec.util.Formats;
import com.mostakim.audiorec.util.Prefs;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The workstation's audio core.
 *
 * One capture thread runs whenever the engine is armed, monitoring or
 * recording.  It reads blocks from an {@link AudioRecord} bound to the selected
 * input endpoint, and in a single pass:
 *
 *   1. normalises the samples to 32-bit float (16-bit, packed 24-bit or float
 *      depending on what the endpoint can carry),
 *   2. measures per-channel peak/RMS for the level meters,
 *   3. taps a decimated frame for the scope and the spectrum display,
 *   4. writes the take through {@link Recorder} (gain applied once, in float),
 *   5. optionally folds the signal back to the selected output for zero-added
 *      latency monitoring.
 *
 * Playback is a second, independent path (see {@link PlaybackEngine}) so you can
 * audition a take while the interface stays armed.
 */
public final class AudioEngine {

    private static final String TAG = "AudioEngine";
    private static final int METER_INTERVAL_MS = 33;      // ~30 fps to the UI
    private static final int SCOPE_DECIMATE = 8;

    public enum State {IDLE, MONITORING, RECORDING, PAUSED}

    public interface Listener {
        default void onEngineState(State s) {
        }

        default void onDevicesChanged() {
        }

        default void onLevels(float[] rmsDb, float[] peakDb, int channels) {
        }

        default void onScope(float[] interleaved, int frames, int channels) {
        }

        /**
         * The analyser window for what is being played back, so the spectrum is
         * live while auditioning a take and not only while capturing.
         */
        default void onPlaybackScope(float[] monoWindow, int frames) {
        }

        default void onRecordingTick(long frames, long bytes, long elapsedMs) {
        }

        default void onRecordingFinished(Recorder.Result result) {
        }

        default void onPlaybackLevels(float[] rmsDb, float[] peakDb, int channels) {
        }

        default void onPlaybackState(PlaybackEngine.State s, String title) {
        }

        default void onPlaybackPosition(long positionMs, long durationMs) {
        }

        default void onError(String message) {
        }
    }

    private final Context mContext;
    private final Prefs mPrefs;
    private final AudioManager mAudioManager;
    private final List<Listener> mListeners = new CopyOnWriteArrayList<>();

    private final List<AudioDevice> mInputs = new ArrayList<>();
    private final List<AudioDevice> mOutputs = new ArrayList<>();
    private List<UsbAudioProbe.UsbFacts> mUsbDevices = new ArrayList<>();

    private AudioDevice mInput, mOutput;
    private AudioRecord mRecord;
    private AudioTrack mMonitorTrack;
    private Recorder mRecorder;
    private PlaybackEngine mPlayback;

    private Thread mCaptureThread;
    private volatile boolean mRunning;
    private volatile State mState = State.IDLE;

    private final float[] mLevelRms, mLevelPeak;
    private float[] mBlock;
    private float[] mMonitorBlock;
    private int mBlockFrames;
    private int mActiveChannels = 2;
    private int mInputEncoding = AudioFormat.ENCODING_PCM_16BIT;
    private int mSessionId = 0;
    private long mRecordingStartedAt;
    private long mLastMeterAt;
    private long mLastTickAt;
    private int mXruns;

    public AudioEngine(Context ctx) {
        mContext = ctx.getApplicationContext();
        mPrefs = new Prefs(ctx);
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mLevelRms = new float[8];
        mLevelPeak = new float[8];
        Arrays.fill(mLevelRms, Pcm.FLOOR_DB);
        Arrays.fill(mLevelPeak, Pcm.FLOOR_DB);
        mPlayback = new PlaybackEngine(mContext, new PlaybackEngine.Levels() {
            @Override
            public void on(float[] rms, float[] peak, int channels) {
                onPlaybackLevels(rms, peak, channels);
            }

            @Override
            public void scope(float[] monoWindow, int frames) {
                onPlaybackScope(monoWindow, frames);
            }
        }, this::onPlaybackState, this::onPlaybackPosition);
        refreshDevices();
    }

    // ============================================================= listeners =
    /** one listener call, used by {@link #dispatch} */
    private interface Call {
        void to(Listener l);
    }

    /**
     * Hands an event to every listener.
     *
     * Listeners are UI code and can throw for reasons that have nothing to do
     * with audio: a view that was released while a take was running, a dialog
     * that is gone, a notification the system refused.  Such an exception used
     * to escape into the capture thread, and Android kills the entire process
     * when a thread dies uncaught - which is exactly what "the app keeps
     * stopping" looks like from the outside.  A failing listener is now a
     * logged warning and the audio keeps running.
     */
    private void dispatch(Call call) {
        for (Listener l : mListeners) {
            try {
                call.to(l);
            } catch (Throwable t) {
                Log.w(TAG, "an audio listener threw - continuing", t);
            }
        }
    }

    public void addListener(Listener l) {
        if (l != null && !mListeners.contains(l)) mListeners.add(l);
    }

    public void removeListener(Listener l) {
        mListeners.remove(l);
    }

    private void onPlaybackLevels(float[] rms, float[] peak, int ch) {
        dispatch(l -> l.onPlaybackLevels(rms, peak, ch));
    }

    private void onPlaybackScope(float[] mono, int frames) {
        dispatch(l -> l.onPlaybackScope(mono, frames));
    }

    private void onPlaybackState(PlaybackEngine.State s, String title) {
        dispatch(l -> l.onPlaybackState(s, title));
    }

    private void onPlaybackPosition(long pos, long dur) {
        dispatch(l -> l.onPlaybackPosition(pos, dur));
    }

    private void fireState(State s) {
        mState = s;
        dispatch(l -> l.onEngineState(s));
    }

    private void fireError(String msg) {
        dispatch(l -> l.onError(msg));
    }

    // =============================================================== devices =
    /** enumerate every endpoint the platform will give us, plus attached USB units */
    public void refreshDevices() {
        mInputs.clear();
        mOutputs.clear();
        mUsbDevices = UsbAudioProbe.list(mContext);

        AudioDeviceInfo[] ins = new AudioDeviceInfo[0];
        AudioDeviceInfo[] outs = new AudioDeviceInfo[0];
        try {
            ins = mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            outs = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        } catch (Exception e) {
            Log.w(TAG, "device enumeration failed", e);
        }

        mInputs.add(AudioDevice.systemDefault(true));
        mOutputs.add(AudioDevice.systemDefault(false));

        for (AudioDeviceInfo info : ins) {
            UsbAudioProbe.UsbFacts f = matchUsb(info);
            AudioDevice d = AudioDevice.from(info, f);
            if (d.isUsb && f != null && mPrefs.directUsbClaim()) {
                // probe-only unless the operator enabled the direct claim path
                d.directClaimed = f.directClaimed;
            }
            mInputs.add(d);
        }
        for (AudioDeviceInfo info : outs) {
            UsbAudioProbe.UsbFacts f = matchUsb(info);
            AudioDevice d = AudioDevice.from(info, f);
            mOutputs.add(d);
        }

        // restore the last selection, preferring what is actually plugged in
        String wantIn = mPrefs.inputDeviceId();
        String wantOut = mPrefs.outputDeviceId();
        AudioDevice sel = find(mInputs, wantIn);
        if (sel == null) sel = pickBest(mInputs, true);
        mInput = sel;
        if (mInput != null && !mInput.key.equals(wantIn)) mPrefs.setInputDeviceId(mInput.key);

        sel = find(mOutputs, wantOut);
        if (sel == null) sel = pickBest(mOutputs, false);
        mOutput = sel;
        if (mOutput != null && !mOutput.key.equals(wantOut)) mPrefs.setOutputDeviceId(mOutput.key);

        dispatch(l -> l.onDevicesChanged());
    }

    private AudioDevice find(List<AudioDevice> list, String key) {
        if (key == null) return null;
        for (AudioDevice d : list) if (d.key.equals(key)) return d;
        return null;
    }

    /** prefer a USB interface over the built-in endpoints when one is attached */
    private AudioDevice pickBest(List<AudioDevice> list, boolean input) {
        AudioDevice bestUsb = null;
        for (AudioDevice d : list) {
            if (d.isUsb && (input ? d.maxChannels() > 0 : true)) {
                if (bestUsb == null || d.maxSampleRate() > bestUsb.maxSampleRate()) bestUsb = d;
            }
        }
        if (bestUsb != null) return bestUsb;
        for (AudioDevice d : list) if (d.id == -1) return d;
        return list.isEmpty() ? null : list.get(0);
    }

    private UsbAudioProbe.UsbFacts matchUsb(AudioDeviceInfo info) {
        CharSequence pn = info.getProductName();
        String name = pn == null ? "" : pn.toString().toLowerCase();
        UsbAudioProbe.UsbFacts best = null;
        for (UsbAudioProbe.UsbFacts f : mUsbDevices) {
            if (!UsbAudioProbe.looksLikeAudioInterface(f)) continue;
            String p = f.productName.toLowerCase();
            if (!name.isEmpty() && (p.contains(name) || name.contains(p))) return f;
            if (best == null) best = f;
        }
        return best;
    }

    public List<AudioDevice> inputs() {
        return mInputs;
    }

    public List<AudioDevice> outputs() {
        return mOutputs;
    }

    public List<UsbAudioProbe.UsbFacts> usbDevices() {
        return mUsbDevices;
    }

    public AudioDevice input() {
        return mInput;
    }

    public AudioDevice output() {
        return mOutput;
    }

    public void selectInput(AudioDevice d) {
        if (d == null) return;
        boolean wasRunning = mRunning;
        boolean wasRecording = mState == State.RECORDING || mState == State.PAUSED;
        if (wasRunning) stopCapture(wasRecording);
        mInput = d;
        mPrefs.setInputDeviceId(d.key);
        dispatch(l -> l.onDevicesChanged());
        if (wasRunning) startMonitor();
    }

    public void selectOutput(AudioDevice d) {
        if (d == null) return;
        mOutput = d;
        mPrefs.setOutputDeviceId(d.key);
        if (mRunning && mPrefs.monitor()) restartMonitorTrack();
        dispatch(l -> l.onDevicesChanged());
    }

    /**
     * Sample rates the selected input can actually run at.  Android reports these
     * per endpoint, which is how the app knows a Focusrite will do 192 kHz while
     * the built-in mic stops at 48 kHz.
     */
    public int[] availableRates() {
        int max = mInput == null ? 48000 : Math.max(8000, mInput.maxSampleRate());
        int[] upTo = Formats.ratesUpTo(max);
        // keep the native rates verbatim even if they are unusual (e.g. 88.2k)
        List<Integer> out = new ArrayList<>();
        for (int r : upTo) out.add(r);
        if (mInput != null) {
            for (int r : mInput.sampleRates) {
                if (r > 0 && !out.contains(r) && r <= max) out.add(r);
            }
        }
        Integer[] boxed = out.toArray(new Integer[0]);
        Arrays.sort(boxed);
        int[] result = new int[boxed.length];
        for (int i = 0; i < result.length; i++) result[i] = boxed[i];
        return result;
    }

    public int[] availableChannels() {
        int max = mInput == null ? 2 : Math.max(1, Math.min(8, mInput.maxChannels()));
        int[] all = {1, 2, 3, 4, 5, 6, 7, 8};
        List<Integer> out = new ArrayList<>();
        for (int c : all) if (c <= max) out.add(c);
        if (out.isEmpty()) out.add(1);
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    public int[] availableDepths() {
        List<Integer> out = new ArrayList<>();
        if (mInput == null || mInput.supports16) out.add(16);
        if (mInput == null || mInput.supports24 || mInput.supportsFloat) out.add(24);
        if (mInput != null && (mInput.supports32 || mInput.supportsFloat)) out.add(32);
        if (out.isEmpty()) out.add(16);
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    /** minimum buffer the platform will accept for this configuration */
    public int minBufferFrames(int sampleRate, int channels, int bitDepth) {
        int mask = channelMask(channels);
        int min = AudioRecord.getMinBufferSize(sampleRate, mask, mInputEncoding);
        if (min <= 0) min = 1024;
        int bytesPerFrame = Math.max(1, channels * (bitDepth / 8));
        return Math.max(64, min / bytesPerFrame);
    }

    private static int channelMask(int channels) {
        switch (channels) {
            case 1: return AudioFormat.CHANNEL_IN_MONO;
            case 2: return AudioFormat.CHANNEL_IN_STEREO;
            default: return AudioFormat.CHANNEL_IN_STEREO;   // indexed masks need >2 support
        }
    }

    /** per-channel trim (dB) applied before meters and disk, so the mix and the file agree */
    public void setChannelTrim(int channel, float db) {
        if (channel < 0 || channel > 7) return;
        float[] trims = mPrefs.channelTrims();
        trims[channel] = Math.max(-24f, Math.min(24f, db));
        mPrefs.setChannelTrims(trims);
    }

    public float channelTrim(int channel) {
        if (channel < 0 || channel > 7) return 0f;
        return mPrefs.channelTrims()[channel];
    }

    public void resetChannelTrims() {
        mPrefs.setChannelTrims(new float[8]);
    }

    /** apply the per-channel trims in place on an interleaved block */
    private void applyTrims(float[] buf, int frames, int channels) {
        float[] trims = mPrefs.channelTrims();
        boolean any = false;
        for (int c = 0; c < channels && c < trims.length; c++) {
            if (trims[c] != 0f) any = true;
        }
        if (!any) return;
        for (int c = 0; c < channels && c < trims.length; c++) {
            float g = Pcm.dbToLinear(trims[c]);
            if (g == 1f) continue;
            for (int f = 0; f < frames; f++) {
                int i = f * channels + c;
                buf[i] = Pcm.clampUnit(buf[i] * g);
            }
        }
    }

    public float[] levelsRms() {
        return mLevelRms;
    }

    public float[] levelsPeak() {
        return mLevelPeak;
    }

    public State state() {
        return mState;
    }

    public boolean isCapturing() {
        return mRunning;
    }

    public Recorder recorder() {
        return mRecorder;
    }

    public long recordingElapsedMs() {
        if (mState != State.RECORDING && mState != State.PAUSED) return 0;
        return System.currentTimeMillis() - mRecordingStartedAt;
    }

    // =============================================================== capture =
    /**
     * Open the input and start metering.  Monitoring (fold-back to the output)
     * follows the "monitor" preference; recording can start on top of this at
     * any moment without re-opening the device - which is what makes arming
     * while listening work.
     */
    public boolean startMonitor() {
        if (mRunning) return true;
        if (mInput == null) refreshDevices();
        if (mInput == null) {
            fireError("No audio input available");
            return false;
        }
        int rate = mPrefs.sampleRate();
        int channels = mPrefs.channels();
        int depth = mPrefs.bitDepth();
        if (mInput.sampleRates.length > 0 && !contains(mInput.sampleRates, rate)) {
            rate = nearest(mInput.sampleRates, rate);
            mPrefs.setSampleRate(rate);
        }
        int maxCh = mInput.maxChannels();
        if (maxCh > 0 && channels > maxCh) {
            channels = Math.max(1, Math.min(maxCh, 2));
            mPrefs.setChannels(channels);
        }

        mInputEncoding = chooseEncoding(depth);
        int mask = channelMask(channels);
        int minBuf = AudioRecord.getMinBufferSize(rate, mask, mInputEncoding);
        if (minBuf <= 0) minBuf = rate / 10 * channels * 2;
        int frameBytes = channels * bytesPerSample(mInputEncoding);
        int wantFrames = Math.max(mPrefs.bufferFrames(), minBuf / Math.max(1, frameBytes));
        int bufBytes = Math.max(minBuf, wantFrames * frameBytes);
        boolean stereoOnly = false;

        try {
            mRecord = buildAudioRecord(rate, channels, mask, mInputEncoding, bufBytes);
            if (mRecord == null && channels > 2) {
                // the platform USB route exposes a stereo stream for most
                // interfaces; retry as stereo rather than refusing to record
                stereoOnly = true;
                channels = 2;
                mask = channelMask(channels);
                minBuf = AudioRecord.getMinBufferSize(rate, mask, mInputEncoding);
                if (minBuf <= 0) minBuf = rate / 10 * channels * 2;
                frameBytes = channels * bytesPerSample(mInputEncoding);
                wantFrames = Math.max(mPrefs.bufferFrames(), minBuf / Math.max(1, frameBytes));
                bufBytes = Math.max(minBuf, wantFrames * frameBytes);
                mRecord = buildAudioRecord(rate, channels, mask, mInputEncoding, bufBytes);
            }
            if (mRecord == null) {
                // fall back to 16-bit, which every device supports
                mInputEncoding = AudioFormat.ENCODING_PCM_16BIT;
                mRecord = buildAudioRecord(rate, channels, mask, mInputEncoding, bufBytes);
            }
            if (mRecord == null) {
                fireError("Could not open the input device");
                return false;
            }
            mRecord.startRecording();
            if (mRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                fireError("Input device refused to start");
                closeRecord();
                return false;
            }
        } catch (Exception e) {
            fireError("Input error: " + e.getMessage());
            closeRecord();
            return false;
        }

        if (stereoOnly) {
            mPrefs.setChannels(2);
            postError("The interface accepted a stereo capture stream only - channels set to 2. "
                    + "Use direct USB claim, or a UAC2 multichannel mode, for more inputs.");
        }
        mActiveChannels = channels;
        mBlockFrames = Math.min(16384, Math.max(256, wantFrames));
        mBlock = new float[mBlockFrames * channels];
        mMonitorBlock = new float[mBlockFrames * channels];
        mSessionId++;
        mXruns = 0;

        if (mPrefs.monitor()) startMonitorTrack(rate, channels);

        mRunning = true;
        mCaptureThread = new Thread(this::captureLoop, "audiorec-capture");
        mCaptureThread.setPriority(Thread.MAX_PRIORITY);
        mCaptureThread.start();
        fireState(mPrefs.monitor() ? State.MONITORING : State.RECORDING);
        if (!mPrefs.monitor()) fireState(State.MONITORING);   // still metering
        return true;
    }

    private AudioRecord buildAudioRecord(int rate, int channels, int mask, int encoding, int bufBytes)
            throws Exception {
        AudioRecord rec;
        int[] sources = candidateSources();
        rec = null;
        for (int src : sources) {
            try {
                rec = newInstance(src, rate, channels, mask, encoding, bufBytes);
                if (rec != null && rec.getState() == AudioRecord.STATE_INITIALIZED) break;
                if (rec != null) {
                    rec.release();
                    rec = null;
                }
            } catch (Exception ignored) {
                rec = null;
            }
        }
        if (rec == null) return null;
        if (mInput != null && mInput.id >= 0) {
            AudioDeviceInfo info = findInfoById(mInput.id);
            if (info != null) rec.setPreferredDevice(info);
        }
        // disable the platform "help": we want the raw converter output
        try {
            if (rec.getAudioSessionId() != 0) {
                if (mAudioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) != null) {
                    // UNPROCESSED already means no AGC/NS/AEC
                }
            }
        } catch (Exception ignored) {
        }
        return rec;
    }

    private AudioRecord newInstance(int source, int rate, int channels, int mask, int encoding,
                                    int bufBytes) {
        int minBuf = AudioRecord.getMinBufferSize(rate, mask, encoding);
        int size = Math.max(bufBytes, minBuf > 0 ? minBuf : bufBytes);
        // multichannel capture needs an index mask; the positional masks only
        // describe mono and stereo
        if (channels > 2 && Build.VERSION.SDK_INT >= 31) {
            try {
                AudioFormat indexFmt = new AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(rate)
                        .setChannelIndexMask((1 << channels) - 1)
                        .build();
                return new AudioRecord.Builder()
                        .setAudioSource(source)
                        .setAudioFormat(indexFmt)
                        .setBufferSizeInBytes(size)
                        .build();
            } catch (Exception ignored) {
                // fall through to the positional mask
            }
        }
        AudioFormat fmt = new AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(rate)
                .setChannelMask(mask)
                .build();
        return new AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(fmt)
                .setBufferSizeInBytes(size)
                .build();
    }

    private int[] candidateSources() {
        AudioManager am = mAudioManager;
        List<Integer> list = new ArrayList<>();
        boolean unprocessed = false;
        try {
            unprocessed = am != null && "true".equalsIgnoreCase(
                    am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED));
        } catch (Exception ignored) {
        }
        if (unprocessed) list.add(MediaRecorder.AudioSource.UNPROCESSED);
        list.add(MediaRecorder.AudioSource.MIC);
        list.add(MediaRecorder.AudioSource.DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && unprocessed) {
            list.add(MediaRecorder.AudioSource.VOICE_RECOGNITION);
        }
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    private AudioDeviceInfo findInfoById(int id) {
        try {
            for (AudioDeviceInfo i : mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
                if (i.getId() == id) return i;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** prefer the deepest encoding the endpoint advertises */
    private int chooseEncoding(int requestedDepth) {
        if (mInput == null) return AudioFormat.ENCODING_PCM_16BIT;
        // the packed 24-bit and 32-bit integer encodings only exist from API 31;
        // below that the float path carries the same resolution internally
        boolean deep = Build.VERSION.SDK_INT >= 31;
        if (deep && requestedDepth >= 24 && mInput.supports24) {
            return AudioFormat.ENCODING_PCM_24BIT_PACKED;
        }
        if (mInput.supportsFloat) return AudioFormat.ENCODING_PCM_FLOAT;
        if (deep && requestedDepth >= 32 && mInput.supports32) {
            return AudioFormat.ENCODING_PCM_32BIT;
        }
        return AudioFormat.ENCODING_PCM_16BIT;
    }

    private static int bytesPerSample(int encoding) {
        switch (encoding) {
            case AudioFormat.ENCODING_PCM_FLOAT:
            case AudioFormat.ENCODING_PCM_32BIT:
                return 4;
            case AudioFormat.ENCODING_PCM_24BIT_PACKED:
                return 3;
            default:
                return 2;
        }
    }

    private static boolean contains(int[] arr, int v) {
        for (int a : arr) if (a == v) return true;
        return false;
    }

    private static int nearest(int[] arr, int v) {
        int best = arr.length > 0 ? arr[0] : v;
        for (int a : arr) {
            if (Math.abs(a - v) < Math.abs(best - v)) best = a;
        }
        return best;
    }

    // ---------------------------------------------------------------- thread -

    /**
     * Runs the capture loop and makes sure nothing escapes it.
     *
     * Whatever happens below - a device read that fails, a writer that throws,
     * a listener that blows up, a bug of ours - the capture thread must never
     * die from an uncaught exception.  If it does, Android tears the process
     * down and the operator sees "AUDIO-rec keeps stopping" with the take
     * unfinished.  Here the take is closed properly and the reason is reported.
     */
    private void captureLoop() {
        try {
            runCapture();
        } catch (Throwable t) {
            Log.w(TAG, "capture thread failed", t);
            Recorder rec = mRecorder;
            if (rec != null) {
                try {
                    Recorder.Result r = rec.stop();
                    dispatch(l -> l.onRecordingFinished(r));
                } catch (Throwable ignored) {
                }
                mRecorder = null;
            }
            postError("Capture stopped: " + t);
        } finally {
            mRunning = false;
            closeRecord();
            fireState(State.IDLE);
        }
    }

    private void runCapture() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        final int channels = mActiveChannels;
        final int encoding = mInputEncoding;
        final int frames = mBlockFrames;
        final int samples = frames * channels;

        byte[] byteBuf = new byte[samples * bytesPerSample(encoding)];
        short[] shortBuf = needsShortBuffer(encoding) ? new short[samples] : null;
        float[] floatBuf = needsFloatBuffer(encoding) ? new float[samples] : null;
        int[] intBuf = encoding == AudioFormat.ENCODING_PCM_32BIT ? new int[samples] : null;

        long lastTickAt = 0;
        while (mRunning) {
            int read;
            int decoded = 0;               // samples this read actually produced
            try {
                if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                    read = mRecord.read(floatBuf, 0, samples, AudioRecord.READ_BLOCKING);
                    if (read > 0) {
                        System.arraycopy(floatBuf, 0, mBlock, 0, read);
                        decoded = read;        // a float read counts samples
                    }
                } else {
                    read = mRecord.read(byteBuf, 0, byteBuf.length, AudioRecord.READ_BLOCKING);
                    if (read > 0) {
                        // a PCM read counts bytes - convert before anything else
                        decoded = decodeBytes(byteBuf, read, encoding, mBlock, shortBuf, intBuf);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "capture read failed", e);
                break;
            }
            if (read <= 0) {
                if (read == AudioRecord.ERROR_DEAD_OBJECT) {
                    mXruns++;
                    if (mRecorder != null) mRecorder.countXrun();
                    postError("USB interface disconnected");
                    break;
                }
                if (read == AudioRecord.ERROR_INVALID_OPERATION) break;
                if (read == AudioRecord.ERROR_BAD_VALUE) break;
                continue;
            }
            // never let a miscalculated block index past the buffer: a crash on
            // this thread is a crash of the whole app
            if (decoded > mBlock.length) decoded = mBlock.length;
            int framesRead = decoded / channels;
            if (framesRead <= 0) continue;
            int sampleCount = framesRead * channels;

            // ---- per-channel trim, then meters/scope, so the displays show
            //      exactly what will be written to disk
            applyTrims(mBlock, framesRead, channels);
            Pcm.analyse(mBlock, framesRead, channels, mLevelPeak, mLevelRms);
            long now = System.currentTimeMillis();
            if (now - mLastMeterAt >= METER_INTERVAL_MS) {
                mLastMeterAt = now;
                float[] rmsCopy = Arrays.copyOf(mLevelRms, channels);
                float[] peakCopy = Arrays.copyOf(mLevelPeak, channels);
                dispatch(l -> l.onLevels(rmsCopy, peakCopy, channels));
                float[] scope = decimate(mBlock, framesRead, channels);
                dispatch(l -> l.onScope(scope, framesRead / SCOPE_DECIMATE, channels));
            }

            // ---- monitoring fold-back
            if (mPrefs.monitor() && mMonitorTrack != null) {
                System.arraycopy(mBlock, 0, mMonitorBlock, 0, sampleCount);
                Pcm.applyGain(mMonitorBlock, sampleCount, Pcm.dbToLinear(mPrefs.monitorGainDb()));
                if (mPrefs.mute()) Arrays.fill(mMonitorBlock, 0, sampleCount, 0f);
                Pcm.clampUnit(mMonitorBlock, sampleCount);
                try {
                    mMonitorTrack.write(mMonitorBlock, 0, sampleCount, AudioTrack.WRITE_NON_BLOCKING);
                } catch (Exception ignored) {
                }
            }

            // ---- recording
            Recorder rec = mRecorder;
            if (rec != null && rec.isOpen() && !rec.isPaused()) {
                try {
                    rec.write(mBlock, sampleCount, mPrefs.gainDb());
                } catch (Exception e) {
                    postError("Write failed: " + e.getMessage());
                    rec.countDrop();
                }
                long t = System.currentTimeMillis();
                if (t - lastTickAt > 200) {
                    lastTickAt = t;
                    long frames2 = rec.result().frames;
                    long bytes = rec.bytesWritten();
                    long elapsed = t - mRecordingStartedAt;
                    dispatch(l -> l.onRecordingTick(frames2, bytes, elapsed));
                }
            }
        }
        // ---- teardown
        Recorder rec = mRecorder;
        boolean wasRecording = rec != null && rec.isOpen();
        if (wasRecording) {
            Recorder.Result r = rec.stop();
            dispatch(l -> l.onRecordingFinished(r));
        }
        mRecorder = null;
        closeRecord();
        mRunning = false;
        fireState(State.IDLE);
    }

    /** decimate for the scope: min/max peak per bucket keeps the envelope honest */
    private float[] decimate(float[] buf, int frames, int channels) {
        int out = Math.max(1, frames / SCOPE_DECIMATE);
        float[] r = new float[out * channels];
        for (int i = 0; i < out; i++) {
            int from = i * SCOPE_DECIMATE;
            int to = Math.min(frames, from + SCOPE_DECIMATE);
            for (int c = 0; c < channels; c++) {
                float mn = 1f, mx = -1f;
                for (int f = from; f < to; f++) {
                    float v = buf[f * channels + c];
                    if (v < mn) mn = v;
                    if (v > mx) mx = v;
                }
                r[i * channels + c] = (mn + mx) * 0.5f;
            }
        }
        return r;
    }

    private static boolean needsShortBuffer(int encoding) {
        return encoding == AudioFormat.ENCODING_PCM_16BIT;
    }

    private static boolean needsFloatBuffer(int encoding) {
        return false;   // float is read straight into mBlock
    }

    /** @return the number of samples written into {@code dst} */
    private int decodeBytes(byte[] src, int byteCount, int encoding, float[] dst,
                            short[] shortBuf, int[] intBuf) {
        int n;
        switch (encoding) {
            case AudioFormat.ENCODING_PCM_24BIT_PACKED:
                n = Pcm.samplesFromBytes(byteCount, 3);
                Pcm.int24ToFloat(src, n, dst);
                return n;
            case AudioFormat.ENCODING_PCM_32BIT: {
                n = Pcm.samplesFromBytes(byteCount, 4);
                for (int i = 0; i < n; i++) {
                    int v = (src[i * 4] & 0xFF) | ((src[i * 4 + 1] & 0xFF) << 8)
                            | ((src[i * 4 + 2] & 0xFF) << 16) | (src[i * 4 + 3] << 24);
                    dst[i] = (float) (v / 2147483648.0);
                }
                return n;
            }
            case AudioFormat.ENCODING_PCM_FLOAT: {
                n = Pcm.samplesFromBytes(byteCount, 4);
                for (int i = 0; i < n; i++) {
                    int v = (src[i * 4] & 0xFF) | ((src[i * 4 + 1] & 0xFF) << 8)
                            | ((src[i * 4 + 2] & 0xFF) << 16) | (src[i * 4 + 3] << 24);
                    dst[i] = Float.intBitsToFloat(v);
                }
                return n;
            }
            default: {
                n = Pcm.samplesFromBytes(byteCount, 2);
                for (int i = 0; i < n; i++) {
                    int v = (src[i * 2] & 0xFF) | (src[i * 2 + 1] << 8);
                    dst[i] = v * (1f / 32768f);
                }
                return n;
            }
        }
    }

    // ------------------------------------------------------------- monitoring
    private void startMonitorTrack(int rate, int channels) {
        stopMonitorTrack();
        try {
            int mask = channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
            int minBuf = AudioTrack.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_FLOAT);
            if (minBuf <= 0) minBuf = rate * channels * 4 / 10;
            int bufSize = Math.max(minBuf, mPrefs.bufferFrames() * channels * 4);
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(rate)
                            .setChannelMask(mask)
                            .build())
                    .setBufferSizeInBytes(bufSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                track.release();
                return;
            }
            if (mOutput != null && mOutput.id >= 0) {
                try {
                    for (AudioDeviceInfo info : mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                        if (info.getId() == mOutput.id) {
                            track.setPreferredDevice(info);
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            track.play();
            mMonitorTrack = track;
        } catch (Exception e) {
            Log.w(TAG, "monitor track failed", e);
        }
    }

    private void restartMonitorTrack() {
        if (mRunning) startMonitorTrack(mPrefs.sampleRate(), mActiveChannels);
    }

    private void stopMonitorTrack() {
        if (mMonitorTrack != null) {
            try {
                mMonitorTrack.stop();
            } catch (Exception ignored) {
            }
            try {
                mMonitorTrack.release();
            } catch (Exception ignored) {
            }
            mMonitorTrack = null;
        }
    }

    /** monitoring on/off while the capture thread keeps running */
    public void setMonitoring(boolean on) {
        mPrefs.setMonitor(on);
        if (on && mRunning && mMonitorTrack == null) {
            startMonitorTrack(mPrefs.sampleRate(), mActiveChannels);
        } else if (!on) {
            stopMonitorTrack();
        }
        fireState(on ? (mRecorder != null && mRecorder.isOpen() ? State.RECORDING : State.MONITORING)
                : (mRecorder != null && mRecorder.isOpen() ? State.RECORDING : State.MONITORING));
    }

    public void setMonitorGainDb(float db) {
        mPrefs.setMonitorGainDb(db);
    }

    public void setMute(boolean mute) {
        mPrefs.setMute(mute);
    }

    // ============================================================= recording =
    public boolean startRecording(Recorder.Config cfg) {
        if (!mRunning) {
            if (!startMonitor()) return false;
        }
        if (mRecorder != null && mRecorder.isOpen()) return false;
        try {
            Recorder r = new Recorder();
            r.start(cfg);
            mRecorder = r;
            mRecordingStartedAt = System.currentTimeMillis();
            fireState(State.RECORDING);
            return true;
        } catch (Exception e) {
            fireError("Could not start recording: " + e.getMessage());
            return false;
        }
    }

    public void pauseRecording() {
        if (mRecorder != null && mRecorder.isOpen()) {
            mRecorder.pause();
            fireState(State.PAUSED);
        }
    }

    public void resumeRecording() {
        if (mRecorder != null && mRecorder.isOpen()) {
            mRecorder.resume();
            fireState(State.RECORDING);
        }
    }

    public Recorder.Result stopRecording() {
        if (mRecorder == null) return null;
        Recorder r = mRecorder;
        mRecorder = null;
        Recorder.Result res = r.stop();
        fireState(mRunning ? (mPrefs.monitor() ? State.MONITORING : State.MONITORING) : State.IDLE);
        return res;
    }

    /** stop the capture thread entirely (and any running take) */
    public void stopCapture(boolean finishTake) {
        if (!mRunning) return;
        if (mRecorder != null && mRecorder.isOpen()) {
            if (finishTake) {
                Recorder.Result res = mRecorder.stop();
                mRecorder = null;
                dispatch(l -> l.onRecordingFinished(res));
            } else {
                mRecorder = null;
            }
        }
        mRunning = false;
        Thread t = mCaptureThread;
        if (t != null) {
            try {
                t.join(1500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            mCaptureThread = null;
        }
        stopMonitorTrack();
        closeRecord();
        fireState(State.IDLE);
    }

    private void closeRecord() {
        if (mRecord != null) {
            try {
                if (mRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    mRecord.stop();
                }
            } catch (Exception ignored) {
            }
            try {
                mRecord.release();
            } catch (Exception ignored) {
            }
            mRecord = null;
        }
    }

    private void postError(final String msg) {
        Log.w(TAG, msg);
        dispatch(l -> l.onError(msg));
    }

    // ============================================================== playback =
    public PlaybackEngine playback() {
        return mPlayback;
    }

    public boolean play(File file, String title) {
        AudioDevice out = mOutput;
        int deviceId = out == null ? -1 : out.id;
        return mPlayback.play(file, title, deviceId);
    }

    public void stopPlayback() {
        mPlayback.stop();
    }

    public void release() {
        stopCapture(false);
        stopMonitorTrack();
        mPlayback.release();
        mListeners.clear();
    }
}
