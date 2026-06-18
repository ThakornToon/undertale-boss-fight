package bullet.undyne;

import core.EntityManager;
import util.GMLHelper;

/**
 * A straight GREEN-soul block spear (GML: {@code obj_blockbullet}). It flies in a
 * straight line from its {@code site} edge toward the shielded heart at speed
 * {@code 8 × speedmod}, passes through, and is culled once it has slid well past.
 *
 * // GML: obj_blockbullet (speartype 0)
 */
public final class BlockBullet extends GreenSpear {

    private boolean moving;

    public BlockBullet(EntityManager manager, double cx, double cy, int site, double speedmod) {
        super(manager, cx, cy, site, speedmod);
    }

    /**
     * The spear closest to the centre (arriving next) is red (frame 0); all others
     * are blue (frame 1). Yellow feint spears are unaffected (BlockSpear2 overrides
     * arrowSpriteName independently).
     */
    @Override
    protected String arrowSpriteName() {
        String base = switch (site) {
            case 0 -> "spr_bullet_test_l_";
            case 1 -> "spr_bullet_test_r_";
            case 2 -> "spr_bullet_test_u_";
            default -> "spr_bullet_test_d_";
        };
        if (!placed || blocked) {
            return base + "1";
        }
        double myDist = distanceToCenter();
        boolean[] isClosest = {true};
        manager.with(BlockBullet.class, other -> {
            if (other != this && other.placed && !other.blocked
                    && other.distanceToCenter() < myDist) {
                isClosest[0] = false;
            }
        });
        return base + (isClosest[0] ? "0" : "1");
    }

    @Override
    protected void step() {
        if (!moving) {
            // GML move_towards_point(centre, 8 * speedmod): constant inbound velocity.
            double dir = GMLHelper.point_direction(x, y, cx, cy);
            double sp = 8 * speedmod;
            hspeed = GMLHelper.lengthdir_x(sp, dir);
            vspeed = GMLHelper.lengthdir_y(sp, dir);
            moving = true;
        }
        x += hspeed;
        y += vspeed;
        // Cull once it has crossed the centre and slid back out of the play field.
        if (distanceToCenter() > 320) {
            manager.destroy(this);
        }
    }
}
