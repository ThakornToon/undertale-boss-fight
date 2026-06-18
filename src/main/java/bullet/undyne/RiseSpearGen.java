package bullet.undyne;

import battle.Soul;
import bullet.Generator;
import core.EntityManager;
import util.GMLHelper;

/**
 * Spawns a {@link RiseSpear} every {@code firingrate} frames at one of three columns
 * across the box, never the same column twice in a row (GML:
 * {@code obj_risespearbulletgen}, which picked {@code xset} of 0..2 avoiding the
 * previous). The GML columns were a fixed 23px apart from the left border; here they
 * spread evenly across the (narrow, tall) rising-spear box so all three are reachable.
 *
 * // GML: obj_risespearbulletgen
 */
public final class RiseSpearGen extends Generator {

    private final Soul soul;
    private int lastCol = -1;

    public RiseSpearGen(EntityManager manager, Soul soul, int firingrate, int dmg) {
        super(manager);
        this.soul = soul;
        this.dmg = dmg;
        this.rate = Math.max(1, firingrate);
        this.count = -1;
        this.life = -1;
    }

    @Override
    protected void emit() {
        // GML: xset = floor(random(3)), bumped if it repeats the last column.
        int col = GMLHelper.irandom(2);
        if (col == lastCol) {
            col = (col + 1) % 3;
        }
        lastCol = col;
        double left = G.idealborder[0];
        double right = G.idealborder[1];
        double bottom = G.idealborder[3];
        // 3 columns, each filling a third of the box.
        double colX = left + (right - left) * (col + 0.5) / 3.0;
        manager.add(new RiseSpear(manager, soul, colX, bottom, dmg));
        util.Audio.play("/audio/snd_spearrise.wav");  // GML: the rising-spear jab
    }

    @Override
    public void update() {
        if (G.turntimer <= 3) {
            manager.destroy(this);
            return;
        }
        super.update();
    }
    // No permanent column markers: each spear's tip emerges from its hole only as it
    // is about to fire (the telegraph), so an idle column shows nothing.
}
