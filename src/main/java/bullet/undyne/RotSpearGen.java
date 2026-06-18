package bullet.undyne;

import battle.Soul;
import bullet.Generator;
import core.EntityManager;
import util.GMLHelper;

/**
 * Spawns converging {@link RotSpearRing}s on a cadence (GML:
 * {@code obj_rotspeargen_gen}). {@code genType 0} alternates clockwise/counter rings
 * every 27 frames; {@code genType 1} fires random-start rings (type 2/3) every 24.
 *
 * // GML: obj_rotspeargen_gen
 */
public final class RotSpearGen extends Generator {

    private final Soul soul;
    private final int genType;
    private int t;          // ring type alternator (GML t)

    public RotSpearGen(EntityManager manager, Soul soul, int genType, int dmg) {
        super(manager);
        this.soul = soul;
        this.genType = genType;
        this.dmg = dmg;
        this.count = -1;
        this.life = -1;
        this.rate = genType == 1 ? 24 : 27;
    }

    @Override
    protected void emit() {
        int ringType;
        if (genType == 1) {
            ringType = GMLHelper.choose(new int[] { 2, 3 });
        } else {
            ringType = t;
            t = (t == 0) ? 1 : 0;
        }
        manager.add(new RotSpearRing(manager, soul, ringType, dmg));
    }

    @Override
    public void update() {
        if (G.turntimer <= 4) {
            manager.destroy(this);
            return;
        }
        super.update();
    }
}
