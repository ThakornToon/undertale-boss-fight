package bullet.asgore;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Graphics2D;

/**
 * Spawns a fixed set of {@link HandBullet}s in one burst (GML:
 * {@code obj_handbulletgen}). The hands streak in from outside the box on crossing
 * headings, each laying a fire trail that then curls into the soul. {@code type} 1 is
 * the gentle opening pair (turn 1); 2 and 3 add hands from above and below for the
 * heavier later sweeps. Spawn points are taken relative to the live combat box so the
 * hands always enter just off its edges.
 *
 * // GML: obj_handbulletgen (type 1..3)
 */
public final class HandBulletGen extends AttackPattern {

    private final Soul soul;
    public int type = 1;

    public HandBulletGen(EntityManager manager, Soul soul, int type) {
        super(manager);
        this.soul = soul;
        this.type = type;
        this.depth = 50;
    }

    @Override
    public void update() {
        double l = G.idealborder[0];
        double r = G.idealborder[1];
        double t = G.idealborder[2];
        double b = G.idealborder[3];
        switch (type) {
            case 1 -> {
                hand(2, r + 100, b - 20);     // up-left across from the right
                hand(1, l - 100, t + 20);     // down-right across from the left
            }
            case 2 -> {
                hand(2, r + 100, b + 10);
                hand(1, l - 100, t - 10);
                hand(4, l - 10, b + 120);     // up from below-left
                hand(3, r - 50, t - 150);     // down from above-right
            }
            default -> {
                hand(2, r + 100, b + 10);
                hand(5, l - 260, t + 40);     // straight right from far left
                hand(4, l - 10, b + 100);
                hand(3, r - 50, t - 200);
            }
        }
        manager.destroy(this);   // one burst; the hands carry on
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
