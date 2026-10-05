package com.mostakim.audiorec.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mostakim.audiorec.App;
import com.mostakim.audiorec.R;
import com.mostakim.audiorec.audio.AudioDevice;
import com.mostakim.audiorec.audio.AudioEngine;
import com.mostakim.audiorec.audio.PlaybackEngine;
import com.mostakim.audiorec.audio.Recorder;
import com.mostakim.audiorec.db.Store;
import com.mostakim.audiorec.ui.kit.Theme;
import com.mostakim.audiorec.ui.kit.Ui;
import com.mostakim.audiorec.ui.screens.AboutScreen;
import com.mostakim.audiorec.ui.screens.DevicesScreen;
import com.mostakim.audiorec.ui.screens.ExportsScreen;
import com.mostakim.audiorec.ui.screens.LibraryScreen;
import com.mostakim.audiorec.ui.screens.MixerScreen;
import com.mostakim.audiorec.ui.screens.PlaylistScreen;
import com.mostakim.audiorec.ui.screens.PresetsScreen;
import com.mostakim.audiorec.ui.screens.RecorderScreen;
import com.mostakim.audiorec.ui.screens.Screen;
import com.mostakim.audiorec.ui.screens.SessionsScreen;
import com.mostakim.audiorec.ui.screens.SettingsScreen;
import com.mostakim.audiorec.ui.screens.StorageScreen;
import com.mostakim.audiorec.util.Fmt;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * The recorder window.
 *
 * This is a recording app, so it opens on the recorder: one status header across
 * the top, the current page filling everything between, and a five-tab bar along
 * the bottom.  There is no drawer to open, no dashboard to land on first and no
 * side rail that eats the width a phone does not have - the page under the finger
 * is always the whole window, in portrait and in landscape, on any aspect ratio.
 *
 * Everything is laid out with weights and full-width blocks rather than fixed
 * pixel widths, which is what keeps the alignment honest on a 20:9 phone, a
 * 16:9 tablet and everything between.  No sign-in, no accounts, all on-device.
 */
public class MainActivity extends Activity implements AudioEngine.Listener {

    public static final int PAGE_RECORDER = 0;
    public static final int PAGE_MIXER = 1;
    public static final int PAGE_LIBRARY = 2;
    public static final int PAGE_PLAYLIST = 3;
    public static final int PAGE_DEVICES = 4;
    public static final int PAGE_SESSIONS = 5;
    public static final int PAGE_EXPORTS = 6;
    public static final int PAGE_PRESETS = 7;
    public static final int PAGE_STORAGE = 8;
    public static final int PAGE_SETTINGS = 9;
    public static final int PAGE_ABOUT = 10;

    private static final int REQ_PERMISSIONS = 4711;

    /** the tabs along the bottom: what an operator reaches in one tap */
    private static final int[] TABS = {
            PAGE_RECORDER, PAGE_MIXER, PAGE_LIBRARY, PAGE_PLAYLIST,
    };
    private static final String[] TAB_LABELS = {"Record", "Mixer", "Library", "Playlist"};
    private static final int[] TAB_ICONS = {
            R.drawable.ic_rec, R.drawable.ic_mixer, R.drawable.ic_library, R.drawable.ic_playlist,
    };

    /** what the fifth tab opens */
    private static final int[] MORE_PAGES = {
            PAGE_DEVICES, PAGE_SESSIONS, PAGE_EXPORTS, PAGE_PRESETS,
            PAGE_STORAGE, PAGE_SETTINGS, PAGE_ABOUT,
    };
    private static final String[] MORE_LABELS = {
            "Devices and interfaces", "Sessions", "Export Files", "Device Presets",
            "Storage", "Settings", "About",
    };

    private Theme mTheme;
    private Store mStore;
    private AudioEngine mEngine;

    private LinearLayout mRoot, mHeader, mStatusRow;
    private TextView mTitle, mSubtitle, mRecDot, mInputChip, mFormatChip, mSpaceChip;
    private FrameLayout mHost;
    private LinearLayout mTabs;
    private final Map<Integer, TextView> mMoreItems = new HashMap<>();

    private final Map<Integer, Screen> mScreens = new HashMap<>();
    private final Map<Integer, View> mTabViews = new HashMap<>();
    private int mPage = PAGE_RECORDER;
    private boolean mUpdatingTabs = false;

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onDevicesChanged();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTheme = new Theme(this);
        mStore = new Store(this, App.get().db());
        mEngine = App.get().audio();

        getWindow().setStatusBarColor(mTheme.bgRoot);
        getWindow().setNavigationBarColor(mTheme.bgRoot);
        getWindow().setBackgroundDrawableResource(R.color.bg_root);
        // stay inside the display cutout in landscape instead of hiding the status
        // header behind a notch or a camera hole
        getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;

        buildShell();
        mEngine.addListener(this);
        mEngine.refreshDevices();
        requestNeededPermissions();

        handleIntent(getIntent());
        navigate(PAGE_RECORDER);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        if (intent.getAction() != null
                && intent.getAction().startsWith("android.hardware.usb.action")) {
            mEngine.refreshDevices();
            navigate(PAGE_RECORDER);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter usbState = new IntentFilter("android.hardware.usb.action.USB_STATE");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(mUsbReceiver, usbState, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mUsbReceiver, usbState);
        }
        mEngine.addListener(this);
        mEngine.refreshDevices();
        Screen s = mScreens.get(mPage);
        if (s != null) s.onResume();
        refreshHeader();
        updateKeepScreen();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(mUsbReceiver);
        } catch (Exception ignored) {
        }
        Screen s = mScreens.get(mPage);
        if (s != null) s.onPause();
    }

    @Override
    protected void onDestroy() {
        mEngine.removeListener(this);
        super.onDestroy();
    }

    private void updateKeepScreen() {
        boolean keep = App.get().prefs().keepScreenOn()
                && (mEngine.state() == AudioEngine.State.RECORDING
                || mEngine.state() == AudioEngine.State.MONITORING
                || mEngine.state() == AudioEngine.State.PAUSED);
        if (keep) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // ------------------------------------------------------------------ shell
    private void buildShell() {
        mRoot = Ui.column(this);
        mRoot.setBackgroundColor(mTheme.bgRoot);

        mRoot.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mHost = new FrameLayout(this);
        mHost.setBackgroundColor(mTheme.bgRoot);
        mRoot.addView(mHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        mRoot.addView(buildTabs(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(mRoot);
    }

    /** one status header: who we are, where we are, and what the interface is doing */
    private LinearLayout buildHeader() {
        mHeader = Ui.column(this);
        mHeader.setBackgroundColor(mTheme.bgPanel);
        mHeader.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 6));

        LinearLayout top = Ui.row(this);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo);
        logo.setColorFilter(mTheme.accent);
        int ls = Ui.dp(this, 24);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ls, ls);
        llp.rightMargin = Ui.dp(this, 8);
        logo.setLayoutParams(llp);
        top.addView(logo);

        TextView brand = Ui.text(this, "AUDIO-rec", R.style.T_Head);
        brand.setTextColor(mTheme.accent);
        brand.setSingleLine(true);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.rightMargin = Ui.dp(this, 10);
        brand.setLayoutParams(blp);
        top.addView(brand);

        mTitle = Ui.text(this, "Recorder", R.style.T_Head);
        mTitle.setSingleLine(true);
        top.addView(mTitle);

        mSubtitle = Ui.caption(this, "");
        mSubtitle.setSingleLine(true);
        mSubtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        slp.leftMargin = Ui.dp(this, 8);
        mSubtitle.setLayoutParams(slp);
        top.addView(mSubtitle);

        mRecDot = Ui.pill(this, "\u25cf REC", R.drawable.bg_pill_rec, mTheme.rec);
        mRecDot.setVisibility(View.GONE);
        top.addView(mRecDot);
        Ui.addWide(mHeader, top);

        // the status strip: input, format, free space, output.  It scrolls sideways
        // rather than wrapping, so a narrow screen never clips it and a wide one
        // shows everything at once.
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(true);
        mStatusRow = Ui.row(this);
        int vpad = Ui.dp(this, 2);
        mStatusRow.setPadding(0, vpad, 0, vpad);
        scroller.addView(mStatusRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mInputChip = chip("IN", () -> navigate(PAGE_DEVICES));
        mFormatChip = chip("format", () -> navigate(PAGE_RECORDER));
        mSpaceChip = chip("space", () -> navigate(PAGE_STORAGE));
        mHeader.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return mHeader;
    }

    /** one tappable status chip */
    private TextView chip(String label, final Runnable tap) {
        TextView t = Ui.badge(this, label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 6);
        t.setLayoutParams(lp);
        t.setClickable(true);
        t.setFocusable(true);
        t.setOnClickListener(v -> tap.run());
        mStatusRow.addView(t);
        return t;
    }

    /** the bottom bar: four pages and a sheet with the rest */
    private LinearLayout buildTabs() {
        mTabs = Ui.row(this);
        mTabs.setBackgroundColor(mTheme.bgPanel);
        mTabs.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 8));

        for (int i = 0; i < TABS.length; i++) {
            mTabs.addView(tab(TABS[i], TAB_LABELS[i], TAB_ICONS[i]),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        mTabs.addView(tab(-1, "More", R.drawable.ic_dash),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return mTabs;
    }

    private LinearLayout tab(final int page, String label, int iconRes) {
        LinearLayout item = Ui.column(this);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setClickable(true);
        item.setFocusable(true);
        item.setMinimumHeight(Ui.dp(this, 48));
        item.setPadding(Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        int s = Ui.dp(this, 22);
        icon.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        icon.setColorFilter(mTheme.textSecondary);
        Ui.addWide(item, icon);

        TextView tv = Ui.text(this, label, R.style.T_Caption);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 2);
        tv.setLayoutParams(lp);
        Ui.addWide(item, tv);

        final View iconView = icon;
        item.setOnClickListener(v -> {
            if (page < 0) showMoreSheet();
            else navigate(page);
        });
        mTabViews.put(page, item);
        // the label sits inside the item; keep both for the highlight pass
        item.setTag(tv);
        return item;
    }

    /** the fifth tab: everything that is not a daily control */
    private void showMoreSheet() {
        String[] labels = new String[MORE_LABELS.length + 1];
        System.arraycopy(MORE_LABELS, 0, labels, 0, MORE_LABELS.length);
        labels[MORE_LABELS.length] = mPage == PAGE_RECORDER ? "Recorder (already open)" : "Recorder";
        new AlertDialog.Builder(this)
                .setTitle("More")
                .setItems(labels, (d, which) -> {
                    if (which < MORE_PAGES.length) navigate(MORE_PAGES[which]);
                    else navigate(PAGE_RECORDER);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    // -------------------------------------------------------------- navigate
    public void navigate(int page) {
        if (mPage != page) {
            Screen old = mScreens.get(mPage);
            if (old != null) old.onPause();
        }
        mPage = page;
        Screen screen = mScreens.get(page);
        if (screen == null) {
            screen = createScreen(page);
            mScreens.put(page, screen);
        }
        mHost.removeAllViews();
        View v = screen.view();
        if (v.getParent() instanceof ViewGroup) {
            ((ViewGroup) v.getParent()).removeView(v);
        }
        mHost.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mTitle.setText(screen.title());
        mSubtitle.setText(screen.subtitle());
        screen.onResume();
        highlightTabs();
        refreshHeader();
    }

    private Screen createScreen(int page) {
        switch (page) {
            case PAGE_MIXER: return new MixerScreen(this);
            case PAGE_DEVICES: return new DevicesScreen(this);
            case PAGE_SESSIONS: return new SessionsScreen(this);
            case PAGE_LIBRARY: return new LibraryScreen(this);
            case PAGE_PLAYLIST: return new PlaylistScreen(this);
            case PAGE_EXPORTS: return new ExportsScreen(this);
            case PAGE_PRESETS: return new PresetsScreen(this);
            case PAGE_STORAGE: return new StorageScreen(this);
            case PAGE_SETTINGS: return new SettingsScreen(this);
            case PAGE_ABOUT: return new AboutScreen(this);
            default: return new RecorderScreen(this);
        }
    }

    private void highlightTabs() {
        if (mTabViews.isEmpty()) return;
        for (Map.Entry<Integer, View> e : mTabViews.entrySet()) {
            int page = e.getKey();
            View item = e.getValue();
            boolean selected = page == mPage;
            // "More" is lit when the page on screen lives inside it
            if (page < 0) selected = isMorePage(mPage);
            for (View child : children(item)) {
                boolean on = child == item.getTag();
                if (child instanceof ImageView) {
                    ((ImageView) child).setColorFilter(selected ? mTheme.accent : mTheme.textSecondary);
                    child.setAlpha(selected ? 1f : 0.75f);
                } else if (child instanceof TextView) {
                    ((TextView) child).setTextColor(selected ? mTheme.accent
                            : mTheme.textSecondary);
                }
                if (selected) item.setBackgroundColor(mTheme.bgCard);
                else item.setBackgroundColor(0);
            }
        }
    }

    private static java.util.List<View> children(View v) {
        java.util.List<View> out = new java.util.ArrayList<>();
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) out.add(g.getChildAt(i));
        }
        return out;
    }

    public static boolean isMorePage(int page) {
        for (int p : MORE_PAGES) {
            if (p == page) return true;
        }
        return false;
    }

    /** invalidate every page that shows shared data */
    public void refreshAll() {
        for (Screen s : mScreens.values()) {
            if (s != null) s.refresh();
        }
        refreshHeader();
    }

    public void refreshPage(int page) {
        Screen s = mScreens.get(page);
        if (s != null) s.refresh();
    }

    // --------------------------------------------------------------- header
    public void refreshHeader() {
        AudioDevice in = mEngine.input();
        AudioDevice out = mEngine.output();

        mInputChip.setText(in == null ? "no interface"
                : (in.isUsb ? "USB  " : "IN  ") + in.shortSpec());
        mInputChip.setBackgroundResource(in != null && in.isUsb
                ? R.drawable.bg_pill : R.drawable.bg_badge);
        mInputChip.setTextColor(in != null && in.isUsb ? mTheme.accent : mTheme.textSecondary);

        mFormatChip.setText(App.get().prefs().bitDepth() + "-bit  \u00b7  "
                + Fmt.khz(App.get().prefs().sampleRate()) + "  \u00b7  "
                + App.get().prefs().channels() + " ch  \u00b7  "
                + App.get().prefs().container().toUpperCase());

        File dir = App.get().prefs().recordDir();
        long free = dir == null ? 0L : dir.getFreeSpace();
        mSpaceChip.setText(free < 500L * 1024 * 1024
                ? "low space: " + Fmt.size(free) : Fmt.size(free) + " free");
        mSpaceChip.setTextColor(free < 500L * 1024 * 1024 ? mTheme.rec : mTheme.textTertiary);

        if (out != null && out.isUsb) {
            mSpaceChip.setText(mSpaceChip.getText() + "  \u00b7  out " + out.name);
        }
        updateRecIndicator();
    }

    private void updateRecIndicator() {
        AudioEngine.State s = mEngine.state();
        boolean rec = s == AudioEngine.State.RECORDING;
        mRecDot.setVisibility(rec || s == AudioEngine.State.PAUSED ? View.VISIBLE : View.GONE);
        if (rec) {
            mRecDot.setText("\u25cf REC " + Fmt.timecode(mEngine.recordingElapsedMs()));
            mRecDot.postDelayed(this::updateRecIndicator, 500);
        } else if (s == AudioEngine.State.PAUSED) {
            mRecDot.setText("II PAUSED");
        }
        updateKeepScreen();
    }

    // ------------------------------------------------------------- listeners
    @Override
    public void onDevicesChanged() {
        runOnUiThread(() -> {
            refreshHeader();
            Screen s = mScreens.get(PAGE_DEVICES);
            if (s != null && mPage == PAGE_DEVICES) s.refresh();
        });
    }

    @Override
    public void onEngineState(AudioEngine.State s) {
        runOnUiThread(this::updateRecIndicator);
    }

    @Override
    public void onRecordingTick(long frames, long bytes, long elapsedMs) {
        runOnUiThread(() -> {
            if (mEngine.state() == AudioEngine.State.RECORDING) {
                mRecDot.setText("\u25cf REC " + Fmt.timecode(elapsedMs));
            }
        });
    }

    @Override
    public void onRecordingFinished(Recorder.Result result) {
        runOnUiThread(() -> {
            updateRecIndicator();
            if (result != null && result.wroteAnything) {
                toast("Take saved: " + (result.file == null ? "" : result.file.getName())
                        + "  \u00b7  " + result.summary());
                if (result.fellBackToWav) {
                    Ui.longToast(this, "Opus encoder unavailable ("
                            + result.fallbackReason + ") - saved as WAV instead.");
                }
            }
        });
    }

    @Override
    public void onPlaybackState(PlaybackEngine.State s, String title) {
        runOnUiThread(() -> {
            Screen lib = mScreens.get(PAGE_LIBRARY);
            if (lib instanceof LibraryScreen) ((LibraryScreen) lib).onPlaybackChanged();
        });
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> Ui.longToast(this, message));
    }

    // ----------------------------------------------------------- permissions
    private void requestNeededPermissions() {
        java.util.List<String> want = new java.util.ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            want.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            want.add("android.permission.POST_NOTIFICATIONS");
        }
        if (!want.isEmpty()) {
            requestPermissions(want.toArray(new String[0]), REQ_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_PERMISSIONS) {
            boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
            if (!mic) {
                new AlertDialog.Builder(this)
                        .setTitle("Microphone access needed")
                        .setMessage("AUDIO-rec records from the USB interface through the "
                                + "system audio input. Grant microphone access to arm the recorder.")
                        .setPositiveButton("Grant", (d, w) ->
                                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                                        REQ_PERMISSIONS))
                        .setNegativeButton("Later", null)
                        .show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        android.net.Uri uri = data.getData();
        if (uri == null) return;
        if (requestCode == LibraryScreen.REQ_PICK) {
            Screen s = mScreens.get(PAGE_LIBRARY);
            if (s instanceof LibraryScreen) ((LibraryScreen) s).handlePicked(uri);
        }
    }

    public boolean hasAudioPermission() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ------------------------------------------------------------- accessors
    @Override
    public void onBackPressed() {
        if (mPage != PAGE_RECORDER) {
            navigate(PAGE_RECORDER);
            return;
        }
        if (mEngine.isCapturing()) {
            Ui.confirm(this, "Capture is running",
                    "Stop monitoring and recording before leaving?",
                    "Stop and exit", () -> {
                        mEngine.stopCapture(true);
                        finish();
                    });
            return;
        }
        super.onBackPressed();
    }

    public Theme theme() {
        return mTheme;
    }

    public Store store() {
        return mStore;
    }

    public AudioEngine engine() {
        return mEngine;
    }

    public void toast(String msg) {
        Ui.toast(this, msg);
    }

    public void openRecorder() {
        navigate(PAGE_RECORDER);
    }

    /** the pages the bottom bar reaches directly, for tests and for the sheet */
    public static int[] tabPages() {
        return TABS.clone();
    }

    public static int[] morePages() {
        return MORE_PAGES.clone();
    }

    /** run the take under a microphone foreground service so it survives backgrounding */
    public void startRecordingService() {
        Intent i = new Intent(this, com.mostakim.audiorec.service.RecordingService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
            else startService(i);
        } catch (Exception e) {
            toast("Could not start the capture service: " + e.getMessage());
        }
    }

    public void stopRecordingService() {
        try {
            stopService(new Intent(this, com.mostakim.audiorec.service.RecordingService.class));
        } catch (Exception ignored) {
        }
    }
}
