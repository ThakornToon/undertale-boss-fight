package bullet.asgore;

import battle.Soul;
import bullet.Bullet;
import core.EntityManager;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * Base for Asgore's fire-bullet projectiles (GML: the {@code spr_firebullet_*}
 * objects — {@code obj_genericfire}, {@code obj_cfire}, {@code obj_sinefire_asghelix},
 * {@code obj_sided_fire}). Unlike a bone (a tall column), a fire is a small point
 * sprite, so this overrides {@link Bullet}'s column hitbox with a centred
 * axis-aligned box and draws the two-frame flame sprite. Subclasses supply only the
 * per-frame motion in {@link #move()}.
 *
 * // GML: obj_asgorebulparent children (fire bullets)
 */
public abstract class AsgoreFire extends Bullet {

    /** GML: image_xscale/yscale — fire dots are usually drawn at 2x. */
    public double scale = 2;
    /** Flame sprite (some side fires use the no-core variant). */
    protected String sprite = "spr_firebullet_center_generous";
    /** GML: image_index — alternates the two flame frames. */
    protected double anim;

    protected AsgoreFire(EntityManager manager, Soul soul) {
        super(manager, soul);
        this.dmg = 3;          // small chip damage; many dots are on screen at once
        this.depth = -1;
    }

    /** Subclass motion for this frame (GML Step). */
    protected abstract void move();

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        move();
        anim += 0.25;
        if (collidesSoul()) {
            onHitSoul();
            manager.destroy(this);   // GML: fire destroys itself on contact (event_user 0)
        }
        cullFarOffscreen();
    }

    /** Half-extent of the (square) flame hitbox — a touch tighter than the sprite. */
    private double half() {
        return 6 * (scale / 2.0);
    }

    @Override
    protected boolean collidesSoul() {
        if (G.inv > 0) {
            return false;
        }
        double h = half();
        return soul.x + Soul.HALF > x - h && soul.x - Soul.HALF < x + h
                && soul.y + Soul.HALF > y - h && soul.y - Soul.HALF < y + h;
    }

    /**
     * Destroy once well outside the play field (each subclass also self-culls). The
     * top bound is generous: the fire-column attack's second wave (GML
     * {@code obj_asgoreattackgen}) spawns its helix fires ~285px <em>above</em> the
     * box and lets them fall in, so a tight top cull would delete the second wave
     * before it ever appears.
     */
    private void cullFarOffscreen() {
        if (x < -120 || x > 760 || y < -700 || y > 640) {
            manager.destroy(this);
        }
    }

    // Bullet's abstract column edges — unused (we override collidesSoul) but required.
    @Override
    protected double boxTop() {
        return y - half();
    }

    @Override
    protected double boxBottom() {
        return y + half();
    }

    @Override
    public void render(Graphics2D g) {
        int frame = ((int) anim) % 2;
        BufferedImage img = Assets.sprite(sprite + "_" + frame);
        int w = (int) (16 * (scale / 2.0)) * 2;
        int h = w;
        if (img != null) {
            g.drawImage(img, (int) (x - w / 2.0), (int) (y - h / 2.0), w, h, null);
        } else {
            g.setColor(java.awt.Color.ORANGE);
            g.fillOval((int) (x - w / 2.0), (int) (y - h / 2.0), w, h);
        }
    }
}
