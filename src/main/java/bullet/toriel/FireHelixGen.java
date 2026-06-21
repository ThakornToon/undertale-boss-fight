package bullet.toriel;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Graphics2D;
import util.GMLHelper;

/**
 * Toriel's fire spawner (GML: {@code obj_1sidegen} restricted to her bullettypes 7,
 * 8 and 10). Every {@code firingrate} frames it drops one fire from a drop point that
 * <b>drifts slowly left and right</b> across the top of the box, re-arming on a fixed
 * cadence ({@code alarm[0] = firingspeed}) until the turn ends. A lower {@code firingrate}
 * packs the column denser; the drift makes the whole weaving wall travel side to side so
 * the player has to chase it (harder than a stationary column).
 *
 * <ul>
 *   <li>{@code type 7} / {@code 10} — one {@link FireHelix} per tick (the sine column).</li>
 *   <li>{@code type 8} — one {@link MiniHelix} per tick (the wider, faster weave).</li>
 * </ul>
 *
 * <p>(GML's bullettype 10 also dropped two {@code blt_floatfire} hugging the side
 * walls; those static edge fires are cut in this port — type 10 is just the helix.)
 *
 * <p>{@link #time} is the shared {@code obj_time.time} clock the helix flames read,
 * so every column spawned this turn stays phase-locked.
 *
 * // GML: obj_1sidegen (bullettype 7 / 8 / 10)
 */
public final class FireHelixGen extends AttackPattern {

    private final Soul soul;
    /** GML bullettype: 7/10 fire helix, 8 mini helix. */
    private final int type;
    /** GML: firingspeed = global.firingrate — frames between spawns. */
    private final int rate;
    /** GML: this fire's damage = global.monsteratk[myself]. */
    private final int fireDmg;

    /** GML: obj_time.time — the shared frame clock advanced each step. */
    private double time;
    private int cooldown;

    public FireHelixGen(EntityManager manager, Soul soul, int type, int rate, int fireDmg) {
        super(manager);
        this.soul = soul;
        this.type = type;
        this.rate = Math.max(1, rate);
        this.fireDmg = fireDmg;
        this.depth = 50;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        time++;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        cooldown = rate;
        emit();
    }

    private void emit() {
        double l = G.idealborder[0];
        double r = G.idealborder[1];
        double t = G.idealborder[2];
        double midX = l + (r - l) / 2.0 - 3;   // GML: mid(left,right) - 3

        // On top of each flame's own weave, drift the whole drop point slowly left and
        // right so the entire wave travels across the box — no spot stays safe, and the
        // player has to chase the column instead of parking under the gap.
        double dropX = midX + Math.sin(time / 11.0) * 48;

        if (type == 8) {
            spawnMini(dropX, t + 5);
        } else {
            spawnHelix(dropX, t - 25);         // type 7 and 10
        }
    }

    private void spawnHelix(double x, double y) {
        FireHelix f = new FireHelix(manager, soul);
        f.x = x;
        f.y = y;
        f.r = (int) Math.round(GMLHelper.random(1));
        f.time = time;                  // seed the shared clock so columns phase-lock
        f.dmg = fireDmg;
        f.spawnClamp();
        manager.add(f);
    }

    private void spawnMini(double x, double y) {
        MiniHelix f = new MiniHelix(manager, soul);
        f.x = x;
        f.y = y;
        f.r = (int) Math.round(GMLHelper.random(1));
        f.dmg = fireDmg;
        manager.add(f);
    }

    @Override
    public void render(Graphics2D g) {
        // Invisible spawn point.
    }
}
