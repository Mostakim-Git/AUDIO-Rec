package com.mostakim.audiorec.ui.screens;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mostakim.audiorec.App;
import com.mostakim.audiorec.R;
import com.mostakim.audiorec.audio.AudioDevice;
import com.mostakim.audiorec.audio.AudioEngine;
import com.mostakim.audiorec.audio.FormatProbe;
import com.mostakim.audiorec.audio.Pcm;
import com.mostakim.audiorec.audio.Recorder;
import com.mostakim.audiorec.db.Models.Preset;
import com.mostakim.audiorec.db.Models.Session;
import com.mostakim.audiorec.db.Models.Track;
import com.mostakim.audiorec.ui.Dialogs;
import com.mostakim.audiorec.ui.MainActivity;
import com.mostakim.audiorec.ui.kit.Ui;
import com.mostakim.audiorec.ui.widgets.FaderStrip;
import com.mostakim.audiorec.ui.widgets.LevelMeterView;
import com.mostakim.audiorec.ui.widgets.SpectrumView;
import com.mostakim.audiorec.ui.widgets.WaveScopeView;
import com.mostakim.audiorec.util.Fmt;
import com.mostakim.audiorec.util.Formats;
import com.mostakim.audiorec.util.Ids;

import java.io.File;
import java.util.List;

/**
 * The recorder itself: transport, meters, routing and the capture format - the
 * screen the whole app exists for.
 */
public class RecorderScreen extends Screen implements AudioEngine.Listener {

    private LevelMeterView mCaptureMeters, mPlaybackMeters;
    private WaveScopeView mScope;
    private SpectrumView mSpectrum;
    private FaderStrip mGain, mMonitorGain;
    private TextView mTimer, mPeakReadout, mStatusLine, mFileName, mDiskLine, mSessionLine;
    private TextView mRecordBtn, mMonitorBtn, mPauseBtn, mStopBtn;
    private LinearLayout mConfigRow, mTakeActions;
    private TextView mScopeToggle, mAnalyserBtn, mFreezeBtn;
    private boolean mScopeVisible = true;
    private boolean mFrozen = false;
    private boolean mScopeIsSpectrum = false;

    public RecorderScreen(MainActivity a) {
        super(a, "Recorder", "Bit-perfect capture from the selected interface", true);
    }

    @Override
    protected void build(LinearLayout col) {
        transport();
        metersCard();
        scopeCard();
        formatCard();
        destinationCard();
    }

    // -------------------------------------------------------------- transport
    private void transport() {
        LinearLayout card = cardStyled(R.drawable.bg_tile);

        LinearLayout top = Ui.row(act);
        mStatusLine = Ui.text(act, "", R.style.T_Section);
        top.addView(mStatusLine, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mTimer = Ui.text(act, "00:00.0", R.style.T_Display);
        mTimer.setTypeface(android.graphics.Typeface.MONOSPACE);
        mTimer.setTextColor(th.textPrimary);
        mTimer.setSingleLine(true);
        top.addView(mTimer);
        Ui.addWide(card, top);

        mFileName = Ui.caption(act, "");
        mFileName.setSingleLine(true);
        mFileName.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        Ui.addWide(card, mFileName);

        card.addView(Ui.spacer(act, 12));

        // the button the whole app exists for: full width, at thumb height
        mRecordBtn = Ui.button(act, "\u25cf  Arm & record", R.style.Btn_Rec, v -> onRecord());
        mRecordBtn.setTextSize(17);
        card.addView(mRecordBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 58)));

        card.addView(Ui.spacer(act, 10));
        LinearLayout second = Ui.row(act);
        mPauseBtn = Ui.button(act, "II  Pause", R.style.Btn, v -> togglePause());
        second.addView(mPauseBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        second.addView(Ui.spacer(act, 8));
        mStopBtn = Ui.button(act, "\u25a0  Stop", R.style.Btn, v -> onStop());
        second.addView(mStopBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        second.addView(Ui.spacer(act, 8));
        mMonitorBtn = Ui.button(act, "Monitor: off", R.style.Btn, v -> onMonitor());
        second.addView(mMonitorBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Ui.addWide(card, second);

        card.addView(Ui.spacer(act, 8));
        mTakeActions = Ui.row(act);
        // filled in when a take has just been saved; until then it is not there at all
        mTakeActions.setVisibility(View.GONE);
        Ui.addWide(card, mTakeActions);
    }

    // ----------------------------------------------------------------- levels
    private void metersCard() {
        LinearLayout card = card("Levels", "peak hold 2.5 s");

        mPeakReadout = Ui.text(act, "peak \u2014", R.style.T_Caption);
        mPeakReadout.setTextColor(th.textTertiary);
        Ui.addWide(card, mPeakReadout);

        card.addView(Ui.spacer(act, 8));

        // the two meters share the width evenly - no fixed sizes, so the same
        // layout lands correctly on a 16:9 phone and on a tablet
        LinearLayout meters = Ui.row(act);
        LinearLayout captureCol = Ui.column(act);
        captureCol.addView(Ui.text(act, "INPUT", R.style.T_Section));
        mCaptureMeters = new LevelMeterView(act);
        mCaptureMeters.setChannelCount(App.get().prefs().channels());
        mCaptureMeters.setPeakHoldMs(2500);
        mCaptureMeters.setOnPeaksCleared(() -> {
            toast("Peak hold cleared");
            updatePeakReadout();
        });
        mCaptureMeters.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 190)));
        Ui.addWide(captureCol, mCaptureMeters);
        meters.addView(captureCol, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        meters.addView(Ui.spacer(act, 8));

        LinearLayout playCol = Ui.column(act);
        playCol.addView(Ui.text(act, "OUTPUT", R.style.T_Section));
        mPlaybackMeters = new LevelMeterView(act);
        mPlaybackMeters.setChannelCount(2);
        mPlaybackMeters.setShowScale(false);
        mPlaybackMeters.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 190)));
        Ui.addWide(playCol, mPlaybackMeters);
        meters.addView(playCol, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Ui.addWide(card, meters);

        card.addView(Ui.spacer(act, 12));

        // gain and monitor: a fader each, plus the buttons that move them by
        // exactly 0.1 dB
        LinearLayout faders = Ui.row(act);
        mGain = FaderStrip.gain(act);
        mGain.setOnValueChanged((db, done) -> {
            App.get().prefs().setGainDb(db);
            if (done) {
                refreshPeakWarn();
                act.refreshHeader();
            }
        });
        faders.addView(mGain, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        faders.addView(Ui.spacer(act, 6));
        mMonitorGain = FaderStrip.monitor(act);
        faders.addView(mMonitorGain, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Ui.addWide(card, faders);

        card.addView(Ui.caption(act, "Tap a meter to clear its peak hold \u00b7 drag a fader, "
                + "or use its \u22120.1 / +0.1 buttons"));
        card.addView(Ui.spacer(act, 6));
        LinearLayout extra = Ui.row(act);
        extra.addView(Ui.button(act, "Preset", R.style.Btn_Small, v -> showPresetPicker()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        extra.addView(Ui.spacer(act, 6));
        extra.addView(Ui.button(act, "Mixer", R.style.Btn_Small,
                        v -> navigate(MainActivity.PAGE_MIXER)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        extra.addView(Ui.spacer(act, 6));
        extra.addView(Ui.button(act, "Devices", R.style.Btn_Small,
                        v -> navigate(MainActivity.PAGE_DEVICES)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Ui.addWide(card, extra);
    }

    // ------------------------------------------------------------------ scope
    private void scopeCard() {
        LinearLayout card = card("Signal", null);
        // the label gets its own line: three buttons and a caption never fit beside
        // each other on a 720 px phone
        card.addView(Ui.body(act, "Scope / spectrum"));

        mScopeToggle = Ui.button(act, "Waveform", R.style.Btn_Small, v -> {
            boolean show = mScope.getVisibility() != View.VISIBLE;
            mScope.setVisibility(show ? View.VISIBLE : View.GONE);
            mScopeToggle.setText(show ? "Waveform on" : "Waveform off");
        });
        mScopeToggle.setText("Waveform on");
        mAnalyserBtn = Ui.button(act, "Analyser on", R.style.Btn_Small, v -> {
            boolean show = mSpectrum.getVisibility() != View.VISIBLE;
            mSpectrum.setVisibility(show ? View.VISIBLE : View.GONE);
            mAnalyserBtn.setText(show ? "Analyser on" : "Analyser off");
        });
        mFreezeBtn = Ui.button(act, "Freeze", R.style.Btn_Small, v -> {
            mFrozen = !mFrozen;
            mScope.setFrozen(mFrozen);
            mSpectrum.setFrozen(mFrozen);
            mFreezeBtn.setText(mFrozen ? "Frozen" : "Freeze");
            toast(mFrozen ? "Display frozen" : "Display live");
        });

        LinearLayout row = Ui.row(act);
        row.addView(mScopeToggle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.spacer(act, 6));
        row.addView(mAnalyserBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.spacer(act, 6));
        row.addView(mFreezeBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Ui.addWide(card, row);

        card.addView(Ui.spacer(act, 8));
        // both displays are live from the moment the app opens: the waveform for
        // the shape of the signal, the spectrum for what is in it
        mScope = new WaveScopeView(act);
        card.addView(mScope, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 96)));
        card.addView(Ui.spacer(act, 6));
        mSpectrum = new SpectrumView(act);
        mSpectrum.setSampleRate(App.get().prefs().sampleRate());
        mSpectrum.setChannelCount(App.get().prefs().channels());
        mSpectrum.setVisibility(View.VISIBLE);
        // tap the analyser to drop its peak caps, like the meters
        mSpectrum.setOnClickListener(v -> {
            mSpectrum.clearCaps();
            toast("Spectrum peaks cleared");
        });
        card.addView(mSpectrum, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 112)));
        card.addView(Ui.caption(act, "Real time \u00b7 40 Hz to 20 kHz \u00b7 tap to clear the "
                + "falling peaks. Feed it from the interface while monitoring, or from "
                + "playback while auditioning a take."));
    }

    // ----------------------------------------------------------------- format
    private void formatCard() {
        LinearLayout card = card("Capture format", "changing this arms a new take");
        mConfigRow = Ui.column(act);
        Ui.addWide(card, mConfigRow);
        rebuildConfig();
    }

    private void rebuildConfig() {
        if (mConfigRow == null) return;
        mConfigRow.removeAllViews();
        AudioEngine engine = act.engine();
        AudioDevice in = engine.input();

        int[] rates = engine.availableRates();
        int[] depths = engine.availableDepths();
        int[] channels = engine.availableChannels();

        row(mConfigRow, "Interface", in == null ? "\u2014" : in.name,
                in != null && in.isUsb ? th.accent : th.textPrimary);
        row(mConfigRow, "Sample rate", Fmt.khz(App.get().prefs().sampleRate())
                + "  (up to " + Fmt.khz(in == null ? 48000 : Math.max(8000, in.maxSampleRate())) + ")",
                th.textPrimary);
        row(mConfigRow, "Bit depth", App.get().prefs().bitDepth() + "-bit  ("
                + (in == null ? "device default" : in.bitDepthLabel() + " available") + ")",
                th.textPrimary);
        row(mConfigRow, "Channels", App.get().prefs().channels() + "  ("
                + (in == null ? "?" : String.valueOf(in.maxChannels())) + " max on this input)",
                th.textPrimary);
        row(mConfigRow, "Buffer", App.get().prefs().bufferFrames() + " frames  ("
                + String.format(java.util.Locale.US, "%.1f",
                App.get().prefs().bufferFrames() * 1000.0 / App.get().prefs().sampleRate())
                + " ms)", th.textPrimary);
        row(mConfigRow, "Container", Formats.displayName(App.get().prefs().container()),
                Formats.isLossless(App.get().prefs().container()) ? th.ok : th.warn);

        mConfigRow.addView(Ui.spacer(act, 10));
        // two rows of buttons, weighted, so nothing is clipped on a narrow screen
        LinearLayout first = Ui.row(act);
        first.addView(Ui.button(act, "Rate", R.style.Btn_Small, v -> pickRate(rates)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        first.addView(Ui.spacer(act, 6));
        first.addView(Ui.button(act, "Depth", R.style.Btn_Small, v -> pickDepth(depths)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        first.addView(Ui.spacer(act, 6));
        first.addView(Ui.button(act, "Channels", R.style.Btn_Small, v -> pickChannels(channels)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Ui.addWide(mConfigRow, first);
        mConfigRow.addView(Ui.spacer(act, 6));
        LinearLayout second = Ui.row(act);
        second.addView(Ui.button(act, "Buffer", R.style.Btn_Small, v -> pickBuffer()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        second.addView(Ui.spacer(act, 6));
        second.addView(Ui.button(act, "Format", R.style.Btn_Small, v -> pickContainer()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        second.addView(Ui.spacer(act, 6));
        second.addView(Ui.button(act, "Devices", R.style.Btn_Small,
                        v -> navigate(MainActivity.PAGE_DEVICES)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Ui.addWide(mConfigRow, second);
    }

    /** label and value share the width; the value never gets clipped to a column */
    private void row(LinearLayout parent, String key, String value, int color) {
        LinearLayout r = Ui.row(act);
        TextView k = Ui.caption(act, key);
        k.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f));
        r.addView(k);
        TextView v = Ui.body(act, value);
        v.setTextColor(color);
        r.addView(v, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        r.setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 4));
        Ui.addWide(parent, r);
    }

    // -------------------------------------------------------------- destination
    private void destinationCard() {
        LinearLayout card = card("Destination", null);
        mSessionLine = Ui.body(act, "");
        Ui.addWide(card, mSessionLine);
        mDiskLine = Ui.caption(act, "");
        Ui.addWide(card, mDiskLine);
        card.addView(Ui.spacer(act, 6));
        card.addView(Ui.caption(act, "Recorder keeps it simple: it writes to the folder you "
                + "pick, or to the app's own folder when you leave it alone. Exports and "
                + "shares make their own copies."));
        card.addView(Ui.spacer(act, 10));
        LinearLayout r = Ui.row(act);
        r.addView(Ui.button(act, "Change folder", R.style.Btn_Small,
                        v -> Dialogs.choose(act, "Recording folder", new String[]{
                                "Replace with manual path\u2026", "Use device storage"},
                                0, idx -> {
                                    if (idx == 0) pickFolderManually();
                                    else {
                                        App.get().prefs().setRecordDir(App.defaultRecordDir(act));
                                        refresh();
                                        toast("Recording to device storage");
                                    }
                                })),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(Ui.spacer(act, 6));
        r.addView(Ui.button(act, "Storage", R.style.Btn_Small,
                        v -> navigate(MainActivity.PAGE_STORAGE)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Ui.addWide(card, r);
    }

    private void pickFolderManually() {
        Ui.prompt(act, "Recording folder", "/storage/XXXX-XXXX/Music/Recordings",
                folderPath(), false, false, value -> {
                    if (value == null || value.isEmpty()) return;
                    File f = new File(value);
                    if (!f.exists() && !f.mkdirs()) {
                        toast("Cannot create " + value);
                        return;
                    }
                    if (!f.canWrite()) {
                        toast("That folder is not writable");
                        return;
                    }
                    App.get().prefs().setRecordDir(f);
                    refresh();
                    toast("Recording to " + f.getAbsolutePath());
                });
    }

    private String folderPath() {
        File d = App.get().prefs().recordDir();
        return d == null ? "" : d.getAbsolutePath();
    }

    // ------------------------------------------------------------------ config
    private void pickRate(int[] rates) {
        String[] labels = new String[rates.length];
        for (int i = 0; i < rates.length; i++) {
            labels[i] = Fmt.khz(rates[i]) + "  \u00b7  " + rates[i] + " Hz";
        }
        Dialogs.choose(act, "Sample rate", labels,
                Math.max(0, Formats.indexOf(rates, App.get().prefs().sampleRate())), idx -> {
                    App.get().prefs().setSampleRate(rates[idx]);
                    mSpectrum.setSampleRate(rates[idx]);
                    afterConfigChange();
                });
    }

    private void pickDepth(int[] depths) {
        String[] labels = new String[depths.length];
        for (int i = 0; i < depths.length; i++) {
            labels[i] = depths[i] + "-bit" + (depths[i] == 32 ? " float" : "");
        }
        Dialogs.choose(act, "Bit depth", labels,
                Math.max(0, Formats.indexOf(depths, App.get().prefs().bitDepth())), idx -> {
                    App.get().prefs().setBitDepth(depths[idx]);
                    afterConfigChange();
                });
    }

    private void pickChannels(int[] channels) {
        String[] labels = new String[channels.length];
        for (int i = 0; i < channels.length; i++) {
            labels[i] = channels[i] + (channels[i] == 1 ? "  (mono)" : (channels[i] == 2
                    ? "  (stereo)" : "  channels"));
        }
        Dialogs.choose(act, "Channels", labels,
                Math.max(0, Formats.indexOf(channels, App.get().prefs().channels())), idx -> {
                    App.get().prefs().setChannels(channels[idx]);
                    mCaptureMeters.setChannelCount(channels[idx]);
                    afterConfigChange();
                });
    }

    private void pickBuffer() {
        int[] sizes = Formats.BUFFER_SIZES;
        String[] labels = new String[sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            labels[i] = sizes[i] + " frames  \u00b7  " + String.format(java.util.Locale.US,
                    "%.1f ms @ %s", sizes[i] * 1000.0 / App.get().prefs().sampleRate(),
                    Fmt.khz(App.get().prefs().sampleRate()));
        }
        Dialogs.choose(act, "Buffer size", labels,
                Math.max(0, Formats.indexOf(sizes, App.get().prefs().bufferFrames())), idx -> {
                    App.get().prefs().setBufferFrames(sizes[idx]);
                    afterConfigChange();
                    toast("Buffer " + sizes[idx] + " frames. Large buffers survive slow storage; "
                            + "small ones keep monitoring tight.");
                });
    }

    private void pickContainer() {
        String[] labels = {"WAV \u00b7 uncompressed, maximum compatibility",
                "FLAC \u00b7 lossless compression (native encoder)",
                "AIFF \u00b7 uncompressed, broadcast/big-endian",
                "OGG \u00b7 Opus, lossy but tiny and patent-free"};
        Dialogs.choose(act, "Recording format", labels,
                Math.max(0, Formats.CONTAINERS.indexOf(App.get().prefs().container())), idx -> {
                    App.get().prefs().setContainer(Formats.CONTAINERS.get(idx));
                    afterConfigChange();
                    if ("ogg".equals(App.get().prefs().container())) {
                        toast("Opus runs at 48 kHz stereo; WAV/FLAC/AIFF keep the native rate.");
                    }
                });
    }

    private void afterConfigChange() {
        if (act.engine().isCapturing()) {
            act.engine().stopCapture(true);
            act.engine().startMonitor();
        }
        rebuildConfig();
        act.refreshHeader();
        updateDiskLine();
    }

    // -------------------------------------------------------------- transport
    private void onRecord() {
        AudioEngine engine = act.engine();
        if (!act.hasAudioPermission()) {
            toast("Microphone permission is required");
            act.requestPermissions(new String[]{
                    android.Manifest.permission.RECORD_AUDIO}, 4711);
            return;
        }
        switch (engine.state()) {
            case RECORDING:
                engine.pauseRecording();
                updateTransport();
                break;
            case PAUSED:
                engine.resumeRecording();
                updateTransport();
                break;
            default: {
                Recorder.Config cfg = buildConfig();
                if (cfg == null) return;
                boolean ok = engine.startRecording(cfg);
                if (ok) {
                    updateTransport();
                    act.startRecordingService();
                    toast("Recording \u2192 " + cfg.file.getName());
                }
            }
        }
    }

    private Recorder.Config buildConfig() {
        File dir = App.get().prefs().recordDir();
        if (dir == null) dir = App.defaultRecordDir(act);
        if (!dir.exists() && !dir.mkdirs()) {
            toast("Cannot write to " + dir.getAbsolutePath());
            return null;
        }
        if (!dir.canWrite()) {
            toast("Recording folder is not writable");
            return null;
        }

        long sessionId = currentSessionId();
        Session session = act.store().session(sessionId);
        int take = act.store().nextTakeNo(sessionId);
        String base = (session == null ? "Take" : session.name) + " " + take;
        String stem = Ids.safeName(base, "Take") + " " + Fmt.fileStamp(System.currentTimeMillis());
        String container = App.get().prefs().container();

        Recorder.Config cfg = new Recorder.Config();
        cfg.title = base;
        cfg.file = Ids.uniqueFile(dir, stem, container);
        cfg.sampleRate = App.get().prefs().sampleRate();
        cfg.channels = App.get().prefs().channels();
        cfg.bitDepth = App.get().prefs().bitDepth();
        cfg.container = container;
        cfg.gainDb = App.get().prefs().gainDb();
        cfg.dither = App.get().prefs().dither();
        cfg.sessionId = sessionId;
        AudioDevice in = act.engine().input();
        cfg.deviceName = in == null ? "unknown" : (in.vendor + " " + in.name).trim();
        cfg.channelMap = buildChannelMap(cfg.channels);
        return cfg;
    }

    private String buildChannelMap(int channels) {
        switch (channels) {
            case 1: return "1 = Mono input";
            case 2: return "1 = L, 2 = R";
            case 4: return "1-2 = pair A, 3-4 = pair B";
            case 6: return "1-6 = 5.1 discrete";
            case 8: return "1-8 = discrete (ADAT 1-8)";
            default: return channels + " discrete inputs";
        }
    }

    private long currentSessionId() {
        long id = App.get().prefs().lastSessionId();
        if (id > 0 && act.store().session(id) != null) return id;
        List<Session> all = act.store().sessions(null);
        if (!all.isEmpty()) {
            App.get().prefs().setLastSessionId(all.get(0).id);
            return all.get(0).id;
        }
        Session s = new Session();
        s.name = "Default session";
        s.createdAt = System.currentTimeMillis();
        long newId = act.store().insert(s);
        App.get().prefs().setLastSessionId(newId);
        return newId;
    }

    private void togglePause() {
        AudioEngine engine = act.engine();
        if (engine.state() == AudioEngine.State.RECORDING) engine.pauseRecording();
        else if (engine.state() == AudioEngine.State.PAUSED) engine.resumeRecording();
        updateTransport();
    }

    private void onStop() {
        AudioEngine engine = act.engine();
        if (engine.recorder() != null) {
            Recorder.Result r = engine.stopRecording();
            saveTake(r);
            act.stopRecordingService();
        } else if (engine.isCapturing()) {
            engine.stopCapture(false);
            toast("Capture stopped");
        }
        updateTransport();
    }

    /** persist the finished take as a Track in the session */
    private void saveTake(Recorder.Result r) {
        if (r == null || !r.wroteAnything) {
            toast("Nothing was recorded");
            return;
        }
        FormatProbe.Info info = r.file != null ? FormatProbe.probe(r.file) : new FormatProbe.Info();
        Track t = new Track();
        t.title = r.file == null ? "Take" : stripExt(r.file.getName());
        t.filePath = r.file == null ? "" : r.file.getAbsolutePath();
        t.container = r.container;
        t.sampleRate = r.sampleRate;
        t.bitDepth = r.bitDepth;
        t.channels = r.channels;
        t.durationMs = info.durationMs > 0 ? info.durationMs : r.durationMs;
        t.sizeBytes = r.bytes;
        t.peakDb = r.peakDb;
        t.rmsDb = r.rmsDb;
        long sessionId = App.get().prefs().lastSessionId();
        t.sessionId = sessionId > 0 ? sessionId : currentSessionId();
        t.takeNo = act.store().nextTakeNo(t.sessionId);
        AudioDevice in = act.engine().input();
        t.deviceName = in == null ? "" : (in.vendor + " " + in.name).trim();
        t.channelMap = buildChannelMap(r.channels);
        t.notes = r.encoderStats;
        act.store().insert(t);
        showTakeActions(t, r);
    }

    private void showTakeActions(Track t, Recorder.Result r) {
        mTakeActions.removeAllViews();
        LinearLayout box = Ui.column(act);
        box.setBackgroundResource(R.drawable.bg_card);
        box.setPadding(Ui.dp(act, 12), Ui.dp(act, 12), Ui.dp(act, 12), Ui.dp(act, 12));
        TextView saved = Ui.body(act, "Saved: " + t.title);
        saved.setTextColor(th.ok);
        Ui.addWide(box, saved);
        box.addView(Ui.caption(act, r.summary() + "   peak " + Fmt.db(r.peakDb)
                + "   rms " + Fmt.db(r.rmsDb)));
        if (r.xruns > 0 || r.droppedBlocks > 0) {
            TextView warn = Ui.caption(act, "Buffer warnings: " + r.xruns + " xruns, "
                    + r.droppedBlocks + " dropped blocks - try a larger buffer.");
            warn.setTextColor(th.warn);
            Ui.addWide(box, warn);
        }
        box.addView(Ui.spacer(act, 8));
        LinearLayout actions = Ui.row(act);
        actions.addView(Ui.button(act, "\u25b6 Play", R.style.Btn_Small,
                        v -> act.engine().play(new File(t.filePath), t.title)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(Ui.spacer(act, 6));
        actions.addView(Ui.button(act, "Rename", R.style.Btn_Small,
                        v -> Dialogs.renameTrack(act, act.store(), t, () -> refresh())),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(Ui.spacer(act, 6));
        actions.addView(Ui.button(act, "Save", R.style.Btn_Small,
                        v -> Dialogs.saveToDownloads(act, new File(t.filePath),
                                Formats.mimeFor(t.container))),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(Ui.spacer(act, 6));
        actions.addView(Ui.button(act, "Share", R.style.Btn_Small,
                        v -> Dialogs.shareFile(act, new File(t.filePath), Formats.mimeFor(t.container))),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Ui.addWide(box, actions);
        mTakeActions.addView(box);
        mTakeActions.setVisibility(View.VISIBLE);
    }

    private static String stripExt(String n) {
        int d = n.lastIndexOf('.');
        return d > 0 ? n.substring(0, d) : n;
    }

    private void onMonitor() {
        AudioEngine engine = act.engine();
        boolean on = !App.get().prefs().monitor();
        App.get().prefs().setMonitor(on);
        if (engine.isCapturing()) {
            engine.setMonitoring(on);
        } else if (on) {
            if (!act.hasAudioPermission()) {
                toast("Microphone permission is required");
                return;
            }
            engine.startMonitor();
        }
        updateTransport();
        if (on) toast("Monitoring on - use headphones, the phone mic will feed back");
    }

    private void showPresetPicker() {
        List<Preset> presets = act.store().presetsFor(
                act.engine().input() == null ? "" : act.engine().input().name,
                act.engine().input() == null ? 0 : act.engine().input().vendorId,
                act.engine().input() == null ? 0 : act.engine().input().productId);
        if (presets.isEmpty()) {
            toast("No presets match this interface yet - create one in Device Presets");
            return;
        }
        String[] labels = new String[presets.size()];
        for (int i = 0; i < presets.size(); i++) {
            Preset p = presets.get(i);
            labels[i] = p.name + "\n" + p.summary();
        }
        Dialogs.choose(act, "Apply preset", labels, 0, idx -> {
            Preset p = presets.get(idx);
            App.get().prefs().setSampleRate(p.sampleRate);
            App.get().prefs().setBitDepth(p.bitDepth);
            App.get().prefs().setChannels(p.channels);
            App.get().prefs().setBufferFrames(p.bufferFrames);
            App.get().prefs().setContainer(p.container);
            App.get().prefs().setGainDb(p.gainDb);
            App.get().prefs().setMonitorGainDb(p.monitorGainDb);
            App.get().prefs().setMonitor(p.monitor);
            act.store().bumpUse(p.id);
            afterConfigChange();
            mGain.setValue(p.gainDb);
            mMonitorGain.setValue(p.monitorGainDb);
            toast("Applied " + p.name);
        });
    }

    // ---------------------------------------------------------------- updates
    private void updateTransport() {
        if (mRecordBtn == null) return;
        AudioEngine.State s = act.engine().state();
        boolean on = App.get().prefs().monitor();
        mMonitorBtn.setText(on ? "Monitor: ON" : "Monitor: off");
        mMonitorBtn.setTextColor(on ? th.ok : th.textPrimary);
        switch (s) {
            case RECORDING:
                mRecordBtn.setText("II  Pause");
                mStatusLine.setText("RECORDING");
                mStatusLine.setTextColor(th.rec);
                break;
            case PAUSED:
                mRecordBtn.setText("\u25b6  Resume");
                mStatusLine.setText("PAUSED");
                mStatusLine.setTextColor(th.warn);
                break;
            case MONITORING:
                mRecordBtn.setText("\u25cf  Record take");
                mStatusLine.setText(on ? "MONITORING" : "ARMED");
                mStatusLine.setTextColor(on ? th.ok : th.accent);
                break;
            default:
                mRecordBtn.setText("\u25cf  Arm & record");
                mStatusLine.setText("IDLE");
                mStatusLine.setTextColor(th.textTertiary);
                break;
        }
        Recorder rec = act.engine().recorder();
        if (rec != null && rec.file() != null) {
            mFileName.setText(rec.file().getName());
        } else {
            mFileName.setText("Next take: " + nextFileNameGuess());
        }
        mCaptureMeters.setChannelCount(App.get().prefs().channels());
        updateDiskLine();
        updateSessionLine();
    }

    private String nextFileNameGuess() {
        Session session = act.store().session(App.get().prefs().lastSessionId());
        String base = (session == null ? "Take" : session.name) + " "
                + act.store().nextTakeNo(session == null ? -1 : session.id);
        return Ids.safeName(base, "Take") + " \u2026." + App.get().prefs().container();
    }

    private void updateDiskLine() {
        if (mDiskLine == null) return;
        File dir = App.get().prefs().recordDir();
        long free = dir == null ? 0 : dir.getFreeSpace();
        long seconds = Fmt.recordableSeconds(free, App.get().prefs().channels(),
                App.get().prefs().bitDepth(), App.get().prefs().sampleRate(),
                App.get().prefs().container());
        mDiskLine.setText(Fmt.size(free) + " free \u00b7 " + Fmt.clock(seconds * 1000)
                + " recordable \u00b7 " + (dir == null ? "" : dir.getName()));
        mDiskLine.setTextColor(free < 500L * 1024 * 1024 ? th.rec : th.textTertiary);
    }

    private void updateSessionLine() {
        if (mSessionLine == null) return;
        long id = App.get().prefs().lastSessionId();
        Session s = act.store().session(id);
        mSessionLine.setText(s == null ? "No session selected"
                : "Session: " + s.name + "  \u00b7  take #" + act.store().nextTakeNo(s.id));
        mSessionLine.setTextColor(s == null ? th.warn : th.textPrimary);
    }

    private void refreshPeakWarn() {
        updatePeakReadout();
    }

    private void updatePeakReadout() {
        if (mPeakReadout == null) return;
        float max = -120f;
        for (int c = 0; c < mCaptureMeters.getChannelCount(); c++) {
            max = Math.max(max, mCaptureMeters.holdOf(c));
        }
        String text = "Peak hold " + Fmt.db(max);
        if (max >= App.get().prefs().peakWarnDb()) {
            text += "  \u00b7  over " + Fmt.dbShort(App.get().prefs().peakWarnDb()) + " dB warning";
            mPeakReadout.setTextColor(th.rec);
        } else if (max > -60f) {
            mPeakReadout.setTextColor(th.textSecondary);
        } else {
            mPeakReadout.setTextColor(th.textTertiary);
        }
        mPeakReadout.setText(text);
    }

    // -------------------------------------------------------------- listeners
    @Override
    public void onResume() {
        act.engine().addListener(this);
        updateTransport();
        rebuildConfig();
        act.refreshHeader();
    }

    @Override
    public void onPause() {
        act.engine().removeListener(this);
    }

    @Override
    public void onLevels(float[] rmsDb, float[] peakDb, int channels) {
        act.runOnUiThread(() -> {
            if (mCaptureMeters == null) return;
            if (mCaptureMeters.getChannelCount() != channels) mCaptureMeters.setChannelCount(channels);
            mCaptureMeters.setLevels(rmsDb, peakDb);
            updatePeakReadout();
        });
    }

    @Override
    public void onPlaybackLevels(float[] rmsDb, float[] peakDb, int channels) {
        act.runOnUiThread(() -> {
            if (mPlaybackMeters != null) mPlaybackMeters.setLevels(rmsDb, peakDb);
        });
    }

    @Override
    public void onScope(float[] interleaved, int frames, int channels) {
        act.runOnUiThread(() -> {
            if (mScope != null) mScope.push(interleaved, frames, channels);
            if (mSpectrum != null) mSpectrum.push(interleaved, frames, channels);
        });
    }

    /** what is playing back, so the analyser is live during audition too */
    @Override
    public void onPlaybackScope(float[] monoWindow, int frames) {
        act.runOnUiThread(() -> {
            if (mSpectrum != null) mSpectrum.pushWindow(monoWindow, frames);
        });
    }

    @Override
    public void onRecordingTick(long frames, long bytes, long elapsedMs) {
        act.runOnUiThread(() -> {
            if (mTimer != null) mTimer.setText(Fmt.timecode(elapsedMs));
            if (mFileName != null && act.engine().recorder() != null
                    && act.engine().recorder().file() != null) {
                mFileName.setText(act.engine().recorder().file().getName()
                        + "  \u00b7  " + Fmt.size(bytes));
            }
        });
    }

    @Override
    public void onEngineState(AudioEngine.State s) {
        act.runOnUiThread(() -> {
            updateTransport();
            if (s == AudioEngine.State.IDLE) mTimer.setText("00:00.0");
        });
    }

    @Override
    public void onRecordingFinished(Recorder.Result result) {
        act.runOnUiThread(() -> {
            if (result != null && result.wroteAnything) saveTake(result);
            updateTransport();
        });
    }

    @Override
    public void onDevicesChanged() {
        act.runOnUiThread(() -> {
            rebuildConfig();
            updateTransport();
        });
    }
}
