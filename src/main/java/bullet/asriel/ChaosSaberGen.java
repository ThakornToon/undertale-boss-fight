package bullet.asriel;

import battle.BulletBoard;
import battle.Soul;
import boss.AsrielBody;
import bullet.AttackPattern;
import core.EntityManager;
import util.GMLHelper;

/**
 * CHAOS SABER / CHAOS SLICER (GML: {@code obj_asriel_swordmaster}). Spawns the two
 * {@link SwordArm}s and runs the slash schedule: {@code maxtime} alternating slashes
 * (5 normal · 6 hard), never the same side three times running, on a 27-frame cadence
 * (21 for the hard "Chaos Slicer"). Each slash flashes a crescent over HALF the box on
 * the slashing side — the heart dodges to the other half.
 *
 * <p>The <b>finale</b> is the closing double slash: both arms strike at once, each crescent
 * covering only a <b>third</b> of the (still small) box so the middle third is a safe gap.
 * Then the box opens out to {@code border 6} and a scatter of {@link SwordTwinkle} stars
 * drifts across it — the only bullets Chaos Saber throws.
 *
 * // GML: obj_asriel_swordmaster
 */
public final class ChaosSaberGen extends AttackPattern {

    private final boolean hard;
    private final int cadence;
    private final int maxtime;

    private SwordArm left;
    private SwordArm right;

    private int timer;
    private int nextSlash = 14;     // initial delay before the first slash
    private int times;
    private int lastSide;           // GML choose-but-never-3×-the-same-side tracking
    private int lastLastSide;
    private boolean finale;
    private int finaleTimer;
    private boolean boxOpened;

    private final BulletBoard board;
    private final AsrielBody body;
    private final Soul soul;
    private final int dmg;

    // GML SCR_BORDERSETUP border 6: the box the finale opens out to.
    private static final double[] BORDER_6 = { 227, 407, 250, 385 };
    // After the double-slash crescents flash, open the box and scatter the stars.
    private static final int OPEN_DELAY = 20;
    private static final int STARS_PER_BLADE = 4;

    public ChaosSaberGen(EntityManager manager, Soul soul, BulletBoard board, AsrielBody body,
                         boolean hard, int dmg) {
        super(manager);
        this.board = board;
        this.body = body;
        this.soul = soul;
        this.dmg = dmg;
        this.hard = hard;
        this.cadence = hard ? 24 : 27;     // GML obj_asriel_swordmaster alarm[5]
        this.maxtime = hard ? 6 : 5;
        this.depth = 50;
        util.Audio.play("/audio/mus_sfx_a_swordappear.ogg");
        left = new SwordArm(manager, soul, body, -1, dmg);
        right = new SwordArm(manager, soul, body, 1, dmg);
        manager.add(left);
        manager.add(right);
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            if (body != null) {
                body.setLean(0, 0);
            }
            manager.destroy(this);
            return;
        }
        // GML: the body leans opposite whichever arm is mid-slash (only one at a time). The
        // finale slashes both arms at once, so it stays centred (symmetric) instead.
        if (body != null) {
            double lean = finale ? 0 : left.busy() ? left.lean() : right.busy() ? right.lean() : 0;
            body.setLean(lean, lean * 0.5);   // bigger torso tilt to match the deeper lean
        }
        timer++;
        if (!finale) {
            if (timer >= nextSlash) {
                if (times < maxtime) {
                    triggerSlash();
                    times++;
                    nextSlash = timer + cadence;
                } else {
                    // GML: the closing double slash — both arms strike the SMALL box at once,
                    // each crescent covering a third (middle third stays safe).
                    finale = true;
                    finaleTimer = 0;
                    boxOpened = false;
                    left.slash(true);
                    right.slash(true);
                }
            }
        } else {
            finaleTimer++;
            if (!boxOpened && finaleTimer >= OPEN_DELAY) {
                // After the double slash, the box opens out and the stars scatter into it.
                boxOpened = true;
                if (board != null) {
                    board.slide(BORDER_6[0], BORDER_6[1], BORDER_6[2], BORDER_6[3]);
                }
                releaseStars();
            }
            if (boxOpened && finaleTimer > OPEN_DELAY + 45 && !left.busy() && !right.busy()) {
                // The stars are drifting; end the turn.
                G.turntimer = 0;
                manager.destroy(this);
            }
        }
    }

    /**
     * GML obj_asriel_swordmaster alarm[5]: pick a side with {@code choose(0,1)}, but never the
     * same side three times in a row, and slash only that one arm. The giant crescent blankets
     * that half of the box; the heart dodges to the other half.
     */
    private void triggerSlash() {
        int which = GMLHelper.irandom(1);   // 0 = left, 1 = right
        if (which == lastSide && which == lastLastSide) {
            which = 1 - which;
        }
        lastLastSide = lastSide;
        lastSide = which;
        if (which == 0) {
            left.slash(false);
        } else {
            right.slash(false);
        }
    }

    /**
     * GML obj_swordtwinkle finale: each blade releases a column of <b>4</b> diamond stars into
     * the opened-out box (4 per side, 8 total), at the blade's x — they then drift apart.
     */
    private void releaseStars() {
        double bt = BORDER_6[2] + 14;
        for (int s = -1; s <= 1; s += 2) {
            double bx = body.x + s * 36;        // the blade's x (body is centred in the finale)
            for (int i = 0; i < STARS_PER_BLADE; i++) {
                double sx = bx + GMLHelper.random(8) - 4;
                manager.add(new SwordTwinkle(manager, soul, sx, bt + i * 30, dmg));
            }
        }
    }

    @Override
    public void render(java.awt.Graphics2D g) {
        // Invisible driver; the arms draw themselves.
    }
}
