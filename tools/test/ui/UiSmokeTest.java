import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mostakim.audiorec.App;
import com.mostakim.audiorec.ui.MainActivity;
import com.mostakim.audiorec.ui.SidebarLayout;

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
    private static final List<String> failures = new ArrayList<>();

    private static void check(String what, boolean ok) {
        checks++;
        if (!ok) failures.add(what);
    }

    public static void main(String[] args) throws Exception {
        treeMode = args.length > 0 && args[0].equals("--tree");
        App app = new App();
        app.initForHarness();

        MainActivity act = new MainActivity();
        act.simulateCreate(null);

        View root = act.contentView();
        check("onCreate() set a content view", root != null);
        if (!(root instanceof SidebarLayout)) {
            check("content view is the SidebarLayout shell", false);
            report();
            return;
        }
        SidebarLayout shell = (SidebarLayout) root;
        check("shell has BOTH panes bound without the XML inflater", shell.isReady());
        check("shell found the content view", shell.content() != null);
        check("shell found the nav rail", shell.sidebar() != null);

        // ---------------------------------------------------- portrait / drawer
        layout(root, 1080, 2340);
        check("portrait phone starts in drawer mode", shell.isDrawerMode());
        check("in drawer mode the rail starts off-screen",
                shell.sidebar().getRight() <= 0);
        check("in drawer mode the content owns the full width",
                shell.content().getLeft() == 0 && shell.content().getWidth() == 1080);
        check("the topbar carries the page title", treeContainsText(root, "Dashboard"));
        check("the rail is built even while it is off-screen",
                ((ViewGroup) shell.sidebar()).getChildCount() >= 12);
        check("the rail shows the app name", treeContainsText(shell.sidebar(), "AUDIO-rec"));

        // a nav item must be reachable and clickable: open the drawer and tap
        shell.openDrawer();
        layout(root, 1080, 2340);
        check("the drawer opens over the content", shell.sidebar().getLeft() == 0);
        View recorderItem = findClickableWithText(shell.sidebar(), "Recorder");
        check("the Recorder nav item exists", recorderItem != null);
        if (recorderItem != null) {
            recorderItem.performClick();
            layout(root, 1080, 2340);
            check("tapping a nav item navigates", "Recorder".equals(titleOf(act)));
            check("the rail closes after a tap", shell.sidebar().getRight() <= 0);
        }

        // -------------------------------------------------------- every screen
        System.out.println();
        System.out.printf("  %-16s %6s %6s %8s  %s%n",
                "page", "views", "words", "size", "state");
        String[][] pages = {
                {"Dashboard", "Dashboard"}, {"Recorder", "Recorder"}, {"Mixer", "Mixer"},
                {"Devices", "Devices"}, {"Sessions", "Sessions"}, {"Library", "Library"},
                {"Playlist", "Playlist"}, {"Export Files", "Exports"},
                {"Device Presets", "Presets"}, {"Storage", "Storage"},
                {"Settings", "Settings"}, {"About", "About"},
        };
        int[] pageIds = {
                MainActivity.PAGE_DASHBOARD, MainActivity.PAGE_RECORDER, MainActivity.PAGE_MIXER,
                MainActivity.PAGE_DEVICES, MainActivity.PAGE_SESSIONS, MainActivity.PAGE_LIBRARY,
                MainActivity.PAGE_PLAYLIST, MainActivity.PAGE_EXPORTS, MainActivity.PAGE_PRESETS,
                MainActivity.PAGE_STORAGE, MainActivity.PAGE_SETTINGS, MainActivity.PAGE_ABOUT,
        };
        for (int i = 0; i < pageIds.length; i++) {
            final int page = pageIds[i];
            final String railLabel = pages[i][0];
            final String name = pages[i][1];
            String state;
            int views = 0;
            int words = 0;
            String size = "";
            try {
                act.navigate(page);
                layout(root, 1080, 2340);
                check(name + ": the top bar really shows this page",
                        name.equals(titleOf(act)));
                check(name + ": the rail carries a \"" + railLabel + "\" entry",
                        treeContainsText(shell.sidebar(), railLabel));
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

        // ------------------------------------------------------ tablet / rail
        act.navigate(MainActivity.PAGE_DASHBOARD);
        layout(root, 2400, 1600);
        shell.setDrawerMode(false);
        layout(root, 2400, 1600);
        check("tablet width uses the fixed rail", !shell.isDrawerMode());
        check("the rail is on screen at tablet width", shell.sidebar().getLeft() == 0);
        check("the rail has its 286dp width", shell.sidebar().getWidth() == 858);
        check("the content starts next to the rail", shell.content().getLeft() == 858);
        check("the content is sized to the rest of the window",
                shell.content().getWidth() == 2400 - 858);
        check("the rail keeps its navigation on a tablet",
                ((ViewGroup) shell.sidebar()).getChildCount() >= 12);
        check("the dashboard is still populated on a tablet",
                countWordyViews(pageRoot(act)) >= 3);

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
        if (v instanceof ViewGroup) {
            sb.append(" (").append(((ViewGroup) v).getChildCount()).append(" children)");
        }
        if (v.getVisibility() != View.VISIBLE) sb.append(" [").append(v.getVisibility()).append("]");
        if (v instanceof TextView) {
            String t = ((TextView) v).getText().toString().replace("\n", " / ");
            sb.append("  \"").append(t.length() > 48 ? t.substring(0, 48) + "..." : t).append("\"");
        }
        if (v instanceof ImageView && ((ImageView) v).getImageResource() != 0) {
            sb.append("  img=").append(Integer.toHexString(((ImageView) v).getImageResource()));
        }
        System.out.println(sb);
        if (v instanceof ViewGroup && depth < 6) {
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
