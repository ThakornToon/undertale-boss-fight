package boss;

import core.Entity;
import core.EntityManager;

/**
 * The body half of the mandatory controller/body split (architecture principle 3).
 * A {@code BossBody} draws the multi-part boss sprite and, for bosses whose
 * patterns live in the body, runs the active attack pattern's sub-state machine.
 * The controller pushes it an integer attack selector via {@link #setAttack(int)}
 * (GML: {@code a_type} / {@code fighto} / {@code turns} / {@code attacktype}).
 *
 * <p>Papyrus's body is purely presentational — the controller spawns the bones —
 * so {@link #setAttack(int)} just records the selector for the body to draw a
 * matching pose if desired.
 *
 * // GML: obj_*_body (e.g. obj_papyrusbody / mypart1)
 */
public abstract class BossBody extends Entity {

    /** GML: the controller→body attack selector. */
    protected int attackSel;

    protected BossBody(EntityManager manager) {
        super(manager);
    }

    /** GML: the controller sets a_type/fighto on the body. */
    public void setAttack(int selector) {
        this.attackSel = selector;
    }
}
