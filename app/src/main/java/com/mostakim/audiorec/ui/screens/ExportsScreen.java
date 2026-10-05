package com.mostakim.audiorec.ui.screens;

import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mostakim.audiorec.R;
import com.mostakim.audiorec.audio.Exporter;
import com.mostakim.audiorec.share.Downloads;
import com.mostakim.audiorec.audio.FormatProbe;
import com.mostakim.audiorec.db.Models.Export;
import com.mostakim.audiorec.db.Models.Track;
import com.mostakim.audiorec.ui.Dialogs;
import com.mostakim.audiorec.ui.MainActivity;
import com.mostakim.audiorec.ui.kit.Ui;
import com.mostakim.audiorec.util.Fmt;
import com.mostakim.audiorec.util.Formats;

import java.io.File;
import java.util.List;

/**
 * Export files: every rendered copy, with re-export, share, reveal and delete.
 *
 * Exports are copies, never the take itself, so re-rendering at another depth
 * or container is always safe.
 */
public class ExportsScreen extends Screen {

    public ExportsScreen(MainActivity a) {
        super(a, "Exports", "Rendered copies for sharing", true);
    }

    @Override
    protected void build(LinearLayout col) {
        List<Export> exports = act.store().exports();

        long total = 0;
        int missing = 0;
        for (Export e : exports) {
            total += e.sizeBytes;
            if (!new File(e.filePath).exists()) missing++;
        }

        LinearLayout actions = Ui.row(act);
        actions.addView(Ui.button(act, "+  Export a take", R.style.Btn_Primary,
                v -> pickTrack()), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(Ui.spacer(act, 8));
        actions.addView(Ui.button(act, "Clean up", R.style.Btn_Small, v -> cleanUp()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f));
        col.addView(actions);
        col.addView(Ui.spacer(act, 12));

        LinearLayout stats = Ui.row(act);
        stats.addView(stat(String.valueOf(exports.size()),
                exports.size() == 1 ? "export" : "exports", null, th.accent),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stats.addView(stat(Fmt.size(total), "on disk", null, th.textPrimary),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stats.addView(stat(String.valueOf(missing), "missing",
                missing == 0 ? "all present" : "file gone",
                missing == 0 ? th.ok : th.rec),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(stats);
        col.addView(Ui.spacer(act, 12));

        if (exports.isEmpty()) {
            empty("No exports yet.\nPick a take, choose FLAC / WAV / AIFF / OGG and a bit depth, "
                    + "and AUDIO-rec renders a copy without touching the original.",
                    "Export a take", v -> pickTrack());
            return;
        }

        col.addView(section("RENDERED COPIES"));
        for (final Export e : exports) {
            final File f = new File(e.filePath);
            boolean ok = f.exists();
            LinearLayout card = Ui.card(act);
            card.setBackgroundResource(ok ? R.drawable.bg_card : R.drawable.bg_card_flat);

            LinearLayout head = Ui.row(act);
            head.addView(Ui.head(act, e.name),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            head.addView(Ui.pill(act, ok ? e.status.toUpperCase() : "MISSING",
                    ok ? R.drawable.bg_pill : R.drawable.bg_pill_rec,
                    ok ? th.accent : th.rec));
            Ui.addWide(card, head);

            card.addView(Ui.caption(act, "from  " + (e.trackTitle.isEmpty()
                    ? "take #" + e.trackId : e.trackTitle)));
            card.addView(Ui.caption(act, Exporter.describe(e) + "  \u00b7  "
                    + Fmt.clock(e.durationMs) + "  \u00b7  " + Fmt.size(e.sizeBytes)
                    + "  \u00b7  " + Fmt.stampShort(e.createdAt)));
            if (!e.note.isEmpty()) {
                TextView n = Ui.caption(act, e.note);
                n.setTextColor(th.textTertiary);
                Ui.addWide(card, n);
            }
            if (!ok) {
                TextView w = Ui.caption(act, "The file was moved or deleted outside the app. "
                        + "Re-export from the source take to rebuild it.");
                w.setTextColor(th.rec);
                Ui.addWide(card, w);
            }

            card.addView(Ui.spacer(act, 8));
            LinearLayout row = Ui.row(act);
            row.addView(Ui.button(act, "Play", R.style.Btn_Small, v -> play(e)),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.spacer(act, 6));
            row.addView(Ui.button(act, "Share", R.style.Btn_Small, v ->
                            Dialogs.shareFile(act, f, Formats.mimeFor(e.container))),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.spacer(act, 6));
            row.addView(Ui.button(act, "Save", R.style.Btn_Small, v ->
                            Dialogs.saveToDownloads(act, f, Formats.mimeFor(e.container))),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.spacer(act, 6));
            row.addView(Ui.button(act, "Verify", R.style.Btn_Small, v -> verify(e, f)),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.spacer(act, 6));
            row.addView(Ui.button(act, "More", R.style.Btn_Small, v -> more(e, f)),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Ui.addWide(card, row);
            col.addView(card);
        }

        col.addView(Ui.spacer(act, 8));
        LinearLayout note = card("Where exports go", null);
        note.addView(Ui.caption(act, "In the app (private): " + Exporter.exportsDir(act)));
        TextView visible = Ui.caption(act, "Every app can see: " + Downloads.visiblePath(""));
        visible.setTextColor(th.accent);
        Ui.addWide(note, visible);
        note.addView(Ui.caption(act, "A finished export is copied there automatically; "
                + "Save does it again for an older one. Rendering is offline: the take is "
                + "decoded locally, "
                + "resampled only when a different rate is asked for, and written with the same "
                + "encoders the recorder uses. No file is uploaded anywhere."));
    }

    private void pickTrack() {
        List<Track> tracks = act.store().allTracks(0);
        if (tracks.isEmpty()) {
            toast("Record something first");
            navigate(MainActivity.PAGE_RECORDER);
            return;
        }
        final String[] names = new String[tracks.size()];
        for (int i = 0; i < tracks.size(); i++) {
            Track t = tracks.get(i);
            names[i] = t.title + "   " + Fmt.clock(t.durationMs) + "  " + t.formatSummary();
        }
        new android.app.AlertDialog.Builder(act)
                .setTitle("Export which take?")
                .setItems(names, (d, which) -> Dialogs.exportTrack(act, tracks.get(which),
                        (container, depth, rate) -> Exporter.start(act, act.store(),
                                tracks.get(which), container, depth, rate,
                                new Exporter.Callback() {
                                    @Override
                                    public void onExportDone(Export e) {
                                        toast("Exported " + e.name + "  \u00b7  "
                                                + Fmt.size(e.sizeBytes));
                                        refresh();
                                        act.refreshHeader();
                                    }

                                    @Override
                                    public void onExportPublished(Export e, String visiblePath) {
                                        Ui.longToast(act, "Copy saved to " + visiblePath
                                                + "\nThe app's own copy stays in its folder.");
                                    }

                                    @Override
                                    public void onExportFailed(String message) {
                                        Ui.longToast(act, "Export failed: " + message);
                                    }
                                })))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void play(Export e) {
        File f = new File(e.filePath);
        if (!f.exists()) {
            toast("File is missing");
            return;
        }
        act.engine().play(f, e.name);
        toast("Playing " + e.name);
    }

    private void verify(Export e, File f) {
        if (!f.exists()) {
            Ui.longToast(act, "Missing: " + f.getAbsolutePath());
            return;
        }
        FormatProbe.Info info = FormatProbe.probe(f);
        StringBuilder b = new StringBuilder();
        b.append("file      ").append(f.getName()).append('\n');
        b.append("on disk   ").append(Fmt.size(f.length())).append('\n');
        b.append("header    ").append(info.valid() ? info.describe() : "unreadable").append('\n');
        b.append("expected  ").append(Exporter.describe(e)).append('\n');
        b.append("frames    ").append(info.frames).append('\n');
        b.append("duration  ").append(Fmt.clock(info.durationMs)).append('\n');
        b.append("db row    ").append(e.container.toUpperCase()).append(' ')
                .append(e.bitDepth).append("-bit ").append(e.sampleRate).append(" Hz\n");
        boolean match = info.valid() && info.sampleRate == e.sampleRate
                && info.channels == e.channels;
        b.append('\n').append(match
                ? "OK - the file matches the database row."
                : "Mismatch - the file was re-rendered or replaced outside the app.");
        Ui.dialog(act, "Verify export").setMessage(b.toString()).show();
    }

    private void more(final Export e, final File f) {
        final String[] items = {"Play", "Share", "Save to Downloads", "Re-export\u2026",
                "Show the app folder", "Delete row", "Delete row and file"};
        new android.app.AlertDialog.Builder(act)
                .setTitle(e.name)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            play(e);
                            break;
                        case 1:
                            Dialogs.shareFile(act, f, Formats.mimeFor(e.container));
                            break;
                        case 2:
                            Dialogs.saveToDownloads(act, f, Formats.mimeFor(e.container));
                            break;
                        case 3:
                            reExport(e);
                            break;
                        case 4:
                            Ui.longToast(act, "In the app: " + Exporter.exportsDir(act)
                                    + "\nThat folder is private to AUDIO-rec - Save to Downloads "
                                    + "makes a copy every other app can see.");
                            break;
                        case 5:
                            act.store().delete(e, false);
                            refresh();
                            toast("Row removed, file kept");
                            break;
                        case 6:
                            Ui.confirm(act, "Delete export?",
                                    "The row and " + e.name + " will be removed permanently.",
                                    "Delete", () -> {
                                        act.store().delete(e, true);
                                        refresh();
                                        toast("Export deleted");
                                    });
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void reExport(Export e) {
        Track t = act.store().track(e.trackId);
        if (t == null) {
            toast("The source take is gone from the library");
            return;
        }
        Dialogs.exportTrack(act, t, (container, depth, rate) ->
                Exporter.start(act, act.store(), t, container, depth, rate, new Exporter.Callback() {
                    @Override
                    public void onExportDone(Export done) {
                        toast("Exported " + done.name);
                        refresh();
                    }

                    @Override
                    public void onExportPublished(Export done, String visiblePath) {
                        Ui.longToast(act, "Copy saved to " + visiblePath
                                + "\nShare it, or find it in any file manager.");
                    }

                    @Override
                    public void onExportFailed(String message) {
                        Ui.longToast(act, "Export failed: " + message);
                    }
                }));
    }

    private void cleanUp() {
        List<Export> exports = act.store().exports();
        final java.util.List<Export> gone = new java.util.ArrayList<>();
        long bytes = 0;
        for (Export e : exports) {
            File f = new File(e.filePath);
            if (!f.exists()) {
                gone.add(e);
            } else if (!e.status.equals("ready")) {
                e.status = "ready";
                act.store().update(e);
            }
        }
        if (gone.isEmpty()) {
            toast("Nothing to clean up");
            return;
        }
        Ui.confirm(act, "Remove " + gone.size() + " stale row"
                        + (gone.size() == 1 ? "?" : "s?"),
                "The audio files are already gone; only the database rows remain.",
                "Remove rows", () -> {
                    for (Export e : gone) act.store().delete(e, false);
                    refresh();
                    toast(gone.size() + " stale row" + (gone.size() == 1 ? "" : "s") + " removed");
                });
    }
}
