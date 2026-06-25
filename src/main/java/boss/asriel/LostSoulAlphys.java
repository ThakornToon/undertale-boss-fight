package boss.asriel;

import battle.Soul;
import battle.SoulMode;
import boss.BossBody;
import bullet.PlayerBullet;
import bullet.mettaton.BombBlockGen;
import bullet.mettaton.DiscoBall;
import bullet.mettaton.Ex2Gen;
import bullet.mettaton.HeartCore;
import bullet.mettaton.MettLeg2Gen;
import bullet.mettaton.MixDropGen;
import bullet.mettaton.OrbitRing;
import bullet.mettaton.ParasolGen;
import bullet.mettaton.RewindGen;
import bullet.mettaton.SegArmGen;
import bullet.mettaton.SideLegGen;
import core.EntityManager;
import core.GlobalState;
import java.util.List;
import util.GMLHelper;

/**
 * The <b>Alphys</b> Lost Soul (GML SAVE entry → {@code flag[506]}). She fights in YELLOW
 * shoot mode — the show she ran with Mettaton — and is freed after three ACTs (Encourage ·
 * Call · Nerd Out · Quiz). GML {@code obj_ripoff_alphys} spawns {@code obj_mettattackgen}
 * (the full Mettaton EX bullet-hell), so this encounter draws each turn at RANDOM from the
 * WHOLE Mettaton EX set — the legs, parasols + plus-bombs, the segmented arm, the heart-core
 * lightning/burst, the orbit-heart finale, the disco-ball lasers, and the bomb-block & rewind
 * rows — and spawns the YELLOW player-bullet itself (the boss body normally would).
 *
 * <p>Canon freed line: <i>"...my friends like me! And I like you, too!"</i>
 */
public final class LostSoulAlphys extends LostSoul {

    private static final GlobalState G = GlobalState.get();
    // The Mettaton EX box presets (SCR_BORDERSETUP). Each beat picks the one it needs so the
    // narrow bomb/block and the rewind beats get the right-sized box, exactly like the show.
    private static final double[] BOX = { 235, 405, 250, 385 };     // border 24 (default)
    private static final double[] NARROW = { 295, 345, 250, 385 };  // border 26 (bomb/block rows)
    private static final double[] REWIND = { 270, 370, 200, 385 };  // border 27 (4 rewind rows)

    /** Every Mettaton EX beat (legs, parasols, heart-core, disco, bomb/block, rewind, finale). */
    private static final int BEATS = 12;

    private boolean encourage;
    private boolean call;
    private boolean nerd;
    private int lastBeat = -1;
    private int beat;        // this turn's pre-rolled beat — border() reads it, chooseAttack plays it

    public LostSoulAlphys() {
        super("Alphys", 506, 3);
        beat = rollBeat();   // pre-roll so the FIRST border() already matches the first beat
    }

    /** Pick a fresh beat (never the same one twice running) for the next turn. */
    private int rollBeat() {
        int b;
        do {
            b = GMLHelper.irandom(BEATS - 1);
        } while (b == lastBeat);
        lastBeat = b;
        return b;
    }

    @Override
    public BossBody createBody(EntityManager manager) {
        return new LostSoulBody(manager, "spr_alphys_l_0", 3.5, 320, 80);
    }

    @Override
    public SoulMode soulMode() {
        return SoulMode.YELLOW;
    }

    @Override
    public double[] border() {
        // The box for THIS turn's pre-rolled beat (set up just before chooseAttack each turn).
        return switch (beat) {
            case 8 -> NARROW;    // bomb/block rows need the narrow vertical box (border 26)
            case 10 -> REWIND;   // rewind rows need the 4-wide taller box (border 27)
            default -> BOX;      // everything else uses the default Mettaton box (border 24)
        };
    }

    @Override
    public List<String> actLabels() {
        return List.of("Encourage", "Call", "Nerd Out", "Quiz");
    }

    @Override
    public List<Integer> actIds() {
        return List.of(0, 1, 2, 3);
    }

    @Override
    public void chooseAttack(EntityManager manager, Soul soul) {
        // GML obj_ripoff_alphys spawns obj_mettattackgen — the FULL Mettaton EX bullet-hell
        // Alphys ran the show with. Play this turn's pre-rolled beat (drawn at random from the
        // whole Mettaton EX set: the LEGS, parasols, the HEART-core lightning/burst, the disco
        // lasers, the bomb/block & rewind rows, the segmented arm, and the orbit-heart finale),
        // then pre-roll the next so the box is already sized for it.
        G.turntimer = 400;
        playBeat(beat, manager, soul);
        beat = rollBeat();
    }

    /** Spawn one Mettaton EX beat. Beats 8/10 use the narrow / rewind box (see {@link #border}). */
    private void playBeat(int b, EntityManager manager, Soul soul) {
        switch (b) {
            case 0 ->                   // ex0: Mettaton's LEGS slide down (shoot to toggle them)
                manager.add(new MettLeg2Gen(manager, soul, GMLHelper.irandom_range(60, 80), 2.4));
            case 1 -> {                 // ex1: parasols in columns + central plus-bombs
                manager.add(new ParasolGen(manager, soul, GMLHelper.irandom_range(2, 3),
                        GMLHelper.irandom_range(50, 70), 1.9, 4, false));
                manager.add(new MixDropGen(manager, soul, 1, GMLHelper.irandom_range(42, 54),
                        3.0, 0.3, 0.7, true, false));
            }
            case 2 ->                   // ex2: right LEGS → centre boxes → left LEGS
                manager.add(new Ex2Gen(manager, soul, 2.4));
            case 3 -> {                 // ex3: segmented arm (shoot the weak point) + parasols
                manager.add(new SegArmGen(manager, soul, GMLHelper.irandom_range(60, 80), 2.4));
                manager.add(new ParasolGen(manager, soul, 4, GMLHelper.irandom_range(85, 105),
                        1.9, 0, false));
            }
            case 4 -> {                 // ex4: HEART-core lightning (all directions) + parasols
                manager.add(new HeartCore(manager, soul, GMLHelper.irandom_range(8, 12),
                        GMLHelper.irandom_range(75, 95)));
                manager.add(new ParasolGen(manager, soul, 2, GMLHelper.irandom_range(50, 70),
                        1.9, 0, false));
            }
            case 5 ->                   // ex5: parasols scattered at random
                manager.add(new ParasolGen(manager, soul, 2, GMLHelper.irandom_range(26, 36),
                        2.2, 0, true));
            case 6 ->                   // ex6: disco-ball lasers — shoot to re-roll colours
                manager.add(new DiscoBall(manager, soul, GMLHelper.irandom_range(3, 5), 1.6));
            case 7 ->                   // ex7: disco-ball, faster
                manager.add(new DiscoBall(manager, soul, 4, 2.7));
            case 8 ->                   // ex8: bomb/block rows in the NARROW box
                manager.add(new BombBlockGen(manager, soul, GMLHelper.irandom_range(45, 60), 3.0));
            case 9 -> {                 // ex11: HEART-core burst + spiralling boxes
                HeartCore h = new HeartCore(manager, soul, GMLHelper.irandom_range(12, 16), 85);
                manager.add(h);
                manager.add(new OrbitRing(manager, soul, h, 8, false, 0.03));
            }
            case 10 ->                  // ex12: REC→REV rewind rows (the 4-wide box)
                manager.add(new RewindGen(manager, soul, GMLHelper.irandom_range(40, 52), 3.12));
            default -> {                // ex17/finale: HEART + 2 orbiting bombs + LEG walls
                HeartCore h = new HeartCore(manager, soul, 16, 85);
                manager.add(h);
                manager.add(new OrbitRing(manager, soul, h, 2, true, 0.04));
                manager.add(new SideLegGen(manager, soul));
            }
        }
    }

    @Override
    public void update(EntityManager manager, Soul soul) {
        // GML: the boss body spawns the YELLOW player-bullet on the frame the soul fires.
        if (soul.mode == SoulMode.YELLOW && soul.firedThisFrame) {
            manager.add(new PlayerBullet(manager, soul.x, soul.y, null));
        }
    }

    @Override
    public String onAct(int whatiheard) {
        String line;
        switch (whatiheard) {
            case 0 -> {
                line = "* You tell the Lost Soul she's  doing great.";
                if (!encourage) {
                    encourage = true;
                    acts++;
                }
            }
            case 1 -> {
                line = "* You call the Lost Soul.  * ... It almost picks up.";
                if (!call) {
                    call = true;
                    acts++;
                }
            }
            case 2 -> {
                line = "* You geek out about anime  with the Lost Soul.";
                if (!nerd) {
                    nerd = true;
                    acts++;
                }
            }
            default -> line = "* You quiz the Lost Soul on  her favorite shows.";
        }
        if (!freed() && acts > 0) {
            line += "  * The Lost Soul is remembering...";
        }
        return line;
    }

    @Override
    public String freedLine() {
        return "...my friends like me! And I like you, too!";
    }

    @Override
    public String introLine() {
        return "* The Lost Soul appeared.  * (It's ALPHYS.)";
    }
}
