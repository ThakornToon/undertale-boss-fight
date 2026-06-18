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
 * The disco ball (GML: {@code spr_discoball_pl} / {@code spr_discoball_invert_pl}) —
 * examples 6/7. It hangs from the top of the box and rakes rotating laser beams
 * across it. Shooting the ball flips every beam between blue and white: blue beams
 * are a blue attack (safe while you hold still), white beams hurt on contact.
 *
 * // GML: obj_mettattackgen disco-ball + beams
 */
public final class DiscoBall extends AttackPattern implements Shootable {

    private final Soul soul;
    private final int beams;
    private final double spin;
    private final boolean[] beamBlue;   // each beam is independently blue or white
    private double rot;

    public DiscoBall(EntityManager manager, Soul soul, int beams, double spin) {
        super(manager);
        this.soul = soul;
        this.beams = beams;
        this.spin = spin;
        this.beamBlue = new boolean[beams];
        randomizeColors();
        this.depth = -1;
    }

    private void randomizeColors() {
        for (int i = 0; i < beams; i++) {
            beamBlue[i] = Math.random() < 0.5;
        }
    }

    private double cx() {
        return (G.idealborder[0] + G.idealborder[1]) / 2.0;
    }

    private double cy() {
        return G.idealborder[2] + 10;
    }

    private boolean soulMoving() {
        return soul.leftHeld || soul.rightHeld || soul.upHeld || soul.downHeld;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        rot += spin;
        double cx = cx();
        double cy = cy();
        double len = G.idealborder[3] - cy + 60;
        for (int i = 0; i < beams; i++) {
            double a = Math.toRadians(rot + i * (360.0 / beams));
            double dx = soul.x - cx;
            double dy = soul.y - cy;
            double along = dx * Math.cos(a) + dy * Math.sin(a);
            double perp = -dx * Math.sin(a) + dy * Math.cos(a);
            if (along > 0 && along < len && Math.abs(perp) < 7 && G.inv <= 0) {
                if (!beamBlue[i] || soulMoving()) {
                    soul.hurt(5);
                }
            }
        }
    }

    @Override
    public boolean hitBy(double px, double py) {
        return Math.abs(px - cx()) < 22 && Math.abs(py - cy()) < 34;
    }

    @Override
    public boolean onShot() {
        // GML: shooting the ball flips EVERY beam's colour (deterministic toggle, so
        // all of them change — including the ones that just rotated in).
        for (int i = 0; i < beams; i++) {
            beamBlue[i] = !beamBlue[i];
        }
        return true;
    }

    @Override
    public int shotRating() {
        return 0; // toggled, not destroyed
    }

    @Override
    public void render(Graphics2D g) {
        double cx = cx();
        double cy = cy();
        double len = G.idealborder[3] - cy + 60;
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(3f));
        for (int i = 0; i < beams; i++) {
            double a = Math.toRadians(rot + i * (360.0 / beams));
            double ex = cx + Math.cos(a) * len;
            double ey = cy + Math.sin(a) * len;
            if (ey > cy) { // only the downward beams are in play
                g.setColor(beamBlue[i] ? new Color(0x20, 0xA9, 0xFF) : Color.WHITE);
                g.drawLine((int) cx, (int) cy, (int) ex, (int) ey);
            }
        }
        g.setStroke(old);
        BufferedImage img = Assets.sprite("spr_discoball_pl_0");
        if (img != null) {
            g.drawImage(img, (int) (cx - 20), (int) (cy - 14), null);
        } else {
            g.setColor(Color.LIGHT_GRAY);
            g.fillOval((int) (cx - 12), (int) (cy - 12), 24, 24);
        }
    }
}
