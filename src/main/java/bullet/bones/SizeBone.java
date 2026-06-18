package bullet.bones;

import battle.Soul;
import bullet.Bullet;
import core.EntityManager;
import java.awt.Graphics2D;

/**
 * The standard Papyrus/Sans bone: a vertical column rising from its spawn
 * {@code y} (its top edge) down to the bottom of the combat box
 * ({@code idealborder[3]}). Slides horizontally on {@code hspeed}; the blue soul
 * jumps to clear it. Lower spawn {@code y} (further above the floor) = taller bone.
 *
 * <p>Drawn the way the GML does: a white (or blue) shaft capped by the rounded
 * {@code spr_bonetop} knob at the free upper end and {@code spr_bonebottom} at the
 * floor — not a bare rectangle — so it reads like Sans's bones.
 *
 * // GML: blt_sizebone (spr_bonetop/spr_bonebottom, draws down to global.idealborder[3])
 */
public final class SizeBone extends Bullet {

    public SizeBone(EntityManager manager, Soul soul) {
        super(manager, soul);
    }

    @Override
    protected double boxTop() {
        return y; // bone's top edge
    }

    @Override
    protected double boxBottom() {
        return G.idealborder[3]; // extends to the floor
    }

    @Override
    public void render(Graphics2D g) {
        int top = (int) y;
        int floor = (int) G.idealborder[3];
        // GML: draw_rectangle(x+3, y+4, x+9, idealborder[3]-6) — the 6px shaft.
        g.setColor(BoneArt.shaftColor(blue));
        int shaftTop = top + 4;
        int shaftBottom = floor - 6;
        if (shaftBottom > shaftTop) {
            g.fillRect((int) x + 3, shaftTop, 6, shaftBottom - shaftTop);
        }
        // Rounded knobs: spr_bonetop at the free upper end, spr_bonebottom at the floor.
        BoneArt.cap(g, "spr_bonetop", blue, (int) x, top);
        BoneArt.cap(g, "spr_bonebottom", blue, (int) x, floor - 10);
    }
}
