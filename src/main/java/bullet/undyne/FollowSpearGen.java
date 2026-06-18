package bullet.undyne;

import battle.Soul;
import bullet.Generator;
import core.EntityManager;

/**
 * Spawns a {@link FollowSpear} every {@code firingrate} frames for the duration of
 * the enemy turn (GML: {@code obj_spearbulletfollowgen}).
 *
 * // GML: obj_spearbulletfollowgen
 */
public final class FollowSpearGen extends Generator {

    private final Soul soul;

    public FollowSpearGen(EntityManager manager, Soul soul, int firingrate, int dmg) {
        super(manager);
        this.soul = soul;
        this.dmg = dmg;
        this.rate = Math.max(1, firingrate);
        this.count = -1;       // emit until the turn ends
        this.life = -1;
    }

    @Override
    protected void emit() {
        manager.add(new FollowSpear(manager, soul, dmg));
    }

    @Override
    public void update() {
        // GML: stop a few frames before the turn ends so the last spear can clear.
        if (G.turntimer <= 3) {
            manager.destroy(this);
            return;
        }
        super.update();
    }
}
