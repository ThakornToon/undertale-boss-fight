package boss;

import battle.Soul;
import battle.SoulMode;
import bullet.Bullet;
import bullet.bones.SizeBone;
import bullet.bones.TopBone;
import core.EntityManager;
import core.TurnManager;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Papyrus (GML: {@code obj_papyrusboss}). The first boss. Displayed "ATK 20 DEF
 * 20", but his attacks barely scratch and you cannot win by FIGHTing — the fight is
 * a turn-counted bone gauntlet that ends when Papyrus runs out of attacks, has his
 * "special attack" stolen by the Annoying Dog, falls back on a "regular attack",
 * and finally grants MERCY. Introduces the BLUE SOUL (gravity + jump).
 *
 * <p>The {@code fighto} counter (−1 → 17) selects each turn's hand-authored bone
 * layout and self-advances, exactly as in GML's {@code mnfight==2} attack block.
 * Core's {@link TurnManager} calls {@link #chooseAttack()} on entering the enemy
 * turn. Every bone layout below is transcribed coordinate-for-coordinate from
 * {@code obj_papyrusboss} (enumb 3); the per-turn dialogue is Papyrus's actual
 * {@code global.msg[0]} lines.
 *
 * <p>The Annoying-Dog special (GML {@code blt_tobydogbone} + the {@code bonetalk}
 * cutscene driven by alarms/OBJ_WRITER) plays its real sprite: see
 * {@link AnnoyingDog}, sequenced from the speech queue in {@link #syncDogPhase()}.
 * APPROX: the closing {@code blt_scootdog}/{@code blt_coolbus}/{@code blt_superbone}
 * set pieces are still rendered with the standard bone bullet (no dedicated sprites
 * yet); the dialogue and bone walls are faithful. Multi-line cutscenes sequence
 * through a simple per-turn speech queue (no Writer object exists yet).
 *
 * // GML: obj_papyrusboss (controller + bone patterns)
 */
public final class PapyrusBoss extends Boss {

    /** GML: truefight (0 pre-fight banter, 1 the real bone gauntlet). */
    private int truefight;
    /** GML: fighto — the per-turn bone-layout selector (−1 → 17). */
    private int fighto = -1;
    /** GML: xfight — special-attack windup sub-counter (fighto 14). */
    private int xfight;
    /** GML: insult / hotcha — ACT accumulators (flavor only; ACT never escalates). */
    private int insult;
    private int hotcha;
    /** Tracks whether Papyrus has offered MERCY (GML: bonetalk3 → mercymod 8000). */
    private boolean spareOffered;
    /** GML: murder — set when scr_murderlv() >= 7 (the genocide route). */
    private boolean murder;

    /** GML: event_user(3) + bonetalk4 — the decapitation death cutscene is playing. */
    private boolean dying;
    private int deathTimer;
    /** Frames the head falls before Papyrus starts speaking. */
    private static final int HEAD_FALL_FRAMES = 45;
    /** Most recently spawned bone, so a layout can tweak osc/blue after spawning. */
    private Bullet last;
    /** The "regular attack" finale's giant bone — the one the soul climbs over. */
    private Bullet superBone;
    /** GML: blt_tobydogbone — the Annoying Dog cutscene prop (fighto 15). */
    private AnnoyingDog dog;

    /** This turn's spoken line(s); multi-line cutscenes step through them in {@link #update()}. */
    private final List<String> speech = new ArrayList<>();
    private int speechPos;
    private int speechTimer;
    /** Frames each cutscene line stays on screen (~3 s at 30 FPS). */
    private static final int SPEECH_FRAMES = 90;

    /** Windup lines for fighto 14 (GML: fighto==14 by xfight). */
    private static final String[] WINDUP_LINES = {
        "GIVE UP OR FACE MY...  SPECIAL ATTACK!!!",
        "YEAH!!!  VERY SOON I WILL USE MY  SPECIAL ATTACK!",
        "NOT TOO LONG AND I WILL USE THAT  SPECIAL ATTACK!!!",
        "THIS IS YOUR LAST CHANCE...  BEFORE MY SPECIAL ATTACK!!",
    };

    public PapyrusBoss(EntityManager manager) {
        super(manager, new Monster(0, "Papyrus", 680, 8, 2, 0));
    }

    private EntityManager mgr() {
        return manager;
    }

    // ---- Boss SPI ----------------------------------------------------------

    @Override
    public String musicPath() {
        return "/audio/mus_papyrus.ogg";   // "Bonetrousle"
    }

    @Override
    public void setup() {
        stats.setup();                 // GML: scr_monstersetup → 680 HP, ATK 8, DEF 2
        mercy.reset();

        // GML: mypart1 = instance_create(x, y, obj_papyrusbody).
        body = new PapyrusBody(manager);
        manager.add(body);

        // No-story boss rush: GML enters the blue-soul gauntlet when flag[67] < 0.
        // We seed that path directly so the player fights immediately.
        truefight = 1;
        fighto = -1;
        xfight = 0;

        // GML: global.border = 5 — Papyrus's combat box (SCR_BORDERSETUP preset 5).
        board.instaBorder(192, 442, 250, 385);

        // Blue soul. Soul#blueGravity uses the authentic GML launch (vspeed = -6)
        // and four-band gravity, so a full-held jump peaks at ~64 px — enough to
        // clear this pattern's tallest bones (up=60) while staying under the box's
        // ~135 px ceiling, exactly as in the original Papyrus fight.
        restBlueSoul();

        // ACT options (GML whatiheard ids): Check=0, Insult=1, Flirt=3.
        act.setOptions(List.of("Check", "Insult", "Flirt"), List.of(0, 1, 3));

        // GML: if(scr_murderlv() >= 7) { murder = 1; ... } — the genocide route.
        // Papyrus's DEF collapses to -20000 so a single FIGHT hit one-shots him, and
        // the spare cutscene gating is skipped (he can still be spared, breaking it).
        murder = G.murderlv >= 7;
        if (murder) {
            G.flag[290] = 1;
            G.monsterdef[stats.slot] = -20000;
            mercy.setMercyMod(8000);
            spareOffered = true;
        }

        message = "* Papyrus blocks the way!";
    }

    @Override
    public void chooseAttack() {
        // HP gates that jump the fight toward its finale (GML enumb 6). In the boss
        // rush you can't FIGHT him down, so normally progression is by turn count.
        int hp = stats.hp();
        if (hp <= 140 && fighto <= 14 && xfight < 4) {
            fighto = 14;
            xfight = 4;
        }
        if (hp <= 80 && fighto < 15) {
            fighto = 15;
            xfight = 0;
        }

        // GML: every enemy turn resets the blue soul to a small upward hop.
        launchBlueSoul();
        G.turntimer = 200;             // default; each layout overrides

        // Flavor line shown in the menu box (NOT the speech bubble).
        message = "* Papyrus is preparing a bone  attack.";
        if (hp < 100) {
            message = "* Papyrus is at the edge of  defeat.";
        }

        switch (fighto) {
            case -1 -> { say("BEHOLD!"); layoutMinus1(); fighto++; }
            case 0  -> { say("HOW HIGH CAN YOU JUMP?"); layout0(); fighto++; }
            case 1  -> { say("YEAH!  DON'T MAKE ME USE MY SPECIAL ATTACK!"); layout1(); fighto++; }
            case 2  -> { say("I CAN ALMOST TASTE MY FUTURE POPULARITY!!!"); layout2(); fighto++; }
            case 3  -> { say("PAPYRUS:  HEAD OF THE ROYAL GUARD!"); layout3(); fighto++; }
            case 4  -> { say("PAPYRUS:  UNPARALLELED SPAGHETTORE!"); layout4(); fighto++; }
            case 5  -> { say("UNDYNE WILL BE REALLY PROUD OF ME!!"); layout5(); fighto++; }
            case 6  -> { say("THE KING WILL TRIM A HEDGE IN THE SHAPE OF MY SMILE!!!"); layout6(); fighto++; }
            case 7  -> { say("MY BROTHER WILL ... WELL, HE WON'T CHANGE VERY MUCH."); layout7(); fighto++; }
            case 8  -> { say("I'LL HAVE LOTS OF ADMIRERS!!  BUT..."); layout8(); fighto++; }
            case 9  -> { say("HOW WILL I KNOW IF PEOPLE SINCERELY LIKE ME???"); layout9(); fighto++; }
            case 10 -> { say("SOMEONE LIKE YOU IS REALLY RARE..."); layout10(); fighto++; }
            case 11 -> { say("I DON'T THINK THEY'LL LET YOU GO..."); layout11(); fighto++; }
            case 12 -> { say("AFTER YOU'RE CAPTURED AND SENT AWAY."); layout12(); fighto++; }
            case 13 -> { say("URGH...  WHO CARES!  GIVE UP!!"); layout13(); fighto++; }
            case 14 -> windup();
            case 15 -> dogSpecial();
            case 16 -> regularAttack();
            default -> spareCutscene();   // fighto >= 17: out of attacks → MERCY
        }

        G.attacked = 1;
        turns = fighto;                // mirror into the universal counter (Boss.turns)
    }

    @Override
    public void update() {
        if (dying) {
            updateDeath();
            return;
        }
        climbStep();   // super-bone climb — a no-op until that finale bone is in play

        // Retire the Annoying-Dog prop once it has scooted off or the cutscene turn
        // ends (control returns to the player menu).
        if (dog != null && (dog.destroyed || G.mnfight == TurnManager.MENU)) {
            if (!dog.destroyed) {
                mgr().destroy(dog);
            }
            dog = null;
        }

        // Step the per-turn speech queue while Papyrus is talking (enemy turn), so
        // his multi-line cutscenes (the Annoying Dog, the MERCY offer) read in order.
        if (speech.size() <= 1 || G.mnfight == TurnManager.MENU) {
            return;
        }
        if (speechPos < speech.size() - 1 && --speechTimer <= 0) {
            speechPos++;
            speechTimer = SPEECH_FRAMES;
            dialogue = speech.get(speechPos);
        }
        // Sync the dog's animation to the line being spoken (GML: bonetalk steps).
        syncDogPhase();
    }

    /**
     * Drive the Annoying Dog through the {@code bonetalk} beats as Papyrus's lines
     * advance: gnawing the bone, then frozen in shock when he yells, then scooting
     * off with the "special attack" once he demands it back.
     */
    private void syncDogPhase() {
        if (dog == null) {
            return;
        }
        switch (speechPos) {
            case 0 -> dog.eat();          // "WHAT THE HECK!  THAT'S MY SPECIAL ATTACK!"
            case 1 -> dog.surprise(0);    // "HEY!  YOU STUPID DOG!"
            case 2 -> dog.surprise(1);    // "DO YOU HEAR ME!?  STOP MUNCHING..."
            default -> dog.scoot();       // "COME BACK HERE..." onward: it bolts
        }
    }

    /**
     * The super-bone climb (GML {@code blt_coolbus}). Once the giant bone is sweeping
     * through the box, holding the jump key against its left face raises the ceiling
     * to follow the soul up and gives it a steady upward push — so the player can
     * jump again and again to scale a bone far taller than the box can normally hold.
     * The box settles back to its resting height once the bone is gone.
     */
    private void climbStep() {
        if (superBone == null || superBone.destroyed
                || G.mnfight != TurnManager.ENEMY_TURN) {
            board.climbTop = -1;       // not the climb phase: normal box height
            return;
        }
        // Live once the bone is approaching the box (its finale), giving the player a
        // head start to climb, and only while the soul hugs its left face holding up.
        boolean boneInPlay = superBone.x < G.idealborder[1] + 400;
        boolean leftFace = soul.x < superBone.x + 20;
        if (boneInPlay && leftFace && soul.upHeld && soul.y > 50) {
            // GML: idealborder[2] = heart.y - 20 once the soul rises above the resting
            // top — the ceiling tracks it up, letting the box stretch taller.
            if (soul.y < 270) {
                board.climbTop = soul.y - 20;
            }
            // GML: a gentle sustained climb (vspeed = -2) while rising and holding up,
            // so a held jump keeps ascending instead of falling back at the apex.
            if (soul.yprevious > soul.y && soul.vspeed >= -2) {
                soul.vspeed = -2;
            }
        }
    }

    @Override
    public void onAct(int whatiheard) {
        // GML: ACTing never escalates the battle once truefight begins; flavor only.
        switch (whatiheard) {
            case 0 -> message = murder
                    ? "* PAPYRUS - ATK 3 DEF 3  * Forgettable."
                    : "* PAPYRUS - ATK 20 DEF 20  * He likes to say:  \"Nyeh heh heh!\"";
            case 1 -> {
                insult++;
                message = "* Papyrus is too busy FIGHTing  to accept your insult.";
            }
            case 3 -> {
                hotcha++;
                message = "* Papyrus is too busy FIGHTing  to flirt back.";
            }
            default -> message = "* ...";
        }
    }

    @Override
    public void onSpare() {
        // GML: Papyrus grants MERCY — the fight ends in friendship. Nobody dies, so
        // the scene shows the outcome inside the box, then heads back to the menu.
        G.flag[67] = 0;
        battleOver = true;
        peacefulEnd = true;
        banner = "YOU WON!  Papyrus believes in you.";
        G.mnfight = -1;                // freeze the turn machine
    }

    @Override
    public void onDefeat() {
        // GML: killed by FIGHT — event_user(3) pops Papyrus's head off and hides his
        // body; bonetalk4 then types his dying words one line at a time. We DON'T end
        // the fight here: the cutscene runs in update() and ends it when finished.
        stats.defeat();                // scr_monsterdefeat: award XP
        G.flag[67] = 1;                // killed outcome (GML: flag[67] = 1)
        G.kills++;

        dying = true;
        deathTimer = 0;
        G.mnfight = -1;                // freeze the turn machine; only the cutscene runs
        soul.hidden = true;
        if (body instanceof PapyrusBody pb) {
            pb.startDecap(murder);     // head detaches + falls, body fades out
        }

        // The dying speech, one sentence per beat. Genocide gets the famous
        // "I believe in you... I promise..."; neutral gets the dark gag.
        if (murder) {
            say(
                "W-WELL...  THAT'S NOT WHAT I  EXPECTED...",
                "BUT...  ST...  STILL!  I BELIEVE IN YOU!",
                "YOU CAN DO A LITTLE BETTER!",
                "EVEN IF YOU DON'T THINK SO!",
                "I...  I PROMISE..."
            );
        } else {
            say(
                "ALAS...  POOR PAPYRUS...",
                "WELL, AT LEAST I STILL HAVE  MY HEAD!"
            );
        }
        dialogue = "";                 // hold the bubble until the head finishes falling
    }

    /**
     * The decapitation cutscene (GML bonetalk4): let the head fall for a beat, then
     * type Papyrus's dying words one sentence at a time in the speech bubble; end
     * the battle once the last line has had its moment.
     */
    private void updateDeath() {
        deathTimer++;
        if (deathTimer < HEAD_FALL_FRAMES) {
            return;                    // head is still detaching/falling — no words yet
        }
        if (deathTimer == HEAD_FALL_FRAMES) {
            speechPos = 0;
            speechTimer = SPEECH_FRAMES;
            dialogue = speech.isEmpty() ? "" : speech.get(0);
            return;
        }
        if (--speechTimer > 0) {
            return;
        }
        if (speechPos < speech.size() - 1) {
            speechPos++;
            dialogue = speech.get(speechPos);
            speechTimer = SPEECH_FRAMES;
        } else {
            // The last line has had its moment — close out the fight.
            battleOver = true;
            banner = murder
                    ? "* PAPYRUS, defeated.   (GENOCIDE ROUTE)"
                    : "* You defeated Papyrus.";
        }
    }

    @Override
    public void render(Graphics2D g) {
        // Papyrus's sprite is drawn by PapyrusBody.
    }

    // ---- Dialogue queue ----------------------------------------------------

    /** Set this turn's spoken line(s). One line stays put; many step in {@link #update()}. */
    private void say(String... lines) {
        speech.clear();
        for (String l : lines) {
            if (l != null && !l.isEmpty()) {
                speech.add(l);
            }
        }
        speechPos = 0;
        speechTimer = SPEECH_FRAMES;
        dialogue = speech.isEmpty() ? "" : speech.get(0);
    }

    /** Give a multi-line cutscene enough turn time to read each line through. */
    private void timeForSpeech() {
        G.turntimer = speech.size() * SPEECH_FRAMES + 60;
    }

    // ---- Blue-soul helpers -------------------------------------------------

    /** Park the blue soul at the centre of the box, resting on the floor. */
    private void restBlueSoul() {
        soul.setMode(SoulMode.BLUE);
        soul.x = (G.idealborder[0] + G.idealborder[1]) / 2.0;
        soul.y = G.idealborder[3] - battle.Soul.HALF;
        soul.vspeed = 0;
        soul.jumpStage = Soul.GROUNDED;
    }

    /** GML: obj_heart.movement=2; vspeed=-1; jumpstage=2 — a little hop each turn. */
    private void launchBlueSoul() {
        soul.setMode(SoulMode.BLUE);
        soul.x = (G.idealborder[0] + G.idealborder[1]) / 2.0;
        soul.y = G.idealborder[3] - battle.Soul.HALF - 1;
        soul.vspeed = -1;
        soul.jumpStage = Soul.AIRBORNE;
    }

    // ---- Bone spawn helpers (relative to the box border) -------------------

    private double left(double n) {
        return G.idealborder[0] - n;   // GML: global.idealborder[0] - N
    }

    private double right(double n) {
        return G.idealborder[1] + n;   // GML: global.idealborder[1] + N
    }

    /** Spawn a bottom bone whose top is {@code up} px above the floor, sliding at {@code hspeed}. */
    private void size(double xpos, double up, double hspeed) {
        SizeBone b = new SizeBone(mgr(), soul);
        b.x = xpos;
        b.y = G.idealborder[3] - up;
        b.hspeed = hspeed;
        mgr().add(b);
        last = b;
    }

    /** Spawn a top (ceiling) bone whose bottom is {@code up} px above the floor. */
    private void top(double xpos, double up, double hspeed) {
        TopBone b = new TopBone(mgr(), soul);
        b.x = xpos;
        b.y = G.idealborder[3] - up;
        b.hspeed = hspeed;
        mgr().add(b);
        last = b;
    }

    /** GML: gen.blue = 1 on the last-spawned bone (blue bone — only hurts a moving soul). */
    private void blue() {
        if (last != null) {
            last.blue = true;
        }
    }

    /** GML: gen.osc / oscmin / oscmax on the last-spawned bone (vertical bob). */
    private void osc(double o, double min, double max) {
        if (last != null) {
            last.osc = o;
            last.oscmin = min;
            last.oscmax = max;
        }
    }

    // ---- Bone layouts (transcribed from obj_papyrusboss enumb 3) -----------

    /** fighto −1: the gentle blue-attack intro — three slow bones. */
    private void layoutMinus1() {
        G.turntimer = 200;
        size(right(30), 20, -3);
        size(right(200), 20, -3);
        size(right(370), 40, -3);
    }

    /** fighto 0: "HOW HIGH CAN YOU JUMP?" — staggered bones from the right. */
    private void layout0() {
        G.turntimer = 300;
        size(right(20), 20, -4);
        size(right(150), 40, -4);
        size(right(280), 40, -4);
        size(right(410), 40, -4);
        size(right(390), 60, -3);
        size(right(510), 60, -3);
        size(right(630), 60, -3);
    }

    /** fighto 1: a tall bone + a low run from the left. */
    private void layout1() {
        G.turntimer = 220;
        size(left(10), 60, 3);
        top(left(80), 40, 3);
        size(left(230), 20, 4);
        size(left(310), 20, 4);
        size(left(390), 20, 4);
        size(left(490), 50, 4);
        top(left(580), 40, 4);
    }

    /** fighto 2: a wall from the left with one blue bone, plus a fast trailing bone. */
    private void layout2() {
        G.turntimer = 240;
        size(left(30), 60, 3.5);
        size(left(160), 60, 3.5);
        size(left(290), 60, 3.5);
        size(left(390), 80, 3.5);
        blue();
        size(right(1120), 30, -6);
    }

    /** fighto 3: a tall pillar from the left, a top bone from the right, then a hill. */
    private void layout3() {
        G.turntimer = 150;
        size(left(40), 50, 4);
        top(left(40), 90, 4);
        top(right(140), 40, -4);
        size(left(260), 20, 4);
        size(left(280), 30, 4);
        size(left(300), 40, 4);
        size(left(320), 50, 4);
        size(left(340), 50, 4);
        size(left(360), 40, 4);
        size(left(380), 30, 4);
        size(left(400), 20, 4);
    }

    /** fighto 4: paired bottom+top "doorways" from the left, a blue bone, then from the right. */
    private void layout4() {
        G.turntimer = 240;
        size(left(40), 30, 4);
        top(left(40), 80, 4);
        size(left(60), 30, 4);
        top(left(60), 80, 4);
        size(left(170), 60, 4);
        top(left(170), 110, 4);
        size(left(190), 60, 4);
        top(left(190), 110, 4);
        size(left(320), 90, 4);
        blue();
        size(right(480), 60, -4);
        size(right(700), 30, -4);
        top(right(700), 80, -4);
        size(left(700), 30, 4);
        top(left(700), 80, 4);
    }

    /** fighto 5: rolling sine hills from the right. */
    private void layout5() {
        G.turntimer = 330;
        size(right(40), 30, -3);
        size(right(70), 45, -3);
        size(right(100), 60, -3);
        size(right(130), 45, -3);
        size(right(160), 30, -3);
        size(right(190), 15, -3);
        size(right(300), 15, -3);
        size(right(330), 30, -3);
        size(right(360), 45, -3);
        size(right(390), 60, -3);
        size(right(700), 30, -4);
        size(right(730), 45, -4);
        size(right(760), 60, -4);
        size(right(790), 45, -4);
        size(right(820), 30, -4);
        size(right(850), 15, -4);
        size(right(970), 15, -4);
        size(right(1000), 30, -4);
        size(right(1030), 45, -4);
        size(right(1060), 60, -4);
    }

    /** fighto 6: two slow low walls from both sides. */
    private void layout6() {
        G.turntimer = 200;
        size(left(10), 35, 2);
        size(left(110), 35, 2);
        size(left(210), 35, 2);
        size(right(10), 35, -2);
        size(right(110), 35, -2);
        size(right(210), 35, -2);
    }

    /** fighto 7: slow low bones squeezing in from both sides. */
    private void layout7() {
        G.turntimer = 150;
        size(left(10), 20, 2);
        size(left(110), 20, 2);
        size(left(210), 20, 2);
        size(left(310), 20, 2);
        size(right(10), 20, -2);
        size(right(110), 20, -2);
        size(right(210), 20, -2);
        size(right(310), 20, -2);
    }

    /** fighto 8: bottom+top gates rising from the right (GML bumped speed to 4.4). */
    private void layout8() {
        G.turntimer = 230;
        size(right(40), 20, -4.4);
        size(right(170), 20, -4.4);
        top(right(170), 70, -4.4);
        size(right(310), 30, -4.4);
        top(right(310), 80, -4.4);
        size(right(460), 40, -4.4);
        top(right(460), 90, -4.4);
        size(right(610), 50, -4.4);
        top(right(610), 100, -4.4);
        size(right(760), 60, -4.4);
        top(right(760), 110, -4.4);
    }

    /** fighto 9: a long descending corridor with oscillating tail (GML bumped speed to 4.2). */
    private void layout9() {
        G.turntimer = 355;
        size(right(60), 60, -4.2);
        size(right(220), 60, -4.2);
        top(right(220), 100, -4.2);
        size(right(360), 50, -4.2);
        top(right(360), 90, -4.2);
        size(right(500), 40, -4.2);
        top(right(500), 80, -4.2);
        size(right(640), 30, -4.2);
        top(right(640), 70, -4.2);
        size(right(780), 10, -4.2);
        top(right(780), 50, -4.2);
        size(right(990), 30, -4.2);
        osc(-1, -1, 30);
        top(right(990), 80, -4.2);
        osc(-1, -1, 30);
        size(right(1130), 50, -4.2);
        osc(-2, -20, 30);
        top(right(1130), 100, -4.2);
        osc(-2, -20, 30);
    }

    /** fighto 10: stacked pillars from the left, oscillating bones from the right (speed 4.2). */
    private void layout10() {
        G.turntimer = 230;
        size(left(40), 30, 4.2);
        size(left(60), 40, 4.2);
        top(left(60), 90, 4.2);
        size(left(80), 50, 4.2);
        top(left(80), 100, 4.2);
        size(left(100), 60, 4.2);
        top(left(100), 110, 4.2);
        size(left(280), 50, 4.2);
        top(left(280), 100, 4.2);
        size(left(295), 40, 4.2);
        top(left(295), 90, 4.2);
        size(left(310), 30, 4.2);
        top(left(310), 80, 4.2);
        size(right(600), 30, -4.2);
        osc(-3, -1, 60);
        size(right(620), 30, -4.2);
        osc(-3, -1, 60);
        size(right(640), 30, -4.2);
        osc(-3, -1, 60);
    }

    /** fighto 11: high blue / low white alternating bones, then a fast pair. */
    private void layout11() {
        G.turntimer = 250;
        size(right(60), 80, -4.5);
        blue();
        size(right(140), 20, -4.5);
        size(right(220), 80, -4.5);
        blue();
        size(right(300), 20, -4.5);
        size(right(380), 80, -4.5);
        blue();
        size(right(460), 20, -4.5);
        size(right(540), 80, -4.5);
        blue();
        size(right(620), 20, -4.5);
        size(right(1250), 80, -7);
        blue();
        size(right(1330), 20, -7);
    }

    /** fighto 12: a hill from the left and a fast hill from the right. */
    private void layout12() {
        G.turntimer = 200;
        size(left(60), 30, 4);
        size(left(87), 40, 4);
        size(left(114), 50, 4);
        size(left(141), 60, 4);
        size(left(168), 50, 4);
        size(left(195), 40, 4);
        size(left(222), 30, 4);
        size(right(600), 30, -6.4);
        size(right(640), 40, -6.4);
        size(right(680), 50, -6.4);
        size(right(720), 60, -6.4);
        size(right(760), 50, -6.4);
        size(right(800), 40, -6.4);
        size(right(840), 30, -6.4);
    }

    /** fighto 13: oscillating bottom bones, oscillating top bones, then mixed gates. */
    private void layout13() {
        G.turntimer = 220;
        size(right(20), 30, -4);
        osc(-3, -1, 60);
        size(right(60), 30, -4);
        osc(-3, -1, 60);
        size(right(100), 30, -4);
        osc(-3, -1, 60);
        top(right(240), 10, -4);
        osc(-3, -1, 60);
        top(right(270), 10, -4);
        osc(-3, -1, 60);
        top(right(300), 10, -4);
        osc(-3, -1, 60);
        size(right(460), 30, -4);
        top(right(460), 40, -4);
        osc(-3, -1, 40);
        size(right(580), 50, -4);
        top(right(580), 60, -4);
        osc(-3, -1, 40);
    }

    /**
     * fighto 14: the "SPECIAL ATTACK" windup. xfight counts the warnings up;
     * once {@code xfight > 3} Papyrus declares his special and the Annoying Dog
     * steals it (advance to fighto 15). Each warning turn fires an arc of bones
     * from both sides (GML's {@code mycommand < 20} windup wave).
     */
    private void windup() {
        if (xfight > 3) {
            say("BEHOLD...!  MY SPECIAL ATTACK!");
            G.turntimer = 80;
            fighto = 15;               // next turn: the dog absconds with it
            return;
        }
        say(WINDUP_LINES[xfight]);
        G.turntimer = 210;
        // GML mycommand<20: a rising arc from the left and a fast arc from the right.
        size(left(60), 30, 4);
        size(left(90), 40, 4);
        size(left(120), 50, 4);
        size(left(150), 60, 4);
        size(left(180), 50, 4);
        size(left(210), 40, 4);
        size(left(240), 30, 4);
        size(right(680), 30, -6.4);
        size(right(720), 40, -6.4);
        size(right(760), 50, -6.4);
        size(right(800), 60, -6.4);
        size(right(840), 50, -6.4);
        size(right(880), 40, -6.4);
        size(right(920), 30, -6.4);
        xfight++;
    }

    /**
     * fighto 15: the Annoying Dog steals Papyrus's special attack
     * (GML {@code blt_tobydogbone} + the {@code bonetalk} cutscene). A harmless turn
     * that plays the canonical dialogue while the dog gnaws the special-attack bone
     * in the corner of the box and scoots off with it, then rolls into the
     * "regular attack". The dog's animation is sequenced in {@link #syncDogPhase()}.
     *
     * // GML: blt_tobydogbone / bonetalk (event_user 7)
     */
    private void dogSpecial() {
        say(
            "WHAT THE HECK!  THAT'S MY SPECIAL ATTACK!",
            "HEY!  YOU STUPID DOG!",
            "DO YOU HEAR ME!?  STOP MUNCHING ON THAT BONE!!!",
            "HEY!!!  WHAT ARE YOU DOING!!!  COME BACK HERE WITH MY SPECIAL ATTACK!!!",
            "...  OH WELL.",
            "I'LL JUST USE A REALLY COOL  REGULAR ATTACK."
        );
        timeForSpeech();
        message = "* Papyrus's special attack...  the dog absconds with it.";

        // GML: instance_create(idealborder[1], idealborder[3]-40, blt_tobydogbone).
        dog = new AnnoyingDog(mgr());
        mgr().add(dog);
        syncDogPhase();                // start it gnawing on the first line

        fighto = 16;
    }

    /**
     * fighto 16: "a REALLY COOL regular attack" — the long bridge gauntlet
     * (GML fighto==15: bone walls, oscillating bones, then the scootdog / coolbus /
     * superbone procession on a 1300-frame turn). After surviving it Papyrus is out
     * of attacks, so the next turn he grants MERCY.
     *
     * // GML: blt_scootdog (636) / blt_coolbus (637) / blt_superbone (638) — drawn
     * here as standard bones (no dedicated sprites yet).
     */
    private void regularAttack() {
        say("*SIGH*  HERE'S AN ABSOLUTELY  NORMAL ATTACK.");
        message = "* Papyrus is getting ready  for a regular attack.";
        G.turntimer = 1300;

        // Opening bone walls hugging the box (GML lines 647-730).
        size(left(10), 20, 4);
        size(left(60), 20, 4);
        size(right(160), 20, -4);
        size(right(210), 20, -4);
        size(left(360), 60, 4);
        size(right(360), 60, -4);
        size(left(540), 30, 4);
        osc(-4, -1, 60);
        size(right(540), 30, -4);
        osc(-4, -1, 60);
        top(left(640), 50, 4);
        osc(-4, -1, 60);
        top(right(640), 50, -4);
        osc(-4, -1, 60);
        size(left(740), 30, 4);
        osc(-2, -1, 40);
        size(right(740), 30, -4);
        osc(-2, -1, 40);
        size(left(890), 30, 4);
        osc(-2, -1, 40);
        size(right(890), 30, -4);
        osc(-2, -1, 40);
        size(right(1090), 30, -4);
        osc(-1, -1, 30);
        size(right(1120), 30, -4);
        osc(-1, -1, 30);
        size(right(1150), 30, -4);
        osc(-1, -1, 30);
        size(left(1340), 30, 4);
        osc(-1, -1, 30);
        size(left(1370), 30, 4);
        osc(-1, -1, 30);
        size(left(1400), 30, 4);
        osc(-1, -1, 30);

        // The procession: the dog on a skateboard, the cool dudes, the cool bus,
        // and finally the SUPER BONE (GML lines 731-768). k = idealborder[1] + 1900.
        size(right(2000), 40, -5);     // scootdog
        size(right(2240), 60, -5);     // cbone
        size(right(2280), 60, -5);     // oolbone
        size(right(2500), 60, -5);     // dbone
        size(right(2540), 60, -5);     // udebone
        size(right(2220), 60, -4);     // skatebone
        for (int i = 0; i < 9; i++) {
            size(right(1910 + i * 60), 60, -3);   // coolbus (k+10 .. k+490)
        }
        size(right(2450), 240, -3);    // SUPER BONE (k+550)
        superBone = last;              // the bone the soul climbs over (blt_superbone)
        size(right(970), 20, -1);      // a slow straggler

        fighto = 17;
    }

    /**
     * fighto ≥ 17: Papyrus has run out of attacks. He concedes the fight and
     * grants the human MERCY (GML bonetalk3: {@code mercymod=8000},
     * {@code monsterdef = -hp*2}).
     */
    private void spareCutscene() {
        say(
            "WELL...!  *HUFF*  IT'S CLEAR...  YOU CAN'T!  *HUFF*  DEFEAT ME!!!",
            "YEAH!!!  I CAN SEE YOU SHAKING  IN YOUR BOOTS!!!",
            "THEREFORE I, THE GREAT PAPYRUS,  ELECT TO GRANT YOU PITY!!",
            "I WILL SPARE YOU, HUMAN!!!",
            "NOW'S YOUR CHANCE TO ACCEPT MY  MERCY."
        );
        timeForSpeech();
        message = "* Papyrus is sparing you.";
        if (!spareOffered) {
            spareOffered = true;
            mercy.setMercyMod(8000);
            G.monsterdef[stats.slot] = -stats.hp() * 2;
            banner = "* Papyrus is sparing you.  (Choose MERCY)";
        }
    }

    @Override
    public boolean introComplete() {
        return true; // no-story boss rush: open the menu immediately
    }
}
