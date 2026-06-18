package bullet.undyne;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Graphics2D;
import util.GMLHelper;

/**
 * The spiraling follow-spear barrage (GML: {@code obj_followspeargen_2}, type 1). It
 * spawns rings of six {@link FollowSpear2}s around the heart, each ring rotated on
 * from the last by a random step and fired a touch faster than the previous, so the
 * spears arrive as a dense converging spiral.
 *
 * // GML: obj_followspeargen_2 (type 1)
 */
public final class FollowSpear2Gen extends AttackPattern {

    private static final int NUM = 6;
    private static final double RR = 180;

    private final Soul soul;
    private double curang;
    private double rate = 20;
    private int cooldown = 1;

    public FollowSpear2Gen(EntityManager manager, Soul soul, int dmg) {
        super(manager);
        this.soul = soul;
        this.dmg = dmg;
        this.depth = 0;
    }

    @Override
    public void update() {
        if (G.turntimer <= 3) {
            manager.destroy(this);
            return;
        }
        if (--cooldown > 0) {
            return;
        }
        for (int i = 0; i < NUM; i++) {
            double a = curang + i / (double) NUM * 360;
            double hx = soul.x + GMLHelper.lengthdir_x(RR, a);
            double hy = soul.y + GMLHelper.lengthdir_y(RR, a);
            manager.add(new FollowSpear2(manager, soul, hx, hy, true, dmg));
        }
        if (rate > 10) {
            rate--;
        }
        curang += 10 + GMLHelper.choose(new int[] { 10, 20, 30 });   // spiral step
        cooldown = (int) rate;
    }

    @Override
    public void render(Graphics2D g) {
        // invisible emitter
    }
}
