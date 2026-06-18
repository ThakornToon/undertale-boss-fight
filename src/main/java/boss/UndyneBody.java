package boss;

import core.EntityManager;
import core.GlobalState;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * Undyne the Undying's body (GML: {@code obj_undyneb_body}). Seven parts —
 * legs, pants, armor, the two arms, hair and the eyepatched head — drawn at the GML
 * 2× scale with the GML layout offsets, bobbing on a shared idle sine. The head frame
 * follows {@link GlobalState#faceemotion} (her expression escalates as the fight
 * does); the hair flutters on its own loop.
 *
 * <p>On death she melts: the parts fade out while {@code spr_undyneb_melt} fades in
 * (GML {@code obj_undyneb_body} User-Defined-13 + the fadeout Step branch), which the
 * controller drives through its reform/death cutscene.
 *
 * // GML: obj_undyneb_body
 */
public final class UndyneBody extends BossBody {

    private static final GlobalState G = GlobalState.get();

    private static final double SCALE = 2.0;
    // Where the figure sits (the box occludes her lower body, as in the GML room).
    static final int BODY_X = 196;
    static final int BODY_Y = 24;

    // GML obj_undyneb_body part offsets [head, hair, armor, rarm, pants, larm, legs].
    private static final int HEAD_X = 80,  HEAD_Y = 0;
    private static final int HAIR_X = 4,   HAIR_Y = -4;
    private static final int ARMOR_X = 52, ARMOR_Y = 56;
    private static final int RARM_X = 148, RARM_Y = 78;
    private static final int PANTS_X = 78, PANTS_Y = 120;
    private static final int LARM_X = 82,  LARM_Y = 66;
    private static final int LEGS_X = 78,  LEGS_Y = 154;

    // GML sprite origins (only the left arm has a non-zero one). Without honouring it
    // the spear arm draws 35×2 = 70px too far right and reads as detached.
    private static final int LARM_ORIGIN_X = 35;

    private double siner;
    private double hairFrame;

    /** Darkify dim during the enemy turn (0 = full bright, up to ~0.6 = gray). The
     *  controller raises it while she's attacking and lowers it for the menu. */
    public double dim;

    // ---- Death melt (GML User-Defined-13 + fadeout) --------------------------
    private boolean melting;
    private double bodyAlpha = 1;
    private double meltAlpha;
    private int meltFrame;

    public UndyneBody(EntityManager manager) {
        super(manager);
        this.depth = 1100;     // behind the combat box (box depth 1000)
    }

    /** GML User-Defined-13: begin the melt — parts fade out, the melt sprite fades in. */
    public void startMelt() {
        melting = true;
    }

    /** Advance the melt sprite frame (the controller's "I WON'T DIE" beats). */
    public void setMeltFrame(int f) {
        meltFrame = Math.max(0, Math.min(3, f));
    }

    public boolean meltGone() {
        return melting && bodyAlpha <= 0;
    }

    @Override
    public void update() {
        siner += 1.2;
        hairFrame += 0.25;       // GML hair.image_speed = 0.25
        if (melting) {
            bodyAlpha = Math.max(0, bodyAlpha - 0.05);
            meltAlpha = Math.min(1, meltAlpha + 0.2);
        }
    }

    @Override
    public void render(Graphics2D g) {
        if (melting && bodyAlpha <= 0) {
            drawMelt(g);
            return;
        }
        double s = Math.sin(siner / 6.0);
        double s3 = Math.sin(siner / 3.0);

        // GML darkify: she (and the box) dim to gray during the enemy turn. White
        // line-art drawn at reduced alpha on black reads as the reference gray.
        double eff = bodyAlpha * (1 - Math.max(0, Math.min(0.85, dim)));
        Composite old = setAlpha(g, (float) eff);
        // Back-to-front: legs, pants, right arm, armor, left arm, hair, head.
        part(g, "spr_undyneb_legs_1", LEGS_X, LEGS_Y, 0);
        part(g, "spr_undyneb_pants_0", PANTS_X, PANTS_Y + (int) (s * 2), 0);
        part(g, "spr_undyneb_rightarm_0", RARM_X - (int) (s3 * 2),
                 RARM_Y + (int) (s * 6 + s3 * 2), 0);
        part(g, "spr_undyneb_armor_0", ARMOR_X, ARMOR_Y + (int) (s * 4), 0);
        part(g, "spr_undyneb_leftarm_0", LARM_X + (int) (s * 5), 16 + LARM_Y + (int) (s * 5),
                LARM_ORIGIN_X);
        part(g, "spr_undyneb_hair_" + (((int) hairFrame) % 4), HAIR_X, HAIR_Y + (int) (s * 4), 0);
        part(g, "spr_undyneb_face_" + faceFrame(), HEAD_X, HEAD_Y + (int) (s * 2), 0);
        restore(g, old);

        if (melting) {
            drawMelt(g);
        }
    }

    private int faceFrame() {
        return Math.max(0, Math.min(8, G.faceemotion));
    }

    /** Draw a part honouring the GML sprite x-origin (scaled), at the 2× scale. */
    private void part(Graphics2D g, String sprite, int offX, int offY, int originX) {
        BufferedImage img = Assets.sprite(sprite);
        if (img == null) {
            return;
        }
        g.drawImage(img, BODY_X + offX - (int) (originX * SCALE), BODY_Y + offY,
                (int) (img.getWidth() * SCALE), (int) (img.getHeight() * SCALE), null);
    }

    private void drawMelt(Graphics2D g) {
        BufferedImage img = Assets.sprite("spr_undyneb_melt_" + meltFrame);
        if (img == null) {
            return;
        }
        Composite old = setAlpha(g, (float) meltAlpha);
        // Centre the melt roughly where her torso was.
        g.drawImage(img, BODY_X + ARMOR_X - 10, BODY_Y + ARMOR_Y,
                (int) (img.getWidth() * SCALE), (int) (img.getHeight() * SCALE), null);
        restore(g, old);
    }

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
}
