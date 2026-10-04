package com.mostakim.audiorec.ui.screens;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.mostakim.audiorec.App;
import com.mostakim.audiorec.R;
import com.mostakim.audiorec.audio.AudioEngine;
import com.mostakim.audiorec.audio.FormatProbe;
import com.mostakim.audiorec.audio.PlaybackEngine;
import com.mostakim.audiorec.db.Models.Track;
import com.mostakim.audiorec.ui.MainActivity;
import com.mostakim.audiorec.ui.kit.Ui;
import com.mostakim.audiorec.util.Fmt;
import com.mostakim.audiorec.util.Formats;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Directory playlist: whatever sits in the recording folder, played in order.
 *
 * Deliberately plain - the ask was a basic folder playlist, not a media library
 * with artwork and tags.  Subfolders can be entered, the order can be sorted,
 * and playback rolls on to the next file unless Auto-advance is switched off.
 */
public class PlaylistScreen extends Screen implements AudioEngine.Listener {

    private File mFolder;
    private int mSort = 0;                      // 0 name, 1 newest, 2 size
    private boolean mAutoAdvance = true;
    private boolean mWasPlaying = false;
    private final List<File> mQueue = new ArrayList<>();
    private int mIndex = -1;
    private final Map<String, int[]> mProbe = new HashMap<>();   // path -> {ms, rate, ch, depth}

    private LinearLayout mList;
    private EditText mFilter;
    private TextView mNow, mFolderLabel, mAutoBtn;
    private SeekBar mScrub;
    private boolean mScrubbing;

    public PlaylistScreen(MainActivity a) {
        super(a, "Playlist", "Folder playlist with auto-advance", true);
    }

    @Override
    protected void build(LinearLayout col) {
        mFolder = App.get().prefs().recordDir();
        if (mFolder == null || !mFolder.isDirectory()) mFolder = App.defaultRecordDir(act);

        nowCard();
        col.addView(Ui.spacer(act, 10));
        folderCard();
        mList = Ui.column(act);
        col.addView(mList);
        rebuild();
    }

    // --------------------------------------------------------------- now card
    private void nowCard() {
        LinearLayout card = cardStyled(R.drawable.bg_tile);
        mNow = Ui.body(act, "Nothing playing");
        mNow.setTextColor(th.textSecondary);
        card.addView(mNow);

        mScrub = new SeekBar(act);
        mScrub.setMax(1000);
        mScrub.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser) mScrubbing = true;
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                mScrubbing = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                mScrubbing = false;
            }
        });
        card.addView(mScrub);

        LinearLayout row = Ui.row(act);
        row.addView(Ui.button(act, "\u23ee", R.style.Btn_Icon, v -> step(-1)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.spacer(act, 6));
        row.addView(Ui.button(act, "\u25b6 / II", R.style.Btn_Icon, v -> togglePlay()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        row.addView(Ui.spacer(act, 6));
        row.addView(Ui.button(act, "\u23ed", R.style.Btn_Icon, v -> step(1)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.spacer(act, 6));
        mAutoBtn = Ui.button(act, "Auto-advance: ON", R.style.Btn_Small, v -> {
            mAutoAdvance = !mAutoAdvance;
            mAutoBtn.setText(mAutoAdvance ? "Auto-advance: ON" : "Auto-advance: OFF");
        });
        row.addView(mAutoBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.6f));
        card.addView(row);
    }

    // ------------------------------------------------------------ folder card
    private void folderCard() {
        LinearLayout card = card("Folder", null);
        mFolderLabel = Ui.caption(act, mFolder.getAbsolutePath());
        mFolderLabel.setTextColor(th.textTertiary);
        card.addView(mFolderLabel);

        LinearLayout row = Ui.row(act);
        row.addView(Ui.button(act, "Sort: " + sortName(), R.style.Btn_Small, v -> {
            mSort = (mSort + 1) % 3;
            rebuild();
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.spacer(act, 6));
        row.addView(Ui.button(act, "Up", R.style.Btn_Small, v -> {
            File up = mFolder.getParentFile();
            if (up != null && up.canRead()) {
                mFolder = up;
                rebuild();
            } else {
                toast("Top of this volume");
            }
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f));
        row.addView(Ui.spacer(act, 6));
        row.addView(Ui.button(act, "Recordings", R.style.Btn_Small, v -> {
            File d = App.get().prefs().recordDir();
            if (d != null) {
                mFolder = d;
                rebuild();
            }
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row);

        mFilter = Ui.textInput(act, "Filter this folder\u2026", "");
        card.addView(mFilter);
        LinearLayout row2 = Ui.row(act);
        row2.addView(Ui.button(act, "Apply filter", R.style.Btn_Small, v -> rebuild()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row2.addView(Ui.spacer(act, 6));
        row2.addView(Ui.button(act, "Add all to library", R.style.Btn_Small, v -> addAllToLibrary()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        card.addView(row2);
    }

    private String sortName() {
        return mSort == 0 ? "name" : (mSort == 1 ? "newest" : "size");
    }

    // ------------------------------------------------------------------ list
    private void rebuild() {
        if (mList == null) return;
        mList.removeAllViews();
        mQueue.clear();
        mFolderLabel.setText(mFolder.getAbsolutePath());

        File[] files = mFolder.listFiles();
        List<File> found = new ArrayList<>();
        if (files != null) {
            String q = mFilter == null ? "" : mFilter.getText().toString().trim().toLowerCase();
            for (File f : files) {
                if (!f.isFile() || !Formats.isPlayable(f.getName())) continue;
                if (!q.isEmpty() && !f.getName().toLowerCase().contains(q)) continue;
                found.add(f);
            }
        }
        File[] arr = found.toArray(new File[0]);
        if (mSort == 0) Arrays.sort(arr, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        else if (mSort == 1) Arrays.sort(arr, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        else Arrays.sort(arr, (a, b) -> Long.compare(b.length(), a.length()));
        mQueue.addAll(Arrays.asList(arr));

        if (mQueue.isEmpty()) {
            // the state lives inside the list container, so the page structure is
            // the same whether or not there is anything to play
            mList.addView(emptyCard("Nothing playable here.\nSupported: "
                    + Formats.playbackExtensions() + ".", null, null));
            return;
        }

        LinearLayout head = Ui.column(act);
        head.addView(section(mQueue.size() + (mQueue.size() == 1 ? " FILE" : " FILES")
                + "  \u00b7  sorted by " + sortName()));
        // subfolders first, so a folder tree stays navigable
        if (files != null) {
            for (File f : files) {
                if (!f.isDirectory()) continue;
                LinearLayout dir = Ui.row(act);
                dir.setBackgroundResource(R.drawable.bg_list_row);
                dir.setPadding(Ui.dp(act, 10), Ui.dp(act, 8), Ui.dp(act, 10), Ui.dp(act, 8));
                TextView name = Ui.body(act, "\u25b8  " + f.getName() + "/");
                name.setTextColor(th.accent);
                dir.addView(name, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                dir.setOnClickListener(v -> {
                    mFolder = f;
                    rebuild();
                });
                head.addView(dir);
            }
        }
        mList.addView(head);

        for (int i = 0; i < mQueue.size(); i++) {
            final int index = i;
            final File f = mQueue.get(i);
            LinearLayout row = Ui.row(act);
            row.setBackgroundResource(index == mIndex ? R.drawable.bg_selected : R.drawable.bg_list_row);
            row.setPadding(Ui.dp(act, 10), Ui.dp(act, 10), Ui.dp(act, 10), Ui.dp(act, 10));

            String[] facts = facts(f);
            TextView num = Ui.caption(act, String.format("%02d", i + 1));
            num.setTextColor(index == mIndex ? th.accent : th.textTertiary);
            num.setPadding(0, 0, Ui.dp(act, 10), 0);
            row.addView(num);

            LinearLayout texts = Ui.column(act);
            texts.addView(Ui.body(act, f.getName()));
            texts.addView(Ui.caption(act, facts[0] + "  \u00b7  " + Fmt.size(f.length())
                    + "  \u00b7  " + Fmt.stampShort(f.lastModified())));
            row.addView(texts, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            row.addView(Ui.button(act, "\u25b6", R.style.Btn_Icon, v -> playIndex(index)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(act, 4);
            row.setLayoutParams(lp);
            mList.addView(row);
        }

        LinearLayout tail = Ui.column(act);
        tail.addView(Ui.spacer(act, 6));
        tail.addView(Ui.caption(act, "Total " + Fmt.size(totalBytes()) + "  \u00b7  playback uses "
                + "the first two outputs of the selected device."));
        mList.addView(tail);
    }

    private long totalBytes() {
        long n = 0;
        for (File f : mQueue) n += f.length();
        return n;
    }

    /** "duration  rate  channels/bit" as one cached string pair */
    private String[] facts(File f) {
        int[] c = mProbe.get(f.getAbsolutePath());
        if (c == null) {
            FormatProbe.Info info = FormatProbe.probe(f);
            c = new int[]{info.durationMs > Integer.MAX_VALUE ? Integer.MAX_VALUE
                    : (int) info.durationMs, info.sampleRate, info.channels, info.bitDepth, 0};
            mProbe.put(f.getAbsolutePath(), c);
        }
        if (c[0] <= 0) return new String[]{"unreadable header"};
        return new String[]{Fmt.clock(c[0]), Fmt.khz(c[1]) + " " + Formats.displayName(ext(f))
                + " " + c[3] + "-bit " + Fmt.ch(c[2])};
    }

    private String ext(File f) {
        String n = f.getName();
        int d = n.lastIndexOf('.');
        return d < 0 ? "" : n.substring(d + 1).toLowerCase();
    }

    // -------------------------------------------------------------- transport
    private void togglePlay() {
        PlaybackEngine p = act.engine().playback();
        if (p.state() == PlaybackEngine.State.PLAYING) {
            p.pause();
        } else if (p.state() == PlaybackEngine.State.PAUSED) {
            p.resume();
        } else if (mIndex >= 0 && mIndex < mQueue.size()) {
            playIndex(mIndex);
        } else if (!mQueue.isEmpty()) {
            playIndex(0);
        }
        updateNow();
    }

    private void step(int delta) {
        if (mQueue.isEmpty()) return;
        int next = mIndex < 0 ? 0 : mIndex + delta;
        if (next < 0) next = mQueue.size() - 1;
        if (next >= mQueue.size()) next = 0;
        playIndex(next);
    }

    private void playIndex(int index) {
        if (index < 0 || index >= mQueue.size()) return;
        mIndex = index;
        File f = mQueue.get(index);
        act.engine().play(f, f.getName());
        rebuild();
        updateNow();
    }

    private void updateNow() {
        PlaybackEngine p = act.engine().playback();
        switch (p.state()) {
            case PLAYING:
                mNow.setText("\u25b6  " + (mIndex + 1) + "/" + mQueue.size() + "  " + p.title());
                mNow.setTextColor(th.accent);
                break;
            case PAUSED:
                mNow.setText("II  " + p.title() + "   \u00b7   "
                        + (mIndex + 1) + "/" + mQueue.size());
                mNow.setTextColor(th.warn);
                break;
            default:
                mNow.setText("Nothing playing");
                mNow.setTextColor(th.textSecondary);
                break;
        }
    }

    private void addAllToLibrary() {
        java.util.Set<String> known = new java.util.HashSet<>();
        for (Track t : act.store().allTracks(0)) known.add(t.filePath);
        int added = 0;
        for (File f : mQueue) {
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
            t.notes = "Added from the folder playlist";
            act.store().insert(t);
            added++;
        }
        toast(added == 0 ? "Already in the library" : added + " added to the library");
        if (added > 0) act.refreshTopbar();
    }

    // -------------------------------------------------------------- listeners
    @Override
    public void onResume() {
        act.engine().addListener(this);
        updateNow();
    }

    @Override
    public void onPause() {
        act.engine().removeListener(this);
    }

    @Override
    public void onPlaybackState(PlaybackEngine.State s, String title) {
        act.runOnUiThread(() -> {
            updateNow();
            boolean wasPlaying = mWasPlaying;
            mWasPlaying = s == PlaybackEngine.State.PLAYING;
            if (s == PlaybackEngine.State.STOPPED && wasPlaying && mAutoAdvance
                    && !mQueue.isEmpty()) {
                int next = mIndex + 1;
                if (next < mQueue.size()) playIndex(next);
            }
        });
    }

    @Override
    public void onPlaybackPosition(long positionMs, long durationMs) {
        act.runOnUiThread(() -> {
            if (!mScrubbing && durationMs > 0) {
                mScrub.setProgress((int) (1000L * positionMs / durationMs));
            }
        });
    }

    @Override
    public void onPlaybackLevels(float[] rmsDb, float[] peakDb, int channels) {
        // no meters on this page - the mixer and library own those
    }
}
