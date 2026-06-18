package bullet.undyne;

import core.EntityManager;
import util.GMLHelper;

/**
 * The GREEN-soul "feint" spear (GML: {@code obj_blockbullet2}). It flies <b>straight
 * in</b> from one side, and only when it nears the shield does it quickly <b>relocate</b>
 * around the rim to the <em>opposite</em> side and lunge at the heart from there. The
 * yellow arrow always points at the side it will <em>actually</em> strike from
 * (GML: {@code image_index = truesite = opposite(site)}), so block wherever the head
 * points (head right → block right; head up → block up).
 *
 * <p>Approach and strike move at a steady speed; the relocate sweep is quick (it is a
 * fast reposition, not a slow curve).
 *
 * // GML: obj_blockbullet2 (speartype 1, truesite arrow)
 */
public final class BlockSpear2 extends GreenSpear {

    private static final double SPEED = 8;         // straight approach / strike speed
    private static final double RELOC_R = 44;       // relocate just outside the shield
    private static final int RELOC_FRAMES = 8;      // reposition

    private final int blkSide;
    private final double spawnAng;
    private final double blockAng;
    private int phase;          // 0 straight approach · 1 relocate · 2 strike
    private double rr = 300;
    private double ang;
    private int relocTimer;

    public BlockSpear2(EntityManager manager, double cx, double cy,
                       int blockSide, double speedmod, double rating) {
        super(manager, cx, cy, opposite(blockSide), speedmod);
        this.blkSide = blockSide;
        this.spawnAng = sideAngle(opposite(blockSide));
        this.blockAng = sideAngle(blockSide);
        this.ang = spawnAng;
    }

    @Override
    public int blockSide() {
        return blkSide;
    }

    /** GML: image_index = truesite = opposite(site) = blkSide — the yellow feint arrow. */
    @Override
    protected String arrowSpriteName() {
        return "spr_bullet_testx_arrow_" + blkSide;
    }

    private static int opposite(int side) {
        return switch (side) {
            case 0 -> 1;
            case 1 -> 0;
            case 2 -> 3;
            default -> 2;
        };
    }

    /** GML angle (0 right, 90 up, 180 left, 270 down) toward a side. */
    private static double sideAngle(int side) {
        return switch (side) {
            case 0 -> 180;   // left
            case 1 -> 0;     // right
            case 2 -> 270;   // below
            default -> 90;   // above
        };
    }

    @Override
    protected void place() {
        rr = spawnDist;   // honour the generator's same-side stagger
        reposition();
        placed = true;
    }

    private void reposition() {
        x = cx + GMLHelper.lengthdir_x(rr, ang);
        y = cy + GMLHelper.lengthdir_y(rr, ang);
    }

    @Override
    protected void step() {
        switch (phase) {
            case 0 -> {                       // straight in from the spawn side
                rr -= SPEED * speedmod;
                if (rr <= RELOC_R) {
                    rr = RELOC_R;
                    phase = 1;
                    relocTimer = 0;
                }
                reposition();
            }
            case 1 -> {                       // quick relocate around the rim
                relocTimer++;
                ang = spawnAng + 180.0 * (relocTimer / (double) RELOC_FRAMES);
                reposition();
                if (relocTimer >= RELOC_FRAMES) {
                    ang = blockAng;
                    phase = 2;
                }
            }
            default -> {                      // strike straight in from the block side
                rr -= SPEED * speedmod;
                reposition();
                if (rr < -30) {
                    manager.destroy(this);
                }
            }
        }
    }
}
