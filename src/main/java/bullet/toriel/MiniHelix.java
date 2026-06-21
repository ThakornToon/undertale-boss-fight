package bullet.toriel;

import battle.Soul;
import core.EntityManager;

/**
 * Toriel's tighter, faster sine fire (GML: {@code blt_minihelix}, bullettype 8). It
 * weaves on its <em>own</em> phase counter ({@code h}) with a wider amplitude than
 * {@link FireHelix}: {@code hspeed = ±sin(h / 5) * 8}, gentle gravity 0.06. Spawned
 * just inside the top of the box and pulsed downward, it sweeps a broad zig-zag the
 * player threads through.
 *
 * // GML: blt_minihelix
 */
public final class MiniHelix extends TorielFire {

    /** GML: r — per-flame sign flip. */
    public int r;
    /** GML: h — this flame's own phase counter (NOT the shared clock). */
    private double h;

    public MiniHelix(EntityManager manager, Soul soul) {
        super(manager, soul);
        // GML Create: gravity 0.06; vspeed seeded small/positive by the generator.
        this.gravity = 0.06;
        this.vspeed = 0.5;
        this.scale = 1;
    }

    @Override
    protected void move() {
        // GML Step: hspeed = sin(h/5)*8, sign per-flame; h increments each step.
        hspeed = Math.sin(h / 5.0) * 8.0;
        if (r == 0) {
            hspeed = -hspeed;
        }
        h++;
        vspeed += gravity;
        x += hspeed;
        y += vspeed;
    }
}
