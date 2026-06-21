package bullet.toriel;

import battle.Soul;
import bullet.Bullet;
import core.EntityManager;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * Base for Toriel's fire-bullet projectiles (GML: the {@code spr_firebullet}
 * objects — {@code blt_firehelix1}, {@code blt_minihelix}, {@code blt_floatfire},
 * {@code blt_chasefire1/2}). A fire is a small two-frame point sprite, so this
 * overrides {@link Bullet}'s tall-column hitbox with a centred square box and draws
 * the flicker animation. Subclasses supply only the per-frame motion in
 * {@link #move()}.
 *
 * <p>Toriel's flames are the SMALL 16px {@code spr_firebullet} (the helix/mini fires
 * never set {@code image_xscale} in GML), half the size of Asgore's 2× fire dots —
 * which is why a dense sine column still leaves gaps to weave through.
 *
 * // GML: blt_parent fire children (spr_firebullet)
 */
public abstract class TorielFire extends Bullet {

    /** GML: image_xscale/yscale. Toriel's fire is drawn at 1× (16px) by default. */
    public double scale = 1;
    /** GML: image_index — alternates the two flame frames at image_speed 0.5. */
    protected double anim;

    protected TorielFire(EntityManager manager, Soul soul) {
        super(manager, soul);
        this.dmg = 6;            // overwritten by the spawner to global.monsteratk
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
        anim += 0.25;           // GML image_speed 0.5 (two frames)
        if (collidesSoul()) {
            onHitSoul();
            manager.destroy(this);   // GML: fire destroys itself on contact
        }
        cullFarOffscreen();
    }

    /** Half-extent of the (square) flame hitbox — a touch tighter than the sprite. */
    private double half() {
        return 5 * scale;
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

    /** Destroy once well outside the play field (subclasses also self-cull tighter). */
    private void cullFarOffscreen() {
        if (x < -120 || x > 760 || y < -200 || y > 640) {
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
        BufferedImage img = Assets.sprite("spr_firebullet_" + frame);
        int w = (int) (16 * scale);
        if (img != null) {
            g.drawImage(img, (int) (x - w / 2.0), (int) (y - w / 2.0), w, w, null);
        } else {
            g.setColor(java.awt.Color.ORANGE);
            g.fillOval((int) (x - w / 2.0), (int) (y - w / 2.0), w, w);
        }
    }
}
