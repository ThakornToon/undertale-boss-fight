package bullet.bones;

import battle.Soul;
import bullet.Bullet;
import core.EntityManager;
import java.awt.Graphics2D;

/**
 * A bone hanging from the ceiling: a vertical column from the top of the combat
 * box ({@code idealborder[2]}) down to its spawn {@code y} (its bottom edge). The
 * blue soul must stay <em>low</em> to clear it. Pairs with {@link SizeBone} to form
 * the gap-corridors of the later Papyrus waves.
 *
 * <p>Drawn with the same rounded knobs as {@link SizeBone}: {@code spr_bonetop}
 * anchors it to the ceiling and {@code spr_bonebottom} caps the free lower tip.
 *
 * // GML: blt_topbone (spr_bonetop/spr_bonebottom, draws from global.idealborder[2] down to y)
 */
public final class TopBone extends Bullet {

    public TopBone(EntityManager manager, Soul soul) {
        super(manager, soul);
    }

    @Override
    protected double boxTop() {
        return G.idealborder[2]; // hangs from the ceiling
    }

    @Override
    protected double boxBottom() {
        return y; // bone's bottom edge
    }

    @Override
    public void render(Graphics2D g) {
        int ceiling = (int) G.idealborder[2];
        int bottom = (int) y;
        // GML: draw_rectangle(x+3, y, x+9, idealborder[2]+10) — the 6px shaft.
        g.setColor(BoneArt.shaftColor(blue));
        int shaftTop = ceiling + 10;
        if (bottom > shaftTop) {
            g.fillRect((int) x + 3, shaftTop, 6, bottom - shaftTop);
        }
        // Rounded knobs: spr_bonetop anchored at the ceiling, spr_bonebottom at the free tip.
        BoneArt.cap(g, "spr_bonetop", blue, (int) x, ceiling + 6);
        BoneArt.cap(g, "spr_bonebottom", blue, (int) x, bottom);
    }
}
