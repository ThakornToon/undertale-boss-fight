package battle;

import core.Entity;
import core.EntityManager;
import core.GlobalState;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import util.GMLHelper;

/**
 * The combat box (GML: the {@code idealborder} / {@code obj_borderparent}
 * system). Holds a target rectangle and an animated current rectangle that lerps
 * toward it, so bosses can shrink, slide, and snap the box live. Each frame it
 * writes the animated bounds back into {@link GlobalState#idealborder} — the
 * single source the soul and every bullet read.
 *
 * // GML: obj_borderparent + global.idealborder[0..3] + instaborder
 */
public final class BulletBoard extends Entity {

    private static final GlobalState G = GlobalState.get();

    /** The combat box [left, right, top, bottom] the boss configures. */
    private final double[] ideal = new double[4];
    /** Animated current box. */
    private final double[] cur = new double[4];

    /**
     * GML: during the player command turn the box widens into the dialogue/menu
     * box (the wide frame that holds the FIGHT/ACT flavor text and option list).
     * When {@link #menuMode} is on the box animates to this shape instead of the
     * boss's combat box.
     */
    private static final double[] MENU_BOX = { 40, 600, 250, 385 };
    public boolean menuMode;

    /**
     * GML {@code blt_coolbus}: while the Papyrus super-bone climb is active, the box
     * top is driven externally — the ceiling follows the soul up so it can scale a
     * bone far taller than the resting box. {@code < 0} = not climbing (normal lerp).
     */
    public double climbTop = -1;

    /** GML: global.border preset index. */
    public int preset;
    /** GML: instaborder — snap instead of animate next update. */
    public boolean instaSnap;
    /** How fast cur approaches ideal (fraction per frame). */
    public double lerpSpeed = 0.25;

    public boolean visible = true;
    public boolean solid = true;
    /**
     * Cosmetic rock of the whole box (degrees), used by Muffet's pet special to
     * tilt the "pulled-paper" web left/right. Visual only — the logical idealborder
     * the soul/bullets read stays axis-aligned, exactly as GML draws the live heart
     * on top of the rocking paper snapshot.
     */
    public double tilt;
    /**
     * GML obj_borderparent.image_alpha — the box frame dims during the darkify
     * (Undyne's green/red attack turns fade the field to gray). 1 = full white.
     */
    public float borderAlpha = 1f;

    public BulletBoard(EntityManager manager) {
        super(manager);
        this.depth = 1000; // draw behind everything
        setPreset(0, true);
    }

    /** GML: global.border = p; SCR_BORDERSETUP. */
    public void setPreset(int p, boolean snap) {
        this.preset = p;
        G.border = p;
        BorderSetup.apply(p, ideal);
        if (snap) {
            System.arraycopy(ideal, 0, cur, 0, 4);
            writeGlobals();
        }
    }

    public void setPreset(int p) {
        setPreset(p, false);
    }

    /** GML: obj_borderparent.instaborder — set the target and snap to it. */
    public void instaBorder(double left, double right, double top, double bottom) {
        ideal[0] = left;
        ideal[1] = right;
        ideal[2] = top;
        ideal[3] = bottom;
        System.arraycopy(ideal, 0, cur, 0, 4);
        writeGlobals();
    }

    /** Animate toward a new explicit target box. */
    public void slide(double left, double right, double top, double bottom) {
        ideal[0] = left;
        ideal[1] = right;
        ideal[2] = top;
        ideal[3] = bottom;
    }

    /** Animate the box to a smaller centered region. */
    public void shrinkTo(double left, double right, double top, double bottom) {
        slide(left, right, top, bottom);
    }

    /**
     * Leave menu mode and snap the box straight to the boss's combat box now, so
     * an attack spawned this very frame reads combat-box bounds (not the wide menu
     * box that was animating in). Called as the enemy turn begins.
     */
    public void snapToCombat() {
        menuMode = false;
        System.arraycopy(ideal, 0, cur, 0, 4);
        writeGlobals();
    }

    @Override
    public void update() {
        // The wide menu box during the command turn, the combat box otherwise.
        double[] target = menuMode ? MENU_BOX : ideal;
        if (instaSnap) {
            System.arraycopy(target, 0, cur, 0, 4);
            instaSnap = false;
        } else {
            for (int i = 0; i < 4; i++) {
                cur[i] = GMLHelper.approach(cur[i], target[i],
                        Math.max(1.0, Math.abs(target[i] - cur[i]) * lerpSpeed));
            }
        }
        // The super-bone climb overrides the top edge directly (snaps, no lerp), so
        // the ceiling tracks the soul up frame-for-frame as it scales the bone.
        if (climbTop >= 0) {
            cur[2] = climbTop;
        }
        writeGlobals();
    }

    private void writeGlobals() {
        System.arraycopy(cur, 0, G.idealborder, 0, 4);
    }

    public double left() {
        return cur[0];
    }

    public double right() {
        return cur[1];
    }

    public double top() {
        return cur[2];
    }

    public double bottom() {
        return cur[3];
    }

    @Override
    public void render(Graphics2D g) {
        if (!visible) {
            return;
        }
        int x = (int) cur[0];
        int y = (int) cur[2];
        int w = (int) (cur[1] - cur[0]);
        int h = (int) (cur[3] - cur[2]);
        java.awt.geom.AffineTransform oldTx = null;
        if (tilt != 0) {
            oldTx = g.getTransform();
            g.rotate(Math.toRadians(tilt), x + w / 2.0, y + h / 2.0);
        }
        // GML: the combat box has a solid black interior. The boss body draws behind
        // the box (higher depth), so this fill is what occludes Asgore's lower body —
        // without it the box is a see-through outline and you can see him through it.
        g.setColor(Color.BLACK);
        g.fillRect(x, y, w, h);
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(5f));
        float a = Math.max(0f, Math.min(1f, borderAlpha));
        g.setColor(new Color(a, a, a)); // white dimmed to gray during darkify
        g.drawRect(x, y, w, h);
        g.setStroke(old);
        if (oldTx != null) {
            g.setTransform(oldTx);
        }
    }
}
