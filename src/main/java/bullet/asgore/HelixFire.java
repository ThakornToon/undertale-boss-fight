package bullet.asgore;

import battle.Soul;
import core.EntityManager;

/**
 * A falling fire that weaves left/right on a sine wave (GML:
 * {@code obj_sinefire_asghelix}). Spawned in mirrored pairs by
 * {@code obj_helixfiregen} to braid the descending fire columns, and singly by the
 * sine-fire generators to fan a spray across the box. {@code sf} is the horizontal
 * amplitude, {@code sv} the wave period (sign flips the weave direction),
 * {@code vspeed} the fall speed.
 *
 * // GML: obj_sinefire_asghelix
 */
public final class HelixFire extends AsgoreFire {

    /** GML: s (phase) / sf (amplitude) / sv (period). */
    public double s;
    public double sf = 10;
    public double sv = 5;

    public HelixFire(EntityManager manager, Soul soul) {
        super(manager, soul);
        this.vspeed = 2;
        // GML obj_sinefire_asghelix / obj_helixfiregen never set image_xscale, so the
        // helix/sine fires (fandom patterns 1 & 2) are the SMALL 16px flame — half the
        // size of the firestorm/converging fires that draw at image_xscale 2.
        this.scale = 1;
    }

    @Override
    protected void move() {
        s++;
        x += Math.sin(s / sv) * sf;
        y += vspeed;
    }
}
