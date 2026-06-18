package bullet.mettaton;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Graphics2D;

/**
 * Spawns Mettaton EX's leg pairs (example_0): a left and a right leg drop together
 * on a cadence, each randomly moving or still, leaving a central gap to weave
 * through (shoot a leg to toggle its motion and widen the gap).
 *
 * // GML: obj_mettattackgen (leg generator)
 */
public final class MettLeg2Gen extends AttackPattern {

    private final Soul soul;
    private final int interval;
    private final double speed;
    private int timer;

    public MettLeg2Gen(EntityManager manager, Soul soul, int interval, double speed) {
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
            // Legs descend from the very top. Whether one or two, each leg is
            // full-frame length; a left/right pair is just staggered so they overlap
            // only slightly.
            double w = G.idealborder[1] - G.idealborder[0];
            if (Math.random() < 0.4) {
                boolean left = Math.random() < 0.5;
                manager.add(new MettLeg2(manager, soul, 0, speed, left, true, w));
            } else {
                manager.add(new MettLeg2(manager, soul, 0, speed, true, true, w));
                manager.add(new MettLeg2(manager, soul, -30, speed, false, true, w));
            }
            timer = interval;
        }
    }

    @Override
    public void render(Graphics2D g) {
        // Invisible generator.
    }
}
