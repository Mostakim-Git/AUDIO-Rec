package com.mostakim.audiorec.db;

import android.content.Context;

import com.mostakim.audiorec.db.Models.Export;
import com.mostakim.audiorec.db.Models.Preset;
import com.mostakim.audiorec.db.Models.Session;
import com.mostakim.audiorec.db.Models.Track;
import com.mostakim.audiorec.util.Formats;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory Store with canned rows: enough for every screen to build a
 * populated page instead of an empty state.
 */
public class Store {

    private final Context mContext;
    private final List<Session> mSessions = new ArrayList<>();
    private final List<Track> mTracks = new ArrayList<>();
    private final List<Preset> mPresets = new ArrayList<>();
    private final List<Export> mExports = new ArrayList<>();

    public Store(Context c, Db db) {
        mContext = c;
        long now = System.currentTimeMillis();

        Session s1 = new Session();
        s1.id = 1;
        s1.name = "Studio A - album takes";
        s1.artist = "Mostakim Billah";
        s1.venue = "Home studio";
        s1.status = "open";
        s1.sampleRate = 96000;
        s1.bitDepth = 24;
        s1.channels = 2;
        s1.container = Formats.WAV;
        s1.createdAt = now - 86400000L * 6;
        mSessions.add(s1);

        Session s2 = new Session();
        s2.id = 2;
        s2.name = "Podcast episode 12";
        s2.artist = "Mostakim Billah";
        s2.venue = "Desk";
        s2.status = "archived";
        s2.sampleRate = 48000;
        s2.bitDepth = 24;
        s2.channels = 1;
        s2.container = Formats.FLAC;
        s2.createdAt = now - 86400000L * 20;
        mSessions.add(s2);

        File dir = com.mostakim.audiorec.App.defaultRecordDir(c);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();

        String[][] takes = {
                {"Take 01 - rhythm guitar", Formats.WAV, "24000", "-1.5", "open"},
                {"Take 02 - rhythm guitar (double)", Formats.WAV, "24000", "-3.0", "open"},
                {"Take 03 - lead vocal", Formats.FLAC, "60000", "-0.2", "open"},
                {"Take 04 - room mic", Formats.OGG, "45000", "-6.4", "archived"},
        };
        for (int i = 0; i < takes.length; i++) {
            Track t = new Track();
            t.id = i + 1;
            t.sessionId = i < 3 ? 1 : 2;
            t.title = takes[i][0];
            t.container = takes[i][1];
            t.sampleRate = i == 3 ? 48000 : 96000;
            t.bitDepth = i == 3 ? 16 : 24;
            t.channels = i == 3 ? 1 : 2;
            t.durationMs = Long.parseLong(takes[i][2]);
            t.sizeBytes = t.durationMs * (t.bitDepth / 8) * t.channels * t.sampleRate / 1000;
            t.peakDb = Float.parseFloat(takes[i][3]);
            t.rmsDb = t.peakDb - 12f;
            t.deviceName = "M-Audio Fast Track Pro";
            t.channelMap = t.channels == 1 ? "1" : "1,2";
            t.takeNo = i + 1;
            t.createdAt = now - 86400000L * (5 - i);
            t.filePath = new File(dir, t.title.replaceAll("[^A-Za-z0-9]+", "-") + "."
                    + Formats.ext(t.container)).getAbsolutePath();
            mTracks.add(t);
        }

        String[][] presets = {
                {"Fast Track Pro 96k", "M-Audio Fast Track Pro", "96000", "24"},
                {"Babyface 192k", "RME Babyface", "192000", "32"},
                {"Yeti podcast mono", "Blue Yeti", "48000", "24"},
        };
        for (int i = 0; i < presets.length; i++) {
            Preset p = new Preset();
            p.id = i + 1;
            p.name = presets[i][0];
            p.deviceName = presets[i][1];
            p.vendorId = 0x1000 + i;
            p.productId = 0x2000 + i;
            p.sampleRate = Integer.parseInt(presets[i][2]);
            p.bitDepth = Integer.parseInt(presets[i][3]);
            p.channels = 2;
            p.bufferFrames = 2048;
            p.container = Formats.WAV;
            p.builtin = i == 0;
            p.useCount = 3 - i;
            mPresets.add(p);
        }

        for (int i = 0; i < 2; i++) {
            Export e = new Export();
            e.id = i + 1;
            e.trackId = i + 1;
            e.trackTitle = takes[i][0];
            e.name = "Take 0" + (i + 1) + " (16bit-48k).wav";
            e.filePath = new File(dir, e.name).getAbsolutePath();
            e.container = Formats.WAV;
            e.sampleRate = 48000;
            e.bitDepth = 16;
            e.channels = 2;
            e.sizeBytes = 8_000_000L + i * 1_000_000L;
            e.durationMs = 24000;
            e.status = "ready";
            mExports.add(e);
        }
    }

    public int restoreBuiltinPresets() { return 0; }

    public int[] tableCounts() {
        return new int[]{mSessions.size(), mTracks.size(), mPresets.size(), mExports.size()};
    }

    public List<Session> sessions(String orderBy) { return new ArrayList<>(mSessions); }

    public Session session(long id) {
        for (Session s : mSessions) if (s.id == id) return s;
        return null;
    }

    public long insert(Session s) {
        s.id = mSessions.size() + 1;
        mSessions.add(s);
        return s.id;
    }

    public int update(Session s) { return 1; }

    public int delete(long id, boolean deleteFiles) {
        Session s = session(id);
        return s != null && mSessions.remove(s) ? 1 : 0;
    }

    public void refreshSessionStats(long sessionId) { }

    public List<Track> tracks(long sessionId) {
        List<Track> out = new ArrayList<>();
        for (Track t : mTracks) if (t.sessionId == sessionId) out.add(t);
        return out;
    }

    public List<Track> allTracks(int limit) {
        List<Track> out = new ArrayList<>();
        for (Track t : mTracks) {
            if (limit > 0 && out.size() >= limit) break;
            out.add(t);
        }
        return out;
    }

    public List<Track> searchTracks(String q) {
        if (q == null || q.trim().isEmpty()) return allTracks(0);
        String needle = q.toLowerCase();
        List<Track> out = new ArrayList<>();
        for (Track t : mTracks) {
            if (t.title.toLowerCase().contains(needle)) out.add(t);
        }
        return out;
    }

    public boolean isKnownFile(String absolute, String canonical) { return true; }

    public Track track(long id) {
        for (Track t : mTracks) if (t.id == id) return t;
        return null;
    }

    public long insert(Track t) {
        t.id = mTracks.size() + 1;
        mTracks.add(t);
        return t.id;
    }

    public int update(Track t) { return 1; }

    public int rename(Track t, String title) {
        t.title = title;
        return 1;
    }

    public int setStarred(long trackId, boolean starred) {
        Track t = track(trackId);
        if (t == null) return 0;
        t.starred = starred;
        return 1;
    }

    public int moveToSession(long trackId, long sessionId) {
        Track t = track(trackId);
        if (t == null) return 0;
        t.sessionId = sessionId;
        return 1;
    }

    public int delete(Track t, boolean deleteFile) { return mTracks.remove(t) ? 1 : 0; }

    public int nextTakeNo(long sessionId) { return tracks(sessionId).size() + 1; }

    public int trackCount() { return mTracks.size(); }

    public List<Preset> presets() { return new ArrayList<>(mPresets); }

    public List<Preset> presetsFor(String deviceName, int vid, int pid) {
        return new ArrayList<>(mPresets);
    }

    public Preset preset(long id) {
        for (Preset p : mPresets) if (p.id == id) return p;
        return null;
    }

    public long insert(Preset p) {
        p.id = mPresets.size() + 1;
        mPresets.add(p);
        return p.id;
    }

    public int update(Preset p) { return 1; }

    public int delete(long id) { return 1; }

    public int bumpUse(long id) { return 1; }

    public List<Export> exports() { return new ArrayList<>(mExports); }

    public List<Export> exportsForTrack(long trackId) {
        List<Export> out = new ArrayList<>();
        for (Export e : mExports) if (e.trackId == trackId) out.add(e);
        return out;
    }

    public long insert(Export e) {
        e.id = mExports.size() + 1;
        mExports.add(e);
        return e.id;
    }

    public int update(Export e) { return 1; }

    public int delete(Export e, boolean deleteFile) { return mExports.remove(e) ? 1 : 0; }

    public long[] libraryTotals() {
        long bytes = 0, ms = 0;
        for (Track t : mTracks) {
            bytes += t.sizeBytes;
            ms += t.durationMs;
        }
        return new long[]{mTracks.size(), bytes, ms};
    }

    public int exportCount() { return mExports.size(); }

    public int presetCount() { return mPresets.size(); }

    public int sessionCount() { return mSessions.size(); }

    public int[] tracksPerDay(int days) {
        int[] out = new int[days];
        out[days - 1] = 2;
        out[days - 3] = 1;
        out[days / 2] = 1;
        return out;
    }
}
