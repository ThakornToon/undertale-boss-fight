package bullet.mettaton;

import battle.Soul;
import bullet.AttackPattern;
import bullet.Shootable;
import core.EntityManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * Mettaton's heart core popped out of his waist (GML: {@code spr_mettheart}) —
 * examples 4/11/17. It wobbles around the box centre and fires radial bursts of
 * {@link LightningBullet}. Shooting it enough times ends the turn early (GML: "shoot
 * his heart several times to end the attack"); touching it hurts.
 *
 * // GML: obj_mettheart_parent + obj_mettattackgen lightning burst
 */
public final class HeartCore extends AttackPattern implements Shootable {

    private final Soul soul;
    private final int hitsToEnd;
    private final int fireInterval;
    private int hits;
    private double wob;
    private int fireTimer = 24;
    private double burstAngle;
    private int waveQueue;  // remaining rings in the current 3-wave burst
    private int waveDelay;

    public HeartCore(EntityManager manager, Soul soul, int hitsToEnd, int fireInterval) {
        super(manager);
        this.soul = soul;
        this.hitsToEnd = hitsToEnd;
        this.fireInterval = Math.max(20, fireInterval);
        this.depth = -2;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        double[] b = G.idealborder;
        wob += 0.12;
        // GML: the heart hovers at Mettaton's waist, above the box, wobbling.
        x = (b[0] + b[1]) / 2.0 + Math.sin(wob) * 14;
        y = b[2] - 70 + Math.cos(wob * 0.9) * 8;

        if (G.inv <= 0 && Math.abs(soul.x - x) < 14 && Math.abs(soul.y - y) < 14) {
            soul.hurt(5);
        }
        // Each burst is three rings in quick succession (GML "three waves together").
        if (--fireTimer <= 0) {
            waveQueue = 3;
            fireTimer = fireInterval;
        }
        if (waveQueue > 0 && --waveDelay <= 0) {
            fireRing();
            waveQueue--;
            waveDelay = 6;
        }
    }

    private void fireRing() {
        int n = 12;
        burstAngle += 14;
        double sp = 3.0;
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(burstAngle + i * (360.0 / n));
            manager.add(new LightningBullet(manager, soul, x, y,
                    Math.cos(a) * sp, Math.sin(a) * sp));
        }
    }

    @Override
    public boolean hitBy(double px, double py) {
        return Math.abs(px - x) < 16 && Math.abs(py - y) < 16;
    }

    @Override
    public boolean onShot() {
        if (++hits >= hitsToEnd) {
            G.turntimer = 0; // shooting the heart ends Mettaton's attack
        }
        return true;
    }

    @Override
    public int shotRating() {
        return 15; // hitting the heart core is worth the most
    }

    @Override
    public void render(Graphics2D g) {
        BufferedImage img = Assets.sprite("spr_mettheart_0");
        if (img != null) {
            g.drawImage(img, (int) (x - img.getWidth() / 2.0), (int) (y - img.getHeight() / 2.0), null);
        } else {
            g.setColor(Color.WHITE);
            g.fillRect((int) (x - 8), (int) (y - 8), 16, 16);
        }
    }
}
