package bullet.asriel;

import battle.Soul;
import bullet.Generator;
import core.EntityManager;
import util.GMLHelper;

/**
 * STAR BLAZING / GALACTA BLAZING (GML: {@code obj_stormstar_gen}). Rains
 * {@link StormStar} down from the upper-right every 8 frames for ~170 frames, then
 * drops one slow {@code big} finale star that bursts into a dense ring. Each falling
 * star detonates into an accelerating ring of {@link RegStar} bullets over the box.
 *
 * <p>{@code hMode} (turn 9, GALACTA BLAZING) tightens the burst spread. The generator
 * is RED-soul (free movement); the player dodges the downpour of exploding stars.
 *
 * // GML: obj_stormstar_gen (Star Blazing / Galacta Blazing)
 */
public final class StarBlazingGen extends Generator {

    private final Soul soul;
    private final boolean hard;
    private int age;
    private boolean bigDropped;
    private final int dmgVal;

    public StarBlazingGen(EntityManager manager, Soul soul, boolean hMode, int dmg) {
        super(manager);
        this.soul = soul;
        this.hard = hMode;
        this.dmgVal = dmg;
        this.rate = 8;          // GML alarm[0]: a star every 8 frames
        this.depth = 50;
        util.Audio.play("/audio/mus_sfx_star.ogg", 1.0, 0.4);
    }

    @Override
    protected void emit() {
        // Every star is AIMED at a point inside the box so it is guaranteed to reach the
        // border and burst there (the old GML spawn rained off to the side and missed). The
        // streak still falls in diagonally from up-and-to-the-right for the classic look.
        double left = G.idealborder[0];
        double right = G.idealborder[1];
        double top = G.idealborder[2];
        double bottom = G.idealborder[3];
        double tx = left + 8 + GMLHelper.random(right - left - 16);
        double ty = top + 6 + GMLHelper.random((bottom - top) * 0.5);
        // Sweep in on a shallow diagonal from the upper-RIGHT (the classic ~215° rain). The
        // star is aimed THROUGH the box, but it bursts the instant it grazes the border.
        double sx = tx + 200 + GMLHelper.random(110);
        double sy = ty - 150 - GMLHelper.random(60);
        double dir = GMLHelper.point_direction(sx, sy, tx, ty);
        StormStar s = new StormStar(manager, soul, sx, sy, false, dir, ty);
        s.hMode = hard;
        s.dmg = dmgVal;
        manager.add(s);
    }

    @Override
    public void update() {
        age++;
        // GML alarm[1] at 170: stop the rain and drop the big finale star. It falls straight
        // down onto the box and bursts the instant it touches the TOP border — it does not
        // have to reach the centre.
        if (age == 170 && !bigDropped) {
            bigDropped = true;
            double cx = (G.idealborder[0] + G.idealborder[1]) / 2.0;
            StormStar big = new StormStar(manager, soul, cx, -180, true, 270, G.idealborder[2]);
            big.hMode = hard;
            big.dmg = dmgVal;
            manager.add(big);
        }
        if (age < 170) {
            super.update();     // keep raining stars
        } else if (age > 260 || G.turntimer <= 0) {
            manager.destroy(this);
        }
    }
}
