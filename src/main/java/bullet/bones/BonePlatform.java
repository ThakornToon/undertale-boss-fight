package bullet.bones;

import battle.Soul;
import bullet.Bullet;
import core.EntityManager;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * A rideable bone platform (GML: {@code obj_boneplat}, spawned via
 * {@code scr_hplat(height, hspeed, xfactor, len)}). Deals no damage — the blue
 * soul lands on it from above and is carried along while it slides, which is how
 * the player crosses the bone-floor and 3-platform jump attacks.
 *
 * // GML: obj_boneplat + scr_hplat.gml
 */
public final class BonePlatform extends Bullet {

    /** Half-length in px (GML: len). */
    public double len = 50;
    /** GML: jud — a_type 17/18 platform that drifts right and bounces in the box. */
    public boolean jud;
    private int judTimer;
    private boolean lock;

    public BonePlatform(EntityManager manager, Soul soul) {
        super(manager, soul);
        this.dmg = 0;
    }

    /** GML: scr_hplat(height, hspeed, xfactor, len). */
    public static BonePlatform hplat(EntityManager manager, Soul soul,
                                     double height, double hspeed, double xfactor, double len) {
        BonePlatform p = new BonePlatform(manager, soul);
        p.y = G.idealborder[3] - height;
        p.hspeed = hspeed;
        p.x = 320 - xfactor * hspeed;
        p.len = len;
        manager.add(p);
        return p;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        integrateMotion();
        if (jud) {
            judTimer++;
            if (judTimer >= 5 && judTimer <= 20) {
                hspeed += 0.25;
            }
            if (judTimer == 21) {
                hspeed = 3;
            }
            if (x > G.idealborder[1] - len && hspeed > 0) {
                hspeed = -hspeed;
            }
            if (x < G.idealborder[0] + len && hspeed < 0) {
                hspeed = -hspeed;
            }
        }
        carrySoul();
        if ((x < -len && hspeed < 0) || (x > 640 + len && hspeed > 0)
                || (vspeed > 0 && y > G.idealborder[3])) {
            manager.destroy(this);
        }
    }

    /** GML: land the falling blue soul on the deck and drag it with the platform. */
    private void carrySoul() {
        boolean over = soul.x > x - len + 2 && soul.x < x + len - 2;
        boolean atDeck = soul.y + Soul.HALF >= y - 4 && soul.y + Soul.HALF <= y + 8;
        if (over && atDeck && soul.vspeed >= 0 && soul.mode.isBlue()) {
            soul.y = y - Soul.HALF;
            soul.vspeed = 0;
            soul.jumpStage = Soul.GROUNDED;
            lock = true;
            soul.x += hspeed;
            soul.x = Math.max(G.idealborder[0] + 5, Math.min(G.idealborder[1] - Soul.HALF, soul.x));
        } else if (lock) {
            // Walked or jumped off: hand the soul back to gravity.
            if (soul.vspeed >= 0 && soul.grounded() && soul.y + Soul.HALF < G.idealborder[3] - 1) {
                soul.jumpStage = Soul.AIRBORNE;
            }
            lock = false;
        }
    }

    @Override
    protected double boxTop() {
        return y;
    }

    @Override
    protected double boxBottom() {
        return y + 6;
    }

    @Override
    public void render(Graphics2D g) {
        int x0 = (int) (x - len);
        int w = (int) (len * 2);
        // The reference fight draws a little bone table: white-edged green deck
        // with two bone legs hanging underneath.
        g.setColor(Color.WHITE);
        g.fillRect(x0, (int) y - 4, w, 2);
        g.setColor(new Color(0x2E, 0x8B, 0x2E));
        g.fillRect(x0, (int) y - 2, w, 5);
        g.setColor(Color.WHITE);
        g.fillRect(x0, (int) y + 3, w, 2);
        int legH = 12;
        g.fillRect(x0 + 3, (int) y + 5, 3, legH);
        g.fillRect(x0 + w - 6, (int) y + 5, 3, legH);
        g.fillRect(x0 + 1, (int) y + 5 + legH, 7, 3);       // little feet caps
        g.fillRect(x0 + w - 8, (int) y + 5 + legH, 7, 3);
    }
}
