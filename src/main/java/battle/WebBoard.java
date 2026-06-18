package battle;

import core.Entity;
import core.EntityManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;

/**
 * The purple "web" the Muffet fight is fought on (GML: the line geometry owned by
 * {@code obj_purpleheart}). Holds the horizontal strands the {@link SoulMode#PURPLE}
 * soul is locked to and that every spider/donut/croissant bullet rides along, and
 * draws them. The heart's own motion lives in {@link Soul#updatePurple()} — exactly
 * as in GML, where the purple heart object both moved the soul and drew the web — so
 * this class is the shared geometry + renderer the soul mutates and the bullets read.
 *
 * <p>Geometry (GML {@code obj_purpleheart} Create): {@code yamt} strands spaced
 * {@code yspace} apart starting at {@code yzero}, spanning {@code xmid±xlen}. Strand
 * {@code i} sits at {@code yzero + yspace*i + yoff}. The pet special raises the web
 * ({@code yzero} climbs, {@code yamt} grows, {@code yoff} scrolls) — that animation is
 * driven by the soul as it tracks the heart, since the two are one object in GML.
 *
 * // GML: obj_purpleheart (the web lines + xmid/xlen/yzero/yspace/yamt/yoff/yadd)
 */
public final class WebBoard extends Entity {

    /** GML purple line colour: make_color_rgb(128,0,128). */
    private static final Color WEB_PURPLE = new Color(128, 0, 128);

    /** GML ttype: 0/3 = normal line-lock fight · 1 = pet-special rising web. */
    public int type;

    public double xmid;
    public double xlen = 100;
    public double yzero;
    public double yspace = 40;
    /** Number of strands (GML yamt — 3 normally, grows during the pet special). */
    public int yamt = 3;
    /** GML yoff — vertical scroll of the whole web (pet special). */
    public double yoff;
    /** GML yadd / yadd2 — per-frame rise speed and its target value. */
    public double yadd;
    public double yadd2 = 3;
    /** GML yz2 — accumulator that adds a new strand every {@code yspace} of rise. */
    public double yz2;

    public boolean visible = true;
    /** Cosmetic left/right rock during the pet special (degrees) — set with the box. */
    public double tilt;

    /** Animation tick — drives the gentle strand wave during the pet special. */
    private int tick;

    public WebBoard(EntityManager manager) {
        super(manager);
        this.depth = 900; // in front of the box fill (1000), behind the bullets/heart
    }

    /** Strand {@code i}'s current screen y. */
    public double lineY(int i) {
        return yzero + yspace * i + yoff;
    }

    @Override
    public void update() {
        // No geometry of its own — Soul.updatePurple() owns the web animation (GML:
        // obj_purpleheart drove both the heart and the strands from one Step event).
        // Only the cosmetic wave phase advances here.
        tick++;
    }

    @Override
    public void render(Graphics2D g) {
        // Only draw while the player is actually dodging on the web (the enemy turn) —
        // checked at render time so the strands never bleed into the menu on the frame
        // a turn ends.
        if (!visible || core.GlobalState.get().mnfight != core.TurnManager.ENEMY_TURN) {
            return;
        }
        java.awt.geom.AffineTransform oldTx = null;
        if (tilt != 0) {
            // Rock about the box centre so the strands rock in lock-step with the box.
            double[] box = core.GlobalState.get().idealborder;
            double pivotY = (box[2] + box[3]) / 2.0;
            oldTx = g.getTransform();
            g.rotate(Math.toRadians(tilt), xmid, pivotY);
        }
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(2f));
        g.setColor(WEB_PURPLE);
        int x0 = (int) (xmid - xlen);
        int x1 = (int) (xmid + xlen);
        // During the pet special the web rides on the pet's pulled "paper", so the
        // strands ripple (GML's sprite_create_from_screen warp); the normal fight draws
        // them dead straight.
        double amp = type == 0 ? 0 : 2.0;
        for (int i = 0; i < yamt; i++) {
            double base = lineY(i);
            if (amp == 0) {
                int y = (int) Math.round(base);
                g.drawLine(x0, y, x1, y);
                continue;
            }
            int prevX = x0;
            int prevY = (int) Math.round(base + Math.sin((x0 + tick * 2 + i * 18) / 30.0) * amp);
            for (int x = x0 + 8; x <= x1; x += 8) {
                int y = (int) Math.round(base + Math.sin((x + tick * 2 + i * 18) / 30.0) * amp);
                g.drawLine(prevX, prevY, x, y);
                prevX = x;
                prevY = y;
            }
        }
        g.setStroke(old);
        if (oldTx != null) {
            g.setTransform(oldTx);
        }
    }
}
