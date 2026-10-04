package android.content;

import android.net.Uri;

import java.util.ArrayList;

public class Intent {
    public static final String ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT";
    public static final String ACTION_SEND = "android.intent.action.SEND";
    public static final String ACTION_GET_CONTENT = "android.intent.action.GET_CONTENT";
    public static final String ACTION_MAIN = "android.intent.action.MAIN";
    public static final String ACTION_VIEW = "android.intent.action.VIEW";
    public static final String CATEGORY_OPENABLE = "android.intent.category.OPENABLE";
    public static final String CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER";
    public static final String EXTRA_STREAM = "android.intent.extra.STREAM";
    public static final String EXTRA_SUBJECT = "android.intent.extra.SUBJECT";
    public static final String EXTRA_TEXT = "android.intent.extra.TEXT";
    public static final int FLAG_GRANT_READ_URI_PERMISSION = 1;
    public static final int FLAG_GRANT_WRITE_URI_PERMISSION = 2;

    private String mAction;
    private Uri mData;
    private String mType;
    private final ArrayList<String> mCategories = new ArrayList<>();

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
    public Intent putExtra(String name, String value) { return this; }
    public Intent putExtra(String name, int value) { return this; }
    public Intent putExtra(String name, long value) { return this; }
    public Intent putExtra(String name, boolean value) { return this; }
    public Intent putExtra(String name, java.io.Serializable value) { return this; }
    public Intent putExtra(String name, android.net.Uri value) { return this; }
    public Intent putExtra(String name, CharSequence value) { return this; }
    public Intent putExtra(String name, String[] value) { return this; }
    public Intent putParcelableArrayListExtra(String name, ArrayList<?> list) { return this; }
    public String getStringExtra(String name) { return null; }
    public int getIntExtra(String name, int def) { return def; }
    public boolean getBooleanExtra(String name, boolean def) { return def; }
    public Intent addFlags(int flags) { return this; }
    public Intent setFlags(int flags) { return this; }

    public static Intent createChooser(Intent target, String title) { return new Intent(ACTION_SEND); }
}
