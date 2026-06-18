package bullet.asgore;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import util.Assets;

/**
 * One of Asgore's flying hands (GML: {@code obj_handbullet_new}). The hand itself is
 * harmless — it streaks across the field on a fixed heading, dropping a trail of
 * {@link LinearFire} flames every few frames. The moment it leaves the screen it
 * fires {@code event_user(10)}: every dropped flame is re-aimed at the soul and
 * accelerates in, so the scattered trail curls into a converging spray. This is the
 * defining shape of Asgore's hand attack.
 *
 * <p>{@code type} sets the spawn heading exactly as the GML alarm[1] block:
 * 1 down-right (with gravity), 2 up-left, 3 straight down, 4 straight up, 5 right.
 *
 * // GML: obj_handbullet_new
 */
public final class HandBullet extends AttackPattern {

    private final Soul soul;
    public int type = 2;

    /** GML: alarm[1] arms velocity after 1 frame; alarm[0] drops fire every 4 frames. */
    private int armTimer = 1;
    private int dropTimer = 5;
    private double gravityAccel;     // GML gravity along gravity_direction (vertical)
    private double imageAlpha;       // GML: fade in from 0
    private boolean moved;           // GML: event_user(10) has fired (retarget done)
    private double fade = 1;         // post-exit fade-out
    private String sprite = "spr_handbullet_old_d";

    private final List<LinearFire> trail = new ArrayList<>();

    public HandBullet(EntityManager manager, Soul soul, int type) {
        super(manager);
        this.soul = soul;
        this.type = type;
        this.depth = -2;
    }

    private void arm() {
        switch (type) {
            case 1 -> { hspeed = 8; vspeed = 3; gravityAccel = 0.1; sprite = "spr_handbullet_old_u"; }
            case 2 -> { hspeed = -8; vspeed = -3; gravityAccel = -0.1; sprite = "spr_handbullet_old_d"; }
            case 3 -> { vspeed = 6; sprite = "spr_handbullet_old_r"; }
            case 4 -> { vspeed = -6; sprite = "spr_handbullet_old_l"; }
            case 5 -> { hspeed = 8; sprite = "spr_handbullet_old_u"; }
            default -> { hspeed = -8; vspeed = -3; }
        }
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        if (armTimer > 0 && --armTimer == 0) {
            arm();
        }
        if (imageAlpha < 1) {
            imageAlpha = Math.min(1, imageAlpha + 0.2);
        }
        // GML gravity_direction is vertical for types 1/2 — fold into vspeed.
        vspeed += gravityAccel;
        x += hspeed;
        y += vspeed;

        // GML alarm[0]: drop a flame at the hand every 4 frames once fully faded in.
        if (!moved && --dropTimer <= 0) {
            dropTimer = 4;
            if (imageAlpha >= 1) {
                LinearFire f = new LinearFire(manager, soul);
                f.x = x + 30;
                f.y = y + 30;
                f.scale = 2;
                manager.add(f);
                trail.add(f);
            }
        }

        // GML Step: once the hand has flown off any edge, retarget the trail at the soul.
        if (!moved && offscreen()) {
            retargetTrail();
            moved = true;
        }
        if (moved) {
            fade -= 0.2;
            if (fade < -0.4) {
                manager.destroy(this);
            }
        }
    }

    private boolean offscreen() {
        if (hspeed > 0 && x > G.idealborder[1] + 20) {
            return true;
        }
        if (hspeed < 0 && x < G.idealborder[0] - 100) {
            return true;
        }
        if (vspeed < 0 && y < G.idealborder[2] - 100) {
            return true;
        }
        return vspeed > 0 && y > G.idealborder[3] + 20;
    }

    /** GML event_user(10): each flame homes once toward the soul, then accelerates. */
    private void retargetTrail() {
        for (LinearFire f : trail) {
            if (!f.destroyed) {
                f.aimAt(soul.x, soul.y, 2, 0.2);
            }
        }
    }

    @Override
    public void render(Graphics2D g) {
        if (imageAlpha <= 0) {
            return;
        }
        BufferedImage img = Assets.sprite(sprite);
        float a = (float) Math.max(0, Math.min(1, imageAlpha * fade));
        if (img == null) {
            return;
        }
        java.awt.Composite old = g.getComposite();
        if (a < 1f) {
            g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, a));
        }
        g.drawImage(img, (int) x, (int) y, img.getWidth() * 2, img.getHeight() * 2, null);
        g.setComposite(old);
    }
}
