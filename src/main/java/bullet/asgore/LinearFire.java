package bullet.asgore;

import battle.Soul;
import core.EntityManager;
import util.GMLHelper;

/**
 * A fire that travels in a straight line at a speed that ramps via {@code friction}
 * (GML: {@code obj_genericfire} — {@code speed}/{@code direction}/{@code friction},
 * negative friction = accelerate; also reused for {@code obj_sided_fire}). When the
 * parent hand bullet leaves the screen it retargets every dropped fire toward the
 * soul ({@link #aimAt}), which is what makes Asgore's hand attack curl its fire
 * trail into the heart.
 *
 * // GML: obj_genericfire / obj_sided_fire
 */
public final class LinearFire extends AsgoreFire {

    /** GML: direction (screen-space degrees) + speed + friction. */
    public double direction;
    public double speed;
    /** GML: friction — subtracted from speed each frame (negative = accelerate). */
    public double friction;

    public LinearFire(EntityManager manager, Soul soul) {
        super(manager, soul);
    }

    /** GML: move_towards_point(heart, spd); friction = -accel — a one-time re-aim. */
    public void aimAt(double tx, double ty, double spd, double accel) {
        direction = GMLHelper.point_direction(x, y, tx, ty);
        speed = spd;
        friction = -accel;
    }

    @Override
    protected void move() {
        x += GMLHelper.lengthdir_x(speed, direction);
        y += GMLHelper.lengthdir_y(speed, direction);
        speed -= friction;          // negative friction accelerates the flame
        if (speed < 0) {
            speed = 0;
        }
    }
}
