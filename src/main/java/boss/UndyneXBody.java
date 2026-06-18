package boss;

import core.EntityManager;
import core.GlobalState;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * Undyne the Undying's <b>genocide</b> body (GML: {@code obj_undynex_body},
 * {@code spr_undynex_*}) — the spikier, feral determination form. Unlike the
 * neutral body's part-instances, the GML draws this one in immediate mode, so this
 * ports the {@code Draw} event directly: hair, legs, the two arms, torso, pants and
 * face composited at the GML 2× scale and offsets (honouring each sprite's x-origin),
 * all bobbing on a shared idle sine, with a periodic eye-beam flash.
 *
 * // GML: obj_undynex_body
 */
public final class UndyneXBody extends BossBody {

    private static final GlobalState G = GlobalState.get();
    private static final double SCALE = 2.0;
    static final int BODY_X = 222;
    static final int BODY_Y = -16;   // sits high (the box covers the legs)

    private double siner;

    /** GML darkify dim during the enemy turn (0 bright … ~0.6 gray). */
    public double dim;
    /** Death: fade the whole body out (the controller's melt/vaporize). */
    public boolean fadeOut;
    private double fade = 1;

    public UndyneXBody(EntityManager manager) {
        super(manager);
        this.depth = 1100;     // behind the combat box
    }

    public boolean faded() {
        return fadeOut && fade <= 0;
    }

    @Override
    public void update() {
        siner += 1.4;
        if (fadeOut) {
            fade = Math.max(0, fade - 0.04);
        }
    }

    @Override
    public void render(Graphics2D g) {
        double s = Math.sin(siner / 6.0);
        double s2 = Math.sin(siner / 3.0);

        double eff = fade * (1 - Math.max(0, Math.min(0.85, dim)));
        Composite old = setAlpha(g, (float) eff);

        // GML draw order: hair, legs, arms, torso, pants, face (then eye beam).
        sprite(g, "spr_undynex_hair_0", 85, s * 3 + 4, 16, 70 - s * 15);
        sprite(g, "spr_undynex_legs", 100, 164, 20, 0);
        // The torso (armor + heart + spiky shoulders) sits high so the shoulders meet
        // the neck; the arms hang a little lower from those shoulders.
        sprite(g, "spr_undynex_leftarm_0", 64 + s * 5, 100 + s * 5, 33, 0);
        sprite(g, "spr_undynex_rightarm_0", 136 + s2 * 3, 100 + s * 6 + s2 * 2, 4, 0);
        sprite(g, "spr_undynex_torso_0", 100, 50 + s * 4, 39, -(s * 4));
        sprite(g, "spr_undynex_pants_0", 100, 150 + s * 2, 20, s * 2);
        sprite(g, "spr_undynex_face1_0", 100, 28 + s * 2, 20, 0);
        restore(g, old);
        // (The GML eye-beam flash is omitted — at the GML scale it streaks across the
        // whole screen, which reads as a glitch here.)
    }

    /** draw_sprite_ext at 2× honouring the GML x-origin and (CCW) angle. */
    private void sprite(Graphics2D g, String name, double offX, double offY,
                        int originX, double gmlAngle) {
        BufferedImage img = Assets.sprite(name);
        if (img == null) {
            return;
        }
        drawExt(g, img, BODY_X + offX, BODY_Y + offY, originX, SCALE, SCALE, gmlAngle);
    }

    private void drawExt(Graphics2D g, BufferedImage img, double px, double py,
                         int originX, double xs, double ys, double gmlAngle) {
        AffineTransform old = g.getTransform();
        g.translate(px, py);
        g.rotate(-Math.toRadians(gmlAngle));
        g.scale(xs, ys);
        g.drawImage(img, -originX, 0, null);
        g.setTransform(old);
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
