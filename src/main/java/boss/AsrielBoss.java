package boss;

import battle.SoulMode;
import bullet.asriel.AngelOfDeathGen;
import bullet.asriel.AsrielFireGen;
import bullet.asriel.ChaosBusterGen;
import bullet.asriel.ChaosSaberGen;
import bullet.asriel.HyperGonerGen;
import bullet.asriel.ShockerBreakerGen;
import bullet.asriel.StarBlazingGen;
import core.EntityManager;
import core.TurnManager;
import java.util.List;
import util.GMLHelper;

/**
 * Asriel Dreemurr — PART A, "The ABSOLUTE GOD of Hyperdeath" (GML: {@code obj_asrielb}
 * + {@code obj_asriel_body}). A scripted, un-winnable-by-damage gauntlet: a {@code turns}
 * counter (0→13) walks a fixed table of named attacks while {@code mercymod} is hugely
 * negative (you cannot spare or kill him). You survive turns and soften the run with
 * ACT <b>Pray</b> (hope: −2 ATK + heal) / <b>Dream</b> (heal). On {@code turns == 13} he
 * readies HYPER GONER and transforms into his final form.
 *
 * <p>Part A ports the calm Fire-Magic intro, the floating body and cosmic backdrop, and the
 * full named-attack table — STAR/GALACTA BLAZING, SHOCKER BREAKER (II), CHAOS SABER/SLICER,
 * CHAOS BUSTER/BLASTER and the HYPER GONER climax (turn 13) that transforms him into his
 * final form. Part B is the "Angel of Death" SAVE finale: token (un-loseable) barrages,
 * the struggle-to-SAVE unlock, the four Lost-Soul encounters, SAVE-ing Asriel himself, and
 * the breakdown cutscene + victory card.
 *
 * // GML: obj_asrielb (Part A controller)
 */
public final class AsrielBoss extends Boss {

    // GML SCR_BORDERSETUP presets used by Asriel.
    private static final double[] BORDER_17 = { 162, 472, 250, 385 }; // star/gun/goner (wide)
    private static final double[] BORDER_6  = { 227, 407, 250, 385 }; // calm Fire-Magic intro
    // GML SCR_BORDERSETUP border 4: a small near-square box low-centre for Chaos Saber.
    private static final double[] BORDER_4  = { 267, 367, 295, 385 };

    private AsrielBody abody;
    private AsrielBackground background;

    /** GML mypart1.specialnormal: the calm "It's the end." Fire-Magic opening phase. */
    private boolean specialnormal = true;
    /** GML hoped/dreamed: ACT escalation counters for repeat flavor lines. */
    private int hoped;
    private int dreamed;

    // ---- Transform boundary (GML mnfight==5 trcon machine) ----------------------
    private boolean transformPending;
    private boolean transforming;
    private int trStep;
    private int trTimer;
    private static final String[] TRANSFORM = {
        "... even after that attack, you're still standing in my way...?",
        "Wow... You really ARE something special.",
        "But don't get cocky.",
        "Up until now, I've only been using a fraction of my REAL power!",
        "Let's see what good your DETERMINATION is against THIS!!",
    };
    private static final int[] TRANSFORM_FRAMES = { 110, 90, 70, 120, 120 };

    // ---- Part B (final "Angel of Death" form, obj_asrielfinal) ----------------
    // The winged form's resting expression. spr_afinal_face_0 is the eyeless "screaming"
    // mouth; every other index has eyes like the Part-A Hyperdeath head, so the idle/taunt
    // face uses an eyed expression to match Part A. (face_0 is reserved for the beam climax.)
    private static final int PARTB_FACE = 1;
    private boolean partB;
    private AsrielFinalBody finalBody;
    /** The active Lost-Soul SAVE encounter, or null when fighting Asriel's form. */
    private boss.asriel.LostSoul activeSoul;
    private boolean inLostSoul;
    private BossBody soulBody;
    /** GML songcon: a centred-narration cutscene (a SAVE realization) is playing. */
    private boolean realizing;
    private int realStep;
    private int realTimer;
    private String[] cutLines;
    private int[] cutFrames;
    private Runnable cutAfter;

    // GML event_user(3): per-"death" taunt, keyed by tempvalue[12] (the struggle count).
    private static final String[] DEATH_TAUNT = {
        "Urah ha ha ha... Behold my TRUE power!",
        "Urah ha ha ha... Behold my TRUE power!",
        "I can feel it... Every time you die, your grip on this world slips away. "
                + "Every time you die, your friends forget you a little more. "
                + "Your life will end here, in a world where no one remembers you...",
        "Still, you're hanging on...? That's fine. In a few moments, you'll forget "
                + "everything, too. That attitude will serve you well in your next life!",
        "Ura ha ha... Still!? Come on... Show me what good your DETERMINATION is now!",
    };

    // GML flag[501]==0, tempvalue[12]>=4: the realization that unlocks SAVE.
    private static final String[] REALIZATION = {
        "* Can't move your body.  * Nothing happened.",
        "* You struggle...  * Nothing happened.",
        "* You tried to reach your  SAVE file.  * Nothing happened.",
        "* You tried again to reach  your SAVE file.  * Nothing happened.",
        "* Seems SAVING the game really  is impossible.",
        "* ...",
        "* But...",
        "* Maybe, with what little  power you have...",
        "* You can SAVE something else.",
    };
    private static final int[] REALIZATION_FRAMES = { 90, 90, 100, 100, 90, 40, 40, 90, 110 };

    // GML flag[501]==2, SAVE "Someone else": reaching the last SOUL — Asriel himself.
    private static final String[] ASRIEL_REALIZE = {
        "* Strangely, as your friends  remembered you...",
        "* Something else began  resonating within the SOUL,  stronger and stronger.",
        "* It seems that there's still  one last person that needs  to be saved.",
        "* But who...?",
        "* ...",
        "* Suddenly, you realize.",
        "* You reach out and call  their name.",
    };
    private static final int[] ASRIEL_REALIZE_FRAMES = { 100, 110, 110, 60, 40, 60, 110 };

    // GML flag[501]==3 breakdown — played as a CUTSCENE (no barrage): Asriel's power fades
    // and he breaks down crying, then releases everyone's SOULs.
    private static final String[] BREAKDOWN = {
        "Wh... what did you do...? What's this feeling...?",
        "Why am I crying...?",
        "... I always was a crybaby, wasn't I, Chara?",
        "... I know. You're not actually Chara, are you.",
        "I'm so alone, Frisk... I'm so afraid...",
        "... but it's okay now. I'll release everyone's SOULs.",
    };
    private static final int[] BREAKDOWN_FRAMES = { 110, 90, 140, 140, 120, 150 };
    private boolean breakingDown;
    private int bdStep;
    private int bdTimer;

    // The four SAVE entries (six friends), shown once SAVE is unlocked (flag[501]>=1).
    private static final java.util.List<String> SAVE_LABELS =
            java.util.List.of("Undyne", "Alphys", "Sans & Papyrus", "Toriel & Asgore");
    private static final java.util.List<Integer> SAVE_IDS = java.util.List.of(0, 3, 1, 2);

    /** GML: a SAVE entry's friend flag (505..508), keyed by its whatiheard id. */
    private static int friendFlagFor(int whatiheard) {
        return switch (whatiheard) {
            case 0 -> 505;      // Undyne
            case 3 -> 506;      // Alphys
            case 1 -> 507;      // Sans & Papyrus
            case 2 -> 508;      // Toriel & Asgore
            default -> 0;
        };
    }

    /** SAVE menu labels, with already-freed friends shown as "(Saved)" (canonical). */
    private java.util.List<String> saveMenuLabels() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < SAVE_LABELS.size(); i++) {
            int flag = friendFlagFor(SAVE_IDS.get(i));
            out.add(flag != 0 && G.flag[flag] == 1 ? "(Saved)" : SAVE_LABELS.get(i));
        }
        return out;
    }

    // GML Alarm[6] per-turn taunt bubble, keyed by `turns` (turn 0/1 stay silent — the
    // first two attacks in the reference clips have no dialogue; verify the exact A1/A2/
    // A3 boundary against the .mov clips in the dialogue pass).
    private static final String[] TAUNT = new String[14];
    static {
        TAUNT[2]  = "After I defeat you and gain total control over the timeline... I just want to reset everything.";
        TAUNT[3]  = "All your progress... Everyone's memories. I'll bring them all back to zero!";
        TAUNT[4]  = "Then we can do everything ALL over again.";
        TAUNT[5]  = "And you know what the best part of all this is? You'll DO it.";
        TAUNT[6]  = "And then you'll lose to me again.";
        TAUNT[7]  = "And again.";
        TAUNT[8]  = "And again!!!";
        TAUNT[9]  = "Because you want a \"happy ending.\"";
        TAUNT[10] = "Because you \"love your friends.\"";
        TAUNT[11] = "Because you \"never give up.\"";
        TAUNT[12] = "Isn't that delicious? Your \"determination.\" The power that let you get this far... It's gonna be your downfall!";
        TAUNT[13] = "Now, ENOUGH messing around! It's time to purge this timeline once and for all!";
    }

    public AsrielBoss(EntityManager manager) {
        // GML scr_monstersetup: HP 9999, ATK 8, DEF 9999 (invulnerable — "no powerful
        // enemy"); you cannot damage or kill him.
        super(manager, new Monster(0, "Asriel", 9999, 8, 9999, 0));
    }

    // ---- Boss SPI ----------------------------------------------------------

    @Override
    public String musicPath() {
        return "/audio/mus_xpart.ogg";   // "Hopes and Dreams" — Part A gauntlet
    }

    @Override
    public void setup() {
        stats.setup();

        background = new AsrielBackground(manager);
        manager.add(background);

        abody = new AsrielBody(manager);
        body = abody;
        manager.add(abody);

        // RED soul, free movement, in the wide box. The calm intro plays first.
        soul.setMode(SoulMode.RED);
        enterBox(BORDER_6);
        centerSoul();

        // GML mercymod = -99999999999999: spare can never resolve (the mercy meter is
        // never driven, so MERCY always "blocks the way").
        mercy.reset();
        mercy.setMercyMod(-999999999);

        // You cannot damage him: DEF 9999 already nullifies FIGHT; hide the HP meter so
        // a frozen 9999 bar doesn't read as a real health gauge.
        hideHpHeader = true;

        // GML: ACT options — Check (0), Hope (3), Dream (1). (Internally "pray"; the
        // menu shows "Hope" to match the canonical fight.)
        act.setOptions(List.of("Check", "Hope", "Dream"), List.of(0, 3, 1));

        // GML flags for the Asriel battle.
        G.flag[500] = 1;
        G.hope = 0;
        G.faceemotion = 0;
        turns = G.flag[504] - 3;     // resume near where a prior entry left off
        if (turns < 0) {
            turns = 0;
        }

        message = "* ASRIEL DREEMURR";
        // Asriel attacks first (the calm Fire-Magic intro), then the player gets a menu.
        G.mnfight = TurnManager.ENTER_ENEMY;
        G.myfight = -1;
    }

    @Override
    public void chooseAttack() {
        if (inLostSoul) {
            soul.setMode(activeSoul.soulMode());
            double[] b = activeSoul.border();
            enterBox(b);
            centerSoul();
            activeSoul.chooseAttack(manager, soul);
            message = "* ...";
            dialogue = "";
            G.attacked = 1;
            return;
        }
        if (partB) {
            partBChooseAttack();
            return;
        }
        // GML mnfight==3: hope is consumed for this turn's damage, then reset.
        int atk = (G.hope == 1) ? 6 : 8;
        G.monsteratk[stats.slot] = atk;
        G.hope = 0;

        if (specialnormal) {
            // Calm Fire-Magic intro — plain black backdrop, a light fire drizzle the
            // player can simply wait out, then the cosmic gauntlet begins.
            abody.startCast(false);  // calm, arms down
            background.active = false;
            enterBox(BORDER_6);
            centerSoul();
            G.faceemotion = 0;       // calm during the intro
            G.turntimer = 140;
            // GML: the calm intro is Asriel's fire magic copied from Toriel — a weaving
            // helix of white flames (the same spr_firebullet helix, on the black backdrop).
            manager.add(new bullet.toriel.FireHelixGen(manager, soul, 7, 6, atk));
            message = "* It's the end.";
            dialogue = "";
            specialnormal = false;   // next turn → the named-attack gauntlet
            G.attacked = 1;
            return;
        }

        // Cosmic gauntlet.
        background.active = true;
        boolean hMode = turns >= 8;                       // GML: hard variants from turn 8
        boolean starTurn = turns == 0 || turns == 4 || turns == 9;
        boolean saberTurn = turns == 2 || turns == 6 || turns == 10;
        boolean gunTurn = turns == 5 || turns == 7 || turns == 11;
        boolean gonerTurn = turns == 13;
        // Chaos Saber and Chaos Buster keep the arms down — the swords / the gun-arm take over.
        abody.startCast(!saberTurn && !gunTurn);
        // Hide the body arm(s) the sword-arms / gun-arm replace (Saber: both; Buster: right).
        abody.setArmVisibility(!saberTurn, !saberTurn && !gunTurn);
        G.faceemotion = 2;               // "powering up" expression during an attack
        enterBox(saberTurn ? BORDER_4 : BORDER_17);
        centerSoul();
        // HYPER GONER opens the box to fullscreen itself, so hide the combat box.
        if (gonerTurn) {
            board.visible = false;
        }
        // GML alarm[5]: Star Blazing runs ~300f (so the big finale star lands), Shocker
        // ~180f; Saber/Buster/Goner run their own length and end the turn themselves.
        G.turntimer = (saberTurn || gunTurn || gonerTurn) ? 9999 : (starTurn ? 330 : 180);

        message = flavorFor(turns);
        dialogue = (turns >= 0 && turns < TAUNT.length && TAUNT[turns] != null) ? TAUNT[turns] : "";

        spawnAttack(turns, hMode, atk);

        if (turns == 13) {
            transformPending = true;      // HYPER GONER → transform after this turn
        } else {
            turns++;
            if (G.flag[504] < turns) {
                G.flag[504] = turns;
            }
        }
        G.attacked = 1;
    }

    /** GML turn table → body pattern selector. Real attacks where ported, else fire. */
    private void spawnAttack(int t, boolean hMode, int atk) {
        switch (t) {
            case 0, 4, 9 ->                 // STAR BLAZING / GALACTA BLAZING (turn 9 hard)
                manager.add(new StarBlazingGen(manager, soul, hMode, atk));
            case 1, 3, 8, 12 ->             // SHOCKER BREAKER / II (turns 8/12 hard)
                manager.add(new ShockerBreakerGen(manager, soul, hMode, atk));
            case 2, 6, 10 ->                // CHAOS SABER / CHAOS SLICER (turn 10 hard)
                manager.add(new ChaosSaberGen(manager, soul, board, abody, hMode, atk));
            case 5, 7, 11 ->                // CHAOS BUSTER / CHAOS BLASTER (turn 11 hard)
                manager.add(new ChaosBusterGen(manager, soul, abody, hMode, atk));
            case 13 ->                      // HYPER GONER → transform climax
                manager.add(new HyperGonerGen(manager, soul, abody, atk));
            default ->
                manager.add(new AsrielFireGen(manager, soul, hMode ? 5 : 7, atk));
        }
    }

    /** GML msg[0] "* Asriel readies ..." flavor for each turn. */
    private static String flavorFor(int t) {
        return switch (t) {
            case 0, 4 -> "* Asriel readies \"STAR BLAZING.\"";
            case 1, 3 -> "* Asriel charges \"SHOCKER  BREAKER.\"";
            case 2, 6 -> "* Asriel calls on \"CHAOS SABER.\"";
            case 5, 7 -> "* Asriel readies \"CHAOS BUSTER.\"";
            case 8, 12 -> "* Asriel readies \"SHOCKER  BREAKER II.\"";
            case 9 -> "* Asriel readies \"GALACTA  BLAZING.\"";
            case 10 -> "* Asriel calls on \"CHAOS SLICER.\"";
            case 11 -> "* Asriel readies \"CHAOS  BLASTER.\"";
            case 13 -> "* Asriel readies \"HYPER GONER.\"";
            default -> "* ...";
        };
    }

    @Override
    public void onAct(int whatiheard) {
        if (inLostSoul) {
            message = activeSoul.onAct(whatiheard);
            if (activeSoul.freed()) {
                freeLostSoul();
            }
            return;
        }
        if (partB) {
            partBOnAct(whatiheard);
            return;
        }
        switch (whatiheard) {
            case 0 -> message = specialnormal
                    ? "* ASRIEL DREEMURR  * Legendary being made of every  SOUL in the underground."
                    : "* ASRIEL DREEMURR  * The Absolute GOD of  Hyperdeath!";
            case 3 -> pray();
            case 1 -> dream();
            default -> message = "* You execute some action.";
        }
    }

    /** GML whatiheard==3: PRAY → hope (damage cut next turn) + small heal. */
    private void pray() {
        G.hope = 1;
        message = hoped > 0
                ? "* You kept holding on.  * DAMAGE reduced!"
                : "* You held on to your hopes...  * You reduced how much DAMAGE  you'll take this turn!";
        hoped++;
        if (G.hp < G.maxhp) {
            G.hp++;
            util.Audio.play("/audio/snd_heal_c.wav");
        }
    }

    /** GML whatiheard==1: DREAM → heal +4 (the "Last Dream" inventory fill is cut). */
    private void dream() {
        message = dreamed > 0
                ? "* Your items fill up with  dreams."
                : "* You think about why you're  here now...  * You can feel the empty space  in your inventory get smaller!";
        dreamed++;
        if (G.hp < G.maxhp) {
            G.hp = Math.min(G.maxhp, G.hp + 4);
            util.Audio.play("/audio/snd_heal_c.wav");
        }
    }

    @Override
    public void onDamaged() {
        if (partB) {
            // Part B: FIGHT only MISSes; trying to act struggles toward the SAVE unlock.
            if (G.flag[501] == 0) {
                struggle();
            } else {
                message = "* ...";
            }
            return;
        }
        // GML: he cannot be hurt (DEF 9999). FIGHT just glances off.
        message = "* Your attack does nothing.";
    }

    @Override
    public void update() {
        if (realizing) {
            updateRealization();
            return;
        }
        if (inLostSoul && activeSoul != null) {
            activeSoul.update(manager, soul);   // e.g. YELLOW player-bullets
            return;
        }
        if (partB) {
            // Once Asriel himself is SAVED, the fight ends in a breakdown CUTSCENE — the
            // player is never forced to dodge a scripted barrage here.
            if (breakingDown) {
                updateBreakdown();
            }
            return;     // other Part-B turns/menu are driven by chooseAttack / onAct
        }
        // Idle pose between turns: drop the cast, return the calm face.
        if (G.mnfight == TurnManager.MENU && !transforming) {
            abody.endCast();
            G.faceemotion = 0;
        }
        if (transforming) {
            updateTransform();
            return;
        }
        // After the HYPER GONER turn ends and control returns to the menu, begin the
        // transform into the final form.
        if (transformPending && G.mnfight == TurnManager.MENU) {
            startTransform();
        }
    }

    private void startTransform() {
        transformPending = false;
        transforming = true;
        trStep = 0;
        trTimer = 0;
        G.mnfight = -1;             // frozen cutscene
        soul.hidden = true;
        board.visible = false;
        G.faceemotion = 0;
        abody.endCast();
    }

    private void updateTransform() {
        if (trStep < TRANSFORM.length) {
            if (trTimer == 0) {
                dialogue = TRANSFORM[trStep];
            }
            if (++trTimer >= TRANSFORM_FRAMES[trStep] || soul.confirmPressed) {
                trStep++;
                trTimer = 0;
            }
            return;
        }
        // The transform is complete — swap to the final winged form and begin Part B.
        dialogue = "";
        enterPartB();
    }

    // ---- Part B (final form / SAVE) ----------------------------------------

    /** Swap the God form for the winged Angel of Death and begin the SAVE phase. */
    private void enterPartB() {
        partB = true;
        transforming = false;
        manager.destroy(abody);             // remove the God-of-Hyperdeath body
        finalBody = new AsrielFinalBody(manager);
        body = finalBody;
        manager.add(finalBody);
        background.active = true;
        background.dark = true;            // Part B: black backdrop + dim starfield
        util.Audio.loop("/audio/mus_a2.ogg");   // GML: batmusic → "Burn in Despair!" (flag[501]==0)

        // Part B: you can't spare/kill; attacks are token (dmg 1) and you cannot die.
        G.monstermaxhp[stats.slot] = 9999;
        G.monsterhp[stats.slot] = 9999;
        G.monsterdef[stats.slot] = 9999;
        mercy.reset();
        mercy.setMercyMod(-999999999);
        G.faceemotion = PARTB_FACE;
        G.flag[501] = 0;
        G.tempvalue[12] = 0;
        // Do NOT refill HP on the Part-B transform — the player keeps whatever HP they had.
        // (The fight is still un-loseable: onLethalDamage refills with "But it refused.", and
        // the SAVE-unlock moment restores HP canonically — but the transform itself must not.)

        soul.setMode(SoulMode.RED);
        soul.hidden = false;
        enterBox(BORDER_17);
        centerSoul();

        // Stage 0: the only meaningful action is "Check", which struggles toward SAVE.
        act.setOptions(List.of("Check"), List.of(0));

        message = "* The whole world is ending.";
        G.mnfight = TurnManager.ENTER_ENEMY;    // the final form attacks first
        G.myfight = -1;
    }

    /** A Part-B enemy turn: token bullets + the per-"death" taunt / resonance line. */
    private void partBChooseAttack() {
        G.faceemotion = PARTB_FACE;
        enterBox(BORDER_17);
        centerSoul();
        G.turntimer = 160;
        // GML: the final form flings pastel comet-streaks that curve in from the wings
        // ("the whole world is ending"); dmg 1 and you cannot die (onLethalDamage) — this is
        // the struggle-to-SAVE phase. Volleys of ~4–7 big comets every ~32 frames.
        manager.add(new AngelOfDeathGen(manager, soul, 1, 32));

        if (G.flag[501] == 0) {
            int d = GMLHelper.clamp(G.tempvalue[12], 0, DEATH_TAUNT.length - 1);
            dialogue = DEATH_TAUNT[d];
            message = "* The whole world is ending.";
        } else {
            dialogue = "";
            message = resonanceLine();
        }
        G.attacked = 1;
    }

    /** GML: the "* You feel ... resonating within ASRIEL" line by friends-freed count. */
    private String resonanceLine() {
        int total = G.flag[505] + G.flag[506] + G.flag[507] + G.flag[508];
        return switch (total) {
            case 1 -> "* You feel something faintly  resonating within ASRIEL.";
            case 2 -> "* You feel something  resonating within ASRIEL.";
            case 3 -> "* You feel something strongly  resonating within ASRIEL.";
            case 4 -> "* You feel your friends' SOULs  resonating within ASRIEL!";
            default -> "* ...";
        };
    }

    private void partBOnAct(int whatiheard) {
        if (G.flag[501] == 0) {
            if (G.tempvalue[12] >= 4) {
                startRealization();
            } else {
                struggle();
            }
            return;
        }
        if (G.flag[501] == 2) {
            // SAVE "Someone else": reach the last SOUL — Asriel himself.
            startCutscene(ASRIEL_REALIZE, ASRIEL_REALIZE_FRAMES, this::onAsrielRealized);
            return;
        }
        // flag[501] == 1: a SAVE entry was chosen. Map it to its friend flag (505..508).
        int friendFlag = friendFlagFor(whatiheard);
        if (friendFlag == 0) {
            message = "* ...";
            return;
        }
        if (G.flag[friendFlag] == 1) {
            message = "* That SOUL already  remembers you.";
            return;
        }
        switch (whatiheard) {
            case 0 -> enterLostSoul(new boss.asriel.LostSoulUndyne());
            case 3 -> enterLostSoul(new boss.asriel.LostSoulAlphys());
            case 1 -> enterLostSoul(new boss.asriel.LostSoulSkelebros());
            case 2 -> enterLostSoul(new boss.asriel.LostSoulDreemurrs());
            default -> message = "* ...";
        }
    }

    /** SAVE → a Lost-Soul encounter: take over the screen with the friend's silhouette. */
    private void enterLostSoul(boss.asriel.LostSoul ls) {
        inLostSoul = true;
        activeSoul = ls;
        finalBody.hidden = true;
        soulBody = ls.createBody(manager);
        manager.add(soulBody);
        background.active = false;          // Lost Souls play on black
        soul.setMode(ls.soulMode());
        enterBox(ls.border());
        centerSoul();
        act.setOptions(ls.actLabels(), ls.actIds());
        message = ls.introLine();
        dialogue = "";
    }

    /** The friend remembered you: set the flag, free them, return to the SAVE menu. */
    private void freeLostSoul() {
        G.flag[activeSoul.flag] = 1;
        dialogue = activeSoul.freedLine();
        message = "* You SAVED " + activeSoul.name + "!";
        manager.destroy(soulBody);
        soulBody = null;
        finalBody.hidden = false;
        background.active = true;
        soul.setMode(SoulMode.RED);
        // All six friends freed → the SAVE-Asriel stage opens ("Someone else").
        int total = G.flag[505] + G.flag[506] + G.flag[507] + G.flag[508];
        if (total >= 4 && G.flag[501] < 2) {
            G.flag[501] = 2;
            act.setOptions(List.of("Someone else"), List.of(0));
        } else {
            act.setOptions(saveMenuLabels(), SAVE_IDS);
        }
        inLostSoul = false;
        activeSoul = null;
    }

    /** GML: "Can't move your body." — each attempt edges toward the SAVE realization. */
    private void struggle() {
        G.tempvalue[12]++;
        message = "* Can't move your body.  * Nothing happened.";
    }

    private void startRealization() {
        startCutscene(REALIZATION, REALIZATION_FRAMES, this::onSaveUnlocked);
    }

    /** A centred-narration cutscene (the SAVE realizations); {@code after} runs at the end. */
    private void startCutscene(String[] lines, int[] frames, Runnable after) {
        cutLines = lines;
        cutFrames = frames;
        cutAfter = after;
        realStep = 0;
        realTimer = 0;
        realizing = true;
        G.mnfight = -1;
        soul.hidden = true;
        dialogue = "";
        // The realization text reads INSIDE the wide box (border 17) — give it room.
        enterBox(BORDER_17);
    }

    private void updateRealization() {
        if (cutLines != null && realStep < cutLines.length) {
            if (++realTimer >= cutFrames[realStep] || soul.confirmPressed) {
                realStep++;
                realTimer = 0;
            }
            return;
        }
        realizing = false;
        Runnable a = cutAfter;
        cutAfter = null;
        if (a != null) {
            a.run();
        }
    }

    /** SAVE unlocked: HP restored, the ACT button becomes SAVE, the friend list opens. */
    private void onSaveUnlocked() {
        G.flag[501] = 1;
        G.hp = G.maxhp;
        act.setOptions(saveMenuLabels(), SAVE_IDS);
        soul.hidden = false;
        message = "* !?!?";
        G.mnfight = TurnManager.MENU;
        util.Audio.play("/audio/snd_heal_c.wav");
        util.Audio.loop("/audio/mus_xpart_2.ogg");  // GML: batmusic → "SAVE the World"
    }

    /**
     * flag[501]==2: SAVE "Someone else" reaches the last SOUL — Asriel himself. His power
     * fades and the fight ends in a breakdown CUTSCENE — no barrage, no forced dodging.
     */
    private void onAsrielRealized() {
        G.flag[501] = 3;
        soul.hidden = true;
        board.visible = false;
        if (finalBody != null) {
            finalBody.cry = 1;
        }
        background.dark = true;
        breakingDown = true;
        bdStep = 0;
        bdTimer = 0;
        dialogue = BREAKDOWN[0];
        message = "* ...";
        G.mnfight = -1;          // frozen cutscene — the player is not forced to act
        G.myfight = -1;
    }

    /** GML flag[501]==3: Asriel breaks down crying (cutscene), then the victory card. */
    private void updateBreakdown() {
        if (bdStep < BREAKDOWN.length) {
            if (bdTimer == 0) {
                dialogue = BREAKDOWN[bdStep];
                if (finalBody != null) {
                    // Tears well up, then he sobs openly for the final lines.
                    finalBody.cry = bdStep >= BREAKDOWN.length - 2 ? 2 : 1;
                }
            }
            if (++bdTimer >= BREAKDOWN_FRAMES[bdStep] || soul.confirmPressed) {
                bdStep++;
                bdTimer = 0;
            }
            return;
        }
        dialogue = "";
        startVictory();
    }

    /** The short victory card (per the chosen ending): you saved everyone, the barrier breaks. */
    private void startVictory() {
        dialogue = "";
        soul.hidden = true;
        board.visible = false;
        battleOver = true;
        peacefulEnd = true;
        banner = "* You SAVED ASRIEL.  * The barrier is broken.  * Everyone is free.";
        G.mnfight = -1;
    }

    @Override
    public boolean wantsSaveButton() {
        // SAVE replaces ACT on Asriel's own turns; inside a Lost-Soul encounter you ACT.
        return partB && G.flag[501] >= 1 && !inLostSoul;
    }

    @Override
    public boolean onLethalDamage() {
        // GML: the WHOLE Asriel fight is un-loseable. "But it refused." applies in both
        // Part A (partB==false) and Part B (until the breakdown, flag[501]==3, ends combat).
        if (G.flag[501] < 3) {
            G.hp = G.maxhp;
            message = "* But it refused.";
            return true;
        }
        return false;
    }

    @Override
    public void render(java.awt.Graphics2D g) {
        // The SAVE realizations are shown as centred narration.
        if (realizing && cutLines != null && realStep < cutLines.length) {
            renderNarration(g, cutLines[realStep]);
        }
    }

    /** Draw the SAVE-realization lines INSIDE the combat box, wrapped and top-left aligned
     *  (GML battle text) — never floating/overflowing above the box. */
    private void renderNarration(java.awt.Graphics2D g, String text) {
        double[] b = G.idealborder;
        g.setFont(util.Fonts.ui(24f));
        g.setColor(java.awt.Color.WHITE);
        java.awt.FontMetrics fm = g.getFontMetrics();
        int textX = (int) b[0] + 26;
        int top = (int) b[2] + 34 + fm.getAscent();
        int lineH = 32;
        int maxChars = Math.max(8, (int) ((b[1] - b[0] - 52) / (fm.charWidth('m') * 0.62)));
        int line = 0;
        // GML uses "  " to start a new bulleted line; wrap each to the box width.
        for (String seg : text.split("  ")) {
            if (seg.isBlank()) {
                continue;
            }
            for (String row : wrapNarration(seg.trim(), maxChars)) {
                g.drawString(row, textX, top + line * lineH);
                line++;
            }
        }
    }

    /** Greedy word-wrap to fit a narration line inside the box width. */
    private static java.util.List<String> wrapNarration(String text, int maxChars) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            if (cur.length() > 0 && cur.length() + 1 + word.length() > maxChars) {
                lines.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) {
                cur.append(' ');
            }
            cur.append(word);
        }
        if (cur.length() > 0) {
            lines.add(cur.toString());
        }
        return lines;
    }

    // ---- helpers -----------------------------------------------------------

    private void enterBox(double[] b) {
        board.instaBorder(b[0], b[1], b[2], b[3]);
        board.visible = true;
    }

    /** GML: recentre the heart in the box at the start of each enemy turn. */
    private void centerSoul() {
        soul.x = (G.idealborder[0] + G.idealborder[1]) / 2.0;
        soul.y = (G.idealborder[2] + G.idealborder[3]) / 2.0;
        soul.vspeed = 0;
        soul.hspeed = 0;
    }
}
