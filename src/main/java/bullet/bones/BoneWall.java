package bullet.bones;

import battle.KarmaTicker;
import battle.Soul;
import bullet.Bullet;
import core.EntityManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * The sliding bone-wall slab (GML: {@code obj_bonewall_normal}). The sprite itself
 * is solid — {@code spr_s_bonewall_tall} is 10x200, {@code spr_s_bonewall_wide} is
 * 200x10 — and the "gap" the player escapes through comes from where the GML
 * positions each slab relative to the box, not from the sprite. Slabs slide in from
 * far offscreen on hspeed/vspeed (the a_type 10/11/22 corridors).
 *
 * // GML: obj_bonewall_normal (spr_s_bonewall_tall / spr_s_bonewall_wide)
 */
public final class BoneWall extends Bullet {

    private final KarmaTicker karma;
    /** Slab extent; tall by default, {@link #makeWide()} flips it. */
    public double width = 10;
    public double height = 200;
    private boolean wide;

    public BoneWall(EntityManager manager, Soul soul, KarmaTicker karma) {
        super(manager, soul);
        this.karma = karma;
        this.dmg = 1;
    }

    /** GML: sprite_index = spr_s_bonewall_wide (a horizontal slab). */
    public void makeWide() {
        wide = true;
        width = 200;
        height = 10;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        integrateMotion();
        if (G.inv <= 0
                && soul.x + Soul.HALF > x && soul.x - Soul.HALF < x + width
                && soul.y + Soul.HALF > y && soul.y - Soul.HALF < y + height) {
            soul.hurt(dmg);
            karma.addKarma(6);   // GML obj_bonewall_normal: innate_karma = 6 (parent default)
        }
        if ((x < -1300 && hspeed < 0) || (x > 1900 && hspeed > 0)
                || (y < -1300 && vspeed < 0) || (y > 1900 && vspeed > 0)) {
            manager.destroy(this);
        }
    }

    @Override
    protected double boxTop() {
        return y;
    }

    @Override
    protected double boxBottom() {
        return y + height;
    }

    @Override
    public void render(Graphics2D g) {
        BufferedImage img = Assets.sprite(wide ? "spr_s_bonewall_wide_0" : "spr_s_bonewall_tall_0");
        if (img != null) {
            g.drawImage(img, (int) x, (int) y, (int) width, (int) height, null);
            return;
        }
        g.setColor(Color.WHITE);
        g.fillRect((int) x, (int) y, (int) width, (int) height);
    }
}
