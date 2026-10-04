package com.mostakim.audiorec.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mostakim.audiorec.App;
import com.mostakim.audiorec.R;
import com.mostakim.audiorec.audio.AudioDevice;
import com.mostakim.audiorec.audio.FormatProbe;
import com.mostakim.audiorec.db.Models.Export;
import com.mostakim.audiorec.db.Models.Preset;
import com.mostakim.audiorec.db.Models.Session;
import com.mostakim.audiorec.db.Models.Track;
import com.mostakim.audiorec.db.Store;
import com.mostakim.audiorec.share.ExportProvider;
import com.mostakim.audiorec.ui.kit.Theme;
import com.mostakim.audiorec.ui.kit.Ui;
import com.mostakim.audiorec.util.Fmt;
import com.mostakim.audiorec.util.Formats;

import java.io.File;
import java.util.List;

/** Shared create/edit/share flows for the four core resources. */
public final class Dialogs {

    private Dialogs() {
    }

    public interface Done {
        void run();
    }

    // ============================================================== SESSIONS =
    public static void sessionEditor(MainActivity a, Store store, Session existing, Done after) {
        Theme th = a.theme();
        boolean isNew = existing == null;
        Session s = isNew ? new Session() : existing;

        LinearLayout col = Ui.column(a);
        int pad = Ui.dp(a, 20);
        col.setPadding(pad, Ui.dp(a, 12), pad, 0);
        ScrollView scroll = new ScrollView(a);
        scroll.addView(col);

        EditText name = Ui.textInput(a, "Session name", s.name);
        EditText artist = Ui.textInput(a, "Artist / client", s.artist);
        EditText venue = Ui.textInput(a, "Venue / room", s.venue);
        EditText notes = Ui.area(a, "Notes, mic positions, anything you'll forget", s.notes);

        col.addView(Ui.caption(a, "NAME"));
        col.addView(name);
        col.addView(Ui.spacer(a, 10));
        col.addView(Ui.caption(a, "ARTIST"));
        col.addView(artist);
        col.addView(Ui.spacer(a, 10));
        col.addView(Ui.caption(a, "VENUE"));
        col.addView(venue);
        col.addView(Ui.spacer(a, 10));
        col.addView(Ui.caption(a, "NOTES"));
        col.addView(notes);
        col.addView(Ui.spacer(a, 14));

        // default format carried by the session
        final int[] rate = {s.sampleRate};
        final int[] depth = {s.bitDepth};
        final int[] channels = {s.channels};
        final String[] container = {s.container};

        List<Integer> rates = new java.util.ArrayList<>();
        for (int r : Formats.ALL_RATES) rates.add(r);
        int[] rateArr = new int[rates.size()];
        for (int i = 0; i < rateArr.length; i++) rateArr[i] = rates.get(i);

        addSpinner(a, col, "Default sample rate", labelsRates(rateArr),
                Math.max(0, Formats.indexOf(rateArr, s.sampleRate)), idx -> rate[0] = rateArr[idx]);
        addSpinner(a, col, "Default bit depth", new String[]{"16-bit", "24-bit", "32-bit float"},
                s.bitDepth == 16 ? 0 : (s.bitDepth == 24 ? 1 : 2), idx -> depth[0] =
                        idx == 0 ? 16 : (idx == 1 ? 24 : 32));
        addSpinner(a, col, "Default channels",
                new String[]{"1 (mono)", "2 (stereo)", "4", "6", "8"},
                s.channels <= 1 ? 0 : (s.channels == 2 ? 1 : (s.channels <= 4 ? 2 : 3)),
                idx -> channels[0] = new int[]{1, 2, 4, 6, 8}[idx]);
        addSpinner(a, col, "Default container",
                new String[]{"WAV", "FLAC", "AIFF", "OGG (Opus)"},
                Math.max(0, Formats.CONTAINERS.indexOf(s.container)),
                idx -> container[0] = Formats.CONTAINERS.get(idx));

        if (!isNew) {
            col.addView(Ui.spacer(a, 14));
            col.addView(Ui.caption(a, "STATUS"));
            final String[] status = {s.status};
            addSpinner(a, col, null, new String[]{"Open", "Archived"},
                    "archived".equals(s.status) ? 1 : 0,
                    idx -> status[0] = idx == 0 ? "open" : "archived");
        }

        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle(isNew ? "New session" : "Edit session")
                .setView(scroll)
                .setPositiveButton(isNew ? "Create" : "Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                Ui.toast(a, "Give the session a name");
                return;
            }
            s.name = n;
            s.artist = artist.getText().toString().trim();
            s.venue = venue.getText().toString().trim();
            s.notes = notes.getText().toString().trim();
            s.sampleRate = rate[0];
            s.bitDepth = depth[0];
            s.channels = channels[0];
            s.container = container[0];
            if (isNew) {
                store.insert(s);
                App.get().prefs().setLastSessionId(s.id);
            } else {
                store.update(s);
            }
            d.dismiss();
            if (after != null) after.run();
        });
    }

    // ================================================================ TRACKS =
    public static void renameTrack(MainActivity a, Store store, Track t, Done after) {
        Ui.prompt(a, "Rename take", "Take name", t.title, false, false, value -> {
            if (value.isEmpty()) return;
            store.rename(t, value);
            t.title = value;
            if (after != null) after.run();
            a.toast("Renamed to \"" + value + "\"");
        });
    }

    public static void trackDetails(MainActivity a, Store store, Track t, Done onChange) {
        Theme th = a.theme();
        File f = new File(t.filePath);
        FormatProbe.Info info = f.exists() ? FormatProbe.probe(f) : new FormatProbe.Info();

        LinearLayout col = Ui.column(a);
        int pad = Ui.dp(a, 20);
        col.setPadding(pad, Ui.dp(a, 12), pad, 0);
        ScrollView scroll = new ScrollView(a);
        scroll.addView(col);

        col.addView(Ui.head(a, t.title));
        col.addView(Ui.caption(a, t.formatSummary()));
        col.addView(Ui.spacer(a, 12));
        addRow(a, col, "File", f.getName());
        addRow(a, col, "Folder", f.getParent());
        addRow(a, col, "Size", Fmt.size(t.sizeBytes));
        addRow(a, col, "Duration", Fmt.clock(t.durationMs));
        addRow(a, col, "Peak", Fmt.db(t.peakDb));
        addRow(a, col, "RMS", Fmt.db(t.rmsDb));
        addRow(a, col, "Device", t.deviceName);
        if (!t.channelMap.isEmpty()) addRow(a, col, "Routing", t.channelMap);
        addRow(a, col, "Take", "#" + t.takeNo);
        addRow(a, col, "Recorded", Fmt.stamp(t.createdAt));
        if (info.valid()) {
            col.addView(Ui.spacer(a, 10));
            col.addView(Ui.text(a, "Verified from file header", R.style.T_Section));
            addRow(a, col, "Header", info.describe());
            addRow(a, col, "Frames", String.valueOf(info.frames));
        }
        if (!f.exists()) {
            TextView warn = Ui.text(a, "The audio file is missing from disk.", R.style.T_Body);
            warn.setTextColor(th.rec);
            col.addView(warn);
        }

        AlertDialog d = new AlertDialog.Builder(a)
                .setView(scroll)
                .setPositiveButton("Close", null)
                .setNeutralButton("Rename", (dl, w) -> renameTrack(a, store, t, onChange))
                .create();
        d.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
        d.show();
    }

    public static void deleteTrack(MainActivity a, Store store, Track t, Done after) {
        Ui.confirm(a, "Delete take?",
                "\"" + t.title + "\" and its " + Fmt.size(t.sizeBytes)
                        + " file will be removed from this device. This cannot be undone.",
                "Delete", () -> {
                    store.delete(t, true);
                    if (after != null) after.run();
                    a.toast("Deleted " + t.title);
                });
    }

    /** move a take into another session */
    public static void moveTrack(MainActivity a, Store store, Track t, Done after) {
        List<Session> sessions = store.sessions(null);
        final String[] names = new String[sessions.size()];
        for (int i = 0; i < sessions.size(); i++) names[i] = sessions.get(i).name;
        new AlertDialog.Builder(a)
                .setTitle("Move to session")
                .setItems(names, (d, which) -> {
                    Session s = sessions.get(which);
                    store.moveToSession(t.id, s.id);
                    if (after != null) after.run();
                    a.toast("Moved to " + s.name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ============================================================== EXPORTS =
    public interface ExportCallback {
        void onExport(String container, int depth, int rate);
    }

    public static void exportTrack(MainActivity a, Track t, ExportCallback cb) {
        LinearLayout col = Ui.column(a);
        int pad = Ui.dp(a, 20);
        col.setPadding(pad, Ui.dp(a, 12), pad, 0);
        col.addView(Ui.caption(a, "Render \"" + t.title + "\" to a new file. The take itself is "
                + "never modified."));
        col.addView(Ui.spacer(a, 12));

        final String[] container = {t.container};
        final int[] depth = {t.bitDepth};
        final int[] rate = {t.sampleRate};

        addSpinner(a, col, "Format", new String[]{"WAV", "FLAC", "AIFF", "OGG (Opus)"},
                Math.max(0, Formats.CONTAINERS.indexOf(t.container)),
                idx -> container[0] = Formats.CONTAINERS.get(idx));

        List<Integer> rates = new java.util.ArrayList<>();
        for (int r : Formats.ALL_RATES) {
            if (r <= Math.max(48000, t.sampleRate)) rates.add(r);
        }
        int[] rateArr = new int[rates.size()];
        for (int i = 0; i < rateArr.length; i++) rateArr[i] = rates.get(i);
        addSpinner(a, col, "Sample rate (resampled if different)",
                labelsRates(rateArr), Math.max(0, Formats.indexOf(rateArr, t.sampleRate)),
                idx -> rate[0] = rateArr[idx]);

        addSpinner(a, col, "Bit depth", new String[]{"16-bit", "24-bit", "32-bit float"},
                t.bitDepth == 16 ? 0 : (t.bitDepth == 24 ? 1 : 2),
                idx -> depth[0] = idx == 0 ? 16 : (idx == 1 ? 24 : 32));

        new AlertDialog.Builder(a)
                .setTitle("Export take")
                .setView(col)
                .setPositiveButton("Export", (d, w) -> cb.onExport(container[0], depth[0], rate[0]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ============================================================== PRESETS =
    public static void presetEditor(MainActivity a, Store store, Preset existing, Done after) {
        boolean isNew = existing == null;
        Preset p = isNew ? new Preset() : existing;
        AudioDevice in = a.engine().input();
        if (isNew && in != null) {
            p.deviceName = in.name;
            p.vendorId = in.vendorId;
            p.productId = in.productId;
            p.sampleRate = App.get().prefs().sampleRate();
            p.bitDepth = App.get().prefs().bitDepth();
            p.channels = App.get().prefs().channels();
            p.bufferFrames = App.get().prefs().bufferFrames();
            p.container = App.get().prefs().container();
            p.gainDb = App.get().prefs().gainDb();
            p.monitorGainDb = App.get().prefs().monitorGainDb();
        }

        LinearLayout col = Ui.column(a);
        int pad = Ui.dp(a, 20);
        col.setPadding(pad, Ui.dp(a, 12), pad, 0);
        ScrollView scroll = new ScrollView(a);
        scroll.addView(col);

        EditText name = Ui.textInput(a, "Preset name", p.name);
        EditText device = Ui.textInput(a, "Device", p.deviceName);
        EditText notes = Ui.area(a, "Notes", p.notes);
        col.addView(Ui.caption(a, "NAME"));
        col.addView(name);
        col.addView(Ui.spacer(a, 10));
        col.addView(Ui.caption(a, "DEVICE"));
        col.addView(device);
        col.addView(Ui.spacer(a, 10));
        col.addView(Ui.caption(a, "NOTES"));
        col.addView(notes);
        col.addView(Ui.spacer(a, 12));

        final int[] rate = {p.sampleRate};
        final int[] depth = {p.bitDepth};
        final int[] channels = {p.channels};
        final int[] buffer = {p.bufferFrames};
        final String[] container = {p.container};
        final float[] gain = {p.gainDb};
        final float[] mon = {p.monitorGainDb};
        final boolean[] monitor = {p.monitor};

        int[] rateArr = a.engine().availableRates();
        if (Formats.indexOf(rateArr, p.sampleRate) < 0) {
            int[] bigger = new int[rateArr.length + 1];
            System.arraycopy(rateArr, 0, bigger, 0, rateArr.length);
            bigger[rateArr.length] = p.sampleRate;
            java.util.Arrays.sort(bigger);
            rateArr = bigger;
        }
        final int[] ratePick = rateArr;
        addSpinner(a, col, "Sample rate", labelsRates(ratePick),
                Math.max(0, Formats.indexOf(ratePick, p.sampleRate)), idx -> rate[0] = ratePick[idx]);
        addSpinner(a, col, "Bit depth", new String[]{"16-bit", "24-bit", "32-bit float"},
                p.bitDepth == 16 ? 0 : (p.bitDepth == 24 ? 1 : 2),
                idx -> depth[0] = idx == 0 ? 16 : (idx == 1 ? 24 : 32));
        addSpinner(a, col, "Channels", new String[]{"1", "2", "4", "6", "8"},
                p.channels <= 1 ? 0 : (p.channels == 2 ? 1 : (p.channels <= 4 ? 2 : 3)),
                idx -> channels[0] = new int[]{1, 2, 4, 6, 8}[idx]);
        addSpinner(a, col, "Buffer size", labelsBuffers(Formats.BUFFER_SIZES),
                Math.max(0, Formats.indexOf(Formats.BUFFER_SIZES, p.bufferFrames)),
                idx -> buffer[0] = Formats.BUFFER_SIZES[idx]);
        addSpinner(a, col, "Container", new String[]{"WAV", "FLAC", "AIFF", "OGG (Opus)"},
                Math.max(0, Formats.CONTAINERS.indexOf(p.container)),
                idx -> container[0] = Formats.CONTAINERS.get(idx));

        addSliderRow(a, col, "Input gain (dB)", -24, 24, (int) p.gainDb,
                v -> gain[0] = v);
        addSliderRow(a, col, "Monitor level (dB)", -40, 6, (int) p.monitorGainDb,
                v -> mon[0] = v);

        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle(isNew ? "New preset" : "Edit preset")
                .setView(scroll)
                .setPositiveButton(isNew ? "Create" : "Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                Ui.toast(a, "Give the preset a name");
                return;
            }
            p.name = n;
            p.deviceName = device.getText().toString().trim();
            p.notes = notes.getText().toString().trim();
            p.sampleRate = rate[0];
            p.bitDepth = depth[0];
            p.channels = channels[0];
            p.bufferFrames = buffer[0];
            p.container = container[0];
            p.gainDb = gain[0];
            p.monitorGainDb = mon[0];
            p.monitor = monitor[0];
            if (isNew) store.insert(p);
            else store.update(p);
            d.dismiss();
            if (after != null) after.run();
        });
    }

    // ================================================================ EXPORT =
    public static void shareFile(MainActivity a, File f, String mime) {
        if (f == null || !f.exists()) {
            a.toast("File is missing");
            return;
        }
        try {
            Uri uri = ExportProvider.uriFor(f);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(mime == null ? "audio/*" : mime);
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.putExtra(Intent.EXTRA_SUBJECT, f.getName());
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(i, "Share " + f.getName()));
        } catch (Exception e) {
            a.toast("Nothing on this device can receive the file");
        }
    }

    // ---------------------------------------------------------------- parts -
    public interface IntChoice {
        void onChoice(int index);
    }

    public static void addSpinner(MainActivity a, LinearLayout col, String caption,
                                  String[] items, int selected, final IntChoice cb) {
        if (caption != null) col.addView(Ui.caption(a, caption));
        android.widget.Spinner sp = new android.widget.Spinner(a);
        android.widget.ArrayAdapter<String> ad = new android.widget.ArrayAdapter<>(
                a, android.R.layout.simple_spinner_dropdown_item, items);
        sp.setAdapter(ad);
        sp.setSelection(Math.max(0, Math.min(items.length - 1, selected)));
        sp.setBackgroundResource(R.drawable.bg_input);
        sp.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6));
        sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                cb.onChoice(position);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 46));
        lp.topMargin = Ui.dp(a, 4);
        lp.bottomMargin = Ui.dp(a, 6);
        col.addView(sp, lp);
    }

    public interface FloatChoice {
        void onChoice(float value);
    }

    public static void addSliderRow(MainActivity a, LinearLayout col, String label,
                                    int min, int max, int value, final FloatChoice cb) {
        LinearLayout row = Ui.row(a);
        row.addView(Ui.body(a, label), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView val = Ui.mono(a, value + " dB");
        row.addView(val);
        col.addView(row);
        android.widget.SeekBar sb = new android.widget.SeekBar(a);
        sb.setMax(max - min);
        sb.setProgress(value - min);
        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar s, int progress, boolean fromUser) {
                val.setText((progress + min) + " dB");
                if (fromUser) cb.onChoice(progress + min);
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar s) {
            }
        });
        col.addView(sb);
        col.addView(Ui.spacer(a, 6));
    }

    public static void addRow(MainActivity a, LinearLayout col, String key, String value) {
        LinearLayout r = Ui.row(a);
        TextView k = Ui.caption(a, key);
        k.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 96),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        r.addView(k);
        TextView v = Ui.body(a, value == null || value.isEmpty() ? "\u2014" : value);
        v.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(v);
        r.setPadding(0, Ui.dp(a, 5), 0, Ui.dp(a, 5));
        col.addView(r);
    }

    private static String[] labelsRates(int[] rates) {
        String[] out = new String[rates.length];
        for (int i = 0; i < rates.length; i++) out[i] = Fmt.khz(rates[i]) + "  (" + rates[i] + " Hz)";
        return out;
    }

    private static String[] labelsBuffers(int[] buffers) {
        String[] out = new String[buffers.length];
        for (int i = 0; i < buffers.length; i++) {
            out[i] = buffers[i] + " frames  (~"
                    + Math.round(buffers[i] * 1000.0 / 48000) + " ms @48k)";
        }
        return out;
    }

    /** generic list chooser used by playlist / storage / exports */
    public static void choose(MainActivity a, String title, String[] items, int selected,
                              final IntChoice cb) {
        new AlertDialog.Builder(a)
                .setTitle(title)
                .setSingleChoiceItems(items, selected, (d, which) -> {
                    d.dismiss();
                    cb.onChoice(which);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static TextView headerRow(MainActivity a, String left, String right) {
        LinearLayout r = Ui.row(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = Ui.text(a, left, R.style.T_Section);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView rv = Ui.caption(a, right);
        r.addView(rv);
        return l;
    }
}
