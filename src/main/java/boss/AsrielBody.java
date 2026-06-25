package boss;

import core.EntityManager;
import core.GlobalState;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import util.Assets;
import util.GMLHelper;

/**
 * Asriel's "GOD of Hyperdeath" body (GML: {@code obj_asriel_body}). A faithful port of
 * the multi-part Draw event: feet, torso ball, torso, locket, two arms + shoulders, a
 * collar and the expression head ({@code spr_asrielhead}, sub-image
 * {@code global.faceemotion}) are each drawn at the GML 2× scale at their own offset
 * from the floating body origin, over a small black backing rectangle so the white
 * line-art reads against the cosmic background.
 *
 * <p>The body <b>floats</b>: in idle it bobs gently around its home position; while an
 * attack is being cast it eases up to the fixed cast position ({@code x=320, y=45}) and
 * raises its arms. The per-part PNGs are full-canvas (their pixel size matches the GML
 * sprite size), so the GML origins are used as-is — no bbox subtraction.
 *
 * // GML: obj_asriel_body (Draw event)
 */
public final class AsrielBody extends BossBody {

    private static final GlobalState G = GlobalState.get();
    private static final double SCALE = 2.0;

    // GML home (obj_asrielb Create spawns the body at the controller's position; the
    // spec's "specialnormal" idle bobs around x=315, y=50). Cast pose eases to (320,45).
    static final double HOME_X = 320;
    static final double HOME_Y = 52;
    private static final double CAST_X = 320;
    private static final double CAST_Y = 45;

    // ---- GML body-part draw table: sprite, origin (full-canvas, used as-is) --------
    // Offsets/positions are applied per-part in render() to match the GML formulas.

    private double siner;
    private double rely;          // GML rely: vertical bob the parts share
    private double armrotL;       // GML armrot_l / armrot_r — arm sway / raise
    private double armrotR;
    private float imageAlpha = 1f;

    // GML swordmaster.x move + king.torsorot: Chaos Saber leans the whole body opposite the
    // slashing arm (set each frame by ChaosSaberGen).
    private double leanX;
    private double leanTilt;

    // An arm is HIDDEN when a sword-arm / gun-arm replaces it (so they don't overlap):
    // Chaos Saber hides both, Chaos Buster hides the right.
    private boolean hideLeftArm;
    private boolean hideRightArm;

    /** True while an attack is being cast: ease to the cast pose and raise the arms. */
    private boolean casting;
    /** Whether the cast pose raises the arms (false for Chaos Saber — swords take over). */
    private boolean raiseArms = true;

    public AsrielBody(EntityManager manager) {
        super(manager);
        this.depth = 1100;        // behind the combat box (box depth 1000)
        this.x = HOME_X;
        this.y = HOME_Y;
    }

    /** Begin an attack cast: float up to the cast position with arms raised. */
    public void startCast() {
        startCast(true);
    }

    /** Cast with control over the arms — Chaos Saber keeps them down (swords take over). */
    public void startCast(boolean raiseArms) {
        casting = true;
        this.raiseArms = raiseArms;
    }

    /** Attack over: drift back to the idle bob and restore both arms. */
    public void endCast() {
        casting = false;
        hideLeftArm = false;
        hideRightArm = false;
        leanX = 0;
        leanTilt = 0;
    }

    /**
     * Hide the body arm(s) that a sword-arm / gun-arm replaces, so they don't draw on top of
     * each other. {@code leftVisible}/{@code rightVisible} are screen-side (right = gun side).
     */
    public void setArmVisibility(boolean leftVisible, boolean rightVisible) {
        hideLeftArm = !leftVisible;
        hideRightArm = !rightVisible;
    }

    public void setAlpha(float a) {
        imageAlpha = Math.max(0f, Math.min(1f, a));
    }

    /** GML swordmaster.x / king.torsorot: lean the whole body (and tilt the head) for a slash. */
    public void setLean(double dx, double tilt) {
        leanX = dx;
        leanTilt = tilt;
    }

    /** The current horizontal lean, so the sword-arms hover with the leaning body. */
    public double leanX() {
        return leanX;
    }

    @Override
    public void update() {
        siner++;
        if (casting) {
            // Ease to the fixed cast pose and raise the arms outward/up.
            x = GMLHelper.approach(x, CAST_X, 2.5);
            y = GMLHelper.approach(y, CAST_Y, 2.5);
            rely *= 0.7;
            armrotL = GMLHelper.approach(armrotL, raiseArms ? 38 : 0, 4);
            armrotR = GMLHelper.approach(armrotR, raiseArms ? -38 : 0, 4);
        } else {
            // GML idle: gentle float — bob vertically and sway a touch horizontally.
            double bx = HOME_X + Math.sin(siner / 24.0) * 6;
            double by = HOME_Y + Math.sin(siner / 8.0) * 4;
            x = GMLHelper.approach(x, bx, 3.0);
            y = GMLHelper.approach(y, by, 3.0);
            rely = Math.sin(siner / 12.0) * 2;
            armrotL = GMLHelper.approach(armrotL, 0, 3);
            armrotR = GMLHelper.approach(armrotR, 0, 3);
        }
    }

    @Override
    public void render(Graphics2D g) {
        double yoff = Math.sin(siner / 6.0);
        double bx = x + leanX;            // GML body lean during a Chaos Saber slash
        Composite oldC = pushAlpha(g, imageAlpha);

        // GML: a black backing rectangle behind the torso so the line-art reads.
        g.setColor(Color.BLACK);
        g.fillRect((int) (bx - 40), (int) (y + 20 + rely), 82, 26);

        // GML draw order (back to front): feet, torso ball, torso, locket, arms,
        // shoulders, collar, head. Each part is full-canvas → origin used as-is.
        // GML spr_asrielfeet has yorigin=-58, so the feet draw well BELOW the draw
        // position (poking out at the bottom of the robe). Passing oy=0 hid them entirely
        // behind the torso — the feet must use the real negative origin.
        part(g, "spr_asrielfeet_0",      bx + yoff * 2, y + 56 + rely * 0.9, SCALE, SCALE, 0, 15, -58);
        part(g, "spr_torsoball_0",       bx + yoff,     y + 48 + rely,       SCALE, SCALE, 0, 20, 2);
        part(g, "spr_asrieltorso_0",     bx + yoff,     y + 48 + rely,       SCALE, SCALE, leanTilt * 0.5, 20, 2);
        part(g, "spr_asriellocket_0",    bx + 2,        y + 34 + rely * 1.2, SCALE, SCALE, leanTilt * 0.5, 17, 3);
        // Arms (left copy is mirrored: negative x-scale). They raise while casting; a hidden
        // arm is the one a sword-arm / gun-arm has taken over.
        if (!hideLeftArm) {
            part(g, "spr_asrielarm_r_0",  bx - 28,       y + 38 + rely * 1.2, -SCALE, SCALE, armrotL, 0, 0);
        }
        if (!hideRightArm) {
            part(g, "spr_asrielarm_r_0",  bx + 30,       y + 38 + rely * 1.2, SCALE,  SCALE, armrotR, 0, 0);
        }
        part(g, "spr_asrielshoulder_r_0", bx - 28,      y + 26 + rely * 1.2, -SCALE, SCALE, 0, 0, 0);
        part(g, "spr_asrielshoulder_r_0", bx + 30,      y + 26 + rely * 1.2, SCALE,  SCALE, 0, 0, 0);
        part(g, "spr_asrielcollar_0",    bx,            y + 22 + rely,       SCALE, SCALE, leanTilt * 0.5, 24, 8);
        int face = GMLHelper.clamp(G.faceemotion, 0, 4);
        part(g, "spr_asrielhead_" + face, bx,           y + rely * 1.2,      SCALE, SCALE, leanTilt, 16, 18);

        popAlpha(g, oldC);
    }

    /**
     * GML {@code draw_sprite_ext}: the sprite origin maps to {@code (x,y)}, scaled
     * (negative x mirrors) and rotated about the origin. GML angles are CCW so the Java
     * rotation is negated, matching {@link AsgoreBody}'s draw.
     */
    private void part(Graphics2D g, String sprite, double px, double py,
                      double xscale, double yscale, double gmlAngle, int ox, int oy) {
        BufferedImage img = Assets.sprite(sprite);
        if (img == null) {
            return;
        }
        AffineTransform old = g.getTransform();
        g.translate(px, py);
        g.rotate(-Math.toRadians(gmlAngle));
        g.scale(xscale, yscale);
        g.drawImage(img, -ox, -oy, null);
        g.setTransform(old);
    }

    private static Composite pushAlpha(Graphics2D g, float a) {
        if (a >= 1f) {
            return null;
        }
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, a)));
        return old;
    }

    private static void popAlpha(Graphics2D g, Composite old) {
        if (old != null) {
            g.setComposite(old);
        }
    }
}
