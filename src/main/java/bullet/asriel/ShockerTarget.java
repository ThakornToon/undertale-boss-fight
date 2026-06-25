package bullet.asriel;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;
import util.GMLHelper;

/**
 * The floor marker that telegraphs where a {@link ShockerBolt} will strike (GML:
 * {@code obj_rainbowtarget}). It flashes on the box floor for ~20 frames, then spawns
 * the lightning column at its X and vanishes. The {@code giga} marker is ×3 and drops
 * a wide ×4 bolt.
 *
 * // GML: obj_rainbowtarget
 */
public final class ShockerTarget extends AttackPattern {

    private final Soul soul;
    private final boolean giga;
    private final int dmgVal;
    private int timer = 20;        // GML alarm[0] = 20
    private int frame;
    private double hue;

    public ShockerTarget(EntityManager manager, Soul soul, double x, boolean giga, int dmg) {
        super(manager);
        this.soul = soul;
        this.x = x;
        this.y = 360;              // GML: targets sit on the box floor (y = 360)
        this.giga = giga;
        this.dmgVal = dmg;
        this.hue = GMLHelper.random(1.0);
        this.depth = -4;
        util.Audio.play("/audio/mus_sfx_a_target.ogg", 0.7);
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        hue = (hue + 0.05) % 1.0;
        if (++frame > 3) {
            frame = 0;
        }
        if (--timer <= 0) {
            manager.add(new ShockerBolt(manager, soul, x, giga ? 1 : 0, dmgVal));
            manager.destroy(this);
        }
    }

    @Override
    public void render(Graphics2D g) {
        BufferedImage img = Assets.sprite("spr_rainbowtarget_" + (frame < 2 ? 0 : 1));
        if (img == null) {
            img = Assets.sprite("spr_rainbowtarget_0");
        }
        if (img == null) {
            return;
        }
        double scale = giga ? 3 : 2;          // GML image_xscale 2, giga 3
        int w = (int) (img.getWidth() * scale);
        int h = (int) (img.getHeight() * scale);
        // Blink faster as the strike approaches so the telegraph is readable.
        float blink = timer < 8 ? (timer % 2 == 0 ? 1f : 0.35f) : 0.8f;
        Composite oc = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, blink));
        Color c = Color.getHSBColor((float) hue, 0.7f, 1f);
        g.drawImage(tint(img, c), (int) (x - w / 2.0), (int) (y - h / 2.0), w, h, null);
        g.setComposite(oc);
    }

    // Small per-frame tint of the 20×20 marker (cheap; few markers on screen).
    private static BufferedImage tint(BufferedImage src, Color c) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        for (int yy = 0; yy < src.getHeight(); yy++) {
            for (int xx = 0; xx < src.getWidth(); xx++) {
                int argb = src.getRGB(xx, yy);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) {
                    continue;
                }
                out.setRGB(xx, yy, (a << 24) | (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue());
            }
        }
        return out;
    }
}
