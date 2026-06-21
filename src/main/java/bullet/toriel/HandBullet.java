package bullet.toriel;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * One of Toriel's sweeping hands (GML: {@code blt_handbullet1} / {@code blt_handbullet2}).
 * The hand itself is harmless — it sails across the box on a shallow arc (the GML
 * {@code path_hand1/2}, missing from the export and reconstructed here from the
 * reference clips), dropping a {@link ChaseFire} every 4 frames at its fingertip. Its
 * {@code path_speed} ramps up to the midpoint and back down, so it lingers longest in
 * the centre. When it reaches the far side it stops, sits {@code inactive} for a beat,
 * then destroys itself and <b>ends the enemy turn</b> ({@code global.turntimer = -1}).
 *
 * <p>Which chase fire it drops depends on the attack (GML controller {@code x1} flag):
 * the lone hand (one-hand attack) drops the self-homing {@link ChaseFire} variant 1;
 * both hands in the two-hand attack drop variant 2, which waits for <b>hand1</b> to
 * finish and then converges on the SOUL all at once.
 *
 * // GML: blt_handbullet1 / blt_handbullet2 (+ path_hand1 / path_hand2)
 */
public final class HandBullet extends AttackPattern {

    private final Soul soul;
    /** 1 = upper hand sweeping left→right; 2 = lower hand sweeping right→left. */
    private final int hand;
    /** GML: which ChaseFire to drop — 1 (self-homing) or 2 (converging). */
    private final int dropVariant;
    /** GML: blt_chasefire2 waits on hand1; the boss passes hand1 in for hand2. */
    private HandBullet hand1Ref;

    /** GML path_position == 1 — the sweep is finished (chasefire2 converges on this). */
    public boolean sweepDone;

    // ---- Sweep state (GML path_start + the path_speed ramp) ----------------------
    private static final double DIP = 50;       // arc depth toward the box centre
    private double pathSpeed = 0.2;             // GML path_start(.., 0.2, ..)
    private double dist;                         // px travelled along the sweep
    private double pathLen = 270;                // set from the live box on the first step
    private double t;                            // 0 → 1 sweep progress
    private boolean started;

    private int dropTimer = 8;                   // GML alarm[0] = 8, then re-arms to 4
    private int inactiveTimer = -1;              // counts down once the sweep is done
    private int frame;

    public HandBullet(EntityManager manager, Soul soul, int hand, int dropVariant) {
        super(manager);
        this.soul = soul;
        this.hand = hand;
        this.dropVariant = dropVariant;
        this.depth = -3;        // hands draw in front of the fire they drop
    }

    /** GML: the hand2 chasefire2 still waits on hand1's sweep. */
    public void setHand1(HandBullet hand1) {
        this.hand1Ref = hand1;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        double l = G.idealborder[0];
        double r = G.idealborder[1];
        double top = G.idealborder[2];
        double bottom = G.idealborder[3];

        if (!started) {
            pathLen = (r - l) + 90;          // sweep runs 45px past each edge
            frame = hand == 1 ? 1 : 0;        // GML: image_index 1 when spawned up high
            started = true;
        }

        if (inactiveTimer < 0) {
            advanceSweep();
            placeOnArc(l, r, top, bottom);
            dropFire(l, r);
        } else if (--inactiveTimer <= 0) {
            // GML Destroy: the wave ends when the hand is gone.
            G.turntimer = -1;
            manager.destroy(this);
        }
    }

    /** GML Step: ramp path_speed up to the midpoint, then down; stop at the end. */
    private void advanceSweep() {
        if (t < 0.5) {
            pathSpeed += 0.2;
        } else {
            pathSpeed = Math.max(0.5, pathSpeed - 0.1);
        }
        dist += pathSpeed;
        t = dist / pathLen;
        if (t >= 1.0) {
            t = 1.0;
            sweepDone = true;
            inactiveTimer = hand == 1 ? 100 : 70;   // GML alarm[1] = 100 / 70
        }
    }

    /** Reconstructed path_hand1/2: a horizontal sweep with a sine dip toward centre. */
    private void placeOnArc(double l, double r, double top, double bottom) {
        double left = l - 45;
        double right = r + 45;
        double arc = Math.sin(t * Math.PI) * DIP;
        if (hand == 1) {
            x = left + t * (right - left);       // left → right
            y = (top + 20) + arc;                // near the top, dipping down
        } else {
            x = right - t * (right - left);      // right → left
            y = (bottom - 20) - arc;             // near the bottom, dipping up
        }
    }

    /** GML alarm[0]: drop a chase fire at the fingertip every 4 frames over the box. */
    private void dropFire(double l, double r) {
        if (--dropTimer > 0) {
            return;
        }
        dropTimer = 4;
        // GML: chasefire dropped left of the box is culled — only drop over the box.
        if (x < l || x > r) {
            return;
        }
        ChaseFire f = new ChaseFire(manager, soul, dropVariant);
        f.x = x - 8;
        f.y = y - 15;
        if (dropVariant == 2) {
            f.waitForHand(hand1Ref != null ? hand1Ref : this);
        }
        manager.add(f);
        // GML plays snd_noise on each drop; left silent here to avoid 4-frame spam.
    }

    @Override
    public void render(Graphics2D g) {
        BufferedImage img = Assets.sprite("spr_handbullet_" + frame);
        int w = 60;
        int h = 40;
        if (img != null) {
            // hand2 sweeps the other way — mirror the sprite so the fingers lead.
            if (hand == 2) {
                g.drawImage(img, (int) (x + w / 2.0), (int) (y - h / 2.0), -w, h, null);
            } else {
                g.drawImage(img, (int) (x - w / 2.0), (int) (y - h / 2.0), w, h, null);
            }
        } else {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect((int) (x - w / 2.0), (int) (y - h / 2.0), w, h);
        }
    }
}
