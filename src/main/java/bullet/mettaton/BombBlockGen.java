package bullet.mettaton;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Graphics2D;

/**
 * The narrow-box bomb/block rows (examples 8/9/16): each row drops a plus-bomb on one
 * side and an unbreakable solid block on the other, swapping sides each row. The
 * block can only be cleared by shooting its paired bomb, so the soul shoots the bomb
 * and darts under the (now broken) block before the blast lands.
 *
 * // GML: obj_mettattackgen (bomb/block generator)
 */
public final class BombBlockGen extends AttackPattern {

    private final Soul soul;
    private final int interval;
    private final double speed;
    private int timer;
    private int row;

    public BombBlockGen(EntityManager manager, Soul soul, int interval, double speed) {
        super(manager);
        this.soul = soul;
        this.interval = Math.max(30, interval);
        this.speed = speed;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        if (--timer <= 0) {
            double l = G.idealborder[0];
            double r = G.idealborder[1];
            double leftX = l + (r - l) * 0.28;
            double rightX = l + (r - l) * 0.72;
            double y = 0; // from the screen top
            boolean bombLeft = row % 2 == 0;
            manager.add(new PlusBomb(manager, soul, bombLeft ? leftX : rightX, y, speed));
            manager.add(new SolidBlock(manager, soul, bombLeft ? rightX : leftX, y, speed));
            row++;
            timer = interval;
        }
    }

    @Override
    public void render(Graphics2D g) {
        // Invisible generator.
    }
}
