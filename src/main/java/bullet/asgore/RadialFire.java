package bullet.asgore;

import battle.Soul;
import core.EntityManager;
import util.GMLHelper;

/**
 * A converging fire (GML: {@code obj_cfire}). It sits on a circle of radius
 * {@code r} around the box centre and spirals inward as {@code r} shrinks by
 * {@code rspeed} each frame (optionally rotating via {@code angspeed}). A ring of 36
 * of these collapses onto the soul — Asgore's "closing fire" attack.
 *
 * // GML: obj_cfire
 */
public final class RadialFire extends AsgoreFire {

    /** GML: r / rspeed / ang / angspeed. */
    public double r = 300;
    public double rspeed = 4;
    public double ang;
    public double angspeed;

    private final double centerX;
    private final double centerY;

    public RadialFire(EntityManager manager, Soul soul) {
        super(manager, soul);
        // GML: centre of the combat box.
        this.centerX = G.idealborder[0] + (G.idealborder[1] - G.idealborder[0]) / 2;
        this.centerY = G.idealborder[2] + (G.idealborder[3] - G.idealborder[2]) / 2;
    }

    @Override
    protected void move() {
        r -= rspeed;
        ang += angspeed;
        if (r <= 0.5) {
            manager.destroy(this);
            return;
        }
        x = centerX + GMLHelper.lengthdir_x(r, ang);
        y = centerY + GMLHelper.lengthdir_y(r, ang);
    }
}
