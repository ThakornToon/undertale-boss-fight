package bullet.muffet;

import bullet.AttackPattern;
import core.EntityManager;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * The pet's giant fanged cupcake (GML: {@code obj_hideouscupcake}). It fades in and
 * rises from below the box during the pet special, then bobs as a hulking maw at the
 * bottom of the rising web — the hazard the player climbs away from. The bite itself
 * is the web's bottom-strand damage (handled in {@link battle.Soul#updatePurple()});
 * this entity is the menacing visual.
 *
 * // GML: obj_hideouscupcake
 */
public final class Cupcake extends AttackPattern {

    private static final int FRAMES = 4;

    private double siner;
    private float alpha;
    private double imageIndex;
    /** Rest height: only the fanged maw peeks up from the box bottom (clipped to it). */
    private double restY = 327;
    private double baseX;
    private boolean arrived;
    /** Set when the pet special ends — the cupcake drops away (GML: vspeed = 4). */
    public boolean falling;

    public Cupcake(EntityManager manager) {
        super(manager);
        this.depth = 800; // in front of the box, behind the web/heart
    }

    @Override
    public void update() {
        if (alpha < 1f) {
            alpha = Math.min(1f, alpha + 0.05f);
        }
        if (baseX == 0) {
            baseX = x;
        }
        imageIndex += 0.125;
        siner++;
        if (falling) {
            y += 4;
            if (y > 520) {
                manager.destroy(this);
            }
            return;
        }
        // Climb into place, then settle into a small bounded bob (a tamed version of
        // GML's cumulative wobble, which otherwise drifts the maw up over the box).
        if (!arrived) {
            y -= 4;
            if (y <= restY) {
                y = restY;
                arrived = true;
            }
            return;
        }
        x = baseX + Math.sin(siner / 6.0) * 2;
        y = restY + Math.sin(siner / 8.0) * 5;
    }

    @Override
    public void render(Graphics2D g) {
        BufferedImage img = Assets.sprite("spr_hideouscupcake_" + ((int) imageIndex % FRAMES));
        if (img == null) {
            return;
        }
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
        // Clip to the combat box so only the maw peeking up from the bottom shows — the
        // teeth/lower jaw stay hidden below the box edge (GML masks the overflow with a
        // black fill), instead of spilling over the HP bar.
        java.awt.Shape oldClip = g.getClip();
        double[] b = G.idealborder;
        g.clipRect((int) b[0], (int) b[2], (int) (b[1] - b[0]), (int) (b[3] - b[2]));
        g.drawImage(img, (int) x, (int) y, null);
        g.setClip(oldClip);
        g.setComposite(old);
    }
}
