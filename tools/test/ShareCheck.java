import com.mostakim.audiorec.share.ShareRules;
import com.mostakim.audiorec.util.Formats;

/**
 * Checks for the sharing rules (ShareRules), which decide whether a file may be
 * handed to Drive/WhatsApp through the content provider.
 *
 * No framework involved: these are pure path and MIME decisions.  The first case
 * is a negative control - the prefix test this replaced must be shown to be
 * wrong, otherwise the rest proves nothing.
 */
public class ShareCheck {

    private static int passed, failed;

    public static void main(String[] args) {
        if (run() != 0) System.exit(1);
    }

    public static int run() {
        System.out.println("-- sharing (content provider) rules --");

        String files = "/data/user/0/com.mostakim.audiorec/files";
        String ext = "/storage/emulated/0/Android/data/com.mostakim.audiorec/files";
        String recordDir = "/storage/1B2C-3D4E/AUDIO-rec";
        String cache = "/data/user/0/com.mostakim.audiorec/cache/share";
        String[] roots = ShareRules.roots(files, ext, recordDir, cache);

        // ---- negative control: the naive prefix test --------------------
        check(naiveAllowed("/data/user/0/com.mostakim.audiorec/files-old/take.wav", files),
                "negative control: startsWith() lets a sibling directory through");
        check(!ShareRules.allowedPath("/data/user/0/com.mostakim.audiorec/files-old/take.wav",
                        roots, false),
                "a sibling directory that merely shares a prefix is NOT ours");

        // ---- inside every root ------------------------------------------
        String[] inside = {
                files + "/Recordings/take-001.wav",
                ext + "/Recordings/Exports/take-001 (24bit-48k).flac",
                recordDir + "/take-001.aiff",
                recordDir + "/Exports/take-001.ogg",
                cache + "/share/preview.wav",
        };
        for (String p : inside) {
            check(ShareRules.allowedPath(p, roots, false), "should be shareable: " + p);
        }
        check(ShareRules.allowedPath(files, roots, false), "the root directory itself counts as inside");

        // ---- outside -----------------------------------------------------
        String[] outside = {
                "/data/user/0/com.other.app/files/take.wav",
                "/storage/emulated/0/Download/take.wav",
                "/storage/1B2C-3D4E/AUDIO-rec-old/take.wav",
                "/data/user/0/com.mostakim.audiorec/filesX/take.wav",
                "/data/user/0/com.mostakim.audiorec/databases/take.wav",
                "/etc/hosts",
        };
        for (String p : outside) {
            check(!ShareRules.allowedPath(p, roots, false), "must NOT be shareable: " + p);
        }

        // a recording the database knows about stays shareable after the operator
        // moved the recording folder somewhere else
        check(ShareRules.allowedPath("/storage/9Z9Z-1234/Old Takes/take-007.wav", roots, true),
                "a known take is shareable from a folder we no longer record into");

        // ---- path shape abuse -------------------------------------------
        // the provider canonicalises first, so what the rules see is the real path
        String traversal;
        try {
            traversal = new java.io.File(files + "/../../../etc/hosts").getCanonicalPath();
        } catch (java.io.IOException e) {
            traversal = "<unresolved>";
        }
        check(!traversal.startsWith("/data/user/0/com.mostakim.audiorec/")
                        && !ShareRules.allowedPath(traversal, roots, false),
                "a traversal resolves outside the roots and is refused (" + traversal + ")");
        check(ShareRules.allowedPath("/data/user/0/com.mostakim.audiorec/files/sub/deep/x.wav",
                        roots, false),
                "nested subdirectories are fine");
        check(ShareRules.under("/a/b", "/a/b/"), "a trailing slash on the root is ignored");
        check(!ShareRules.under(null, "/a") && !ShareRules.under("/a", null),
                "nulls never match");
        check(!ShareRules.allowedPath(null, roots, false), "a null path is refused");
        check(!ShareRules.allowedPath(files + "/x.wav", new String[0], false),
                "with no roots resolved, nothing is served");

        // ---- root assembly ----------------------------------------------
        check(ShareRules.roots(null, null, null, null).length == 0,
                "no directories at all -> no roots");
        check(ShareRules.roots(files, files, files, files).length == 1, "duplicates collapse");
        check(ShareRules.roots(null, ext, recordDir, null).length == 2,
                "a null entry does not take the others down with it");
        check(ShareRules.roots("  " + files + "  ", "", null, null).length == 1
                        && ShareRules.under(files + "/x.wav", ShareRules.roots("  " + files + "  ",
                        "", null, null)[0]),
                "blank entries are dropped and whitespace trimmed");

        // ---- MIME table ---------------------------------------------------
        String[][] mimes = {
                {"take-001.wav", "audio/wav"},
                {"take-001.WAV", "audio/wav"},
                {"take-001.flac", "audio/flac"},
                {"take-001.aiff", "audio/x-aiff"},
                {"take-001.aif", "audio/x-aiff"},
                {"take-001.ogg", "audio/ogg"},
                {"My Take.oga", "audio/ogg"},
                {"notes.txt", "application/octet-stream"},
                {null, "application/octet-stream"},
        };
        for (String[] m : mimes) {
            check(m[1].equals(ShareRules.mimeFor(m[0])),
                    "mime for " + m[0] + " should be " + m[1] + ", got " + ShareRules.mimeFor(m[0]));
        }
        // the recorder's container tags and the file names must agree, or WhatsApp
        // gets a file whose type and extension disagree
        for (String container : Formats.CONTAINERS) {
            String byTag = Formats.mimeFor(container);
            String byName = ShareRules.mimeFor("take-001." + container);
            check(byTag.equals(byName), "container tag " + container + " -> " + byTag
                    + " but the file name gives " + byName);
        }
        System.out.println("sharing checks: " + passed + " passed"
                + (failed > 0 ? ", " + failed + " FAILED" : ""));
        return failed;
    }

    /** the check the provider used to make */
    private static boolean naiveAllowed(String path, String root) {
        return path.startsWith(root);
    }

    private static void check(boolean ok, String what) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("  FAIL " + what);
        }
    }
}
