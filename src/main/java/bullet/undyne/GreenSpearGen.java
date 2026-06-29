package bullet.undyne;

import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import util.GMLHelper;

/**
 * The queued GREEN-soul spear emitter (GML: {@code obj_greenspeargen}). The
 * {@link SpearBlocker} fills it with a per-{@code lesson} choreography via
 * {@link #queue(int, int, double, double)} (GML {@code scr_sr}), then this fires the
 * queued spears one at a time on a cadence of {@code rating × timemod} frames.
 *
 * <p>{@code speartype}: 0 → a straight {@link BlockBullet}; 1 → a weaving
 * {@link BlockSpear2}; 2 → a "saver" pair (a {@link BlockSpear2} from {@code timemod}
 * and a {@link BlockBullet} from {@code speardir}), at a fixed {@code rating × 2}
 * cadence.
 *
 * // GML: obj_greenspeargen + scr_sr
 */
public final class GreenSpearGen extends AttackPattern {

    /** One queued spear: {dir, type, timemod (gap), speedmod}. */
    private final List<double[]> queue = new ArrayList<>();
    private int spearno;
    private double cooldown = 5;     // GML Create: alarm[0] = 5
    public boolean done;

    /** GML: rating — base spacing; set by the SpearBlocker. */
    public double rating = 8;
    /** Centre the spears converge on (the shielded heart). */
    public double cx;
    public double cy;

    public GreenSpearGen(EntityManager manager, double cx, double cy, double rating, int dmg) {
        super(manager);
        this.cx = cx;
        this.cy = cy;
        this.rating = rating;
        this.dmg = dmg;
        this.depth = 0;
    }

    /**
     * GML {@code scr_sr(dir, type, gap, speed)}: append one spear to the queue.
     * {@code dir == 4} picks a random side; {@code type == 3} picks a random straight
     * /weave; {@code gap == 0} defaults to 1 (except the saver type); {@code speed ==
     * 0} defaults to 1.
     */
    public void queue(int dir, int type, double gap, double speed) {
        if (dir == 4) {
            dir = GMLHelper.irandom(3);
        }
        if (type == 3) {
            type = GMLHelper.irandom(1);
        }
        if (gap == 0 && type != 2) {
            gap = 1;
        }
        if (speed == 0) {
            speed = 1;
        }
        queue.add(new double[] { dir, type, gap, speed });
    }

    public int queued() {
        return queue.size();
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        cooldown--;
        if (cooldown > 0 || spearno >= queue.size()) {
            if (spearno >= queue.size()) {
                done = true;
            }
            return;
        }
        double[] s = queue.get(spearno);
        int dir = (int) s[0];
        int type = (int) s[1];
        double timemod = s[2];
        double speedmod = s[3];

        if (type == 0) {
            BlockBullet b = new BlockBullet(manager, cx, cy, dir, speedmod);
            b.spawnDist = laneFor(dir);
            b.dmg = dmg;
            manager.add(b);
            cooldown = Math.max(1, Math.round(rating * timemod));
        } else if (type == 1) {
            BlockSpear2 b = new BlockSpear2(manager, cx, cy, dir, speedmod);
            b.spawnDist = laneFor(b.site);   // site = the physical spawn side
            b.dmg = dmg;
            manager.add(b);
            cooldown = Math.max(1, Math.round(rating * timemod));
        } else {
            // Saver pair: a weave from timemod's side + a straight from speardir's side.
            BlockSpear2 sp = new BlockSpear2(manager, cx, cy, (int) timemod, speedmod);
            sp.spawnDist = laneFor(sp.site);
            sp.dmg = dmg;
            manager.add(sp);
            BlockBullet bb = new BlockBullet(manager, cx, cy, dir, speedmod);
            // The pair spawns in the same frame, so the feint isn't counted yet —
            // if the straight shares its spawn side, trail it behind by hand.
            bb.spawnDist = laneFor(dir) + (dir == sp.site ? LANE_GAP : 0);
            bb.dmg = dmg;
            manager.add(bb);
            cooldown = Math.max(1, Math.round(rating * 2));
        }
        spearno++;
        if (spearno >= queue.size()) {
            done = true;
        }
    }

    /** Extra distance per same-side spear already approaching (anti-overlap stagger). */
    private static final double LANE_GAP = 60;

    /**
     * The spawn distance for a spear approaching from {@code side}: the GML's 300,
     * pushed out by {@link #LANE_GAP} for every spear already inbound on that side so
     * a fresh spear (notably the yellow feint) doesn't materialise on top of one
     * that's already in flight.
     */
    private double laneFor(int side) {
        int[] n = { 0 };
        manager.with(GreenSpear.class, s -> {
            if (!s.blocked && s.site == side && s.distanceToCenter() > 230) {
                n[0]++;
            }
        });
        return 300 + n[0] * LANE_GAP;
    }

    @Override
    public void render(Graphics2D g) {
        // Invisible spawn point.
    }
}
