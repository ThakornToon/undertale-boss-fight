package bullet.asriel;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Composite;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;
import util.GMLHelper;

/**
 * A big rainbow star that falls diagonally across the screen and <b>detonates into a
 * ring of {@link RegStar} bullets</b> (GML: {@code obj_stormstar}). The falling star
 * itself does not hurt — only its burst does. It cruises down-left (GML
 * {@code direction = 215}) trailing afterimages, cycles through the rainbow, then after
 * a short timer bursts and fades out, growing as it goes.
 *
 * <p>The {@code big} finale star is slower, larger and bursts into a much denser ring
 * (three stacked rings of 20). {@code hMode} (GALACTA BLAZING, turn 9) tightens the
 * burst friction so the rings spread faster.
 *
 * // GML: obj_stormstar
 */
public final class StormStar extends AttackPattern {

    private final Soul soul;
    public boolean big;
    public boolean hMode;
    public int dmg = 8;

    private double speed;
    private final double dir;     // aim direction — every star is flung INTO the box
    private final double targetY; // detonate the instant the star reaches this depth (border / inside box)
    private int con = 1;          // 1 falling · 3 exploding (GML detonates on box contact)
    private int frame;            // sprite sub-image toggle
    private double scale = 1;
    private float alpha = 1f;
    private final float hue;      // GML make_color_hsv: each star a different pastel hue

    // Afterimage trail (GML draws the previous few positions at reduced alpha).
    private final double[] tx = new double[3];
    private final double[] ty = new double[3];

    // Cache of pastel-tinted star sprites by hue bucket (the falling stars are colourful).
    private static final java.util.Map<String, BufferedImage> TINTS = new java.util.HashMap<>();

    public StormStar(EntityManager manager, Soul soul, double x, double y, boolean big,
                     double dir, double targetY) {
        super(manager);
        this.soul = soul;
        this.big = big;
        this.x = x;
        this.y = y;
        this.dir = dir;
        this.targetY = targetY;
        this.depth = -3;
        this.speed = big ? 6 : 13 + GMLHelper.random(5);
        this.hue = (float) GMLHelper.random(1.0);
        if (big) {
            this.scale = 2;
        }
        for (int i = 0; i < tx.length; i++) {
            tx[i] = x;
            ty[i] = y;
        }
    }

    @Override
    public void update() {
        if (G.turntimer <= 0 && con < 3) {
            manager.destroy(this);
            return;
        }
        if (++frame > 7) {
            frame = 0;
        }
        // GML direction 215 (down-left). Trail follows the leading position.
        for (int i = tx.length - 1; i > 0; i--) {
            tx[i] = tx[i - 1];
            ty[i] = ty[i - 1];
        }
        tx[0] = x;
        ty[0] = y;

        if (con == 1) {
            move();
            // The star bursts the instant it just GRAZES the box — touching any part of the
            // border counts as a hit (the big finale star bursts on the top border too).
            if (reachedBox()) {
                detonate();
            } else if (y > G.idealborder[3] + 80 || x < G.idealborder[0] - 140) {
                manager.destroy(this);   // safety: should never miss now that stars are aimed
            }
        } else if (con == 3) {
            // Explosion: a quick flash-fade (the burst stars carry the motion now).
            scale += big ? 0.06 : 0.03;
            alpha -= 0.12f;
            if (alpha <= 0) {
                manager.destroy(this);
            }
        }
    }

    private void move() {
        // Both the rain and the finale star travel straight along their aim into the box.
        x += GMLHelper.lengthdir_x(speed, dir);
        y += GMLHelper.lengthdir_y(speed, dir);
    }

    /** True the instant the star touches the combat box. */
    private boolean reachedBox() {
        if (big) {
            // The huge finale star bursts as soon as it touches the TOP border.
            return y >= targetY;
        }
        // A rain star bursts on first contact with any part of the box (a graze counts).
        return x >= G.idealborder[0] && x <= G.idealborder[1]
                && y >= G.idealborder[2] && y <= G.idealborder[3];
    }

    /** GML event_user(3): burst into an accelerating ring of small stars, then explode. */
    private void detonate() {
        con = 3;
        util.Audio.play("/audio/mus_sfx_abreak.ogg", big ? 0.9 : 0.6, big ? 1.0 : 1.1 + GMLHelper.random(0.2));
        double base = GMLHelper.random(360);
        if (!big) {
            double fric = hMode ? -0.25 : -0.2;
            double sp = hMode ? 1.5 : 1.4;
            for (int i = 0; i < 7; i++) {
                spawnStar(base + 51.4285714 * i, sp, fric);
            }
        } else {
            for (int i = 0; i < 20; i++) {
                spawnStar(base + 22.5 * i, 1.6, -0.3);
            }
            base += 9;
            for (int i = 0; i < 20; i++) {
                spawnStar(base + 22.5 * i, 0.8, -0.24);
            }
            for (int i = 0; i < 20; i++) {
                spawnStar(base + 18 * i, 0.2, -0.18);
            }
        }
    }

    /** A single ring fragment, spawned at the detonating star's position (not 0,0). */
    private void spawnStar(double dir, double sp, double fric) {
        RegStar r = new RegStar(manager, soul, dir, sp, fric, dmg);
        r.x = x;
        r.y = y;
        manager.add(r);
    }

    @Override
    public void render(Graphics2D g) {
        String sprite = big ? "spr_stormstar_b_0" : "spr_stormstar_" + (frame < 4 ? 0 : 1);
        BufferedImage white = Assets.sprite(sprite);
        if (white == null) {
            white = Assets.sprite("spr_stormstar_0");
        }
        if (white == null) {
            return;
        }
        // GML: each falling star is a big pastel-coloured star (make_color_hsv), ~127px.
        BufferedImage img = tinted(sprite, hue);
        double sc = scale * (big ? 1.6 : 0.95);
        int w = (int) (img.getWidth() * sc);
        int h = (int) (img.getHeight() * sc);
        Composite oc = g.getComposite();
        // GML: the big finale star drags a faint ring behind it.
        if (big) {
            java.awt.Color c = java.awt.Color.getHSBColor(hue, 0.5f, 1f);
            g.setColor(new java.awt.Color(c.getRed(), c.getGreen(), c.getBlue(),
                    (int) (60 * Math.max(0, alpha))));
            int r = (int) (w * 0.9);
            g.fillOval((int) (x - r / 2.0), (int) (y - r / 2.0), r, r);
        }
        // Afterimage trail.
        for (int i = tx.length - 1; i >= 0; i--) {
            float a = alpha * (0.14f + 0.1f * (tx.length - 1 - i));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, a)));
            g.drawImage(img, (int) (tx[i] - w / 2.0), (int) (ty[i] - h / 2.0), w, h, null);
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, alpha)));
        g.drawImage(img, (int) (x - w / 2.0), (int) (y - h / 2.0), w, h, null);
        g.setComposite(oc);
    }

    /** Pastel-tint the white star sprite to the star's hue (cached by hue bucket). */
    private static BufferedImage tinted(String sprite, float hue) {
        int bucket = (int) (hue * 16);
        String key = sprite + "#" + bucket;
        BufferedImage cached = TINTS.get(key);
        if (cached != null) {
            return cached;
        }
        BufferedImage src = Assets.sprite(sprite);
        java.awt.Color c = java.awt.Color.getHSBColor(bucket / 16f, 0.45f, 1f);  // pastel
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) {
                    continue;
                }
                int lum = (argb >> 16) & 0xFF;
                int r = lum * c.getRed() / 255;
                int gg = lum * c.getGreen() / 255;
                int b = lum * c.getBlue() / 255;
                out.setRGB(x, y, (a << 24) | (r << 16) | (gg << 8) | b);
            }
        }
        TINTS.put(key, out);
        return out;
    }
}
