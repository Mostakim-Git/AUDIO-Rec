package android.content;

public abstract class BroadcastReceiver {
    public abstract void onReceive(Context context, Intent intent);

    public static class PendingResult {
    }
}
