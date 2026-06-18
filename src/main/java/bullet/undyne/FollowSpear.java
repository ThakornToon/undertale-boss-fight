package bullet.undyne;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import util.GMLHelper;

/**
 * A RED-soul follow spear (GML: {@code obj_spearbullet_follow}). It materialises
 * ~140px from the heart, spins down in place, then locks its angle and lunges
 * straight at where the heart is, accelerating as it goes. It hurts the heart only
 * once it has stopped spinning (the GML {@code rotspeed == 0} gate), so the spin is a
 * fair telegraph.
 *
 * // GML: obj_spearbullet_follow
 */
public final class FollowSpear extends AttackPattern {

    private final Soul soul;
    private double direction;
    private double speed;
    private double friction;
    private double rotspeed = 32;
    private double alpha;
    private boolean lunging;

    public FollowSpear(EntityManager manager, Soul soul, int dmg) {
        super(manager);
        this.soul = soul;
        this.dmg = dmg;
        this.depth = -200;
        util.Audio.play("/audio/snd_spearappear.wav");  // GML: each spear shings in

        // GML Create: appear next to the heart, then push 140px out along a random-ish
        // direction; drift in that direction at speed 4 with friction 0.2.
        x = soul.x - 4 + GMLHelper.random(8);
        y = soul.y - 4 + GMLHelper.random(8);
        double offsetdir = GMLHelper.point_direction(x, y, soul.x, soul.y);
        x += GMLHelper.lengthdir_x(140, offsetdir);
        y += GMLHelper.lengthdir_y(140, offsetdir);
        direction = offsetdir;
        speed = 4;
        friction = 0.2;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        if (alpha < 1) {
            alpha += 0.05;
        }
        // GML: spin down, then once stopped and slow, charge the heart.
        imageAngle -= rotspeed;
        if (rotspeed > 0) {
            rotspeed--;
        }
        if (!lunging && rotspeed == 0 && speed < 1) {
            direction = GMLHelper.point_direction(x, y, soul.x + 10, soul.y + 10);
            speed = 3;
            friction = -0.3;     // GML negative friction = accelerate
            imageAngle = direction;
            lunging = true;
        }
        // Integrate direction/speed with GM-style friction (toward 0, or away if neg).
        speed = Math.max(0, speed - friction);
        x += GMLHelper.lengthdir_x(speed, direction);
        y += GMLHelper.lengthdir_y(speed, direction);

        if (rotspeed == 0 && collidesSoul()) {
            soul.hurt(dmg);
        }
        // Cull once it has flown well outside the box (a lunge that missed).
        if (x < G.idealborder[0] - 120 || x > G.idealborder[1] + 120
                || y < G.idealborder[2] - 120 || y > G.idealborder[3] + 120) {
            manager.destroy(this);
        }
    }

    /** GML collision_line along the spear's length vs the heart. */
    private boolean collidesSoul() {
        if (G.inv > 0) {
            return false;
        }
        double xoff = GMLHelper.lengthdir_x(25, direction);
        double yoff = GMLHelper.lengthdir_y(25, direction);
        return segDistance(x - xoff / 2, y - yoff / 2, x + xoff, y + yoff,
                soul.x, soul.y) < Soul.HALF;
    }

    /** Distance from point (px,py) to segment (ax,ay)-(bx,by). */
    static double segDistance(double ax, double ay, double bx, double by,
                              double px, double py) {
        double dx = bx - ax;
        double dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : ((px - ax) * dx + (py - ay) * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        double cxp = ax + t * dx;
        double cyp = ay + t * dy;
        return Math.hypot(px - cxp, py - cyp);
    }

    @Override
    public void render(Graphics2D g) {
        if (alpha <= 0) {
            return;
        }
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.rotate(-Math.toRadians(imageAngle));
        Stroke os = g.getStroke();
        g.setColor(new Color(1f, 1f, 1f, (float) Math.min(1, alpha)));
        // Big bold arrow (the reference follow-spears are chunky).
        g.setStroke(new BasicStroke(7f));
        g.drawLine(-26, 0, 16, 0);
        g.fillPolygon(new int[] { 14, 34, 14 }, new int[] { -12, 0, 12 }, 3);
        g.setStroke(os);
        g.setTransform(old);
    }
}
