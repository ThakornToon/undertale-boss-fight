package bullet.mettaton;

import battle.Soul;
import bullet.Bullet;
import bullet.Shootable;
import core.EntityManager;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * A tiny parasol Mettaton (GML: {@code spr_parasolmett}) — examples 1/3/4/5. It
 * glides down on its parasol, swaying; shooting it makes it vanish. If it reaches the
 * bottom unshot, it flings an M-marked heart back up the screen
 * ({@link MettHeartBullet}). Touching it hurts.
 *
 * // GML: obj_mettattackgen parasol bullets
 */
public final class ParasolMett extends Bullet implements Shootable {

    private static final double HALF = 15;
    private double frame;
    private double sway;
    private boolean thrown;

    public ParasolMett(EntityManager manager, Soul soul, double x, double y, double vspeed) {
        super(manager, soul);
        this.x = x;
        this.y = y;
        this.vspeed = vspeed;
        this.sway = Math.random() * 6;
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
        sway += 0.09;
        x += Math.sin(sway) * 0.7;
        y += vspeed;
        frame += 0.5;
        if (G.inv <= 0 && Math.abs(soul.x - x) < HALF && Math.abs(soul.y - y) < HALF) {
            onHitSoul();
        }
        if (!thrown && y >= G.idealborder[3] - 12) {
            // Reached the bottom unshot: throw an M-heart toward the soul, then leave.
            thrown = true;
            double dx = soul.x - x;
            double dy = soul.y - y;
            double d = Math.max(1, Math.hypot(dx, dy));
            double sp = 3.4;
            manager.add(new MettHeartBullet(manager, soul, x, y, dx / d * sp, dy / d * sp));
        }
        if (y > G.idealborder[3] + 40) {
            manager.destroy(this);
        }
    }

    @Override
    public boolean hitBy(double px, double py) {
        return Math.abs(px - x) < HALF + 3 && Math.abs(py - y) < HALF + 4;
    }

    @Override
    public boolean onShot() {
        manager.destroy(this);
        return true;
    }

    @Override
    public void render(Graphics2D g) {
        int f = ((int) frame) % 18;
        BufferedImage img = Assets.sprite("spr_parasolmett_" + f);
        if (img != null) {
            // origin (20,23); draw a touch under 1× to fit the box.
            double s = 0.9;
            int w = (int) (img.getWidth() * s);
            int h = (int) (img.getHeight() * s);
            g.drawImage(img, (int) (x - 20 * s), (int) (y - 23 * s), w, h, null);
        }
    }
}
