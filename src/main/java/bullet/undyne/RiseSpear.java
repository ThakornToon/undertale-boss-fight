package bullet.undyne;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;

/**
 * A RED-soul rising spear (GML: {@code obj_risespearbullet}). Like a mole popping out
 * of a hole: first only its pointed tip pokes out of a floor base (the telegraph),
 * then it <b>shoots straight up and out</b> of the box — the tip leaves the hole and
 * the whole spear flies off the top. The player reads the tip and steps out of that
 * column. A tall, thin spear, clipped to the box so the part still "in the hole" is
 * hidden.
 *
 * // GML: obj_risespearbullet (type 0 = rise from the floor)
 */
public final class RiseSpear extends AttackPattern {

    private static final int TELE = 24;          // tip emerges gradually (telegraph)
    private static final double LAUNCH = 9;      // upward launch speed
    private static final double SHAFT = 80;      // spear length
    private static final int HEAD = 14;

    private final Soul soul;
    private final double floorY;
    private final double boxTop;
    private int phase;       // 0 telegraph · 1 launch
    private int timer;
    private double headY;    // y of the pointed tip
    private double alpha = 1;

    public RiseSpear(EntityManager manager, Soul soul, double columnX, double floorY, int dmg) {
        super(manager);
        this.soul = soul;
        this.dmg = dmg;
        this.depth = -200;
        this.x = columnX;
        this.floorY = floorY;
        this.boxTop = G.idealborder[2];
        this.headY = floorY - 1;    // the tip only just breaks the surface
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        timer++;
        if (phase == 0) {
            // Telegraph: the tip rises gradually out of the hole (little → full head)
            // so the player can read which column is about to fire.
            headY = floorY - 1 - 15.0 * Math.min(1.0, timer / (double) TELE);
            if (timer >= TELE) {
                phase = 1;
                timer = 0;
            }
        } else {
            headY -= LAUNCH;                 // shoot up and out of the hole
            if (headY < boxTop - SHAFT) {    // fully flown off the top
                manager.destroy(this);
                return;
            }
        }
        if (phase == 1 && collidesSoul()) {
            soul.hurt(dmg);
        }
    }

    private boolean collidesSoul() {
        if (G.inv > 0) {
            return false;
        }
        double top = headY;
        double bot = Math.min(headY + SHAFT, floorY);   // the part still in the hole can't hit
        // The spear fills its column — only an adjacent column is safe.
        return Math.abs(soul.x - x) < 14 && soul.y + Soul.HALF > top && soul.y - Soul.HALF < bot;
    }

    @Override
    public void render(Graphics2D g) {
        if (alpha <= 0) {
            return;
        }
        // Clip to the box interior so the part in the hole (below the floor) and the
        // part that has flown past the top are both hidden.
        Shape oldClip = g.getClip();
        int bl = (int) G.idealborder[0];
        int bw = (int) (G.idealborder[1] - G.idealborder[0]);
        g.clipRect(bl, (int) boxTop, bw, (int) (floorY - boxTop));
        g.setColor(new Color(1f, 1f, 1f, (float) Math.min(1, alpha)));
        g.fillRect((int) (x - 4), (int) (headY + HEAD), 8, (int) (SHAFT - HEAD));   // wide shaft
        g.fillPolygon(
                new int[] { (int) (x - 15), (int) x, (int) (x + 15) },
                new int[] { (int) (headY + HEAD), (int) headY, (int) (headY + HEAD) }, 3); // big tip
        g.setClip(oldClip);
    }
}
