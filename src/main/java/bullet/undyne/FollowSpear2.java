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
 * A ring-spawned follow spear (GML: {@code obj_followspear_2}). Spawned on a circle
 * around the heart by {@link FollowSpear2Gen}, it spins fast in place, then locks its
 * angle and lunges along it. With {@code fade} it self-retires shortly after lunging,
 * so a dense spiral barrage doesn't pile up.
 *
 * // GML: obj_followspear_2
 */
public final class FollowSpear2 extends AttackPattern {

    private final Soul soul;
    private double direction;
    private double speed;
    private double friction;
    private double rotspeed = 38;
    private double alpha;
    private int timer;
    private final boolean fade;
    private boolean lunging;
    private boolean deactivating;

    public FollowSpear2(EntityManager manager, Soul soul, double x, double y, boolean fade, int dmg) {
        super(manager);
        this.soul = soul;
        this.fade = fade;
        this.dmg = dmg;
        this.depth = -200;
        this.x = x;
        this.y = y;
        double offsetdir = GMLHelper.point_direction(x, y, soul.x, soul.y);
        this.imageAngle = offsetdir + 20;
        this.direction = offsetdir;
        this.speed = 4;
        this.friction = 0.2;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        if (!deactivating && alpha < 1) {
            alpha += 0.05;
        }
        imageAngle -= rotspeed;
        if (rotspeed > 0) {
            rotspeed -= 2;
        }
        if (rotspeed <= 0 && !lunging && speed < 1) {
            timer++;
            if (timer == 5) {
                speed = 8;
                friction = -0.3;       // accelerate
                direction = imageAngle; // lunge wherever the spin ended
                lunging = true;
                timer = 0;
            }
        }
        if (fade && speed >= 7) {
            timer++;
            if (timer >= 22) {
                deactivating = true;
            }
        }
        speed = Math.max(0, speed - friction);
        x += GMLHelper.lengthdir_x(speed, direction);
        y += GMLHelper.lengthdir_y(speed, direction);

        if (rotspeed <= 0 && !deactivating && G.inv <= 0) {
            double xoff = GMLHelper.lengthdir_x(25, direction);
            double yoff = GMLHelper.lengthdir_y(25, direction);
            if (FollowSpear.segDistance(x - xoff / 2, y - yoff / 2, x + xoff, y + yoff,
                    soul.x, soul.y) < Soul.HALF) {
                soul.hurt(dmg);
            }
        }
        if (deactivating) {
            alpha -= 0.25;
            if (alpha <= 0) {
                manager.destroy(this);
                return;
            }
        }
        if (x < G.idealborder[0] - 160 || x > G.idealborder[1] + 160
                || y < G.idealborder[2] - 160 || y > G.idealborder[3] + 160) {
            manager.destroy(this);
        }
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
        g.setStroke(new BasicStroke(6f));
        g.drawLine(-22, 0, 14, 0);
        g.fillPolygon(new int[] { 12, 30, 12 }, new int[] { -10, 0, 10 }, 3);
        g.setStroke(os);
        g.setTransform(old);
    }
}
