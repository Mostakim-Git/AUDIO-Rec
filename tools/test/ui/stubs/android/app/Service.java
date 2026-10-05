package android.app;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

public abstract class Service extends Context {
    public void onCreate() { }
    public int onStartCommand(Intent intent, int flags, int startId) { return 1; }
    public void onDestroy() { }
    public IBinder onBind(Intent intent) { return null; }
    public void stopSelf() { }
    public final void startForeground(int id, Object notification) { }
}
