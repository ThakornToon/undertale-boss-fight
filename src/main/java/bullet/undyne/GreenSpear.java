package bullet.undyne;

import bullet.AttackPattern;
import core.EntityManager;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;
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
     * Draw the arrow sprite for this spear (GML: {@code draw_sprite_ext(spr, image_index, x, y)}).
     * Subclasses override {@link #arrowSpriteName()} to select the correct sprite frame.
     */
    @Override
    public void render(Graphics2D g) {
        if (alpha <= 0) {
            return;
        }
        float a = (float) Math.min(1, Math.max(0, alpha));
        BufferedImage img = Assets.sprite(arrowSpriteName());
        if (img == null) {
            return;
        }
        Composite oc = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        g.drawImage(img,
                (int) Math.round(x - img.getWidth() / 2.0),
                (int) Math.round(y - img.getHeight() / 2.0), null);
        g.setComposite(oc);
    }

    /**
     * GML: {@code draw_sprite_ext(spr, image_index, ...)} — which sprite frame to draw.
     * Default uses the white directional spear ({@code spr_bullet_test_*}) based on site.
     * {@link BlockSpear2} overrides to use the yellow feint arrow.
     */
    protected String arrowSpriteName() {
        return switch (site) {
            case 0 -> "spr_bullet_test_l_0";   // from left  (→)
            case 1 -> "spr_bullet_test_r_0";   // from right (←)
            case 2 -> "spr_bullet_test_u_0";   // from below (↑)
            default -> "spr_bullet_test_d_0";  // from above (↓)
        };
    }
}
