package bullet.asgore;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Graphics2D;
import util.GMLHelper;

/**
 * Emits a braided column of falling fire (GML: {@code obj_helixfiregen}). Every 2
 * frames it drops a mirrored pair of {@link HelixFire} (opposite {@code sv} sign) so
 * they weave around each other as they fall, building one wiggling fire column. The
 * fire-column attack ({@code obj_asgoreattackgen}) scatters a grid of these above
 * the box.
 *
 * // GML: obj_helixfiregen
 */
public final class HelixFireGen extends AttackPattern {

    private final Soul soul;

    /** GML knobs (defaults match obj_helixfiregen Create). */
    public double mys;
    public double mysadd = 0.1;
    public double mysv = 4;
    public double mysf = 4;

    private final double selfSpeed = GMLHelper.random(1.5);
    private final double selfSpeed2 = selfSpeed - 0.1 + GMLHelper.random(0.1);
    private int timer = 1;
    private int count;

    public HelixFireGen(EntityManager manager, Soul soul) {
        super(manager);
        this.soul = soul;
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
        timer = 2;
        mys += mysadd;
        spawn(mysv, 5.5 + selfSpeed);
        spawn(-mysv, 5.5 + selfSpeed2);
        if (++count > 15) {
            manager.destroy(this);
        }
    }

    private void spawn(double sv, double vspeed) {
        HelixFire f = new HelixFire(manager, soul);
        f.x = x;
        f.y = y;
        f.s = mys;
        f.sf = mysf;
        f.sv = sv;
        f.vspeed = vspeed;
        manager.add(f);
    }

    @Override
    public void render(Graphics2D g) {
        // Invisible spawn point.
    }
}
