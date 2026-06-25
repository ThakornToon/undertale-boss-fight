package bullet.asriel;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;

/**
 * SHOCKER BREAKER / SHOCKER BREAKER II (GML: {@code obj_rainbowbolt_realgen}). The
 * vertical rainbow-lightning attack (turns 1/3/8/12). It sweeps a line of
 * {@link ShockerTarget} markers across the board in four passes, then drops "giga"
 * columns at fixed positions; 20 frames after each marker appears its {@link ShockerBolt}
 * strikes, so the player weaves between the telegraphed columns.
 *
 * <p>{@code hard} (SHOCKER BREAKER II, turns 8/12) tightens the sweep timing, adds a
 * column that <b>homes on the heart</b>, and finishes with a 5-burst giga sweep across
 * the board.
 *
 * // GML: obj_rainbowbolt_realgen
 */
public final class ShockerBreakerGen extends AttackPattern {

    private final Soul soul;
    private final boolean hard;
    private final int dmgVal;
    private int timer;
    private int i;        // GML i: the sweep index, walks up then down
    private int rr;       // GML rr: hard-mode giga sweep counter

    public ShockerBreakerGen(EntityManager manager, Soul soul, boolean hard, int dmg) {
        super(manager);
        this.soul = soul;
        this.hard = hard;
        this.dmgVal = dmg;
        this.depth = 50;
    }

    /** GML: target X = -80 + gen.x(0) + i/8*640 + off. */
    private void sweep(double off) {
        manager.add(new ShockerTarget(manager, soul, -80 + i / 8.0 * 640 + off, false, dmgVal));
    }

    private void giga(double gx) {
        manager.add(new ShockerTarget(manager, soul, gx, true, dmgVal));
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        timer++;
        if (!hard) {
            if (timer > 1 && timer < 10) { sweep(20); i++; }
            if (timer > 21 && timer < 30) { sweep(-20); i--; }
            if (timer > 41 && timer < 50) { sweep(20); i++; }
            if (timer > 61 && timer < 70) { sweep(-20); i--; }
            if (timer == 90) { giga(200); giga(320); giga(440); }
            if (timer == 106) { giga(260); giga(380); }
            if (timer == 122) { giga(200); giga(320); giga(440); }
            if (timer > 170) {
                manager.destroy(this);
            }
        } else {
            if (timer > 1 && timer < 10) { sweep(20); i++; }
            if (timer > 17 && timer < 27) { sweep(-20); i--; }
            if (timer > 34 && timer < 44) { sweep(20); i++; }
            if (timer > 54 && timer < 70) {
                manager.add(new ShockerTarget(manager, soul, soul.x + 8, false, dmgVal)); // homing
                i++;
            }
            if (timer == 76 || timer == 91 || timer == 106 || timer == 121 || timer == 136) {
                giga(200 + rr * 60);
                rr++;
            }
            if (timer > 175) {
                manager.destroy(this);
            }
        }
    }

    @Override
    public void render(java.awt.Graphics2D g) {
        // Invisible driver; targets/bolts draw themselves.
    }
}
