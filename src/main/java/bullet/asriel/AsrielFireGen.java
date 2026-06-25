package bullet.asriel;

import battle.Soul;
import bullet.Generator;
import core.EntityManager;
import util.GMLHelper;

/**
 * Drizzles {@link AsrielFire} motes down through the combat box. Drives the calm
 * Fire-Magic intro ("It's the end.") and stands in for the not-yet-ported gauntlet
 * patterns so each Part-A turn is survivable while the boss is built out.
 *
 * <p>{@code density} scales the rain rate; the fire columns drift slowly left↔right so
 * there's no fixed safe spot.
 *
 * // GML: obj_1sidegen (fire) — intro + placeholder generator
 */
public final class AsrielFireGen extends Generator {

    private final Soul soul;
    private final int dmgVal;
    private double sweep;

    public AsrielFireGen(EntityManager manager, Soul soul, int rate, int dmg) {
        super(manager);
        this.soul = soul;
        this.rate = rate;
        this.dmgVal = dmg;
        this.depth = 50;
    }

    @Override
    protected void emit() {
        double l = G.idealborder[0];
        double r = G.idealborder[1];
        double top = G.idealborder[2];
        sweep += 0.6;
        // Two drifting drop points so the rain has no static gap.
        for (int k = 0; k < 2; k++) {
            double base = l + (r - l) * (0.5 + 0.4 * Math.sin(sweep + k * Math.PI));
            double px = GMLHelper.clamp(base + GMLHelper.random(40) - 20, l + 6, r - 6);
            double v = 3 + GMLHelper.random(2.5);
            manager.add(new AsrielFire(manager, soul, px, top - 6, v, dmgVal));
        }
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        super.update();
    }
}
