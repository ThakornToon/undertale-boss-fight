package core;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * The GML instance list. Owns all live {@link Entity} objects and reproduces
 * GML's {@code instance_create}, {@code instance_destroy}, {@code with(obj)}
 * iteration, {@code instance_exists}, and depth-sorted drawing.
 *
 * <p>Adds and destroys are queued so iteration during a frame is safe, then
 * applied by {@link #flush()} at the end of the frame.
 *
 * // GML: the global instance list + with()/instance_*() built-ins
 */
public final class EntityManager {

    private final List<Entity> active = new ArrayList<>();
    private final List<Entity> addQueue = new ArrayList<>();
    private final List<Entity> destroyQueue = new ArrayList<>();

    /** GML: instance_create(...). Deferred until {@link #flush()}. */
    public <T extends Entity> T add(T e) {
        addQueue.add(e);
        return e;
    }

    /** GML: instance_destroy(). Deferred until {@link #flush()}. */
    public void destroy(Entity e) {
        if (e != null) {
            e.destroyed = true;
            destroyQueue.add(e);
        }
    }

    /** GML: with(Class) { action }. Iterates a snapshot, skipping destroyed. */
    @SuppressWarnings("unchecked")
    public <T extends Entity> void with(Class<T> type, Consumer<T> action) {
        // Iterate a copy so the action may spawn/destroy safely.
        for (Entity e : new ArrayList<>(active)) {
            if (!e.destroyed && type.isInstance(e)) {
                action.accept((T) e);
            }
        }
    }

    /** GML: instance_exists(Class). */
    public boolean exists(Class<? extends Entity> type) {
        for (Entity e : active) {
            if (!e.destroyed && type.isInstance(e)) {
                return true;
            }
        }
        return false;
    }

    /** GML: instance_number(Class). */
    public int count(Class<? extends Entity> type) {
        int n = 0;
        for (Entity e : active) {
            if (!e.destroyed && type.isInstance(e)) {
                n++;
            }
        }
        return n;
    }

    /** GML: instance_nearest(x, y, Class). Null if none. */
    public <T extends Entity> T nearest(double x, double y, Class<T> type) {
        T best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : active) {
            if (e.destroyed || !type.isInstance(e)) {
                continue;
            }
            double dx = e.x - x;
            double dy = e.y - y;
            double d = dx * dx + dy * dy;
            if (d < bestDist) {
                bestDist = d;
                best = type.cast(e);
            }
        }
        return best;
    }

    /** GML: Begin Step pass over all instances. */
    public void beginStepAll() {
        for (Entity e : new ArrayList<>(active)) {
            if (!e.destroyed) {
                e.beginStep();
            }
        }
    }

    /** GML: alarms tick, then Step event, for all instances. */
    public void updateAll() {
        for (Entity e : new ArrayList<>(active)) {
            if (!e.destroyed) {
                e.tickAlarms();
                e.update();
            }
        }
    }

    /** GML: End Step pass over all instances. */
    public void endStepAll() {
        for (Entity e : new ArrayList<>(active)) {
            if (!e.destroyed) {
                e.endStep();
            }
        }
    }

    /** GML: draw all instances, deepest first (largest depth drawn first). */
    public void renderAll(Graphics2D g) {
        List<Entity> ordered = new ArrayList<>(active);
        ordered.sort(Comparator.comparingInt((Entity e) -> e.depth).reversed());
        for (Entity e : ordered) {
            if (!e.destroyed) {
                e.render(g);
            }
        }
    }

    /** Apply queued adds/destroys. Called once at the end of each frame. */
    public void flush() {
        if (!destroyQueue.isEmpty()) {
            for (Entity e : destroyQueue) {
                if (active.remove(e)) {
                    e.onDestroy();
                }
            }
            destroyQueue.clear();
        }
        if (!addQueue.isEmpty()) {
            active.addAll(addQueue);
            addQueue.clear();
        }
    }

    /** Remove every instance (scene teardown). */
    public void clear() {
        active.clear();
        addQueue.clear();
        destroyQueue.clear();
    }
}
