package bullet.asgore;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Graphics2D;
import util.GMLHelper;

/**
 * Throws a single {@link HandBullet} from a random direction on a steady cadence
 * (GML: {@code obj_randomhandgen}). Every {@code factor} frames it picks one of four
 * entry headings, so a relentless drizzle of fire-trailing hands keeps re-aiming at
 * the soul. A smaller {@code factor} (40 → 30 across the fight) spawns them faster.
 *
 * // GML: obj_randomhandgen
 */
public final class RandomHandGen extends AttackPattern {

    private final Soul soul;
    public int factor = 40;

    private int timer = 1;

    public RandomHandGen(EntityManager manager, Soul soul, int factor) {
        super(manager);
        this.soul = soul;
        this.factor = factor;
        this.depth = 50;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        if (--timer > 0) {
            return;
        }
        timer = factor;
        double l = G.idealborder[0];
        double r = G.idealborder[1];
        double t = G.idealborder[2];
        double b = G.idealborder[3];
        switch (GMLHelper.choose(new int[] { 1, 2, 3, 4 })) {
            case 1 -> hand(1, l - 100, t + 20);    // down-right from the left
            case 2 -> hand(2, r + 100, b - 20);    // up-left from the right
            case 3 -> hand(3, r - 50, t - 150);    // down from above
            default -> hand(4, l - 10, b + 120);   // up from below
        }
    }

    private void hand(int handType, double x, double y) {
        HandBullet h = new HandBullet(manager, soul, handType);
        h.x = x;
        h.y = y;
        manager.add(h);
    }

    @Override
    public void render(Graphics2D g) {
    }
}
