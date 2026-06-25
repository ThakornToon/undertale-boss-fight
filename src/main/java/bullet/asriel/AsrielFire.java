package bullet.asriel;

import battle.Soul;
import bullet.Bullet;
import core.EntityManager;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * A single falling fire mote — the Dreemurr-family fire magic Asriel copies from
 * Toriel during the calm "It's the end." intro (GML: {@code obj_1sidegen} bullettype 7).
 * It also stands in as a light, survivable token attack for the Part-A gauntlet turns
 * whose bespoke patterns (Shocker Breaker, Chaos Saber, Chaos Buster, Hyper Goner) are
 * not yet ported, so every turn remains dodgeable while the structure is built out.
 *
 * // GML: obj_1sidegen bullettype 7 (fire) — placeholder stand-in
 */
public final class AsrielFire extends Bullet {

    private double siner;

    public AsrielFire(EntityManager manager, Soul soul, double x, double y,
                      double vspeed, int dmg) {
        super(manager, soul);
        this.x = x;
        this.y = y;
        this.vspeed = vspeed;
        this.dmg = dmg;
        this.depth = -2;
    }

    @Override
    protected double halfWidth() {
        return 5;
    }

    @Override
    protected double boxTop() {
        return y - 8;
    }

    @Override
    protected double boxBottom() {
        return y + 8;
    }

    @Override
    public void update() {
        siner++;
        super.update();
        // Cull once it falls past the bottom of the box.
        if (y > G.idealborder[3] + 16) {
            manager.destroy(this);
        }
    }

    @Override
    public void render(Graphics2D g) {
        // A little orange flame with a yellow core.
        float flick = (float) (0.7 + 0.3 * Math.sin(siner * 0.5));
        g.setColor(new Color(1f, 0.45f * flick, 0f));
        g.fillOval((int) x - 5, (int) y - 7, 10, 14);
        g.setColor(new Color(1f, 0.9f, 0.3f));
        g.fillOval((int) x - 2, (int) y - 3, 5, 8);
    }
}
