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
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
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
import com.mostakim.audiorec.ui.screens.DashboardScreen;
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
 * The workstation window: navigation rail on the left, contextual header on top,
 * the current page underneath.  No sign-in, no accounts - the app opens straight
 * onto the dashboard and everything it does stays on the device.
 */
public class MainActivity extends Activity implements AudioEngine.Listener {

    public static final int PAGE_DASHBOARD = 0;
    public static final int PAGE_RECORDER = 1;
    public static final int PAGE_MIXER = 2;
    public static final int PAGE_DEVICES = 3;
    public static final int PAGE_SESSIONS = 4;
    public static final int PAGE_LIBRARY = 5;
    public static final int PAGE_PLAYLIST = 6;
    public static final int PAGE_EXPORTS = 7;
    public static final int PAGE_PRESETS = 8;
    public static final int PAGE_STORAGE = 9;
    public static final int PAGE_SETTINGS = 10;
    public static final int PAGE_ABOUT = 11;

    private static final int REQ_PERMISSIONS = 4711;

    private Theme mTheme;
    private Store mStore;
    private AudioEngine mEngine;

    private SidebarLayout mShell;
    private LinearLayout mSidebar;
    private LinearLayout mTopbar;
    private TextView mTitle, mSubtitle;
    private LinearLayout mPills;
    private TextView mRecDot;
    private FrameLayout mHost;
    private ImageView mMenuIcon;

    private final Map<Integer, Screen> mScreens = new HashMap<>();
    private final Map<Integer, TextView> mNavItems = new HashMap<>();
    private int mPage = PAGE_DASHBOARD;
    private int mLastSidebarPage = -1;

    private boolean mWasDrawerMode;

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        buildShell();
        buildSidebar();
        mEngine.addListener(this);
        mEngine.refreshDevices();
        requestNeededPermissions();

        // deep links from the USB attach intent
        handleIntent(getIntent());
        navigate(PAGE_DASHBOARD);
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
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter usbState = new IntentFilter("android.hardware.usb.action.USB_STATE");
        if (Build.VERSION.SDK_INT >= 33) {
            // system broadcast, but be explicit on Android 13+ instead of relying
            // on the "system broadcasts are exempt" carve-out
            registerReceiver(mUsbReceiver, usbState, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mUsbReceiver, usbState);
        }
        mEngine.addListener(this);
        mEngine.refreshDevices();
        Screen s = mScreens.get(mPage);
        if (s != null) s.onResume();
        refreshTopbar();
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
        mShell = new SidebarLayout(this);
        mSidebar = Ui.column(this);
        mSidebar.setBackgroundColor(mTheme.bgSidebar);
        mSidebar.setPadding(Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18));

        LinearLayout content = Ui.column(this);
        content.setBackgroundColor(mTheme.bgRoot);
        mTopbar = Ui.row(this);
        mTopbar.setBackgroundColor(mTheme.bgPanel);
        mTopbar.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));

        // hamburger (drawer mode only)
        LinearLayout menuBtn = Ui.row(this);
        menuBtn.setGravity(Gravity.CENTER);
        menuBtn.setBackgroundResource(R.drawable.bg_btn);
        int ms = Ui.dp(this, 40);
        menuBtn.setLayoutParams(new LinearLayout.LayoutParams(ms, ms));
        mMenuIcon = new ImageView(this);
        mMenuIcon.setImageResource(R.drawable.ic_menu);
        mMenuIcon.setColorFilter(mTheme.textPrimary);
        LinearLayout.LayoutParams mip = new LinearLayout.LayoutParams(
                Ui.dp(this, 20), Ui.dp(this, 20));
        mMenuIcon.setLayoutParams(mip);
        menuBtn.addView(mMenuIcon);
        menuBtn.setOnClickListener(v -> mShell.toggleDrawer());
        mTopbar.addView(menuBtn);

        LinearLayout titles = Ui.column(this);
        titles.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        mTitle = Ui.title(this, "");
        mSubtitle = Ui.caption(this, "");
        titles.addView(mTitle);
        titles.addView(mSubtitle);
        mTopbar.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        mPills = Ui.row(this);
        mPills.setGravity(Gravity.END);
        mRecDot = Ui.pill(this, "\u25cf REC", R.drawable.bg_pill_rec, mTheme.rec);
        mRecDot.setVisibility(View.GONE);
        mPills.addView(mRecDot);
        mTopbar.addView(mPills);

        content.addView(mTopbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View hairline = new View(this);
        hairline.setBackgroundColor(mTheme.strokeSoft);
        content.addView(hairline, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 1))));

        mHost = new FrameLayout(this);
        content.addView(mHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // content first, rail second: the rail is the topmost child, so it draws
        // over the drawer scrim and gets first refusal on touches
        mShell.setChildren(content, mSidebar);

        setContentView(mShell);

        mShell.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            boolean drawer = (r - l) < Ui.dp(this, 620);
            if (drawer != mWasDrawerMode) {
                mWasDrawerMode = drawer;
                mShell.setDrawerMode(drawer);
                updateMenuVisibility();
            }
        });
        mShell.post(() -> {
            boolean drawer = mShell.getWidth() < Ui.dp(this, 620);
            mWasDrawerMode = drawer;
            mShell.setDrawerMode(drawer);
            updateMenuVisibility();
        });
    }

    private void updateMenuVisibility() {
        if (mMenuIcon == null) return;
        mMenuIcon.setVisibility(mShell.isDrawerMode() ? View.VISIBLE : View.GONE);
    }

    private void buildSidebar() {
        mSidebar.removeAllViews();

        // brand lockup
        LinearLayout brand = Ui.row(this);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo);
        logo.setColorFilter(mTheme.accent);
        int s = Ui.dp(this, 34);
        logo.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        brand.addView(logo);
        LinearLayout brandText = Ui.column(this);
        brandText.setPadding(Ui.dp(this, 10), 0, 0, 0);
        TextView name = Ui.head(this, "AUDIO-rec");
        name.setTextColor(mTheme.textPrimary);
        TextView tag = Ui.text(this, "USB recording workstation", R.style.T_Caption);
        tag.setTextColor(mTheme.textTertiary);
        brandText.addView(name);
        brandText.addView(tag);
        brand.addView(brandText);
        mSidebar.addView(brand);

        addNavSection("CAPTURE");
        addNavItem(PAGE_DASHBOARD, "Dashboard", R.drawable.ic_dash);
        addNavItem(PAGE_RECORDER, "Recorder", R.drawable.ic_rec);
        addNavItem(PAGE_MIXER, "Mixer", R.drawable.ic_mixer);
        addNavItem(PAGE_DEVICES, "Devices", R.drawable.ic_usb);

        addNavSection("CONTENT");
        addNavItem(PAGE_SESSIONS, "Sessions", R.drawable.ic_sessions);
        addNavItem(PAGE_LIBRARY, "Library", R.drawable.ic_library);
        addNavItem(PAGE_PLAYLIST, "Playlist", R.drawable.ic_playlist);
        addNavItem(PAGE_EXPORTS, "Export Files", R.drawable.ic_export);
        addNavItem(PAGE_PRESETS, "Device Presets", R.drawable.ic_preset);

        addNavSection("SYSTEM");
        addNavItem(PAGE_STORAGE, "Storage", R.drawable.ic_storage);
        addNavItem(PAGE_SETTINGS, "Settings", R.drawable.ic_settings);
        addNavItem(PAGE_ABOUT, "About", R.drawable.ic_about);

        mSidebar.addView(Ui.spacer(this, 10));
        View flex = Ui.flex(this);
        flex.setLayoutParams(new LinearLayout.LayoutParams(1, 0, 1f));
        mSidebar.addView(flex);

        // live footer: the current interface, always visible
        LinearLayout footer = Ui.column(this);
        footer.setBackgroundResource(R.drawable.bg_card_flat);
        footer.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        TextView fh = Ui.text(this, "CURRENT INPUT", R.style.T_Section);
        footer.addView(fh);
        TextView dev = Ui.body(this, "\u2014");
        dev.setTag("footer_device");
        footer.addView(dev);
        TextView spec = Ui.caption(this, "");
        spec.setTag("footer_spec");
        footer.addView(spec);
        footer.setOnClickListener(v -> navigate(PAGE_DEVICES));
        mSidebar.addView(footer);
        mSidebar.addView(Ui.spacer(this, 8));
        TextView credit = Ui.text(this, "Mostakim Billah  \u00b7  v1.0.0", R.style.T_Caption);
        credit.setTextColor(mTheme.textTertiary);
        mSidebar.addView(credit);
    }

    private void addNavSection(String label) {
        TextView t = Ui.text(this, label, R.style.T_Section);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 18);
        lp.bottomMargin = Ui.dp(this, 6);
        lp.leftMargin = Ui.dp(this, 8);
        t.setLayoutParams(lp);
        mSidebar.addView(t);
    }

    private void addNavItem(final int page, String label, int iconRes) {
        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        row.setBackgroundResource(R.drawable.bg_sidebar_item);
        row.setClickable(true);
        row.setFocusable(true);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        int s = Ui.dp(this, 20);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(s, s);
        ip.rightMargin = Ui.dp(this, 14);
        icon.setLayoutParams(ip);
        row.addView(icon);

        TextView tv = Ui.body(this, label);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView count = Ui.badge(this, "");
        count.setVisibility(View.GONE);
        count.setTag("count");
        row.addView(count);

        row.setOnClickListener(v -> {
            navigate(page);
            if (mShell.isDrawerMode()) mShell.closeDrawer();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 2);
        row.setLayoutParams(lp);
        mSidebar.addView(row);
        mNavItems.put(page, tv);
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
        highlightNav();
        refreshTopbar();
    }

    private Screen createScreen(int page) {
        switch (page) {
            case PAGE_RECORDER: return new RecorderScreen(this);
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
            default: return new DashboardScreen(this);
        }
    }

    private void highlightNav() {
        for (Map.Entry<Integer, TextView> e : mNavItems.entrySet()) {
            boolean sel = e.getKey() == mPage;
            e.getValue().setTextColor(sel ? mTheme.accent : mTheme.textSecondary);
            ViewGroup row = (ViewGroup) e.getValue().getParent();
            row.setSelected(sel);
            View icon = row.getChildAt(0);
            if (icon instanceof ImageView) {
                ((ImageView) icon).setColorFilter(sel ? mTheme.accent : mTheme.textSecondary);
            }
        }
    }

    /** invalidate every page that shows shared data */
    public void refreshAll() {
        for (Screen s : mScreens.values()) {
            if (s != null) s.refresh();
        }
        refreshTopbar();
    }

    public void refreshPage(int page) {
        Screen s = mScreens.get(page);
        if (s != null) s.refresh();
    }

    // --------------------------------------------------------------- topbar
    public void refreshTopbar() {
        mPills.removeAllViews();
        AudioDevice in = mEngine.input();
        AudioDevice out = mEngine.output();
        if (in != null) {
            boolean usb = in.isUsb;
            TextView p = Ui.pill(this, (usb ? "USB  " : "IN  ") + in.shortSpec(),
                    usb ? R.drawable.bg_pill : R.drawable.bg_badge,
                    usb ? mTheme.accent : mTheme.textSecondary);
            mPills.addView(p);
        }
        int channels = App.get().prefs().channels();
        String container = App.get().prefs().container();
        TextView fmt = Ui.badge(this, App.get().prefs().bitDepth() + "-bit  \u00b7  "
                + Fmt.khz(App.get().prefs().sampleRate()) + "  \u00b7  " + container.toUpperCase());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(this, 6);
        fmt.setLayoutParams(lp);
        mPills.addView(fmt);

        File dir = App.get().prefs().recordDir();
        if (dir != null) {
            long free = dir.getFreeSpace();
            TextView disk = Ui.badge(this, Fmt.size(free) + " free");
            if (in != null) disk.setTextColor(mTheme.textTertiary);
            if (free < 500L * 1024 * 1024) {
                disk.setTextColor(mTheme.rec);
                disk.setText("Low space: " + Fmt.size(free));
            }
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp2.leftMargin = Ui.dp(this, 6);
            disk.setLayoutParams(lp2);
            mPills.addView(disk);
        }

        if (out != null && out.isUsb) {
            TextView o = Ui.badge(this, "OUT " + out.name);
            LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp3.leftMargin = Ui.dp(this, 6);
            o.setLayoutParams(lp3);
            mPills.addView(o);
        }

        updateRecIndicator();
        updateSidebarFooter(in);
    }

    private void updateSidebarFooter(AudioDevice in) {
        View dev = mSidebar.findViewWithTag("footer_device");
        View spec = mSidebar.findViewWithTag("footer_spec");
        if (dev instanceof TextView) {
            ((TextView) dev).setText(in == null ? "No input" : in.name);
            ((TextView) dev).setTextColor(in != null && in.isUsb ? mTheme.accent : mTheme.textPrimary);
        }
        if (spec instanceof TextView) {
            ((TextView) spec).setText(in == null ? "" : in.usbSpec() + "  \u00b7  " + in.shortSpec());
        }
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
            refreshTopbar();
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
        if (mShell.isDrawerMode() && mShell.isOpen()) {
            mShell.closeDrawer();
            return;
        }
        if (mPage != PAGE_DASHBOARD) {
            navigate(PAGE_DASHBOARD);
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
