package bullet.asriel;

import battle.Soul;
import bullet.Bullet;
import core.EntityManager;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;
import util.GMLHelper;

/**
 * A small star flung outward when a falling {@link StormStar} detonates (GML:
 * {@code obj_regstar_blt}). It starts slow and <b>accelerates</b> (GML used a negative
 * {@code friction}, which adds to {@code speed} every step), spreading the ring wider
 * the longer it lives. These are the bullets that actually hurt during STAR BLAZING.
 *
 * // GML: obj_regstar_blt
 */
public final class RegStar extends Bullet {

    private final double dir;
    private double curSpeed;
    private final double accel;     // GML: -friction (negative friction → speed grows)
    private double siner;

    public RegStar(EntityManager manager, Soul soul, double dir, double speed,
                   double friction, int dmg) {
        super(manager, soul);
        this.dir = dir;
        this.curSpeed = speed;
        this.accel = -friction;
        this.dmg = dmg;
        this.depth = -5;
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
    public void update() {
        siner++;
        curSpeed += accel;
        if (curSpeed < 0) {
            curSpeed = 0;
        }
        hspeed = GMLHelper.lengthdir_x(curSpeed, dir);
        vspeed = GMLHelper.lengthdir_y(curSpeed, dir);
        super.update();         // turntimer cull + integrate + collide + offscreen cull
        // The ring fragments that fly up/down out of the box shouldn't linger off-screen
        // (Bullet.cullOffscreen only culls horizontally).
        if (y < G.idealborder[2] - 150 || y > G.idealborder[3] + 120) {
            manager.destroy(this);
        }
    }

    @Override
    public void render(Graphics2D g) {
        BufferedImage img = Assets.sprite("spr_regstar_0");
        double sc = 0.8;
        if (img != null) {
            int w = (int) (img.getWidth() * sc);
            int h = (int) (img.getHeight() * sc);
            g.drawImage(img, (int) (x - w / 2.0), (int) (y - h / 2.0), w, h, null);
        } else {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect((int) x - 3, (int) y - 3, 6, 6);
        }
    }
}
