package bullet.muffet;

import battle.BulletBoard;
import battle.Soul;
import battle.WebBoard;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * The "Breakfast / Lunch / Dinner" pet special (GML: {@code obj_fakeborderdraw}
 * pattern 1, driving {@code obj_grosscupcake2}, {@code obj_purpleheart}'s
 * {@code ttype 3 → 1}, and {@code obj_hideouscupcake}). Fired on turns 4 / 9 / 15.
 *
 * <p>Three sequential beats, matching the reference clips (5/10/16):
 * <ol>
 *   <li><b>Slide</b> — the box is shoved to the left then slides back to the right,
 *       bobbing up/down, while spiders still crawl across horizontally (the turn's
 *       normal pattern keeps firing). GML {@code obj_fakeborderdraw} con 0–1.</li>
 *   <li><b>Cupcake</b> — the box widens to the right (GML border 23) and the pet
 *       ({@code obj_grosscupcake2} = {@code spr_cupcakemonster}) lunges in from the
 *       right and chomps. GML con 2–3.</li>
 *   <li><b>Climb</b> — the box grows tall and rocks left/right (the "pulled-paper"
 *       tilt) while the web rises (GML border 22 + {@code WebBoard.type 3 → 1}); the
 *       horizontal spiders stop, the {@link Cupcake} maw sits at the bottom, and only
 *       {@link VertSpider}s rappel down. GML con 4–5.</li>
 * </ol>
 * When the climb is done the box snaps back to its normal size <em>and the enemy turn
 * ends on the same frame</em> (the two used to drift apart).
 *
 * // GML: obj_fakeborderdraw (pattern 1) + obj_grosscupcake2
 */
public final class PetSpecial extends AttackPattern {

    private static final int SLIDE_LEN = 50;       // beat 1: slide in from the left
    private static final int BITE_LEN = 70;        // beat 2: the pet lunges in and chomps
    private static final double SLIDE_DX = 70;     // how far left the box starts
    private static final double BOX_L = 197;
    private static final double BOX_R = 437;
    private static final double BOX_R_WIDE = 537;  // GML border 23 (the bite widens right)
    private static final double BOX_T = 250;
    private static final double BOX_B = 385;

    private final Soul soul;
    private final BulletBoard board;
    private final WebBoard web;
    /** The turn's horizontal-spider generator — stopped when the climb begins. */
    private final SpiderBulletGen gen;
    /** GML yadd2 — the steady scroll speed once the web finishes rising (3/4/5). */
    private final int yadd2;
    /** GML turnamt 10/16 → the rappelling spiders come down faster. */
    private final boolean fast;

    private int phase;   // 0 slide · 1 bite · 2 climb · 3 done
    private int timer;
    private int biteTimer;
    private int climbTimer;
    private int spawnTimer;
    private double cmX = 600;     // the lunging pet's x (enters from off the right)
    private double prevDx;
    private double prevDy;
    private double baseXmid;
    private double baseYzero;
    private Cupcake cupcake;

    public PetSpecial(EntityManager manager, Soul soul, BulletBoard board,
                      SpiderBulletGen gen, int yadd2, boolean fast) {
        super(manager);
        this.soul = soul;
        this.board = board;
        this.web = soul.web;
        this.gen = gen;
        this.yadd2 = yadd2;
        this.fast = fast;
        this.depth = 60;
        this.baseXmid = web.xmid;
        this.baseYzero = web.yzero;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            restore();
            manager.destroy(this);
            return;
        }
        timer++;
        switch (phase) {
            case 0 -> slideBeat();
            case 1 -> biteBeat();
            case 2 -> climbBeat();
            default -> { }
        }
    }

    /** Beat 1: the box snaps left and slides back to centre, bobbing, spiders crawling. */
    private void slideBeat() {
        double p = Math.min(1.0, timer / (double) SLIDE_LEN);
        double dx = -SLIDE_DX * (1 - p);              // starts left, eases to 0
        double dy = Math.sin(timer / 5.0) * 4;        // gentle up/down bob
        applyShift(dx, dy);
        if (timer >= SLIDE_LEN) {
            applyShift(0, 0);
            phase = 1;
            board.instaBorder(BOX_L, BOX_R_WIDE, BOX_T, BOX_B); // GML border 23
        }
    }

    /** Beat 2: the pet lunges in from the right and chomps; spiders still crawl. */
    private void biteBeat() {
        if (cmX > 415) {
            cmX -= 12;
        }
        if (++biteTimer >= BITE_LEN) {
            startClimb();
        }
    }

    /** Beat 3: tall, rocking box; horizontal spiders stop; only vertspiders rappel. */
    private void climbBeat() {
        climbTimer++;
        // GML border 22: the box top tracks the rising web (offpurple − 10).
        double top = Math.min(web.yzero, 250) - 10;
        board.instaBorder(BOX_L, BOX_R, top, BOX_B);
        // The "pulled-paper" left/right rock (GML rotfactor wobble).
        double rock = Math.sin(climbTimer / 16.0) * 3.0;
        board.tilt = rock;
        web.tilt = rock;
        if (--spawnTimer <= 0) {
            VertSpider v = new VertSpider(manager, soul);
            v.fast = fast;
            v.dmg = G.monsteratk[G.myself];
            manager.add(v);
            spawnTimer = fast ? 9 : 13;
        }
        // End the climb near the turn's natural end, then snap the box back to normal
        // AND end the turn on the same frame.
        if (G.turntimer <= 6) {
            restore();
            G.turntimer = 0;
            phase = 3;
        }
    }

    /** Move the box + web (and the heart riding it) together, for the slide/bob. */
    private void applyShift(double dx, double dy) {
        board.instaBorder(BOX_L + dx, BOX_R + dx, BOX_T + dy, BOX_B + dy);
        web.xmid = baseXmid + dx;
        web.yzero = baseYzero + dy;
        soul.x += dx - prevDx;
        soul.y += dy - prevDy;
        prevDx = dx;
        prevDy = dy;
    }

    private void startClimb() {
        phase = 2;
        // Stop the horizontal spiders: kill the generator and clear what's in flight,
        // so the climb is vertical-only (GML's continuous gen spawns vertspiders).
        if (gen != null) {
            manager.destroy(gen);
        }
        manager.with(SpiderBullet.class, manager::destroy);
        web.xmid = baseXmid;
        web.yzero = baseYzero;
        web.type = 3;        // begin the cupcake-rise transition (Soul drives it)
        web.yadd2 = yadd2;   // the steady scroll speed it settles into
        cupcake = new Cupcake(manager);
        cupcake.x = web.xmid - web.xlen;
        cupcake.y = 460;
        manager.add(cupcake);
        spawnTimer = 1;
    }

    /** Snap everything back to the normal combat box (and stop the rock). */
    private void restore() {
        board.tilt = 0;
        if (web != null) {
            web.tilt = 0;
            web.type = 0;
            web.xmid = baseXmid;
            web.yzero = baseYzero;
            web.yoff = 0;
            web.yadd = 0;
            web.yamt = 3;
        }
        if (cupcake != null) {
            manager.destroy(cupcake);
        }
        board.instaBorder(BOX_L, BOX_R, BOX_T, BOX_B);
    }

    @Override
    public void render(Graphics2D g) {
        if (phase != 1) {
            return; // the pet-lunge sprite only shows during the bite beat
        }
        // GML obj_grosscupcake2 = spr_cupcakemonster: frame 0, then the chomp (frame 1),
        // jittering as it bites in from the right edge of the widened box.
        int frame = biteTimer >= 30 ? 1 : 0;
        BufferedImage img = Assets.sprite("spr_cupcakemonster_" + frame);
        if (img == null) {
            return;
        }
        int jx = (int) (Math.random() * 2 - 1);
        int jy = (int) (Math.random() * 2 - 1);
        g.drawImage(img, (int) cmX + jx, (int) (BOX_B - img.getHeight()) + jy, null);
    }
}
