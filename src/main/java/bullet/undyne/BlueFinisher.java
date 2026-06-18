package bullet.undyne;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;

/**
 * The blue finisher spear thrown at the end of a GREEN turn, on the switch to RED
 * (GML: {@code obj_undyneboss}'s {@code xbullet} on the green→red swap). Undyne turns
 * the player RED so they can move, then hurls one fast spear straight across the
 * (unchanged) box for them to dodge — not block — before the turn ends.
 *
 * // GML: blt_parent_noborder with spr_undynespear_r on the green→red transition
 */
public final class BlueFinisher extends AttackPattern {

    private final Soul soul;

    public BlueFinisher(EntityManager manager, Soul soul, boolean fromLeft, double y, int dmg) {
        super(manager);
        this.soul = soul;
        this.dmg = dmg;
        this.depth = -200;
        this.y = y;
        // GML blt_parent_noborder: it flies in from the edge of the screen.
        if (fromLeft) {
            x = -45;
            hspeed = 13;
        } else {
            x = core.Game.WIDTH + 45;
            hspeed = -13;
        }
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        x += hspeed;
        // A horizontal spear: dodge it by moving the (now free) heart off its line.
        if (G.inv <= 0 && Math.abs(soul.y - y) < Soul.HALF + 5 && Math.abs(soul.x - x) < 22) {
            soul.hurt(dmg);
        }
        if ((hspeed > 0 && x > core.Game.WIDTH + 60) || (hspeed < 0 && x < -60)) {
            manager.destroy(this);
        }
    }

    @Override
    public void render(Graphics2D g) {
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        if (hspeed < 0) {
            g.rotate(Math.PI);
        }
        Stroke os = g.getStroke();
        g.setColor(new Color(0x40 / 255f, 0xC0 / 255f, 1f, 1f));
        g.setStroke(new BasicStroke(5f));
        g.drawLine(-22, 0, 14, 0);
        g.fillPolygon(new int[] { 12, 30, 12 }, new int[] { -8, 0, 8 }, 3);
        g.setStroke(os);
        g.setTransform(old);
    }
}
