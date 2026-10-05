package android.os;

/** Harness stand-in for android.os.Looper: the main looper is just this thread. */
public class Looper {

    private static final Looper MAIN = new Looper();

    public static Looper getMainLooper() {
        return MAIN;
    }

    public static Looper myLooper() {
        return MAIN;
    }

    public Thread getThread() {
        return Thread.currentThread();
    }
}
