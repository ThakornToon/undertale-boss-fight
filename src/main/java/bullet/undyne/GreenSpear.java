package bullet.undyne;

import bullet.AttackPattern;
import core.EntityManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import util.GMLHelper;

/**
 * Base for the GREEN-soul "block" spears (GML: {@code obj_blockbullet} /
 * {@code obj_blockbullet2}). These fly inward toward the shielded heart from one of
 * four sides; the {@link SpearBlocker} owns the collision (a spear is blocked if the
 * shield faces it, and damages the player only if it reaches the centre unblocked),
 * so these classes are pure movers that fade in and report their {@code site}.
 *
 * <p>{@code site}: 0 = from the left, 1 = from the right, 2 = from below, 3 = from
 * above (GML {@code obj_blockbullet} positions: {@code object0.x ∓ 300} /
 * {@code object0.y ± 300}).
 *
 * // GML: obj_blockbullet (parent of obj_blockbullet2)
 */
public abstract class GreenSpear extends AttackPattern {

    /** GML: which side the spear approaches from (0 L · 1 R · 2 below · 3 above). */
    public int site;
    /** GML: speedmod — per-spear speed multiplier (base inbound speed is 8). */
    public double speedmod = 1;
    /**
     * Distance from the centre this spear spawns at (GML's fixed 300). The
     * {@link GreenSpearGen} staggers it outward when another spear is already
     * approaching from the same side, so same-side spears form a visible train
     * instead of stacking exactly on top of one another.
     */
    public double spawnDist = 300;
    /** The shielded heart's location (GML: object0 = obj_spearblocker at box centre). */
    protected double cx;
    protected double cy;
    /** GML: image_alpha ramps 0→1 as the spear appears, and back down once blocked. */
    protected double alpha;
    /** Set by {@link SpearBlocker} once this spear is blocked — it then fades out. */
    public boolean blocked;
    /** True once the spear has been positioned at its spawn edge (alarm[0] = 1). */
    protected boolean placed;
    /** Arrow colour: white for the normal spear, yellow for the special feint. */
    protected Color arrowColor = Color.WHITE;
    /**
     * If set (not NaN), the arrow head is drawn pointing this fixed GML angle instead
     * of toward the centre — the yellow feint's head shows the side it FINALLY comes
     * from, not its current motion (GML {@code obj_blockbullet2} image_index/truesite).
     */
    protected double fixedArrowAngle = Double.NaN;
    /** The blue end-of-turn finisher spear: drawn bigger, in cyan. */
    public boolean finisher;

    /**
     * The side the player must face to block this spear (0 left · 1 right · 2 below ·
     * 3 above). For a straight spear it is simply where it is; the feint overrides it
     * to a fixed side that is the opposite of where the arrow head points.
     */
    public int blockSide() {
        return threatSide();
    }

    protected GreenSpear(EntityManager manager, double cx, double cy, int site, double speedmod) {
        super(manager);
        this.cx = cx;
        this.cy = cy;
        this.site = site;
        this.speedmod = speedmod;
        this.dmg = 7;
        this.depth = -1000;
    }

    /** GML obj_blockbullet alarm[0]: snap to the spawn edge, then move toward centre. */
    protected void place() {
        switch (site) {
            case 0 -> { x = cx - spawnDist; y = cy; }
            case 1 -> { x = cx + spawnDist; y = cy; }
            case 2 -> { x = cx; y = cy + spawnDist; }
            default -> { x = cx; y = cy - spawnDist; }
        }
        placed = true;
    }

    /** Current distance from the shielded heart. */
    public double distanceToCenter() {
        return GMLHelper.point_distance(x, y, cx, cy);
    }

    /**
     * Which cardinal side the spear currently threatens from (the arrow the player
     * must face to block it). Robust for the weaving {@link BlockSpear2} too: it is
     * the dominant-axis direction from the heart to the spear.
     */
    public int threatSide() {
        double dx = x - cx;
        double dy = y - cy;
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx < 0 ? 0 : 1;    // left / right
        }
        return dy > 0 ? 2 : 3;        // below / above
    }

    /** GML: the spear was blocked — begin the fade-out. */
    public void block() {
        blocked = true;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        if (!placed) {
            place();
        }
        if (blocked) {
            alpha -= 0.1;
            if (alpha <= 0) {
                manager.destroy(this);
            }
            return;
        }
        if (alpha < 1) {
            alpha += 0.2;
        }
        step();
    }

    /** Per-frame motion (straight or weaving). */
    protected abstract void step();

    /**
     * Shared draw: a small, short arrow pointing inward (its head shows the side you
     * must block). The normal spear is white; the special feint ({@link BlockSpear2})
     * is yellow so the two read as the reference's "two colours".
     */
    @Override
    public void render(Graphics2D g) {
        if (alpha <= 0) {
            return;
        }
        float a = (float) Math.min(1, Math.max(0, alpha));
        AffineTransform old = g.getTransform();
        Stroke os = g.getStroke();

        if (finisher) {
            // The blue finisher: a longer cyan spear pointing along its travel.
            double ang = GMLHelper.point_direction(x, y, cx, cy);
            g.translate(x, y);
            g.rotate(-Math.toRadians(ang));
            g.setColor(new Color(0x40 / 255f, 0xC0 / 255f, 1f, a));
            g.setStroke(new BasicStroke(5f));
            g.drawLine(-22, 0, 14, 0);
            g.fillPolygon(new int[] { 12, 30, 12 }, new int[] { -8, 0, 8 }, 3);
            g.setStroke(os);
            g.setTransform(old);
            return;
        }

        // The head points toward the centre, unless a fixed "final direction" is set.
        double ang = Double.isNaN(fixedArrowAngle)
                ? GMLHelper.point_direction(x, y, cx, cy) : fixedArrowAngle;
        g.translate(x, y);
        g.rotate(-Math.toRadians(ang));
        Color c = arrowColor;
        g.setColor(new Color(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, a));
        g.setStroke(new BasicStroke(4f));
        g.drawLine(-7, 0, 4, 0);                                       // short shaft
        g.fillPolygon(new int[] { 3, 13, 3 }, new int[] { -6, 0, 6 }, 3); // chevron head
        g.setStroke(os);
        g.setTransform(old);
    }
}
