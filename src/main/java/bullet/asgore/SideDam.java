package bullet.asgore;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;
import util.GMLHelper;

/**
 * The side-fire warning + cloud (GML: {@code obj_sidedam} + {@code obj_sided_fire}).
 * One of the two halves of fandom pattern 2: a strip down one edge of the box is
 * marked with a blinking <b>red</b> outline and a flashing exclamation point for
 * {@code wait} frames, then it erupts — a dense flood of <em>small</em> fires
 * ({@code obj_sided_fire}, 16px) pours through the strip from the top and bottom for
 * a short burst (GML spawns {@code repeat(4)} per frame for ~14 frames, the "massive
 * cloud" that covers the side). Paired with {@link SineFireGen} so the player is
 * squeezed between the falling sine spray and the wall of fire.
 *
 * // GML: obj_sidedam + obj_sided_fire
 */
public final class SideDam extends AttackPattern {

    /** GML draw_set_color(255) = red (GML colours are BGR; 255 = 0x0000FF). */
    private static final Color WARN_RED = new Color(0xFF, 0x00, 0x00);
    private static final Color WARN_YELLOW = new Color(0xFF, 0xFF, 0x00);

    private final Soul soul;
    /** GML: side 0 = left edge, 1 = right edge. */
    public int side;
    public double len = 75;
    public int wait = 35;

    // GML con: 1 = warning, 3 = erupting flood, 4 = done.
    private int con = 1;
    private int waitTimer = -1;  // < 0 until the generator's `wait` is latched
    private int floodTimer;     // GML alarm[4] = 14 during the eruption
    private int eo;             // exclamation-point blink frame (GML eo 0..2)
    private int blink;

    public SideDam(EntityManager manager, Soul soul) {
        super(manager);
        this.soul = soul;
        this.depth = 40;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        // The Create runs before len/wait are set by the generator, so latch the
        // warning duration once on the first tick.
        if (waitTimer < 0) {
            waitTimer = wait;
        }
        if (con == 1) {
            if (--waitTimer <= 0) {
                con = 3;
                floodTimer = 14;        // GML alarm[4] = 14
            }
        } else if (con == 3) {
            // GML con==3: repeat(4) instance_create(... obj_sided_fire) every frame.
            for (int i = 0; i < 4; i++) {
                spawnFire();
            }
            if (--floodTimer <= 0) {
                manager.destroy(this);
            }
        }
    }

    /** GML obj_sided_fire: a small fire shooting through the strip, top↓ or bottom↑. */
    private void spawnFire() {
        double l = G.idealborder[0];
        double r = G.idealborder[1];
        LinearFire f = new LinearFire(manager, soul);
        f.scale = 1;                            // GML: no image_xscale → the small fire
        f.x = (side == 0)
                ? l + GMLHelper.random(len - 6) - 6
                : r - GMLHelper.random(len + 6) - 8;
        if (GMLHelper.choose(new int[] { 0, 1 }) == 0) {
            f.y = G.idealborder[2] - 16 - 5;    // from the top, moving down
            f.direction = 270;
            f.speed = 9 + GMLHelper.random(0.5);
        } else {
            f.y = G.idealborder[3] + 5;         // from the bottom, moving up
            f.direction = 90;
            f.speed = 9 + GMLHelper.random(0.5);
        }
        f.friction = 0;
        manager.add(f);
    }

    @Override
    public void render(Graphics2D g) {
        if (con != 1) {
            return;
        }
        blink++;
        double l = G.idealborder[0];
        double r = G.idealborder[1];
        double t = G.idealborder[2];
        double b = G.idealborder[3];

        // GML: a blinking exclamation point at the top of the strip, then a red
        // 2px outline over the threatened strip.
        eo = (blink / 6) % 3;
        BufferedImage ex = Assets.sprite("spr_exclamationpoint_" + (eo % 2));
        int exX = (side == 0) ? (int) (l + 12) : (int) (r - 38);
        if (ex != null) {
            g.drawImage(ex, exX, (int) (t + 24), null);
        }

        g.setColor((eo == 1) ? WARN_YELLOW : WARN_RED);
        if (side == 0) {
            g.drawRect((int) (l + 5), (int) (t + 5), (int) (len - 6), (int) (b - t - 10));
            g.drawRect((int) (l + 6), (int) (t + 6), (int) (len - 8), (int) (b - t - 12));
        } else {
            g.drawRect((int) (r - len), (int) (t + 5), (int) (len - 6), (int) (b - t - 10));
            g.drawRect((int) (r - len + 1), (int) (t + 6), (int) (len - 8), (int) (b - t - 12));
        }
    }
}
