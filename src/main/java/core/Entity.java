package core;

import java.awt.Graphics2D;

/**
 * Base for everything that lives in the battle's instance list — the soul,
 * boss bodies, bullets, generators, writers, menu buttons. Reproduces a GML
 * instance: per-frame {@code update()}, a {@code Draw} event, a {@code destroyed}
 * flag standing in for {@code instance_destroy}, and Begin/End Step hooks.
 *
 * <p>Alarms ({@code alarm[0..11]}) and named {@code event_user(n)} callbacks are
 * attached in step 3 once {@link AlarmTimer}/{@link EventUser} exist.
 *
 * // GML: a GameMaker object instance (Create/Step/Draw/Destroy events)
 */
public abstract class Entity {

    /** GML: x / y. */
    public double x;
    public double y;
    /** GML draw depth; higher depth draws first (further back). */
    public int depth;
    /** GML: pending instance_destroy — flushed at end of frame. */
    public boolean destroyed;

    /** The instance list this entity belongs to (for spawning/querying). */
    protected final EntityManager manager;

    /** GML: this instance's alarm[0..11]. */
    protected final AlarmTimer alarms = new AlarmTimer(this);
    /** GML: this instance's User Defined events (event_user). */
    protected final EventUser events = new EventUser();

    protected Entity(EntityManager manager) {
        this.manager = manager;
    }

    /** GML: alarms tick before the Step event. Driven by {@link EntityManager}. */
    public void tickAlarms() {
        alarms.tick();
    }

    /** GML: with(this) event_user(n). */
    public void fireEvent(int n) {
        events.fire(n);
    }

    /** GML: Step event. Called once per logic frame. */
    public abstract void update();

    /** GML: Draw event. */
    public abstract void render(Graphics2D g);

    /** GML: Begin Step event. Runs before {@link #update()} for all entities. */
    public void beginStep() {
    }

    /** GML: End Step event. Sans i-frames / KARMA tick run here. */
    public void endStep() {
    }

    /** GML: instance_destroy(). Deferred — see {@link EntityManager#destroy}. */
    public void onDestroy() {
    }

    /** GML: alarm[n] firing. Overridden by entities that use alarms. */
    public void onAlarm(int n) {
    }
}
