package boss.asriel;

import battle.Soul;
import battle.SoulMode;
import boss.BossBody;
import bullet.bones.SizeBone;
import bullet.bones.TopBone;
import core.EntityManager;
import core.GlobalState;
import java.util.List;

/**
 * The paired <b>Papyrus &amp; Sans</b> Lost Soul (GML SAVE entry → {@code flag[507]}). They
 * fight in BLUE mode — scrolling bone walls you jump to dodge — and are freed after four
 * ACTs across both brothers (Papyrus: Joke/Puzzle · Sans: Take Break/Judgment). Reuses the
 * Papyrus fight's {@link SizeBone} sliding bones and the BLUE gravity/jump soul.
 *
 * <p>Canon freed lines: Papyrus <i>"NO! WAIT!! YOU'RE MY FRIEND!"</i> · Sans
 * <i>"nah, i'm rootin for ya, kid."</i>
 */
public final class LostSoulSkelebros extends LostSoul {

    private static final GlobalState G = GlobalState.get();
    // GML obj_ripoff_papyrus → SCR_BORDERSETUP border 5 (the wide BLUE bone-jump box).
    private static final double[] BOX = { 192, 442, 250, 385 };

    private final boolean[] done = new boolean[4];
    private int turn;

    public LostSoulSkelebros() {
        super("Sans & Papyrus", 507, 4);
    }

    @Override
    public BossBody createBody(EntityManager manager) {
        return new LostSoulBody(manager, "spr_papyrus_d_0", 3.0, 158, 116)
                .add("spr_sans_d_0", 3.0, 482, 130);
    }

    @Override
    public SoulMode soulMode() {
        return SoulMode.BLUE;
    }

    @Override
    public double[] border() {
        return BOX;
    }

    @Override
    public List<String> actLabels() {
        return List.of("Papyrus: Joke", "Papyrus: Puzzle", "Sans: Take Break", "Sans: Judgment");
    }

    @Override
    public List<Integer> actIds() {
        return List.of(0, 1, 2, 3);
    }

    @Override
    public void chooseAttack(EntityManager manager, Soul soul) {
        // BLUE soul rests on the floor; bones slide in from BOTH sides and the heart jumps
        // through the gaps (GML obj_ripoff_papyrus: blt_sizebone floor bones + blt_topbone
        // ceiling bones forming gaps, sliding from idealborder[0]-N and idealborder[1]+N).
        soul.setMode(SoulMode.BLUE);
        soul.x = (G.idealborder[0] + G.idealborder[1]) / 2.0;
        soul.y = G.idealborder[3] - Soul.HALF;
        soul.vspeed = 0;
        soul.jumpStage = Soul.GROUNDED;
        double left = G.idealborder[0];
        double right = G.idealborder[1];

        // The Sans & Papyrus Lost Soul has TWO distinct beats:
        //   • Asriel/lost-spul-sans-papyrus-1.mov = the SCROLLING CORRIDOR — PAIRED bones ("II")
        //     slide across as a right→left stream of obstacles: GAP CORRIDORS (a ceiling pair +
        //     a floor pair leaving an opening whose height VARIES wildly — high near the ceiling,
        //     low near the floor — so the heart jumps to the right height) interleaved with tall
        //     CYAN "blue" bones you pass by HOLDING STILL (you can't jump a full-height bone).
        //   • Asriel/lost-spul-sans-papyrus-2.mov = FLOOR HOPS — short bones the grounded heart
        //     hops over.
        // We rotate through both so the fight is complete. (Heart jump apex ≈ 66px; box ceiling
        // 250 / floor 385, so a HIGH gap centred ~318 is just reachable at the top of a jump.)
        final double up = 24;        // short floor bone — comfortably-jumpable
        final double sp = 3.2;       // slow glide so the hop/gap timing is readable
        // Gap-centre heights (HIGH gap = ceiling bone SHORT + floor bone TALL, heart jumps near
        // its ~66px apex; LOW gap = ceiling bone TALL + floor bone tiny, heart barely hops).
        final double HI = 308, MID = 333, LO = 357;
        switch (turn % 4) {
            case 0 ->                 // SCROLLING CORRIDOR (clip 1): varied gaps + blue walls
                corridorStream(manager, soul, new double[][] {
                    { 'G', HI }, { 'G', LO }, { 'B', 0 }, { 'G', MID },
                    { 'G', HI }, { 'B', 0 }, { 'G', LO }, { 'G', MID },
                });
            case 1 -> {
                // FLOOR HOPS (clip 2): evenly-staggered short bones converging from both sides.
                G.turntimer = 230;
                for (int i = 0; i < 7; i++) {
                    size(manager, soul, left - 30 - i * 150, up, sp);
                    size(manager, soul, right + 30 + i * 150, up, -sp);
                }
            }
            case 2 ->                 // SCROLLING CORRIDOR (clip 1): a different obstacle order
                corridorStream(manager, soul, new double[][] {
                    { 'G', LO }, { 'G', HI }, { 'B', 0 }, { 'G', MID },
                    { 'G', LO }, { 'B', 0 }, { 'G', HI }, { 'G', MID },
                });
            default -> {
                // FLOOR HOPS (clip 2): alternating singles — left, right, left, right — a zig-zag.
                G.turntimer = 220;
                for (int i = 0; i < 11; i++) {
                    if (i % 2 == 0) {
                        size(manager, soul, left - 30 - i * 95, up, sp);
                    } else {
                        size(manager, soul, right + 30 + i * 95, up, -sp);
                    }
                }
            }
        }
        turn++;
    }

    /**
     * Emit a LEFT→RIGHT stream of obstacles (clip 1 — the columns drift rightward across the box).
     * Each entry is {@code {type, gapMid}}: {@code 'G'} = a gap corridor opening at {@code gapMid},
     * {@code 'B'} = a tall cyan blue wall (stand still to pass). Obstacles start off the LEFT edge,
     * staggered by index so they enter one at a time; the turn lasts until the last one crosses.
     */
    private void corridorStream(EntityManager manager, Soul soul, double[][] seq) {
        final double left = G.idealborder[0];
        final double right = G.idealborder[1];
        final double sp = 3.2;       // rightward glide
        final double step = 150;     // horizontal gap between obstacles (≈ time to react)
        for (int i = 0; i < seq.length; i++) {
            double x = left - 60 - i * step;     // off the LEFT edge, staggered
            if (seq[i][0] == 'B') {
                blueWall(manager, soul, x, sp);
            } else {
                corridor(manager, soul, x, seq[i][1], sp);
            }
        }
        // Let the furthest obstacle slide all the way across before the turn ends.
        double span = 60 + (seq.length - 1) * step + (right - left) + 60;
        G.turntimer = (int) (span / sp) + 30;
    }

    /** A floor bone whose top is {@code up} px above the floor, sliding at {@code hspeed}. */
    private void size(EntityManager manager, Soul soul, double xpos, double up, double hspeed) {
        SizeBone b = new SizeBone(manager, soul);
        b.x = xpos;
        b.y = G.idealborder[3] - up;
        b.hspeed = hspeed;
        b.dmg = 4;
        manager.add(b);
    }

    /**
     * A gap-corridor obstacle: a PAIR of {@link TopBone}s hanging from the ceiling and a PAIR of
     * {@link SizeBone}s rising from the floor (the "II" clusters in the reference), leaving a
     * ~48px opening centred on {@code gapMid} for the heart to glide through. All slide at
     * {@code hspeed}.
     */
    private void corridor(EntityManager manager, Soul soul, double xpos, double gapMid,
            double hspeed) {
        final double halfGap = 24;   // 48px opening
        for (double dx : new double[] { 0, 28 }) {
            TopBone top = new TopBone(manager, soul);
            top.x = xpos + dx;
            top.y = gapMid - halfGap;     // ceiling bone hangs DOWN to the top of the gap
            top.hspeed = hspeed;
            top.dmg = 4;
            manager.add(top);
            SizeBone bottom = new SizeBone(manager, soul);
            bottom.x = xpos + dx;
            bottom.y = gapMid + halfGap;  // floor bone rises UP to the bottom of the gap
            bottom.hspeed = hspeed;
            bottom.dmg = 4;
            manager.add(bottom);
        }
    }

    /**
     * A tall CYAN "blue" wall — a SINGLE {@link SizeBone} flagged {@code blue} (the reference's
     * blue bones are single, not paired). It rises ~90px from the floor: too tall to jump (the
     * apex is ~66px), so the heart must HOLD STILL on the floor to pass through unharmed (GML
     * blue bone: only a moving blue soul is hurt).
     */
    private void blueWall(EntityManager manager, Soul soul, double xpos, double hspeed) {
        SizeBone b = new SizeBone(manager, soul);
        b.x = xpos;
        b.y = G.idealborder[2] + 44;  // top ~y294 (well below the ceiling) — tall but unjumpable
        b.hspeed = hspeed;
        b.dmg = 4;
        b.blue = true;
        manager.add(b);
    }

    @Override
    public String onAct(int whatiheard) {
        String line = switch (whatiheard) {
            case 0 -> "* You tell PAPYRUS a joke.  * NYEH HEH HEH!";
            case 1 -> "* You ask PAPYRUS about his  puzzles.";
            case 2 -> "* You suggest SANS take a  break.  * (He looks tempted.)";
            default -> "* You ask SANS for his  judgment.";
        };
        if (whatiheard >= 0 && whatiheard < done.length && !done[whatiheard]) {
            done[whatiheard] = true;
            acts++;
        }
        if (!freed() && acts > 0) {
            line += "  * The Lost Souls are remembering...";
        }
        return line;
    }

    @Override
    public String freedLine() {
        return "NO! WAIT!! YOU'RE MY FRIEND! / nah, i'm rootin for ya, kid.";
    }

    @Override
    public String introLine() {
        return "* The Lost Souls appeared.  * (It's SANS and PAPYRUS.)";
    }
}
