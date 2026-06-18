package bullet.mettaton;

import battle.Soul;
import bullet.Bullet;
import core.EntityManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * A lightning bolt fired by the popped-out heart core (GML: {@code spr_mettlightning_pl})
 * — examples 4/11/17. Flies outward from the heart in a radial burst; touching it
 * hurts. Culls once it leaves the box.
 *
 * // GML: obj_mettattackgen lightning bullets
 */
public final class LightningBullet extends Bullet {

    private static final double HALF = 8;

    public LightningBullet(EntityManager manager, Soul soul, double x, double y,
                           double hspeed, double vspeed) {
        super(manager, soul);
        this.x = x;
        this.y = y;
        this.hspeed = hspeed;
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
        x += hspeed;
        y += vspeed;
        if (G.inv <= 0 && Math.abs(soul.x - x) < HALF + Soul.HALF
                && Math.abs(soul.y - y) < HALF + Soul.HALF) {
            onHitSoul();
        }
        // Cull at the screen edges (not the box) so the radial burst spreads in every
        // direction from the waist-height heart, not just downward into the box.
        if (x < -24 || x > 664 || y < -24 || y > 504) {
            manager.destroy(this);
        }
    }

    @Override
    public void render(Graphics2D g) {
        BufferedImage img = Assets.sprite("spr_mettlightning_pl_0");
        if (img != null) {
            double ang = Math.atan2(vspeed, hspeed);
            AffineTransform old = g.getTransform();
            g.translate(x, y);
            g.rotate(ang);
            g.drawImage(img, -12, -12, null);
            g.setTransform(old);
        } else {
            g.setColor(Color.WHITE);
            g.fillRect((int) (x - 4), (int) (y - 4), 8, 8);
        }
    }
}
