package bullet.asriel;

import battle.Soul;
import bullet.Bullet;
import core.EntityManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import util.GMLHelper;

/**
 * A comet from Asriel's final-form barrage (GML: {@code obj_ultimabullet}). It is flung
 * <b>±160px out to one side and downward</b>, then <b>curves homing</b> back toward a target
 * that lags the heart — accelerating (negative friction, capped at 22) and cycling pastel hues
 * — so the streaks sweep out from the spread wings and arc gracefully into the SOUL. It trails
 * a long tapering rainbow streak.
 *
 * // GML: obj_ultimabullet
 */
public final class AngelComet extends Bullet {

    private final AngelOfDeathGen gen;
    private final int side;          // 0 = launched left, 1 = launched right (steers back inward)
    private float hue;
    private int life = 140;          // GML alarm[5] = 140

    // GML xprev[18]/yprev[18]: the streak history used to draw the tapering tail.
    private final double[] px = new double[19];
    private final double[] py = new double[19];

    public AngelComet(EntityManager manager, Soul soul, AngelOfDeathGen gen,
                      double gx, double gy, int side) {
        super(manager, soul);
        this.gen = gen;
        this.side = side;
        this.dmg = 0;                // damage is applied (gated) via the generator's applyHit()
        this.depth = -6;
        this.hue = (float) GMLHelper.random(1.0);
        // GML event_user(1): offset to the side and launch outward + down.
        if (side == 0) {
            this.x = gx - 160;
            this.hspeed = -9 - GMLHelper.random(8);
        } else {
            this.x = gx + 160;
            this.hspeed = 9 + GMLHelper.random(8);
        }
        this.y = gy;
        this.vspeed = 4 + GMLHelper.random(10);
        for (int i = 0; i < px.length; i++) {
            px[i] = x;
            py[i] = y;
        }
    }

    @Override
    protected double halfWidth() {
        return 13;
    }

    @Override
    protected double boxTop() {
        return y - 13;
    }

    @Override
    protected double boxBottom() {
        return y + 13;
    }

    @Override
    protected boolean collidesSoul() {
        return Math.abs(soul.x - x) < halfWidth() + Soul.HALF
                && Math.abs(soul.y - y) < halfWidth() + Soul.HALF;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        // GML homing: steer toward a target that lags the heart, but only back INWARD
        // (a left-launched comet steers right, a right-launched one steers left) and only
        // UPWARD (never push further down) — this carves the graceful in-sweeping arcs.
        double hh = clamp((gen.targetX() - x) / 20.0, -1, 1);
        if (side == 0 && hh < 0) {
            hh = 0;
        }
        if (side == 1 && hh > 0) {
            hh = 0;
        }
        double vv = clamp((gen.targetY() - y) / 20.0, -1, 1);
        if (vv > 0) {
            vv = 0;
        }
        hspeed += hh;
        vspeed += vv;
        // GML friction = -0.1 → the comet accelerates, capped at speed 22.
        double sp = Math.hypot(hspeed, vspeed);
        if (sp > 0.0001) {
            double ns = Math.min(22, sp + 0.1);
            hspeed = hspeed / sp * ns;
            vspeed = vspeed / sp * ns;
        }
        x += hspeed;
        y += vspeed;

        for (int i = px.length - 1; i > 0; i--) {
            px[i] = px[i - 1];
            py[i] = py[i - 1];
        }
        px[0] = x;
        py[0] = y;
        hue = (hue + 20f / 255f) % 1f;       // GML huer += 20 (hsv) → fast pastel cycle

        if (collidesSoul()) {
            gen.applyHit();                  // touch damage (the SOUL cannot die in Part B)
        }
        if (--life <= 0 || x < -80 || x > 720 || y > 560) {
            manager.destroy(this);
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    @Override
    public void render(Graphics2D g) {
        // GML pastel streak (make_color_hsv(huer, 60, 255) → low saturation): a tapering tail
        // drawn as four segments of growing width toward the bright head.
        Color c = Color.getHSBColor(hue, 0.235f, 1f);
        g.setColor(c);
        Stroke os = g.getStroke();
        drawSeg(g, 10, 12, 3);
        drawSeg(g, 8, 10, 6);
        drawSeg(g, 4, 8, 10);
        // head → px[4] (the fat near-head section)
        g.setStroke(new BasicStroke(13, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) x, (int) y, (int) px[4], (int) py[4]);
        g.setStroke(os);
        // Big bright pastel head (a light tint of the same hue, not pure white).
        Color glow = Color.getHSBColor(hue, 0.35f, 1f);
        g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 180));
        g.fillOval((int) x - 16, (int) y - 16, 32, 32);
        g.setColor(Color.getHSBColor(hue, 0.12f, 1f));
        g.fillOval((int) x - 9, (int) y - 9, 18, 18);
    }

    private void drawSeg(Graphics2D g, int a, int b, float w) {
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) px[a], (int) py[a], (int) px[b], (int) py[b]);
    }
}
