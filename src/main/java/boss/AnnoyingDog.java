package boss;

import core.Entity;
import core.EntityManager;
import core.GlobalState;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * The Annoying Dog (GML: {@code blt_tobydogbone}) that absconds with Papyrus's
 * "SPECIAL ATTACK" bone. Purely presentational — it never collides with the soul.
 * It sits at the bottom-right of the combat box gnawing the bone
 * ({@code spr_tobydogeat}), freezes in shock when Papyrus yells at it
 * ({@code spr_tobydogsurprise}), then scoots off to the right with it
 * ({@code spr_tobydogscoot}). {@link PapyrusBoss} drives the phase from its cutscene
 * dialogue ({@code bonetalk} state machine).
 *
 * // GML: blt_tobydogbone (spr_tobydogeat, image_xscale/yscale = 2, draw_self_border)
 */
public final class AnnoyingDog extends Entity {

    /** The cutscene beats, matched to Papyrus's {@code bonetalk} lines. */
    public enum Phase { EAT, SURPRISE, SCOOT }

    private static final GlobalState G = GlobalState.get();
    /** GML: image_xscale = image_yscale = 2. */
    private static final int SCALE = 2;
    /** GML sprite frame size (spr_tobydogeat is 64x38). */
    private static final int FRAME_H = 38;

    private Phase phase = Phase.EAT;
    private int surpriseFrame;        // 0 or 1 within SURPRISE
    private double animTimer;         // accumulates image_speed for frame stepping
    private int frame;                // current EAT/SCOOT animation frame
    private double hspeed;            // SCOOT drift to the right

    public AnnoyingDog(EntityManager manager) {
        super(manager);
        this.depth = -2;              // in front of bones (depth -1) and the box
        // GML created it at the box's bottom-right corner; we right-align it inside
        // the box (letting the bone poke a touch past the edge) and rest it on the floor.
        this.x = G.idealborder[1] - 110;
        this.y = G.idealborder[3] - FRAME_H * SCALE;
    }

    /** GML bonetalk 0: gnawing the bone (spr_tobydogeat, image_speed 0.2). */
    public void eat() {
        if (phase != Phase.EAT) {
            phase = Phase.EAT;
        }
    }

    /** GML bonetalk 1/2: caught in the act (spr_tobydogsurprise, frozen on a frame). */
    public void surprise(int frame) {
        phase = Phase.SURPRISE;
        this.surpriseFrame = frame;
    }

    /** GML bonetalk 3: grabs the bone and bolts to the right (spr_tobydogscoot, hspeed). */
    public void scoot() {
        if (phase != Phase.SCOOT) {
            phase = Phase.SCOOT;
            frame = 0;
            animTimer = 0;
            hspeed = 3;               // GML hspeed = 1, sped up so it clears the box
        }
    }

    @Override
    public void update() {
        switch (phase) {
            case EAT -> stepAnim(8, 0.2);
            case SCOOT -> {
                stepAnim(2, 0.2);
                x += hspeed;
                if (x > G.idealborder[1] + 20) {   // scooted off-screen → gone
                    manager.destroy(this);
                }
            }
            default -> { }            // SURPRISE holds a single reaction frame
        }
    }

    /** Advance the looping animation at {@code speed} frames-per-tick over {@code frames}. */
    private void stepAnim(int frames, double speed) {
        animTimer += speed;
        while (animTimer >= 1.0) {
            animTimer -= 1.0;
            frame = (frame + 1) % frames;
        }
    }

    private String spriteName() {
        return switch (phase) {
            case EAT -> "spr_tobydogeat_" + frame;
            case SURPRISE -> "spr_tobydogsurprise_" + surpriseFrame;
            case SCOOT -> "spr_tobydogscoot_" + frame;
        };
    }

    @Override
    public void render(Graphics2D g) {
        BufferedImage img = Assets.sprite(spriteName());
        if (img == null) {
            return;
        }
        g.drawImage(img, (int) x, (int) y,
                img.getWidth() * SCALE, img.getHeight() * SCALE, null);
    }
}
