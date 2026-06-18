package bullet.gaster;

import battle.KarmaTicker;
import battle.Soul;
import bullet.Generator;
import core.EntityManager;
import util.GMLHelper;

/**
 * Spawns heart-seeking Gaster Blasters on a cadence (GML:
 * {@code obj_gasterbl_gen}). Each emission picks a random direction around the
 * soul, parks a blaster 200px out along it (clamped to the screen), aims it back
 * at where the soul was, and lets it fire. {@code type} 1 is the a_type 12
 * pattern, {@code type} 2 the slower/bigger a_type 13 pattern.
 *
 * // GML: obj_gasterbl_gen (type 1 / type 2)
 */
public final class GasterBlasterGen extends Generator {

    private final Soul soul;
    private final KarmaTicker karma;

    public GasterBlasterGen(EntityManager manager, Soul soul, KarmaTicker karma, int type) {
        super(manager);
        this.soul = soul;
        this.karma = karma;
        this.type = type;
        this.rate = (type == 2) ? 20 : 16;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        super.update();
    }

    @Override
    protected void emit() {
        double dd = GMLHelper.random(360);
        GasterBlaster gb = new GasterBlaster(manager, soul, karma);
        double ix = soul.x + GMLHelper.lengthdir_x(200, dd);
        double iy = soul.y + GMLHelper.lengthdir_y(200, dd);
        gb.idealx = GMLHelper.clamp(ix, 50, 590);
        gb.idealy = GMLHelper.clamp(iy, 40, 440);
        gb.x = soul.x + GMLHelper.lengthdir_x(400, dd);
        gb.y = soul.y + GMLHelper.lengthdir_y(300, dd);
        gb.idealrot = GMLHelper.point_direction(gb.idealx, gb.idealy, soul.x, soul.y) + 90;
        gb.imageAngle = gb.idealrot;
        gb.terminal = 1;
        if (type == 2) {
            gb.xscale = 2;
            gb.yscale = 2;
            gb.pause = 20;
        } else {
            gb.xscale = 1;
            gb.yscale = 2;
            gb.pause = 14;
        }
        manager.add(gb);
    }
}
