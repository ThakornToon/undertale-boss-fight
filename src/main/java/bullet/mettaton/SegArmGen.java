package bullet.mettaton;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Graphics2D;

/**
 * Spawns Mettaton's segmented arm bars (example_3): a box-wide blocking bar with a
 * sliding yellow weak point drops from the top on a cadence; shoot the weak point to
 * clear each bar before it reaches you.
 *
 * // GML: obj_mettattackgen (segmented-arm generator)
 */
public final class SegArmGen extends AttackPattern {

    private final Soul soul;
    private final int interval;
    private final double speed;
    private int timer;

    public SegArmGen(EntityManager manager, Soul soul, int interval, double speed) {
        super(manager);
        this.soul = soul;
        this.interval = Math.max(40, interval);
        this.speed = speed;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        if (--timer <= 0) {
            manager.add(new SegArm(manager, soul, 0, speed)); // from the screen top
            timer = interval;
        }
    }

    @Override
    public void render(Graphics2D g) {
        // Invisible generator.
    }
}
