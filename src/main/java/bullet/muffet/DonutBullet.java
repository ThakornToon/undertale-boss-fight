package bullet.muffet;

import battle.Soul;
import core.EntityManager;

/**
 * A bouncing donut (GML: {@code obj_donutbullet}, a child of {@code obj_spiderbullet}).
 * It slides horizontally like a spider but also drifts vertically and bounces off the
 * top and bottom of the web band, squashing on each impact. The launch direction is
 * set by the strand it spawns on: strand 1 (top) falls, strand 3 (bottom) rises,
 * strand 2 (middle) stays level.
 *
 * // GML: obj_donutbullet
 */
public final class DonutBullet extends SpiderBullet {

    public DonutBullet(EntityManager manager, Soul soul) {
        super(manager, soul);
        this.spriteName = "spr_donutbullet_0";
    }

    @Override
    protected void arm() {
        // GML alarm[0]: spawn flush at xmid±xlen*2 (no +40 lead the spider has), then
        // a vertical kick for the top/bottom strands.
        y = web.yzero + (choice - 1) * web.yspace;
        if (side == 0) {
            x = web.xmid - web.xlen * 2;
            hspeed = speedfactor;
        } else {
            x = web.xmid + web.xlen * 2;
            hspeed = -speedfactor;
        }
        if (choice == 1) {
            vspeed = Math.abs(hspeed) / 2;   // top strand → arc downward
        } else if (choice == 3) {
            vspeed = -Math.abs(hspeed) / 2;  // bottom strand → arc upward
        }
    }

    @Override
    protected void motionStep() {
        // GML Step: recover from the squash, and bounce off the band edges.
        if (imageYscale < 1) {
            imageYscale = Math.min(1, imageYscale + 0.1);
        }
        double bandBottom = web.yzero + (web.yamt - 1) * web.yspace + 10;
        double bandTop = web.yzero - 10;
        if (vspeed > 0 && y > bandBottom) {
            y -= vspeed;
            vspeed = -vspeed;
            imageYscale = 0.6;
        } else if (vspeed < 0 && y < bandTop) {
            y -= vspeed;
            vspeed = -vspeed;
            imageYscale = 0.6;
        }
    }
}
