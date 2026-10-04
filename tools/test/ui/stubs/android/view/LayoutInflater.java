package android.view;

import android.content.Context;

/**
 * The app builds every view in code; if it ever starts inflating XML, the
 * harness should say so loudly rather than quietly working.
 */
public class LayoutInflater {

    private LayoutInflater() {
    }

    public static LayoutInflater from(Context context) {
        throw new UnsupportedOperationException(
                "AUDIO-rec builds its UI in code - there are no layout XML files");
    }

    public View inflate(int resource, ViewGroup root) {
        throw new UnsupportedOperationException("no XML layouts in this app");
    }
}
