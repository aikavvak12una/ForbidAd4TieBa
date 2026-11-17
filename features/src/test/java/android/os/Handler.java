package android.os;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** A deterministic per-thread queue; unscoped calls retain Android's JVM stub failure. */
public final class Handler {
    private static final class Work {
        final long due;
        final Runnable action;
        Work(long due, Runnable action) { this.due = due; this.action = action; }
    }
    private static final class Queue {
        long now;
        final List<Work> work = new ArrayList<>();
    }
    private static final ThreadLocal<Queue> CURRENT = new ThreadLocal<>();

    public Handler(Looper looper) { requireTest(); }
    public Handler() { throw new RuntimeException("Stub!"); }
    static void requireTest() { queue(); }
    private static Queue queue() {
        Queue queue = CURRENT.get();
        if (queue == null) throw new RuntimeException("Stub!");
        return queue;
    }
    public static void beginTest() {
        if (CURRENT.get() != null) throw new IllegalStateException("Handler scope already open");
        CURRENT.set(new Queue());
    }
    public static void endTest() { CURRENT.remove(); }
    public boolean post(Runnable action) { return postDelayed(action, 0L); }
    public boolean postDelayed(Runnable action, long delay) {
        Queue queue = queue();
        queue.work.add(new Work(queue.now + delay, action));
        return true;
    }
    public void removeCallbacks(Runnable action) { queue().work.removeIf(work -> work.action == action); }
    public static void runCurrent() { advanceBy(0L); }
    public static void advanceBy(long elapsed) {
        if (elapsed < 0L) throw new IllegalArgumentException("Negative time advance");
        Queue queue = queue();
        long until = queue.now + elapsed;
        int limit = 1000;
        while (true) {
            Work work = queue.work.stream().filter(item -> item.due <= until)
                .min(Comparator.comparingLong(item -> item.due)).orElse(null);
            if (work == null) break;
            if (--limit == 0) throw new AssertionError("Unbounded scheduled work");
            queue.work.remove(work);
            queue.now = work.due;
            work.action.run();
        }
        queue.now = until;
    }
}
