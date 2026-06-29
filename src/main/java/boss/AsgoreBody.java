package boss;

import core.EntityManager;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import util.Assets;
import util.GMLHelper;

/**
 * Asgore's body (GML: {@code obj_asgoreb_body} + {@code obj_asgorespear}). This is a
 * faithful port of the GML draw code so the King, his red trident, the two gripping
 * hands and the ball-jointed arms line up exactly as in the fight — the previous
 * version hand-placed the trident with guessed offsets, which is why it sat crooked.
 *
 * <p>Everything is composed at the GML scale (2×) at the GML world position
 * ({@code obj_asgoreb} spawns at (208, 8); the spear hangs at body+{@code (76,100)}),
 * so the silhouette and the combat box (border 29/30) align with the real game. The
 * body draws <em>behind</em> the box (high depth); the box's black interior occludes
 * his lower body.
 *
 * <p>The body has three poses: the eight-part <b>standing</b> King with the trident
 * (default), the arms-spread <b>calm</b> intro pose ({@code spr_asgore_prebrandish} +
 * {@code spr_asgore_bface}, shown during the opening cutscene), and the
 * <b>kneeling</b> end pose. While {@link #swiping} the trident-swipe attack
 * ({@link bullet.asgore.SpearSwipe}) draws the swing sprite itself, in front of the
 * box, so the body draws nothing.
 *
 * // GML: obj_asgoreb_body + obj_asgorespear
 */
public final class AsgoreBody extends BossBody {

    // ---- GML world layout (obj_asgoreb spawns at (208,8); see obj_asgoreb Create) --
    private static final int BODY_X = 208;
    private static final int BODY_Y = 8;
    private static final double SCALE = 2.0;          // GML draws every part at 2×.
    /** GML spear position = body + (76, 100). */
    private static final int SPEAR_X = BODY_X + 76;    // 284
    private static final double SPEAR_Y0 = BODY_Y + 100; // 108

    // GML obj_asgoreb_body part offsets [cape, feet, legs, dress, armr, arml, armor, head].
    private static final String[] PART = {
        "spr_asgoreb_cape_0", "spr_asgoreb_feet_0", "spr_asgoreb_legs_0",
        "spr_asgoreb_dress_0", "spr_asgoreb_armr_0", "spr_asgoreb_arml_0",
        "spr_asgoreb_armor_0", "spr_asgoreb_head1_0",
    };
    private static final int[] PART_X = { -48, 32, 54, 50, 168, 16, 0, 68 };
    private static final double[] PARTY_BASE = { 62, 210, 156, 130, 70, 70, 28, 0 };

    /** The trident, tinted red from the white line-art sprite, built once. */
    private static BufferedImage redSpear;

    private double siner;
    /** GML party[]: per-part vertical offsets that bob over time. */
    private final double[] party = PARTY_BASE.clone();
    /** GML obj_asgorespear: the trident's bobbing y and wobbling angle. */
    private double spearY = SPEAR_Y0;
    private double spearAngle = -25;       // GML: angle = -25 (25° clockwise on screen).

    // ---- Swing takeover (driven by SpearSwipe; the swipe draws the body itself) ----
    public boolean swiping;

    // ---- Calm intro pose (spr_asgore_prebrandish + spr_asgore_bface) ---------------
    private boolean calm;
    private int calmFace;
    private static final int CALM_X = 150;   // centres the 170-wide pose on x=320 at 2×.
    private static final int CALM_Y = 10;

    // ---- Endgame kneel cutscene ----------------------------------------------------
    private boolean kneeling;
    private boolean kneelFromCalm;     // what to cross-fade out of
    private double kneelFade;           // 0 → 1
    /** GML lastface frame: 9 = sad (neutral fivedamage), 3 = genocide. */
    public int kneelFace = 9;
    private static final int KNEEL_X = 128;
    private static final int KNEEL_Y = 46;

    public AsgoreBody(EntityManager manager) {
        super(manager);
        this.depth = 1100;        // behind the combat box (box depth 1000)
    }

    /** Opening cutscene: the arms-spread peaceful pose with the given face frame. */
    public void showCalm(int face) {
        calm = true;
        calmFace = face;
        kneeling = false;
        swiping = false;
    }

    /** Cut to the battle stance (eight-part King holding the trident). */
    public void showBattle() {
        calm = false;
    }

    /** GML fivedamage / genocide: drop to the kneeling sprite, cross-fading in. */
    public void startKneel() {
        kneeling = true;
        kneelFromCalm = calm;
        swiping = false;
    }

    public boolean kneelComplete() {
        return kneeling && kneelFade >= 1.0;
    }

    @Override
    public void update() {
        siner++;
        // GML obj_asgoreb_body Step: each part bobs on its own sine/cosine.
        double s = Math.sin(siner / 15.0);
        double c = Math.cos(siner / 15.0);
        party[7] += s * 0.3;   // head
        party[6] += s * 0.2;   // armor
        party[5] += c * 0.1;   // arml
        party[4] += c * 0.1;   // armr
        party[3] += s * 0.1;   // dress
        party[0] += s * 0.05;  // cape
        // GML obj_asgorespear Step: slow vertical bob + angle wobble.
        spearY += s * 0.3;
        spearAngle += s * 0.02;
        if (kneeling && kneelFade < 1.0) {
            kneelFade = Math.min(1.0, kneelFade + 0.05);
        }
    }

    @Override
    public void render(Graphics2D g) {
        if (kneeling) {
            renderKneel(g);
            return;
        }
        if (swiping) {
            return;            // SpearSwipe draws the swing in front of the box.
        }
        if (calm) {
            renderCalm(g, 1f);
            return;
        }
        renderStanding(g, 1f);
        renderTrident(g, 1f);
    }

    // ---- Standing King (eight parts) ----------------------------------------------

    private void renderStanding(Graphics2D g, float alpha) {
        Composite old = setAlpha(g, alpha);
        for (int i = 0; i < PART.length; i++) {
            BufferedImage img = Assets.sprite(PART[i]);
            if (img == null) {
                continue;
            }
            int px = (int) (BODY_X + PART_X[i]);
            int py = (int) (BODY_Y + party[i]);
            g.drawImage(img, px, py,
                    (int) (img.getWidth() * SCALE), (int) (img.getHeight() * SCALE), null);
        }
        restore(g, old);
    }

    // ---- The trident + hands + ball-arms (GML obj_asgorespear, exact port) ---------

    private void renderTrident(Graphics2D g, float alpha) {
        BufferedImage spear = tintedSpear();
        if (spear == null) {
            return;
        }
        Composite old = setAlpha(g, alpha);

        double angle = spearAngle;                 // GML degrees (0 = right, CCW +).
        double sx = SPEAR_X;
        double sy = spearY;
        // GML: hand offsets along the shaft (left hand near the butt, right near the prongs).
        double xh = GMLHelper.lengthdir_x(55, angle);
        double yh = GMLHelper.lengthdir_y(55, angle);
        double rdistx = sx + xh * 2;
        double rdisty = sy + yh * 2;

        // GML armtest: the two ball-jointed arms reaching from the shoulders to the grips.
        // Left arm: from the arml shoulder to the butt-grip.
        double lpx = PART_X[5] + 14 + BODY_X;      // 238
        double lpy = party[5] + 64 + BODY_Y;
        drawBallArm(g, lpx, lpy, sx - xh, sy - yh, 0.35);

        // Right arm: from the armr shoulder to the prong-grip (with GML's reach clamp).
        double rpx = PART_X[4] + 34 + BODY_X;      // 410
        double rpy = party[4] + 64 + BODY_Y;
        double rArmLen = GMLHelper.point_distance(rpx, rpy, rdistx, rdisty);
        if (rArmLen > 100) {
            double armoff = (rArmLen - 100) / 2;
            rdistx = sx + GMLHelper.lengthdir_x(55 - armoff, angle) * 2;
            rdisty = sy + GMLHelper.lengthdir_y(55 - armoff, angle) * 2;
        }
        double rArmAngle = GMLHelper.point_direction(rpx, rpy, rdistx, rdisty);
        if (rArmAngle > 100) {
            rpy -= 12;
        }
        drawBallArm(g, rpx, rpy, rdistx, rdisty, 0.6);

        // GML draw order: trident (red), then the right hand, then the left hand.
        // Each is drawn at its true sprite origin so it rotates about the grip, the
        // way GML's draw_sprite_ext pivots on (xorig,yorigin) — not the top-left.
        // spr_asgorespear: GML origin (60,31), but the PNG is trimmed 7px off the top
        // (gmx height 47 < bbox_bottom 53, bbox_top 7), so the real PNG origin is
        // (60, 31-7)=(60,24) — this is the shaft centerline, so the hands grip the
        // shaft instead of dangling below it. spr_spearhand{r,l} are untrimmed (10,7).
        drawSpriteExt(g, spear, sx, sy, SCALE, SCALE, angle, 60, 24);
        BufferedImage hr = Assets.sprite("spr_spearhandr_0");
        BufferedImage hl = Assets.sprite("spr_spearhandl_0");
        if (hr != null) {
            drawSpriteExt(g, hr, rdistx, rdisty, SCALE, SCALE, angle, 10, 7);
        }
        if (hl != null) {
            drawSpriteExt(g, hl, sx - xh, sy - yh, SCALE, SCALE, angle, 10, 7);
        }
        restore(g, old);
    }

    /** GML: stretch the 20×20 ball-arm sprite from {@code (px,py)} to the grip. */
    private void drawBallArm(Graphics2D g, double px, double py,
                             double tx, double ty, double minSize) {
        BufferedImage arm = Assets.sprite("spr_asgoreb_ballarm_0");
        if (arm == null) {
            return;
        }
        double len = GMLHelper.point_distance(px, py, tx, ty);
        double size = len / 40.0;        // GML: armsize = armlength / 40 (sprite is 20px → 40px at 2×).
        if (size < minSize) {
            return;                       // GML: armsize < min → 0 (arm hidden).
        }
        double ang = GMLHelper.point_direction(px, py, tx, ty);
        // GML draws the ball-arm at its origin (0,15): pivot on the shoulder end,
        // vertically centred on the joint, so the stretched arm meets the grip.
        drawSpriteExt(g, arm, px, py, size * 2, 2, ang, 0, 15);
    }

    /**
     * GML {@code draw_sprite_ext(img, x, y, xscale, yscale, gmlAngle, ...)}: the
     * sprite origin maps to {@code (x,y)}, scaled and rotated. GML angles are CCW
     * (0 = right, 90 = up), so the Java rotation is negated.
     */
    private void drawSpriteExt(Graphics2D g, BufferedImage img, double x, double y,
                               double xscale, double yscale, double gmlAngle,
                               int ox, int oy) {
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.rotate(-Math.toRadians(gmlAngle));
        g.scale(xscale, yscale);
        g.drawImage(img, -ox, -oy, null);
        g.setTransform(old);
    }

    // ---- Calm intro pose -----------------------------------------------------------

    private void renderCalm(Graphics2D g, float alpha) {
        Composite old = setAlpha(g, alpha);
        BufferedImage pre = Assets.sprite("spr_asgore_prebrandish_0");
        if (pre != null) {
            g.drawImage(pre, CALM_X, CALM_Y,
                    (int) (pre.getWidth() * SCALE), (int) (pre.getHeight() * SCALE), null);
        }
        BufferedImage face = Assets.sprite("spr_asgore_bface_" + calmFace);
        if (face != null) {
            // GML: face at body + (138, -12).
            g.drawImage(face, CALM_X + 138, CALM_Y - 12,
                    (int) (face.getWidth() * SCALE), (int) (face.getHeight() * SCALE), null);
        }
        restore(g, old);
    }

    // ---- Kneel ending --------------------------------------------------------------

    private void renderKneel(Graphics2D g) {
        float standAlpha = (float) Math.max(0, 1 - kneelFade);
        if (standAlpha > 0) {
            if (kneelFromCalm) {
                renderCalm(g, standAlpha);
            } else {
                renderStanding(g, standAlpha);
                renderTrident(g, standAlpha);
            }
        }
        BufferedImage kneel = Assets.sprite("spr_asgore_kneel_0");
        if (kneel == null) {
            return;
        }
        Composite old = setAlpha(g, (float) kneelFade);
        g.drawImage(kneel, KNEEL_X, KNEEL_Y,
                (int) (kneel.getWidth() * SCALE), (int) (kneel.getHeight() * SCALE), null);
        BufferedImage face = Assets.sprite("spr_asgore_lastface_" + kneelFace);
        if (face != null) {
            // GML: lastface at kneel + (136, -8).
            g.drawImage(face, KNEEL_X + 136, KNEEL_Y - 8,
                    (int) (face.getWidth() * SCALE), (int) (face.getHeight() * SCALE), null);
        }
        restore(g, old);
    }

    // ---- Alpha helpers -------------------------------------------------------------

    private static Composite setAlpha(Graphics2D g, float alpha) {
        if (alpha >= 1f) {
            return null;
        }
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, alpha)));
        return old;
    }

    private static void restore(Graphics2D g, Composite old) {
        if (old != null) {
            g.setComposite(old);
        }
    }

    /**
     * Build (once) a red-tinted copy of the white trident line-art
     * ({@code spr_asgorespear_0}) — GML draws it with {@code color = 255} (BGR red).
     * Each pixel's luminance maps to red while alpha is preserved, so the white shaft
     * becomes a solid red trident with its shading intact.
     */
    private static BufferedImage tintedSpear() {
        if (redSpear != null) {
            return redSpear;
        }
        BufferedImage src = Assets.sprite("spr_asgorespear_0");
        if (src == null) {
            return null;
        }
        BufferedImage out = new BufferedImage(
                src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) {
                    continue;
                }
                int lum = (argb >> 16) & 0xFF;
                int r = lum;
                int gg = (int) (lum * 0.12);
                int b = (int) (lum * 0.12);
                out.setRGB(x, y, (a << 24) | (r << 16) | (gg << 8) | b);
            }
        }
        redSpear = out;
        return out;
    }
}
