package bullet.bones;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * Shared drawing for Papyrus's sliding bones ({@link SizeBone} / {@link TopBone}).
 * A Papyrus bone is a flat shaft topped at each end by a rounded knob sprite —
 * {@code spr_bonetop} / {@code spr_bonebottom} — exactly as the GML draws it, so
 * the bones look finished like Sans's instead of bare rectangles. Each cap sprite
 * has a blue frame ({@code _0}, RGB 0,162,232) and a white frame ({@code _1}); the
 * exported PNG frame order is the reverse of the GML's {@code image_index}, so the
 * blue bone uses {@code _0} and the white bone {@code _1}.
 *
 * // GML: blt_sizebone / blt_topbone Draw event (draw_sprite_part 105/106 + draw_rectangle)
 */
final class BoneArt {

    /** GML draw_set_color(16754964) → RGB(20,169,255), the blue-bone shaft colour. */
    private static final Color BLUE_SHAFT = new Color(20, 169, 255);

    /** Native cap sprite size (spr_bonetop/spr_bonebottom are 13×10). */
    private static final int CAP_W = 13;
    private static final int CAP_H = 10;

    private BoneArt() {
    }

    /** Fill colour for the bone's central shaft. */
    static Color shaftColor(boolean blue) {
        return blue ? BLUE_SHAFT : Color.WHITE;
    }

    /**
     * Draw a rounded end-cap at {@code (sx, sy)} using {@code base} + the white
     * ({@code _0}) or blue ({@code _1}) frame. No-op if the sprite is absent.
     */
    static void cap(Graphics2D g, String base, boolean blue, int sx, int sy) {
        BufferedImage img = Assets.sprite(base + (blue ? "_0" : "_1"));
        if (img != null) {
            g.drawImage(img, sx, sy, CAP_W, CAP_H, null);
        }
    }
}
