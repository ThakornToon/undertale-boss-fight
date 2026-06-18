package bullet.bones;

import battle.KarmaTicker;
import battle.Soul;
import bullet.Bullet;
import core.EntityManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * A looping vertical bone segment (GML: {@code obj_boneloop_v}, 10x40). Travels
 * up or down on {@code vspeed} and wraps around the combat box, so chains of them
 * form the endless right-side bone columns of a_type 17/18 while the blue soul
 * rides platforms past them.
 *
 * // GML: obj_boneloop_v (spr_s_boneloop)
 */
public final class BoneLoopV extends Bullet {

    private static final double W = 10;
    private static final double H = 40;

    private final KarmaTicker karma;

    public BoneLoopV(EntityManager manager, Soul soul, KarmaTicker karma) {
        super(manager, soul);
        this.karma = karma;
        this.dmg = 1;
        this.vspeed = 4;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        integrateMotion();
        if (vspeed < 0 && y < G.idealborder[2] - H) {
            y = G.idealborder[3];
        }
        if (vspeed > 0 && y > G.idealborder[3]) {
            y = G.idealborder[2] - H;
        }
        if (G.inv <= 0
                && soul.x + Soul.HALF > x && soul.x - Soul.HALF < x + W
                && soul.y + Soul.HALF > y && soul.y - Soul.HALF < y + H) {
            soul.hurt(dmg);
            karma.addKarma(5);   // GML obj_boneloop_v: innate_karma = 5
        }
        if ((hspeed < 0 && x < -20) || (hspeed > 0 && x > 660)) {
            manager.destroy(this);
        }
    }

    @Override
    protected double boxTop() {
        return y;
    }

    @Override
    protected double boxBottom() {
        return y + H;
    }

    @Override
    public void render(Graphics2D g) {
        // Clip to the box vertically so wrapped segments don't poke outside it.
        int top = (int) Math.max(y, G.idealborder[2]);
        int bottom = (int) Math.min(y + H, G.idealborder[3]);
        if (bottom <= top) {
            return;
        }
        BufferedImage img = Assets.sprite("spr_s_boneloop_0");
        if (img != null && bottom - top == (int) H) {
            g.drawImage(img, (int) x, (int) y, (int) W, (int) H, null);
            return;
        }
        g.setColor(Color.WHITE);
        g.fillRect((int) x + 2, top, (int) W - 4, bottom - top);
    }
}
