package bullet.asriel;

import battle.Soul;
import bullet.Bullet;
import core.EntityManager;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;
import util.GMLHelper;

/**
 * A vertical rainbow lightning column that drops from the sky onto a telegraphed
 * column (GML: {@code obj_rainbowbolt}). It only <b>hurts during its bright flash</b>
 * (GML: {@code image_alpha > 0.8}) — a ~3-frame strike — then jitters and fades out.
 * The {@code giga} bolt is wider (×4) and lingers a touch longer.
 *
 * <p>Spawned at {@code y = -10} with the 32×200 column hanging straight down (origin
 * top-centre), so it covers the whole combat box; standing out of the struck column
 * is the dodge.
 *
 * // GML: obj_rainbowbolt
 */
public final class ShockerBolt extends Bullet {

    private final double thisx;
    private final double thisy;
    private final boolean giga;
    private double scale;
    private double ss;
    private boolean sfxPlayed;
    private float alpha = 1f;
    private double hue;
    private int frame;

    public ShockerBolt(EntityManager manager, Soul soul, double x, double giga0, int dmg) {
        super(manager, soul);
        this.thisx = x;
        this.thisy = -10;
        this.x = x;
        this.y = -10;
        this.giga = giga0 > 0;
        this.scale = this.giga ? 4 : 2;
        this.ss = this.giga ? -2 : 0;       // GML: giga starts ss = -2 (lingers longer)
        this.dmg = dmg;
        this.hue = GMLHelper.random(1.0);
        this.frame = GMLHelper.irandom(1);
        this.depth = -6;
    }

    @Override
    protected double halfWidth() {
        return 16 * scale;                  // 32-wide sprite, centred
    }

    @Override
    protected double boxTop() {
        return G.idealborder[2] - 60;       // the column spans the whole box vertically
    }

    @Override
    protected double boxBottom() {
        return G.idealborder[3] + 60;
    }

    /** GML: only deals damage during the bright flash (image_alpha > 0.8). */
    private boolean live() {
        return alpha > 0.8f;
    }

    @Override
    protected boolean collidesSoul() {
        if (G.inv > 0 || !live()) {
            return false;
        }
        return Math.abs(soul.x - x) < halfWidth() + Soul.HALF;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        // GML: jitter around the spawn point (giga jitters harder + slows ss).
        double j = giga ? 12 : 6;
        x = thisx + GMLHelper.random(j) - GMLHelper.random(j);
        y = thisy + GMLHelper.random(j) - GMLHelper.random(j);
        if (giga) {
            ss -= 0.5;
        }
        ss++;
        if (!sfxPlayed && ss >= 0) {
            sfxPlayed = true;
            double pitch = giga ? 0.8 : 0.9 + GMLHelper.random(0.2);
            util.Audio.play("/audio/mus_sfx_a_lithit.ogg", 0.10, pitch);
        }
        if (ss > 2) {
            alpha -= 0.1f;
            if (alpha < 0.5f) {
                scale = Math.max(0.2, scale - 0.2);
            }
            if (alpha < 0.1f) {
                manager.destroy(this);
                return;
            }
        }
        hue = (hue + 0.04) % 1.0;
        if (++frame > 3) {
            frame = 0;
        }
        if (collidesSoul()) {
            onHitSoul();
        }
    }

    @Override
    public void render(Graphics2D g) {
        BufferedImage img = Assets.sprite("spr_rainbowbolt_" + (frame < 2 ? 0 : 1));
        if (img == null) {
            img = Assets.sprite("spr_rainbowbolt_0");
        }
        if (img == null) {
            return;
        }
        int w = (int) (img.getWidth() * scale);
        int h = (int) (img.getHeight() * scale);
        int dx = (int) (x - w / 2.0);          // origin (16,0): top-centre
        int dy = (int) y;
        Composite oc = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, alpha)));
        // Rainbow column behind the white lightning core.
        Color c = Color.getHSBColor((float) hue, 0.85f, 1f);
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 150));
        g.fillRect(dx, dy, w, h);
        g.drawImage(img, dx, dy, w, h, null);
        // GML: a solid white flash fills the column while it is bright.
        if (alpha > 0.8f) {
            g.setColor(Color.WHITE);
            g.fillRect(dx + w / 4, dy, w / 2, h);
        }
        g.setComposite(oc);
    }
}
