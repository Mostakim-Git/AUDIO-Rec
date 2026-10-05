package android.content;

import android.net.Uri;

/**
 * Harness stand-in for android.content.ClipData.
 *
 * The app sets a clip on the share intent because a per-URI read grant only
 * travels with the chooser when the URI is in the clip; this stub keeps the
 * item so the harness can check that it really happens.
 */
public class ClipData {

    private final String mLabel;
    private final Uri mUri;

    private ClipData(String label, Uri uri) {
        mLabel = label;
        mUri = uri;
    }

    public static ClipData newPlainText(CharSequence label, CharSequence text) {
        return new ClipData(String.valueOf(label), null);
    }

    public static ClipData newUri(ContentResolver resolver, CharSequence label, Uri uri) {
        return new ClipData(String.valueOf(label), uri);
    }

    public static ClipData newRawUri(CharSequence label, Uri uri) {
        return new ClipData(String.valueOf(label), uri);
    }

    public int getItemCount() {
        return mUri == null ? 0 : 1;
    }

    public String getLabel() {
        return mLabel;
    }

    /** stub helper: the item's URI (real code goes through Item.getUri()) */
    public Uri firstUri() {
        return mUri;
    }
}
