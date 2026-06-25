package boss;

import core.EntityManager;
import core.GlobalState;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * Asriel's final "Angel of Death" form (GML: {@code obj_afinal_body}). A large winged
 * figure built from many animated parts — two cosmos wings and two orb wings (tinted with
 * a cycling rainbow), a stem and orb body, two arms + shoulders, and the expression face
 * ({@code spr_afinal_face}, sub-image {@code global.faceemotion}). It floats with a gentle
 * bob over Part-B's cosmic backdrop.
 *
 * // GML: obj_afinal_body (Draw event)
 */
public final class AsrielFinalBody extends BossBody {

    private static final GlobalState G = GlobalState.get();
    static final double HOME_X = 320;
    static final double HOME_Y = 100;
    private static final double SCALE = 2.0;

    private int anim;
    private double siner;
    /** Tucked away while a Lost-Soul encounter takes over the screen. */
    public boolean hidden;
    /** GML cry state: 0 normal face, 1/2 crying (breakdown). */
    public int cry;

    // small per-hue-bucket tint cache for the wings (avoids re-tinting every frame)
    private final java.util.Map<String, BufferedImage> tintCache = new java.util.HashMap<>();

    public AsrielFinalBody(EntityManager manager) {
        super(manager);
        this.depth = 1100;
        this.x = HOME_X;
        this.y = HOME_Y;
    }

    @Override
    public void update() {
        anim++;
        siner++;
    }

    @Override
    public void render(Graphics2D g) {
        if (hidden) {
            return;
        }
        double yoff = Math.sin(siner / 4.0);
        double yoff2 = Math.sin(siner / 16.0);
        int af = anim / 6;               // GML floor(anim/6); each sprite wraps by its frames
        // The cosmos wings are white outlines (the dark starfield shows through their
        // interior); the orb-wing tendrils are the pastel "dripping" colour.
        Color pastel = Color.getHSBColor((float) ((siner * 4 / 360.0) % 1.0), 0.35f, 1f);

        part(g, "spr_afinal_cosmoswing_0", x + 42, y - 52 + yoff2 * 4, SCALE, SCALE, 0, 0, 0);
        part(g, "spr_afinal_cosmoswing_0", x - 44, y - 52 + yoff2 * 4, -SCALE, SCALE, 0, 0, 0);
        tinted(g, "spr_afinal_orbwing_" + (af % 4), pastel, x - 110, y - 52, SCALE, 0, 0);
        tinted(g, "spr_afinal_orbwing_" + (af % 4), pastel, x + 108, y - 52, -SCALE, 0, 0);

        // Lower body.
        part(g, "spr_afinal_stem_" + (af % 5), x - 2, y + 146, SCALE, SCALE, 0, 13, 25);
        part(g, "spr_afinal_orb_" + (af % 3), x - 2, y + 68, SCALE, SCALE, 0, 20, 17);

        // Face (in front of the body), then arms + shoulders.
        renderFace(g);
        part(g, "spr_afinal_arm_" + (af % 4), x - 58, y + 56 + yoff * 2, SCALE, SCALE, 0, 9, 2);
        part(g, "spr_afinal_arm_" + (af % 4), x + 56, y + 56 + yoff * 2, -SCALE, SCALE, 0, 9, 2);
        part(g, "spr_afinal_shoulder_" + (af % 4), x - 84, y + 32, SCALE, SCALE, 0, 0, 0);
        part(g, "spr_afinal_shoulder_" + (af % 4), x + 82, y + 32, -SCALE, SCALE, 0, 0, 0);
    }

    private void renderFace(Graphics2D g) {
        if (cry == 1) {
            part(g, "spr_afinal_face_cry_" + ((int) (siner / 8) % 3), x, y, SCALE, SCALE, 0, 32, 22);
        } else if (cry == 2) {
            part(g, "spr_afinal_face_cry2_" + ((int) (siner / 2) % 2), x, y, SCALE, SCALE, 0, 32, 22);
        } else {
            int face = Math.max(0, Math.min(12, G.faceemotion));
            part(g, "spr_afinal_face_" + face, x, y, SCALE, SCALE, 0, 32, 22);
        }
    }

    /** draw_sprite_ext: origin → (x,y), scaled (negative x mirrors), rotated. */
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

    /** Draw a rainbow-tinted (luminance × hue) copy of a white sprite. */
    private void tinted(Graphics2D g, String sprite, Color hue, double px, double py,
                        double xscale, int ox, int oy) {
        BufferedImage src = Assets.sprite(sprite);
        if (src == null) {
            return;
        }
        int bucket = (hue.getRed() >> 5 << 10) | (hue.getGreen() >> 5 << 5) | (hue.getBlue() >> 5);
        String key = sprite + "#" + bucket;
        BufferedImage img = tintCache.get(key);
        if (img == null) {
            img = tint(src, hue);
            tintCache.put(key, img);
        }
        part(g, img, px, py, xscale, SCALE, ox, oy);
    }

    private void part(Graphics2D g, BufferedImage img, double px, double py,
                      double xscale, double yscale, int ox, int oy) {
        AffineTransform old = g.getTransform();
        g.translate(px, py);
        g.scale(xscale, yscale);
        g.drawImage(img, -ox, -oy, null);
        g.setTransform(old);
    }

    private static BufferedImage tint(BufferedImage src, Color c) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) {
                    continue;
                }
                int lum = (argb >> 16) & 0xFF;       // white line-art → luminance from red
                int r = lum * c.getRed() / 255;
                int gg = lum * c.getGreen() / 255;
                int b = lum * c.getBlue() / 255;
                out.setRGB(x, y, (a << 24) | (r << 16) | (gg << 8) | b);
            }
        }
        return out;
    }
}
