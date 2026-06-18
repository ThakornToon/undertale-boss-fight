package bullet.mettaton;

import battle.Soul;
import bullet.Bullet;
import bullet.Shootable;
import core.EntityManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;

/**
 * Mettaton's long segmented arm (example_3): a box-wide bar divided into squares,
 * descending, with a small yellow square sliding back and forth along it. The bar
 * blocks the whole width (touching it hurts); shooting the yellow square clears the
 * entire bar.
 *
 * // GML: obj_mettattackgen segmented-arm bar (spr_mettstick + sliding weak point)
 */
public final class SegArm extends Bullet implements Shootable {

    private static final double H = 9;
    private double markerT;
    private double markerDir = 1;
    private boolean pendingMarker;

    public SegArm(EntityManager manager, Soul soul, double y, double vspeed) {
        super(manager, soul);
        this.y = y;
        this.vspeed = vspeed;
        this.markerT = Math.random();
        this.dmg = 5;
        this.depth = -1;
    }

    @Override
    protected double halfWidth() {
        return 9999;
    }

    @Override
    protected double boxTop() {
        return y - H;
    }

    @Override
    protected double boxBottom() {
        return y + H;
    }

    private double markerX() {
        double[] b = G.idealborder;
        return b[0] + 12 + (b[1] - b[0] - 24) * markerT;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        y += vspeed;
        markerT += markerDir * 0.018;
        if (markerT >= 1) {
            markerT = 1;
            markerDir = -1;
        } else if (markerT <= 0) {
            markerT = 0;
            markerDir = 1;
        }
        if (G.inv <= 0 && Math.abs(soul.y - y) < H + Soul.HALF) {
            onHitSoul(); // the bar blocks the whole width
        }
        if (y > G.idealborder[3] + 20) {
            manager.destroy(this);
        }
    }

    @Override
    public boolean hitBy(double px, double py) {
        if (Math.abs(py - y) > H + 5) {
            return false;
        }
        // The whole bar blocks the gun; only a shot on the yellow marker clears it.
        pendingMarker = Math.abs(px - markerX()) < 12;
        return true;
    }

    @Override
    public boolean onShot() {
        if (pendingMarker) {
            manager.destroy(this); // hit the weak point → the whole bar clears
        }
        return true;              // either way the shot is blocked/consumed
    }

    @Override
    public int shotRating() {
        return pendingMarker ? 5 : 0; // ratings only when the bar is actually cleared
    }

    @Override
    public void render(Graphics2D g) {
        double[] b = G.idealborder;
        int x0 = (int) b[0];
        int w = (int) (b[1] - b[0]);
        // The bar: a white outline split into segments.
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(2f));
        g.setColor(Color.WHITE);
        g.drawRect(x0, (int) (y - H), w, (int) (H * 2));
        int segs = Math.max(4, w / 22);
        for (int i = 1; i < segs; i++) {
            int sx = x0 + (int) (w * (i / (double) segs));
            g.drawLine(sx, (int) (y - H), sx, (int) (y + H));
        }
        // The yellow weak-point square.
        g.setColor(Color.YELLOW);
        g.fillRect((int) (markerX() - 8), (int) (y - 8), 16, 16);
        g.setStroke(old);
    }
}
