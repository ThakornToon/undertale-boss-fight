package boss;

import core.EntityManager;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * Toriel's body (GML: {@code obj_torielboss}'s own {@code sprite_index}). Unlike
 * Asgore, Toriel's battle art is a <em>single</em> baked sprite the controller swaps
 * by state — standing, hurt, the side/sad spare poses, and the kneel/death frames —
 * so this body just draws whatever sprite name {@link TorielBoss} hands it, at the
 * GML 2× scale and a fixed world position.
 *
 * <p>It draws <b>behind</b> the combat box (high depth); the box's solid black
 * interior occludes her lower body, so the same standing sprite reads as
 * "hands resting on the box" under the tall fire box (border 7) and as a full
 * standing figure under the short hand box (border 6) — exactly as in the fight.
 *
 * // GML: obj_torielboss draw (sprite_index at image_xscale/yscale 2)
 */
public final class TorielBody extends BossBody {

    private static final double SCALE = 2.0;          // GML image_xscale/yscale = 2

    /** World position of the sprite's top-left (tuned so she sits behind the box). */
    public double baseX = 248;
    public double baseY = 38;

    /** GML sprite_index — the controller picks the pose; default is the stand pose. */
    public String spriteName = "spr_torielboss_0";
    /** GML alarm[3] hurt shudder — a horizontal offset the controller decays. */
    public double shudderX;

    public TorielBody(EntityManager manager) {
        super(manager);
        this.depth = 1100;        // behind the combat box (box depth 1000)
    }

    @Override
    public void update() {
        // Pose + shudder are driven by the controller; nothing to integrate here.
    }

    @Override
    public void render(Graphics2D g) {
        BufferedImage img = Assets.sprite(spriteName);
        if (img == null) {
            return;
        }
        int w = (int) (img.getWidth() * SCALE);
        int h = (int) (img.getHeight() * SCALE);
        g.drawImage(img, (int) (baseX + shudderX), (int) baseY, w, h, null);
    }
}
