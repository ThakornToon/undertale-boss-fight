package bullet.mettaton;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Graphics2D;

/**
 * A generic falling-bullet generator for the late-show patterns — the central
 * plus-bomb column (example 1), the dense scattered plus-bombs + black-circle boxes
 * (examples 12–17). {@code perWave} bullets drop on a cadence across a configurable
 * fraction of the box width; each is a plus-bomb or a box per the flags.
 *
 * // GML: obj_mettattackgen (mixed bomb/box drops)
 */
public final class MixDropGen extends AttackPattern {

    private final Soul soul;
    private final int perWave;
    private final int interval;
    private final double speed;
    private final double xMinFrac;
    private final double xMaxFrac;
    private final boolean plus;
    private final boolean box;
    private int timer;

    /** {@code xMinFrac}/{@code xMaxFrac} are the spawn band as fractions (0..1) of the box width. */
    public MixDropGen(EntityManager manager, Soul soul, int perWave, int interval,
                      double speed, double xMinFrac, double xMaxFrac, boolean plus, boolean box) {
        super(manager);
        this.soul = soul;
        this.perWave = Math.max(1, perWave);
        this.interval = Math.max(6, interval);
        this.speed = speed;
        this.xMinFrac = xMinFrac;
        this.xMaxFrac = xMaxFrac;
        this.plus = plus;
        this.box = box;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        if (--timer <= 0) {
            double l = G.idealborder[0] + 12;
            double r = G.idealborder[1] - 12;
            for (int i = 0; i < perWave; i++) {
                double x = l + (xMinFrac + Math.random() * (xMaxFrac - xMinFrac)) * (r - l);
                double y = -Math.random() * 16; // from the screen top
                boolean usePlus = plus && (!box || Math.random() < 0.5);
                if (usePlus) {
                    manager.add(new PlusBomb(manager, soul, x, y, speed));
                } else {
                    manager.add(new CircleBox(manager, soul, x, y, speed));
                }
            }
            timer = interval;
        }
    }

    @Override
    public void render(Graphics2D g) {
        // Invisible generator.
    }
}
