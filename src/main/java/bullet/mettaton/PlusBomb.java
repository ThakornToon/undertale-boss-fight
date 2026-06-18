package bullet.mettaton;

import battle.Soul;
import bullet.AttackPattern;
import bullet.Bullet;
import bullet.Shootable;
import core.EntityManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * A plus-bomb (GML: {@code spr_plusbomb}) — examples 1/12/13/14/15/17. Drifts down;
 * shooting it detonates a slow plus-shaped blast ({@link PlusBlast}) that you dodge
 * by stepping sideways, and which destroys adjacent boxes/blocks and chains into
 * other bombs. Touching the bomb itself hurts.
 *
 * // GML: obj_mettattackgen plus-bomb bullets
 */
public final class PlusBomb extends Bullet implements Shootable {

    private static final double HALF = 13;
    // The detonation reach: a long thin plus that catches blocks/bombs in line.
    private static final double REACH = 340;
    private static final double CATCH = 18;
    private double frame;
    private boolean detonated;
    /** Rewind rows persist off-screen (so all rows survive to reverse back up). */
    public boolean noBoundsCull;

    public PlusBomb(EntityManager manager, Soul soul, double x, double y, double vspeed) {
        super(manager, soul);
        this.x = x;
        this.y = y;
        this.vspeed = vspeed;
        this.dmg = 4;
        this.depth = -1;
    }

    @Override
    protected double halfWidth() {
        return HALF;
    }

    @Override
    protected double boxTop() {
        return y - HALF;
    }

    @Override
    protected double boxBottom() {
        return y + HALF;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        y += vspeed;
        frame += 0.3;
        if (G.inv <= 0 && Math.abs(soul.x - x) < HALF + 2 && Math.abs(soul.y - y) < HALF + 2) {
            onHitSoul();
        }
        double hi = noBoundsCull ? 900 : G.idealborder[3] + 30;
        double lo = noBoundsCull ? -900 : -40;
        // Cull off the top only when rising (reversed rewind); bombs descending from
        // above the screen must be left to fall into view.
        if (y > hi || (y < lo && vspeed < 0)) {
            manager.destroy(this);
        }
    }

    @Override
    public boolean hitBy(double px, double py) {
        return Math.abs(px - x) < HALF + 2 && Math.abs(py - y) < HALF + 3;
    }

    @Override
    public boolean onShot() {
        if (detonated) {
            return true;
        }
        detonated = true;
        // Detonate now, while bomb and any paired block are still aligned: clear the
        // pure-white solid blocks in the plus and chain other plus-bombs.
        manager.with(AttackPattern.class, e -> {
            if (e == this) {
                return;
            }
            double dx = Math.abs(e.x - x);
            double dy = Math.abs(e.y - y);
            boolean inPlus = (dy < CATCH && dx < REACH) || (dx < CATCH && dy < REACH);
            if (!inPlus) {
                return;
            }
            if (e instanceof SolidBlock sb) {
                manager.destroy(sb);
            } else if (e instanceof PlusBomb pb) {
                pb.onShot();
            }
        });
        manager.add(new PlusBlast(manager, soul, x, y)); // the dodgeable soul hazard
        manager.destroy(this);
        return true;
    }

    @Override
    public void render(Graphics2D g) {
        BufferedImage img = Assets.sprite("spr_plusbomb_" + (((int) frame) % 2));
        if (img != null) {
            g.drawImage(img, (int) (x - img.getWidth() / 2.0), (int) (y - img.getHeight() / 2.0), null);
        } else {
            g.setColor(Color.WHITE);
            g.fillRect((int) (x - 10), (int) (y - 4), 20, 8);
            g.fillRect((int) (x - 4), (int) (y - 10), 8, 20);
        }
    }
}
