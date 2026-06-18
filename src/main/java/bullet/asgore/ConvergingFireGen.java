package bullet.asgore;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Graphics2D;
import util.GMLHelper;

/**
 * Periodically rings the box with {@link RadialFire} that collapse inward (GML:
 * {@code obj_cfiregen}). Each wave spawns ~36 flames evenly spaced (8° apart) at a
 * random base angle, all spiraling toward the centre; higher {@code diff} adds a
 * rotation ({@code angspeed}) and quickens the cadence so the closing rings get
 * harder to slip through.
 *
 * // GML: obj_cfiregen (diff 0..3)
 */
public final class ConvergingFireGen extends AttackPattern {

    private final Soul soul;
    public int diff;

    private int timer = 10;

    public ConvergingFireGen(EntityManager manager, Soul soul, int diff) {
        super(manager);
        this.soul = soul;
        this.diff = diff;
        this.depth = 50;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        if (--timer > 0) {
            return;
        }
        double baseAng = GMLHelper.random(360);
        switch (diff) {
            case 0 -> { wave(36, baseAng, 4, 0); timer = 35; }
            case 1 -> { wave(36, baseAng, 6, -2 + GMLHelper.random(4)); timer = 30; }
            case 2 -> { wave(36, baseAng, 4, -4 + GMLHelper.random(8)); timer = 30; }
            default -> { wave(33, baseAng, 6, -6 + GMLHelper.random(12)); timer = 25; }
        }
    }

    private void wave(int n, double baseAng, double rspeed, double angspeed) {
        for (int i = 0; i < n; i++) {
            RadialFire f = new RadialFire(manager, soul);
            f.ang = baseAng + i * 8;
            f.rspeed = rspeed;
            f.r = 300;
            f.angspeed = angspeed;
            manager.add(f);
        }
    }

    @Override
    public void render(Graphics2D g) {
    }
}
