package bullet.muffet;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import util.GMLHelper;

/**
 * Muffet's bullet engine (GML: {@code obj_spiderbulletgen}). One generator is created
 * per enemy turn; {@link #type} (= the boss's {@code turnamt}, 0..15) selects one of
 * the 16 hand-authored patterns. {@link #buildPattern()} ports each pattern verbatim
 * from the GML {@code event_user(13)} block — a sequence of {@code scr_sp} calls plus
 * the {@code firingrate}/{@code turntimer} tweaks — and {@link #update()} fires the
 * queued shots on their per-shot delays, exactly like the GML {@code alarm[2]} loop.
 *
 * <p>{@code scr_sp(btype, bspeed, bchoice, bside, btime)}: {@code btype} 0 spider /
 * 1 donut / 2 croissant / 3 spider-pair; {@code bchoice} strand 1..3 (0 = random);
 * {@code bside} 0 L→R / 1 R→L / 2 random; {@code btime} frames until the next shot
 * (0 = use the current {@code global.firingrate}).
 *
 * // GML: obj_spiderbulletgen
 */
public final class SpiderBulletGen extends AttackPattern {

    private final Soul soul;

    /** GML type = turnamt — which of the 16 patterns to fire. */
    public int type;
    /** GML dmg — base bullet damage (= monster ATK − bribe), applied to each shot. */
    public int dmg = 8;
    /** GML obj_spiderb.turnamt — at ≥ 15 every bullet does one less damage. */
    public int turnAmt;

    /** The queued shots: {btype, bspeed, bchoice, bside, btime}. */
    private final List<double[]> queue = new ArrayList<>();
    private int bno;
    /** Frames until the next shot fires (GML alarm[2]; starts at 10). */
    private int fireTimer = 10;
    private boolean built;

    public SpiderBulletGen(EntityManager manager, Soul soul) {
        super(manager);
        this.soul = soul;
        this.depth = 50;
    }

    /** GML scr_sp: queue one shot. */
    private void sp(int btype, double bspeed, int bchoice, int bside, int btime) {
        queue.add(new double[] { btype, bspeed, bchoice, bside, btime });
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        if (!built) {
            return; // the boss calls buildPattern() right after configuring type/dmg
        }
        if (bno >= queue.size()) {
            manager.destroy(this);   // queue drained; the live bullets carry on
            return;
        }
        if (--fireTimer > 0) {
            return;
        }
        double[] s = queue.get(bno);
        fire((int) s[0], s[1], (int) s[2], (int) s[3]);
        // GML: alarm[2] = btime (or firingrate if 0); then bno++.
        int btime = (int) s[4];
        fireTimer = (btime == 0) ? G.firingrate : btime;
        bno++;
    }

    /** GML alarm[2]: spawn the shot for the current queue entry. */
    private void fire(int btype, double bspeed, int bchoice, int bside) {
        if (btype == 3) {
            // A pair of spiders on two strands (GML: btype == 3).
            int c1;
            int c2;
            switch (bchoice) {
                case 1 -> { c1 = 1; c2 = 2; }
                case 2 -> { c1 = 1; c2 = 3; }
                default -> { c1 = 2; c2 = 3; }
            }
            int side = (bside == 2) ? GMLHelper.choose(new int[] { 0, 1 }) : bside;
            spawnSpider(new SpiderBullet(manager, soul), c1, side, bspeed);
            spawnSpider(new SpiderBullet(manager, soul), c2, side, bspeed);
            return;
        }
        SpiderBullet b = switch (btype) {
            case 1 -> new DonutBullet(manager, soul);
            case 2 -> new Croissant(manager, soul);
            default -> new SpiderBullet(manager, soul);
        };
        int choice = (bchoice == 0) ? GMLHelper.choose(new int[] { 1, 2, 3 }) : bchoice;
        int side = (bside == 2) ? GMLHelper.choose(new int[] { 0, 1 }) : bside;
        spawnSpider(b, choice, side, bspeed);
    }

    private void spawnSpider(SpiderBullet b, int choice, int side, double speed) {
        b.choice = choice;
        b.side = side;
        b.speedfactor = speed;
        b.dmg = (turnAmt >= 15) ? dmg - 1 : dmg;
        manager.add(b);
    }

    @Override
    public void render(Graphics2D g) {
        // Invisible spawn point.
    }

    /**
     * GML {@code event_user(13)}: queue this turn's pattern and apply its
     * {@code firingrate}/{@code turntimer} tweaks. The boss sets {@code turntimer=180}
     * and {@code firingrate=10} before calling this.
     */
    public void buildPattern() {
        built = true;
        switch (type) {
            case 0 -> {
                sp(0, 8, 1, 1, 0); sp(0, 8, 1, 1, 20); sp(0, 8, 3, 1, 0); sp(0, 8, 3, 1, 20);
                sp(0, 8, 2, 0, 0); sp(0, 8, 2, 0, 20); sp(0, 8, 3, 0, 0); sp(0, 8, 1, 0, 0);
                G.firingrate = 10; G.turntimer -= 30;
            }
            case 1 -> {
                sp(0, 8, 2, 1, 0); sp(0, 8, 3, 1, 16); sp(0, 8, 1, 1, 0); sp(0, 8, 2, 1, 16);
                sp(0, 8, 2, 0, 0); sp(0, 8, 1, 0, 16); sp(0, 8, 2, 0, 0); sp(0, 8, 3, 0, 16);
                sp(0, 8, 2, 1, 0); sp(0, 8, 3, 1, 16); sp(0, 8, 1, 1, 0); sp(0, 8, 2, 1, 16);
                G.firingrate = 8;
            }
            case 2 -> {
                sp(3, 9, 3, 1, 0); sp(0, 9, 1, 1, 16); sp(3, 9, 1, 1, 0); sp(0, 9, 3, 1, 16);
                sp(0, 9, 2, 0, 16); sp(3, 9, 2, 0, 16); sp(0, 9, 2, 1, 16); sp(3, 9, 2, 1, 16);
                G.firingrate = 12;
            }
            case 3 -> {
                sp(0, 9, 1, 1, 8); sp(0, 9, 3, 1, 8); sp(0, 9, 2, 1, 16); sp(3, 9, 1, 0, 13);
                sp(3, 9, 3, 0, 13); sp(3, 9, 2, 0, 20); sp(1, 8, 1, 0, 20); sp(1, 8, 3, 0, 20);
                sp(1, 8, 1, 0, 20);
                G.turntimer += 10; G.firingrate = 14;
            }
            case 4 -> {
                sp(3, 8, 2, 1, 18); sp(0, 8, 2, 1, 18); sp(3, 9, 2, 1, 15); sp(0, 9, 2, 1, 15);
                sp(3, 9.5, 2, 1, 14); sp(0, 9.5, 2, 1, 14); sp(3, 10, 2, 1, 13); sp(0, 10, 2, 1, 13);
                sp(3, 10.5, 2, 1, 12); sp(0, 10.5, 2, 1, 12); sp(3, 11, 2, 1, 11); sp(0, 11, 2, 1, 11);
                sp(3, 12, 2, 1, 10); sp(0, 12, 2, 1, 10); sp(3, 13, 2, 1, 9); sp(0, 13, 2, 1, 9);
                sp(3, 13, 2, 1, 9); sp(0, 13, 2, 1, 9);
                G.firingrate = 14;
            }
            case 5 -> {
                sp(3, 10, 2, 1, 0); sp(1, 5, 1, 1, 1); sp(1, 5, 2, 1, 20); sp(3, 10, 0, 0, 0);
                sp(1, 5, 1, 0, 1); sp(1, 5, 2, 0, 20); sp(3, 10, 3, 1, 0); sp(1, 5, 1, 1, 1);
                sp(1, 5, 2, 1, 20); sp(3, 10, 2, 0, 0);
                G.firingrate = 15; G.turntimer -= 10;
            }
            case 6 -> {
                sp(0, 11, 1, 1, 0); sp(0, 11, 0, 0, 0); sp(0, 11, 2, 1, 0); sp(0, 11, 0, 0, 0);
                sp(0, 11, 3, 1, 0); sp(0, 11, 0, 0, 0); sp(0, 11, 2, 1, 0); sp(0, 11, 0, 0, 0);
                sp(0, 11, 1, 1, 0); sp(0, 11, 0, 0, 0); sp(0, 11, 2, 1, 0); sp(0, 11, 0, 0, 0);
                G.firingrate = 10; G.turntimer -= 10;
            }
            case 7 -> {
                sp(1, 6, 1, 1, 1); sp(1, 6, 3, 1, 1); sp(1, 6, 1, 0, 1); sp(1, 6, 3, 0, 20);
                sp(0, 12, 1, 1, 0); sp(0, 12, 1, 0, 8); sp(0, 12, 3, 1, 0); sp(0, 12, 3, 0, 8);
                sp(0, 12, 2, 1, 0); sp(0, 12, 2, 0, 20); sp(2, 13, 2, 0, 0);
                G.firingrate = 14; G.turntimer += 40;
            }
            case 8 -> {
                sp(2, 13, 1, 0, 1); sp(2, 13, 3, 0, 30); sp(2, 13, 2, 0, 0); sp(2, 13, 1, 1, 1);
                sp(2, 13, 3, 1, 30); sp(2, 13, 2, 1, 30);
                G.turntimer += 10; G.firingrate = 20;
            }
            case 9 -> {
                sp(0, 9, 3, 1, 10); sp(3, 9, 1, 1, 15); sp(0, 9.5, 1, 1, 10); sp(3, 9.5, 3, 1, 14);
                sp(0, 10, 3, 1, 9); sp(3, 10, 1, 1, 13); sp(0, 11, 1, 1, 9); sp(3, 11, 3, 1, 12);
                sp(0, 12, 3, 1, 8); sp(3, 12, 1, 1, 11); sp(0, 13, 1, 1, 8); sp(3, 13, 3, 1, 18);
                sp(0, 13, 2, 1, 8); sp(3, 13, 2, 1, 9); sp(0, 13, 2, 1, 8); sp(3, 13, 2, 1, 9);
                G.firingrate = 14;
            }
            case 10 -> {
                sp(0, 12, 3, 1, 0); sp(0, 12, 0, 0, 0); sp(0, 12, 1, 1, 0); sp(0, 12, 0, 0, 0);
                sp(0, 12, 2, 1, 0); sp(0, 12, 0, 0, 0); sp(0, 12, 3, 1, 0); sp(0, 12, 0, 0, 0);
                sp(0, 12, 1, 1, 0); sp(0, 12, 0, 0, 0); sp(0, 12, 2, 1, 0); sp(0, 12, 0, 0, 18);
                sp(3, 12, 2, 1, 1); sp(3, 12, 2, 0, 0);
                G.firingrate = 9;
            }
            case 11 -> {
                sp(1, 8, 1, 0, 1); sp(1, 8, 2, 0, 0); sp(1, 8, 1, 1, 1); sp(1, 8, 2, 1, 0);
                sp(1, 8, 3, 0, 1); sp(1, 8, 2, 0, 0); sp(1, 8, 3, 1, 1); sp(1, 8, 2, 1, 30);
                sp(1, 8, 1, 0, 1); sp(1, 8, 3, 0, 0); sp(1, 8, 3, 1, 1); sp(1, 8, 1, 1, 0);
                G.firingrate = 20;
            }
            case 12 -> {
                sp(2, 13, 1, 0, 0); sp(2, 13, 3, 0, 0); sp(2, 13, 2, 0, 0); sp(2, 13, 1, 1, 0);
                sp(2, 13, 3, 1, 0); sp(2, 13, 2, 1, 0); sp(2, 13, 1, 0, 0); sp(2, 13, 3, 0, 0);
                sp(2, 13, 2, 0, 0);
                G.firingrate = 18; G.turntimer += 90;
            }
            case 13 -> {
                sp(3, 5, 2, 1, 0); sp(0, 8, 2, 0, 10); sp(3, 5, 2, 1, 0); sp(0, 8, 2, 0, 10);
                sp(3, 5, 2, 1, 0); sp(0, 8, 2, 0, 10); sp(3, 5, 2, 1, 0); sp(0, 8, 2, 0, 10);
                sp(3, 5, 2, 1, 0); sp(0, 8, 2, 0, 10);
                G.firingrate = 14; G.turntimer += 30;
            }
            case 14 -> {
                sp(1, 6, 1, 0, 1); sp(1, 6, 2, 0, 1); sp(1, 6, 3, 1, 1); sp(1, 6, 2, 1, 38);
                sp(3, 9, 2, 1, 1); sp(3, 9, 2, 0, 8); sp(3, 9, 2, 1, 40); sp(2, 13, 1, 0, 4);
                sp(2, 13, 3, 1, 4); sp(2, 13, 3, 0, 4); sp(2, 13, 1, 1, 25); sp(0, 8, 2, 1, 1);
                sp(0, 8, 2, 0, 15);
                G.firingrate = 14; G.turntimer += 50;
            }
            default -> { // case 15 (and any beyond the table)
                sp(3, 10, 1, 1, 0); sp(3, 10, 2, 1, 0); sp(3, 10, 3, 1, 0); sp(3, 10, 2, 1, 0);
                sp(3, 10.5, 1, 1, 0); sp(3, 10.5, 2, 1, 0); sp(3, 10.5, 3, 1, 0); sp(3, 10.5, 2, 1, 0);
                sp(3, 11, 1, 1, 0); sp(3, 11, 2, 1, 0); sp(3, 11, 3, 1, 0); sp(3, 11, 2, 1, 0);
                sp(3, 11.5, 1, 1, 0); sp(3, 11.5, 2, 1, 0); sp(3, 12, 3, 1, 0); sp(3, 12, 2, 1, 0);
                sp(3, 12, 1, 1, 0); sp(3, 12, 2, 1, 0); sp(3, 12, 3, 1, 0);
                G.firingrate = 9;
            }
        }
    }
}
