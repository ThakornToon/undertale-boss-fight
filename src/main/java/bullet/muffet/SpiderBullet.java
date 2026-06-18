package bullet.muffet;

import battle.Soul;
import battle.WebBoard;
import bullet.Bullet;
import core.EntityManager;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * A spider that scuttles horizontally along one web strand (GML:
 * {@code obj_spiderbullet}, the parent of the donut and croissant). It waits one
 * frame off the side of the box, then slides across the chosen strand at
 * {@code speedfactor} px/frame and vanishes once it has crossed to the far side.
 *
 * <p>This is also the base class for {@link DonutBullet} and {@link Croissant}, which
 * share the spawn/cull logic and only add their own per-frame motion (the donut's
 * vertical bounce, the croissant's spin and acceleration) via {@link #motionStep()}.
 *
 * <p>Position is read from the {@link WebBoard}: strand {@code choice} (1-indexed) at
 * {@code yzero + (choice-1)*yspace}, spawning at {@code xmid ± (xlen*2 + 40)}.
 *
 * // GML: obj_spiderbullet
 */
public class SpiderBullet extends Bullet {

    protected final WebBoard web;

    /** GML choice — the strand (1..yamt) this bullet rides. */
    public int choice = 1;
    /** GML side — 0 = left→right, 1 = right→left. */
    public int side;
    /** GML speedfactor — horizontal speed in px/frame. */
    public double speedfactor = 6;

    /** GML alarm[0] fires one frame after creation to position + launch the bullet. */
    private int armTimer = 1;
    protected boolean armed;
    /** GML image_angle (croissant) — kept here so the shared render can rotate. */
    protected double imageAngleDeg;
    /** GML image_yscale (donut) — squashes on a bounce, then recovers to 1. */
    protected double imageYscale = 1;

    protected String spriteName = "spr_spiderbullet1_0";

    public SpiderBullet(EntityManager manager, Soul soul) {
        super(manager, soul);
        this.web = soul.web;
        this.depth = -1;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0 || web == null) {
            manager.destroy(this);
            return;
        }
        if (!armed) {
            if (--armTimer <= 0) {
                arm();
                armed = true;
            }
            return; // GML: invisible until alarm[0] positions it
        }
        x += hspeed;
        y += vspeed;
        motionStep();
        if (collidesSoul()) {
            onHitSoul();
        }
        cullStep();
    }

    /** GML alarm[0]: snap to the strand and launch in from off the chosen side. */
    protected void arm() {
        y = web.yzero + (choice - 1) * web.yspace;
        if (side == 0) {
            x = web.xmid - web.xlen * 2 - 40;
            hspeed = speedfactor;
        } else {
            x = web.xmid + web.xlen * 2 + 40;
            hspeed = -speedfactor;
        }
    }

    /** Per-frame extra motion for subclasses (donut bounce, croissant spin). */
    protected void motionStep() {
    }

    /** GML Step: destroy once the bullet has slid past the far rim of the web span. */
    protected void cullStep() {
        if (hspeed > 0 && x > web.xmid + web.xlen * 2) {
            manager.destroy(this);
        } else if (hspeed < 0 && x < web.xmid - web.xlen * 2) {
            manager.destroy(this);
        }
    }

    /** Half-extent of the (square) hitbox; the visible art is ~12px centred in 24px. */
    protected double half() {
        return 6;
    }

    @Override
    protected double boxTop() {
        return y - half();
    }

    @Override
    protected double boxBottom() {
        return y + half();
    }

    /** Centred overlap test (the bullet's x,y is its centre, unlike the bone bullets). */
    @Override
    protected boolean collidesSoul() {
        if (G.inv > 0) {
            return false;
        }
        return Math.abs(soul.x - x) < half() + Soul.HALF
                && Math.abs(soul.y - y) < half() + Soul.HALF;
    }

    @Override
    public void render(Graphics2D g) {
        if (!armed) {
            return;
        }
        BufferedImage img = Assets.sprite(spriteName);
        if (img == null) {
            return;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        if (imageAngleDeg != 0 || imageYscale != 1) {
            java.awt.geom.AffineTransform old = g.getTransform();
            g.translate(x, y);
            g.rotate(-Math.toRadians(imageAngleDeg)); // GML angles are CCW
            g.scale(1, imageYscale);
            g.drawImage(img, -w / 2, -h / 2, null);
            g.setTransform(old);
        } else {
            g.drawImage(img, (int) (x - w / 2.0), (int) (y - h / 2.0), null);
        }
    }
}
