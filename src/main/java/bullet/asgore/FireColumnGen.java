package bullet.asgore;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Graphics2D;
import util.GMLHelper;

/**
 * Scatters a grid of {@link HelixFireGen} above the box so several braided fire
 * columns rain down at once (GML: {@code obj_asgoreattackgen}). {@code t} 1 is the
 * plain column rain; {@code t} 2 makes each column weave wider/faster. The grid is
 * two stacked rows of four columns, jittered horizontally and vertically like the
 * GML's {@code random()} offsets. One-shot: it spawns the column generators, which
 * outlive it.
 *
 * // GML: obj_asgoreattackgen (t == 1 / t == 2)
 */
public final class FireColumnGen extends AttackPattern {

    private final Soul soul;
    public int t = 2;

    public FireColumnGen(EntityManager manager, Soul soul, int t) {
        super(manager);
        this.soul = soul;
        this.t = t;
        this.depth = 50;
    }

    @Override
    public void update() {
        double gil = G.idealborder[0];
        double gir = G.idealborder[1];
        double giw = gir - gil;
        double giu = G.idealborder[2];
        double rowGap = (t == 1) ? 360 : 340;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 4; j++) {
                double cx = gil + j * giw / 4 + 20 - GMLHelper.random(10) + 10 * j;
                double cy = giu - 80 - rowGap * i - GMLHelper.random(90);
                HelixFireGen gen = new HelixFireGen(manager, soul);
                gen.x = cx;
                gen.y = cy;
                if (t == 2) {
                    gen.mysf = 5.5;
                    gen.mysv = 3.5;
                    gen.mys = GMLHelper.random(2);
                }
                manager.add(gen);
            }
        }
        manager.destroy(this);   // one-shot; the column generators carry on
    }

    @Override
    public void render(Graphics2D g) {
    }
}
