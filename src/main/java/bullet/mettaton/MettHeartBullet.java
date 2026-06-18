package bullet.mettaton;

import battle.Soul;
import bullet.Bullet;
import core.EntityManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * The M-marked heart a parasol Mettaton flings up when it reaches the bottom unshot
 * (GML: {@code spr_smheartbullet}). Travels straight up and off the top; touching it
 * hurts — the punishment for letting a parasol slip past.
 *
 * // GML: obj_mettattackgen thrown-heart bullets
 */
public final class MettHeartBullet extends Bullet {

    private static final double HALF = 12; // GML: the thrown heart is scaled up ×1.5

    public MettHeartBullet(EntityManager manager, Soul soul, double x, double y,
                           double hspeed, double vspeed) {
        super(manager, soul);
        this.x = x;
        this.y = y;
        this.hspeed = hspeed;
        this.vspeed = vspeed;
        this.dmg = 4;
        this.depth = -1;
    }

    @Override
    protected double halfWidth() {
        return HALF;
    }

    @Override
    protected double boxTop() {
        return y - HALF;
    }

    @Override
    protected double boxBottom() {
        return y + HALF;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        x += hspeed;
        y += vspeed;
        if (G.inv <= 0 && Math.abs(soul.x - x) < HALF + Soul.HALF
                && Math.abs(soul.y - y) < HALF + Soul.HALF) {
            onHitSoul();
        }
        // Off any screen edge.
        if (x < -24 || x > 664 || y < -24 || y > 504) {
            manager.destroy(this);
        }
    }

    @Override
    public void render(Graphics2D g) {
        BufferedImage img = Assets.sprite("spr_smheartbullet_0");
        if (img != null) {
            g.drawImage(img, (int) (x - 12), (int) (y - 12), 24, 24, null);
        } else {
            g.setColor(Color.WHITE);
            g.fillRect((int) (x - 6), (int) (y - 6), 12, 12);
        }
    }
}
