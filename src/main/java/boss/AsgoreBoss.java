package boss;

import battle.SoulMode;
import bullet.asgore.ConvergingFireGen;
import bullet.asgore.FireColumnGen;
import bullet.asgore.FireStormGen;
import bullet.asgore.HandBulletGen;
import bullet.asgore.RandomHandGen;
import bullet.asgore.SineFireGen;
import bullet.asgore.SpearSwipe;
import core.EntityManager;
import core.TurnManager;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.List;
import javax.sound.sampled.Clip;
import util.GMLHelper;

/**
 * Asgore Dreemurr (GML: {@code obj_asgoreb} + {@code obj_asgoreb_body} +
 * {@code obj_asgore_finalintro} + {@code obj_asgore_lastcutscene}). The final boss.
 *
 * <p><b>Opening cutscene</b> (GML {@code obj_asgore_finalintro}): Asgore stands in
 * his calm, arms-spread pose while the narration plays ("A strange light fills the
 * room…" → "…DETERMINATION."), then he speaks ("Human… / It was nice to meet you. /
 * Goodbye.") and the fight cuts in. His defining cruelty: the <b>MERCY button is
 * gone</b> from the very start ({@code global.mercy = 2}) — there is no shatter
 * animation, the button simply isn't there. The soul is RED (free movement) and the
 * fight is a long turn-counted gauntlet of fire, flying hands and trident swipes.
 *
 * <p><b>Genocide route</b> (GML {@code murder == 1}): instead of attacking, Asgore
 * offers tea ("Now, now. / There's no need to fight. / Why not settle this… / Over a
 * nice cup of tea?"); the player strikes him down in one blow, and he kneels —
 * "Why… You…" — and is defeated.
 *
 * <p><b>Neutral end</b> ({@code fivedamage}): a FIGHT hit that would drop him to
 * {@code <= 500} HP fires the kneel cutscene instead — Asgore is left broken at low
 * HP and the fight ends ("Ah… / … / So that is how it is.").
 *
 * // GML: obj_asgoreb (+ obj_asgore_finalintro + obj_asgore_lastcutscene)
 */
public final class AsgoreBoss extends Boss {

    // GML SCR_BORDERSETUP: Asgore's combat box. Border 30 (normal turns) is taller;
    // border 29 (trident-swipe turns) is shorter. Both are centred, 207..427 wide.
    private static final double BOX_L = 207;
    private static final double BOX_R = 427;
    private static final double BOX_T_NORMAL = 200;   // GML border 30
    private static final double BOX_T_SWIPE = 250;    // GML border 29
    private static final double BOX_B = 385;

    /** GML: talk_x — TALK escalation counter (only meaningful while kills == 0). */
    private int talkX;

    private AsgoreBody abody;

    // ---- Opening cutscene (GML: obj_asgore_finalintro) ---------------------------
    private boolean genocide;
    private boolean introActive = true;
    private int introStep;
    private int introTimer;
    private int sliceFlash;     // genocide: the white "cut" flash
    // The one-shot "Bergentrückung" prelude. The neutral intro is paced against this
    // clip's real playback position so the cut to the fight lands exactly on its end.
    private Clip introMusic;

    // GML obj_asgore_finalintro narration (the four DETERMINATION boxes).
    private static final String[] NARRATION = {
        "* (A strange light fills the room.)",
        "* (Twilight is shining through the barrier.)",
        "* (It seems your journey is finally over.)",
        "* (You're filled with DETERMINATION.)",
    };
    // The neutral intro's seven beats (4 narration + 3 farewell) and their relative
    // weights. The cutscene is paced across the "Bergentrückung" prelude by these
    // weights, so the farewell lands exactly as the track ends and the fight cuts in
    // (see {@link #introProgress}). Used as the fallback frame budget when there's no
    // audio (headless renders/tests).
    private static final int[] NEUTRAL_BEAT_FRAMES = { 110, 110, 110, 130, 60, 90, 80 };
    private static final int NEUTRAL_TOTAL_FRAMES = 690;
    // Asgore's farewell. The intro pose is his calm, eyes-closed face
    // (spr_asgore_bface frame 4) throughout — frames 0–3 are open-eyed/alert and
    // read as "surprised", which is wrong for this resigned moment.
    private static final int CALM_FACE = 4;
    private static final String[] FAREWELL = { "Human...", "It was nice to meet you.", "Goodbye." };
    private static final int[] FAREWELL_FACE = { CALM_FACE, CALM_FACE, CALM_FACE };
    // Genocide tea offer (GML: \E1 ... face 1 throughout).
    private static final String[] TEA = {
        "Now, now.", "There's no need to fight.", "Why not settle this...", "Over a nice cup of tea?",
    };
    private static final int[] TEA_FRAMES = { 55, 70, 70, 90 };

    // ---- Endgame kneel cutscene (GML: fivedamage → obj_asgore_lastcutscene) -------
    private boolean ending;
    private int endStep;
    private int endTimer;
    private static final int[] END_LINE_FRAMES = { 70, 60, 110 };
    private static final String[] END_LINES = { "Ah . . .", ". . .", "So that is how it is." };

    public AsgoreBoss(EntityManager manager) {
        // GML scr_monstersetup (monstertype 52): HP 3500, ATK 10, DEF -30.
        super(manager, new Monster(0, "Asgore", 3500, 10, -30, 0));
    }

    // ---- Boss SPI ----------------------------------------------------------

    @Override
    public String musicPath() {
        // Self-managed: the slow "Bergentrückung" prelude is played as a one-shot in
        // setup() so the opening cutscene can be synced to it (returning a path here
        // would make BattleScene loop it instead). Once the prelude ends, startFight()
        // cuts to "ASGORE" (mus_vsasgore).
        return null;
    }

    @Override
    public void setup() {
        stats.setup();

        // GML: this run is the genocide finale once the murder level is high enough
        // (the boss-select GENOCIDE route, SIN >= 7).
        genocide = G.murderlv >= 7;

        // GML: mypart1 = obj_asgoreb_body. Drawn behind the box.
        abody = new AsgoreBody(manager);
        body = abody;
        manager.add(abody);
        abody.showCalm(CALM_FACE);   // open in the calm, eyes-closed, arms-spread pose

        // GML: obj_sparebt.visible = 0; global.mercy = 2 — the MERCY button is simply
        // gone (no shatter animation); the menu drops it from the command row.
        mercy.reset();
        mercy.setBrokenMercy();

        // RED soul, free movement. The box is the (hidden) combat box until the
        // opening cutscene cuts to the fight.
        soul.setMode(SoulMode.RED);
        board.instaBorder(BOX_L, BOX_R, BOX_T_NORMAL, BOX_B);
        board.visible = false;
        centerSoul();

        // You can't kill him outright: a hit that would drop him to <= 500 HP fires
        // the kneel cutscene instead (GML: global.monsterhp - takedamage <= 500).
        damage.fivedamageThreshold = 500;
        damage.playerDamageMultiplier = 4;
        damage.playerMinDamage = 100;

        // ACT: CHECK (whatiheard 0) and TALK (3). TALK is the only thing that ever
        // softens him, and only on a pacifist run (kills == 0).
        act.setOptions(List.of("Check", "Talk"), List.of(0, 3));

        // The slow "Bergentrückung" prelude plays once (not looped): the neutral intro
        // is paced against it so the farewell ends exactly as the track does. Null when
        // audio is off (headless renders/tests) — the intro then falls back to frames.
        introMusic = util.Audio.playMusicOnce("/audio/mus_bergentruckung.ogg");

        // GML: the fight opens in the cutscene; stay on mnfight 99 until it ends.
        G.mnfight = TurnManager.SETUP;
        message = "* ASGORE blocks the way!";
        turns = 0;
    }

    @Override
    public void chooseAttack() {
        turns++;
        // GML: ttttt widens (shortens) the box on swipe turns (border 29 vs 30).
        boolean swipeTurn = turns == 4 || turns == 8 || turns == 12 || turns == 16
                || turns == 20 || turns == 23;
        board.instaBorder(BOX_L, BOX_R, swipeTurn ? BOX_T_SWIPE : BOX_T_NORMAL, BOX_B);
        centerSoul();
        G.turntimer = 180;
        message = stats.hp() <= stats.maxhp / 4 ? "* Asgore has low HP." : "* ...";

        switch (turns) {
            case 1 -> { G.turntimer = 110; spawn(new HandBulletGen(manager, soul, 1)); }
            case 2 -> { G.turntimer = 160; spawn(new FireColumnGen(manager, soul, 1)); }
            case 3 -> { G.turntimer = 180; spawn(new SineFireGen(manager, soul, 2)); }
            case 4 -> swipe(0);
            case 5 -> { G.turntimer = 175; spawn(new RandomHandGen(manager, soul, 40)); }
            case 6 -> { G.turntimer = 190; spawn(new ConvergingFireGen(manager, soul, 0)); }
            case 7 -> { G.turntimer = 160; spawn(new FireStormGen(manager, soul, 1)); }
            case 8 -> swipe(1);
            case 9 -> { G.turntimer = 145; spawn(new FireColumnGen(manager, soul, 2)); }
            case 10 -> { G.turntimer = 190; spawn(new RandomHandGen(manager, soul, 35)); }
            case 11 -> { G.turntimer = 180; spawn(new ConvergingFireGen(manager, soul, 1)); }
            case 12 -> swipe(1);
            case 13 -> { G.turntimer = 140; spawn(new FireStormGen(manager, soul, 2)); }
            case 14 -> { G.turntimer = 190; spawn(new SineFireGen(manager, soul, 3)); }
            case 15 -> { G.turntimer = 175; spawn(new ConvergingFireGen(manager, soul, 2)); }
            case 16 -> swipe(2);
            case 17 -> { G.turntimer = 173; spawn(new RandomHandGen(manager, soul, 30)); }
            case 18 -> { G.turntimer = 188; spawn(new ConvergingFireGen(manager, soul, 3)); }
            case 19 -> { G.turntimer = 130; spawn(new FireStormGen(manager, soul, 3)); }
            case 20 -> swipe(3);
            case 21, 22 -> randomLatePick();
            case 23 -> { swipe(3); turns = 20; }   // GML: loop back to turn 20
            default -> randomLatePick();
        }
        decayDefenseLate();

        G.attacked = 1;
    }

    /** GML turns 21/22 + loop: DEF keeps dropping and a random attack is chosen. */
    private void randomLatePick() {
        if (G.monsterdef[stats.slot] > -90) {
            G.monsterdef[stats.slot] -= 5;
        }
        switch (GMLHelper.choose(new int[] { 0, 1, 2, 3, 4 })) {
            case 0 -> { G.turntimer = 188; spawn(new ConvergingFireGen(manager, soul, 3)); }
            case 1 -> { G.turntimer = 130; spawn(new FireStormGen(manager, soul, 3)); }
            case 2 -> { G.turntimer = 173; spawn(new RandomHandGen(manager, soul, 30)); }
            case 3 -> { G.turntimer = 190; spawn(new SineFireGen(manager, soul, 3)); }
            default -> { G.turntimer = 145; spawn(new FireColumnGen(manager, soul, 2)); }
        }
    }

    /** GML: once turns >= 20 his DEF collapses toward -120 so the fight can end. */
    private void decayDefenseLate() {
        if (turns >= 20 && G.monsterdef[stats.slot] > -120) {
            G.monsterdef[stats.slot] -= 10;
        }
    }

    /** GML: turntimer = 9999; obj_asgore_spearswipegen — the trident swipe ends the turn. */
    private void swipe(int diff) {
        G.turntimer = 9999;
        spawn(new SpearSwipe(manager, soul, abody, diff));
    }

    private void spawn(core.Entity e) {
        manager.add(e);
    }

    @Override
    public void onAct(int whatiheard) {
        switch (whatiheard) {
            case 0 -> message = "* ASGORE - ATK 80 DEF 80  * The king of monsters.";
            case 3 -> talk();
            default -> message = "* ...";
        }
    }

    /** GML whatiheard == 3: TALK. Softens Asgore only on a no-kill run. */
    private void talk() {
        if (G.kills > 0) {
            message = "* But there was nothing to  say.";
            return;
        }
        switch (talkX) {
            case 0 -> message = "* You quietly tell ASGORE you  don't want to fight him.  * His hands tremble for a moment.";
            case 1 -> message = "* You tell ASGORE that you  don't want to fight him.  * His breathing gets funny for a moment.";
            case 2 -> {
                message = "* You firmly tell ASGORE to STOP  fighting.  * Recollection flashes in his  eyes...  * ASGORE's ATTACK and DEFENSE  dropped!";
                G.monsteratk[stats.slot]--;
                G.monsterdef[stats.slot] -= 10;
            }
            case 8 -> message = "* All you can do is FIGHT.";
            default -> message = "* Seems talking won't do any  more good.";
        }
        talkX++;
    }

    @Override
    public void onDamaged() {
        // GML Step-3 hurt: a hit that drops him to <= 500 fires the kneel cutscene.
        if (G.fivedamage == 1 && !ending) {
            beginEnding(9);
        }
    }

    @Override
    public void onDefeat() {
        // The fivedamage branch normally pre-empts an actual kill, but if HP ever
        // hits zero, route to the same kneel ending rather than a generic banner.
        stats.defeat();
        if (!ending) {
            beginEnding(9);
        }
    }

    /** Enter the endgame: freeze the turn machine, hide the soul, kneel Asgore. */
    private void beginEnding(int kneelFace) {
        ending = true;
        endStep = 0;
        endTimer = 0;
        G.mnfight = -1;        // frozen cutscene: no menu, no enemy turn
        soul.hidden = true;
        // The combat box vanishes the instant he kneels — no lingering frame behind
        // the kneeling sprite or doubled up with the outcome banner.
        board.visible = false;
        abody.kneelFace = kneelFace;
        abody.startKneel();
        dialogue = "";
        message = "* Asgore has nothing left.";
    }

    @Override
    public void update() {
        if (introActive) {
            updateIntro();
            return;
        }
        if (!ending) {
            return;
        }
        // Wait for the body to finish kneeling, then type Asgore's last words.
        if (!abody.kneelComplete()) {
            return;
        }
        if (endStep < END_LINES.length) {
            if (endTimer == 0) {
                dialogue = END_LINES[endStep];
            }
            if (++endTimer >= END_LINE_FRAMES[endStep]) {
                endStep++;
                endTimer = 0;
            }
            return;
        }
        // The last line has had its moment — close out the fight peacefully.
        battleOver = true;
        peacefulEnd = true;
        banner = "* ASGORE kneels, defeated.  His will to fight is gone.";
    }

    // ---- Opening cutscene driver ------------------------------------------------

    private void updateIntro() {
        introTimer++;
        if (genocide) {
            // Genocide isn't music-synced (it ends in defeat, no cut to the fight), so
            // it keeps the original auto-advance + Z-skip pacing.
            updateGenocideIntro(soul.confirmPressed);
        } else {
            updateNeutralIntro();
        }
    }

    /**
     * GML obj_asgore_finalintro (murder == 0): narration → farewell → cut to fight.
     *
     * <p>The seven beats are paced across the "Bergentrückung" prelude (see
     * {@link #introProgress}), so the farewell lands as the track ends and the cut to
     * the fight is locked to the music. Not skippable — the prelude plays in full.
     */
    private void updateNeutralIntro() {
        double progress = introProgress();
        if (progress >= 1.0) {
            // Cut to the fight: battle pose, the box appears, "ASGORE attacks!", his turn.
            startFight();
            return;
        }
        // Map elapsed progress onto the beat whose weighted slice we're in.
        int elapsed = (int) Math.round(progress * NEUTRAL_TOTAL_FRAMES);
        int acc = 0;
        int beat = 0;
        while (beat < NEUTRAL_BEAT_FRAMES.length - 1 && elapsed >= acc + NEUTRAL_BEAT_FRAMES[beat]) {
            acc += NEUTRAL_BEAT_FRAMES[beat];
            beat++;
        }
        introStep = beat;
        if (beat < NARRATION.length) {
            dialogue = "";   // narration draws in the box area, not the speech bubble
        } else {
            int f = beat - NARRATION.length;
            dialogue = FAREWELL[f];
            abody.showCalm(FAREWELL_FACE[f]);
        }
    }

    /**
     * How far the neutral intro is through the "Bergentrückung" prelude, 0..1. Driven
     * by the clip's real playback position when audio is on; otherwise (headless) it
     * falls back to a fixed frame budget so renders/tests still advance.
     */
    private double introProgress() {
        if (introMusic != null) {
            long length = introMusic.getMicrosecondLength();
            if (length > 0) {
                // The one-shot clip stops at its end; treat "stopped" as fully done so a
                // few microseconds of slack at the tail can't strand the cutscene.
                if (introTimer > 2 && !introMusic.isRunning()) {
                    return 1.0;
                }
                return Math.min(1.0, introMusic.getMicrosecondPosition() / (double) length);
            }
        }
        return Math.min(1.0, introTimer / (double) NEUTRAL_TOTAL_FRAMES);
    }

    /** GML obj_asgore_finalintro (murder == 1): tea offer → slice → kneel → defeat. */
    private void updateGenocideIntro(boolean advance) {
        if (introStep < TEA.length) {
            dialogue = TEA[introStep];
            abody.showCalm(CALM_FACE);
            if (advance || introTimer >= TEA_FRAMES[introStep]) {
                nextIntroStep();
            }
            return;
        }
        int s = introStep - TEA.length;
        switch (s) {
            case 0 -> {
                // GML con -120: the player strikes; obj_slice cuts Asgore down.
                dialogue = "";
                if (introTimer == 1) {
                    sliceFlash = 18;
                    abody.kneelFace = 3;
                    abody.startKneel();
                }
                if (introTimer >= 35) {
                    nextIntroStep();
                }
            }
            case 1 -> {
                // GML con 20: kneeling, "Why... You..."
                if (introTimer == 1) {
                    dialogue = "Why... You...";
                }
                if (advance || introTimer >= 110) {
                    nextIntroStep();
                }
            }
            default -> {
                // Defeated.
                introActive = false;
                dialogue = "";
                battleOver = true;
                banner = "* ASGORE was torn apart.";
                G.mnfight = -1;
            }
        }
    }

    private void nextIntroStep() {
        introStep++;
        introTimer = 0;
    }

    /** Cut from the calm pose into the battle: Asgore's turn begins. */
    private void startFight() {
        introActive = false;
        dialogue = "";
        // GML: the cutscene cuts to the fight — swap "Bergentrückung" for "ASGORE".
        util.Audio.loop("/audio/mus_vsasgore.ogg");
        abody.showBattle();
        board.visible = true;
        board.instaBorder(BOX_L, BOX_R, BOX_T_NORMAL, BOX_B);
        centerSoul();
        message = "* ASGORE attacks!";
        // GML: con 50 sets mnfight = 3 — Asgore takes the first turn.
        G.mnfight = TurnManager.ENTER_ENEMY;
        G.myfight = -1;
    }

    @Override
    public boolean introComplete() {
        return !introActive;
    }

    @Override
    public void render(Graphics2D g) {
        // The opening narration boxes (0.1–0.4) draw as plain "* (...)" text where the
        // box would be; the farewell/tea lines use the speech bubble (boss.dialogue).
        if (introActive && !genocide && introStep < NARRATION.length) {
            renderNarration(g, NARRATION[introStep]);
        }
        // The genocide "cut" — a white flash and a slash across the screen.
        if (sliceFlash > 0) {
            renderSlice(g);
            sliceFlash--;
        }
    }

    private void renderNarration(Graphics2D g, String text) {
        g.setFont(util.Fonts.ui(22f));
        g.setColor(Color.WHITE);
        int x = 120;
        int y = 300;
        int lineH = 30;
        int maxChars = 30;
        int line = 0;
        for (String row : wrap(text, maxChars)) {
            g.drawString(row, x, y + line * lineH);
            line++;
        }
    }

    private void renderSlice(Graphics2D g) {
        // A diagonal white slash, then a brief full-screen white flash.
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(4f));
        g.setColor(Color.WHITE);
        g.drawLine(420, 70, 230, 300);
        g.setStroke(old);
        Composite oc = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                Math.min(0.8f, sliceFlash / 12f)));
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, core.Game.WIDTH, core.Game.HEIGHT);
        g.setComposite(oc);
    }

    private static List<String> wrap(String text, int maxChars) {
        List<String> lines = new java.util.ArrayList<>();
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

    /** GML enumb 6: recentre the heart in the box at the start of each enemy turn. */
    private void centerSoul() {
        soul.x = (G.idealborder[0] + G.idealborder[1]) / 2.0;
        soul.y = (G.idealborder[2] + G.idealborder[3]) / 2.0;
        soul.vspeed = 0;
        soul.hspeed = 0;
    }
}
