package bullet.asgore;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Graphics2D;
import util.GMLHelper;

/**
 * The sine-fire spray (GML: {@code obj_sinefiregen_asg_lv2_usethis} /
 * {@code obj_sinefiregen_asglv3}). From a point bobbing near the top centre it
 * streams {@link HelixFire}, alternating the weave direction so the flames fan out
 * and rain across the box, while {@link SideDam} periodically threatens one edge.
 * {@code lv3} is faster and denser than {@code lv2}.
 *
 * // GML: obj_sinefiregen_asg_lv2_usethis / obj_sinefiregen_asglv3
 */
public final class SineFireGen extends AttackPattern {

    private final Soul soul;
    public int lv = 2;

    private double s = GMLHelper.random(360);
    private int off;
    private int side;
    private int fireTimer = 1;
    private int sideTimer = 30;

    public SineFireGen(EntityManager manager, Soul soul, int lv) {
        super(manager);
        this.soul = soul;
        this.lv = lv;
        this.depth = 50;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        if (--fireTimer <= 0) {
            emitFire();
        }
        if (--sideTimer <= 0) {
            emitSide();
        }
    }

    private void emitFire() {
        boolean lv3 = lv == 3;
        s += lv3 ? 2 : 1.5;
        double cx = G.idealborder[0] + (G.idealborder[1] - G.idealborder[0]) / 2
                + Math.cos(off / 6.0) * 20;
        double cy = 130 + Math.sin(off / 5.0) * 24;
        HelixFire f = new HelixFire(manager, soul);
        f.x = cx;
        f.y = cy;
        f.sf = lv3 ? 5 : 3.5;
        f.vspeed = lv3 ? 5 : 4;
        f.s = s;
        side++;
        if (side >= 4) {
            side = GMLHelper.choose(new int[] { -2, -1 });
        }
        double sv = lv3 ? 9 : 10;
        f.sv = (side <= 1) ? sv : -sv;
        manager.add(f);
        off++;
        f.vspeed += Math.sin(off / 6.0) * 0.2;
        fireTimer = lv3 ? 6 : 5;
    }

    private void emitSide() {
        SideDam sd = new SideDam(manager, soul);
        sd.side = (soul.x < (G.idealborder[0] + G.idealborder[1]) / 2) ? 0 : 1;
        sd.len = (lv == 3) ? 60 : 75;
        sd.wait = (lv == 3) ? 25 : 35;
        manager.add(sd);
        sideTimer = (lv == 3) ? 40 : 50;
    }

    @Override
    public void render(Graphics2D g) {
    }
}
