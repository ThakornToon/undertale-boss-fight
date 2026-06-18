package battle;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;
import util.Assets;

/**
 * A purely-decorative Monster Kid (GML {@code spr_mkid}) that paces back and forth
 * across the title screen and — being Monster Kid — periodically trips and falls
 * flat on his face before picking himself up and carrying on. He has no gameplay
 * effect; he's just the running gag the boss-select menu was missing.
 *
 * <p>Animations come straight from the ported overworld sprite frames:
 * <ul>
 *   <li>{@code spr_mkid_r_0..3} / {@code spr_mkid_l_0..3} — the 4-frame walk cycle
 *       (right- / left-facing).</li>
 *   <li>{@code spr_mkid_trip_r_0..19} / {@code spr_mkid_trip_l_0..19} — the full
 *       stumble→fall→get-up sequence (frame 19 lands back on a standing pose, so a
 *       single pass leaves him ready to walk again).</li>
 * </ul>
 *
 * <p>Frames are drawn anchored at the <em>bottom-centre</em> so the wide, short
 * fallen frames lie on the same ground line his feet walk along, rather than
 * floating from a shared top-left origin.
 *
 * // GML: obj_mkid's overworld step/draw, with no story/collision — just the
 * // walk + spr_mkid_trip gag.
 */
public final class MonsterKid {

    private static final int WALK_FRAMES = 4;
    private static final int TRIP_FRAMES = 20;

    /** Ticks each walk frame is held (30 FPS tick → ~7.5 fps stride). */
    private static final int WALK_FRAME_TICKS = 4;
    /** Ticks each trip frame is held (whole stumble+recover ≈ 80 ticks ≈ 2.7 s). */
    private static final int TRIP_FRAME_TICKS = 4;

    private static final double SPEED = 1.6;     // px per tick while walking
    private static final double SCALE = 1.7;     // sprites are ~20×26 px natively

    /** Per-tick odds of starting a trip while walking (~1 in 220 ≈ every ~7 s). */
    private static final double TRIP_CHANCE = 1.0 / 220.0;

    private enum State { WALK, TRIP }

    private final int leftBound;
    private final int rightBound;
    private final int groundY;
    private final Random rng = new Random();

    private double x;
    private boolean facingRight = true;
    private State state = State.WALK;
    private int animFrame;
    private int frameTimer;

    /**
     * @param leftBound  smallest x (centre) he walks to
     * @param rightBound largest x (centre) he walks to
     * @param groundY    y his feet rest on (frames are bottom-anchored)
     */
    public MonsterKid(int leftBound, int rightBound, int groundY) {
        this.leftBound = leftBound;
        this.rightBound = rightBound;
        this.groundY = groundY;
        this.x = leftBound + (rightBound - leftBound) * 0.3;
    }

    public void update() {
        frameTimer++;
        if (state == State.WALK) {
            updateWalk();
        } else {
            updateTrip();
        }
    }

    private void updateWalk() {
        x += facingRight ? SPEED : -SPEED;
        // Turn around at the edges.
        if (x >= rightBound) {
            x = rightBound;
            facingRight = false;
        } else if (x <= leftBound) {
            x = leftBound;
            facingRight = true;
        }

        if (frameTimer >= WALK_FRAME_TICKS) {
            frameTimer = 0;
            animFrame = (animFrame + 1) % WALK_FRAMES;
        }

        // Clumsy as ever — random faceplant (never right at an edge, so the fall
        // animation stays on screen).
        if (x > leftBound + 12 && x < rightBound - 12 && rng.nextDouble() < TRIP_CHANCE) {
            state = State.TRIP;
            animFrame = 0;
            frameTimer = 0;
        }
    }

    private void updateTrip() {
        if (frameTimer >= TRIP_FRAME_TICKS) {
            frameTimer = 0;
            animFrame++;
            if (animFrame >= TRIP_FRAMES) {
                // Frame 19 left him standing again — back to strolling.
                state = State.WALK;
                animFrame = 0;
            }
        }
    }

    public void render(Graphics2D g) {
        String dir = facingRight ? "r" : "l";
        String name = state == State.TRIP
                ? "spr_mkid_trip_" + dir + "_" + animFrame
                : "spr_mkid_" + dir + "_" + animFrame;
        BufferedImage img = Assets.sprite(name);
        if (img == null) {
            return;
        }
        int w = (int) Math.round(img.getWidth() * SCALE);
        int h = (int) Math.round(img.getHeight() * SCALE);
        int drawX = (int) Math.round(x - w / 2.0);
        int drawY = groundY - h;
        g.drawImage(img, drawX, drawY, w, h, null);
    }
}
