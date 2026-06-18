package bullet.mettaton;

import battle.Soul;
import bullet.Bullet;
import bullet.Shootable;
import core.EntityManager;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * A solid white block (examples 8/9/16). It <b>blocks</b> the player's gun — a shot
 * that hits it is stopped but the block isn't destroyed; only a neighbouring
 * {@link PlusBlast} (from the bomb it falls paired with) breaks it. Touching it
 * hurts, so the soul must clear the paired bomb and slip under.
 *
 * // GML: obj_mettattackgen solid block bullets
 */
public final class SolidBlock extends Bullet implements Shootable {

    private static final double HALF = 13;

    public SolidBlock(EntityManager manager, Soul soul, double x, double y, double vspeed) {
        super(manager, soul);
        this.x = x;
        this.y = y;
        this.vspeed = vspeed;
        this.dmg = 5;
        this.depth = -1;
    }

    @Override
    protected double halfWidth() {
        return HALF;
    }

    @Override
    protected double boxTop() {
        return y - HALF;
    }

    @Override
    protected double boxBottom() {
        return y + HALF;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        y += vspeed;
        if (G.inv <= 0 && Math.abs(soul.x - x) < HALF + 1 && Math.abs(soul.y - y) < HALF + 1) {
            onHitSoul();
        }
        if (y > G.idealborder[3] + 30) {
            manager.destroy(this);
        }
    }

    @Override
    public boolean hitBy(double px, double py) {
        return Math.abs(px - x) < HALF + 1 && Math.abs(py - y) < HALF + 1;
    }

    @Override
    public boolean onShot() {
        return true; // blocks the gun bullet, but the block itself survives
    }

    @Override
    public int shotRating() {
        return 0; // blocked, not destroyed — no ratings
    }

    @Override
    public void render(Graphics2D g) {
        g.setColor(Color.WHITE);
        g.fillRect((int) (x - HALF), (int) (y - HALF), (int) (HALF * 2), (int) (HALF * 2));
    }
}
