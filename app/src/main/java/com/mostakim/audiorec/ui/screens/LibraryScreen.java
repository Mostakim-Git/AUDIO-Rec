package com.mostakim.audiorec.ui.screens;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mostakim.audiorec.App;
import com.mostakim.audiorec.R;
import com.mostakim.audiorec.audio.AudioEngine;
import com.mostakim.audiorec.audio.Exporter;
import com.mostakim.audiorec.audio.FormatProbe;
import com.mostakim.audiorec.audio.PlaybackEngine;
import com.mostakim.audiorec.db.Models.Export;
import com.mostakim.audiorec.db.Models.Session;
import com.mostakim.audiorec.db.Models.Track;
import com.mostakim.audiorec.ui.Dialogs;
import com.mostakim.audiorec.ui.MainActivity;
import com.mostakim.audiorec.ui.kit.Ui;
import com.mostakim.audiorec.ui.widgets.LevelMeterView;
import com.mostakim.audiorec.util.Fmt;
import com.mostakim.audiorec.util.Formats;

import java.io.File;
import java.util.List;

/**
 * The library: every take, with playback, rename, star, share, export and delete.
 *
 * Takes whose file has been removed behind the app's back are shown as missing
 * rather than silently disappearing - a recorder that hides lost audio is worse
 * than one that admits it.
 */
public class LibraryScreen extends Screen implements AudioEngine.Listener {

    private EditText mSearch;
    private LevelMeterView mPlayMeters;
    private TextView mNowPlaying, mPosition;
    private LinearLayout mList;
    private android.widget.SeekBar mScrub;
    private boolean mScrubbing;

    public LibraryScreen(MainActivity a) {
        super(a, "Library", "Every recorded take", true);
    }

    @Override
    protected void build(LinearLayout col) {
        playerBar();
        searchRow();
        mList = Ui.column(act);
        col.addView(mList);
        rebuildList();
        importRow();
    }

    // ------------------------------------------------------------ player bar
    private void playerBar() {
        LinearLayout card = cardStyled(R.drawable.bg_tile);
        LinearLayout row = Ui.row(act);
        mNowPlaying = Ui.body(act, "Nothing playing");
        mNowPlaying.setTextColor(th.textSecondary);
        row.addView(mNowPlaying, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        mPlayMeters = new LevelMeterView(act);
        mPlayMeters.setChannelCount(2);
        mPlayMeters.setShowScale(false);
        mPlayMeters.setPeakHoldMs(1500);
        mPlayMeters.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(act, 74), Ui.dp(act, 30)));
        row.addView(mPlayMeters);
        card.addView(row);

        mPosition = Ui.caption(act, "00:00:00 / 00:00:00");
        card.addView(mPosition);

        mScrub = new android.widget.SeekBar(act);
        mScrub.setMax(1000);
        mScrub.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar s, int progress, boolean fromUser) {
                if (fromUser) mScrubbing = true;
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar s) {
                mScrubbing = true;
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar s) {
                mScrubbing = false;
            }
        });
        card.addView(mScrub);

        LinearLayout controls = Ui.row(act);
        controls.addView(Ui.button(act, "\u25b6", R.style.Btn_Icon,
                        v -> resumeCurrent()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        controls.addView(Ui.spacer(act, 6));
        controls.addView(Ui.button(act, "II", R.style.Btn_Icon, v -> {
            PlaybackEngine p = act.engine().playback();
            if (p.state() == PlaybackEngine.State.PLAYING) p.pause();
            else if (p.state() == PlaybackEngine.State.PAUSED) p.resume();
            onPlaybackChanged();
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        controls.addView(Ui.spacer(act, 6));
        controls.addView(Ui.button(act, "\u25a0", R.style.Btn_Icon,
                        v -> act.engine().stopPlayback()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        controls.addView(Ui.spacer(act, 6));
        controls.addView(Ui.button(act, "Play all", R.style.Btn_Small, v -> playAll()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        card.addView(controls);
    }

    private Track mCurrent;

    private void searchRow() {
        col.addView(section("TAKES"));
        LinearLayout row = Ui.row(act);
        mSearch = Ui.textInput(act, "Search takes, devices, notes\u2026", "");
        row.addView(mSearch, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.spacer(act, 6));
        row.addView(Ui.button(act, "Find", R.style.Btn_Small, v -> rebuildList()));
        col.addView(row);
        col.addView(Ui.spacer(act, 10));
    }

    private void rebuildList() {
        if (mList == null) return;
        mList.removeAllViews();
        final String query = mSearch == null ? "" : mSearch.getText().toString().trim();
        List<Track> tracks = query.isEmpty() ? act.store().allTracks(0)
                : act.store().searchTracks(query);

        if (tracks.isEmpty()) {
            empty(query.isEmpty()
                            ? "No takes yet.\nArm the recorder and capture something - WAV, FLAC, "
                            + "AIFF and OGG all land here with their real format read back from disk."
                            : "Nothing matches \"" + query + "\".",
                    query.isEmpty() ? "Open recorder" : null,
                    v -> navigate(MainActivity.PAGE_RECORDER));
            return;
        }

        for (final Track t : tracks) {
            final File f = new File(t.filePath);
            boolean missing = !f.exists();

            LinearLayout row = Ui.row(act);
            row.setBackgroundResource(R.drawable.bg_list_row);
            row.setPadding(Ui.dp(act, 10), Ui.dp(act, 10), Ui.dp(act, 10), Ui.dp(act, 10));

            TextView play = Ui.button(act, "\u25b6", R.style.Btn_Icon,
                    v -> act.engine().play(f, t.title));
            row.addView(play);

            LinearLayout texts = Ui.column(act);
            texts.setPadding(Ui.dp(act, 12), 0, Ui.dp(act, 6), 0);
            LinearLayout titleRow = Ui.row(act);
            // the title yields: without the weight a long take name eats the row and
            // squeezes the badges that follow it down to nothing
            TextView title = Ui.body(act, t.title);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            titleRow.addView(title, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (t.starred) {
                TextView star = Ui.caption(act, "  \u2605");
                star.setTextColor(th.brand);
                titleRow.addView(star);
            }
            if (missing) {
                TextView gone = Ui.pill(act, "missing", R.drawable.bg_pill_rec, th.rec);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = Ui.dp(act, 8);
                gone.setLayoutParams(lp);
                titleRow.addView(gone);
            }
            texts.addView(titleRow);
            texts.addView(Ui.caption(act, Fmt.clock(t.durationMs) + "  \u00b7  " + t.formatSummary()));
            TextView meta = Ui.caption(act, (t.deviceName.isEmpty() ? "unknown device" : t.deviceName)
                    + "  \u00b7  take #" + t.takeNo + "  \u00b7  " + Fmt.size(t.sizeBytes)
                    + "  \u00b7  peak " + Fmt.dbShort(t.peakDb) + " dB");
            meta.setTextColor(th.textTertiary);
            texts.addView(meta);
            row.addView(texts, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView actions = Ui.button(act, "\u22ef", R.style.Btn_Icon, v -> showTrackMenu(t, f));
            row.addView(actions);

            row.setOnClickListener(v -> {
                mCurrent = t;
                act.engine().play(f, t.title);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(act, 6);
            row.setLayoutParams(lp);
            mList.addView(row);
        }
    }

    private void showTrackMenu(final Track t, final File f) {
        final String[] items = {
                "Play",
                "Details",
                "Rename",
                t.starred ? "Remove star" : "Star",
                "Move to session\u2026",
                "Export as\u2026",
                "Share",
                "Delete"
        };
        new android.app.AlertDialog.Builder(act)
                .setTitle(t.title)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            mCurrent = t;
                            act.engine().play(f, t.title);
                            break;
                        case 1:
                            Dialogs.trackDetails(act, act.store(), t, () -> refresh());
                            break;
                        case 2:
                            Dialogs.renameTrack(act, act.store(), t, () -> refresh());
                            break;
                        case 3:
                            act.store().setStarred(t.id, !t.starred);
                            rebuildList();
                            break;
                        case 4:
                            Dialogs.moveTrack(act, act.store(), t, () -> refresh());
                            break;
                        case 5:
                            export(t);
                            break;
                        case 6:
                            Dialogs.shareFile(act, f, Formats.mimeFor(t.container));
                            break;
                        case 7:
                            Dialogs.deleteTrack(act, act.store(), t, () -> {
                                refresh();
                                act.refreshTopbar();
                            });
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void export(final Track t) {
        Dialogs.exportTrack(act, t, (container, depth, rate) ->
                Exporter.start(act, act.store(), t, container, depth, rate, new Exporter.Callback() {
                    @Override
                    public void onExportDone(Export e) {
                        toast("Exported " + e.name + "  \u00b7  " + Fmt.size(e.sizeBytes));
                        refresh();
                        act.refreshTopbar();
                    }

                    @Override
                    public void onExportFailed(String message) {
                        Ui.longToast(act, "Export failed: " + message);
                    }
                }));
    }

    private void importRow() {
        col.addView(Ui.spacer(act, 8));
        LinearLayout c = card("Import audio", "wav / aiff / flac / ogg");
        c.addView(Ui.caption(act, "Load files from anywhere on the device for playback, "
                + "or drop them in the recordings folder and use Rescan in Storage."));
        LinearLayout row = Ui.row(act);
        row.addView(Ui.button(act, "Pick audio file", R.style.Btn, v -> pickFile()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.spacer(act, 8));
        row.addView(Ui.button(act, "Scan folder", R.style.Btn, v -> scanFolder()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        c.addView(row);
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        try {
            act.startActivityForResult(i, REQ_PICK);
        } catch (Exception e) {
            toast("No file picker available");
        }
    }

    public static final int REQ_PICK = 3101;

    /** called by the activity after the picker returns */
    public void handlePicked(Uri uri) {
        if (uri == null) return;
        // the picker offers audio/*, which includes formats this app will not
        // play - MP3 above all (patented).  Refuse them here, by name and again
        // by content below, so no such file can reach the library.
        String picked = uri.getLastPathSegment();
        if (picked != null && !Formats.isPlayable(picked)) {
            toast("AUDIO-rec plays " + Formats.playbackExtensions()
                    + " only - MP3 and other codecs are not supported.");
            return;
        }
        File dir = App.get().prefs().recordDir();
        if (dir == null) dir = App.defaultRecordDir(act);
        File out = new File(dir, "imported-" + System.currentTimeMillis() + guessExt(uri));
        // the same rule for the extension we are about to give the copy
        if (!Formats.isPlayable(out.getName())) {
            toast("AUDIO-rec cannot import that format.");
            return;
        }
        try {
            java.io.InputStream in = act.getContentResolver().openInputStream(uri);
            java.io.OutputStream os = new java.io.FileOutputStream(out);
            byte[] buf = new byte[1 << 16];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                total += n;
            }
            os.close();
            in.close();
            FormatProbe.Info info = FormatProbe.probe(out);
            if (!Formats.CONTAINERS.contains(info.container)) {
                // the extension lied: throw the copy away instead of keeping a
                // track the engine cannot decode
                //noinspection ResultOfMethodCallIgnored
                out.delete();
                toast("That file is not " + Formats.playbackExtensions()
                        + " - nothing was imported.");
                return;
            }
            Track t = new Track();
            t.title = out.getName();
            t.filePath = out.getAbsolutePath();
            t.container = info.container;
            t.sampleRate = info.sampleRate;
            t.bitDepth = info.bitDepth;
            t.channels = info.channels;
            t.durationMs = info.durationMs;
            t.sizeBytes = total;
            t.sessionId = App.get().prefs().lastSessionId();
            t.notes = "Imported from " + uri.getLastPathSegment();
            act.store().insert(t);
            toast("Imported " + info.describe());
            refresh();
        } catch (Exception e) {
            toast("Import failed: " + e.getMessage());
        }
    }

    private String guessExt(Uri uri) {
        String s = uri.getLastPathSegment();
        if (s != null && s.contains(".")) return s.substring(s.lastIndexOf('.'));
        return ".wav";
    }

    /** import everything playable that is already sitting in the folder */
    private void scanFolder() {
        File dir = App.get().prefs().recordDir();
        if (dir == null || !dir.isDirectory()) {
            toast("Recording folder is not available");
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) return;
        int added = 0;
        java.util.Set<String> known = new java.util.HashSet<>();
        for (Track t : act.store().allTracks(0)) known.add(t.filePath);
        for (File f : files) {
            if (!f.isFile() || !Formats.isPlayable(f.getName())) continue;
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
            t.notes = "Found by folder scan";
            act.store().insert(t);
            added++;
        }
        toast(added == 0 ? "Nothing new in " + dir.getName()
                : added + " file" + (added == 1 ? "" : "s") + " added to the library");
        refresh();
    }

    // ------------------------------------------------------------------ player
    private void resumeCurrent() {
        PlaybackEngine p = act.engine().playback();
        switch (p.state()) {
            case PAUSED:
                p.resume();
                break;
            case PLAYING:
                break;
            default:
                if (mCurrent != null) act.engine().play(new File(mCurrent.filePath), mCurrent.title);
                else playAll();
        }
        onPlaybackChanged();
    }

    private void playAll() {
        List<Track> tracks = act.store().allTracks(1);
        if (tracks.isEmpty()) {
            toast("Nothing to play");
            return;
        }
        Track t = tracks.get(0);
        mCurrent = t;
        act.engine().play(new File(t.filePath), t.title);
    }

    public void onPlaybackChanged() {
        if (mNowPlaying == null) return;
        PlaybackEngine p = act.engine().playback();
        switch (p.state()) {
            case PLAYING:
                mNowPlaying.setText("\u25b6 " + p.title());
                mNowPlaying.setTextColor(th.accent);
                break;
            case PAUSED:
                mNowPlaying.setText("II " + p.title());
                mNowPlaying.setTextColor(th.warn);
                break;
            default:
                mNowPlaying.setText("Nothing playing");
                mNowPlaying.setTextColor(th.textSecondary);
                break;
        }
    }

    // -------------------------------------------------------------- listeners
    @Override
    public void onResume() {
        act.engine().addListener(this);
        onPlaybackChanged();
    }

    @Override
    public void onPause() {
        act.engine().removeListener(this);
    }

    @Override
    public void onLevels(float[] rmsDb, float[] peakDb, int channels) {
        // capture meters belong to the recorder screen
    }

    @Override
    public void onPlaybackLevels(float[] rmsDb, float[] peakDb, int channels) {
        act.runOnUiThread(() -> {
            if (mPlayMeters != null) mPlayMeters.setLevels(rmsDb, peakDb);
        });
    }

    @Override
    public void onPlaybackPosition(long positionMs, long durationMs) {
        act.runOnUiThread(() -> {
            if (mPosition == null) return;
            mPosition.setText(Fmt.clock(positionMs) + " / " + Fmt.clock(durationMs));
            if (!mScrubbing && durationMs > 0) {
                mScrub.setProgress((int) (1000L * positionMs / durationMs));
            }
        });
    }
}
