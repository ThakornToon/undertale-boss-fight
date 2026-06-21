package boss;

import battle.SoulMode;
import bullet.toriel.FireHelixGen;
import bullet.toriel.HandBullet;
import core.EntityManager;
import core.TurnManager;
import java.util.List;
import util.GMLHelper;

/**
 * Toriel (GML: {@code obj_torielboss} + {@code obj_1sidegen} + the {@code blt_*fire}
 * /{@code blt_handbullet} family). The first boss — a long fire-and-hands gauntlet
 * with two ways out:
 *
 * <ul>
 *   <li><b>The kill route.</b> A FIGHT hit chips her 440 HP; the moment she drops to
 *       {@code HP ≤ 150} her DEF collapses to {@code −140}, so the next blow almost
 *       always kills (the classic "you only meant to hit her once more" trap). Her
 *       death monologue plays, and the fight is won by force.</li>
 *   <li><b>The spare route.</b> She can't be spared by the button ({@code mercymod =
 *       −20000}); instead you <em>refuse to fight</em> — end turns without damaging
 *       her. Each refusal advances a {@code conversation} counter and a guilt-trip
 *       line; after enough of them she stops attacking, pleads with you, and finally
 *       relents — won by mercy.</li>
 * </ul>
 *
 * <p><b>This port does not let Toriel hold back.</b> The vanilla Faltering attack
 * ({@code blt_avoidfire}) and all of her low-HP damage softening / early-turn-end
 * logic are cut (see {@code TORIEL.md §0}): her bullets always deal full damage and
 * the player can die. Only the <em>spare</em> route's Faltering visuals are dropped —
 * once the conversation is far enough along she simply stops attacking.
 *
 * <p>Each enemy turn rolls {@code mycommand} (0–100) to pick one of five attacks; the
 * vertical fire columns use the tall box (border 7) and the hand sweeps use the wide,
 * short box (border 6).
 *
 * // GML: obj_torielboss
 */
public final class TorielBoss extends Boss {

    // GML SCR_BORDERSETUP: border 6 (hand sweeps, short) and 7 (fire columns, tall).
    private static final double BOX_L = 227;
    private static final double BOX_R = 407;
    private static final double BOX_T6 = 250;   // border 6 — wide/short box
    private static final double BOX_T7 = 200;   // border 7 — taller box
    private static final double BOX_B = 385;

    private TorielBody tbody;

    /** GML: refuse-to-fight progress; drives the dialogue and the spare ending. */
    private int conversation;
    /** GML: hplastturn — her HP at the start of the previous turn (refusal check). */
    private int hplastturn;
    /** GML: mycommand — this turn's rolled attack id (0–100). */
    private int mycommand;
    /** GML: scr_murderlv() >= 1 — one-hit genocide kill with the cold death line. */
    private boolean genocide;

    // ---- Hurt shudder (GML alarm[3]) --------------------------------------------
    private int hurtMag;
    private int shudderSign = 1;
    private boolean reallyHurt;

    // ---- Endgame monologue driver (spare relent OR kill death) ------------------
    private boolean ending;
    private boolean spareEnding;
    private int lineIdx;
    private int lineTimer;
    private List<String> endLines;
    private List<String> endSprites;   // parallel: pose per line ("" = keep current)

    public TorielBoss(EntityManager manager) {
        // GML scr_monstersetup (monstertype 10): HP 440, ATK 6, DEF 1, no XP/gold reward.
        super(manager, new Monster(0, "TORIEL", 440, 6, 1, 0));
    }

    // ---- Boss SPI ----------------------------------------------------------------

    @Override
    public String musicPath() {
        return "/audio/mus_toriel.ogg";   // "Heartache"
    }

    @Override
    public void setup() {
        stats.setup();

        // GML: a genocide run sets DEF -9999 in scr_monstersetup — she dies in one hit.
        genocide = G.murderlv >= 1;
        if (genocide) {
            G.monsterdef[stats.slot] = -9999;
        }

        // NORMAL has player ATK 0 (this port has no weapon-ATK system), so a raw FIGHT
        // chips only ~1 off her 440 HP — effectively unkillable by grinding. Scale it so
        // a perfectly-centred hit lands a max of 42 (≈10 hits to wear her down, fewer once
        // HP ≤ 150 collapses her DEF), the same min-damage compensation Asgore/Mettaton use.
        // (Genocide is unaffected — DEF -9999 already makes every hit a one-shot kill.)
        damage.playerDamageMultiplier = 2;
        damage.playerMinDamage = 42;

        tbody = new TorielBody(manager);
        body = tbody;
        manager.add(tbody);

        // GML mercymod = -20000: the SPARE button never lights, but it stays on the
        // command row — pressing it (refusing to fight) is how you spare her.
        mercy.reset();
        mercy.setMercyMod(-20000);

        soul.setMode(SoulMode.RED);
        board.instaBorder(BOX_L, BOX_R, BOX_T6, BOX_B);
        board.visible = true;
        centerSoul();

        // ACT: CHECK and TALK — flavor only (talking is "not the solution" with Toriel),
        // but both still spend the turn without damaging her, so they advance the spare
        // route exactly like pressing MERCY does.
        act.setOptions(List.of("Check", "Talk"), List.of(0, 3));

        message = "* TORIEL bars the way!";
        hplastturn = stats.hp();
        conversation = 0;
        turns = 0;

        // GML: the player takes the first turn — open straight onto the command menu.
        G.mnfight = TurnManager.SETUP;
    }

    @Override
    public void chooseAttack() {
        turns++;

        // GML alarm[6]: a turn that left her HP untouched is a refusal — advance the
        // conversation and (until she stops attacking) show a guilt-trip line.
        boolean refused = stats.hp() == hplastturn;
        if (refused) {
            conversation++;
        }
        hplastturn = stats.hp();

        // GML: conversation >= 13 → she stops attacking and runs the relent dialogue.
        // (Vanilla switched to Faltering here; that attack is cut in this port.)
        if (conversation >= 13 && !genocide) {
            beginRelent();
            return;
        }

        // GML alarm[6]/alarm[5]: re-roll the attack and pick the box. Vertical fire
        // (mycommand < 40) or a panicking player (hp < 3) uses the taller border 7.
        mycommand = (int) Math.round(GMLHelper.random(100));
        boolean tall = mycommand < 40 || G.hp < 3;
        board.instaBorder(BOX_L, BOX_R, tall ? BOX_T7 : BOX_T6, BOX_B);
        centerSoul();

        int atk = G.monsteratk[stats.slot];
        if (mycommand <= 20) {
            G.turntimer = 140;
            manager.add(new FireHelixGen(manager, soul, 7, 5, atk));   // fire helix column
        } else if (mycommand <= 40) {
            G.turntimer = 140;
            manager.add(new FireHelixGen(manager, soul, 8, 2, atk));   // mini helix
        } else if (mycommand <= 60) {
            G.turntimer = 140;
            manager.add(new FireHelixGen(manager, soul, 10, 6, atk));  // helix + side floats
        } else if (mycommand <= 80) {
            twoHands();                                                // two sweeping hands
        } else {
            oneHand();                                                 // single hand
        }

        // GML msg[0] action captions, escalating with mycommand.
        message = caption(mycommand);
        // The guilt-trip line shows in the speech bubble while she attacks.
        dialogue = refused && conversation >= 1 ? guiltLine(conversation) : "";

        G.attacked = 1;
    }

    /** GML mycommand 60–80: two hands sweep in, both dropping converging chase fire. */
    private void twoHands() {
        G.turntimer = 200;
        HandBullet h1 = new HandBullet(manager, soul, 1, 2);   // x1=1 → drops chasefire2
        manager.add(h1);
        HandBullet h2 = new HandBullet(manager, soul, 2, 2);
        h2.setHand1(h1);                                        // waits on hand1's sweep
        manager.add(h2);
    }

    /** GML mycommand 80–100: a lone hand dropping self-homing chase fire. */
    private void oneHand() {
        G.turntimer = 200;
        manager.add(new HandBullet(manager, soul, 1, 1));      // drops chasefire1
    }

    @Override
    public void onAct(int whatiheard) {
        switch (whatiheard) {
            case 0 -> message = "* TORIEL - ATK 80 DEF 80  * Knows best for you.";
            case 3 -> message = "* You couldn't think of any  conversation topics.";
            default -> message = "* ...";
        }
    }

    @Override
    public void onDamaged() {
        if (ending) {
            return;
        }
        // GML alarm[3]: the hurt shudder + sprite swap (reallyhurt on a big hit).
        reallyHurt = G.damage > 100;
        hurtMag = reallyHurt ? 32 : 16;
        shudderSign = 1;
        // GML alarm[3]: a real hit resets the early refusal counter — you can't both
        // fight her and talk her down.
        if (conversation < 4) {
            conversation = 0;
        }
        // GML Step hurtanim==2: at HP <= 150 her DEF collapses (the lethal trap).
        if (stats.hp() <= 150 && G.monsterdef[stats.slot] > -140) {
            G.monsterdef[stats.slot] = -140;
        }
    }

    @Override
    public void onDefeat() {
        stats.defeat();
        if (!ending) {
            beginDeath();
        }
    }

    // ---- Spare relent ending (GML conversation 13 → 25) -------------------------

    private void beginRelent() {
        ending = true;
        spareEnding = true;
        lineIdx = 0;
        lineTimer = 0;
        G.mnfight = -1;          // frozen: no menu, no enemy turn — just the dialogue
        soul.hidden = true;
        board.visible = false;
        G.mercy = 1;
        endLines = List.of(
                "... ...",
                "I know you want to go home,  but...",
                "But please... go upstairs now.",
                "I promise I will take good  care of you here.",
                "I know we do not have much,  but...",
                "We can have a good life here.",
                "Why are you making this so  difficult?",
                "Please, go upstairs.",
                ".....",
                "Ha ha...",
                "Pathetic, is it not? I cannot  save even a single child.",
                "...",
                "No, I understand.",
                "You would just be unhappy  trapped down here.",
                "The RUINS are very small once  you get used to them.",
                "It would not be right for you  to grow up in a place like  this.",
                "My expectations... My  loneliness... My fear...",
                "For you, my child... I will put  them aside.");
        endSprites = List.of(
                "spr_torielboss_side_0",
                "spr_torielboss_sad_0",
                "spr_torielboss_sadhappy_0",
                "spr_torielboss_sadhappy_0",
                "spr_torielboss_sadhappy_0",
                "spr_torielboss_sadhappy_0",
                "spr_torielboss_sad_0",
                "spr_torielboss_sidesad_0",
                "spr_torielboss_sidesad2_0",
                "spr_torielboss_sidesadhappy_0",
                "spr_torielboss_sidesadhappy_0",
                "spr_torielboss_sidesad_0",
                "spr_torielboss_neutral_0",
                "spr_torielboss_neutral_0",
                "spr_torielboss_neutral_0",
                "spr_torielboss_neutral_0",
                "spr_torielboss_neutral_0",
                "spr_torielboss_neutral_0");
    }

    // ---- Kill ending (GML destroyed == 1 death monologue) -----------------------

    private void beginDeath() {
        ending = true;
        spareEnding = false;
        lineIdx = 0;
        lineTimer = 0;
        G.mnfight = -1;
        soul.hidden = true;
        board.visible = false;
        hurtMag = 0;
        tbody.shudderX = 0;

        if (genocide || conversation > 13) {
            // GML flag[202]>=20 / genocide: the cold, betrayed death line.
            endLines = List.of(
                    "Y... you... really hate me  that much?",
                    "Now I see who I was protecting  by keeping you here.",
                    "Not you...",
                    "But them!",
                    "Ha... ha...");
            endSprites = List.of(
                    "spr_torielboss_murdered_0", "spr_torielboss_murdered_0",
                    "spr_torielboss_murdered_0", "spr_torielboss_murdered_0",
                    "spr_torielboss_murdered_0");
        } else {
            endLines = List.of(
                    "Urgh...",
                    "You are stronger than I  thought...",
                    "Listen to me, small one...",
                    "If you go beyond this door,  keep walking as far as you  can.",
                    "Eventually you will reach an  exit.",
                    ".....",
                    "Do not let ASGORE take your  soul.",
                    "His plan cannot be allowed to  succeed.",
                    "Be good, won't you?",
                    "My child.");
            endSprites = List.of(
                    "spr_torielboss_kneel_0", "spr_torielboss_kneel_0",
                    "spr_torielboss_kneel_0", "spr_torielboss_kneel_0",
                    "spr_torielboss_kneel_0", "spr_torielboss_kneel_0",
                    "spr_torielboss_kneel_0", "spr_torielboss_kneel_0",
                    "spr_torielboss_kneelsmile_0", "spr_torielboss_kneelsmile_0");
        }
    }

    // ---- Per-frame ---------------------------------------------------------------

    @Override
    public void update() {
        if (ending) {
            updateEnding();
            return;
        }
        // Hurt shudder: a damped horizontal shake while she recovers from a hit.
        if (hurtMag > 0) {
            shudderSign = -shudderSign;
            tbody.shudderX = shudderSign * hurtMag;
            hurtMag -= 2;
            tbody.spriteName = reallyHurt ? "spr_torielboss_reallyhurt_0"
                    : "spr_torielboss_hurt_0";
            if (hurtMag <= 0) {
                tbody.shudderX = 0;
            }
        } else {
            tbody.spriteName = "spr_torielboss_0";
        }
    }

    /** Advance the relent / death monologue: a beat per line, Z to skip ahead. */
    private void updateEnding() {
        if (lineIdx >= endLines.size()) {
            // The last line has had its moment — close out the fight.
            battleOver = true;
            peacefulEnd = spareEnding;
            banner = spareEnding
                    ? "* You spared TORIEL.  She steps aside and lets you pass."
                    : "* TORIEL was defeated.";
            return;
        }
        // Re-assert the frozen cutscene every frame: the relent begins inside
        // chooseAttack, where TurnManager would otherwise flip us back to ENEMY_TURN.
        G.mnfight = -1;
        soul.hidden = true;
        if (lineTimer == 0) {
            dialogue = endLines.get(lineIdx);
            String pose = endSprites.get(lineIdx);
            if (pose != null && !pose.isEmpty()) {
                tbody.spriteName = pose;
            }
        }
        lineTimer++;
        boolean advance = soul.confirmPressed || lineTimer >= lineFrames(endLines.get(lineIdx));
        if (advance) {
            lineIdx++;
            lineTimer = 0;
        }
    }

    @Override
    public void render(java.awt.Graphics2D g) {
        // The body (TorielBody) draws Toriel; the controller draws nothing itself.
    }

    /** Longer lines linger longer; "..." beats are short. */
    private static int lineFrames(String line) {
        return Math.max(60, Math.min(150, 36 + line.length() * 2));
    }

    // ---- Captions / dialogue (GML msg[0] tables) --------------------------------

    private static String caption(int mycommand) {
        if (mycommand >= 90) {
            return "* Toriel takes a deep breath.";
        }
        if (mycommand >= 70) {
            return "* Toriel is acting aloof.";
        }
        if (mycommand >= 30) {
            return "* Toriel looks through you.";
        }
        return "* Toriel prepares a magical  attack.";
    }

    /** GML alarm[6] conversation table (the guilt-trip escalation, 1–12). */
    private static String guiltLine(int c) {
        return switch (c) {
            case 1 -> ".....";
            case 2 -> "..... .....";
            case 3 -> "..... ..... .....";
            case 4 -> "...?";
            case 5 -> "What are you doing?";
            case 6 -> "Attack or run away!";
            case 7 -> "What are you proving this  way?";
            case 8 -> "Fight me or leave!";
            case 9 -> "Stop it.";
            case 10 -> "Stop looking at me that way.";
            case 11 -> "Go away!";
            default -> "...";
        };
    }

    /** GML: recentre the heart in the box at the start of each enemy turn. */
    private void centerSoul() {
        soul.x = (G.idealborder[0] + G.idealborder[1]) / 2.0;
        soul.y = (G.idealborder[2] + G.idealborder[3]) / 2.0;
        soul.vspeed = 0;
        soul.hspeed = 0;
    }
}
