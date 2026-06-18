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
 * Sans's standard bone (GML: {@code obj_sans_bonebul}, spawned via
 * {@code scr_sbo(height, hspeed, xfactor, type)}). A 10px-wide column anchored to
 * the combat box: type 0 rises from the floor up to its spawn {@code y}; type 1 is
 * the same shape flagged blue (only hurts a <em>moving</em> soul); type 2 hangs
 * from the ceiling down to {@code y}. The column is clipped horizontally to the
 * box, like the GML's draw_sprite_part width logic.
 *
 * // GML: obj_sans_bonebul + scr_sbo.gml
 */
public final class SansBone extends Bullet {

    public static final int BOTTOM = 0;
    public static final int TALL_BLUE = 1;
    public static final int TOP = 2;

    // The in-game blue attack reads bright cyan, not the raw GML blend value.
    private static final Color BLUE_TINT = new Color(0x3F, 0xC1, 0xF5);

    private final KarmaTicker karma;
    public int type;
    /** GML: z_a / z_b / siner — optional sine wobble around the spawn height. */
    public double zA;
    public double zB;
    public double siner;
    private double yinit;

    public SansBone(EntityManager manager, Soul soul, KarmaTicker karma, int type) {
        super(manager, soul);
        this.karma = karma;
        this.type = type;
        this.blue = (type == TALL_BLUE);
        this.dmg = 1;
    }

    /** GML: scr_sbo(height, hspeed, xfactor, type) — spawn relative to the floor. */
    public static SansBone sbo(EntityManager manager, Soul soul, KarmaTicker karma,
                               double height, double hspeed, double xfactor, int type) {
        SansBone b = new SansBone(manager, soul, karma, type);
        b.y = G.idealborder[3] - height;
        b.yinit = b.y;
        b.hspeed = hspeed;
        b.x = 320 - hspeed * xfactor;
        manager.add(b);
        return b;
    }

    /** Keep the wobble centred where the bone currently sits (after x/y tweaks). */
    public void rebase() {
        yinit = y;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        integrateMotion();
        siner++;
        if (zA != 0) {
            y = yinit + Math.sin(siner * zB) * zA;
        }
        if (overlapsSoul()) {
            hitSoul();
        }
        if ((x < -60 && hspeed < 0) || (x > 700 && hspeed > 0)) {
            manager.destroy(this);
        }
    }

    private boolean overlapsSoul() {
        if (G.inv > 0) {
            return false;
        }
        // GML clips the column to the box (the le/rc_cut width logic).
        double l = Math.max(x + 2, G.idealborder[0]);
        double r = Math.min(x + 8, G.idealborder[1]);
        if (r <= l) {
            return false;
        }
        double top = (type == TOP) ? G.idealborder[2] + 6 : y + 5;
        double bottom = (type == TOP) ? y : G.idealborder[3] - 6;
        return soul.x + Soul.HALF > l && soul.x - Soul.HALF < r
                && soul.y + Soul.HALF > top && soul.y - Soul.HALF < bottom;
    }

    private void hitSoul() {
        if (blue && !soulMoving()) {
            return; // blue bone: holding still is safe
        }
        soul.hurt(dmg);
        karma.addKarma(6);   // GML obj_sans_bonebul: innate_karma = 6
    }

    @Override
    protected double boxTop() {
        return (type == TOP) ? G.idealborder[2] : y;
    }

    @Override
    protected double boxBottom() {
        return (type == TOP) ? y : G.idealborder[3];
    }

    @Override
    public void render(Graphics2D g) {
        double l = Math.max(x, G.idealborder[0] - 5);
        double r = Math.min(x + 10, G.idealborder[1] + 5);
        if (r <= l) {
            return;
        }
        int top;
        int bottom;
        if (type == TOP) {
            top = (int) G.idealborder[2] + 5;
            bottom = (int) y;
        } else {
            top = (int) y;
            bottom = (int) G.idealborder[3] - 5;
        }
        if (bottom <= top) {
            return;
        }
        g.setColor(blue ? BLUE_TINT : Color.WHITE);
        g.fillRect((int) l + 2, top + 5, (int) (r - l) - 4, Math.max(0, bottom - top - 10));
        // Rounded tip on the free end; the other end disappears into the border.
        if (type == TOP) {
            drawCap(g, "spr_s_bonebul_bottom_0", (int) l, bottom - 6);
        } else {
            drawCap(g, "spr_s_bonebul_top_0", (int) l, top);
        }
    }

    private void drawCap(Graphics2D g, String sprite, int sx, int sy) {
        if (sprite == null) {
            return;
        }
        if (blue) {
            // A blue bone is blue end to end (no white cap sprite exists for it).
            g.setColor(BLUE_TINT);
            g.fillRoundRect(sx, sy, 10, 6, 6, 6);
            return;
        }
        BufferedImage img = Assets.sprite(sprite);
        if (img != null) {
            g.drawImage(img, sx, sy, 10, 6, null);
        }
    }
}
