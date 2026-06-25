package bullet.asriel;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import core.Game;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import util.GMLHelper;

/**
 * Asriel's final-form comet barrage (GML: {@code obj_ultimagen} + {@code obj_ultimatarget}):
 * {@link AngelComet} streaks are flung out from the Angel's hands to alternating sides, then
 * <b>curve homing</b> back toward a target that lags the heart — sweeping in from the spread
 * wings. A glowing orb sits at each hand (the source of the streaks). This is the "the whole
 * world is ending" wave of Part B (token damage; the SOUL cannot die).
 *
 * // GML: obj_ultimagen (+ obj_ultimatarget)
 */
public final class AngelOfDeathGen extends AttackPattern {

    private final Soul soul;
    private final int dmgVal;
    private final int batchInterval; // frames between each volley of comets
    private int timer;

    // The comets are flung from the body's hands (centre); the lagging target chases the heart.
    private static final double GEN_X = Game.WIDTH / 2.0;       // 320
    private static final double GEN_Y = 135;
    private static final double HAND_OFF = 150;                 // glowing-orb hands spread
    private double targetX = GEN_X;
    private double targetY = 300;

    public AngelOfDeathGen(EntityManager manager, Soul soul, int dmg, int interval) {
        super(manager);
        this.soul = soul;
        this.dmgVal = dmg;
        this.batchInterval = Math.max(8, interval);
        this.depth = -5;
    }

    public double targetX() {
        return targetX;
    }

    public double targetY() {
        return targetY;
    }

    /** A comet touched the SOUL — a touch of damage (the SOUL can't actually die in Part B). */
    public void applyHit() {
        if (G.inv <= 0) {
            soul.hurt(dmgVal);
        }
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        // GML obj_ultimatarget: a point that lags the heart, moving toward it at speed 3.
        double tx = soul.x;
        double ty = soul.y;
        double d = Math.hypot(tx - targetX, ty - targetY);
        if (d > 3) {
            targetX += (tx - targetX) / d * 3;
            targetY += (ty - targetY) / d * 3;
        } else {
            targetX = tx;
            targetY = ty;
        }
        timer++;
        // The barrage comes in VOLLEYS — roughly 4–7 big comets flung out together, split
        // across the two hands, then they all curve in toward the heart.
        if (timer >= 6 && (timer - 6) % batchInterval == 0) {
            int n = GMLHelper.irandom_range(4, 7);
            for (int i = 0; i < n; i++) {
                manager.add(new AngelComet(manager, soul, this, GEN_X, GEN_Y, GMLHelper.irandom(1)));
            }
        }
    }

    @Override
    public void render(Graphics2D g) {
        // A glowing orb at each spread hand — the source of the streaks.
        drawHandOrb(g, GEN_X - HAND_OFF);
        drawHandOrb(g, GEN_X + HAND_OFF);
    }

    private void drawHandOrb(Graphics2D g, double hx) {
        Composite oc = g.getComposite();
        for (int i = 4; i >= 1; i--) {
            float a = 0.18f + 0.06f * i;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, a)));
            Color c = Color.getHSBColor((float) ((timer * 0.02 + i * 0.12) % 1.0), 0.45f, 1f);
            g.setColor(c);
            int r = 4 + i * 3 + (int) (2 * Math.sin(timer * 0.2));
            g.fillOval((int) (hx - r), (int) (GEN_Y - r), r * 2, r * 2);
        }
        g.setComposite(oc);
    }
}
