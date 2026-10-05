package android.content;

public class ClipboardManager {
    private ClipData mClip;

    public void setPrimaryClip(ClipData clip) { mClip = clip; }

    public ClipData getPrimaryClip() { return mClip; }
}
