package bullet;

import core.Entity;
import core.EntityManager;
import core.GlobalState;

/**
 * Base for both bullets and the timed generator objects (architecture principle 5:
 * generators are entities too). Carries the GML motion fields every projectile
 * shares — {@code hspeed}/{@code vspeed}/{@code gravity}/{@code image_angle}/
 * {@code image_speed} — and a {@code dmg} value, and integrates motion each frame.
 *
 * // GML: blt_parent / *_gen objects (motion + dmg)
 */
public abstract class AttackPattern extends Entity {

    protected static final GlobalState G = GlobalState.get();

    /** GML: hspeed / vspeed. */
    public double hspeed;
    public double vspeed;
    /** GML: gravity + gravity_direction folded into a velocity delta per frame. */
    public double gravity;
    public double gravityDirection = 270; // GML default: down
    /** GML: image_angle / image_speed. */
    public double imageAngle;
    public double imageSpeed;
    /** GML: this bullet's damage. */
    public int dmg;

    protected AttackPattern(EntityManager manager) {
        super(manager);
        this.depth = -1; // GML bone depth
    }

    /** GML: the built-in hspeed/vspeed/gravity/image_angle integration. */
    protected void integrateMotion() {
        if (gravity != 0) {
            // GML screen-space: 270° = down (y grows downward).
            hspeed += gravity * Math.cos(Math.toRadians(gravityDirection));
            vspeed += -gravity * Math.sin(Math.toRadians(gravityDirection));
        }
        x += hspeed;
        y += vspeed;
        imageAngle += imageSpeed;
    }

    @Override
    public void update() {
        integrateMotion();
    }
}
