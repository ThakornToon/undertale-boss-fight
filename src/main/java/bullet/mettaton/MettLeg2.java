package bullet.mettaton;

import battle.Soul;
import bullet.Bullet;
import bullet.Shootable;
import core.EntityManager;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * One of Mettaton EX's descending legs (GML: {@code spr_mettleg2} / its flip —
 * example_0). A leg juts in from one side and slowly descends; its inner foot slides
 * in and out (it does not stretch — the whole leg, at a fixed length, slides), and
 * shooting it toggles moving↔still and white↔yellow. Touching the leg hurts.
 *
 * <p>{@code length} is the leg's fixed length: a lone leg is as long as the whole box;
 * a left/right pair is ~0.6 of the box each. The bar is long but stays thin.
 *
 * // GML: obj_mettattackgen leg bullets (spr_mettleg2 / spr_mettleg2_flip)
 */
public final class MettLeg2 extends Bullet implements Shootable {

    private static final double HALF_H = 13;     // thin (sprite is 13px tall, ×2)
    private static final double SLIDE_SPEED = 0.03;
    private static final double SLIDE_MIN = 0.15;

    private final boolean left;
    private final double length;
    private boolean moving;
    private boolean yellow;
    private double slide;       // 0 = out (off-screen), 1 = fully in
    private double dir = 1;

    public MettLeg2(EntityManager manager, Soul soul, double y, double vspeed,
                    boolean left, boolean moving, double length) {
        super(manager, soul);
        this.y = y;
        this.vspeed = vspeed;
        this.left = left;
        this.moving = moving;
        this.yellow = moving;
        this.length = length;
        this.slide = moving ? SLIDE_MIN : 1.0;
        this.dmg = 5;
        this.depth = -1;
    }

    /** The inner foot x (the gap is past it). */
    private double innerX() {
        double[] b = G.idealborder;
        return left ? b[0] + slide * length : b[1] - slide * length;
    }

    @Override
    protected double halfWidth() {
        return 9999;
    }

    @Override
    protected double boxTop() {
        return y - HALF_H;
    }

    @Override
    protected double boxBottom() {
        return y + HALF_H;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        y += vspeed;
        if (moving) {
            slide += dir * SLIDE_SPEED;
            if (slide >= 1) {
                slide = 1;
                dir = -1;
            } else if (slide <= SLIDE_MIN) {
                slide = SLIDE_MIN;
                dir = 1;
            }
        }
        if (G.inv <= 0 && Math.abs(soul.y - y) < HALF_H + Soul.HALF) {
            double ix = innerX();
            boolean inside = left ? soul.x < ix : soul.x > ix;
            if (inside) {
                onHitSoul();
            }
        }
        if (y > G.idealborder[3] + 30) {
            manager.destroy(this);
        }
    }

    @Override
    public boolean hitBy(double px, double py) {
        if (Math.abs(py - y) > HALF_H + 6) {
            return false;
        }
        double ix = innerX();
        return left ? px < ix : px > ix;
    }

    @Override
    public boolean onShot() {
        moving = !moving;
        yellow = !yellow;
        return true;
    }

    @Override
    public int shotRating() {
        return 0; // toggled, not destroyed
    }

    @Override
    public void render(Graphics2D g) {
        String name = left ? "spr_mettleg2_0" : "spr_mettleg2_flip_0";
        BufferedImage img = yellow ? tinted(name) : Assets.sprite(name);
        if (img == null) {
            return;
        }
        int w = (int) length;
        int h = (int) (img.getHeight() * 2);  // fixed thin thickness
        double ix = innerX();
        int dx = left ? (int) (ix - w) : (int) ix; // foot at the inner edge; slides with `slide`
        g.drawImage(img, dx, (int) (y - h / 2.0), w, h, null);
    }

    /** Cache of white line-art recoloured to yellow (the "moving" leg state). */
    private static final java.util.Map<String, BufferedImage> TINT = new java.util.HashMap<>();

    private static BufferedImage tinted(String name) {
        BufferedImage cached = TINT.get(name);
        if (cached != null) {
            return cached;
        }
        BufferedImage src = Assets.sprite(name);
        if (src == null) {
            return null;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        for (int yy = 0; yy < src.getHeight(); yy++) {
            for (int xx = 0; xx < src.getWidth(); xx++) {
                int argb = src.getRGB(xx, yy);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) {
                    continue;
                }
                int lum = (argb >> 16) & 0xFF;
                out.setRGB(xx, yy, (a << 24) | (lum << 16) | (lum << 8)); // white → yellow
            }
        }
        TINT.put(name, out);
        return out;
    }
}
