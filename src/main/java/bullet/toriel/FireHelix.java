package bullet.toriel;

import battle.Soul;
import core.EntityManager;

/**
 * Toriel's tall sine-wave fire column (GML: {@code blt_firehelix1}, bullettype 7 and
 * part of 10). It falls under gravity while weaving left/right on a <em>shared</em>
 * clock: {@code hspeed = ±sin(obj_time.time / 10) * 4}. Because every helix flame
 * reads the same {@code obj_time.time}, they all sway in lockstep — and the random
 * sign ({@code r}) splits the column into two intertwining streams (the "helix").
 *
 * <p>The shared clock is reproduced by seeding {@link #time} from the generator's
 * frame counter at spawn and advancing it one per step: {@code genTime + age} equals
 * the absolute frame, so all columns stay phase-locked exactly as in GML.
 *
 * // GML: blt_firehelix1
 */
public final class FireHelix extends TorielFire {

    /** GML: r — the per-flame sign flip (1 = +sin, 0 = −sin). */
    public int r;
    /** GML: obj_time.time — the shared frame clock, seeded by the generator. */
    public double time;

    public FireHelix(EntityManager manager, Soul soul) {
        super(manager, soul);
        // GML Create: gravity 0.12 downward, vspeed 0.7 initial.
        this.gravity = 0.12;
        this.vspeed = 0.7;
        this.scale = 1;
    }

    /** GML Create: nudge the spawn in by 20px if it lands within 20px of an edge. */
    public void spawnClamp() {
        if (y > G.idealborder[3] - 20) {
            y -= 20;
        }
        if (y < G.idealborder[2] + 20) {
            y += 20;
        }
    }

    @Override
    protected void move() {
        // GML Step: hspeed = sin(time/10)*4, sign flipped per-flame.
        hspeed = Math.sin(time / 10.0) * 4.0;
        if (r == 0) {
            hspeed = -hspeed;
        }
        time++;
        vspeed += gravity;       // GML gravity 0.12, direction down
        x += hspeed;
        y += vspeed;
    }
}
