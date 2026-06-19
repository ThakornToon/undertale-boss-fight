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
 * <p><b>Deferred-mutation invariant.</b> {@link #add} and {@link #destroy} never
 * touch {@link #active} directly — they push to {@link #addQueue}/{@link #destroyQueue},
 * and only {@link #flush()} (called once at the very end of the frame) and
 * {@link #clear()} (scene teardown) ever modify {@code active}'s structure. So while
 * a frame's Begin-Step / Step / End-Step / Draw passes run, {@code active} is
 * structurally frozen even though the entities being ticked spawn and kill each other
 * freely. That lets every pass iterate {@code active} <em>directly</em> with a plain
 * for-each: there is no {@link java.util.ConcurrentModificationException} risk and no
 * need to copy the list each pass. A destroyed entity stays in {@code active} until
 * the flush, so each pass skips it via its {@code destroyed} flag — exactly what a
 * defensive snapshot would have done, only without the per-frame allocation.
 *
 * // GML: the global instance list + with()/instance_*() built-ins
 */
public final class EntityManager {

    private final List<Entity> active = new ArrayList<>();
    private final List<Entity> addQueue = new ArrayList<>();
    private final List<Entity> destroyQueue = new ArrayList<>();
    /** Reused scratch list for the depth-sorted Draw pass (avoids a per-frame copy). */
    private final List<Entity> renderOrder = new ArrayList<>();

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

    /**
     * GML: with(Class) { action }. Runs {@code action} for every live instance of
     * {@code type}, skipping ones already flagged destroyed. The action may freely
     * spawn or destroy entities (those go through the deferred queues), so iterating
     * {@code active} directly is safe — see the class-level invariant.
     */
    @SuppressWarnings("unchecked")
    public <T extends Entity> void with(Class<T> type, Consumer<T> action) {
        for (Entity e : active) {
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

    /** GML: Begin Step pass over all instances (direct iteration — see invariant). */
    public void beginStepAll() {
        for (Entity e : active) {
            if (!e.destroyed) {
                e.beginStep();
            }
        }
    }

    /** GML: alarms tick, then Step event, for all instances (direct iteration). */
    public void updateAll() {
        for (Entity e : active) {
            if (!e.destroyed) {
                e.tickAlarms();
                e.update();
            }
        }
    }

    /** GML: End Step pass over all instances (direct iteration — see invariant). */
    public void endStepAll() {
        for (Entity e : active) {
            if (!e.destroyed) {
                e.endStep();
            }
        }
    }

    /**
     * GML: draw all instances, deepest first (largest depth drawn first). Needs a
     * sorted view, so it fills the reusable {@link #renderOrder} buffer rather than
     * allocating a fresh list every frame.
     */
    public void renderAll(Graphics2D g) {
        renderOrder.clear();
        renderOrder.addAll(active);
        renderOrder.sort(Comparator.comparingInt((Entity e) -> e.depth).reversed());
        for (Entity e : renderOrder) {
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
