import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mostakim.audiorec.App;
import com.mostakim.audiorec.ui.MainActivity;
import com.mostakim.audiorec.ui.Dialogs;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless UI construction test.
 *
 * This compiles the app's real ui/ sources against stand-in Android classes
 * (tools/test/ui/stubs) and stand-in engines (tools/test/ui/fakes), then runs
 * the real MainActivity.onCreate(), the real sidebar, and every real screen.
 * The view tree is then measured and laid out at phone and tablet sizes, and
 * the result is inspected: a page must contain real, non-empty, non-zero-sized
 * content, or the test fails.
 *
 * Why it exists: the first shipped build opened onto a completely blank window
 * because the shell layout discovered its panes in onFinishInflate(), which the
 * platform only calls for XML inflation - and this app builds every view in
 * code.  No amount of "it compiles and the APK is signed" could catch that; this
 * test can, off-device, in a second.
 */
public class UiSmokeTest {

    private static int checks;
    private static boolean treeMode;
    private static String treeShape = "";
    private static final List<String> failures = new ArrayList<>();

    private static void check(String what, boolean ok) {
        checks++;
        if (!ok) failures.add(what);
    }

    /** same, with something measured to explain a failure */
    private static void check(String what, boolean ok, String detail) {
        checks++;
        if (!ok) failures.add(what + " - " + detail);
    }

    public static void main(String[] args) throws Exception {
        treeMode = args.length > 0 && args[0].equals("--tree");
        for (String a : args) if (a.startsWith("--shape=")) treeShape = a.substring(8);
        App app = new App();
        app.initForHarness();

        MainActivity act = new MainActivity();
        act.simulateCreate(null);

        View root = act.contentView();
        check("onCreate() set a content view", root != null);
        if (!(root instanceof LinearLayout)) {
            check("content view is the tab shell (no drawer, no rail)", false);
            report();
            return;
        }

        // ------------------------------------------------- recording-first shell
        layout(root, 1080, 2340);
        check("the app opens on the recorder, not on a dashboard",
                "Recorder".equals(titleOf(act)));
        check("the header carries the page title", treeContainsText(root, "Recorder"));
        check("the header carries the app name", treeContainsText(root, "AUDIO-rec"));
        check("there is no dashboard page any more",
                !treeContainsText(root, "Dashboard"));

        // "available disk space" was on the list, so the header says it out loud
        // while recording: the figure is the space left on the recording volume.
        String space = null;
        List<View> everyView = new ArrayList<>();
        collect(root, everyView);
        for (View v : everyView) {
            if (!(v instanceof TextView)) continue;
            String t = ((TextView) v).getText().toString();
            if (t.endsWith(" free") || t.startsWith("low space:")) {
                space = t;
                break;
            }
        }
        check("the header shows the space left on the recording volume", space != null);
        check("the free-space figure is a figure, with units",
                space != null && space.matches(".*\\d.*\\s?(B|KB|MB|GB).*"),
                String.valueOf(space));

        // the bottom bar: five tabs, all inside the window, all reachable
        List<View> tabs = tabsOf(root, 1080, 2340);
        check("the bottom bar has five tabs", tabs.size() == 5, tabs.size() + " found");
        String[] want = {"Record", "Mixer", "Library", "Playlist", "More"};
        for (int i = 0; i < want.length && i < tabs.size(); i++) {
            final int which = i;
            check("tab " + want[i] + " is labelled and clickable",
                    treeContainsText(tabs.get(i), want[i])
                            && tabs.get(i).getOnClickListener() != null);
            check("tab " + want[i] + " is inside the window",
                    tabs.get(i).getLeft() >= 0 && tabs.get(i).getRight() <= 1080
                            && tabs.get(i).getWidth() > 0,
                    tabs.get(i).getLeft() + ".." + tabs.get(i).getRight());
        }
        // and it navigates
        if (tabs.size() == 5) {
            tabs.get(1).performClick();
            layout(root, 1080, 2340);
            check("tapping a tab navigates", "Mixer".equals(titleOf(act)));
            tabs.get(3).performClick();
            layout(root, 1080, 2340);
            check("the playlist tab navigates", "Playlist".equals(titleOf(act)));
            tabs.get(4).performClick();
            check("the More tab opens the sheet of the remaining pages",
                    android.app.Dialog.lastShown() instanceof android.app.AlertDialog
                            && ((android.app.AlertDialog) android.app.Dialog.lastShown())
                            .itemsText().contains("Export Files"));
        }

        // -------------------------------------------------------- every screen
        System.out.println();
        System.out.printf("  %-16s %6s %6s %8s  %s%n",
                "page", "views", "words", "size", "state");
        String[][] pages = {
                {"Recorder", "Recorder"}, {"Mixer", "Mixer"},
                {"Devices", "Devices"}, {"Sessions", "Sessions"}, {"Library", "Library"},
                {"Playlist", "Playlist"}, {"Exports", "Exports"},
                {"Presets", "Presets"}, {"Storage", "Storage"},
                {"Settings", "Settings"}, {"About", "About"},
        };
        int[] pageIds = {
                MainActivity.PAGE_RECORDER, MainActivity.PAGE_MIXER,
                MainActivity.PAGE_DEVICES, MainActivity.PAGE_SESSIONS, MainActivity.PAGE_LIBRARY,
                MainActivity.PAGE_PLAYLIST, MainActivity.PAGE_EXPORTS, MainActivity.PAGE_PRESETS,
                MainActivity.PAGE_STORAGE, MainActivity.PAGE_SETTINGS, MainActivity.PAGE_ABOUT,
        };
        check("the tab bar reaches four pages and More holds the other seven",
                MainActivity.tabPages().length + MainActivity.morePages().length == pageIds.length);
        for (int i = 0; i < pageIds.length; i++) {
            final int page = pageIds[i];
            final String name = pages[i][1];
            String state;
            int views = 0;
            int words = 0;
            String size = "";
            try {
                act.navigate(page);
                layout(root, 1080, 2340);
                check(name + ": the header really shows this page",
                        name.equals(titleOf(act)));
                View pageRoot = pageRoot(act);
                check(name + ": a page root was attached to the window", pageRoot != null);
                if (pageRoot != null) {
                    views = countViews(pageRoot);
                    words = countWordyViews(pageRoot);
                    size = pageRoot.getWidth() + "x" + pageRoot.getHeight();
                    List<View> empty = emptyContainers(pageRoot);
                    check(name + ": the page container has children",
                            !(pageRoot instanceof ViewGroup)
                                    || ((ViewGroup) pageRoot).getChildCount() > 0);
                    check(name + ": the page has real text on it", words >= 3);
                    check(name + ": the page fills the window",
                            pageRoot.getWidth() > 0 && pageRoot.getHeight() > 0);
                    for (View v : empty) {
                        check(name + ": empty container at " + pathOf(pageRoot, v), false);
                    }
                    List<View> collapsed = collapsedContent(pageRoot);
                    for (View v : collapsed) {
                        check(name + ": " + describe(v) + " measured to nothing at "
                                + pathOf(pageRoot, v), false);
                    }
                    state = "ok";
                } else {
                    state = "NO ROOT";
                }
            } catch (Throwable t) {
                state = "THREW " + t;
                check(name + ": building the page threw " + t, false);
            }
            System.out.printf("  %-16s %6d %6d %8s  %s%n", name, views, words, size, state);
            if (treeMode) {
                View pr = pageRoot(act);
                if (pr != null) dump(pr, 0);
            }
        }

        // ------------------------------------------------ every screen shape
        // "it feels like a website with not proper alignment": a page that fits a
        // 20:9 phone in portrait still has to fit a 16:9 one in landscape and a
        // tablet, with nothing hanging off the edge and nothing squeezed to nothing.
        System.out.println();
        System.out.println("  every screen shape");
        int[][] shapes = {
                {720, 1280}, {1080, 1920}, {1080, 2340}, {1440, 3120},
                {800, 1280}, {1200, 1920},
                {1280, 720}, {2340, 1080}, {1920, 1200},
        };
        for (int[] shape : shapes) {
            int w = shape[0], h = shape[1];
            String label = w + "x" + h;
            int overflow = 0, squeezed = 0, chromeOff = 0;
            String firstProblem = "";
            StringBuilder details = new StringBuilder();
            for (int page : pageIds) {
                try {
                    act.navigate(page);
                    layout(root, w, h);
                    View pr = pageRoot(act);
                    if (pr == null) {
                        overflow++;
                        firstProblem = label + " page " + page + " has no root";
                        continue;
                    }
                    for (View v : overflowViews(pr, w)) {
                        overflow++;
                        if (firstProblem.isEmpty()) {
                            firstProblem = label + " " + describe(v) + " at " + pathOf(pr, v)
                                    + " runs to " + v.getRight() + " (window " + w + ")";
                        }
                    }
                    for (View v : zeroSized(pr)) {
                        squeezed++;
                        String what = v instanceof TextView
                                ? "\"" + ((TextView) v).getText() + "\""
                                : v.getClass().getSimpleName();
                        if (treeMode && (treeShape.isEmpty() || treeShape.equals(label))) {
                            System.out.println("        -- page " + page + " tree --");
                            if (pr != null) dump(pr, 2);
                        }
                        if (details.length() < 600) {
                            details.append("\n        ").append(pathOf(pr, v))
                                    .append(" " + v.getWidth() + "x" + v.getHeight());
                        }
                        if (firstProblem.isEmpty() || squeezed <= 3) {
                            firstProblem = label + " " + what + " at " + pathOf(pr, v)
                                    + " has no room to draw";
                        }
                    }
                    List<View> bar = tabsOf(root, w, h);
                    if (bar.size() != 5) {
                        chromeOff++;
                        firstProblem = label + ": " + bar.size() + " tabs found";
                    } else {
                        for (View tab : bar) {
                            if (tab.getRight() > w || tab.getWidth() <= 0
                                    || tab.getBottom() > h) {
                                chromeOff++;
                                if (firstProblem.isEmpty()) {
                                    firstProblem = label + " tab " + ((TextView) tab.getTag())
                                            .getText() + " at " + tab.getLeft() + ".."
                                            + tab.getRight() + " (window " + w + ")";
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    overflow++;
                    firstProblem = label + " page " + page + " threw " + t;
                }
            }
            check("shape " + label + ": nothing hangs off the side",
                    overflow == 0, firstProblem);
            check("shape " + label + ": every label has room to draw",
                    squeezed == 0, firstProblem + details);
            check("shape " + label + ": the tab bar fits", chromeOff == 0, firstProblem);
        }
        layout(root, 1080, 2340);
        act.navigate(MainActivity.PAGE_RECORDER);

        // ------------------------------------------------------- click handlers
        int clicked = 0;
        int clickFailures = 0;
        for (int page : pageIds) {
            try {
                act.navigate(page);
                layout(root, 1080, 2340);
                for (View v : clickables(pageRoot(act))) {
                    try {
                        v.performClick();
                        clicked++;
                        layout(root, 1080, 2340);
                    } catch (Throwable t) {
                        clickFailures++;
                        check("clicking a control on page " + page + " threw " + t, false);
                    }
                }
            } catch (Throwable t) {
                clickFailures++;
                check("opening page " + page + " for the click pass threw " + t, false);
            }
        }
        check("click handlers ran", clicked > 10);
        check("no click handler threw", clickFailures == 0);

        // ------------------------------------------------- the feature checklist
        // Every item on the operator's list, checked where the operator meets it:
        // first that the control is on the page, then that it really opens the
        // chooser it promises.
        System.out.println();
        System.out.println("  feature checklist");
        // the click pass above starts a take; put the engine back to idle so the
        // Recorder page shows what the operator sees when they open the app
        act.engine().stopCapture(false);
        layout(root, 1080, 2340);
        String[][] visible = {
                {"Recorder", "Arm & record", "arm and record"},
                {"Recorder", "Monitor:", "monitor button for setting levels"},
                {"Recorder", "Rate", "sample-rate picker"},
                {"Recorder", "Depth", "bit-depth picker"},
                {"Recorder", "Channels", "channel picker"},
                {"Recorder", "Buffer", "buffer-size picker"},
                {"Recorder", "Format", "container picker"},
                {"Recorder", "INPUT", "recording level meters"},
                {"Recorder", "OUTPUT", "playback level meters"},
                {"Recorder", "peak hold", "peak-hold explanation"},
                {"Recorder", "Change folder", "recording folder"},
                {"Devices", "INPUT", "input device list"},
                {"Devices", "OUTPUT", "output device list"},
                {"Mixer", "CHANNEL TRIM", "internal gain per channel"},
                {"Mixer", "MUTE", "mute"},
                {"Library", "Pick audio file", "load wav/aiff/flac/ogg for playback"},
                {"Library", "Scan folder", "import what is already in the folder"},
                {"Playlist", "Folder", "directory playlist"},
                {"Playlist", "Auto-advance", "playlist transport"},
                {"Storage", "used", "available disk space"},
                {"Storage", "Record here", "choose the recording folder"},
                {"Storage", "volumes", "external volumes"},
                {"Exports", "Share", "share a rendered copy"},
                {"Settings", "Input", "input selection"},
                {"Settings", "Output", "output selection"},
                {"About", "Mostakim Billah", "the author"},
        };
        int[] pageOf = {
                MainActivity.PAGE_RECORDER, MainActivity.PAGE_RECORDER, MainActivity.PAGE_RECORDER,
                MainActivity.PAGE_RECORDER, MainActivity.PAGE_RECORDER, MainActivity.PAGE_RECORDER,
                MainActivity.PAGE_RECORDER, MainActivity.PAGE_RECORDER, MainActivity.PAGE_RECORDER,
                MainActivity.PAGE_RECORDER, MainActivity.PAGE_RECORDER, MainActivity.PAGE_DEVICES,
                MainActivity.PAGE_DEVICES, MainActivity.PAGE_MIXER, MainActivity.PAGE_MIXER,
                MainActivity.PAGE_LIBRARY, MainActivity.PAGE_LIBRARY, MainActivity.PAGE_PLAYLIST,
                MainActivity.PAGE_PLAYLIST, MainActivity.PAGE_STORAGE, MainActivity.PAGE_STORAGE,
                MainActivity.PAGE_STORAGE, MainActivity.PAGE_EXPORTS, MainActivity.PAGE_SETTINGS,
                MainActivity.PAGE_SETTINGS, MainActivity.PAGE_ABOUT,
        };
        for (int i = 0; i < visible.length; i++) {
            act.navigate(pageOf[i]);
            layout(root, 1080, 2340);
            View pr = pageRoot(act);
            check("feature: " + visible[i][2] + " (" + visible[i][1] + " on " + visible[i][0] + ")",
                    treeHasText(pr, visible[i][1]));
        }

        // ------------------------------------------------------- real-time analyser
        // \"spectrums will be real-time\": the analyser has to react to what is
        // arriving while capturing and while playing back, put a tone in the right
        // place on the frequency axis, and fall again when the signal stops.
        System.out.println();
        System.out.println("  real-time spectrum");
        act.navigate(MainActivity.PAGE_RECORDER);
        layout(root, 1080, 2340);
        com.mostakim.audiorec.ui.widgets.SpectrumView spectrum =
                (com.mostakim.audiorec.ui.widgets.SpectrumView)
                        findViewByName(pageRoot(act), "SpectrumView");
        check("analyser: it is on the recorder page", spectrum != null);
        // the click pass above walks every control, which includes the display
        // toggles and Freeze: put the page back to how the operator finds it
        if (spectrum != null && spectrum.isFrozen()) {
            View freeze = findClickableContaining(pageRoot(act), "Frozen");
            if (freeze == null) freeze = findClickableContaining(pageRoot(act), "Freeze");
            if (freeze != null) {
                freeze.performClick();
                layout(root, 1080, 2340);
            }
        }
        View analyserOff = findClickableContaining(pageRoot(act), "Analyser off");
        if (analyserOff != null) {
            analyserOff.performClick();
            layout(root, 1080, 2340);
        }
        check("analyser: it is visible without touching anything",
                spectrum != null && spectrum.getVisibility() == View.VISIBLE);
        check("analyser: it has a box to draw in",
                spectrum != null && spectrum.getHeight() > 0 && spectrum.getWidth() > 0,
                spectrum == null ? "no view"
                        : spectrum.getWidth() + "x" + spectrum.getHeight());
        if (spectrum != null) {
            App.get().prefs().setSampleRate(48000);
            spectrum.setSampleRate(48000);
            int before = spectrum.computeCount();

            // a 1 kHz tone has to light up the bar that covers 1 kHz
            int expect1k = barFor(1000.0, 48000);
            feedTone((com.mostakim.audiorec.audio.AudioEngine) act.engine(), 1000.0, 48000, 2);
            check("analyser: it analyses what arrives", spectrum.computeCount() > before,
                    (spectrum.computeCount() - before) + " frames analysed");
            int loudest = loudestBar(spectrum);
            check("analyser: a 1 kHz tone lands on the 1 kHz bar",
                    Math.abs(loudest - expect1k) <= 1,
                    "loudest bar " + loudest + ", expected " + expect1k);
            check("analyser: the tone is clearly above the noise floor",
                    spectrum.bar(loudest) > 0.5f,
                    String.valueOf(spectrum.bar(loudest)));
            check("analyser: it holds a falling peak cap", spectrum.cap(loudest) >= spectrum.bar(loudest)
                    - 0.01f);

            // a different tone has to move the peak
            int expect300 = barFor(300.0, 48000);
            feedTone((com.mostakim.audiorec.audio.AudioEngine) act.engine(), 300.0, 48000, 2);
            int loudest300 = loudestBar(spectrum);
            check("analyser: a 300 Hz tone lands on the 300 Hz bar",
                    Math.abs(loudest300 - expect300) <= 1,
                    "loudest bar " + loudest300 + ", expected " + expect300);

            // and silence has to drop the bars again
            feedSilence((com.mostakim.audiorec.audio.AudioEngine) act.engine(), 2);
            check("analyser: the bars fall when the signal stops",
                    spectrum.bar(loudest300) < 0.2f,
                    String.valueOf(spectrum.bar(loudest300)));

            // playback feeds the same display, so auditioning a take is live too
            int beforePlay = spectrum.computeCount();
            ((com.mostakim.audiorec.audio.AudioEngine) act.engine())
                    .feedPlaybackScope(tone(1000.0, 48000, 2048), 2048);
            check("analyser: it is live during playback as well",
                    spectrum.computeCount() > beforePlay);
        }

        // ------------------------------------------------------ gain and monitor
        // "the sliders should work properly with -0.1 and +0.1": one press of the
        // buttons is exactly one step, and a press in each direction returns to
        // where it started.
        System.out.println();
        System.out.println("  gain and monitor controls");
        App.get().prefs().setGainDb(0f);
        App.get().prefs().setMonitorGainDb(-6f);
        for (int page : new int[]{MainActivity.PAGE_RECORDER, MainActivity.PAGE_MIXER}) {
            act.navigate(page);
            layout(root, 1080, 2340);
            View plus = findClickableContaining(pageRoot(act), "+0.1");
            View minus = findClickableContaining(pageRoot(act), "\u22120.1");
            check("gain: the +0.1 button is on page " + page, plus != null);
            check("gain: the -0.1 button is on page " + page, minus != null);
            if (plus == null || minus == null) continue;
            float before = App.get().prefs().gainDb();
            plus.performClick();
            check("gain: one press on page " + page + " is exactly +0.1 dB",
                    Math.abs(App.get().prefs().gainDb() - (before + 0.1f)) < 1e-5f,
                    before + " -> " + App.get().prefs().gainDb());
            plus.performClick();
            check("gain: two presses are +0.2 dB",
                    Math.abs(App.get().prefs().gainDb() - (before + 0.2f)) < 1e-5f,
                    String.valueOf(App.get().prefs().gainDb()));
            minus.performClick();
            minus.performClick();
            check("gain: -0.1 twice comes back to where it started",
                    Math.abs(App.get().prefs().gainDb() - before) < 1e-5f,
                    before + " -> " + App.get().prefs().gainDb());
        }
        // the monitor strip is a separate control and moves the same way
        act.navigate(MainActivity.PAGE_RECORDER);
        layout(root, 1080, 2340);
        String[][] monitorButtons = {{"+0.1", "+0.1"}, {"\u22120.1", "-0.1"}};
        // two strips are on the page: the gain one first, then MONITOR
        View monitorPlus = nthClickableContaining(pageRoot(act), "+0.1", 2);
        check("monitor: the +0.1 button exists", monitorPlus != null);
        if (monitorPlus != null) {
            float before = App.get().prefs().monitorGainDb();
            monitorPlus.performClick();
            check("monitor: one press is exactly +0.1 dB",
                    Math.abs(App.get().prefs().monitorGainDb() - (before + 0.1f)) < 1e-5f,
                    before + " -> " + App.get().prefs().monitorGainDb());
            View monitorMinus = nthClickableContaining(pageRoot(act), "\u22120.1", 2);
            if (monitorMinus != null) {
                monitorMinus.performClick();
                check("monitor: and -0.1 returns to the start",
                        Math.abs(App.get().prefs().monitorGainDb() - before) < 1e-5f,
                        before + " -> " + App.get().prefs().monitorGainDb());
            } else {
                check("monitor: the -0.1 button exists", false);
            }
        }
        App.get().prefs().setGainDb(0f);
        App.get().prefs().setMonitorGainDb(-6f);

        // ------------------------------------------------ exporting and sharing
        // "I exported it but I never got the file": the rendered copy used to be
        // written only into the app's private folder, which no file manager, Drive
        // or WhatsApp can see, and the share intent carried no clip - so the read
        // grant never reached the receiving app.  Both are driven here through the
        // real code paths.
        System.out.println();
        System.out.println("  exporting and sharing");
        File shareSrc = File.createTempFile("audiorec-share-", ".wav");
        java.io.FileOutputStream out0 = new java.io.FileOutputStream(shareSrc);
        out0.write(new byte[]{82, 73, 70, 70, 1, 2, 3, 4, 5, 6, 7, 8});
        out0.close();

        android.content.ContentResolver.reset();
        android.content.Context.forgetStarted();
        try {
            Uri published = com.mostakim.audiorec.share.Downloads.saveNow(
                    act, shareSrc, "audio/wav", shareSrc.getName());
            ContentValues row = android.content.ContentResolver.rowOf(published);
            byte[] copied = android.content.ContentResolver.bytesOf(published);
            check("export: the copy is published through MediaStore", published != null);
            check("export: it lands in Download/AUDIO-rec",
                    row != null && ("Download/audiorec".equalsIgnoreCase(
                            String.valueOf(row.get("relative_path")))
                            || String.valueOf(row.get("relative_path"))
                            .equalsIgnoreCase("Download/" + com.mostakim.audiorec.share.Downloads.SUBDIR)),
                    row == null ? "no row" : String.valueOf(row.get("relative_path")));
            check("export: it keeps the file name",
                    row != null && shareSrc.getName().equals(row.get("_display_name")),
                    row == null ? "no row" : String.valueOf(row.get("_display_name")));
            check("export: it is stored with an audio MIME type",
                    row != null && "audio/wav".equals(row.get("mime_type")),
                    row == null ? "no row" : String.valueOf(row.get("mime_type")));
            check("export: the copy is visible again (pending flag cleared)",
                    row != null && "0".equals(String.valueOf(row.get("is_pending"))),
                    row == null ? "no row" : String.valueOf(row.get("is_pending")));
            check("export: every byte of the file was copied",
                    copied != null && copied.length == 12,
                    copied == null ? "nothing written" : copied.length + " bytes");
            String where = com.mostakim.audiorec.share.Downloads.visiblePath("take.wav");
            check("export: the operator is told where to look",
                    where.equals(android.os.Environment.DIRECTORY_DOWNLOADS + "/"
                            + com.mostakim.audiorec.share.Downloads.SUBDIR + "/take.wav"),
                    where);
        } catch (Throwable t) {
            check("export: publishing to Downloads works (" + t + ")", false);
        }

        android.content.Context.forgetStarted();
        try {
            Dialogs.shareFile(act, shareSrc, "audio/wav");
            Intent chooser = android.content.Context.lastStarted();
            Intent send = chooser == null ? null : chooser.chooserTarget();
            check("share: a chooser opens", chooser != null
                    && "android.intent.action.CHOOSER".equals(chooser.getAction()));
            check("share: it offers a SEND of the take",
                    send != null && Intent.ACTION_SEND.equals(send.getAction()));
            check("share: the receiving app is told the MIME type",
                    send != null && "audio/wav".equals(send.getType()),
                    send == null ? "nothing" : String.valueOf(send.getType()));
            check("share: the stream is a content:// URI from our provider",
                    send != null && send.getParcelableExtra(Intent.EXTRA_STREAM) != null
                            && send.getParcelableExtra(Intent.EXTRA_STREAM).toString()
                            .startsWith("content://com.mostakim.audiorec.files/"),
                    send == null || send.getParcelableExtra(Intent.EXTRA_STREAM) == null ? "none"
                            : send.getParcelableExtra(Intent.EXTRA_STREAM).toString());
            check("share: the read grant is set on the SEND and on the chooser",
                    send != null
                            && (send.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
                            && chooser != null
                            && (chooser.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
            ClipData clip = send == null ? null : send.getClipData();
            check("share: the URI travels in the clip, so the grant reaches the target",
                    clip != null && clip.firstUri() != null
                            && clip.firstUri().toString()
                            .startsWith("content://com.mostakim.audiorec.files/"),
                    clip == null ? "no clip" : String.valueOf(clip.firstUri()));
            check("share: the file name is offered as subject and title",
                    send != null && shareSrc.getName().equals(send.getStringExtra(Intent.EXTRA_SUBJECT))
                            && shareSrc.getName().equals(send.getStringExtra(Intent.EXTRA_TITLE)));
        } catch (Throwable t) {
            check("share: building the share intent works (" + t + ")", false);
        }
        check("share: a take can be saved to Downloads from the UI",
                findClickableContainingText(act, "Save") != null
                        || treeContainsText(pageRoot(act), "Save"));

        // the storage gauge paints its figures on a canvas, so it has no text for
        // the tree walk to find: it still needs a real box, and its figures still
        // have to be readable (accessibility, tests, screen readers)
        act.navigate(MainActivity.PAGE_STORAGE);
        layout(root, 1080, 2340);
        View gauge = findViewByName(pageRoot(act), "DiskBarView");
        check("feature: the disk gauge has a box to paint in",
                gauge != null && gauge.getWidth() > 0 && gauge.getHeight() > 0,
                gauge == null ? "not on the page"
                        : gauge.getWidth() + "x" + gauge.getHeight());
        check("feature: the disk gauge reports free and total space",
                gauge != null && gauge.getContentDescription() != null
                        && gauge.getContentDescription().toString().contains("free"),
                gauge == null || gauge.getContentDescription() == null ? "none"
                        : gauge.getContentDescription().toString());

        // the choosers, opened the way the operator opens them
        checkChooser(act, root, MainActivity.PAGE_RECORDER, "Buffer", "the buffer-size chooser",
                new String[]{"1024 frames", "16384 frames"}, null);
        checkChooser(act, root, MainActivity.PAGE_RECORDER, "Format", "the container chooser",
                new String[]{"WAV", "FLAC", "AIFF", "OGG"}, "MP3");
        checkChooser(act, root, MainActivity.PAGE_RECORDER, "Rate", "the sample-rate chooser",
                new String[]{"kHz", "192000"}, null);
        checkChooser(act, root, MainActivity.PAGE_RECORDER, "Depth", "the bit-depth chooser",
                new String[]{"16", "24", "32"}, null);
        checkChooser(act, root, MainActivity.PAGE_RECORDER, "Channels", "the channel chooser",
                new String[]{"channel"}, null);
        checkChooser(act, root, MainActivity.PAGE_LIBRARY, "\u22ef", "the take menu",
                new String[]{"Rename", "Delete", "Share", "Export as"}, null);
        checkChooser(act, root, MainActivity.PAGE_SETTINGS, "Input", "the input-device chooser",
                new String[]{"System default"}, null);
        checkChooser(act, root, MainActivity.PAGE_SETTINGS, "Output", "the output-device chooser",
                new String[]{"System default"}, null);

        report();
    }

    // ------------------------------------------------------------------ helpers
    private static void layout(View root, int width, int height) {
        // let queued callbacks (drawer animation, tickers) run before measuring
        android.os.HarnessLoop.pump(400, 200);
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }

    private static String titleOf(MainActivity act) throws Exception {
        Object t = field(act, "mTitle");
        return t instanceof TextView ? ((TextView) t).getText().toString() : null;
    }

    private static View pageRoot(MainActivity act) throws Exception {
        Object host = field(act, "mHost");
        if (host instanceof ViewGroup && ((ViewGroup) host).getChildCount() > 0) {
            return ((ViewGroup) host).getChildAt(0);
        }
        return null;
    }

    private static Object field(Object owner, String name) throws Exception {
        Class<?> c = owner.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(owner);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    /** "FrameLayout[1]/ScrollView[0]/LinearLayout[3]" - where a view sits in the tree */
    private static String pathOf(View root, View target) {
        if (root == target) return root.getClass().getSimpleName();
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                String sub = pathOf(g.getChildAt(i), target);
                if (sub != null) {
                    return root.getClass().getSimpleName() + "[" + i + "]/" + sub;
                }
            }
        }
        return null;
    }

    private static void collect(View v, List<View> out) {
        out.add(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out);
        }
    }

    private static int countViews(View root) {
        List<View> all = new ArrayList<>();
        collect(root, all);
        return all.size();
    }

    /** views that actually say something (text, or an icon) */
    private static int countWordyViews(View root) {
        List<View> all = new ArrayList<>();
        collect(root, all);
        int n = 0;
        for (View v : all) {
            if (v.getVisibility() != View.VISIBLE) continue;
            if (v instanceof TextView && ((TextView) v).getText().length() > 0) n++;
            if (v instanceof ImageView && ((ImageView) v).getImageResource() != 0) n++;
        }
        return n;
    }

    private static List<View> emptyContainers(View root) {
        List<View> all = new ArrayList<>();
        collect(root, all);
        List<View> out = new ArrayList<>();
        for (View v : all) {
            if (v.getVisibility() != View.VISIBLE) continue;
            if (!(v instanceof ViewGroup) || v instanceof ScrollView) continue;
            if (((ViewGroup) v).getChildCount() != 0) continue;
            // an empty container that cannot be seen (no background, no height) is a
            // deliberate placeholder - it costs nothing. Anything else is a defect.
            boolean visibleBox = v.getBackground() != null || v.getHeight() > 0;
            if (visibleBox) out.add(v);
        }
        return out;
    }

    /** visible content that came out 0 x 0 after layout - i.e. invisible UI */
    private static List<View> collapsedContent(View root) {
        List<View> all = new ArrayList<>();
        collect(root, all);
        List<View> out = new ArrayList<>();
        for (View v : all) {
            if (v.getVisibility() != View.VISIBLE) continue;
            boolean saysSomething = (v instanceof TextView && ((TextView) v).getText().length() > 0)
                    || (v instanceof ImageView && ((ImageView) v).getImageResource() != 0);
            if (!saysSomething) continue;
            if (v.getWidth() <= 0 || v.getHeight() <= 0) out.add(v);
        }
        return out;
    }

    private static Set<View> clickables(View root) {
        Set<View> out = new LinkedHashSet<>();
        if (root == null) return out;
        List<View> all = new ArrayList<>();
        collect(root, all);
        for (View v : all) {
            if (v.getVisibility() != View.VISIBLE) continue;
            if (v.getOnClickListener() != null) out.add(v);
        }
        return out;
    }

    private static View findClickableWithText(View root, String text) {
        List<View> all = new ArrayList<>();
        collect(root, all);
        for (View v : all) {
            if (v instanceof TextView && text.contentEquals(((TextView) v).getText())
                    && hasClickableAncestor(v, root)) {
                return clickTarget(v, root);
            }
        }
        return null;
    }

    private static boolean hasClickableAncestor(View v, View root) {
        return clickTarget(v, root) != null;
    }

    private static View clickTarget(View v, View root) {
        View cur = v;
        while (cur != null) {
            if (cur.getOnClickListener() != null) return cur;
            View parent = cur.getParent();
            if (parent == root.getParent()) break;
            cur = parent;
        }
        return null;
    }

    private static boolean treeContainsText(View root, String text) {
        List<View> all = new ArrayList<>();
        collect(root, all);
        for (View v : all) {
            if (v instanceof TextView && text.contentEquals(((TextView) v).getText())) return true;
        }
        return false;
    }

    /** the five tabs of the bottom bar, in order */
    private static List<View> tabsOf(View root, int w, int h) {
        List<View> out = new ArrayList<>();
        for (String label : new String[]{"Record", "Mixer", "Library", "Playlist", "More"}) {
            View item = findClickableExact(root, label);
            if (item != null) out.add(item);
        }
        return out;
    }

    /** a clickable whose own text is exactly this (the tab items, not their labels) */
    private static View findClickableExact(View root, String text) {
        List<View> all = new ArrayList<>();
        collect(root, all);
        View group = null;
        for (View v : all) {
            if (!(v instanceof TextView)) continue;
            if (!((TextView) v).getText().toString().equals(text)) continue;
            View target = clickTarget(v, root);
            if (target != null && target instanceof LinearLayout) return target;
            if (target != null) group = target;
        }
        return group;
    }

    /** visible content that sticks out past the window horizontally */
    private static List<View> overflowViews(View root, int windowWidth) {
        List<View> out = new ArrayList<>();
        List<View> all = new ArrayList<>();
        collect(root, all);
        for (View v : all) {
            if (v.getVisibility() != View.VISIBLE) continue;
            if (v instanceof ViewGroup) continue;
            if (v.getWidth() <= 0 && v.getHeight() <= 0) continue;
            if (insideScroller(v)) continue;      // a sideways strip is allowed to be wide
            if (v.getRight() > windowWidth + 1 || v.getLeft() < -1) out.add(v);
        }
        return out;
    }

    /** true when this view lives inside a HorizontalScrollView */
    private static boolean insideScroller(View v) {
        View p = v.getParent();
        int guard = 0;
        while (p != null && guard++ < 40) {
            if ("HorizontalScrollView".equals(p.getClass().getSimpleName())) return true;
            p = p.getParent();
        }
        return false;
    }

    /**
     * Anything that is on screen with no room to draw: text with no width, and
     * any visible leaf - a meter, a fader, a gauge - measured to nothing.
     */
    private static List<View> zeroSized(View root) {
        List<View> out = new ArrayList<>();
        List<View> all = new ArrayList<>();
        collect(root, all);
        for (View v : all) {
            if (v.getVisibility() != View.VISIBLE) continue;
            if (v instanceof ViewGroup) continue;
            // a filler carries nothing; it is allowed to be squeezed to nothing
            if (com.mostakim.audiorec.ui.kit.Ui.FILLER.equals(v.getTag())) continue;
            if (v.getWidth() > 0 && v.getHeight() > 0) continue;
            if (v instanceof TextView && ((TextView) v).getText().length() == 0) continue;
            out.add(v);
        }
        return out;
    }

    /** which spectrum bar covers a frequency, using the same 40 Hz - 20 kHz map */
    private static int barFor(double hz, int sampleRate) {
        int bars = 48;
        double fMin = 40, fMax = Math.min(20000, sampleRate / 2.0);
        double logMin = Math.log10(fMin), logMax = Math.log10(fMax);
        double t = (Math.log10(hz) - logMin) / (logMax - logMin);
        return (int) Math.max(0, Math.min(bars - 1, Math.floor(t * bars)));
    }

    private static int loudestBar(com.mostakim.audiorec.ui.widgets.SpectrumView s) {
        int best = 0;
        for (int i = 1; i < s.barCount(); i++) {
            if (s.bar(i) > s.bar(best)) best = i;
        }
        return best;
    }

    /** a mono sine, the shape the analyser windows hold */
    private static float[] tone(double hz, int sampleRate, int frames) {
        float[] out = new float[frames];
        for (int i = 0; i < frames; i++) {
            out[i] = (float) (0.8 * Math.sin(2 * Math.PI * hz * i / sampleRate));
        }
        return out;
    }

    /** feeds a tone the way the capture thread does: blocks of 256 frames, ~5 ms apart */
    private static void feedTone(com.mostakim.audiorec.audio.AudioEngine engine, double hz,
                                 int sampleRate, int channels) throws Exception {
        int block = 256;
        for (int n = 0; n < 40; n++) {
            float[] interleaved = new float[block * channels];
            for (int i = 0; i < block; i++) {
                float v = (float) (0.8 * Math.sin(2 * Math.PI * hz * (n * block + i) / sampleRate));
                for (int c = 0; c < channels; c++) interleaved[i * channels + c] = v;
            }
            engine.feedScope(interleaved, block, channels);
            Thread.sleep(5);
        }
    }

    private static void feedSilence(com.mostakim.audiorec.audio.AudioEngine engine, int channels)
            throws Exception {
        // ~450 ms of silence: with a 0.22 s release the bars should be down to an
        // eighth of where they were
        for (int n = 0; n < 90; n++) {
            engine.feedScope(new float[256 * channels], 256, channels);
            Thread.sleep(5);
        }
    }

    /** the n-th (1-based) clickable whose text contains this string */
    private static View nthClickableContaining(View root, String text, int n) {
        List<View> all = new ArrayList<>();
        collect(root, all);
        int seen = 0;
        for (View v : all) {
            if (v instanceof TextView && ((TextView) v).getText().toString().contains(text)) {
                View target = clickTarget(v, root);
                if (target != null && ++seen == n) return target;
            }
        }
        return null;
    }

    /** any clickable on the current page whose text contains this, anywhere in the tree */
    private static View findClickableContainingText(MainActivity act, String text)
            throws Exception {
        for (int page : new int[]{MainActivity.PAGE_LIBRARY, MainActivity.PAGE_EXPORTS,
                MainActivity.PAGE_RECORDER}) {
            act.navigate(page);
            View found = findClickableContaining(pageRoot(act), text);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Taps a control and inspects the chooser it opens: the items the operator
     * gets offered, and that a format we do not support is never on the list.
     */
    private static void checkChooser(MainActivity act, View root, int page, String tap,
                                     String what, String[] mustOffer, String mustNotOffer)
            throws Exception {
        act.navigate(page);
        layout(root, 1080, 2340);
        View target = findClickableContaining(pageRoot(act), tap);
        if (target == null) {
            check("feature: " + what + " opens (\"" + tap + "\" is on the page)", false);
            return;
        }
        try {
            target.performClick();
        } catch (Throwable t) {
            check("feature: " + what + " opens without throwing (" + t + ")", false);
            return;
        }
        layout(root, 1080, 2340);
        android.app.Dialog d = android.app.Dialog.lastShown();
        String items = d instanceof android.app.AlertDialog
                ? ((android.app.AlertDialog) d).itemsText() : null;
        if (items == null) {
            check("feature: " + what + " really opens", false);
            return;
        }
        check("feature: " + what + " really opens", true);
        for (String need : mustOffer) {
            check("feature: " + what + " offers " + need, items.contains(need));
        }
        if (mustNotOffer != null) {
            check("feature: " + what + " never offers " + mustNotOffer,
                    !items.contains(mustNotOffer));
        }
    }

    /** the first view whose class is named this, whatever package it is in */
    private static View findViewByName(View root, String simpleName) {
        if (root == null) return null;
        List<View> all = new ArrayList<>();
        collect(root, all);
        for (View v : all) {
            if (v.getClass().getSimpleName().equals(simpleName)) return v;
        }
        return null;
    }

    /** a clickable whose text contains the given string */
    private static View findClickableContaining(View root, String text) {
        List<View> all = new ArrayList<>();
        collect(root, all);
        for (View v : all) {
            if (v instanceof TextView && ((TextView) v).getText().toString().contains(text)) {
                View target = clickTarget(v, root);
                if (target != null) return target;
            }
        }
        return null;
    }

    /** any TextView on the page whose text contains the given string */
    private static boolean treeHasText(View root, String text) {
        if (root == null) return false;
        List<View> all = new ArrayList<>();
        collect(root, all);
        for (View v : all) {
            if (v instanceof TextView && ((TextView) v).getText().toString().contains(text)) {
                return true;
            }
        }
        return false;
    }

    /** indented view tree, for when a page needs explaining */
    private static void dump(View v, int depth) {
        StringBuilder sb = new StringBuilder("        ");
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append(v.getClass().getSimpleName())
                .append(" ").append(v.getWidth()).append("x").append(v.getHeight());
        android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp != null) {
            sb.append(" lp=").append(lp.width).append(",").append(lp.height);
            if (lp instanceof android.widget.LinearLayout.LayoutParams
                    && ((android.widget.LinearLayout.LayoutParams) lp).weight > 0) {
                sb.append(" w=").append(((android.widget.LinearLayout.LayoutParams) lp).weight);
            }
        }
        if (v instanceof ViewGroup) {
            sb.append(" (").append(((ViewGroup) v).getChildCount()).append(" children)");
        }
        if (v.getVisibility() != View.VISIBLE) sb.append(" [").append(v.getVisibility()).append("]");
        if (v instanceof TextView && ((TextView) v).getMaxWidth() > 0) {
            sb.append(" maxW=").append(((TextView) v).getMaxWidth());
        }
        if (v instanceof TextView) {
            String t = ((TextView) v).getText().toString().replace("\n", " / ");
            sb.append("  \"").append(t.length() > 48 ? t.substring(0, 48) + "..." : t).append("\"");
        }
        if (v instanceof ImageView && ((ImageView) v).getImageResource() != 0) {
            sb.append("  img=").append(Integer.toHexString(((ImageView) v).getImageResource()));
        }
        System.out.println(sb);
        if (v instanceof ViewGroup && depth < 14) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) dump(g.getChildAt(i), depth + 1);
        }
    }

    private static String describe(View v) {
        String label = "";
        if (v instanceof TextView) label = " \"" + ((TextView) v).getText() + "\"";
        return v.getClass().getSimpleName() + label;
    }

    private static void report() {
        System.out.println();
        if (failures.isEmpty()) {
            System.out.println("ui checks: " + checks + " passed");
            return;
        }
        System.out.println("ui checks: " + (checks - failures.size()) + "/" + checks
                + " passed, " + failures.size() + " FAILED");
        for (String f : failures) System.out.println("  FAIL  " + f);
        System.exit(1);
    }
}
