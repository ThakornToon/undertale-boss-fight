package boss;

import battle.RatingsMaster;
import battle.SoulMode;
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
import core.TurnManager;
import java.awt.Graphics2D;
import java.util.List;

/**
 * Mettaton EX (GML: {@code obj_mettatonex} + {@code obj_mettb_body} +
 * {@code obj_ratingsmaster}). The show-stopper: a YELLOW shoot soul, a 20-turn
 * taunt/dance machine, and a fight you win by <b>RATINGS</b>, not HP — though HP
 * death is also a real ending.
 *
 * <p><b>Turn machine</b> (GML {@code event_user(6)} → {@code turns++}): each turn
 * sets the taunt, the {@code dancewait} tempo, the box border, and (turn 14) blows
 * his arms off; the attack is {@code attacktype = 29 + turns}. This foundation rains
 * the bomb generator every turn so the soul has something to dodge and the heart to
 * shoot; the full attacktype bank is a later phase.
 *
 * <p><b>Two endings (both reachable):</b> grind his 1600 HP to zero (gun + FIGHT) →
 * the "So I was wrong… You've been a great audience!" death (GML {@code con 50});
 * or push RATINGS past the call-in milestone → the "OOH, LOOK AT THESE RATINGS!"
 * finale where he delays his debut and stays (GML {@code con 90}).
 *
 * // GML: obj_mettatonex
 */
public final class MettatonExBoss extends Boss {

    private MettatonExBody exbody;
    private RatingsMaster ratings;

    private int hurtTimer;
    private int prevMnfight = -99;

    // ---- Ending driver (GML con 50 = HP death / con 90 = ratings victory) --
    private static final int END_NONE = 0;
    private static final int END_HP = 1;
    private static final int END_RATINGS = 2;
    private int ending;
    private int step;
    private int timer;
    private boolean exploding;
    private String[] endLines = new String[0];
    private int[] endFaces = new int[0];
    private int[] endFrames = new int[0];

    // Per-turn taunts — the user's trimmed [PA0]..[PA19] sequence (the pop-quiz turn
    // is cut), 1-indexed so turn N shows line N-1 leading into attack N-1 (image N-1).
    private static final String[] TAUNT = {
        "", // turns are 1-based
        "ABSOLUTELY beautiful!",
        "Lights! Camera! Action!",
        "Drama! Romance! Bloodshed!",
        "I'm the idol everyone craves!",
        "Smile for the camera!",
        "Why don't I show you mine?",
        "Ooooh, I'm just warming up!",
        "But how are you on the dance floor!?",
        "Can you keep up the pace!?",
        "Lights! Camera Bombs!",
        "Things are blowing up!",
        "Time for our union-regulated break!",
        "We've grown so distant, darling... How about another heart-to-heart?",
        "A.. arms? Wh... who needs arms with legs like these? I'm still going to win!",
        "Come on ...!",
        "The show ... must go on!",
        "Dr... Drama! A... Action!",
        "L... lights... C... camera... Enough of this! Do you really want humanity to perish!?",
        "Haha, how inspiring! Witness the true power of humanity's star!",
        "... then... Are YOU the star? Can you really protect humanity!?",
    };

    public MettatonExBoss(EntityManager manager) {
        // GML scr_monstersetup: HP 1600, ATK 8, DEF 1 (CHECK displays the joke 47/47).
        super(manager, new Monster(0, "Mettaton EX", 1600, 8, 1, 0));
    }

    // ---- Boss SPI ----------------------------------------------------------

    @Override
    public String musicPath() {
        return "/audio/mus_mettaton_ex.ogg";   // "Death by Glamour"
    }

    @Override
    public void setup() {
        stats.setup();

        ratings = new RatingsMaster(manager);
        manager.add(ratings);

        exbody = new MettatonExBody(manager);
        body = exbody;
        manager.add(exbody);
        exbody.soul = soul;
        // Shooting things scores ratings: a normal destroy = 5, a heart hit = 15.
        exbody.onScore = r -> ratings.pushRating("Action", r);

        // YELLOW shoot soul (GML: obj_heart.shot = 1).
        soul.setMode(SoulMode.YELLOW);
        board.setPreset(24, true);
        centerSoul();

        // GML: mercymod = -100000 (can't spare). FIGHT compensates for the missing
        // weapon-ATK system so the HP-death path is grindable (DEF is only 1). Set
        // after reset(), which clears mercymod.
        mercy.reset();
        G.mercymod = -100000;
        damage.playerDamageMultiplier = 6;
        damage.playerMinDamage = 40;

        // The RATINGS meter sits top-left; the HP bar still shows top-right like
        // every other boss, so the player can see the damage they're landing.

        // ACT: Check, Pose (Dramatic), Boast, Heel turn.
        act.setOptions(List.of("Check", "Pose", "Boast", "Heel Turn"), List.of(0, 1, 3, 4));

        G.attacktype = 26;
        turns = 0;
        message = "* Mettaton EX strikes a pose!";
        dialogue = "ABSOLUTELY beautiful!";
        // No separate intro turn — the menu opens and the show begins.
        G.mnfight = TurnManager.MENU;
        G.myfight = 0;
    }

    @Override
    public void chooseAttack() {
        if (ending != END_NONE) {
            return;
        }
        turns++;
        ratings.turns = turns;
        if (turns < TAUNT.length) {
            dialogue = TAUNT[turns];
        }

        applyTurnPresentation();

        // GML: attacktype = 29 + turns (with the late DEF-drop remaps). We compute it
        // for fidelity; the foundation dispatches the bomb generator for every turn.
        G.attacktype = 29 + turns;
        if (G.attacktype >= 50 && G.monsterdef[stats.slot] > -10) {
            G.monsterdef[stats.slot] = -10;
        }

        spawnAttackFor(turns);
        // Longer turns so slow patterns (parasols gliding from the top) fully play out;
        // turn 11 (example_10) is the short break.
        G.turntimer = turns == 11 ? 90 : 400;

        // GML obj_mettatonex: refresh the menu flavor every turn so the previous
        // player action's result text ("* You strike!" / an ACT line) never lingers
        // into the next MAIN menu.
        message = util.GMLHelper.random(100) >= 90 ? "* Smells like Mettaton." : "* Mettaton.";
        if (G.mercymod > 100) {
            message = "* Monster seems satisfied.";
        }
        if (stats.hp() <= stats.maxhp / 4) {
            message = "* Mettaton has low HP.";
        }

        G.attacked = 1;
    }

    /**
     * Dispatch this turn's bullet pattern. Turn N plays example_(N-1) — the patterns
     * ported from the reference videos: legs, parasols, plus-bombs, black-circle
     * boxes, the disco-ball lasers, the narrow-box bomb/block rows, the heart-core
     * lightning burst, and the dense finale drops. (The segmented-arm and the
     * rewind-rows beats are approximated with the closest ported pattern for now.)
     */
    private void spawnAttackFor(int turn) {
        switch (turn) {
            case 1 ->                  // ex0: the legs slide down (shoot to toggle them)
                spawn(new MettLeg2Gen(manager, soul, 70, 2.4));
            case 2 -> {                // ex1: parasols in two columns ×4 + central plus-bombs
                spawn(new ParasolGen(manager, soul, 2, 70, 1.9, 4, false));
                spawn(new MixDropGen(manager, soul, 1, 48, 3.0, 0.3, 0.7, true, false));
            }
            case 3 ->                  // ex2: right legs → centre boxes (+gap) → left legs
                spawn(new Ex2Gen(manager, soul, 2.4));
            case 4 -> {                // ex3: segmented arm (shoot the weak point) + parasols
                spawn(new SegArmGen(manager, soul, 70, 2.4));
                spawn(new ParasolGen(manager, soul, 4, 95, 1.9, 0, false));
            }
            case 5 -> {                // ex4: heart-core lightning (all directions) + parasols
                HeartCore h = new HeartCore(manager, soul, 12, 85);
                spawn(h);
                spawn(new ParasolGen(manager, soul, 2, 60, 1.9, 0, false));
            }
            case 6 ->                  // ex5: parasols scattered at random
                spawn(new ParasolGen(manager, soul, 2, 30, 2.2, 0, true));
            case 7 ->                  // ex6: disco-ball lasers (shoot to re-roll colours)
                spawn(new DiscoBall(manager, soul, 4, 1.6));
            case 8 ->                  // ex7: disco-ball, faster
                spawn(new DiscoBall(manager, soul, 4, 2.7));
            case 9 ->                  // ex8: bomb/block rows in the narrow box (baseline)
                spawn(new BombBlockGen(manager, soul, 60, 3.0));
            case 10 ->                 // ex9: = ex8 × 2
                spawn(new BombBlockGen(manager, soul, 30, 6.0));
            case 11 ->                 // ex10: the union break — no attack
                message = "* HAPPY BREAKTIME";
            case 12 -> {               // ex11: heart-core burst + spiralling boxes (revive outward)
                HeartCore h = new HeartCore(manager, soul, 14, 85);
                spawn(h);
                spawn(new OrbitRing(manager, soul, h, 8, false, 0.03));
            }
            case 13 ->                 // ex12: REC→REV rewind, ×3 wider rows, ×1.2 speed
                spawn(new RewindGen(manager, soul, 52, 3.12));
            case 14 ->                 // ex13: = ex12 × 2
                spawn(new RewindGen(manager, soul, 26, 6.24));
            case 15 ->                 // ex14: scattered plus-bombs + boxes (baseline)
                spawn(new MixDropGen(manager, soul, 2, 18, 3.0, 0.0, 1.0, true, true));
            case 16 ->                 // ex15: = ex14 × 2
                spawn(new MixDropGen(manager, soul, 2, 9, 6.0, 0.0, 1.0, true, true));
            case 17 ->                 // ex16: = ex8 × 2 (narrow box)
                spawn(new BombBlockGen(manager, soul, 30, 6.0));
            default -> {
                if (turns >= 21) {
                    randomAttack();    // the scripted show is over — keep going at random
                } else {               // ex17 + finale: heart + 2 orbiting bombs + leg walls
                    HeartCore h = new HeartCore(manager, soul, 16, 85);
                    spawn(h);
                    spawn(new OrbitRing(manager, soul, h, 2, true, 0.04));
                    spawn(new SideLegGen(manager, soul));
                }
            }
        }
    }

    /** After the 20-turn show completes, pick a random attack (with a matching box). */
    private void randomAttack() {
        int[] pool = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 14, 15, 16, 17 };
        int t = pool[(int) (Math.random() * pool.length)];
        int border = 24;
        if (t == 9 || t == 10 || t == 17) {
            border = 26;
        } else if (t == 13 || t == 14) {
            border = 27;
        }
        board.setPreset(border, true);
        centerSoul();
        spawnAttackFor(t);
    }

    /** GML event_user(6): dancewait tempo, box border, and the turn-14 arms-off. */
    private void applyTurnPresentation() {
        // dancewait ramp: the dance gets frantic across turns 7..12 (warming up →
        // dance floor → pace → bombs → blowing up → break), then slows for the finale.
        int dw = switch (turns) {
            case 7 -> 18; case 8 -> 15; case 9 -> 12; case 10 -> 9; case 11 -> 6;
            case 12 -> 3; case 13 -> 60; case 14 -> 80; case 15 -> 120; case 16 -> 180;
            case 17 -> 240; default -> 25;
        };
        exbody.dancewait = dw;

        // border 24 default; 26 (2-wide) for the bomb/block rows (ex8/9/16);
        // 27 (4-wide) for the rewind rows (ex12/13).
        int border = 24;
        if (turns == 9 || turns == 10 || turns == 17) {
            border = 26;
        } else if (turns == 13 || turns == 14) {
            border = 27;
        }
        board.setPreset(border, true);
        centerSoul();

        // GML: turn 14 — the arms blow off and the face switches to the "defeated" set.
        if (turns == 14) {
            exbody.blowOffArms();
            exbody.faceSet = true;
            G.faceemotion = 8;
        }
        if (turns >= 14 && turns <= 17) {
            exbody.faceSet = true;
            G.faceemotion = 8;
        }
        if (turns >= 18) {
            exbody.dance = -1;            // hold a pose for the desperation finale
            exbody.faceSet = true;
            G.faceemotion = turns == 20 ? 7 : 5;
        }
    }

    /** GML: turns>=19 && ratings>=10000, or turns<19 && ratings>=12000. */
    private boolean qualifies() {
        return (turns >= 19 && G.ratings >= 10000) || (turns < 19 && G.ratings >= 12000);
    }

    @Override
    public void onAct(int whatiheard) {
        switch (whatiheard) {
            case 0 -> message = "* METTATON EX - ATK 47 DEF 47  * His weak point is his  heart-shaped core.";
            case 1 -> {
                // Pose (Dramatic) — a bigger boost the lower your HP.
                ratings.addRating(RatingsMaster.DRAMATIC);
                if (G.hp <= 3) {
                    message = "* With the last of your power,  you pose dramatically.  * The audience screams.";
                } else if (G.hp < G.maxhp / 4) {
                    message = "* Despite being wounded, you  posed dramatically.  * The audience gasps.";
                } else if (G.hp <= G.maxhp / 2) {
                    message = "* Despite being hurt, you posed  dramatically.  * The audience applauds.";
                } else {
                    message = "* You posed dramatically.  * The audience nods.";
                }
            }
            case 3 -> {
                ratings.boastmode = true;
                message = "* You say you aren't going to  get hit at ALL.  * Ratings gradually increase  during Mettaton's turn.";
            }
            case 4 -> {
                ratings.heel = true;
                message = "* You turn and scoff at the  audience.  * They're rooting for your  destruction this turn!";
            }
            default -> message = "* ...";
        }
    }

    @Override
    public void onDamaged() {
        // GML hurt event: an Action rating + a hurt face on the FIGHT hit.
        ratings.addRating(RatingsMaster.ACTION);
        exbody.pause = 1;
        hurtTimer = 14;
    }

    @Override
    public void onDefeat() {
        // FIGHT reduced him to 0 HP.
        if (ending == END_NONE) {
            stats.defeat();
            beginHpDeath();
        }
    }

    // ---- Endings -----------------------------------------------------------

    private void beginHpDeath() {
        ending = END_HP;
        startEndCutscene();
        exbody.faceSet = true;     // GML: the "defeated" face set
        G.faceemotion = 0;
        endLines = new String[] {
            "H.. ha...",
            "So I was wrong.",
            "Darling...",
            "You really are strong enough to get past ASGORE.",
            "Well then...  It's time for you to go.",
            "Don't worry about me.  Dr. Alphys can always repair me.",
            "And... besides...",
            "Even if I'm not cut out to be a star...",
            "I still got to perform for a human, didn't I?",
            "So, thank you, darling...  You've been a great audience!",
        };
        endFaces = new int[]  { 0, 0, 0, 1, 0, 1, 0, 0, 1, 1 };
        endFrames = new int[] { 60, 60, 50, 95, 90, 95, 55, 80, 90, 110 };
    }

    private void beginRatingsVictory() {
        ending = END_RATINGS;
        startEndCutscene();
        exbody.endface = true;     // GML: the call-in "general" face set
        G.faceemotion = 0;
        endLines = new String[] {
            "OOH, LOOK AT THESE RATINGS!!!",
            "WE'VE REACHED THE VIEWER CALL-IN MILESTONE!",
            "ONE LUCKY VIEWER WILL HAVE THE CHANCE TO TALK TO ME...",
            "... oh... hi... mettaton... i really liked watching your show...",
            "AH... I... I SEE...",
            "EVERYONE... THANK YOU SO MUCH.",
            "PERHAPS... IT MIGHT BE BETTER IF I STAY HERE FOR A WHILE.",
            "SO... I THINK I'LL HAVE TO DELAY MY BIG DEBUT.",
            "KNOCK 'EM DEAD, DARLING.",
            "AND EVERYONE... THANK YOU. YOU'VE BEEN A GREAT AUDIENCE!",
        };
        endFaces = new int[]  { 6, 6, 8, 0, 3, 4, 1, 0, 5, 0 };
        endFrames = new int[] { 80, 80, 95, 120, 60, 70, 95, 90, 70, 110 };
    }

    /** Shared setup for both endings: freeze the loop, hide the field. */
    private void startEndCutscene() {
        step = 0;
        timer = 0;
        exploding = false;
        G.mnfight = -1;
        G.myfight = -1;
        soul.hidden = true;
        board.visible = false;
        exbody.dance = -1;
        ratings.active = false;
        dialogue = "";
        message = "";
    }

    @Override
    public void update() {
        if (hurtTimer > 0 && --hurtTimer == 0) {
            exbody.pause = 0;
        }
        // Dim the combat box during the attack turn (the YELLOW-mode darkify).
        board.borderAlpha = G.mnfight == TurnManager.ENEMY_TURN ? 0.5f : 1f;
        // Clear the previous turn's flavour text when the player's turn opens.
        if (G.mnfight == TurnManager.MENU && prevMnfight != TurnManager.MENU && ending == END_NONE) {
            message = "* Mettaton.";
        }
        prevMnfight = G.mnfight;
        // GML: a high enough ratings ends the show with the call-in finale. Checked at
        // the player's turn — chooseAttack runs inside TurnManager, which would clobber
        // the cutscene's mnfight = -1 with ENEMY_TURN.
        if (ending == END_NONE && G.mnfight == TurnManager.MENU && qualifies()) {
            beginRatingsVictory();
            return;
        }
        if (ending == END_NONE) {
            return;
        }

        timer++;
        if (step < endLines.length) {
            if (timer == 1) {
                G.faceemotion = endFaces[step];
                dialogue = endLines[step];
            }
            if (soul.confirmPressed || timer >= endFrames[step]) {
                step++;
                timer = 0;
            }
            return;
        }

        // The monologue is done: white-fade and finish.
        if (!exploding) {
            exploding = true;
            dialogue = "";
            exbody.fadewhite = true;
            if (ending == END_HP) {
                G.xp += 800;
                G.kills++;
            }
            G.flag[425] = 1;
        }
        if (exbody.fadeComplete()) {
            battleOver = true;
            peacefulEnd = ending == END_RATINGS;   // ratings = he leaves, not dies
            banner = ending == END_RATINGS
                    ? "* Mettaton delays his debut.  The show goes on."
                    : "* Mettaton EX was defeated.";
        }
    }

    @Override
    public boolean introComplete() {
        return true;
    }

    @Override
    public void render(Graphics2D g) {
        // The body + ratings meter draw themselves.
    }

    private void spawn(core.Entity e) {
        manager.add(e);
    }

    private void centerSoul() {
        soul.x = (G.idealborder[0] + G.idealborder[1]) / 2.0;
        soul.y = (G.idealborder[2] + G.idealborder[3]) / 2.0;
        soul.hspeed = 0;
        soul.vspeed = 0;
    }
}
