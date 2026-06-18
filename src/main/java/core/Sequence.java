package core;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Reusable scripted-cutscene helper (Core Requirement 10). Replaces the
 * hand-rolled linear integer counters every boss uses for its intro, death,
 * transform, and ratings/spare endings — {@code con}, {@code trcon},
 * {@code death_c}, {@code songcon}, {@code lac}, {@code bonetalk}, etc.
 *
 * <p>This class only removes the counter/advance boilerplate; the <em>content</em>
 * (what happens at each step) stays in the boss, registered via {@link #at}.
 * A step's handler runs once when the sequence first reaches it. The boss decides
 * when to {@link #advance()} — typically gated on {@link #whenWriterDone}.
 *
 * // GML: con / trcon / death_c / lac counters stepped on alarms + writer checks
 */
public final class Sequence {

    /** GML: the counter variable (con / death_c / ...). */
    private int step;
    private int lastRunStep = Integer.MIN_VALUE;
    private final Map<Integer, Runnable> actions = new HashMap<>();

    public Sequence() {
        this(0);
    }

    public Sequence(int start) {
        this.step = start;
    }

    /** Register the action that runs when the sequence reaches {@code step}. */
    public Sequence at(int step, Runnable action) {
        actions.put(step, action);
        return this;
    }

    /** GML: con. The current step. */
    public int step() {
        return step;
    }

    /** GML: con = n. Jump directly to a step. */
    public void setStep(int n) {
        step = n;
    }

    /** GML: con++. Move to the next step. */
    public void advance() {
        step++;
    }

    /** GML: con += n. */
    public void advance(int by) {
        step += by;
    }

    /**
     * Run the current step's action if it hasn't run yet. Call once per frame.
     * Re-entering the same step (without changing it) does not re-run.
     */
    public void update() {
        if (step != lastRunStep) {
            lastRunStep = step;
            Runnable a = actions.get(step);
            if (a != null) {
                a.run();
            }
        }
    }

    /**
     * GML: {@code if (!instance_exists(OBJ_WRITER)) con++}. Advance only once the
     * supplied gate (typically "writer finished") is true. Returns whether it
     * advanced this call.
     */
    public boolean whenWriterDone(BooleanSupplier writerDone) {
        if (writerDone.getAsBoolean()) {
            advance();
            return true;
        }
        return false;
    }
}
