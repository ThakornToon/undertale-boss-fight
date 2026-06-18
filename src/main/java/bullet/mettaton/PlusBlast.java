package bullet.mettaton;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;

/**
 * The plus-shaped detonation of a {@link PlusBomb}. Its four arms grow over a few
 * frames; the soul is hurt while inside the cross (step sideways out of an arm to
 * survive). It destroys black-circle boxes and solid blocks in its arms and chains
 * into other plus-bombs it reaches.
 *
 * // GML: spr_plusbomb_*blast — the plus-bomb explosion
 */
public final class PlusBlast extends AttackPattern {

    private static final int LIFE = 18;
    private static final double ARM = 340;   // the cross reaches the screen edges
    private static final double THICK = 11;

    private final Soul soul;
    private int age;
    private double len;

    public PlusBlast(EntityManager manager, Soul soul, double x, double y) {
        super(manager);
        this.soul = soul;
        this.x = x;
        this.y = y;
        this.dmg = 5;
        this.depth = -2;
    }

    private boolean inCross(double px, double py) {
        boolean hor = Math.abs(py - y) < THICK && Math.abs(px - x) < len;
        boolean ver = Math.abs(px - x) < THICK && Math.abs(py - y) < len;
        return hor || ver;
    }

    @Override
    public void update() {
        age++;
        len = ARM * Math.min(1.0, age / 8.0);

        if (G.inv <= 0 && inCross(soul.x, soul.y)) {
            soul.hurt(dmg);
        }
        if (age >= LIFE) {
            manager.destroy(this);
        }
    }

    @Override
    public void render(Graphics2D g) {
        Composite oc = g.getComposite();
        float a = (float) Math.max(0, 1.0 - age / (double) LIFE);
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, a));
        g.setColor(new Color(0xFF, 0xE0, 0x40));
        g.fillRect((int) (x - len), (int) (y - THICK), (int) (len * 2), (int) (THICK * 2));
        g.fillRect((int) (x - THICK), (int) (y - len), (int) (THICK * 2), (int) (len * 2));
        g.setComposite(oc);
    }
}
