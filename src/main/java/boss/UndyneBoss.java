package boss;

import battle.SoulMode;
import bullet.undyne.FollowSpear2Gen;
import bullet.undyne.FollowSpearGen;
import bullet.undyne.RiseSpearGen;
import bullet.undyne.RotSpearGen;
import bullet.undyne.SpearBlocker;
import core.EntityManager;
import core.TurnManager;
import java.util.List;

/**
 * Undyne the Undying — the NORMAL-route, head-on fight (GML: {@code obj_undyneboss} +
 * {@code obj_undyneb_body}). She opens with "En guarde!" and a GREEN-soul shield
 * tutorial, escalating her taunt every turn ({@code order} 1→24) while alternating
 * GREEN shield turns with RED dodge windows at her three rage points:
 *
 * <ul>
 *   <li><b>order 1–5</b> GREEN shield (spear-block tutorial)</li>
 *   <li><b>order 6–8</b> RED follow-spears — "NGAHHH! Enough warming up!"</li>
 *   <li><b>order 9–11</b> GREEN shield</li>
 *   <li><b>order 12–14</b> RED rising-spears — "So STOP being so damn resilient!"</li>
 *   <li><b>order 15–21</b> GREEN shield</li>
 *   <li><b>order 22–24</b> RED rising-spears — "DIE ALREADY, YOU LITTLE BRAT!"</li>
 * </ul>
 *
 * <p>She cannot be spared ({@code mercymod} hugely negative). When her HP hits zero
 * she does <b>not</b> die: she melts, refuses ("NO! I won't die! ... I WILL DEFEAT
 * YOU!") and <b>reforms</b> into a short second phase ({@code order} −40→−35) of
 * harmless spears, then finally melts away for good ("...Alphys... This is what I was
 * afraid of... I WON'T DIE!").
 *
 * <p>The GML HP-gate jumps (75%/50%/20% → order 6/12/22) are kept, so dealing damage
 * faster escalates her sooner; otherwise the order simply advances one per turn. FIGHT
 * damage is compensated (as Asgore's is) so her 1500 HP is grindable from LV1.
 *
 * // GML: obj_undyneboss (+ obj_undyneb_body)
 */
public final class UndyneBoss extends Boss {

    // Combat boxes (GML SCR_BORDERSETUP 12/13/14, hand-tuned to the references).
    private static final double[] GREEN_BOX = { 247, 393, 175, 321 };
    private static final double[] FOLLOW_BOX = { 240, 400, 165, 331 };
    // Rising-spears: a narrow box (GML border 12 = room/2 ± 40, 80px wide) so the 3
    // columns sit tight together and you can't just slip off to the side to dodge.
    private static final double[] RISE_BOX = { 280, 360, 216, 322 };

    private UndyneBody ubody;

    // ---- GML controller state ----------------------------------------------
    private int order = 1;       // dialogue / phase index
    private int lesson = 1;      // green-table selector
    private int ratingb;         // red firing-rate ramp
    private double rating = 12;  // green spacing/difficulty
    private boolean green = true;
    private boolean died;        // reformed into the second phase
    private boolean phase2;
    private boolean finalDeath;  // phase-2 last stand reached → trigger from update()
    private boolean died2;       // final-death cutscene started (guard)
    private double dim;          // GML darkify: field dims to gray during the attack

    // ---- GENOCIDE route (obj_undyne_ex) -------------------------------------
    private boolean genocide;
    private UndyneXBody uxbody;
    private boolean genGreen = true;   // current phase
    private int genTurnsLeft = 3;      // turns left in the current phase
    private int genLesson = -5;        // descending genocide green tables (-5..-14)
    private int orderb;                // red-generator cycle (0..7)
    private boolean genoDead;          // genocide death cutscene started

    // ---- Cutscene driver (intro / reform / death) ---------------------------
    private List<String> cutLines;
    private int[] cutFaces;
    private int cutIdx;
    private int cutTimer;
    private Runnable cutThen;
    private boolean introDone;

    public UndyneBoss(EntityManager manager) {
        // GML scr_monstersetup: HP 1500 (flag[351]), ATK 50, DEF 20.
        super(manager, new Monster(0, "Undyne", 1500, 50, 20, 1500));
    }

    // ---- Boss SPI ----------------------------------------------------------

    @Override
    public String musicPath() {
        // setup() has already resolved the route, and BattleScene calls this after
        // setup(): GENOCIDE = "Battle Against a True Hero", NORMAL = "Spear of Justice".
        return genocide ? "/audio/mus_x_undyne.ogg" : "/audio/mus_undyneboss.ogg";
    }

    @Override
    public void setup() {
        stats.setup();

        // GML: the genocide fight is obj_undyne_ex (the spikier UNDYING form).
        genocide = G.murderlv >= 7;

        if (genocide) {
            uxbody = new UndyneXBody(manager);
            body = uxbody;
            manager.add(uxbody);
            // GML "UNDYNE THE UNDYING 99 ATK 99 DEF" with a huge HP pool, killable only
            // because player damage is ×21 (min 600). ~20 FIGHT hits.
            G.monstermaxhp[stats.slot] = 15000;
            G.monsterhp[stats.slot] = 15000;
        } else {
            ubody = new UndyneBody(manager);
            body = ubody;
            manager.add(ubody);
        }

        soul.setMode(SoulMode.GREEN);
        enterBox(GREEN_BOX);
        board.visible = false;

        // GML mercymod hugely negative: she can never be spared.
        mercy.reset();
        mercy.setMercyMod(-9_999_999);

        // FIGHT compensation: ×21 (Asgore-style). NORMAL floors at 70 so her 1500 HP is
        // grindable from LV1; GENOCIDE uses the GML min of 600.
        damage.playerDamageMultiplier = 21;
        damage.playerMinDamage = genocide ? 600 : 70;

        G.faceemotion = 0;
        G.mnfight = TurnManager.SETUP;
        message = "* Undyne blocks the way!";

        if (genocide) {
            // GENOCIDE: no per-turn dialogue — just the opener, then the gauntlet.
            act.setOptions(List.of("Check"), List.of(0));
            playCut(new String[] {
                    "You're gonna have to try a little harder than THAT!",
                    "* Undyne attacks!" }, new int[] { 0, 0 }, this::startFirstTurn);
        } else {
            // ACT: Check, Challenge (raise difficulty), Plead (lower it).
            act.setOptions(List.of("Check", "Challenge", "Plead"), List.of(0, 1, 3));
            playCut(new String[] { "En guarde!", "* Undyne attacks!" },
                    new int[] { 0, 0 }, this::startFirstTurn);
        }
    }

    private void startFirstTurn() {
        introDone = true;
        green = true;
        order = 1;
        lesson = 1;
        dialogue = "";
        board.visible = true;
        G.mnfight = TurnManager.ENTER_ENEMY;   // she attacks first
        G.myfight = -1;
    }

    @Override
    public boolean introComplete() {
        return introDone;
    }

    @Override
    public void chooseAttack() {
        if (genocide) {
            chooseGenocide();
            return;
        }
        if (phase2) {
            choosePhase2();
            return;
        }
        applyHpGates();
        green = isGreenOrder(order);

        // Her expression escalates with the rage points.
        G.faceemotion = order >= 22 ? 5 : order >= 12 ? 4 : order >= 6 ? 1 : 0;
        dialogue = taunt(order);
        message = flavorNormal();   // GML global.msg[0] flavour for the next menu

        if (green) {
            startGreenTurn(lesson);
        } else if (order <= 8) {
            startFollowTurn();
        } else {
            startRiseTurn();
        }

        order++;
        lesson++;
        G.attacked = 1;
    }

    /** GML order schedule: GREEN except the three RED rage windows. */
    private static boolean isGreenOrder(int o) {
        boolean red = (o >= 6 && o <= 8) || (o >= 12 && o <= 14) || (o >= 22 && o <= 24);
        return !red;
    }

    /** GML: dealing damage fast jumps her straight to the next rage point. */
    private void applyHpGates() {
        int hp = stats.hp();
        int max = stats.maxhp;
        if (hp < max * 0.75 && lesson < 5) {
            lesson = 6;
            order = 6;
        }
        if (hp < max * 0.5 && lesson < 11) {
            lesson = 11;
            order = 12;
        }
        if (hp < max * 0.2 && lesson < 20) {
            lesson = 20;
            order = 22;
            rating += 2;
        }
    }

    private void startGreenTurn(int lessonNo) {
        soul.setMode(SoulMode.GREEN);
        enterBox(GREEN_BOX);
        G.turntimer = 300;          // the blocker ends the turn when its queue empties
        SpearBlocker blocker = new SpearBlocker(manager, soul);
        blocker.lesson = lessonNo;
        blocker.rating = rating;
        blocker.dmg = 7;                 // GML obj_blockbullet: dmg 7
        // The blue finisher only comes on the last GREEN turn before a RED window.
        blocker.fireFinisher = !isGreenOrder(order + 1);
        manager.add(blocker);
    }

    private void startFollowTurn() {
        soul.setMode(SoulMode.RED);
        enterBox(FOLLOW_BOX);
        ratingb = Math.min(9, ratingb + 1);
        int firingrate = 19 - ratingb;
        G.firingrate = firingrate;
        G.turntimer = 240;
        manager.add(new FollowSpearGen(manager, soul, firingrate, 7));
    }

    private void startRiseTurn() {
        soul.setMode(SoulMode.RED);
        enterBox(RISE_BOX);
        ratingb = Math.min(9, ratingb + 1);
        int firingrate = 23 - ratingb;
        G.firingrate = firingrate;
        G.turntimer = 220;
        manager.add(new RiseSpearGen(manager, soul, firingrate, 7));
    }

    /** GML order table (1..24+), cleaned of control codes. */
    private static String taunt(int o) {
        return switch (o) {
            case 1 -> "As long as you're GREEN you CAN'T ESCAPE! Unless you learn to "
                    + "face danger head-on... You won't last a SECOND against ME!";
            case 2 -> "Not bad! Then how about THIS!?";
            case 3 -> "For years, we've dreamed of a happy ending...";
            case 4 -> "And now, sunlight is just within our reach!";
            case 5 -> "I won't let you snatch it away from us!";
            case 6 -> "NGAHHH! Enough warming up!";
            case 7 -> "Heh... You're tough!";
            case 8 -> "But even if you could beat me...";
            case 9 -> "No human has EVER made it past ASGORE!";
            case 10 -> "Honestly, killing you now is an act of mercy...!";
            case 11 -> "...";
            case 12 -> "So STOP being so damn resilient!";
            case 13 -> "What the hell are humans made out of!?";
            case 14 -> "Anyone else would be DEAD by now!";
            case 15 -> "Alphys told me humans were determined...";
            case 16 -> "I see now what she meant by that!";
            case 17 -> "But I'm determined, too!";
            case 18 -> "Determined to end this RIGHT NOW!";
            case 19 -> "... RIGHT NOW!";
            case 20 -> "... RIGHT... ... ... NOW!!";
            case 21 -> "Ha... Ha...";
            case 22 -> "NGAHHH!!! DIE ALREADY, YOU LITTLE BRAT!";
            case 23 -> "YOU'RE GETTING IN MY WAY!";
            case 24 -> "I WILL NOT BE DEFEATED!";
            default -> "...";
        };
    }

    // GML normal-route menu flavour (global.msg[0]), random by HP bracket.
    private static final String[] FLAVOR_HIGH = {
        "* Undyne points heroically  towards the sky.",
        "* Undyne flips her spear  impatiently.",
        "* Undyne flashes a menacing  smile.",
        "* Undyne draws her finger  across her neck.",
        "* Undyne towers threateningly.",
        "* Undyne thinks of her friends  and pounds the ground.",
        "* Smells like sushi.",
    };
    private static final String[] FLAVOR_LOW = {
        "* The wind is howling...",
        "* Undyne flips her spear  impatiently.",
        "* Flower pollen drifts in  front of you.",
        "* Water rushes around you.",
        "* The spears pause for a  moment.",
    };
    private static final String[] FLAVOR_RAGE = {
        "* Undyne's eye is twitching  involuntarily.",
        "* Undyne is smashing spears  on the ground.",
        "* Undyne is hyperventilating.",
        "* Smells like angry fish.",
    };

    private String flavorNormal() {
        String[] set = order > 22 ? FLAVOR_RAGE
                : stats.hp() >= stats.maxhp / 2 ? FLAVOR_HIGH : FLAVOR_LOW;
        return set[util.GMLHelper.irandom(set.length - 1)];
    }

    // ---- Phase 2 (after the reform) -----------------------------------------

    private void choosePhase2() {
        // GML second phase: harmless GREEN spears (dmg 1) while she's "dying".
        G.faceemotion = order <= -37 ? 7 : 8;
        if (order >= -35) {
            // Her last stand is over. The final-death cutscene can't be started from
            // here — TurnManager overwrites mnfight right after chooseAttack — so flag
            // it and let update() (which runs before the turn machine) fire it.
            finalDeath = true;
            G.turntimer = 9999;     // hold the (empty) turn until the cutscene starts
            return;
        }
        dialogue = phase2Taunt(order);
        message = phase2Flavor(order);   // GML msg[0] for the next menu (per order)
        soul.setMode(SoulMode.GREEN);
        enterBox(GREEN_BOX);
        G.turntimer = 300;
        SpearBlocker blocker = new SpearBlocker(manager, soul);
        blocker.lesson = lesson;     // -40..-36 → the dmg=1 finale tables
        blocker.rating = 12;
        blocker.dmg = 1;
        manager.add(blocker);
        order++;
        lesson++;
        G.attacked = 1;
    }

    // GML obj_undyneboss: the second-phase menu flavor, keyed by order (-40..-36).
    private static String phase2Flavor(int o) {
        return switch (o) {
            case -40 -> "* Undyne is smiling as if  nothing is wrong.";
            case -39 -> "* Undyne's body is wavering.";
            case -38 -> "* Undyne's body is losing  its shape.";
            case -37 -> "* Undyne's body...";
            default  -> "* ...";
        };
    }

    private static String phase2Taunt(int o) {
        return switch (o) {
            case -40 -> "Come on, is that all you've got!?";
            case -39 -> "... pathetic. You're going to have to try harder than that!";
            case -38 -> "S-see how strong we are when we believe in ourselves?";
            case -37 -> "H... heh... Had enough yet?";
            case -36 -> "... I won't... ... give up...";
            default -> "...";
        };
    }

    // ---- GENOCIDE attack schedule (obj_undyne_ex) ---------------------------

    // GENOCIDE red boxes: a big arena for the rings/barrage; rise reuses the tall box.
    private static final double[] GENO_BIG = { 130, 510, 95, 375 };

    /** GML: alternates GREEN shield runs with RED dodge runs, no dialogue. */
    private void chooseGenocide() {
        dialogue = "";
        message = "* The wind is howling...";    // GML obj_undyne_ex flavour
        if (genTurnsLeft <= 0) {
            genGreen = !genGreen;
            genTurnsLeft = genGreen ? 3 : 4;
        }
        genTurnsLeft--;
        if (genGreen) {
            startGenoGreen();
        } else {
            startGenoRed();
        }
        G.attacked = 1;
    }

    private void startGenoGreen() {
        soul.setMode(SoulMode.GREEN);
        enterBox(GREEN_BOX);
        G.turntimer = 300;
        SpearBlocker blocker = new SpearBlocker(manager, soul);
        blocker.lesson = genLesson;     // descending genocide tables (-5..-14)
        blocker.rating = 9;
        blocker.dmg = 10;
        // genTurnsLeft is already decremented; 0 means the next turn switches to RED.
        blocker.fireFinisher = genTurnsLeft == 0;
        manager.add(blocker);
        genLesson--;
        if (genLesson < -14) {
            genLesson = -5;
        }
    }

    /** GML orderb cycle: 0/6 follow · 1/7 rise · 2/3 rot-ring · 4 spiral barrage · 5 rot-ring. */
    private void startGenoRed() {
        soul.setMode(SoulMode.RED);
        ratingb = Math.min(10, ratingb + 1);
        switch (orderb) {
            case 0, 6 -> {
                enterBox(GENO_BIG);
                G.turntimer = 240;
                int fr = Math.max(2, 18 - ratingb);
                G.firingrate = fr;
                manager.add(new FollowSpearGen(manager, soul, fr, 11));
            }
            case 1, 7 -> {
                enterBox(RISE_BOX);
                G.turntimer = 220;
                int fr = Math.max(2, 23 - ratingb);
                G.firingrate = fr;
                manager.add(new RiseSpearGen(manager, soul, fr, 11));
            }
            case 4 -> {
                enterBox(GENO_BIG);
                G.turntimer = 400;
                manager.add(new FollowSpear2Gen(manager, soul, 11));
            }
            case 5 -> {
                enterBox(GENO_BIG);
                G.turntimer = 215;
                manager.add(new RotSpearGen(manager, soul, 1, 12));
            }
            default -> {     // 2, 3
                enterBox(GENO_BIG);
                G.turntimer = 215;
                manager.add(new RotSpearGen(manager, soul, 0, 12));
            }
        }
        orderb++;
        if (orderb >= 8) {
            orderb = 4;
        }
    }

    // ---- GENOCIDE death (obj_undyne_ex con 50→71) ---------------------------

    private void beginGenocideDeath() {
        genoDead = true;
        G.mnfight = -1;
        soul.hidden = true;
        board.visible = false;
        playCut(new String[] {
                "Damn it...",
                "So even THAT power... It wasn't enough...?",
                "...",
                "Heh...",
                "Heheheh...",
                "If you...",
                "If you think I'm gonna give up hope, you're wrong.",
                "'Cause I've... Got my friends behind me.",
                "And with that power...",
        }, null, this::startGenoMelt);
    }

    private void startGenoMelt() {
        if (uxbody != null) {
            uxbody.fadeOut = true;       // she melts away
        }
        playCut(new String[] { "This world will live on...!" }, null, this::finishGenoDeath);
    }

    private void finishGenoDeath() {
        stats.defeat();
        battleOver = true;
        banner = "* UNDYNE THE UNDYING fades away.";
        G.mnfight = -1;
    }

    // ---- Defeat → reform → death --------------------------------------------

    @Override
    public void onDefeat() {
        if (genocide) {
            if (!genoDead) {
                beginGenocideDeath();
            }
            return;
        }
        if (phase2 || died) {
            return;                 // phase-2 death is scripted, not HP-driven
        }
        beginReform();
    }

    /** GML con 50→56: she melts, refuses, and reforms for round two. */
    private void beginReform() {
        died = true;
        G.mnfight = -1;             // freeze: no menu, no attacks
        soul.hidden = true;
        board.visible = false;
        G.faceemotion = 8;
        ubody.startMelt();
        playCut(new String[] {
                "Ngahhh...",
                "You were stronger... Than I thought...",
                "So then... ... this is where... ... it ends...",
                "...",
                "No...",
                "NO!",
                "I won't die!",
                "Alphys... Asgore... Papyrus...",
                "Everyone is counting on me to protect them!",
                "NNNNGAH!",
                "Human!",
                "In the name of everybody's hopes and dreams...",
                "I WILL DEFEAT YOU!",
        }, null, this::startPhase2);
    }

    private void startPhase2() {
        phase2 = true;
        order = -40;
        lesson = -40;
        // Reform: the melted body is gone — build a fresh one and give her a high HP
        // pool so FIGHT lands but never kills (the second-phase death is scripted).
        manager.destroy(ubody);
        ubody = new UndyneBody(manager);
        body = ubody;
        manager.add(ubody);
        G.monsterhp[stats.slot] = 99999;
        G.faceemotion = 0;
        soul.hidden = false;
        board.visible = true;
        message = "* Undyne looks determined.";
        dialogue = "";
        G.mnfight = TurnManager.ENTER_ENEMY;
        G.myfight = -1;
    }

    /** GML con 60→73: the final melt, "I WON'T DIE!", then she vaporizes. */
    private void beginFinalDeath() {
        soul.hidden = true;
        board.visible = false;
        ubody.startMelt();
        G.faceemotion = 7;
        playCut(new String[] {
                "...",
                "Ha... ha...",
                "... Alphys...",
                "This is what I was afraid of...",
                "This is why I never told you...",
                "...",
                "No... No!",
                "Not yet!",
                "I won't die!",
                "NGAHHHHHHHH!!!",
                "I WON'T DIE!",
                "I WON'T DIE!",
                "I WON'T DIE!",
                "I WON'T",
        }, null, this::finishDeath);
    }

    private void finishDeath() {
        stats.defeat();
        manager.destroy(ubody);     // she has vaporized — no body behind the banner
        battleOver = true;
        banner = "* Undyne fades away.  \"This world will live on...!\"";
        G.mnfight = -1;
    }

    // ---- ACT ----------------------------------------------------------------

    @Override
    public void onAct(int whatiheard) {
        if (genocide) {
            message = "* UNDYNE THE UNDYING - 99ATK  99DEF  * Heroine reformed by her own  DETERMINATION to save Earth.";
            return;
        }
        switch (whatiheard) {
            case 0 -> message = "* UNDYNE - ATK 50 DEF 20  * The heroine that NEVER  gives up.";
            case 1 -> challenge();
            case 3 -> plead();
            default -> message = "* ...";
        }
    }

    /** GML whatiheard 1: CHALLENGE — makes her attacks harder (lowers green spacing). */
    private void challenge() {
        if (rating > 6) {
            rating -= 1;
            if (ratingb < 6) {
                ratingb++;
            }
            message = "* You tell UNDYNE her attacks  are too easy.  * The bullets get faster.";
        } else {
            message = "* You tell UNDYNE her attacks  are too easy.  * She doesn't care.";
        }
    }

    /** GML whatiheard 3: PLEAD — softens her attacks a little. */
    private void plead() {
        if (ratingb > 6 || rating < 12) {
            rating += 1;
            if (ratingb > 6) {
                ratingb--;
            }
            message = "* You told Undyne you just  want to be friends.  * Her attacks became a little  less extreme.";
        } else {
            message = "* You told Undyne you didn't  want to fight.  * But nothing happened.";
        }
    }

    // ---- Cutscene driver ----------------------------------------------------

    private void playCut(String[] lines, int[] faces, Runnable then) {
        cutLines = List.of(lines);
        cutFaces = faces;
        cutIdx = 0;
        cutTimer = 0;
        cutThen = then;
    }

    private boolean cutActive() {
        return cutLines != null;
    }

    private void updateCut() {
        if (cutIdx >= cutLines.size()) {
            List<String> done = cutLines;
            Runnable then = cutThen;
            cutLines = null;
            cutThen = null;
            if (then != null) {
                then.run();
            }
            return;
        }
        if (cutTimer == 0) {
            String line = cutLines.get(cutIdx);
            // "* ..." lines are box notifications; spoken lines pop the bubble.
            if (line.startsWith("*")) {
                message = line;
                dialogue = "";
            } else {
                dialogue = line;
            }
            if (cutFaces != null && cutIdx < cutFaces.length) {
                G.faceemotion = cutFaces[cutIdx];
            }
        }
        int hold = Math.max(45, cutLines.get(cutIdx).length() * 3);
        boolean skip = soul.confirmPressed && cutTimer > 6;
        if (++cutTimer >= hold || skip) {
            cutIdx++;
            cutTimer = 0;
        }
    }

    @Override
    public void update() {
        // Fire the scripted final death here (not from chooseAttack, whose mnfight
        // write TurnManager would clobber). update() runs before the turn machine.
        if (finalDeath && !died2) {
            died2 = true;
            G.mnfight = -1;
            beginFinalDeath();
        }
        if (cutActive()) {
            updateCut();
        }

        // GML darkify: the field fades to gray while she is attacking (the enemy
        // turn), and back to full bright for the player menu.
        double target = G.mnfight == TurnManager.ENEMY_TURN ? 1 : 0;
        dim = util.GMLHelper.approach(dim, target, 0.08);
        if (ubody != null) {
            ubody.dim = dim * 0.6;            // body → ~0.4 alpha (reference gray)
        }
        if (uxbody != null) {
            uxbody.dim = dim * 0.6;
        }
        if (board != null) {
            board.borderAlpha = (float) (1 - dim * 0.55); // box frame → ~0.45 gray
        }
    }

    @Override
    public void onSpecial() {
        // Undyne has no mnfight==5 transform; reform is driven by the cutscene.
    }

    @Override
    public void render(java.awt.Graphics2D g) {
        // The body draws Undyne; the spear engine and board draw themselves.
    }

    /** Snap the combat box to a preset {L, R, T, B} and re-centre the soul in it. */
    private void enterBox(double[] box) {
        board.instaBorder(box[0], box[1], box[2], box[3]);
        centerSoul();
    }

    private void centerSoul() {
        soul.x = (G.idealborder[0] + G.idealborder[1]) / 2.0;
        soul.y = (G.idealborder[2] + G.idealborder[3]) / 2.0;
        soul.vspeed = 0;
        soul.hspeed = 0;
    }
}
