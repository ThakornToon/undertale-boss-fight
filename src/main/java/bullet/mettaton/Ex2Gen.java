package bullet.mettaton;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Graphics2D;

/**
 * The example_2 formation: a single stack that descends together at one speed —
 * three legs reaching in from the right, then two rows of centre boxes flanking a
 * box-wide centre channel, a vertical gap, two more centre-box rows, and finally
 * three legs reaching in from the left. The soul weaves left → centre channel →
 * right as the stack falls.
 *
 * // GML: obj_mettattackgen example_2 leg/box formation
 */
public final class Ex2Gen extends AttackPattern {

    private final Soul soul;
    private final double speed;
    private boolean spawned;

    public Ex2Gen(EntityManager manager, Soul soul, double speed) {
        super(manager);
        this.soul = soul;
        this.speed = speed;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        if (!spawned) {
            spawned = true;
            spawnFormation();
        }
    }

    private void spawnFormation() {
        double[] b = G.idealborder;
        double cx = (b[0] + b[1]) / 2.0;
        double w = b[1] - b[0];
        double legLen = w;          // full-frame legs, ex0-style (slide + shootable)
        double sb = 38;             // leg row spacing
        double boxH = 24;           // box height — the six boxes stack touching

        // Three right legs (arrive first, nearest the box). They start STILL — full
        // walls blocking the way; the soul shoots them to set them moving.
        for (int i = 0; i < 3; i++) {
            manager.add(new MettLeg2(manager, soul, -10 - i * sb, speed, false, false, legLen));
        }
        // The gap between the two leg sets is exactly six black-circle boxes stacked
        // touching, with the 3rd and 4th omitted — a small channel to slip through.
        double yR = -10 - 2 * sb;   // y of the last (deepest) right leg
        for (int k = 0; k < 6; k++) {
            if (k == 2 || k == 3) {
                continue;           // the slip-through gap
            }
            manager.add(new CircleBox(manager, soul, cx, yR - 12 - k * boxH, speed));
        }
        // Three left legs (arrive last), exactly six boxes below the right set. Also
        // start still, blocking.
        double yL = yR - 6 * boxH;
        for (int i = 0; i < 3; i++) {
            manager.add(new MettLeg2(manager, soul, yL - i * sb, speed, true, false, legLen));
        }
    }

    @Override
    public void render(Graphics2D g) {
        // Invisible generator.
    }
}
