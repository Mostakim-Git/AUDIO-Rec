package android.os;

/**
 * Harness stand-in for android.os.Handler.
 *
 * The harness has no message loop, so post() runs the callback straight away -
 * exactly like the Activity stub's runOnUiThread().  Code that hands work to a
 * Handler therefore completes before the call returns, which keeps the tests
 * deterministic.
 */
public class Handler {

    private final Looper mLooper;

    public Handler() {
        mLooper = Looper.getMainLooper();
    }

    public Handler(Looper looper) {
        mLooper = looper;
    }

    public Looper getLooper() {
        return mLooper;
    }

    public boolean post(Runnable r) {
        r.run();
        return true;
    }

    public boolean postDelayed(Runnable r, long delayMs) {
        r.run();
        return true;
    }

    public void removeCallbacks(Runnable r) {
    }

    public void removeCallbacksAndMessages(Object token) {
    }
}
