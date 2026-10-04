package android.os;

import java.util.ArrayList;
import java.util.List;

/**
 * A tiny stand-in for the main message queue.
 *
 * Callbacks are queued rather than run inline, because some of the app's
 * callbacks re-post themselves until wall-clock time passes (the drawer
 * animation, the recording ticker).  The test drives them with drain(), which
 * runs due callbacks and sleeps a little so that "time" really does advance.
 */
public final class HarnessLoop {

    private HarnessLoop() {
    }

    private static final class Task {
        final Runnable what;
        final long dueAt;

        Task(Runnable what, long dueAt) {
            this.what = what;
            this.dueAt = dueAt;
        }
    }

    private static final List<Task> QUEUE = new ArrayList<>();

    public static void post(Runnable r) {
        if (r != null) QUEUE.add(new Task(r, 0L));
    }

    public static void postDelayed(Runnable r, long delayMs) {
        if (r != null) QUEUE.add(new Task(r, System.currentTimeMillis() + Math.max(0, delayMs)));
    }

    public static void remove(Runnable r) {
        for (int i = QUEUE.size() - 1; i >= 0; i--) {
            if (QUEUE.get(i).what == r) QUEUE.remove(i);
        }
    }

    /**
     * Runs due callbacks for up to budgetMs of wall-clock time, so animations
     * that re-post themselves until time has passed really do finish.
     */
    public static int pump(long budgetMs, int maxCallbacks) {
        long deadline = System.currentTimeMillis() + budgetMs;
        int ran = 0;
        while (ran < maxCallbacks && System.currentTimeMillis() < deadline) {
            int n = drain(64);
            if (n == 0) {
                if (QUEUE.isEmpty()) break;
                try {
                    Thread.sleep(2);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                ran += n;
            }
        }
        return ran;
    }

    /** runs due callbacks; returns how many ran */
    public static int drain(int maxCallbacks) {
        int ran = 0;
        while (ran < maxCallbacks) {
            long now = System.currentTimeMillis();
            Task due = null;
            for (Task t : QUEUE) {
                if (t.dueAt <= now) {
                    due = t;
                    break;
                }
            }
            if (due == null) break;
            QUEUE.remove(due);
            due.what.run();
            ran++;
            try {
                Thread.sleep(2);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return ran;
    }

    /** runs everything, ignoring delays, up to a bound */
    public static int drainAll(int maxCallbacks) {
        int ran = 0;
        while (!QUEUE.isEmpty() && ran < maxCallbacks) {
            Task t = QUEUE.remove(0);
            t.what.run();
            ran++;
        }
        return ran;
    }

    public static int pending() {
        return QUEUE.size();
    }

    public static void clear() {
        QUEUE.clear();
    }
}
