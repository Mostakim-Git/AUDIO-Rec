package com.mostakim.audiorec.ui.screens;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mostakim.audiorec.App;
import com.mostakim.audiorec.R;
import com.mostakim.audiorec.audio.FormatProbe;
import com.mostakim.audiorec.db.Models.Track;
import com.mostakim.audiorec.ui.MainActivity;
import com.mostakim.audiorec.ui.kit.Ui;
import com.mostakim.audiorec.ui.widgets.DiskBarView;
import com.mostakim.audiorec.util.Fmt;
import com.mostakim.audiorec.util.Formats;
import com.mostakim.audiorec.util.Prefs;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Storage: where takes are written, how much room is left, and library health.
 *
 * Recording to a card that is about to fill up is the classic way to lose a
 * performance, so the space estimate is shown in the format currently selected
 * and the folder can be pointed at any writable volume - internal storage, the
 * app's own directory, or a mounted SD card.
 */
public class StorageScreen extends Screen {

    private DiskBarView mBar;
    private LinearLayout mVolumes;
    private LinearLayout mHealth;

    public StorageScreen(MainActivity a) {
        super(a, "Storage", "Recording folder and space", true);
    }

    @Override
    protected void build(LinearLayout col) {
        folderCard();
        volumesCard();
        mHealth = Ui.column(act);
        col.addView(mHealth);
        healthCard();
        permissionsCard();
    }

    // ------------------------------------------------------------ current folder
    private void folderCard() {
        Prefs p = App.get().prefs();
        File recorded = p.recordDir();
        final File dir = recorded == null ? App.defaultRecordDir(act) : recorded;

        LinearLayout card = cardStyled(R.drawable.bg_tile);
        card.addView(Ui.head(act, Fmt.khz(p.sampleRate()) + "  \u00b7  " + p.bitDepth()
                + "-bit  \u00b7  " + Fmt.ch(p.channels()) + "  \u00b7  "
                + Formats.displayName(p.container())));
        TextView path = Ui.caption(act, dir.getAbsolutePath());
        path.setTextColor(th.textTertiary);
        card.addView(path);

        long free = freeBytes(dir);
        long total = totalBytes(dir);
        mBar = new DiskBarView(act);
        mBar.setData(total - free, total, Fmt.size(free) + " free", Fmt.size(total));
        card.addView(mBar);
        card.addView(Ui.caption(act, DiskBarView.usageText(total - free, total)));

        long secs = Fmt.recordableSeconds(free, p.channels(), p.bitDepth(),
                p.sampleRate(), p.container());
        TextView est = Ui.head(act, "about " + Fmt.duration((int) Math.min(secs, Integer.MAX_VALUE)));
        est.setTextColor(secs < 300 ? th.rec : th.accent);
        card.addView(est);
        card.addView(Ui.caption(act, "at the current format \u00b7 "
                + Fmt.size(Fmt.bytesPerSecond(p.channels(), p.bitDepth(), p.sampleRate(),
                p.container())) + " per second"));

        card.addView(Ui.spacer(act, 10));
        LinearLayout row = Ui.row(act);
        row.addView(Ui.button(act, "Record here", R.style.Btn_Primary, v -> {
            App.get().prefs().setRecordDir(dir);
            toast("Recording folder set to " + dir.getName());
            refresh();
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.spacer(act, 8));
        row.addView(Ui.button(act, "Type a path\u2026", R.style.Btn, v -> askPath()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row);
    }

    private void askPath() {
        Ui.prompt(act, "Recording folder", "/storage/XXXX-XXXX/Recordings",
                App.get().prefs().recordDir() == null ? "" : App.get().prefs().recordDir().getAbsolutePath(),
                false, false, value -> {
                    if (value == null || value.trim().isEmpty()) return;
                    File f = new File(value.trim());
                    String problem = prepare(f);
                    if (problem != null) {
                        Ui.longToast(act, problem);
                        return;
                    }
                    App.get().prefs().setRecordDir(f);
                    toast("Recording folder set");
                    refresh();
                });
    }

    // -------------------------------------------------------------- candidates
    private void volumesCard() {
        LinearLayout card = card("Available volumes", "tap one to record there");
        mVolumes = Ui.column(act);
        card.addView(mVolumes);
        fillVolumes();
        LinearLayout row = Ui.row(act);
        row.addView(Ui.button(act, "Re-scan volumes", R.style.Btn_Small, v -> fillVolumes()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.spacer(act, 8));
        row.addView(Ui.button(act, "New folder\u2026", R.style.Btn_Small, v -> newFolder()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row);
    }

    private void fillVolumes() {
        if (mVolumes == null) return;
        mVolumes.removeAllViews();
        for (File v : candidates()) {
            final File dir = new File(v, "Recordings");
            long free = freeBytes(v);
            boolean current = App.get().prefs().recordDir() != null
                    && App.get().prefs().recordDir().getAbsolutePath().startsWith(v.getAbsolutePath());
            boolean writable = v.canWrite() || v.canRead();

            LinearLayout row = Ui.row(act);
            row.setBackgroundResource(current ? R.drawable.bg_selected : R.drawable.bg_list_row);
            row.setPadding(Ui.dp(act, 10), Ui.dp(act, 10), Ui.dp(act, 10), Ui.dp(act, 10));
            LinearLayout texts = Ui.column(act);
            texts.addView(Ui.body(act, label(v)));
            texts.addView(Ui.caption(act, v.getAbsolutePath() + "  \u00b7  " + Fmt.size(free)
                    + " free" + (writable ? "" : "  \u00b7  read-only")));
            row.addView(texts, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (current) row.addView(Ui.pill(act, "IN USE", R.drawable.bg_pill, th.accent));
            row.setOnClickListener(x -> {
                String problem = prepare(dir);
                if (problem != null) {
                    Ui.longToast(act, problem);
                    return;
                }
                App.get().prefs().setRecordDir(dir);
                toast("Recording folder: " + dir.getAbsolutePath());
                refresh();
            });
            mVolumes.addView(row);
        }
    }

    /** every place a take could plausibly be written on this device */
    private List<File> candidates() {
        List<File> out = new ArrayList<>();
        addCandidate(out, App.defaultRecordDir(act));
        addCandidate(out, act.getFilesDir());
        addCandidate(out, new File(Environment.getExternalStorageDirectory(), "Recordings"));
        try {
            File[] ext = act.getExternalFilesDirs(null);
            if (ext != null) {
                for (File f : ext) {
                    if (f != null) addCandidate(out, new File(f, "Recordings"));
                }
            }
            File[] media = act.getExternalMediaDirs();
            if (media != null) {
                for (File f : media) {
                    if (f != null) addCandidate(out, new File(f, "Recordings"));
                }
            }
        } catch (Exception ignored) {
        }
        File storage = new File("/storage");
        File[] vols = storage.listFiles();
        if (vols != null) {
            for (File v : vols) {
                String n = v.getName();
                if ("emulated".equals(n) || "self".equals(n) || "enc_emulated".equals(n)) continue;
                if (v.isDirectory() && v.canRead()) addCandidate(out, new File(v, "Recordings"));
            }
        }
        // de-duplicate by canonical path, keeping the shortest label
        Map<String, File> uniq = new HashMap<>();
        for (File f : out) {
            String key;
            try {
                key = f.getCanonicalPath();
            } catch (Exception e) {
                key = f.getAbsolutePath();
            }
            if (!uniq.containsKey(key)) uniq.put(key, f);
        }
        return new ArrayList<>(uniq.values());
    }

    private void addCandidate(List<File> out, File f) {
        if (f == null) return;
        File parent = f.getParentFile();
        if (parent != null && parent.canRead() && parent.canWrite()) out.add(f);
        else if (f.canRead() || f.canWrite()) out.add(f);
    }

    private String label(File v) {
        String p = v.getAbsolutePath();
        if (p.startsWith("/storage/emulated") || p.startsWith("/sdcard")) return "Internal shared storage";
        if (p.startsWith("/data/")) return "App private storage";
        if (p.startsWith("/storage/")) return "Removable volume " + new File(p).getParentFile().getName();
        return v.getName().isEmpty() ? p : v.getName();
    }

    private void newFolder() {
        Ui.prompt(act, "New folder name", "Session masters", "", false, false, name -> {
            if (name == null || name.trim().isEmpty()) return;
            File base = App.get().prefs().recordDir();
            if (base == null) base = App.defaultRecordDir(act);
            File dir = new File(base, com.mostakim.audiorec.util.Ids.safeName(name, "Recordings"));
            String problem = prepare(dir);
            if (problem != null) {
                Ui.longToast(act, problem);
                return;
            }
            App.get().prefs().setRecordDir(dir);
            toast("Recording folder: " + dir.getAbsolutePath());
            refresh();
        });
    }

    /** create + probe a folder; returns null when usable, else the reason */
    private String prepare(File dir) {
        try {
            if (!dir.exists() && !dir.mkdirs()) {
                return "Could not create " + dir.getAbsolutePath();
            }
            File probe = new File(dir, ".audiorec-write-test");
            java.io.FileOutputStream os = new java.io.FileOutputStream(probe);
            os.write(1);
            os.close();
            //noinspection ResultOfMethodCallIgnored
            probe.delete();
            return null;
        } catch (Exception e) {
            return "Not writable: " + dir.getAbsolutePath() + "\n" + e.getMessage();
        }
    }

    // ------------------------------------------------------------ library health
    private void healthCard() {
        if (mHealth == null) return;
        mHealth.removeAllViews();
        LinearLayout card = Ui.card(act);
        card.addView(Ui.head(act, "Library health"));

        List<Track> all = act.store().allTracks(0);
        int gone = 0;
        long bytes = 0;
        for (Track t : all) {
            File f = new File(t.filePath);
            if (!f.exists()) gone++;
            else bytes += f.length();
        }
        final int missing = gone;
        Set<String> known = new HashSet<>();
        for (Track t : all) known.add(t.filePath);

        File dir = App.get().prefs().recordDir();
        if (dir == null) dir = App.defaultRecordDir(act);
        int orphans = 0, empty = 0;
        long orphanBytes = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.isFile() || !Formats.isPlayable(f.getName())) continue;
                if (f.length() == 0) empty++;
                else if (!known.contains(f.getAbsolutePath())) {
                    orphans++;
                    orphanBytes += f.length();
                }
            }
        }

        card.addView(Ui.caption(act, all.size() + " takes in the database  \u00b7  "
                + Fmt.size(bytes) + " of audio  \u00b7  " + missing + " missing"));
        card.addView(Ui.caption(act, orphans + " playable file"
                + (orphans == 1 ? "" : "s") + " in " + dir.getName() + " not in the library ("
                + Fmt.size(orphanBytes) + ")  \u00b7  " + empty + " empty file"
                + (empty == 1 ? "" : "s")));

        LinearLayout row = Ui.row(act);
        row.addView(Ui.button(act, "Add " + orphans + " to library", R.style.Btn_Small, v -> {
            File d = App.get().prefs().recordDir();
            if (d == null) d = App.defaultRecordDir(act);
            File[] fs = d.listFiles();
            if (fs == null) {
                toast("Folder not readable");
                return;
            }
            int added = 0;
            for (File f : fs) {
                if (!f.isFile() || f.length() == 0 || !Formats.isPlayable(f.getName())) continue;
                if (known.contains(f.getAbsolutePath())) continue;
                FormatProbe.Info info = FormatProbe.probe(f);
                Track t = new Track();
                t.title = f.getName();
                t.filePath = f.getAbsolutePath();
                t.container = info.container;
                t.sampleRate = info.sampleRate;
                t.bitDepth = info.bitDepth;
                t.channels = info.channels;
                t.durationMs = info.durationMs;
                t.sizeBytes = f.length();
                t.sessionId = App.get().prefs().lastSessionId();
                t.notes = "Found by the storage scan";
                act.store().insert(t);
                added++;
            }
            toast(added + " added to the library");
            act.refreshTopbar();
            healthCard();
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        row.addView(Ui.spacer(act, 6));
        row.addView(Ui.button(act, "Remove " + missing + " missing", R.style.Btn_Small, v -> {
            if (missing == 0) {
                toast("Nothing missing");
                return;
            }
            Ui.confirm(act, "Remove missing entries?",
                    missing + " take" + (missing == 1 ? "" : "s") + " point at files that no "
                            + "longer exist. The database rows will be removed; the audio is "
                            + "already gone.",
                    "Remove rows", () -> {
                        int n = 0;
                        for (Track t : act.store().allTracks(0)) {
                            if (!new File(t.filePath).exists()) {
                                act.store().delete(t, false);
                                n++;
                            }
                        }
                        toast(n + " row" + (n == 1 ? "" : "s") + " removed");
                        act.refreshTopbar();
                        healthCard();
                    });
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        card.addView(row);

        if (empty > 0) {
            LinearLayout row2 = Ui.row(act);
            row2.addView(Ui.button(act, "Delete " + empty + " empty file"
                            + (empty == 1 ? "" : "s"), R.style.Btn_Small, v -> {
                        File d = App.get().prefs().recordDir();
                        if (d == null) d = App.defaultRecordDir(act);
                        File[] fs = d.listFiles();
                        int n = 0;
                        if (fs != null) {
                            for (File f : fs) {
                                if (f.isFile() && f.length() == 0 && f.getName().endsWith(".wav")) {
                                    //noinspection ResultOfMethodCallIgnored
                                    f.delete();
                                    n++;
                                }
                            }
                        }
                        toast(n + " empty file" + (n == 1 ? "" : "s") + " deleted");
                        healthCard();
                    }), new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            card.addView(row2);
        }
    }

    // -------------------------------------------------------------- permissions
    private void permissionsCard() {
        LinearLayout card = card("Storage access", null);
        boolean allFiles;
        if (Build.VERSION.SDK_INT >= 30) {
            allFiles = Environment.isExternalStorageManager();
        } else {
            allFiles = act.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        card.addView(Ui.pill(act, allFiles ? "FULL ACCESS GRANTED" : "APP-SCOPED STORAGE",
                allFiles ? R.drawable.bg_pill : R.drawable.bg_pill_warn,
                allFiles ? th.ok : th.warn));
        card.addView(Ui.spacer(act, 8));
        card.addView(Ui.caption(act, allFiles
                ? "AUDIO-rec can write to SD cards and any folder you point it at. Nothing is "
                + "uploaded; files stay where you put them."
                : "Without \"All files access\" Android lets the app write only to its own "
                + "folders. Recording works - choose a folder under the app's own directory - "
                + "but writing to an SD card needs full access."));
        if (!allFiles && Build.VERSION.SDK_INT >= 30) {
            LinearLayout row = Ui.row(act);
            row.addView(Ui.button(act, "Grant all-files access", R.style.Btn_Primary, v -> {
                try {
                    Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    i.setData(Uri.parse("package:" + act.getPackageName()));
                    act.startActivity(i);
                } catch (Exception e) {
                    try {
                        act.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    } catch (Exception e2) {
                        toast("Open Settings \u2192 Apps \u2192 AUDIO-rec \u2192 Permissions");
                    }
                }
            }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            card.addView(row);
        } else if (!allFiles) {
            LinearLayout row = Ui.row(act);
            row.addView(Ui.button(act, "Request storage permission", R.style.Btn_Primary, v ->
                            act.requestPermissions(new String[]{
                                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4712)),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            card.addView(row);
        }
    }

    // ------------------------------------------------------------------ numbers
    private long freeBytes(File dir) {
        try {
            StatFs s = new StatFs(dir.getAbsolutePath());
            if (Build.VERSION.SDK_INT >= 18) return s.getAvailableBytes();
            return (long) s.getAvailableBlocks() * s.getBlockSize();
        } catch (Exception e) {
            return 0;
        }
    }

    private long totalBytes(File dir) {
        try {
            StatFs s = new StatFs(dir.getAbsolutePath());
            if (Build.VERSION.SDK_INT >= 18) return s.getTotalBytes();
            return (long) s.getBlockCount() * s.getBlockSize();
        } catch (Exception e) {
            return 0;
        }
    }
}
