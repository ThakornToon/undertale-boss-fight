package bullet.asgore;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import core.Game;

/**
 * The fire storm (GML: {@code obj_firestormgen}). A burst point sweeps across the
 * field ({@code hspeed}) dimming the screen, and every few frames it explodes a full
 * ring of {@link LinearFire} that accelerate outward (negative friction) and rain
 * down through the box. {@code lv} 1→3 raises the sweep speed, ring density, and
 * flame speed for the late-fight storms.
 *
 * // GML: obj_firestormgen (lv 1..3)
 */
public final class FireStormGen extends AttackPattern {

    private final Soul soul;
    public int lv = 1;

    private static final int MAX_BURSTS = 12;
    private double amount;
    private double baseAng;
    private int timer = 1;
    private double dim;                 // GML: dr — screen dim alpha
    private boolean bursting = true;

    public FireStormGen(EntityManager manager, Soul soul, int lv) {
        super(manager);
        this.soul = soul;
        this.lv = lv;
        this.baseAng = Math.random() * 100;
        this.depth = 60;
        // GML: the gen is created at (0,0) — the top-LEFT corner — and slides right
        // across the top edge every step (hspeed 5/6/8 by lv) while bursting rings,
        // so the rings of fire sweep "from one corner to the other" (fandom pattern 5).
        this.x = 0;
        this.y = 0;
        this.hspeed = switch (lv) {
            case 1 -> 5;
            case 2 -> 6;
            default -> 8;
        };
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        // GML applies hspeed every step — this is the corner-to-corner sweep the
        // previous port was missing (it only nudged x once per ring, ~60px total).
        x += hspeed;
        if (bursting && --timer <= 0) {
            burst();
        }
    }

    private void burst() {
        switch (lv) {
            case 1 -> { amount += 1; ring(20, 3, -0.15, 3); timer = 8; }
            case 2 -> { amount += 1.2; baseAng += 1.5; ring(22, 3.5, -0.17, 3.5); timer = 7; }
            default -> { amount += 1.2; baseAng += 2; ring(22, 12, 0.06, 6); timer = 6; }
        }
        if (amount > MAX_BURSTS) {
            bursting = false;          // GML alarm[0] = -1: stop bursting, let it fade
        }
    }

    private void ring(int amt, double speed, double friction, double ignored) {
        for (int i = 0; i < amt; i++) {
            LinearFire f = new LinearFire(manager, soul);
            f.x = x;
            f.y = y;
            f.direction = i * 360.0 / amt + baseAng;
            f.speed = speed;
            f.friction = friction;     // negative = accelerate outward
            f.scale = 2;
            manager.add(f);
        }
    }

    @Override
    public void render(Graphics2D g) {
        // GML: dim the whole screen up to ~0.5, easing out as the turn ends.
        if (dim < 0.5) {
            dim += 0.1;
        }
        if (G.turntimer < 6) {
            dim -= 0.2;
        }
        float a = (float) Math.max(0, Math.min(0.5, dim));
        if (a <= 0) {
            return;
        }
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, Game.WIDTH, Game.HEIGHT);
        g.setComposite(old);
    }
}
