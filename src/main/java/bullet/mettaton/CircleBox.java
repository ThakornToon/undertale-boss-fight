package bullet.mettaton;

import battle.Soul;
import bullet.Bullet;
import bullet.Shootable;
import core.EntityManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * A white box with a black target circle (GML: {@code spr_blackbox_pl}) — examples
 * 2/11/12/13/14/15. Falls through the box and is destroyed by a gun shot (or a
 * neighbouring plus-blast). Touching it hurts.
 *
 * // GML: obj_mettattackgen black-circle box bullets
 */
public final class CircleBox extends Bullet implements Shootable {

    private static final double HALF = 12;
    /** Rewind rows persist off-screen (so all rows survive to reverse back up). */
    public boolean noBoundsCull;

    public CircleBox(EntityManager manager, Soul soul, double x, double y, double vspeed) {
        super(manager, soul);
        this.x = x;
        this.y = y;
        this.vspeed = vspeed;
        this.dmg = 4;
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
        if (G.inv <= 0 && Math.abs(soul.x - x) < HALF + 2 && Math.abs(soul.y - y) < HALF + 2) {
            onHitSoul();
        }
        double hi = noBoundsCull ? 900 : G.idealborder[3] + 30;
        double lo = noBoundsCull ? -900 : -40;
        // Cull off the top only when rising (reversed rewind); boxes descending from
        // above the screen (e.g. the ex2 column) must be left to fall into view.
        if (y > hi || (y < lo && vspeed < 0)) {
            manager.destroy(this);
        }
    }

    @Override
    public boolean hitBy(double px, double py) {
        return Math.abs(px - x) < HALF + 2 && Math.abs(py - y) < HALF + 2;
    }

    @Override
    public boolean onShot() {
        manager.destroy(this);
        return true;
    }

    @Override
    public void render(Graphics2D g) {
        BufferedImage img = Assets.sprite("spr_blackbox_pl_0");
        if (img != null) {
            g.drawImage(img, (int) (x - img.getWidth() / 2.0), (int) (y - img.getHeight() / 2.0), null);
            return;
        }
        g.setColor(Color.WHITE);
        g.fillRect((int) (x - HALF), (int) (y - HALF), (int) (HALF * 2), (int) (HALF * 2));
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(2f));
        g.setColor(Color.BLACK);
        g.drawOval((int) (x - 6), (int) (y - 6), 12, 12);
        g.setStroke(old);
    }
}
