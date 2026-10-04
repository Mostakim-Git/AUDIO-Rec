package com.mostakim.audiorec.ui.screens;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mostakim.audiorec.App;
import com.mostakim.audiorec.R;
import com.mostakim.audiorec.audio.AudioDevice;
import com.mostakim.audiorec.audio.AudioEngine;
import com.mostakim.audiorec.audio.Recorder;
import com.mostakim.audiorec.audio.UsbAudioProbe;
import com.mostakim.audiorec.db.Models.Session;
import com.mostakim.audiorec.db.Models.Track;
import com.mostakim.audiorec.ui.Dialogs;
import com.mostakim.audiorec.ui.MainActivity;
import com.mostakim.audiorec.ui.kit.Theme;
import com.mostakim.audiorec.ui.kit.Ui;
import com.mostakim.audiorec.util.Fmt;
import com.mostakim.audiorec.util.Formats;

import java.io.File;
import java.util.List;

/** Landing page: interface status, one-tap arm, library totals, recent takes. */
public class DashboardScreen extends Screen {

    public DashboardScreen(MainActivity a) {
        super(a, "Dashboard", "USB audio recording & production workstation", true);
    }

    @Override
    protected void build(LinearLayout col) {
        hero();
        stats();
        quickActions();
        recentTakes();
        activity();
    }

    // ------------------------------------------------------------------ hero
    private void hero() {
        AudioDevice in = act.engine().input();
        LinearLayout card = cardStyled(R.drawable.bg_tile);
        card.setPadding(Ui.dp(act, 16), Ui.dp(act, 16), Ui.dp(act, 16), Ui.dp(act, 16));

        LinearLayout head = Ui.row(act);
        ImageView logo = new ImageView(act);
        logo.setImageResource(R.drawable.ic_logo);
        logo.setColorFilter(th.accent);
        int s = Ui.dp(act, 40);
        logo.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        head.addView(logo);

        LinearLayout texts = Ui.column(act);
        texts.setPadding(Ui.dp(act, 12), 0, 0, 0);
        TextView title = Ui.title(act, in == null ? "No input selected" : in.name);
        texts.addView(title);
        TextView sub = Ui.caption(act, in == null ? "Attach a USB interface to begin"
                : (in.isUsb ? in.usbSpec() : in.typeName()) + "  \u00b7  " + in.shortSpec());
        texts.addView(sub);
        head.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (in != null && in.isUsb) {
            TextView usb = Ui.pill(act, "USB", R.drawable.bg_pill, th.accent);
            head.addView(usb);
        }
        card.addView(head);

        card.addView(Ui.spacer(act, 14));

        // current capture format
        int rate = App.get().prefs().sampleRate();
        int depth = App.get().prefs().bitDepth();
        int ch = App.get().prefs().channels();
        String container = App.get().prefs().container();
        LinearLayout fmt = Ui.row(act);
        fmt.addView(metric("RATE", Fmt.khz(rate)));
        fmt.addView(metric("DEPTH", depth + "-bit"));
        fmt.addView(metric("CHANNELS", String.valueOf(ch)));
        fmt.addView(metric("FORMAT", container.toUpperCase()));
        card.addView(fmt);

        card.addView(Ui.spacer(act, 8));

        File dir = App.get().prefs().recordDir();
        long free = dir == null ? 0 : dir.getFreeSpace();
        long seconds = Fmt.recordableSeconds(free, ch, depth, rate, container);
        TextView space = Ui.caption(act, "Recording to " + (dir == null ? "\u2014" : dir.getAbsolutePath()));
        card.addView(space);
        TextView remain = Ui.body(act, Fmt.size(free) + " free  \u00b7  about "
                + (seconds > 3600 ? (seconds / 3600) + " h " + ((seconds % 3600) / 60) + " min"
                : (seconds / 60) + " min") + " of " + container.toUpperCase() + " at these settings");
        remain.setTextColor(free < 500L * 1024 * 1024 ? th.rec : th.textSecondary);
        card.addView(remain);

        card.addView(Ui.spacer(act, 16));
        LinearLayout actions = Ui.row(act);
        TextView record = Ui.button(act, armLabel(), R.style.Btn_Rec, v -> toggleArm());
        actions.addView(record, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(Ui.spacer(act, 10));
        TextView open = Ui.button(act, "Open recorder", R.style.Btn, v -> navigate(MainActivity.PAGE_RECORDER));
        actions.addView(open, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(actions);
    }

    private String armLabel() {
        AudioEngine.State s = act.engine().state();
        switch (s) {
            case RECORDING: return "\u25cf  Recording";
            case PAUSED: return "II  Paused";
            case MONITORING: return "\u25cf  Record take";
            default: return "\u25cf  Arm recorder";
        }
    }

    private LinearLayout metric(String label, String value) {
        LinearLayout c = Ui.column(act);
        TextView v = Ui.mono(act, value);
        v.setTextSize(17);
        v.setTextColor(th.accent);
        c.addView(v);
        c.addView(Ui.text(act, label, R.style.T_Section));
        c.setPadding(0, 0, Ui.dp(act, 16), 0);
        return c;
    }

    private void toggleArm() {
        if (act.engine().isCapturing()) {
            navigate(MainActivity.PAGE_RECORDER);
            return;
        }
        if (!act.hasAudioPermission()) {
            toast("Grant microphone access first");
            return;
        }
        if (act.engine().startMonitor()) {
            navigate(MainActivity.PAGE_RECORDER);
        }
    }

    // ----------------------------------------------------------------- stats
    private void stats() {
        col.addView(section("LIBRARY"));
        long[] totals = act.store().libraryTotals();
        LinearLayout row = Ui.row(act);
        row.addView(box(stat(String.valueOf(totals[0]), "takes", null, th.textPrimary)));
        row.addView(box(stat(Fmt.size(totals[1]), "recorded", null, th.accent)));
        row.addView(box(stat(Fmt.clock(totals[2]), "total time", null, th.brand)));
        col.addView(row);

        LinearLayout row2 = Ui.row(act);
        row2.addView(box(stat(String.valueOf(act.store().sessionCount()), "sessions",
                null, th.textPrimary)));
        row2.addView(box(stat(String.valueOf(act.store().presetCount()), "presets",
                null, th.textPrimary)));
        row2.addView(box(stat(String.valueOf(act.store().exportCount()), "exports",
                null, th.textPrimary)));
        col.addView(row2);
    }

    private LinearLayout box(LinearLayout content) {
        LinearLayout c = Ui.column(act);
        c.setBackgroundResource(R.drawable.bg_card);
        c.setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 12), Ui.dp(act, 10));
        c.addView(content);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = Ui.dp(act, 8);
        lp.bottomMargin = Ui.dp(act, 8);
        c.setLayoutParams(lp);
        return c;
    }

    // --------------------------------------------------------- quick actions
    private void quickActions() {
        col.addView(section("QUICK ACTIONS"));
        LinearLayout c = card();
        LinearLayout r1 = Ui.row(act);
        r1.addView(actionTile("New session", R.drawable.ic_sessions,
                v -> Dialogs.sessionEditor(act, act.store(), null, () -> act.refreshAll())),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r1.addView(Ui.spacer(act, 10));
        r1.addView(actionTile("Import file", R.drawable.ic_folder,
                v -> navigate(MainActivity.PAGE_LIBRARY)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        c.addView(r1);
        c.addView(Ui.spacer(act, 10));
        LinearLayout r2 = Ui.row(act);
        r2.addView(actionTile("Playlist", R.drawable.ic_playlist,
                v -> navigate(MainActivity.PAGE_PLAYLIST)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r2.addView(Ui.spacer(act, 10));
        r2.addView(actionTile("Device presets", R.drawable.ic_preset,
                v -> navigate(MainActivity.PAGE_PRESETS)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        c.addView(r2);
    }

    private LinearLayout actionTile(String label, int icon, View.OnClickListener l) {
        LinearLayout t = Ui.row(act);
        t.setBackgroundResource(R.drawable.bg_btn);
        t.setPadding(Ui.dp(act, 12), Ui.dp(act, 12), Ui.dp(act, 12), Ui.dp(act, 12));
        t.setClickable(true);
        ImageView iv = new ImageView(act);
        iv.setImageResource(icon);
        iv.setColorFilter(th.accent);
        int s = Ui.dp(act, 18);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(s, s);
        ip.rightMargin = Ui.dp(act, 10);
        iv.setLayoutParams(ip);
        t.addView(iv);
        t.addView(Ui.body(act, label));
        t.setOnClickListener(l);
        return t;
    }

    // ---------------------------------------------------------- recent takes
    private void recentTakes() {
        col.addView(section("RECENT TAKES"));
        List<Track> tracks = act.store().allTracks(5);
        if (tracks.isEmpty()) {
            empty("Nothing recorded yet.\nArm the recorder and capture your first take - "
                    + "the file lands in " + folderName() + ".",
                    "Go to recorder", v -> navigate(MainActivity.PAGE_RECORDER));
            return;
        }
        for (final Track t : tracks) {
            LinearLayout row = Ui.row(act);
            row.setBackgroundResource(R.drawable.bg_list_row);
            row.setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 12), Ui.dp(act, 10));
            row.setClickable(true);

            TextView play = Ui.button(act, "\u25b6", R.style.Btn_Icon,
                    v -> act.engine().play(new File(t.filePath), t.title));
            row.addView(play);

            LinearLayout texts = Ui.column(act);
            texts.setPadding(Ui.dp(act, 12), 0, Ui.dp(act, 8), 0);
            texts.addView(Ui.body(act, t.title));
            texts.addView(Ui.caption(act, Fmt.clock(t.durationMs) + "  \u00b7  "
                    + t.formatSummary() + (t.peakDb > -0.3f ? "  \u00b7  clipped" : "")));
            row.addView(texts, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView sizeCol = Ui.caption(act, Fmt.size(t.sizeBytes));
            row.addView(sizeCol);

            row.setOnClickListener(v -> navigate(MainActivity.PAGE_LIBRARY));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(act, 6);
            row.setLayoutParams(lp);
            col.addView(row);
        }
        col.addView(Ui.button(act, "Open library", R.style.Btn,
                v -> navigate(MainActivity.PAGE_LIBRARY)));
    }

    private String folderName() {
        File d = App.get().prefs().recordDir();
        return d == null ? "the recordings folder" : d.getName();
    }

    // --------------------------------------------------------------- activity
    private void activity() {
        col.addView(section("LAST 14 DAYS"));
        LinearLayout c = card();
        int[] perDay = act.store().tracksPerDay(14);
        int max = 1;
        for (int v : perDay) max = Math.max(max, v);
        LinearLayout bars = Ui.row(act);
        bars.setGravity(Gravity.BOTTOM);
        for (int i = 0; i < perDay.length; i++) {
            View bar = new View(act);
            int h = Math.max(Ui.dp(act, 3), (int) (Ui.dp(act, 56) * (perDay[i] / (float) max)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, h, 1f);
            lp.rightMargin = Ui.dp(act, 3);
            bar.setLayoutParams(lp);
            bar.setBackgroundColor(perDay[i] > 0 ? th.accent : th.strokeSoft);
            bars.addView(bar);
        }
        c.addView(bars);
        c.addView(Ui.spacer(act, 6));
        c.addView(Ui.caption(act, "Takes captured per day. Peak day: " + max
                + " take" + (max == 1 ? "" : "s") + "."));
    }
}
