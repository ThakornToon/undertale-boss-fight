package bullet.asriel;

import battle.Soul;
import bullet.Bullet;
import core.EntityManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;
import util.GMLHelper;

/**
 * A Chaos Buster bolt (GML: {@code obj_gunarm_bolt}). It launches straight at the heart,
 * then after 2 frames snaps to its fanned spread angle and flies straight at speed 20 —
 * so a wave reads as "aimed at you, then fans out."
 *
 * // GML: obj_gunarm_bolt
 */
public final class GunBolt extends Bullet {

    private double dir;             // current heading (GML direction)
    private final double spreadDir; // GML thisd: the heading after the 2-frame redirect
    private final double speed;
    private int age;

    public GunBolt(EntityManager manager, Soul soul, double x, double y,
                   double aimDir, double spreadDir, double speed, int dmg) {
        super(manager, soul);
        this.x = x;
        this.y = y;
        this.dir = aimDir;
        this.spreadDir = spreadDir;
        this.speed = speed;
        this.dmg = dmg;
        this.depth = -6;
    }

    @Override
    protected double halfWidth() {
        return 5;
    }

    @Override
    protected double boxTop() {
        return y - 5;
    }

    @Override
    protected double boxBottom() {
        return y + 5;
    }

    @Override
    protected boolean collidesSoul() {
        if (G.inv > 0) {
            return false;
        }
        return Math.abs(soul.x - x) < halfWidth() + Soul.HALF
                && Math.abs(soul.y - y) < halfWidth() + Soul.HALF;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        age++;
        if (age == 2) {
            dir = spreadDir;        // GML alarm[0]: snap to the spread heading
        }
        hspeed = GMLHelper.lengthdir_x(speed, dir);
        vspeed = GMLHelper.lengthdir_y(speed, dir);
        x += hspeed;
        y += vspeed;
        if (collidesSoul()) {
            onHitSoul();
        }
        if (x < -60 || x > 700 || y < -60 || y > 540) {
            manager.destroy(this);
        }
    }

    @Override
    public void render(Graphics2D g) {
        BufferedImage img = Assets.sprite("spr_asriel_gunbolt_0");
        if (img != null) {
            int w = img.getWidth() * 2;
            int h = img.getHeight() * 2;
            g.drawImage(img, (int) (x - w / 2.0), (int) (y - h / 2.0), w, h, null);
        } else {
            g.setColor(new Color(120, 220, 255));
            g.fillOval((int) x - 5, (int) y - 5, 10, 10);
            g.setColor(Color.WHITE);
            g.fillOval((int) x - 2, (int) y - 2, 4, 4);
        }
    }
}
