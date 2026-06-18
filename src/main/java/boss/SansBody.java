package boss;

import battle.BulletBoard;
import battle.KarmaTicker;
import battle.Soul;
import battle.SoulMode;
import bullet.bones.BoneLoopV;
import bullet.bones.BonePlatform;
import bullet.bones.BoneStab;
import bullet.bones.BoneWall;
import bullet.bones.SansBone;
import bullet.gaster.GasterBlaster;
import bullet.gaster.GasterBlasterGen;
import core.EntityManager;
import core.GlobalState;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;
import util.GMLHelper;

/**
 * Sans's body (GML: {@code obj_sansb_body}). Draws the multi-part sprite
 * (legs / torso / arm pose / face / sweat / blue eye) and runs every live attack:
 * the {@code a_type} patterns the controller selects, the smasher slams, the
 * {@code lac} special-attack chain (warmup → blaster ring → the slam loop → the
 * winded monologue → sleep), the dodge animation, and the fake FIGHT button of
 * the sleep ending.
 *
 * <p>The GML's {@code alarm[5]} "lac++" stepping is ported as
 * {@link #waitThen(int, int)}: a frame countdown that jumps {@code lac} to an
 * explicit next step, which also lets the out-of-scope sentry-shadow steps be
 * bridged over cleanly.
 *
 * // GML: obj_sansb_body (Create/Draw/user events 0,10-13/alarms 5-8)
 */
public final class SansBody extends BossBody {

    private static final GlobalState G = GlobalState.get();

    // GML smashdir / BoneStab direction convention: 0 bottom, 1 right, 2 top, 3 left.
    // Slam dd convention (lac 53): 1 right, 2 up, 3 down, 4 left.

    private final SansBoss boss;
    private final Soul soul;
    private final BulletBoard board;
    private final KarmaTicker karma;

    // ---- Render / pose state (GML draw event) -------------------------------
    public int bounce = 1;
    private int siner;
    private double yoff;
    private double xoff;
    private int movearm;
    private double armI;
    private double aspeed = 1;
    private double headx;
    private double heady;
    private int facetype;
    private int fI;
    public int sweat;

    // ---- Dodge (GML dodge 0/1/2) --------------------------------------------
    private int dodgePhase;
    private int dodgeT;
    private double dodgeX;
    private double dodgeH;

    // ---- Smasher (GML smasher/smashcon/smashlv) ------------------------------
    private boolean smasher;
    private int smashcon = -1;
    private int smashdir;
    private int prevsmash = -1;
    private int smashlv = 2;
    private int smashamt;
    private static final int SMASH_MAX = 8;
    private int smashAlarm = -1;
    private int xtimer;
    /** Scripted slam directions (the screenshot reference); null = GML random. */
    private int[] smashSeq;

    // ---- 3-platform generator (GML obj_3platgen, inlined) --------------------
    private boolean platgen;
    private int platgenType;
    private final int[] pgAlarm = new int[4];
    private int pgG = 4;
    private int pgGg = 4;
    private int pgGg2 = 4;
    private int pgSd;
    private boolean pgSkl;

    // ---- Opening barrage (GML fac) --------------------------------------------
    private int fac;
    private int facTimer = -1;
    private int facNext;

    // ---- Chained sub-attacks (screenshot reference 14.x / 16.x / 20.x / 24.x) --
    // Sub-attack ids; chains list them in screenshot order, _f = flash between.
    private static final int SUB_SQUEEZE = 0;
    private static final int SUB_GATES = 1;
    private static final int SUB_TRAIN = 2;
    private static final int SUB_BLUEPAIR = 3;
    private static final int SUB_WAVE_L = 4;
    private static final int SUB_WAVE_R = 5;
    private static final int SUB_PLATFORMS = 6;
    private static final int SUB_PLATFORMS_M = 7;
    private static final int SUB_SLIDES = 8;
    private static final int SUB_XBLAST = 9;
    private static final int SUB_SMASH = 10;
    /** The [Attack #] group running (14/16/20/24); 0 = none. */
    private int chain;
    private int chainStep = -1;
    private int chainTimer;

    // ---- Special attack (GML lac) --------------------------------------------
    /** 0 = inactive; the GML lac step otherwise. */
    public int lac;
    private int lacTimer = -1;
    private int lacNext;
    private int intensity = 15;
    // Blaster ring.
    private double gt;
    private double gin;
    private boolean everyOther;
    // Slam loop.
    private int lcT;
    private int lcC;
    private int lcA;
    private int dd;
    private int pdd = -1;
    // Corridor.
    private boolean repeater;
    private double rpX;

    // ---- Sleep ending ---------------------------------------------------------
    private static final double[] SLEEP_BOX = { 240, 400, 225, 385 };
    private static final int[] BUTTON = { 30, 426, 110, 42 };
    private static final double BOX_SIZE = 160;
    /** GML obj_emptyborder_s: how far the box can be shoved (maxx / maxy). */
    private static final double PUSH_MIN_X = 20;
    private static final double PUSH_MAX_Y = 310;
    /** 0 none · 1 drowsy (timer up to 1200) · 2 asleep (push window). */
    private int sleepPhase;
    private int sleepT;
    private int sleepCountdown;
    /** Top-left of the empty border box once it becomes pushable. */
    private double sleepEx = SLEEP_BOX[0];
    private double sleepEy = SLEEP_BOX[2];
    private boolean showButton;
    private int zClock;
    private boolean buttonLit;
    /** Pin Sans's y while the soul shoves the sleep box around (so he stays put). */
    private boolean sleepPinned;
    /** The final hit landed: show the wounded (bloodied/slashed) art for the ending. */
    private boolean injured;

    /** GML choose(...) over ints (GMLHelper's overloads are ambiguous for literals). */
    static int chooseInt(int... options) {
        return options[(int) Math.floor(Math.random() * options.length)];
    }

    public SansBody(EntityManager manager, SansBoss boss, Soul soul,
                    BulletBoard board, KarmaTicker karma) {
        super(manager);
        this.boss = boss;
        this.soul = soul;
        this.board = board;
        this.karma = karma;
        this.x = 320;
        this.depth = 10;
    }

    // ---- Controller entry points ---------------------------------------------

    /** GML: controller sets a_type and fires event_user(0). */
    @Override
    public void setAttack(int selector) {
        super.setAttack(selector);
        movearm = 0;
        spawnAttack(selector);
    }

    /** GML: with(517) { smasher=1; smashlv=lv; smashcon=0; }. */
    public void startSmasher(int lv) {
        startSmasher(lv, null);
    }

    /** Smasher with a scripted direction list (0 down, 1 right, 2 up, 3 left). */
    public void startSmasher(int lv, int[] seq) {
        smasher = true;
        smashlv = lv;
        smashcon = 0;
        smashamt = 0;
        prevsmash = -1;
        smashSeq = seq;
    }

    /** hit_try 27 → the final attack: the 26.x chain, then the speech, then lac 50. */
    public void startFinal() {
        lac = 4;
    }

    /** The final hit landed (mercy_death): switch Sans to his wounded art. */
    public void showInjured() {
        injured = true;
    }

    /** GML: con 11 → with(517) fac = 1 — the famous opening barrage. */
    public void startOpening() {
        fac = 1;
    }

    public boolean specialRunning() {
        return lac != 0 || sleepPhase != 0 || chain != 0;
    }

    /** GML obj_sansb_body Begin Step: harsher i-frames once the special is armed. */
    @Override
    public void beginStep() {
        if (lac >= 4 && G.hp <= 10 && G.inv > 15) {
            G.inv = 15;
        }
    }

    @Override
    public void update() {
        if (boss.battleOver) {
            return;
        }
        stepBounce();
        stepDodge();
        stepPlatgen();
        stepSmasher();
        stepChain();
        stepFac();
        stepLac();
        stepRepeater();
        stepSleep();
    }

    // ---- a_type patterns (GML event_user 0) -----------------------------------

    private SansBone sbo(double height, double hspeed, double xfactor, int type) {
        return SansBone.sbo(manager, soul, karma, height, hspeed, xfactor, type);
    }

    /** GML: scr_bwall — a train of short bone columns sharing one height. */
    private void bwall(double height, double hspeed, double xfactor, int count) {
        for (int i = 0; i < count; i++) {
            SansBone b = sbo(height, hspeed, xfactor, SansBone.BOTTOM);
            if (b.x < 320) {
                b.x -= i * 15;
            } else {
                b.x += i * 15;
            }
        }
    }

    private BonePlatform hplat(double height, double hspeed, double xfactor, double len) {
        return BonePlatform.hplat(manager, soul, height, hspeed, xfactor, len);
    }

    private BoneWall wall(double x, double y, double hs, double vs, boolean wide) {
        BoneWall w = new BoneWall(manager, soul, karma);
        if (wide) {
            w.makeWide();
        }
        w.x = x;
        w.y = y;
        w.hspeed = hs;
        w.vspeed = vs;
        manager.add(w);
        return w;
    }

    private void soulStopRed() {
        soul.setMode(SoulMode.RED);
        soul.jumpStage = Soul.AIRBORNE;
    }

    private void centerSoul() {
        soul.x = G.idealborder[0] + (G.idealborder[1] - G.idealborder[0]) / 2;
        soul.y = G.idealborder[2] + (G.idealborder[3] - G.idealborder[2]) / 2;
    }

    private void spawnAttack(int aType) {
        double[] b = G.idealborder;
        switch (aType) {
            case 0 -> {
                G.turntimer = 200;
                for (int i = 0; i < 8; i++) {
                    sbo(20, 6, 40 + i * 20, 0);
                    sbo(20, -6, 40 + i * 20, 0);
                    sbo(40, 6, 40 + i * 20, 2);
                    sbo(40, -6, 40 + i * 20, 2);
                }
            }
            case 1 -> {
                G.turntimer = 190;
                for (int i = 0; i < 8; i++) {
                    sbo(20, 7, 40 + i * 19, 0);
                    sbo(20, -7, 40 + i * 19, 0);
                    sbo(40, 7, 40 + i * 19, 2);
                    sbo(40, -7, 40 + i * 19, 2);
                }
            }
            case 2 -> {
                G.turntimer = 240;
                double value = 0;
                for (int i = 0; i < 5; i++) {
                    double ht = chooseInt(20, 30, 40, 60);
                    double xx = i > 0 ? chooseInt(-2, 0, 2) : 0;
                    if (ht == 60) {
                        xx = 0;
                    }
                    if (ht == 40) {
                        xx *= 0.5;
                    }
                    sbo(ht, 7 + xx, 40 + i * 25 + value, 0);
                    sbo(ht, -7 + xx, 40 + i * 25 + value, 0);
                    sbo(ht + 24, 7 + xx, 40 + i * 25 + value, 2);
                    sbo(ht + 24, -7 + xx, 40 + i * 25 + value, 2);
                    if (ht == 30) {
                        value += 5;
                    }
                    if (ht == 40) {
                        value += 10;
                    }
                    if (ht == 60) {
                        value += 20;
                    }
                }
            }
            case 3 -> {
                G.turntimer = 190;
                sbo(100, -10, 25, 1);
                sbo(20, -10, 32, 0);
                sbo(100, -10, 47, 1);
                sbo(20, -10, 54, 0);
                sbo(100, -10, 69, 1);
                sbo(20, -10, 76, 0);
                sbo(20, 10, 105, 0);
                sbo(100, 10, 117, 1);
                sbo(20, 10, 127, 0);
                sbo(100, 10, 139, 1);
                sbo(20, 10, 149, 0);
                sbo(100, 10, 161, 1);
            }
            case 5 -> {
                G.turntimer = 230;
                for (int i = 0; i < 8; i++) {
                    sbo(20, 4, 65 + i * 19, 0);
                    sbo(28, -4, 65 + i * 19, 2);
                }
            }
            case 6 -> {
                G.turntimer = 250;
                bwall(30, 4, 60, 41);
                hplat(40, 4, 70, 30);
                hplat(40, 5, 120, 30);
                hplat(40, 6, 160, 30);
                sbo(90, 7, 160, 2);
                sbo(90, 7, 162, 2);
                sbo(90, 7, 164, 2);
                sbo(40, 9, 222, 2);
            }
            case 7 -> {
                G.turntimer = 290;
                bwall(30, -4, 60, 58);
                hplat(40, -5, 70, 25);
                sbo(70, -5, 90, 0);
                hplat(90, -5, 95, 25);
                hplat(40, -5, 110, 25);
                hplat(60, -5, 150, 25);
                sbo(90, -5, 148, 2);
                hplat(50, -5, 170, 25);
                sbo(80, -5, 168, 2);
                hplat(70, -5, 190, 25);
                sbo(100, -5, 188, 2);
                hplat(90, -2, 230, 15);
                sbo(110, -8, 240, 0);
                sbo(40, 3, 260, 2);
            }
            case 8 -> {
                G.turntimer = 240;
                startPlatgen(1);
            }
            case 10 -> {
                // The wall corridors (19): horizontal slabs sweeping vertically at
                // a uniform speed, evenly spaced down each column — left column
                // falls, right column rises (reference image: even gaps throughout).
                soulStopRed();
                board.instaBorder(220, 420, b[3] - 200, b[3]);
                b = G.idealborder;
                G.turntimer = 220; // GML relies on the red-phase default; corridors need real time here
                double vs = 8;     // was 7, ×1.2
                for (int i = 0; i < 4; i++) {
                    wall(b[0] - 90, -110 - i * 225, 0, vs, true);   // falling
                    wall(b[0] + 90, 760 + i * 225, 0, -vs, true);   // rising
                }
            }
            case 11 -> {
                soulStopRed();
                board.instaBorder(270, 470, b[3] - 200, b[3]);
                b = G.idealborder;
                G.turntimer = 200;
                wall(b[0] + 100, -250, 0, 7, true);
                wall(b[0] - 100, 630, 0, -6, true);
                wall(b[0] + 100, -500, 0, 7, true);
                wall(b[0] - 100, 880, 0, -6, true);
            }
            case 12 -> {
                G.turntimer = 240;
                soulStopRed();
                board.instaBorder(120, 520, b[3] - 200, b[3]);
                manager.add(new GasterBlasterGen(manager, soul, karma, 1));
            }
            case 13 -> {
                G.turntimer = 240;
                soulStopRed();
                board.instaBorder(120, 520, b[3] - 200, b[3]);
                manager.add(new GasterBlasterGen(manager, soul, karma, 2));
            }
            case 15 -> {
                G.turntimer = 250;
                startPlatgen(2);
            }
            case 16 -> {
                G.turntimer = 240;
                startPlatgen(3);
            }
            case 17 -> {
                G.turntimer = 220;
                bwall(20, 2, 3, 50);
                bwall(20, 2, -5, 20);
                soul.y = b[3] - 70;
                soul.vspeed = 1;
                BonePlatform p = hplat(50, 0, 0, 20);
                p.jud = true;
                p.x -= 150;
                soul.x -= 150;
                loop(b[1] - 260, b[2] + 40, -4);
                loop(b[1] - 260, b[2] + 125, -4);
                loop(b[1] - 180, b[2], 5);
                loop(b[1] - 180, b[2] + 95, 5);
                loop(b[1] - 100, b[2] + 20, -3);
                loop(b[1] - 100, b[2] + 105, -3);
            }
            case 18 -> {
                G.turntimer = 220;
                bwall(20, 2, 3, 50);
                bwall(20, 2, -5, 20);
                soul.y = b[3] - 70;
                soul.vspeed = 1;
                BonePlatform p = hplat(50, 0, 0, 15);
                p.jud = true;
                p.x -= 150;
                soul.x -= 150;
                loop(b[1] - 260, b[2] + 40, -3);
                loop(b[1] - 260, b[2] + 105, -3);
                loop(b[1] - 260, b[2] + 170, -3);
                loop(b[1] - 180, b[2], 4);
                loop(b[1] - 180, b[2] + 90, 4);
                loop(b[1] - 100, b[2] + 40, -3);
                loop(b[1] - 100, b[2] + 105, -3);
                loop(b[1] - 100, b[2] + 170, -3);
            }
            case 20 -> {
                // The tiny "spare platter" box (GML mercy_death turn).
                soulStopRed();
                board.instaBorder(270, 370, b[3] - 100, b[3]);
                soul.x = G.idealborder[0] + 42;
                soul.y = G.idealborder[2] + 42;
            }
            case 21 -> {
                G.turntimer = 210;
                randomWalls(7, 9, 16, 22, 12, 13, 16, 22, 8);
            }
            case 22 -> {
                G.turntimer = 180;
                soulStopRed();
                board.instaBorder(240, 400, b[3] - 160, b[3]);
                b = G.idealborder;
                soul.x = b[0] + 76;
                soul.y = b[2] + 76;
                for (int i = 0; i < 7; i++) {
                    wall(b[0] - 110, b[2] - 300 - i * (216 - i * 3), 0, 10, true);
                    wall(b[1] - 70, b[3] + 300 + i * (216 - i * 3), 0, -10, true);
                }
            }
            case 23 -> {
                G.turntimer = 210;
                randomWalls(9, 11, 19, 25, 15, 17, 19, 25, 8);
            }
            default -> G.turntimer = 60; // a_type 9 (vplatgen): TODO — unreachable in the GML controller
        }
    }

    private void loop(double lx, double ly, double vs) {
        BoneLoopV l = new BoneLoopV(manager, soul, karma);
        l.x = lx;
        l.y = ly;
        l.vspeed = vs;
        manager.add(l);
    }

    /** GML a_type 21/23: random-height paired walls until 150px of gaps are used. */
    private void randomWalls(int pre20, int pre30, int pre40, int pre60,
                             int post20, int post30, int post40, int post60, double speed) {
        double vtotal = 0;
        boolean first = true;
        while (vtotal < 150) {
            double ht = chooseInt(20, 30, 40, 60);
            double xx = first ? 0 : chooseInt(-2, 0, 2);
            boolean down = false;
            if (ht == 60) {
                xx = 0;
                down = true;
            }
            if (ht == 40) {
                xx = 0;
            }
            if (!first) {
                if (ht == 20) {
                    vtotal += pre20;
                }
                if (ht == 30) {
                    vtotal += pre30;
                }
                if (ht == 40) {
                    vtotal += pre40;
                }
                if (ht == 60) {
                    vtotal += pre60;
                }
            }
            sbo(ht, speed + (down ? -1 : xx), 32 + vtotal, 0);
            sbo(ht, -speed + (down ? 1 : xx), 32 + vtotal, 0);
            sbo(ht + 24, speed + (down ? -1 : xx), 32 + vtotal, 2);
            sbo(ht + 24, -speed + (down ? 1 : xx), 32 + vtotal, 2);
            if (ht == 20) {
                vtotal += post20;
            }
            if (ht == 30) {
                vtotal += post30;
            }
            if (ht == 40) {
                vtotal += post40;
            }
            if (ht == 60) {
                vtotal += post60;
            }
            first = false;
        }
    }

    // ---- 3-platform generator (GML obj_3platgen) -------------------------------

    private void startPlatgen(int type) {
        platgen = true;
        platgenType = type;
        pgAlarm[0] = 1;
        pgAlarm[1] = 1;
        pgAlarm[2] = 1;
        pgAlarm[3] = -1;
        pgG = 4;
        pgGg = 4;
        pgGg2 = 4;
        pgSd = 0;
        pgSkl = false;
    }

    private void stepPlatgen() {
        if (!platgen) {
            return;
        }
        if (G.turntimer <= 0) {
            platgen = false;
            return;
        }
        for (int n = 0; n < 4; n++) {
            if (pgAlarm[n] > 0 && --pgAlarm[n] == 0) {
                platgenAlarm(n);
            }
        }
    }

    private void platgenAlarm(int n) {
        switch (n) {
            case 0 -> { // low platform
                if (platgenType == 1) {
                    pgAlarm[0] = 55;
                    hplat(40, -4, 65, 60);
                } else {
                    pgAlarm[0] = 35;
                    hplat(40, -4, 65, 25);
                    pgAlarm[2] = -1;
                    if (!pgSkl) {
                        pgSkl = true;
                        pgAlarm[3] = 1;
                    }
                }
            }
            case 1 -> { // high platform
                if (platgenType == 1) {
                    pgAlarm[1] = 70;
                    hplat(80, 4, 80, 80);
                } else {
                    pgAlarm[1] = 40;
                    hplat(80, 4, 80, 25);
                }
            }
            case 2 -> { // bone lane (type 1 only)
                pgAlarm[2] = 15;
                int g = pickLane();
                if (g == 0) {
                    sbo(35, -4, 50, 0);
                } else if (g == 1) {
                    sbo(90, -4, 50, 2);
                } else {
                    bwall(80, 4, 50, 1);
                }
            }
            case 3 -> { // side blasters (type 2/3)
                int g = pickLane();
                GasterBlaster gb = new GasterBlaster(manager, soul, karma);
                boolean fromLeft = pgSd == 0;
                gb.x = fromLeft ? 0 : 640;
                gb.y = 240;
                gb.idealx = fromLeft ? G.idealborder[0] - 60 : G.idealborder[1] + 60;
                gb.idealy = switch (g) {
                    case 0 -> G.idealborder[3] - 20;
                    case 1 -> G.idealborder[2] + 35;
                    default -> G.idealborder[2] + 75;
                };
                gb.idealrot = fromLeft ? 90 : -90;
                gb.imageAngle = gb.idealrot;
                gb.pause = 17;
                gb.terminal = 3;
                gb.yscale = 2;
                manager.add(gb);
                pgSd = 1 - pgSd;
                pgAlarm[3] = platgenType == 3 ? 21 : 26;
            }
            default -> { }
        }
    }

    /** GML: avoid three repeats and lanes the soul can't possibly be threatened in. */
    private int pickLane() {
        int zone = 0;
        if (soul.y >= G.idealborder[2] + 40) {
            zone = 1;
        }
        if (soul.y >= G.idealborder[2] + 80) {
            zone = 2;
        }
        pgGg2 = pgGg;
        pgGg = pgG;
        pgG = chooseInt(0, 1, 2);
        boolean reroll = (pgGg == pgG && pgGg2 == pgGg)
                || (pgG == 0 && zone == 0) || (pgG == 1 && zone == 2);
        if (reroll) {
            pgG = chooseInt(0, 1, 2);
        }
        return pgG;
    }

    // ---- Chained sub-attacks (screenshot reference) -------------------------------

    /** Begin an [Attack #id] chain; flashFirst = the speech→attack scene cut. */
    public void startChain(int id, boolean flashFirst) {
        chain = id;
        chainStep = 0;
        if (flashFirst) {
            flashCut();
        }
        enterSub(chainSubs()[0]);
    }

    /** The sub-attacks of each chained turn, in screenshot order. */
    private int[] chainSubs() {
        return switch (chain) {
            case 14 -> new int[] { SUB_SQUEEZE, SUB_GATES, SUB_TRAIN, SUB_SQUEEZE, SUB_BLUEPAIR };
            case 16 -> new int[] { SUB_WAVE_L, SUB_PLATFORMS, SUB_SLIDES, SUB_XBLAST, SUB_WAVE_L, SUB_SMASH };
            case 20 -> new int[] { SUB_WAVE_R, SUB_GATES, SUB_GATES, SUB_PLATFORMS, SUB_GATES, SUB_TRAIN };
            default -> new int[] { SUB_WAVE_L, SUB_SQUEEZE, SUB_PLATFORMS_M, SUB_SLIDES, SUB_BLUEPAIR, SUB_PLATFORMS };
        };
    }

    private void stepChain() {
        if (chain == 0) {
            return;
        }
        if (chainSubs()[chainStep] == SUB_SMASH) {
            if (!smasher) {
                advanceChain();
            }
            return;
        }
        if (chainTimer > 0 && --chainTimer == 0) {
            advanceChain();
        }
    }

    private void advanceChain() {
        int[] subs = chainSubs();
        boolean smashEnd = subs[chainStep] == SUB_SMASH;
        chainStep++;
        if (chainStep >= subs.length) {
            if (!smashEnd) {
                flashCut();   // the closing _f cut (14.5 / 20.6 / 24.6)
            }
            chain = 0;
            chainStep = -1;
            G.turntimer = 0;  // back to the player menu
            return;
        }
        flashCut();
        enterSub(subs[chainStep]);
    }

    /** The _f scene cut: white blink + every live bullet swept away. */
    private void flashCut() {
        manager.add(new ScreenFlash(manager));
        manager.with(bullet.AttackPattern.class, manager::destroy);
    }

    private void enterSub(int sub) {
        switch (sub) {
            case SUB_SQUEEZE -> subSqueeze();
            case SUB_GATES -> subGates();
            case SUB_TRAIN -> subTrain();
            case SUB_BLUEPAIR -> subBluePair();
            case SUB_WAVE_L -> subWave(false);
            case SUB_WAVE_R -> subWave(true);
            case SUB_PLATFORMS -> subPlatforms(false);
            case SUB_PLATFORMS_M -> subPlatforms(true);
            case SUB_SLIDES -> subSlides();
            case SUB_XBLAST -> subXBlasters();
            default -> {        // SUB_SMASH: the 16.6 scripted slam set
                board.instaBorder(240, 400, 225, 385);
                centerSoul();
                startSmasher(1, new int[] { 3, 3, 0, 1, 1, 3, 0, 2, 0 });
                chainTimer = -1;
            }
        }
    }

    /** The wide flat box every blue-soul sub uses (SCR_BORDERSETUP preset 35). */
    private void wideBorder() {
        board.instaBorder(132, 502, 250, 385);
    }

    private void blueFloor() {
        wideBorder();
        soul.setMode(SoulMode.BLUE);
        soul.hspeed = 0;
        soul.vspeed = 0;
        soul.x = (G.idealborder[0] + G.idealborder[1]) / 2;
        soul.y = G.idealborder[3] - Soul.HALF;
        soul.jumpStage = Soul.GROUNDED;
    }

    /** A bone at an absolute x (sbo positions by xfactor, which 0 disables). */
    private SansBone boneAtX(double bx, double hs, double height, int type) {
        SansBone bn = sbo(height, hs, 0, type);
        bn.x = bx;
        return bn;
    }

    /**
     * 14.1 / 14.4 / 24.2 — squeezed from both sides: per side one near-full
     * column (outermost) leading four mediums {S MMMM → ← MMMM S}; the player
     * hops the mediums and escapes through the gap above, between the columns.
     */
    private void subSqueeze() {
        blueFloor();
        double[] b = G.idealborder;
        double sp = 10;   // near wave-bone speed
        boneAtX(b[0] + 5, sp, 120, SansBone.BOTTOM);
        boneAtX(b[1] - 15, -sp, 120, SansBone.BOTTOM);
        for (int i = 0; i < 4; i++) {
            boneAtX(b[0] + 20 + i * 14, sp, 38, SansBone.BOTTOM);
            boneAtX(b[1] - 30 - i * 14, -sp, 38, SansBone.BOTTOM);
        }
        // Cut the instant the two innermost bones first touch (10px wide).
        double innerGap = (b[1] - 30 - 3 * 14) - (b[0] + 20 + 3 * 14);
        chainTimer = (int) Math.ceil((innerGap - 10) / (sp * 2));
    }

    /**
     * 14.2 / 20.2 / 20.3 / 20.5 — randomized bone lines sealed top+bottom (the
     * a_type 2 family: the only way through is the slit between the pair); the
     * player threads two slits, then the cut.
     */
    private void subGates() {
        blueFloor();
        double value = 0;
        for (int i = 0; i < 5; i++) {
            double ht = chooseInt(20, 30, 40, 60);
            double xx = i > 0 ? chooseInt(-2, 0, 2) : 0;
            if (ht == 60) {
                xx = 0;
            }
            if (ht == 40) {
                xx *= 0.5;
            }
            sbo(ht, 7 + xx, 40 + i * 25 + value, SansBone.BOTTOM);
            sbo(ht, -7 + xx, 40 + i * 25 + value, SansBone.BOTTOM);
            sbo(ht + 24, 7 + xx, 40 + i * 25 + value, SansBone.TOP);
            sbo(ht + 24, -7 + xx, 40 + i * 25 + value, SansBone.TOP);
            if (ht == 30) {
                value += 5;
            }
            if (ht == 40) {
                value += 10;
            }
            if (ht == 60) {
                value += 20;
            }
        }
        chainTimer = 78;   // the second slit crosses the soul, then the cut
    }

    /** 14.3 / 20.6 — a train from the left: 11 mediums leading, 10 smalls behind. */
    private void subTrain() {
        blueFloor();
        double[] b = G.idealborder;
        double sp = 16;   // 1.6× wave-bone speed
        double head = b[0] - 40;
        for (int i = 0; i < 11; i++) {
            boneAtX(head - i * 26, sp, 65, SansBone.BOTTOM);
        }
        for (int i = 0; i < 10; i++) {
            boneAtX(head - 11 * 26 - i * 13, sp, 16, SansBone.BOTTOM);
        }
        // Cut once the 10th medium has slid past the right border.
        chainTimer = (int) ((b[1] - (head - 9 * 26)) / sp) + 4;
    }

    /** 14.5 / 24.5 — blue long / short / long from both sides; cut as shorts meet. */
    private void subBluePair() {
        blueFloor();
        double[] b = G.idealborder;
        double sp = 10;   // near wave-bone speed
        boneAtX(b[0] + 40, sp, 128, SansBone.TALL_BLUE);
        boneAtX(b[0] + 10, sp, 20, SansBone.BOTTOM);
        boneAtX(b[0] - 25, sp, 128, SansBone.BOTTOM);
        boneAtX(b[1] - 50, -sp, 128, SansBone.TALL_BLUE);
        boneAtX(b[1] - 20, -sp, 20, SansBone.BOTTOM);
        boneAtX(b[1] + 15, -sp, 128, SansBone.BOTTOM);
        // The cut lands exactly as the two short bones collide mid-box.
        chainTimer = (int) (((b[1] - 20) - (b[0] + 10)) / (sp * 2)) + 2;
    }

    /** 16.1 / 16.5 / 24.1 (left) and 20.1 (right) — the sine bone wave. */
    private void subWave(boolean fromRight) {
        board.instaBorder(200, 440, 225, 385);
        soulStopRed();
        centerSoul();
        double hs = fromRight ? -12 : 12;
        for (int i = 0; i < 20; i++) {
            sbo(135 - Math.sin(i / 3.0) * 28, hs, 40 + i * 2, SansBone.TOP);
            sbo(90 - Math.sin(i / 3.0) * 28, hs, 40 + i * 2, SansBone.BOTTOM);
        }
        chainTimer = 110;   // cut once the whole wave has crossed the box
    }

    /**
     * 16.2 / 20.4 / 24.6 (24.3 mirrored) — two static platforms stacked dead in
     * line over a bone-tooth floor; a ceiling bone hanging down to the upper deck
     * glides in first, and just before it reaches the platforms a floor bone
     * rising to the upper deck follows from the other side. Cut once the floor
     * bone has cleared the platforms.
     */
    private void subPlatforms(boolean mirror) {
        wideBorder();
        double[] b = G.idealborder;
        // The floor is carpeted in bone teeth — only the platforms are safe.
        for (double bx = b[0] + 6; bx < b[1] - 10; bx += 13) {
            boneAtX(bx, 0, 12, SansBone.BOTTOM);
        }
        double mid = (b[0] + b[1]) / 2;
        BonePlatform p1 = hplat(55, 0, 0, 28);
        p1.x = mid;
        BonePlatform p2 = hplat(95, 0, 0, 28);
        p2.x = mid;
        soul.setMode(SoulMode.BLUE);
        soul.hspeed = 0;
        soul.vspeed = 0;
        soul.x = p2.x;
        soul.y = p2.y - Soul.HALF;
        soul.jumpStage = Soul.GROUNDED;
        // Near wave-bone speed; the ceiling bone hangs down to the upper deck.
        double sp = mirror ? -10 : 10;
        boneAtX(mirror ? b[1] + 30 : b[0] - 30, sp, 97, SansBone.TOP);
        // The floor bone is staged farther out so it only enters the box as the
        // ceiling bone is about to reach the platforms.
        boneAtX(mirror ? b[0] - 220 : b[1] + 220, -sp, 90, SansBone.BOTTOM);
        chainTimer = 60;   // the floor bone has cleared both platforms by now
    }

    /**
     * 16.3 / 24.4 — the a_type 5 thin crossing streams ([Attack #10]'s pattern):
     * low bones crawl in from the left while hanging bones drift in from the
     * right; cut once the first bone has slid past the far border.
     */
    private void subSlides() {
        blueFloor();
        for (int i = 0; i < 8; i++) {
            sbo(20, 4, 65 + i * 19, SansBone.BOTTOM);
            sbo(28, -4, 65 + i * 19, SansBone.TOP);
        }
        chainTimer = 118;
    }

    /** 16.4 — four corner blasters firing through the small box as an X. */
    private void subXBlasters() {
        board.instaBorder(255, 390, 195, 320);
        soulStopRed();
        centerSoul();
        double[] b = G.idealborder;
        blasterAt(0, 0, 45, b[0] - 50, b[2] - 50, 2, 12, 8);
        blasterAt(640, 0, -45, b[1] + 50, b[2] - 50, 2, 12, 8);
        blasterAt(0, 480, 135, b[0] - 50, b[3] + 50, 2, 12, 8);
        blasterAt(640, 480, -135, b[1] + 50, b[3] + 50, 2, 12, 8);
        chainTimer = 80;
    }

    // ---- Smasher (GML smasher block) --------------------------------------------

    private void stepSmasher() {
        if (!smasher) {
            return;
        }
        if (smashAlarm > 0 && --smashAlarm == 0) {
            smashcon++;
        }
        switch (smashcon) {
            case 0 -> {
                centerSoul();
                soulStopRed();
                smashdir = nextSmashdir();
                prevsmash = smashdir;
                aspeed = smashlv == 2 ? 2 : 1;
                armForSmashdir(smashdir);
                smashcon = 1;
                smashAlarm = smashlv == 2 ? 4 : 8;
            }
            case 2 -> {
                xtimer = 0;
                intensity = 16;
                slamWall(smashdir, false);
                smashcon = 3;
            }
            case 3 -> {
                xtimer++;
                if (soul.grounded() && xtimer >= 5) {
                    xtimer = 0;
                    BoneStab bs = new BoneStab(manager, soul, karma);
                    bs.dir = smashdir;
                    bs.warning = smashlv == 1 ? 9 : 12;
                    bs.height = smashlv == 2 ? 40 : 25;
                    bs.retain = smashlv == 2 ? -7 : (smashlv == 1 ? -2 : 4);
                    manager.add(bs);
                    smashcon = 4;
                    smashAlarm = smashlv == 2 ? 7 : (smashlv == 1 ? 12 : 18);
                }
            }
            case 5 -> {
                smashamt++;
                if (smashamt > (smashSeq != null ? smashSeq.length - 1 : SMASH_MAX)) {
                    endSmasher();
                } else {
                    smashdir = nextSmashdir();
                    prevsmash = smashdir;
                    armForSmashdir(smashdir);
                    smashcon = 1;
                    smashAlarm = smashlv == 2 ? 7 : 8;
                }
            }
            default -> { }
        }
    }

    /** Scripted direction when a sequence is set; the GML random pick otherwise. */
    private int nextSmashdir() {
        if (smashSeq != null) {
            return smashSeq[Math.min(smashamt, smashSeq.length - 1)];
        }
        int dir = chooseInt(0, 1, 2, 3);
        for (int i = 0; i < 10 && dir == prevsmash; i++) {
            dir = chooseInt(0, 1, 2, 3);
        }
        if (dir == prevsmash) {
            dir = (dir + 1) % 4;
        }
        return dir;
    }

    private void endSmasher() {
        smasher = false;
        smashcon = -1;
        smashamt = 0;
        smashSeq = null;
        soulStopRed();
        movearm = 0;
        if (lac == 0 && chain == 0) {
            G.turntimer = 0; // back to the player menu (GML: mnfight = 3)
        }
    }

    /** GML: smashdir → movearm pose (0 down, 1 right, 2 up, 3 left). */
    private void armForSmashdir(int dir) {
        movearm = switch (dir) {
            case 0 -> 3;   // handdown
            case 1 -> 1;   // rightstrike
            case 2 -> 2;   // handup
            default -> 4;  // rightstrike reversed
        };
        armI = 0;
        headx = 0;
        heady = 0;
    }

    // ---- Opening barrage (GML fac 1-22) --------------------------------------------

    private void facWait(int frames, int next) {
        facTimer = frames;
        facNext = next;
    }

    private GasterBlaster blasterAt(double sx, double sy, double rot,
                                    double ix, double iy, double scale,
                                    int pause, int terminal) {
        GasterBlaster gb = new GasterBlaster(manager, soul, karma);
        gb.x = sx;
        gb.y = sy;
        gb.idealrot = rot;
        gb.imageAngle = rot;
        gb.idealx = ix;
        gb.idealy = iy;
        gb.xscale = scale;
        gb.yscale = scale;
        gb.pause = pause;
        gb.terminal = terminal;
        manager.add(gb);
        return gb;
    }

    private void stepFac() {
        if (fac == 0) {
            return;
        }
        if (facTimer > 0 && --facTimer == 0) {
            fac = facNext;
        }
        double[] b = G.idealborder;
        switch (fac) {
            case 1 -> {
                intensity = 25;
                board.instaBorder(240, 400, b[3] - 160, b[3]);
                centerSoul();
                soul.setMode(SoulMode.BLUE);
                bounce = 0;
                facetype = 1;        // the glowing blue eye
                fI = 0;
                movearm = 3;
                armI = 0;
                fac = 2;
                facWait(7, 3);
            }
            case 3 -> {
                slamWall(0, false);  // the first slam down
                fac = 2;
                facWait(14, 5);
            }
            case 5 -> {
                movearm = 2;
                armI = 0;
                facetype = 0;
                BoneStab bs = new BoneStab(manager, soul, karma);
                bs.dir = BoneStab.FROM_BOTTOM;
                bs.height = 55;
                bs.warning = 6;
                bs.retain = 30;
                manager.add(bs);
                for (int i = 0; i < 20; i++) {
                    sbo(135 - Math.sin(i / 3.0) * 28, 12, 40 + i * 2, 2);
                    sbo(90 - Math.sin(i / 3.0) * 28, 12, 40 + i * 2, 0);
                }
                fac = 2;
                facWait(10, 7);
            }
            case 7 -> {
                intensity = 15;
                fac = 2;
                facWait(10, 9);
            }
            case 9 -> {
                movearm = 1;
                armI = 0;
                soulStopRed();
                fac = 2;
                facWait(45, 11);   // GML 9→9.1→10.1→10 (alarm 8 + 37)
            }
            case 11 -> {
                blasterAt(0, 0, 90, b[0] - 50, b[2] + 20, 2, 10, 8);
                blasterAt(640, 480, -90, b[1] + 50, b[3] - 20, 2, 10, 8);
                blasterAt(0, 0, 0, b[0] + 20, b[2] - 60, 2, 10, 8);
                blasterAt(640, 480, 180, b[1] - 20, b[3] + 60, 2, 10, 8);
                movearm = 0;
                fac = 2;
                facWait(25, 13);
            }
            case 13 -> {
                blasterAt(0, 0, 45, b[0] - 50, b[2] - 50, 2, 10, 8);
                blasterAt(640, 0, -45, b[1] + 50, b[2] - 50, 2, 10, 8);
                blasterAt(0, 480, 135, b[0] - 50, b[3] + 50, 2, 10, 8);
                blasterAt(640, 480, -135, b[1] + 50, b[3] + 50, 2, 10, 8);
                fac = 2;
                facWait(25, 15);
            }
            case 15 -> {
                blasterAt(0, 0, 90, b[0] - 50, b[2] + 20, 2, 10, 8);
                blasterAt(640, 480, -90, b[1] + 50, b[3] - 20, 2, 10, 8);
                blasterAt(0, 0, 0, b[0] + 20, b[2] - 60, 2, 10, 8);
                blasterAt(640, 480, 180, b[1] - 20, b[3] + 60, 2, 10, 8);
                fac = 2;
                facWait(20, 17);
            }
            case 17 -> {
                blasterAt(0, 240, 90, b[0] - 100, b[2] + 80, 3, 20, 15);
                blasterAt(640, 240, -90, b[1] + 100, b[2] + 80, 3, 20, 15);
                fac = 2;
                facWait(90, 19);
            }
            case 19 -> {
                G.faceemotion = 0;
                G.flag[20] = 0;
                boss.monologue(SansBoss.OPENING_HUH);
                fac = 2;
                facWait(SansBoss.OPENING_HUH.length * SansBoss.SPEECH_FRAMES + 20, 20);
            }
            case 20 -> {
                bounce = 1;
                movearm = 0;
                facetype = 0;
                fac = 0;
                boss.openingDone();
            }
            default -> { }
        }
    }

    // ---- Slams (GML event_user 10-13) --------------------------------------------

    /**
     * Throw the soul at a wall; wall ids match BoneStab dirs (0 b / 1 r / 2 t / 3 l).
     * {@code pain} mirrors GML's {@code slam_pain}: only the final slam loop sets it,
     * so smashers and the scripted intro throws don't hurt on landing.
     */
    private void slamWall(int wallDir, boolean pain) {
        SoulMode mode = switch (wallDir) {
            case 0 -> SoulMode.BLUE;
            case 1 -> SoulMode.BLUE_RIGHT;
            case 2 -> SoulMode.BLUE_UP;
            default -> SoulMode.BLUE_LEFT;
        };
        soul.applySlam(mode);
        switch (wallDir) {
            case 0 -> soul.vspeed = intensity;
            case 1 -> soul.hspeed = intensity;
            case 2 -> soul.vspeed = -intensity;
            default -> soul.hspeed = -intensity;
        }
        soul.slamPain = pain ? 1 : 0;
        soul.jumpStage = Soul.AIRBORNE;
    }

    /** GML lac-53 dd convention (1 right, 2 up, 3 down, 4 left), optional wall stab. */
    private void slamDd(int slamDd, boolean withStab) {
        int wallDir = switch (slamDd) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 0;
            default -> 3;
        };
        slamWall(wallDir, withStab);
        if (withStab) {
            BoneStab bs = new BoneStab(manager, soul, karma);
            bs.dir = wallDir;
            bs.warning = 12;
            bs.height = 25;
            bs.retain = -2;
            manager.add(bs);
        }
    }

    // ---- The special attack (GML lac chain) ----------------------------------------

    private void waitThen(int frames, int next) {
        lacTimer = frames;
        lacNext = next;
    }

    private void stepLac() {
        if (lac == 0) {
            return;
        }
        if (lacTimer > 0 && --lacTimer == 0) {
            lac = lacNext;
        }
        double[] b = G.idealborder;
        // 26.3 — the conveyor: the heart holds its x and only dodges vertically
        // until the corridor's end throws it loose.
        if (repeater && lac >= 17 && lac <= 20) {
            soul.x = 80;
            soul.hspeed = 0;
        }
        switch (lac) {
            case 4 -> {
                // 26.1 — the scripted up/up/down/up slam set.
                board.instaBorder(240, 400, b[3] - 160, b[3]);
                centerSoul();
                soulStopRed();
                startSmasher(1, new int[] { 2, 2, 0, 2 });
                lac = 5;
            }
            case 5 -> {
                // 26.2 — horizontal slabs sweeping vertically (turn 19's corridor,
                // a touch faster), falling on one side while rising on the other.
                if (!smasher) {
                    soulStopRed();
                    board.instaBorder(220, 420, 185, 385);
                    double[] bb = G.idealborder;
                    wall(bb[0] - 90, -110, 0, 9, true);
                    wall(bb[0] + 90, 760, 0, -9, true);
                    wall(bb[0] - 90, -310, 0, 9, true);
                    wall(bb[0] + 90, 900, 0, -8, true);
                    wall(bb[0] - 90, -650, 0, 9, true);
                    wall(bb[0] + 90, 1180, 0, -9, true);
                    wall(bb[0] - 90, -960, 0, 9, true);
                    wall(bb[0] + 110, 1510, 0, -9, true);
                    lac = 6;
                    waitThen(190, 7);
                }
            }
            case 7 -> {
                // 26.3 begins — throw the soul right and tear the box open.
                intensity = 15;
                movearm = 1;
                armI = 0;
                slamDd(1, false);
                lac = 14;
                waitThen(10, 15);
            }
            case 14 -> {
                board.instaBorder(b[0], b[1] + 15, b[2], b[3]);
                // The soul rests on the right wall while the box stretches —
                // ride the wall outward instead of being left behind mid-air.
                if (soul.grounded()) {
                    soul.x = G.idealborder[1] - Soul.HALF;
                }
            }
            case 15 -> {
                lac = 16;
                waitThen(40, 17);
            }
            case 16 -> {
                // The box tears open into a long flat corridor while Sans backs off
                // left. GML lets the right edge run to x≈950 offscreen; clamp it to
                // the screen so a falling soul lands on a visible wall.
                board.instaBorder(b[0] - 30, Math.min(632, b[1] + 10), b[2] + 2, b[3] - 0.75);
                repeater = true;
                rpX = 0;
                soul.hspeed = 0;
                soul.vspeed = 0;
                if (soul.x > 80) {
                    soul.x -= 10;
                }
            }
            case 17 -> {
                for (int i = 0; i < 45; i++) {
                    SansBone bn = sbo(70 - Math.sin(i / 2.0) * 25, -30, 10 + i * 2, 2);
                    bn.x += 15;
                    bn = sbo(30 - Math.sin(i / 2.0) * 25, -30, 10 + i * 2, 0);
                    bn.x += 15;
                }
                lac = 18;
                waitThen(100, 19);
            }
            case 19 -> {
                corridorGrid();
                lac = 20;
                waitThen(134, 21);
            }
            case 21 -> {
                board.instaBorder(b[0], 640, b[2], b[3]);
                soul.setMode(SoulMode.BLUE_RIGHT);
                soul.hspeed = 11;
                lac = 23;
            }
            case 23 -> {
                if (b[1] > 420) {
                    board.instaBorder(b[0], Math.max(420, b[1] - 18), b[2], b[3]);
                }
                if (soul.grounded() && soul.hspeed <= 0) {
                    G.faceemotion = 0;
                    G.flag[20] = 0;
                    repeater = false;
                    x = 320;
                    lac = 24;
                    waitThen(5, 25);
                }
            }
            case 25 -> {
                BoneStab bs = new BoneStab(manager, soul, karma);
                bs.dir = BoneStab.FROM_RIGHT;
                bs.warning = 12;
                bs.height = 50;
                bs.retain = 15;
                manager.add(bs);
                aspeed = 1;
                movearm = 4;
                armI = 0;
                lac = 26;
                waitThen(28, 27);
            }
            case 27 -> {
                // 26.4 — dropped low; warned, then bones erupt from the top AND
                // the bottom together — the only safety is the slit between them.
                flashCut();
                board.instaBorder(250, 390, 185, 320);
                double[] z = G.idealborder;
                slamWall(0, false);
                soul.x = (z[0] + z[1]) / 2;
                stab(BoneStab.FROM_TOP, 42);
                stab(BoneStab.FROM_BOTTOM, 42);
                lac = 28;
                waitThen(95, 30);
            }
            case 30 -> {
                // 26.5 — pinned to the top-left corner; the top and the left erupt.
                flashCut();
                double[] z = G.idealborder;
                slamWall(2, false);
                soul.x = z[0] + 22;
                stab(BoneStab.FROM_TOP, 48);
                stab(BoneStab.FROM_LEFT, 60);
                lac = 31;
                waitThen(95, 32);
            }
            case 32 -> {
                // 26.6 — the bottom-left corner; the bottom and the left erupt.
                flashCut();
                double[] z = G.idealborder;
                slamWall(0, false);
                soul.x = z[0] + 22;
                stab(BoneStab.FROM_BOTTOM, 48);
                stab(BoneStab.FROM_LEFT, 60);
                lac = 33;
                waitThen(95, 34);
            }
            case 34 -> {
                // 26.7 — shoved against the left wall, which then erupts.
                flashCut();
                slamWall(3, false);
                stab(BoneStab.FROM_LEFT, 62);
                lac = 35;
                waitThen(95, 36);
            }
            case 36 -> {
                // [After final attack] — the speech, then the special itself.
                soulStopRed();
                centerSoul();
                movearm = 0;
                sweat = 0;
                monologue(SansBoss.PRE_SPECIAL, 50);
            }
            case 50 -> {
                board.instaBorder(SLEEP_BOX[0], SLEEP_BOX[1], SLEEP_BOX[2], SLEEP_BOX[3]);
                centerSoul();
                soulStopRed();
                gt = 0;
                gin = 1;
                everyOther = false;
                lac = 51;
            }
            case 51 -> stepRing();
            case 53 -> stepSlamLoop();
            case 60 -> {
                boss.setMusicVolume(0);
                movearm = 0;
                headx = 0;
                heady = 0;
                bounce = 2;
                lac = 61;
                waitThen(80, 62);
            }
            case 62 -> {
                soulStopRed();
                sweat = 3;
                G.faceemotion = 9;
                G.flag[20] = 0;
                monologue(SansBoss.MONO_HUFF, 65);
            }
            case 65 -> monologue(SansBoss.MONO_NOTHING, 68);
            case 68 -> monologue(SansBoss.MONO_BORED, 71);
            case 71 -> monologue(SansBoss.MONO_TYPE, 74);
            case 74 -> monologue(SansBoss.MONO_GIVE_UP, 75);
            case 75 -> {
                G.faceemotion = 9;
                boss.clearSpeech();  // no bubble lingering over the dozing-off window
                sleepPhase = 1;
                sleepT = 0;
                lac = -1;   // the lac chain is done; the sleep machine takes over
            }
            default -> { }
        }
    }

    /** A 26.4–26.7 wall eruption: long red warning, then the slab thrusts. */
    private void stab(int dir, double height) {
        BoneStab bs = new BoneStab(manager, soul, karma);
        bs.dir = dir;
        bs.height = height;
        bs.warning = 30;
        bs.retain = 18;
        manager.add(bs);
    }

    /** The ending monologue runs at a brisker per-line pace than normal speech. */
    private static final int END_SPEECH_FRAMES = 55;

    private void monologue(SansBoss.Line[] lines, int next) {
        boss.monologue(lines, END_SPEECH_FRAMES);
        waitThen(lines.length * END_SPEECH_FRAMES + 90, next);
        lac = 1; // inert step (no handler) while the lines play out
    }

    /**
     * GML lac 19: the corridor grid — clusters alternate hanging/rising across
     * the run (26.3(2)), every cluster the same 50px long whether it hangs from the
     * ceiling or rises from the floor (26.3(3): "123 / 123"), then the closing
     * wedge whose slit slides down to drop the heart out.
     */
    private void corridorGrid() {
        double h = G.idealborder[3] - G.idealborder[2];
        double[][] rows = {
            { 10, 2 }, { 21, 0 }, { 31, 2 }, { 41, 0 }, { 50, 2 },
            { 59, 0 }, { 67, 2 }, { 78, 0 }, { 87, 2 },
        };
        for (double[] row : rows) {
            boolean top = row[1] == 2;
            for (int k = 0; k < 3; k++) {
                // A hanging cluster's sbo height is measured from the floor, so
                // h-50 gives it the same visible 50px as a rising cluster.
                SansBone bn = sbo(top ? h - 50 : 50, -30, row[0],
                        top ? SansBone.TOP : SansBone.BOTTOM);
                bn.x += 15 + k * 15;
            }
        }
        // The closing wedge: every column a symmetric pair (top length = bottom
        // length, "123.../123..."), the centred slit narrowing but always wide
        // enough for the heart to slip through.
        for (int i = 0; i < 24; i++) {
            double len = 8 + i;                 // gap = h - 2*len, ending ≈ 28px
            SansBone bn = sbo(h - len, -30, 100 + i, SansBone.TOP);
            bn.x += 15;
            bn = sbo(len, -30, 100 + i, SansBone.BOTTOM);
            bn.x += 15;
        }
    }

    /** GML lac 51: the accelerating 360° Gaster Blaster ring. */
    private void stepRing() {
        if (everyOther) {
            double[] b = G.idealborder;
            double cx = b[0] + (b[1] - b[0]) / 2;
            double cy = b[2] + (b[3] - b[2]) / 2;
            double disx = GMLHelper.lengthdir_x(150, gt * 10);
            double disy = GMLHelper.lengthdir_y(150, gt * 10);
            GasterBlaster gb = new GasterBlaster(manager, soul, karma);
            gb.idealrot = -90 + gt * 10;
            gb.imageAngle = gb.idealrot;
            gb.idealx = cx + disx;
            gb.idealy = cy + disy;
            gb.x = cx + disx * 3;
            gb.y = cy + disy * 3;
            gb.terminal = 0;
            gb.pause = 15;
            gb.yscale = 2;
            manager.add(gb);
            gt += gin;
            if (gin < 1.7) {
                gin += 0.015;
            }
            everyOther = false;
        } else {
            everyOther = true;
        }
        if (gt >= 190) {
            pdd = -1;
            bounce = 0;
            intensity = 30;
            aspeed = 2;
            lcT = 0;
            lcC = 0;
            lcA = 0;
            lac = 52;
            waitThen(30, 53);
        }
    }

    /** GML lac 53: the infamous slam loop, lc_c 0→19, tempo and music dying down. */
    private void stepSlamLoop() {
        int armFrames = (int) Math.round(8 / aspeed);
        if (lcT == 0) {
            dd = chooseInt(1, 2, 3, 4);
            for (int i = 0; i < 8 && dd == pdd; i++) {
                dd = chooseInt(1, 2, 3, 4);
            }
            if (lcC == 0) {
                dd = 1;
                facetype = 1;
            }
            if (lcC == 18) {
                dd = 2;
            }
            movearm = dd;
            armI = 0;
            headx = 0;
            heady = 0;
        }
        if (lcT == armFrames) {
            if (lcC == 18) {
                lcA = 21;
            }
            if (lcC == 17) {
                lcA = 12;
            }
            slamDd(dd, true);
        }
        if (lcT == lcA * 2 + armFrames + 4) {
            if (lcC == 18) {
                intensity = 2;
            }
            dd = switch (dd) {
                case 3 -> 2;
                case 1 -> 4;
                case 4 -> 1;
                default -> 3;
            };
            pdd = dd;
            if (lcC == 18) {
                dd = 3;
                sweat = 3;
                G.faceemotion = 9;
            }
            movearm = dd;
            armI = 0;
            headx = 0;
            heady = 0;
        }
        if (lcT == lcA * 2 + armFrames * 2 + 4) {
            slamDd(dd, true);
            if (lcC == 18) {
                lcA = 21;
            }
        }
        lcT++;
        if (lcT == lcA * 4 + armFrames * 2 + 7) {
            lcT = 0;
            lcC++;
            G.shakify = Math.max(0, lcC - 10);
            switch (lcC) {
                case 11 -> {
                    lcA = 1;
                    boss.setMusicVolume(0.8);
                    intensity = 20;
                }
                case 12 -> {
                    lcA = 2;
                    intensity = 20;
                }
                case 13 -> {
                    lcA = 0;
                    aspeed = 1;
                    intensity = 16;
                    sweat = 1;
                    facetype = 0;
                    G.faceemotion = 0;
                }
                case 14 -> {
                    lcA = 2;
                    boss.setMusicVolume(0.7);
                    intensity = 14;
                }
                case 15 -> {
                    lcA = 4;
                    boss.setMusicVolume(0.5);
                    intensity = 12;
                }
                case 16 -> {
                    lcA = 6;
                    boss.setMusicVolume(0.25);
                    intensity = 12;
                }
                case 17 -> {
                    lcA = 8;
                    boss.setMusicVolume(0.15);
                    aspeed = 0.5;
                    intensity = 11;
                    sweat = 2;
                    G.faceemotion = 2;
                }
                case 18 -> {
                    lcA = 15;
                    boss.setMusicVolume(0.07);
                    intensity = 8;   // the trap: everything has nearly stopped
                }
                case 19 -> {
                    G.shakify = 0;
                    lac = 60;
                }
                default -> { }
            }
        }
    }

    // ---- Sleep ending -----------------------------------------------------------

    private void stepSleep() {
        if (sleepPhase == 1) {
            sleepT++;
            if (sleepT == 300) {
                G.faceemotion = 12;
                sweat = Math.max(0, sweat - 1);
            }
            if (sleepT == 600) {
                G.faceemotion = 13;
                sweat = Math.max(0, sweat - 1);
            }
            if (sleepT == 900) {
                G.faceemotion = 14;
                sweat = Math.max(0, sweat - 1);
            }
            if (sleepT >= 1200) {
                fallAsleep();
            }
        } else if (sleepPhase == 2) {
            stepSnore();
            stepPush();
            buttonLit = soulOnButton();
            if (buttonLit && soul.confirmPressed) {
                sleepPhase = 3;
                boss.setSleepZ("");
                boss.mercyDeath();
                return;
            }
            sleepCountdown--;
            if (sleepCountdown <= 0) {
                sleepPhase = 3;
                bounce = 1;
                G.faceemotion = 0;
                boss.setSleepZ("");
                boss.wakeUp();
            }
        }
    }

    /** The snoring "Z"s, shown as a speech bubble rather than floating sprites. */
    private static final String[] SNORE = { "Z", "Z   z", "Z   z   z", "z   z   z" };

    private void stepSnore() {
        zClock++;
        boss.setSleepZ(SNORE[(zClock / 26) % SNORE.length]);
    }

    private void fallAsleep() {
        sleepPhase = 2;
        sleepCountdown = 2700;   // pushing the box takes a while — ~90 s window
        G.faceemotion = 9;       // a non-bloody dozing face
        bounce = 3;
        sleepPinned = true;      // freeze Sans's y so shoving the box won't drag him
        showButton = true;       // the fake FIGHT button only appears now he's asleep
        sleepEx = SLEEP_BOX[0];
        sleepEy = SLEEP_BOX[2];
        // The white border is replaced by the empty frame the soul can shove.
        board.visible = false;
        board.instaBorder(sleepEx, sleepEx + BOX_SIZE, sleepEy, sleepEy + BOX_SIZE);
        soulStopRed();
    }

    /**
     * GML obj_emptyborder_s: while Sans sleeps the soul stays boxed in, but
     * pressing against the left wall shoves the whole box left (to x=20), and —
     * once it's fully left — pressing the floor shoves it down (to y=310), until
     * the box surrounds the fake FIGHT button.
     */
    private void stepPush() {
        boolean moved = false;
        if (soul.leftHeld && soul.x <= sleepEx + Soul.HALF + 2 && sleepEx > PUSH_MIN_X) {
            sleepEx = Math.max(PUSH_MIN_X, sleepEx - 0.5);
            moved = true;
        }
        if (sleepEx <= PUSH_MIN_X && soul.downHeld
                && soul.y >= sleepEy + BOX_SIZE - Soul.HALF - 2 && sleepEy < PUSH_MAX_Y) {
            sleepEy = Math.min(PUSH_MAX_Y, sleepEy + 0.5);
            moved = true;
        }
        if (moved) {
            board.instaBorder(sleepEx, sleepEx + BOX_SIZE, sleepEy, sleepEy + BOX_SIZE);
        }
    }

    private boolean soulOnButton() {
        return soul.x + Soul.HALF > BUTTON[0] && soul.x - Soul.HALF < BUTTON[0] + BUTTON[2]
                && soul.y + Soul.HALF > BUTTON[1] && soul.y - Soul.HALF < BUTTON[1] + BUTTON[3];
    }

    // ---- Idle/pose helpers --------------------------------------------------------

    private void stepBounce() {
        switch (bounce) {
            case 1 -> {
                siner++;
                yoff = Math.sin(siner / 3.0);
                xoff = Math.cos(siner / 6.0);
            }
            case 2 -> {
                siner++;
                yoff = Math.sin(siner / 15.0) * 4;
                xoff = 0;
            }
            case 3 -> {
                siner++;
                yoff = Math.sin(siner / 18.0) * 2;
                xoff = 0;
            }
            default -> {
                siner = 0;
                yoff = 0;
                xoff = 0;
            }
        }
    }

    /**
     * The turning-point "mercy" trap: the player tried to SPARE, so Sans takes the
     * opening — his eye flares and a Gaster Blaster materialises point-blank on the
     * soul and fires at once. {@link SansBoss} forces the kill a few frames later.
     */
    public void sneakAttack() {
        bounce = 0;
        movearm = 0;
        sweat = 0;
        facetype = 1;        // the glowing blue eye
        fI = 0;
        G.faceemotion = 5;
        // The beam travels along (imageAngle − 90): idealrot 0 fires straight DOWN.
        // The skull hovers above the box; its mouth (≈84px below centre at 2.4×) sits
        // just over the soul so the vertical beam runs right through it.
        GasterBlaster gb = new GasterBlaster(manager, soul, karma);
        gb.x = soul.x;
        gb.y = -100;
        gb.idealx = soul.x;
        gb.idealy = soul.y - 150;
        gb.idealrot = 0;     // beam fires straight down at the soul
        gb.imageAngle = 0;
        gb.xscale = 2.4;
        gb.yscale = 2.4;
        gb.pause = 3;
        gb.terminal = 8;
        manager.add(gb);
    }

    /** GML dodge: the player swung — Sans slips left, waits out the swing, returns. */
    public void dodge() {
        if (dodgePhase == 0) {
            dodgePhase = 1;
        }
    }

    private void stepDodge() {
        if (dodgePhase == 1) {
            dodgeT = 0;
            dodgeX = x;
            dodgeH = -12;
            dodgePhase = 2;
        }
        if (dodgePhase == 2) {
            x += dodgeH;
            if (x < dodgeX - 60 && dodgeT < 20) {
                dodgeH = dodgeH < 0 ? dodgeH + 2 : 0;
            }
            dodgeT++;
            if (dodgeT >= 35) {
                if (dodgeH < 12) {
                    dodgeH += 2;
                }
                if (x >= dodgeX - 13) {
                    dodgeH = 0;
                    x = dodgeX;
                    dodgePhase = 0;
                }
            }
        }
    }

    private void stepRepeater() {
        if (!repeater) {
            return;
        }
        movearm = 0;
        rpX += 0.05;
        x -= Math.floor(30 + rpX);
        if (x < -100) {
            G.faceemotion = chooseInt(0, 1, 3, 4, 5);
            G.flag[20] = chooseInt(0, 0, 0, 1);
            x = 740;
        }
    }

    // ---- Rendering ------------------------------------------------------------------

    private double y() {
        // While Sans sleeps the soul can shove the empty box around; keep his
        // sprite anchored to the original box top so he doesn't ride it down.
        if (sleepPinned) {
            return SLEEP_BOX[2] - 130;
        }
        return G.idealborder[2] - 130;
    }

    @Override
    public void render(Graphics2D g) {
        double by = y();
        renderSleepFurniture(g);
        drawPart(g, "spr_sansb_legs", 0, x, by + 90, 21, 11);
        switch (movearm) {
            case 0 -> {
                int tf = cleanTorso(G.flag[20]);
                // Each torso frame is a different width; centre it on x by using its
                // own art-centre as the origin (the head/legs follow the same rule),
                // so the parts stack on one vertical axis instead of drifting left.
                drawPart(g, "spr_sansb_torso", tf, x + xoff, by + 42 + yoff / 1.5, torsoOriginX(tf), 12);
            }
            case 1 -> drawArm(g, "spr_sansb_rightstrike", 11, false, by);
            case 2 -> drawArm(g, "spr_sansb_handup", 11, false, by);
            case 3 -> drawArm(g, "spr_sansb_handdown", 9, false, by);
            case 4 -> drawArm(g, "spr_sansb_rightstrike", 10, true, by);
            default -> { }
        }
        if (facetype == 1) {
            fI++;
            drawPart(g, "spr_sansb_blueeye", Math.min(1, fI / 2), x + xoff + headx, by + yoff + heady, 17, 16);
        } else {
            drawPart(g, "spr_sansb_face", cleanFace(G.faceemotion), x + xoff + headx, by + yoff + heady, 16, 15);
        }
        if (sweat > 0) {
            drawPart(g, "spr_sansb_face_sweat", sweat - 1, x + xoff + headx, by + yoff + heady, 16, 15);
        }
    }

    /**
     * The boss-rush Sans never actually bleeds <em>during the fight</em> (he only
     * dodges), so the bloodied art frames are swapped for their clean look-alikes
     * (face 3/4/6/7 → 5/10/11/5). The exception is the final hit / mercy_death
     * ending ({@link #injured}): there the GML deliberately shows the wounded
     * frames (faceemotion 4, torso 0 with the slash), so we draw them raw.
     */
    private int cleanFace(int frame) {
        if (injured) {
            return frame;
        }
        return switch (frame) {
            case 3 -> 5;
            case 4 -> 10;
            case 6 -> 11;
            case 7 -> 5;
            default -> frame;
        };
    }

    /** Horizontal origin (art bbox centre) for each torso frame, so it centres on x. */
    private static int torsoOriginX(int frame) {
        return switch (frame) {
            case 1 -> 35;    // bloodied frames 1/5/6 (shown only at the ending)
            case 5 -> 32;
            case 6 -> 36;
            case 7 -> 27;
            default -> 26;   // frames 0/2/3/4 (~52px wide, centre ~26)
        };
    }

    /**
     * spr_sansb_torso 0-3 are the bloodied frames; 4-7 are the clean equivalents.
     * Frame 4 (the clean idle) is drawn crooked, so the idle torso uses 7 — the
     * clean, symmetric closed-jacket pose — instead. At the mercy_death ending
     * ({@link #injured}) the bloodied frame (with the slash) is shown raw.
     */
    private int cleanTorso(int frame) {
        if (injured) {
            return frame;
        }
        return switch (frame) {
            case 0 -> 7;
            case 1 -> 5;
            case 2 -> 6;
            case 3 -> 7;
            default -> frame;
        };
    }

    /** GML movearm 1-4: the arm-pose strips animate with arm_i, nudging the head. */
    private void drawArm(Graphics2D g, String sprite, int maxI, boolean reversed, double by) {
        int idx = (int) Math.floor(Math.min(armI, maxI) / 2);
        if (reversed) {
            idx = Math.max(0, 5 - idx);
        }
        drawPart(g, sprite, idx, x, by + 42, sprite.endsWith("rightstrike") ? 33 : 30,
                sprite.endsWith("rightstrike") ? 12 : 34);
        // Head nudges keyed to the raise (GML's arm_i tables, condensed).
        int step = (int) Math.min(armI, maxI);
        switch (movearm) {
            case 1 -> headx = new int[] { 0, 0, -4, -4, -8, -8, 10, 10, 4, 4, 0, 0 }[Math.min(step, 11)];
            case 2 -> heady = new int[] { 4, 4, 10, 10, 4, 4, -4, -4, 0, 0, 0, 0 }[Math.min(step, 11)];
            case 3 -> heady = new int[] { 0, 0, 0, 0, 6, 6, 10, 10, 10, 10 }[Math.min(step, 9)];
            case 4 -> headx = new int[] { 0, 0, 4, 4, 10, 10, -8, -8, -4, -4, 0 }[Math.min(step, 10)];
            default -> { }
        }
        if (armI < maxI) {
            armI += aspeed;
        }
    }

    private void drawPart(Graphics2D g, String name, int frame, double cx, double cy,
                          int xorig, int yorig) {
        BufferedImage img = Assets.sprite(name + "_" + frame);
        if (img == null) {
            img = Assets.sprite(name + "_0");
        }
        if (img == null) {
            g.setColor(Color.WHITE);
            g.fillRect((int) cx - 20, (int) cy - 20, 40, 40);
            return;
        }
        g.drawImage(img, (int) (cx - xorig * 2), (int) (cy - yorig * 2),
                img.getWidth() * 2, img.getHeight() * 2, null);
    }

    /** The sleep scene: the fake FIGHT button, and the shovable empty frame. */
    private void renderSleepFurniture(Graphics2D g) {
        if (showButton) {
            BufferedImage btn = Assets.sprite(buttonLit ? "spr_fightbt_0" : "spr_fightbt_1");
            if (btn != null) {
                g.drawImage(btn, BUTTON[0], BUTTON[1], BUTTON[2], BUTTON[3], null);
            } else {
                g.setColor(buttonLit ? Color.YELLOW : new Color(0xFF, 0x99, 0x00));
                g.drawRect(BUTTON[0], BUTTON[1], BUTTON[2], BUTTON[3]);
                g.drawString("FIGHT", BUTTON[0] + 30, BUTTON[1] + 27);
            }
        }
        if (sleepPhase >= 2) {
            g.setColor(Color.WHITE);
            java.awt.Stroke old = g.getStroke();
            g.setStroke(new java.awt.BasicStroke(5f));
            g.drawRect((int) sleepEx, (int) sleepEy, (int) BOX_SIZE, (int) BOX_SIZE);
            g.setStroke(old);
        }
    }
}
