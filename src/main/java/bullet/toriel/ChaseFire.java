package bullet.toriel;

import battle.Soul;
import core.EntityManager;

/**
 * The homing fire a sweeping {@link HandBullet} drops every few frames — the main
 * threat of Toriel's hand attacks now that the Faltering attack is cut.
 *
 * <p>Two variants, matching the two hand attacks:
 * <ul>
 *   <li><b>1</b> (GML {@code blt_chasefire1}, the one-hand attack): the flame sits
 *       where it was dropped for ~45 frames, then <em>lunges</em> at the SOUL
 *       (speed 2) and keeps nudging toward it every couple frames, accelerating
 *       ({@code friction = −0.05}) and ricocheting off the box walls.</li>
 *   <li><b>2</b> (GML {@code blt_chasefire2}, the two-hand attack): every dropped
 *       flame waits until <em>hand1 finishes its sweep</em>, then all lunge once at
 *       the SOUL together (speed 0.6, {@code friction = −0.1}) — so the scattered
 *       trail converges on the player at once.</li>
 * </ul>
 *
 * // GML: blt_chasefire1 / blt_chasefire2
 */
public final class ChaseFire extends TorielFire {

    private final int variant;
    /** GML: blt_chasefire2 waits on {@code blt_handbullet1.path_position == 1}. */
    private HandBullet waitFor;

    /** GML friction (negative = accelerating). 0 while the flame is still resting. */
    private double friction;
    private boolean started;       // has the flame begun its lunge?
    private int restTimer = 45;    // GML alarm[1]: 45f before chasefire1 lunges
    private int homeTimer = 4;     // GML alarm[2] cadence for the homing nudge
    private boolean bouncedTop;    // GML: bounced — top ricochet arms the bottom kill

    public ChaseFire(EntityManager manager, Soul soul, int variant) {
        super(manager, soul);
        this.variant = variant;
        this.scale = 1;
        this.dmg = variant == 1 ? 4 : 5;   // GML Create dmg (full damage — softening cut)
    }

    /** GML: blt_chasefire2 holds until this hand has finished sweeping. */
    public void waitForHand(HandBullet hand1) {
        this.waitFor = hand1;
    }

    @Override
    protected void move() {
        if (variant == 1) {
            stepVariant1();
        } else {
            stepVariant2();
        }
        applyFriction();
        x += hspeed;
        y += vspeed;
        bounceOffWalls();
        // GML Step: a flame that slips past the bottom of the box is gone.
        if (y > G.idealborder[3] + 4) {
            manager.destroy(this);
        }
    }

    /** GML blt_chasefire1: rest, then lunge + continuous homing nudge. */
    private void stepVariant1() {
        if (!started) {
            if (--restTimer <= 0) {
                lungeAtSoul(2.0);     // GML alarm[1]: move_towards_point(heart, 2)
                friction = -0.05;
                dmg = 5;              // GML: dmg ramps to 5 on the lunge
                started = true;
                homeTimer = 4;
            }
            return;
        }
        if (--homeTimer <= 0) {       // GML alarm[2]: nudge toward the heart every 2f
            hspeed += soul.x > x ? 0.1 : -0.1;
            vspeed += soul.y > y ? 0.1 : -0.1;
            homeTimer = 2;
        }
    }

    /** GML blt_chasefire2: wait for hand1, then a single converging lunge. */
    private void stepVariant2() {
        if (!started && (waitFor == null || waitFor.sweepDone)) {
            lungeAtSoul(0.6);         // GML: move_towards_point(heart, 0.6)
            friction = -0.1;
            started = true;
        }
    }

    /** GML: move_towards_point(heart.x+2, heart.y+2, speed). */
    private void lungeAtSoul(double speed) {
        double dx = (soul.x + 2) - x;
        double dy = (soul.y + 2) - y;
        double d = Math.hypot(dx, dy);
        if (d < 0.0001) {
            return;
        }
        hspeed = dx / d * speed;
        vspeed = dy / d * speed;
    }

    /** GML friction: bleed (or, when negative, build) speed along the heading. */
    private void applyFriction() {
        if (friction == 0) {
            return;
        }
        double sp = Math.hypot(hspeed, vspeed);
        if (sp < 0.0001) {
            return;
        }
        double nsp = Math.max(0, sp - friction);   // friction<0 → accelerate
        hspeed *= nsp / sp;
        vspeed *= nsp / sp;
    }

    /** GML obj_*border collisions: ricochet off the box, damping with positive friction. */
    private void bounceOffWalls() {
        double l = G.idealborder[0];
        double r = G.idealborder[1];
        double t = G.idealborder[2];
        double b = G.idealborder[3];
        if (x < l && hspeed < 0) {
            hspeed = -hspeed;
            friction = 0.04;
        } else if (x > r && hspeed > 0) {
            hspeed = -hspeed;
            friction = 0.04;
        }
        if (y < t && vspeed < 0) {
            vspeed = -vspeed;
            friction = 0.04;
            bouncedTop = true;             // GML: bounced = 1
        } else if (y > b && vspeed > 0) {
            vspeed = -vspeed;
            friction = 0.04;
            if (variant == 1 && bouncedTop) {
                manager.destroy(this);     // GML dborder: destroy once it has bounced
            }
        }
    }
}
