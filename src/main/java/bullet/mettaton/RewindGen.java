package bullet.mettaton;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * The "REC → REV" rewind rows (examples 12/13). Four neatly-spaced pieces (plus-bombs
 * and black-circle boxes) drop per row while a red "REC" shows in the box corner;
 * after a while it flips to "REV" and every piece reverses straight back up the way it
 * came — anything already shot stays gone.
 *
 * // GML: obj_mettattackgen rewind rows
 */
public final class RewindGen extends AttackPattern {

    private static final int ROWS = 4;

    private final Soul soul;
    private final int interval;
    private final double speed;
    private int timer;
    private int row;
    private boolean rev;
    private boolean tracking;
    private double row4Y;

    public RewindGen(EntityManager manager, Soul soul, int interval, double speed) {
        super(manager);
        this.soul = soul;
        this.interval = Math.max(8, interval);
        this.speed = speed;
        this.depth = -4; // draw the REC/REV label in front
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        if (rev) {
            return;
        }
        if (row < ROWS) {
            if (--timer <= 0) {
                spawnRow();
                timer = interval;
                if (row >= ROWS) {
                    tracking = true; // follow the 4th (last) row down
                }
            }
        }
        // Reverse the moment the 4th row touches the box top — by then rows 1–3 are
        // still inside the box (close spacing keeps all four on screen).
        if (tracking) {
            row4Y += speed;
            if (row4Y >= G.idealborder[2]) {
                rev = true;
                reverseAll();
            }
        }
    }

    private void spawnRow() {
        double l = G.idealborder[0];
        double r = G.idealborder[1];
        for (int i = 0; i < 4; i++) {
            double x = l + (i + 0.5) * (r - l) / 4.0;
            if ((i + row) % 2 == 0) {
                PlusBomb b = new PlusBomb(manager, soul, x, 0, speed);
                b.noBoundsCull = true;
                manager.add(b);
            } else {
                CircleBox b = new CircleBox(manager, soul, x, 0, speed);
                b.noBoundsCull = true;
                manager.add(b);
            }
        }
        row++;
    }

    private void reverseAll() {
        // The rewind (REV) travels back up 1.2× faster than the descent (REC).
        manager.with(PlusBomb.class, b -> b.vspeed = -Math.abs(b.vspeed) * 1.2);
        manager.with(CircleBox.class, b -> b.vspeed = -Math.abs(b.vspeed) * 1.2);
    }

    @Override
    public void render(Graphics2D g) {
        int rx = (int) G.idealborder[1] - 54;
        int ry = (int) G.idealborder[3] - 12;
        g.setColor(rev ? new Color(0x40, 0xA0, 0xFF) : new Color(0xFF, 0x30, 0x30));
        g.fillOval(rx, ry - 9, 9, 9);
        g.setFont(util.Fonts.ui(14f));
        g.setColor(Color.WHITE);
        g.drawString(rev ? "REV" : "REC", rx + 13, ry);
    }
}
