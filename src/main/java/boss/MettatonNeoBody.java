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

/**
 * Mettaton NEO's body (GML: {@code obj_mneo_body}). A faithful port of the GML draw
 * code: two flickering rocket bursts behind the torso, the legs, the two arms, the
 * winged body, and the face frame keyed on {@link GlobalState#faceemotion} — all at
 * the GML 2× scale, bobbing on a single sine. NEO never attacks (his turn is skipped
 * instantly), so the body only ever idles, shudders on the killing blow
 * ({@link #shake}), and white-fades on death ({@link #fadewhite}).
 *
 * // GML: obj_mneo_body (spr_mneo_burst / legs / armr / arml / body / face)
 */
public final class MettatonNeoBody extends BossBody {

    private static final GlobalState G = GlobalState.get();
    private static final double SCALE = 2.0;

    /** Body anchor (the head/face origin); the figure is centred on the play field. */
    private double bodyX = 320;
    private double bodyY = 96;

    /** GML sprite origins (xorig, yorigin) — parts are drawn anchored here, not top-left. */
    private static final java.util.Map<String, int[]> ORIGIN = java.util.Map.of(
            "spr_mneo_body", new int[] { 56, 35 },
            "spr_mneo_legs", new int[] { 58, 56 },
            "spr_mneo_arml", new int[] { 60, 2 },
            "spr_mneo_armr", new int[] { 2, 2 },
            "spr_mneo_face", new int[] { 23, 19 },
            "spr_mneo_burst", new int[] { 0, 0 });

    private double siner;
    /** GML pause: freezes the idle sine (set while the death hit lands). */
    public boolean pause;
    /** GML event_user(10): jitter the whole body on the killing blow. */
    public boolean shake;
    /** GML fadewhite: the death white-out ramp. */
    public boolean fadewhite;
    private double whiteval;
    private boolean fadeComplete;

    public MettatonNeoBody(EntityManager manager) {
        super(manager);
        this.depth = 1100; // behind the combat box (box depth 1000)
    }

    public boolean fadeComplete() {
        return fadeComplete;
    }

    @Override
    public void update() {
        if (pause) {
            siner = 0;
        } else {
            siner++;
        }
        // Advance the death fade here (not in render) so it completes even headless.
        if (fadewhite) {
            whiteval += 0.2;
            if (whiteval >= 44) {
                fadeComplete = true;
            }
        }
    }

    @Override
    public void render(Graphics2D g) {
        double jx = 0;
        double jy = 0;
        if (shake) {
            jx = Math.random() * 2 - Math.random() * 2;
            jy = Math.random() * 2 - Math.random() * 2;
        }
        double x = bodyX + jx;
        double y = bodyY + jy;

        // GML: two rocket bursts behind the torso, flickering and gently rotating.
        float burstA = (float) (Math.abs(Math.sin(siner * 0.3)) * 0.5 + 0.4);
        draw(g, "spr_mneo_burst_0", x - 24, y + 18 + Math.sin(siner / 3) * 1,
                -SCALE, SCALE, Math.sin(siner / 6) * 2, burstA);
        draw(g, "spr_mneo_burst_0", x + 28, y + 18 + Math.sin(siner / 3) * 1,
                SCALE, SCALE, -Math.sin(siner / 6) * 2, burstA);

        // GML: legs, arms, body, face (each on its own sine offset).
        draw(g, "spr_mneo_legs_0", x, y + 84 + 112, SCALE, SCALE, 0, 1f);
        draw(g, "spr_mneo_armr_0", x + 40 + Math.sin(siner / 3) * 2, y + 40,
                SCALE, SCALE, Math.sin(siner / 6) * 2, 1f);
        draw(g, "spr_mneo_arml_0", x - 26 - Math.sin(siner / 3) * 2, y + 40,
                SCALE, SCALE, -Math.sin(siner / 6) * 2, 1f);
        draw(g, "spr_mneo_body_0", x + 4, y + 36 + Math.sin(siner / 3) * 2, SCALE, SCALE, 0, 1f);
        draw(g, "spr_mneo_face_" + G.faceemotion, x, y + Math.sin(siner / 3) * 3, SCALE, SCALE, 0, 1f);

        if (fadewhite) {
            renderFadeWhite(g);
        }
    }

    /** GML fadewhite: draw the white→black ramp (progression is in update). */
    private void renderFadeWhite(Graphics2D g) {
        Composite oc = g.getComposite();
        if (whiteval <= 10) {
            g.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, (float) Math.min(1.0, whiteval / 10.0)));
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, core.Game.WIDTH, core.Game.HEIGHT);
        } else {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, core.Game.WIDTH, core.Game.HEIGHT);
            g.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, (float) Math.min(1.0, -1 + whiteval / 10.0)));
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, core.Game.WIDTH, core.Game.HEIGHT);
        }
        g.setComposite(oc);
    }

    /**
     * GML {@code draw_sprite_ext}: map the sprite's origin (xorig, yorigin) to
     * {@code (x,y)}, scaled (negative xscale mirrors about the origin), rotated by the
     * GML angle (CCW), with alpha.
     */
    private void draw(Graphics2D g, String sprite, double x, double y,
                      double xscale, double yscale, double gmlAngle, float alpha) {
        BufferedImage img = Assets.sprite(sprite);
        if (img == null) {
            return;
        }
        int[] o = origin(sprite);
        Composite oc = null;
        if (alpha < 1f) {
            oc = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, alpha)));
        }
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        if (gmlAngle != 0) {
            g.rotate(-Math.toRadians(gmlAngle));
        }
        g.scale(xscale, yscale);
        g.drawImage(img, -o[0], -o[1], null);
        g.setTransform(old);
        if (oc != null) {
            g.setComposite(oc);
        }
    }

    /** Look up a sprite's GML origin (strips the trailing _frame index). */
    private static int[] origin(String sprite) {
        int i = sprite.lastIndexOf('_');
        String base = sprite;
        if (i > 0 && sprite.substring(i + 1).chars().allMatch(Character::isDigit)) {
            base = sprite.substring(0, i);
        }
        return ORIGIN.getOrDefault(base, new int[] { 0, 0 });
    }
}
