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
 * A ring of spears that spins while closing in on where the heart was (GML:
 * {@code obj_rotspeargen} + the {@code obj_rotspear} spears it positions). {@code num}
 * (7–8) spears sit on a circle of radius {@code rr} around a fixed centre (the heart's
 * position at spawn); each frame the radius shrinks and the ring rotates (the spin
 * eases from ±8 toward ±2), so the player must slip out before it collapses.
 *
 * <p>Spears are managed internally (not separate entities) and point inward; once the
 * ring has collapsed it stops hurting and is culled.
 *
 * // GML: obj_rotspeargen (+ obj_rotspear)
 */
public final class RotSpearRing extends AttackPattern {

    private final Soul soul;
    private final double cx;
    private final double cy;
    private final int num;
    private double rr;
    private double curang;
    private double rotspeed;
    private final double rotmin;
    private boolean dead;       // collapsed: spears no longer hurt

    /** type 0/1 = 7 spears r220; 2/3 = 8 spears r230 with a random start angle. */
    public RotSpearRing(EntityManager manager, Soul soul, int type, int dmg) {
        super(manager);
        this.soul = soul;
        this.dmg = dmg;
        this.depth = -200;
        this.cx = soul.x;
        this.cy = soul.y;
        switch (type) {
            case 1 -> { rotspeed = -8; rotmin = -2; num = 7; rr = 220; }
            case 2 -> { rotspeed = 8;  rotmin = 2;  num = 8; rr = 230; curang = GMLHelper.random(360); }
            case 3 -> { rotspeed = -8; rotmin = -2; num = 8; rr = 230; curang = GMLHelper.random(360); }
            default -> { rotspeed = 8; rotmin = 2;  num = 7; rr = 220; }
        }
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        // Ease the spin toward its minimum, converge the radius, rotate.
        if (rotspeed > rotmin) {
            rotspeed -= 0.2;
        } else if (rotspeed < rotmin) {
            rotspeed += 0.2;
        }
        if (rr < 8) {
            dead = true;
            rr++;
            rotspeed *= 0.8;
        }
        if (rr < -20) {
            manager.destroy(this);
            return;
        }
        rr -= 4;
        curang += rotspeed;

        if (!dead && G.inv <= 0) {
            for (int i = 0; i < num; i++) {
                double a = curang + i / (double) num * 360;
                double sx = cx + GMLHelper.lengthdir_x(rr, a);
                double sy = cy + GMLHelper.lengthdir_y(rr, a);
                double inward = GMLHelper.point_direction(sx, sy, cx, cy);
                double xoff = GMLHelper.lengthdir_x(25, inward);
                double yoff = GMLHelper.lengthdir_y(25, inward);
                if (FollowSpear.segDistance(sx - xoff / 2, sy - yoff / 2, sx + xoff, sy + yoff,
                        soul.x, soul.y) < Soul.HALF) {
                    soul.hurt(dmg);
                    break;
                }
            }
        }
    }

    @Override
    public void render(Graphics2D g) {
        float a = (float) Math.max(0, Math.min(1, dead ? 0.4 : 1));
        for (int i = 0; i < num; i++) {
            double ang = curang + i / (double) num * 360;
            double sx = cx + GMLHelper.lengthdir_x(rr, ang);
            double sy = cy + GMLHelper.lengthdir_y(rr, ang);
            double inward = GMLHelper.point_direction(sx, sy, cx, cy);
            AffineTransform old = g.getTransform();
            g.translate(sx, sy);
            g.rotate(-Math.toRadians(inward));
            Stroke os = g.getStroke();
            g.setColor(new Color(1f, 1f, 1f, a));
            g.setStroke(new BasicStroke(5f));
            g.drawLine(-18, 0, 12, 0);
            g.fillPolygon(new int[] { 10, 24, 10 }, new int[] { -8, 0, 8 }, 3);
            g.setStroke(os);
            g.setTransform(old);
        }
    }
}
