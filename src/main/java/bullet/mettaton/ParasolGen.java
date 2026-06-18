package bullet.mettaton;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Graphics2D;

/**
 * Spawns waves of parasol Mettatons spread across the top of the box (examples
 * 1/3/5). {@code perWave} parasols drop together at evenly-spread x positions on a
 * cadence; the soul shoots them down before they reach the bottom.
 *
 * // GML: obj_mettattackgen (parasol generator)
 */
public final class ParasolGen extends AttackPattern {

    private final Soul soul;
    private final int perWave;
    private final int interval;
    private final double speed;
    private final int maxWaves;
    private final boolean scatter;
    private int timer;
    private int waves;

    public ParasolGen(EntityManager manager, Soul soul, int perWave, int interval,
                      double speed, int maxWaves, boolean scatter) {
        super(manager);
        this.soul = soul;
        this.perWave = Math.max(1, perWave);
        this.interval = Math.max(20, interval);
        this.speed = speed;
        this.maxWaves = maxWaves;
        this.scatter = scatter;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        if (--timer <= 0) {
            double l = G.idealborder[0] + 18;
            double r = G.idealborder[1] - 18;
            double step = perWave > 1 ? (r - l) / (perWave - 1) : 0;
            for (int i = 0; i < perWave; i++) {
                double x = scatter
                        ? l + Math.random() * (r - l)
                        : (perWave > 1 ? l + i * step : (l + r) / 2);
                manager.add(new ParasolMett(manager, soul, x, 0, speed)); // from screen top
            }
            timer = interval;
            if (maxWaves > 0 && ++waves >= maxWaves) {
                manager.destroy(this);
            }
        }
    }

    @Override
    public void render(Graphics2D g) {
        // Invisible generator.
    }
}
