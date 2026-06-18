package boss;

import battle.BulletBoard;
import core.EntityManager;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * Muffet's body (GML: {@code obj_spiderb_body}). A faithful port of the multi-part
 * draw: six arms (two upper + shoulders, two lower, two mid-arms holding teapots), the
 * head with its two braids and five blinking spider-eyes, and the shirt/pants. Every
 * part is composed at the GML 2× scale at its true sprite origin, swaying on the same
 * {@code sin(siner/5)} the GML uses, so the silhouette matches the fight.
 *
 * <p>The body floats above the combat box and rises with it when the box grows tall
 * during the pet special (GML: it tracks {@code obj_uborder.y}). Drawn behind the box
 * so the box edge can tuck under the dangling lower arms.
 *
 * // GML: obj_spiderb_body
 */
public final class MuffetBody extends BossBody {

    private static final double SCALE = 2.0;

    /** GML body anchor — chosen so Muffet centres over the border-21 box (xmid 317). */
    private static final int BODY_X = 250;
    private static final int BODY_Y = 40;

    private final BulletBoard board;

    private double siner;
    private int anim;
    private final double[] eye = new double[5];

    /** Set by the boss during the hurt shudder: freezes the sway, shows hurt eyes. */
    public boolean hurt;

    public MuffetBody(EntityManager manager, BulletBoard board) {
        super(manager);
        this.board = board;
        this.depth = 1100; // behind the combat box (box depth 1000)
    }

    @Override
    public void update() {
        if (!hurt) {
            siner++;
        }
        // GML eye animation: a rolling blink across the five eyes, plus a shared blink.
        for (int i = 0; i < 5; i++) {
            if (anim > i * 5 && anim < 7 + i * 5) {
                eye[i] += 0.5;
            }
            if (anim > 12 + i * 5 && anim < 16 + i * 5) {
                eye[i] -= 0.5;
            }
            if (anim > 70 && anim < 77) {
                eye[i] += 0.5;
            }
            if (anim > 88 && anim < 95) {
                eye[i] -= 0.5;
            }
            eye[i] = Math.max(0, Math.min(3, eye[i]));
        }
        anim++;
        if (anim > 110) {
            anim = 0;
        }
        if (hurt) {
            for (int i = 0; i < 5; i++) {
                eye[i] = 0;
            }
        }
    }

    @Override
    public void render(Graphics2D g) {
        // GML: the body rises with the box top once it climbs above y=240 (pet special).
        double top = board.top();
        double y = BODY_Y - Math.max(0, 240 - top);
        double x = BODY_X;

        double s = Math.sin(siner / 5.0);
        double c = Math.cos(siner / 5.0);
        double heady = y + s * 4;
        double hairrot = s * 25;

        // Back arms (upper arms + shoulders).
        part(g, "spr_spiderb_upperarm_0", x + 14, y + 86 + 26 + c, -2, 2, -s * 6, 0, 19);
        part(g, "spr_spiderb_shoulder_0", x + 42, y + 86 + c, -2, 2, 0, 0, 0);
        part(g, "spr_spiderb_upperarm_0", x + 78, y + 86 + 26 + c, 2, 2, s * 6, 0, 19);
        part(g, "spr_spiderb_shoulder_0", x + 50, y + 86 + c, 2, 2, 0, 0, 0);

        // Braids, head, legs.
        part(g, "spr_spiderb_hair_0", x + 80, heady * 1.02 + 18, 2, 2, hairrot, 0, 5);
        part(g, "spr_spiderb_hair_0", x + 12, heady * 1.02 + 18, -2, 2, -hairrot, 0, 5);
        part(g, "spr_spiderb_head_0", x, heady, 2, 2, 0, 0, 0);
        part(g, "spr_spiderb_legs_0", x + 30, y + 162, 2, 2, 0, 0, 0);

        // Lower arms (swap frame on the up-swing).
        String low = s < 0 ? "spr_spiderb_lowarm_1" : "spr_spiderb_lowarm_0";
        part(g, low, x + 26, y + 130 + s, 2, 2, s * 8 - 8, 17, 0);
        part(g, low, x + 64, y + 130 + s, -2, 2, -(s * 8) + 8, 17, 0);

        // Teapots + mid-arms (the idle "pouring tea" pose, mode 0).
        part(g, "spr_spiderb_teapot_0", x - 22, y + 104 + c * 2, 2, 2, -s * 24, 19, 7);
        part(g, "spr_spiderb_midarm_0", x + 12, y + 116 + c * 2, 2, 2, 0, 0, 0);
        part(g, "spr_spiderb_midarm2_0", x + 12, y + 130 + c * 2, 2, 2, s * 3, 15, 14);
        part(g, "spr_spiderb_teapot_0", x + 114, y + 104 + c * 2, -2, 2, -s * 24, 19, 7);
        part(g, "spr_spiderb_midarm_0", x + 80, y + 116 + c * 2, -2, 2, 0, 0, 0);
        part(g, "spr_spiderb_midarm2_0", x + 80, y + 130 + c * 2, -2, 2, s * 3, 15, 14);

        // Torso.
        part(g, "spr_spiderb_pants_0", x + 20, y + 114 + s, 2, 2, 0, 0, 0);
        part(g, "spr_spiderb_shirt_0", x + 28, y + 92 + s * 2, 2, 2, 0, 0, 0);

        // The five eyes (hurt swaps to the half-shut "hurt" sprites).
        String big = hurt ? "spr_spiderb_eyebig_hurt_0" : "spr_spiderb_eyebig_";
        String med = hurt ? "spr_spiderb_eyemed_hurt_0" : "spr_spiderb_eyemed_";
        String cen = hurt ? "spr_spiderb_eyecen_hurt_0" : "spr_spiderb_eyecen_";
        eyePart(g, big, eye[0], x + 24, heady + 42, 2);
        eyePart(g, med, eye[1], x + 30, heady + 32, 2);
        eyePart(g, cen, eye[2], x + 42, heady + 26, 2);
        eyePart(g, med, eye[3], x + 62, heady + 32, -2);
        eyePart(g, big, eye[4], x + 68, heady + 42, -2);
    }

    /** Draw an eye: hurt sprites are single-frame, normal ones index by {@code frame}. */
    private void eyePart(Graphics2D g, String name, double frame, double x, double y, double xs) {
        String sprite = hurt ? name : name + (int) frame;
        part(g, sprite, x, y, xs, 2, 0, 0, 0);
    }

    /**
     * GML {@code draw_sprite_ext}: the sprite's origin {@code (ox,oy)} maps to
     * {@code (x,y)}, scaled (negative {@code xs} mirrors) and rotated (GML angles are
     * CCW, so the Java rotation is negated).
     */
    private void part(Graphics2D g, String name, double x, double y,
                      double xs, double ys, double angleDeg, int ox, int oy) {
        BufferedImage img = Assets.sprite(name);
        if (img == null) {
            return;
        }
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.rotate(-Math.toRadians(angleDeg));
        g.scale(xs, ys);
        g.drawImage(img, -ox, -oy, null);
        g.setTransform(old);
    }
}
