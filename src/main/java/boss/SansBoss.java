package boss;

import battle.Soul;
import battle.SoulMode;
import core.EntityManager;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.Clip;
import util.Audio;

/**
 * Sans (GML: {@code obj_sansb}). 1 HP / 1 ATK / 1 DEF, but no FIGHT ever lands —
 * he dodges every swing, so the fight is gated purely by the turn counter
 * {@code hit_try}: a 13-turn blue-soul gauntlet, the hit_try-14 "turning point"
 * where he offers to spare you, the red-soul real battle (screenshot-referenced
 * turns 15-26: chained multi-part attacks, scripted smashers, blasters,
 * corridors), and finally the 26.x final chain → the "survive THIS" speech →
 * the Special Attack ({@code lac} 50→75 in {@link SansBody}) ending in his
 * sleep — where the only true win is reaching the fake FIGHT button
 * ({@code mercy_death}, played here as the spare ending).
 *
 * <p>A FIGHT only makes Sans dodge (the slide animation) — his turn still runs
 * its full attack, exactly as in the GML. At hit_try 14 the counter stalls — he
 * keeps sparing you — until the player either spares him back (mercy ending) or
 * attacks again (the real battle begins).
 *
 * // GML: obj_sansb (controller; dialogue from enumb 6, phase gates from Step)
 */
public final class SansBoss extends Boss {

    /** Frames each spoken line stays up (~3 s at 30 FPS). */
    public static final int SPEECH_FRAMES = 90;

    /** One spoken line plus its face state ({@code \E} code / {@code flag[20]}). */
    public static final class Line {
        final int face;
        final int torso;
        final String text;

        Line(int face, int torso, String text) {
            this.face = face;
            this.torso = torso;
            this.text = text;
        }
    }

    private static Line l(int face, String text) {
        return new Line(face, 0, text);
    }

    private static Line lt(int face, int torso, String text) {
        return new Line(face, torso, text);
    }

    // ---- Battle intro (GML obj_sansb con 2-12 → obj_sansb_body fac 19) ----------
    private static final Line[] INTRO_LINES = {
        l(4, "it's a beautiful day outside."),
        l(4, "birds are singing, flowers are blooming..."),
        l(4, "on days like these, kids like you..."),
        l(5, "Should be burning in hell."),
    };
    static final Line[] OPENING_HUH = {
        l(1, "here we go."),
    };

    /** [After final attack] — played once the 26.x chain ends, before the special. */
    static final Line[] PRE_SPECIAL = {
        l(4, "well, here goes nothing..."),
        l(3, "are you ready?"),
        l(5, "survive THIS, and i'll show you my special attack!"),
    };

    // ---- Special-attack monologues (GML obj_sansb_body lac 62/65/68/71/74) ----
    static final Line[] MONO_HUFF = {
        l(9, "huff... puff..."),
        l(9, "all right.  that's it."),
        l(9, "it's time for my special attack."),
        l(3, "are you ready?"),
        l(4, "here goes nothing."),
    };
    static final Line[] MONO_NOTHING = {
        l(1, "yep."),
        l(1, "that's right."),
        l(3, "it's literally nothing."),
        l(1, "and it's not gonna be anything, either."),
        l(4, "heh heh heh... ya get it?"),
        l(1, "i know i can't beat you."),
        l(4, "one of your turns..."),
        l(9, "you're just gonna kill me."),
        l(1, "so, uh."),
        l(4, "i've decided..."),
        l(4, "it's not gonna BE your turn.  ever."),
        l(3, "i'm just gonna keep having MY turn until you give up."),
        l(5, "even if it means we have to stand here until the end of time."),
        l(1, "capiche?"),
    };
    static final Line[] MONO_BORED = {
        l(9, "you'll get bored here."),
        l(1, "if you haven't gotten bored already, i mean."),
        l(5, "and then, you'll finally quit."),
    };
    static final Line[] MONO_TYPE = {
        l(5, "i know your type."),
        l(1, "you're, uh, very determined, aren't you?"),
        l(4, "you'll never give up, even if there's, uh..."),
        l(3, "absolutely NO benefit to persevering whatsoever."),
        l(1, "if i can make that clear."),
        l(4, "no matter what, you'll just keep going."),
        l(9, "not out of any desire for good or evil..."),
        l(3, "but just because you think you can."),
        l(1, "and because you \"can\"..."),
        l(9, "... you \"have to.\""),
    };
    static final Line[] MONO_GIVE_UP = {
        l(9, "but now, you've reached the end."),
        l(4, "there is nothing left for you now."),
        l(1, "so, uh, in my personal opinion..."),
        l(3, "the most \"determined\" thing you can do here?"),
        l(1, "is to, uh, completely give up."),
        l(3, "and... (yawn) do literally anything else."),
    };

    /** GML mercy_death: the spare ending. */
    private static final Line[] MERCY_LINES = {
        l(4, "..."),
        l(4, "you're sparing me?"),
        l(1, "finally."),
        l(3, "buddy.  pal."),
        l(4, "i know how hard it must be..."),
        l(4, "to make that choice."),
        l(4, "to go back on everything you've worked up to."),
        l(0, "i want you to know...  i won't let it go to waste."),
        l(0, "..."),
        l(3, "c'mere, pal."),
    };

    private SansBody sansBody;
    /** GML: hit_try — the turn counter every phase gate reads. */
    private int hitTry;
    /** GML: hit_reached — which hit_try dialogue has already played. */
    private int hitReached;
    /** GML: part — index into the per-phase attack table. */
    private int part;
    /** GML: nx — the red-soul "REAL battle" flag. */
    private boolean nx;
    /** The player FIGHTed since the last enemy turn (Sans dodged it). */
    private boolean dodged;
    private boolean specialStarted;
    /** A chained attack waiting for the current speech to finish (hit_try 15). */
    private int pendingChain;
    /** Intro: 0 talking · 1 opening attack queued · 2 opening running · 3 done. */
    private int introPhase;
    /** 0 none · 1 mercy (spare win) · 2 wake ("guess not."). */
    private int ending;
    private int endingHold;
    /**
     * The turning-point "mercy" trap (GML): >0 once the player tries to SPARE Sans
     * at his offer — counts down a few frames of the point-blank surprise blast,
     * then drops the player to 0 HP (the soul shatters). There is no sparing him
     * here; the only real spare is the sleep ending's fake FIGHT button.
     */
    private int sneak;

    private final List<Line> speech = new ArrayList<>();
    private int speechPos;
    private int speechTimer;

    private Clip music;

    public SansBoss(EntityManager manager) {
        super(manager, new Monster(0, "Sans", 1, 1, 1, 0));
    }

    // ---- Boss SPI -------------------------------------------------------------

    @Override
    public void setup() {
        stats.setup();                       // scr_monstersetup → 1 HP / 1 ATK / 1 DEF
        mercy.reset();
        mercy.setMercyMod(-99999);
        damage.karma.enabled = true;         // KARMA is what actually kills you

        sansBody = new SansBody(manager, this, soul, board, damage.karma);
        body = sansBody;
        manager.add(sansBody);

        applyBorder(35);
        centerSoulRed();
        G.faceemotion = 0;
        G.flag[20] = 0;

        act.setOptions(List.of("Check"), List.of(0));

        // The battle intro: Sans speaks, then the opening barrage, then the menu
        // (GML con 2→12 / fac 1→22, the overworld lead-in cut).
        say(INTRO_LINES);
        message = "* You feel like you're going to  have a bad time.";

        music = Audio.loop("/audio/mus_zz_megalovania.ogg");
    }

    @Override
    public void chooseAttack() {
        if (introPhase == 1) {
            // The opening barrage runs as a real enemy turn so the player can move.
            introPhase = 2;
            clearSpeech();
            sansBody.startOpening();
            G.turntimer = 999999;
            return;
        }
        if (ending != 0 || specialStarted || sansBody.specialRunning()) {
            G.turntimer = 999999;            // the body owns the clock from here on
            return;
        }
        boolean fought = dodged;
        dodged = false;
        G.attacked = 1;

        // GML: at the turning point Sans spares you every turn until you attack.
        if (hitTry == 14 && !fought) {
            message = "* Sans is sparing you.";
            dialogue = "";
            speech.clear();
            centerSoulRed();
            G.turntimer = 60;
            return;
        }

        if (hitTry < 27) {
            hitTry++;
        }
        turns = hitTry;
        sayFor(hitTry);
        message = flavorLine();

        if (hitTry == 14) {                  // the turning point / mercy offer
            mercy.setMercyMod(999999);
            sansBody.sweat = 2;
            setMusicVolume(0.05);            // GML: caster_pause(global.batmusic)
            centerSoulRed();
            G.turntimer = speechFrames();
            return;
        }
        if (hitTry == 15) {                  // "welp, it was worth a shot."
            nx = true;
            mercy.setMercyMod(-10000);
            setMusicVolume(1.0);             // GML: caster_resume — the REAL battle
            centerSoulRed();
            // The speech plays out, then the scene cuts into the 14.x chain.
            pendingChain = 14;
            G.turntimer = 999999;
            return;
        }
        if (hitTry == 27) {                  // the final attack → speech → special
            specialStarted = true;
            centerSoulRed();
            sansBody.startFinal();
            G.turntimer = 999999;
            return;
        }
        // GML: a FIGHT just makes Sans dodge — his turn still attacks in full.
        if (hitTry <= 13) {
            blueAttack();
        } else {
            redAttack();
        }
    }

    /** GML mnfight==2 hit_try<13: blue soul, part → a_type table. */
    private void blueAttack() {
        int sel = part;
        part++;
        int aType = switch (sel) {
            case 0 -> 0;
            case 1 -> 3;
            case 2 -> 23;
            case 3 -> 6;
            case 4 -> 7;
            case 5 -> 8;
            case 6 -> 17;
            case 7 -> 15;
            case 8 -> 18;
            case 9 -> 1;
            case 10 -> 5;
            case 11 -> 21;
            case 12 -> 16;
            default -> SansBody.chooseInt(1, 5, 21, 16);
        };
        boolean wide = sel == 6 || sel == 8;         // a_type 17/18: border 39
        applyBorder(wide ? 39 : 35);
        soul.setMode(SoulMode.BLUE);
        soul.x = (G.idealborder[0] + G.idealborder[1]) / 2;
        if (wide) {
            soul.y = G.idealborder[3] - 70;
            soul.vspeed = 2;
        } else {
            soul.y = G.idealborder[3] - Soul.HALF;
            soul.jumpStage = Soul.GROUNDED;
        }
        sansBody.setAttack(aType);
    }

    /**
     * Red phase, one entry per player turn (screenshot reference: [Attack #15-#25]
     * = hitTry 16-26). Multi-part turns (16.x / 20.x / 24.x) run as chains in
     * {@link SansBody}; the directional smashers follow the reference's scripts.
     */
    private void redAttack() {
        centerSoulRed();
        switch (hitTry) {
            case 16, 26 -> {                 // 15 / 25 — the diagonal blaster sweep
                applyBorder(37);
                sansBody.setAttack(12);
            }
            case 17 -> chainTurn(16);        // 16.1-16.6
            case 18 -> smasherTurn(1, new int[] { 1, 0, 1, 0, 3, 0, 2, 3, 0 });
            case 19, 24 -> {                 // 18 / 23 — the blaster barrage
                applyBorder(37);
                sansBody.setAttack(13);
            }
            case 20 -> sansBody.setAttack(10);   // 19 — the wall corridors
            case 21 -> chainTurn(20);        // 20.1-20.6
            case 22 -> smasherTurn(2, new int[] { 2, 3, 2, 1, 2, 0, 1, 3, 0 });
            case 23 -> smasherTurn(2, new int[] { 1, 3, 1, 2, 0, 2, 0, 0 });
            case 25 -> chainTurn(24);        // 24.1-24.6
            default -> sansBody.setAttack(11);
        }
    }

    private void chainTurn(int id) {
        sansBody.startChain(id, false);
        G.turntimer = 999999;                // the chain ends the turn itself
    }

    private void smasherTurn(int lv, int[] seq) {
        applyBorder(36);
        sansBody.startSmasher(lv, seq);
        G.turntimer = 999999;                // the smasher ends the turn itself
    }

    /** SCR_BORDERSETUP presets 35-39 (Sans's boxes; kept here, not in Core). */
    private void applyBorder(int preset) {
        switch (preset) {
            case 36 -> board.instaBorder(240, 400, 225, 385);
            case 37 -> board.instaBorder(120, 520, 185, 385);
            case 38 -> board.instaBorder(270, 370, 285, 385);
            case 39 -> board.instaBorder(112, 542, 250, 385);
            default -> board.instaBorder(132, 502, 250, 385);   // 35
        }
    }

    private void centerSoulRed() {
        soul.setMode(SoulMode.RED);
        soul.x = (G.idealborder[0] + G.idealborder[1]) / 2;
        soul.y = (G.idealborder[2] + G.idealborder[3]) / 2 - 8;
    }

    /** GML: the menu-box flavor line — KARMA gradually drowns out everything else. */
    private String flavorLine() {
        String m = "* Just keep attacking.";
        if (hitTry >= 5) {
            m = "* Sans's movements grow a  little wearier.";
        }
        if (hitTry >= 9) {
            m = "* Sans's movements seem to be  slower.";
        }
        if (hitTry >= 14) {
            m = "* Felt like a turning point.";
        }
        // GML: if (global.km >= 0) — always true, so KARMA text takes over here.
        m = "* You felt your sins crawling  on your back.";
        if (G.km >= 10) {
            m = "* You felt your sins weighing  on your neck.";
        }
        if (G.km >= 20) {
            m = "* KARMA coursing through your  veins.";
        }
        if (G.km >= 30) {
            m = "* Doomed to death of KARMA!";
        }
        if (hitTry == 16) {
            m = "* The REAL battle finally begins.";
        }
        if (hitTry >= 20) {
            m = "* Reading this doesn't seem  like the best use of time.";
        }
        if (hitTry >= 21) {
            m = "* Sans is starting to look  really tired.";
        }
        if (hitTry >= 22) {
            m = "* Sans is preparing something.";
        }
        if (hitTry >= 23) {
            m = "* Sans is getting ready to  use his special attack.";
        }
        return m;
    }

    @Override
    public void onAct(int whatiheard) {
        if (whatiheard == 0) {
            message = hitTry > 0
                    ? "* SANS 1 ATK 1 DEF  * The easiest enemy.  * Can only deal 1 damage."
                    + "  * Can't keep dodging forever.  * Keep attacking."
                    : "* SANS 1 ATK 1 DEF  * The easiest enemy.  * Can only deal 1 damage.";
        }
    }

    /**
     * A FIGHT "kill" never sticks: Sans dodges. His real HP is 1, so every landed
     * swing routes through here — restore the point and play the dodge instead.
     * battleOver is only ever set by the sleep ending.
     */
    @Override
    public void onDefeat() {
        stats.setHp(stats.maxhp);
        dodged = true;
        sansBody.dodge();
        message = "* You swing at Sans.  * He dodges.";
    }

    @Override
    public void onSpare() {
        // The SPARE button is only ever offered at the turning point (hit_try 14,
        // mercymod 999999). But Sans's "mercy" there is bait: drop your guard to
        // spare him and he takes the opening — a point-blank blast, instant death.
        // (The legitimate spare is the sleep ending's fake FIGHT button, which calls
        // mercyDeath() directly, never through here.)
        if (ending != 0 || sneak != 0 || specialStarted || sansBody.specialRunning()) {
            return;
        }
        startSneakAttack();
    }

    /** GML: the turning-point spare trap — Sans strikes the off-guard player dead. */
    private void startSneakAttack() {
        sneak = 1;
        mercy.setMercyMod(-99999);           // the SPARE button never comes back
        setMusicVolume(1.0);                 // the music swells back for the strike
        G.mnfight = core.TurnManager.ENEMY_TURN;
        G.attacked = 1;
        G.turntimer = 9999;                  // hold the turn; the blast ends it
        message = "* You tried to SPARE Sans.";
        dialogue = "";
        speech.clear();
        applyBorder(38);             // a tight box around the off-guard soul
        centerSoulRed();
        soul.hspeed = 0;
        soul.vspeed = 0;
        sansBody.sneakAttack();
    }

    /** The spare win (GML mercy_death): the fake FIGHT button or MERCY at 13+. */
    public void mercyDeath() {
        if (ending != 0) {
            return;
        }
        ending = 1;
        G.mnfight = -1;
        Audio.stop(music);
        music = null;
        G.faceemotion = 4;
        G.flag[20] = 0;
        sansBody.showInjured();   // the final hit landed → show the wounded art
        say(MERCY_LINES);
    }

    /** The sleep window expired: Sans wakes up and the chance is gone. */
    public void wakeUp() {
        if (ending != 0) {
            return;
        }
        ending = 2;
        G.mnfight = -1;
        say(new Line[] { l(3, "guess not.") });
    }

    /** The body finished the fac opening — hand the fight to the player menu. */
    void openingDone() {
        introPhase = 3;
        clearSpeech();
        message = "* You feel like you're going to  have a bad time.";
        G.turntimer = 0;                     // sweeps leftover bullets, opens the menu
    }

    void clearSpeech() {
        speech.clear();
        dialogue = "";
    }

    /** Drive the snoring "Z" bubble while Sans sleeps (no queued speech then). */
    void setSleepZ(String z) {
        dialogue = z;
    }

    @Override
    public void update() {
        if (battleOver) {
            return;
        }
        if (sneak != 0) {
            // The spare trap is playing out: the blaster charges, then connects.
            sneak++;
            if (sneak >= 26) {
                G.hp = 0;   // the point-blank blast lands → the soul shatters
            }
            return;         // nothing else runs while Sans cashes in the opening
        }
        stepSpeech();
        if (introPhase == 0 && speechDone()) {
            introPhase = 1;
            G.mnfight = core.TurnManager.ENTER_ENEMY;   // skip the menu: he attacks first
        }
        if (pendingChain != 0 && speechDone()) {
            int id = pendingChain;
            pendingChain = 0;
            clearSpeech();
            sansBody.startChain(id, true);   // the speech→attack scene cut
        }
        if (ending != 0 && speechDone()) {
            endingHold++;
            if (endingHold > 45) {
                battleOver = true;
                if (ending == 1) {
                    peacefulEnd = true;
                    G.flag[272] = 1;          // GML: the spare-ending flag
                    banner = "YOU WON!  You spared Sans.";
                } else {
                    banner = "* Sans woke up.  The chance is gone.";
                }
            }
        }
    }

    // ---- Dialogue ---------------------------------------------------------------

    /** Per-line dwell for the live speech queue (defaults to {@link #SPEECH_FRAMES}). */
    private int framesPerLine = SPEECH_FRAMES;

    /** Queue lines + apply the first line's face. Body monologues use this too. */
    void monologue(Line[] lines) {
        say(lines, SPEECH_FRAMES);
    }

    /** Queue lines at a custom per-line dwell (the ending monologue runs faster). */
    void monologue(Line[] lines, int framesPerLine) {
        say(lines, framesPerLine);
    }

    void setMusicVolume(double volume) {
        Audio.setVolume(music, volume);
    }

    private void say(Line[] lines) {
        say(lines, SPEECH_FRAMES);
    }

    private void say(Line[] lines, int framesPerLine) {
        this.framesPerLine = framesPerLine;
        speech.clear();
        for (Line line : lines) {
            speech.add(line);
        }
        speechPos = 0;
        speechTimer = framesPerLine;
        applyLine();
    }

    private void applyLine() {
        if (speech.isEmpty()) {
            dialogue = "";
            return;
        }
        Line line = speech.get(speechPos);
        dialogue = line.text;
        G.faceemotion = line.face;
        G.flag[20] = line.torso;
    }

    private void stepSpeech() {
        if (speech.isEmpty() || G.mnfight == core.TurnManager.MENU) {
            return;
        }
        if (--speechTimer > 0) {
            return;
        }
        if (speechPos < speech.size() - 1) {
            speechPos++;
            speechTimer = framesPerLine;
            applyLine();
        }
    }

    private boolean speechDone() {
        return speech.isEmpty() || (speechPos >= speech.size() - 1 && speechTimer <= 0);
    }

    private int speechFrames() {
        return Math.max(60, speech.size() * SPEECH_FRAMES + 30);
    }

    /** GML enumb 6: the per-hit_try monologue (played once, hit_reached gating). */
    private void sayFor(int t) {
        if (t <= hitReached) {
            return;
        }
        hitReached = t;
        Line[] lines = switch (t) {
            // case 1: first player action after "here we go." — Sans says nothing
            case 2 -> new Line[] {
                lt(3, 1, "what?  you think i'm just gonna stand there and take it?"),
            };
            case 3 -> new Line[] {
                l(0, "our reports showed a massive anomaly in the timespace continuum."),
                l(0, "timelines jumping left and right, stopping and starting..."),
            };
            case 4 -> new Line[] {
                l(4, "until suddenly, everything ends."),
            };
            case 5 -> new Line[] {
                l(4, "heh heh heh..."),
                l(5, "that's your fault, isn't it?"),
            };
            case 6 -> new Line[] {
                l(1, "you can't understand how this feels."),
            };
            case 7 -> new Line[] {
                l(4, "knowing that one day, without any warning..."),
                l(9, "it's all going to be reset."),
            };
            case 8 -> new Line[] {
                lt(9, 1, "look.  i gave up trying to go back a long time ago."),
            };
            case 9 -> new Line[] {
                lt(4, 1, "and getting to the surface doesn't really appeal anymore, either."),
            };
            case 10 -> new Line[] {
                lt(4, 1, "cause even if we do..."),
                lt(5, 1, "we'll just end up right back here, without any memory of it, right?"),
            };
            case 11 -> new Line[] {
                lt(1, 1, "to be blunt..."),
                lt(4, 1, "it makes it kind of hard to give it my all."),
            };
            case 12 -> new Line[] {
                lt(1, 1, "... or is that just a poor excuse for being lazy...?"),
                lt(3, 1, "hell if i know."),
            };
            case 13 -> new Line[] {
                lt(4, 1, "all i know is... seeing what comes next..."),
                lt(9, 1, "i can't afford not to care anymore."),
            };
            case 14 -> new Line[] {
                l(9, "ugh...  that being said..."),
                l(1, "you, uh, really like swinging that thing around, huh?"),
                l(0, "..."),
                l(4, "listen."),
                l(4, "i know you didn't answer me before, but..."),
                l(4, "somewhere in there.  i can feel it."),
                l(0, "there's a glimmer of a good person inside of you."),
                l(4, "the memory of someone who once wanted to do the right thing."),
                l(1, "someone who, in another time, might have even been..."),
                l(4, "a friend?"),
                l(3, "c'mon, buddy."),
                l(0, "do you remember me?"),
                l(4, "please, if you're listening..."),
                l(9, "let's forget all this, ok?"),
                l(3, "just lay down your weapon, and..."),
                l(4, "well, my job will be a lot easier."),
            };
            case 15 -> new Line[] {
                lt(3, 1, "welp, it was worth a shot."),
                lt(5, 1, "guess you like doing things the hard way, huh?"),
            };
            case 16 -> new Line[] {
                l(4, "sounds strange, but before all this i was secretly hoping we could be friends."),
                l(1, "i always thought the anomaly was doing this cause they were unhappy."),
                l(1, "and when they got what they wanted, they would stop all this."),
            };
            case 17 -> new Line[] {
                l(3, "and maybe all they needed was...  i dunno."),
                l(3, "some good food, some bad laughs, some nice friends."),
            };
            case 18 -> new Line[] {
                l(4, "but that's ridiculous, right?"),
                l(5, "yeah, you're the type of person who won't EVER be happy."),
            };
            case 19 -> new Line[] {
                l(5, "you'll keep consuming timelines over and over, until..."),
                l(4, "well."),
                l(4, "hey."),
                l(3, "take it from me, kid."),
                l(3, "someday..."),
                l(3, "you gotta learn when to QUIT."),
            };
            case 20 -> new Line[] {
                l(3, "and that day's TODAY."),
            };
            case 21 -> new Line[] {
                l(4, "cause...  y'see..."),
                l(1, "all this fighting is really tiring me out."),
            };
            case 22 -> new Line[] {
                l(4, "and if you keep pushing me..."),
                l(3, "then i'll be forced to use my special attack."),
            };
            case 23 -> new Line[] {
                l(3, "yeah, my special attack.  sound familiar?"),
                l(1, "well, get ready.  cause after the next move, i'm going to use it."),
                l(3, "so, if you don't wanna see it, now would be a good time to die."),
            };
            // 24-26 ([Attack #23-#25]): no dialogue — the PRE_SPECIAL speech moved
            // to the end of the 26.x final chain.
            default -> new Line[0];
        };
        // Sweat cues from the GML (with(mypart1) sweat = n).
        if (t == 21 || t == 22) {
            sansBody.sweat = 1;
        }
        if (t == 23) {
            sansBody.sweat = 2;
        }
        if (lines.length > 0) {
            say(lines);
        }
    }

    @Override
    public void render(Graphics2D g) {
        // The sprite is drawn by SansBody.
    }

    @Override
    public boolean introComplete() {
        return introPhase == 3;
    }
}
