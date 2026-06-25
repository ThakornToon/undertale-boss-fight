package boss;

import core.Entity;
import core.EntityManager;
import core.Game;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * The "GOD of Hyperdeath" cosmic backdrop (GML: the scrolling rainbow starfield the
 * room switches to once Asriel powers up). The calm Fire-Magic intro plays over plain
 * black; the moment the named-attack gauntlet begins the field switches on, so this
 * entity just toggles {@link #active} and paints a slow rainbow nebula with twinkling
 * stars behind everything else.
 *
 * <p>Drawn at the highest depth so it sits behind the body, the box and the bullets
 * (the scene has already filled the frame black, so an inactive backdrop is a no-op).
 *
 * // GML: the obj_asrielb room's cosmic background
 */
public final class AsrielBackground extends Entity {

    /** Off during the calm intro (plain black); on for the cosmic gauntlet. */
    public boolean active;

    private double t;
    // A fixed scatter of stars, each with its own hue and twinkle phase.
    private final double[] sx;
    private final double[] sy;
    private final double[] shue;
    private final double[] sphase;
    private final double[] ssize;

    public AsrielBackground(EntityManager manager) {
        super(manager);
        this.depth = 100000;        // behind the body (1100) and the box (1000)
        int n = 110;
        sx = new double[n];
        sy = new double[n];
        shue = new double[n];
        sphase = new double[n];
        ssize = new double[n];
        java.util.Random r = new java.util.Random(0xA5C1EL); // stable layout
        for (int i = 0; i < n; i++) {
            sx[i] = r.nextDouble() * Game.WIDTH;
            sy[i] = r.nextDouble() * Game.HEIGHT;
            shue[i] = r.nextDouble();
            sphase[i] = r.nextDouble() * Math.PI * 2;
            ssize[i] = 1 + r.nextDouble() * 2.5;
        }
    }

    @Override
    public void update() {
        if (active) {
            t += 1;
        }
    }

    /** Part B's "Angel of Death" backdrop is black with a dim starfield (not the bright nebula). */
    public boolean dark;

    @Override
    public void render(Graphics2D g) {
        if (!active) {
            return;
        }
        if (!dark) {
            // Part A: a slow rainbow nebula, kept dim so the line-art and bullets read.
            int bands = 24;
            int bh = Game.HEIGHT / bands + 1;
            for (int i = 0; i < bands; i++) {
                float hue = (float) (((i / (double) bands) + t * 0.0015) % 1.0);
                g.setColor(Color.getHSBColor(hue, 0.85f, 0.28f));
                g.fillRect(0, i * bh, Game.WIDTH, bh);
            }
        }
        // Twinkling colored stars drifting slowly downward.
        for (int i = 0; i < sx.length; i++) {
            double y = (sy[i] + t * 0.4) % Game.HEIGHT;
            float tw = (float) (0.55 + 0.45 * Math.sin(t * 0.06 + sphase[i]));
            g.setColor(Color.getHSBColor((float) shue[i], 0.35f, dark ? tw * 0.7f : tw));
            int s = (int) Math.round(ssize[i]);
            g.fillRect((int) sx[i], (int) y, s, s);
        }
    }
}
