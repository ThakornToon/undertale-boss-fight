package bullet.muffet;

import battle.Soul;
import core.EntityManager;

/**
 * A spinning croissant (GML: {@code obj_croissant}, a child of
 * {@code obj_spiderbullet}). It launches in like a spider, then tumbles — spinning as
 * it goes and easing its speed by side — and flies far out past the web rim
 * ({@code xlen*5}) before despawning, which gives it its lazy, looping arc.
 *
 * // GML: obj_croissant
 */
public final class Croissant extends SpiderBullet {

    public Croissant(EntityManager manager, Soul soul) {
        super(manager, soul);
        this.spriteName = "spr_croissantr_0";
    }

    @Override
    protected void motionStep() {
        // GML Step: ease speed and spin opposite ways depending on the side.
        if (side == 0) {
            hspeed -= 0.25;
            imageAngleDeg += 8;
        } else {
            hspeed += 0.25;
            imageAngleDeg -= 8;
        }
    }

    @Override
    protected void cullStep() {
        // GML: the croissant flies far past the web (xlen*5) before vanishing.
        if (hspeed > 0 && x > web.xmid + web.xlen * 5) {
            manager.destroy(this);
        } else if (hspeed < 0 && x < web.xmid - web.xlen * 5) {
            manager.destroy(this);
        }
    }
}
