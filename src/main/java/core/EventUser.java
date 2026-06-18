package core;

import java.util.HashMap;
import java.util.Map;

/**
 * Emulates GML's {@code event_user(0..15)} named callbacks. An entity registers
 * a handler per slot; another object fires it with {@code event_user(n)} (in GML,
 * via {@code with(obj) event_user(n)}).
 *
 * <p>Bodies use these as sub-state entry points — e.g. Undyne's body registers
 * {@code event_user(2)} = spear turn, {@code 13} = melt, {@code 14} = siner.
 *
 * // GML: event_user(n) + User Defined events 0..15
 */
public final class EventUser {

    public static final int COUNT = 16;

    private final Map<Integer, Runnable> handlers = new HashMap<>();

    /** GML: defining User Defined event n. */
    public void register(int n, Runnable handler) {
        handlers.put(n, handler);
    }

    /** GML: event_user(n). No-op if nothing registered for that slot. */
    public void fire(int n) {
        Runnable h = handlers.get(n);
        if (h != null) {
            h.run();
        }
    }

    public boolean has(int n) {
        return handlers.containsKey(n);
    }
}
