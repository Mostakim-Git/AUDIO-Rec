package android.content;

import android.net.Uri;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Harness stand-in for android.content.Intent.
 *
 * It records what was set on it so the UI tests can assert on the intents the
 * app sends out - the share sheet above all: which URI, which MIME type, which
 * flags and whether the read grant travels in the clip.
 */
public class Intent {
    public static final String ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT";
    public static final String ACTION_SEND = "android.intent.action.SEND";
    public static final String ACTION_CHOOSER = "android.intent.action.CHOOSER";
    public static final String ACTION_GET_CONTENT = "android.intent.action.GET_CONTENT";
    public static final String ACTION_VIEW = "android.intent.action.VIEW";
    public static final String ACTION_MAIN = "android.intent.action.MAIN";
    public static final String CATEGORY_OPENABLE = "android.intent.category.OPENABLE";
    public static final String CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER";
    public static final String EXTRA_STREAM = "android.intent.extra.STREAM";
    public static final String EXTRA_SUBJECT = "android.intent.extra.SUBJECT";
    public static final String EXTRA_TEXT = "android.intent.extra.TEXT";
    public static final String EXTRA_TITLE = "android.intent.extra.TITLE";
    public static final String EXTRA_INTENT = "android.intent.extra.INTENT";
    public static final int FLAG_GRANT_READ_URI_PERMISSION = 1;
    public static final int FLAG_GRANT_WRITE_URI_PERMISSION = 2;
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;

    private String mAction;
    private Uri mData;
    private String mType;
    private int mFlags;
    private ClipData mClip;
    private final ArrayList<String> mCategories = new ArrayList<>();
    private final Map<String, Object> mExtras = new LinkedHashMap<>();

    public Intent() { }

    public Intent(String action) { mAction = action; }

    public Intent(Context ctx, Class<?> cls) { }

    public String getAction() { return mAction; }
    public Intent setAction(String action) { mAction = action; return this; }
    public Uri getData() { return mData; }
    public Intent setData(Uri uri) { mData = uri; return this; }
    public String getType() { return mType; }
    public Intent setType(String type) { mType = type; return this; }
    public Intent addCategory(String c) { mCategories.add(c); return this; }
    public Intent putExtra(String name, String value) { mExtras.put(name, value); return this; }
    public Intent putExtra(String name, int value) { mExtras.put(name, value); return this; }
    public Intent putExtra(String name, long value) { mExtras.put(name, value); return this; }
    public Intent putExtra(String name, boolean value) { mExtras.put(name, value); return this; }
    public Intent putExtra(String name, java.io.Serializable value) { mExtras.put(name, value); return this; }
    public Intent putExtra(String name, android.net.Uri value) { mExtras.put(name, value); return this; }
    public Intent putExtra(String name, CharSequence value) { mExtras.put(name, String.valueOf(value)); return this; }
    public Intent putExtra(String name, String[] value) { mExtras.put(name, value); return this; }
    public Intent putExtra(String name, Intent value) { mExtras.put(name, value); return this; }
    public Intent putExtra(String name, Object value) { mExtras.put(name, value); return this; }
    public Intent putParcelableArrayListExtra(String name, ArrayList<?> list) { mExtras.put(name, list); return this; }
    public String getStringExtra(String name) {
        Object v = mExtras.get(name);
        return v == null ? null : String.valueOf(v);
    }
    public Uri getParcelableExtra(String name) {
        Object v = mExtras.get(name);
        return v instanceof Uri ? (Uri) v : null;
    }
    public int getIntExtra(String name, int def) {
        Object v = mExtras.get(name);
        return v instanceof Number ? ((Number) v).intValue() : def;
    }
    public boolean getBooleanExtra(String name, boolean def) {
        Object v = mExtras.get(name);
        return v instanceof Boolean ? (Boolean) v : def;
    }
    public boolean hasExtra(String name) { return mExtras.containsKey(name); }
    public Intent addFlags(int flags) { mFlags |= flags; return this; }
    public Intent setFlags(int flags) { mFlags = flags; return this; }
    public int getFlags() { return mFlags; }
    public Intent setClipData(ClipData clip) { mClip = clip; return this; }
    public ClipData getClipData() { return mClip; }

    /** stub helper: the intent a chooser was built around */
    public Intent chooserTarget() {
        Object v = mExtras.get(EXTRA_INTENT);
        return v instanceof Intent ? (Intent) v : null;
    }

    public static Intent createChooser(Intent target, String title) {
        Intent c = new Intent(ACTION_CHOOSER);
        c.putExtra(EXTRA_INTENT, target);
        c.putExtra(EXTRA_TITLE, title);
        return c;
    }

    public String toString() {
        return "Intent(" + mAction + ", type=" + mType + ", data=" + mData
                + ", flags=" + mFlags + ", extras=" + mExtras.keySet() + ")";
    }
}
