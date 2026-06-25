package bullet.asriel;

import battle.Soul;
import bullet.Bullet;
import core.EntityManager;
import core.Game;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;
import util.GMLHelper;

/**
 * A residual diamond spark thrown into the box when the Chaos Saber finale shatters the
 * swords (GML: {@code obj_swordtwinkle}, parent {@code obj_asbulletparent} — so it is a
 * real bullet). It drifts outward and down, picking up speed, then fades after ~40 frames.
 * These are the <b>only</b> bullets Chaos Saber spawns; the regular slashes are pure blade
 * sweeps.
 *
 * // GML: obj_swordtwinkle
 */
public final class SwordTwinkle extends Bullet {

    private float alpha = 1f;
    private int life = 40;          // GML alarm[0] = 40 → begin fading

    public SwordTwinkle(EntityManager manager, Soul soul, double x, double y, int dmg) {
        super(manager, soul);
        this.x = x;
        this.y = y;
        this.dmg = dmg;
        this.depth = -7;
        // GML: outward+down drift, flipped on the left half so it spreads from centre.
        double gr = 0.05 + GMLHelper.random(0.1);
        this.gravity = (x < Game.WIDTH / 2.0) ? -gr : gr;
        this.gravityDirection = 180;     // horizontal spread; GML jitters this ±40
        this.vspeed = 1 + GMLHelper.random(1) - GMLHelper.random(2);
        this.hspeed = (x < Game.WIDTH / 2.0 ? -1 : 1) * GMLHelper.random(1.5);
    }

    @Override
    protected double halfWidth() {
        return 6;
    }

    @Override
    protected double boxTop() {
        return y - 6;
    }

    @Override
    protected double boxBottom() {
        return y + 6;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        integrateMotion();
        if (collidesSoul()) {
            onHitSoul();
        }
        if (--life <= 0) {
            alpha -= 0.05f;
            if (alpha <= 0) {
                manager.destroy(this);
            }
        }
    }

    @Override
    public void render(Graphics2D g) {
        BufferedImage img = Assets.sprite("spr_asriel_swordtwinkle_0");
        Composite oc = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, alpha)));
        if (img != null) {
            int w = img.getWidth() * 2;
            int h = img.getHeight() * 2;
            g.drawImage(img, (int) (x - w / 2.0), (int) (y - h / 2.0), w, h, null);
        } else {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect((int) x - 3, (int) y - 3, 6, 6);
        }
        g.setComposite(oc);
    }
}
