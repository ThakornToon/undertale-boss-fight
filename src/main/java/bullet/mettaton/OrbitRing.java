package bullet.mettaton;

import battle.Soul;
import bullet.AttackPattern;
import bullet.Shootable;
import core.EntityManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * A ring of boxes (or plus-bombs) orbiting Mettaton's popped-out heart (examples 11 /
 * 17). They start circling close, slowly spiral outward off the screen, then spiral
 * back in (any that were shot return on the inward pass), and repeat — all centred on
 * the wobbling {@link HeartCore}. Touching one hurts; shooting one removes it until
 * the next inward pass.
 *
 * // GML: obj_mettattackgen orbiting boxes around the heart
 */
public final class OrbitRing extends AttackPattern implements Shootable {

    private static final double BASE_R = 24;
    private static final double RANGE = 320;
    private static final double TWO_PI = Math.PI * 2;

    private final Soul soul;
    private final HeartCore heart;
    private final int n;
    private final boolean usePlus;
    private final double spin;
    private final boolean[] alive;
    private double rot;
    private double phase;
    private int pendingHit = -1;

    public OrbitRing(EntityManager manager, Soul soul, HeartCore heart, int n,
                     boolean usePlus, double spin) {
        super(manager);
        this.soul = soul;
        this.heart = heart;
        this.n = Math.max(1, n);
        this.usePlus = usePlus;
        this.spin = spin;
        this.alive = new boolean[this.n];
        java.util.Arrays.fill(alive, true);
        this.depth = -2;
    }

    private double radius() {
        return BASE_R + (1 - Math.cos(phase)) / 2 * RANGE;
    }

    private double bx(int i) {
        return heart.x + Math.cos(rot + i * TWO_PI / n) * radius();
    }

    private double by(int i) {
        return heart.y + Math.sin(rot + i * TWO_PI / n) * radius();
    }

    @Override
    public void update() {
        if (G.turntimer <= 0 || heart.destroyed) {
            manager.destroy(this);
            return;
        }
        rot += spin;
        double prev = phase;
        phase += 0.02;
        // Revive destroyed boxes as the ring reaches its outward peak (off-screen), so
        // they come spiralling back in with the rest.
        if (prev % TWO_PI < Math.PI && phase % TWO_PI >= Math.PI) {
            java.util.Arrays.fill(alive, true);
        }
        for (int i = 0; i < n; i++) {
            if (alive[i] && G.inv <= 0
                    && Math.abs(soul.x - bx(i)) < 13 && Math.abs(soul.y - by(i)) < 13) {
                soul.hurt(5);
            }
        }
    }

    @Override
    public boolean hitBy(double px, double py) {
        for (int i = 0; i < n; i++) {
            if (alive[i] && Math.abs(px - bx(i)) < 13 && Math.abs(py - by(i)) < 13) {
                pendingHit = i;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onShot() {
        if (pendingHit >= 0) {
            if (usePlus) {
                // An orbiting plus-bomb detonates normally when shot.
                manager.add(new PlusBlast(manager, soul, bx(pendingHit), by(pendingHit)));
            }
            alive[pendingHit] = false;
            pendingHit = -1;
        }
        return true;
    }

    @Override
    public void render(Graphics2D g) {
        BufferedImage img = Assets.sprite(usePlus ? "spr_plusbomb_0" : "spr_blackbox_pl_0");
        for (int i = 0; i < n; i++) {
            if (!alive[i]) {
                continue;
            }
            double x = bx(i);
            double y = by(i);
            if (img != null) {
                g.drawImage(img, (int) (x - img.getWidth() / 2.0),
                        (int) (y - img.getHeight() / 2.0), null);
            } else {
                Stroke old = g.getStroke();
                g.setStroke(new BasicStroke(2f));
                g.setColor(Color.WHITE);
                g.fillRect((int) (x - 10), (int) (y - 10), 20, 20);
                g.setColor(Color.BLACK);
                g.drawOval((int) (x - 5), (int) (y - 5), 10, 10);
                g.setStroke(old);
            }
        }
    }
}
