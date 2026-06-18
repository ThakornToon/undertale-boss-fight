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
 * A wall of bones that stabs out of one side of the combat box (GML:
 * {@code obj_bonestab}). A red warning rectangle flashes along the wall for
 * {@code warning} frames, then a full-length slab thrusts {@code height} px into
 * the box, jitters in place for the {@code retain} window, and withdraws.
 * Direction follows the GML convention: 0 = from the bottom, 1 = from the right,
 * 2 = from the top, 3 = from the left.
 *
 * // GML: obj_bonestab (spr_s_bonestab_v_wide / spr_s_bonestab_h_tall)
 */
public final class BoneStab extends Bullet {

    public static final int FROM_BOTTOM = 0;
    public static final int FROM_RIGHT = 1;
    public static final int FROM_TOP = 2;
    public static final int FROM_LEFT = 3;

    private final KarmaTicker karma;
    public int dir = FROM_BOTTOM;
    public int warning = 9;
    public double height = 25;
    public int retain = 4;

    /** How far the slab currently protrudes into the box. */
    private double extent;
    private int timer;
    private double racket = 3;
    private double jx;
    private double jy;

    public BoneStab(EntityManager manager, Soul soul, KarmaTicker karma) {
        super(manager, soul);
        this.karma = karma;
        this.dmg = 1;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        timer++;
        if (timer <= warning) {
            return;
        }
        int t = timer - warning;
        if (t <= 3) {
            extent = Math.min(height, extent + height / 3.0);   // thrust in
        } else if (t <= 9 + Math.max(0, retain)) {
            // GML: racket — the slab rattles while held in place.
            jx = Math.random() * racket - Math.random() * racket;
            jy = Math.random() * racket - Math.random() * racket;
            if (racket > 1) {
                racket--;
            }
        } else {
            extent -= height / 4.0;                              // withdraw
            if (extent <= 0) {
                manager.destroy(this);
                return;
            }
        }
        if (extent > 0 && G.inv <= 0 && overlapsSoul()) {
            soul.hurt(dmg);
            karma.addKarma(6);   // GML obj_bonestab: innate_karma = 6
        }
    }

    private boolean overlapsSoul() {
        double[] r = hitRect();
        return soul.x + Soul.HALF > r[0] && soul.x - Soul.HALF < r[2]
                && soul.y + Soul.HALF > r[1] && soul.y - Soul.HALF < r[3];
    }

    /** The protruding slab as [left, top, right, bottom], spanning its whole wall. */
    private double[] hitRect() {
        double[] b = G.idealborder;
        return switch (dir) {
            case FROM_BOTTOM -> new double[] { b[0], b[3] - extent, b[1], b[3] };
            case FROM_RIGHT -> new double[] { b[1] - extent, b[2], b[1], b[3] };
            case FROM_TOP -> new double[] { b[0], b[2], b[1], b[2] + extent };
            default -> new double[] { b[0], b[2], b[0] + extent, b[3] };
        };
    }

    @Override
    protected double boxTop() {
        return hitRect()[1];
    }

    @Override
    protected double boxBottom() {
        return hitRect()[3];
    }

    @Override
    public void render(Graphics2D g) {
        double[] b = G.idealborder;
        if (timer <= warning) {
            // GML: a red outline strip along the threatened wall.
            g.setColor(Color.RED);
            int[] w = switch (dir) {
                case FROM_BOTTOM -> new int[] { (int) b[0] + 8, (int) (b[3] - height), (int) b[1] - 3, (int) b[3] - 3 };
                case FROM_RIGHT -> new int[] { (int) (b[1] - height), (int) b[2] + 8, (int) b[1] - 3, (int) b[3] - 3 };
                case FROM_TOP -> new int[] { (int) b[0] + 8, (int) b[2] + 6, (int) b[1] - 3, (int) (b[2] + 5 + height) };
                default -> new int[] { (int) b[0] + 8, (int) b[2] + 8, (int) (b[0] + 5 + height), (int) b[3] - 3 };
            };
            g.drawRect(w[0], w[1], Math.max(1, w[2] - w[0]), Math.max(1, w[3] - w[1]));
            return;
        }
        if (extent <= 0) {
            return;
        }
        double[] r = hitRect();
        int x0 = (int) (r[0] + jx);
        int y0 = (int) (r[1] + jy);
        int w = (int) (r[2] - r[0]);
        int h = (int) (r[3] - r[1]);
        boolean vertical = dir == FROM_RIGHT || dir == FROM_LEFT;
        BufferedImage img = Assets.sprite(vertical ? "spr_s_bonestab_h_tall_0" : "spr_s_bonestab_v_wide_0");
        if (img != null) {
            // The sprite's spikes face out of its wall; crop to the protrusion.
            int sw = img.getWidth();
            int sh = img.getHeight();
            BufferedImage part = switch (dir) {
                case FROM_BOTTOM -> img.getSubimage(0, 0, sw, Math.min(sh, Math.max(1, h)));
                case FROM_TOP -> img.getSubimage(0, Math.max(0, sh - Math.max(1, h)), sw, Math.min(sh, Math.max(1, h)));
                case FROM_RIGHT -> img.getSubimage(0, 0, Math.min(sw, Math.max(1, w)), sh);
                default -> img.getSubimage(Math.max(0, sw - Math.max(1, w)), 0, Math.min(sw, Math.max(1, w)), sh);
            };
            g.drawImage(part, x0, y0, w, h, null);
            return;
        }
        g.setColor(Color.WHITE);
        g.fillRect(x0, y0, w, h);
    }
}
