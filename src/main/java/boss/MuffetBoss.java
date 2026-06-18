package boss;

import battle.SoulMode;
import battle.WebBoard;
import bullet.muffet.SpiderBulletGen;
import core.EntityManager;
import core.TurnManager;
import java.util.List;

/**
 * Muffet (GML: {@code obj_spiderb}, the "spider boss"). HP 1250 / ATK 8 / DEF 0.
 * The whole fight is fought in the {@link SoulMode#PURPLE} web mode: the soul is
 * locked to three horizontal strands and dodges by hopping between them as spiders,
 * donuts and croissants stream across.
 *
 * <p><b>Flow.</b> An opening cutscene traps you in the purple web; then sixteen enemy
 * turns ({@code turnAmt} 0..15) each fire one of {@link SpiderBulletGen}'s 16 patterns
 * while Muffet recounts the spiders' plight. Turns 4 / 9 / 15 are the
 * "Breakfast / Lunch / Dinner" pet special (handled in {@link #petSpecial()}). After
 * the sixteenth attack a telegram arrives from the RUINS spiders — it was all a
 * misunderstanding — and Muffet spares you (a peaceful end). The murder route
 * ({@code scr_murderlv ≥ 12}) swaps her dialogue and drops her DEF so a FIGHT run
 * fells her in a couple of hits.
 *
 * // GML: obj_spiderb
 */
public final class MuffetBoss extends Boss {

    // GML SCR_BORDERSETUP border 21 — Muffet's combat box.
    private static final double BOX_L = 197;
    private static final double BOX_R = 437;
    private static final double BOX_T = 250;
    private static final double BOX_B = 385;

    /** GML: scr_murderlv() >= 12 → murder dialogue + spareable-by-low-DEF. */
    private boolean murder;

    private MuffetBody mbody;
    private WebBoard web;

    /** GML turnamt — which attack/dialogue line (0..15), then the spare ending. */
    private int turnAmt;
    /** GML atkdown — a successful bribe drops her ATK by 2 for the coming turn. */
    private int atkdown;
    /** GML bribes / struggle — escalating ACT flavour counters. */
    private int bribes;
    private int struggle;
    private int bribePrice = 10; // GML global.flag[382]

    // ---- Opening cutscene (the purple-web trap) ----------------------------
    private boolean introActive = true;
    private int introStep;
    private int introTimer;
    private static final String[] INTRO = {
        "Don't look so blue, my deary~",
        "... I think purple is a better look on you! Ahuhuhu~",
    };
    private static final int[] INTRO_FRAMES = { 70, 90 };

    // ---- Spare ending (the telegram) ---------------------------------------
    private boolean ending;
    private int endStep;
    private int endTimer;
    private String[] endingLines;
    private static final int END_LINE_FRAMES = 90;

    // ---- Hurt shudder ------------------------------------------------------
    private int hurtTimer;
    /** Frames left to show the current turn's spoken line before it auto-clears. */
    private int dialogueTimer;

    public MuffetBoss(EntityManager manager) {
        // GML scr_monstersetup type 39: HP 1250, ATK 8, DEF 0, XP 300.
        super(manager, new Monster(0, "Muffet", 1250, 8, 0, 300));
    }

    @Override
    public String musicPath() {
        return "/audio/mus_spider.ogg"; // "Spider Dance"
    }

    // ---- Boss SPI ----------------------------------------------------------

    @Override
    public void setup() {
        stats.setup();
        murder = G.murderlv >= 12;

        // GML: mercymod = -960 (SPARE refused until the telegram); murder = -90000 and
        // DEF -800 so a FIGHT run can actually fell her.
        mercy.reset();
        mercy.setMercyMod(murder ? -90000 : -960);
        if (murder) {
            G.monsterdef[stats.slot] = -800;
        }

        // The combat box (border 21) + the purple web that fills it.
        board.instaBorder(BOX_L, BOX_R, BOX_T, BOX_B);
        board.visible = true;

        web = new WebBoard(manager);
        configureWeb();
        manager.add(web);

        // PURPLE soul, locked to the web from the first frame.
        soul.web = web;
        soul.setMode(SoulMode.PURPLE);
        centerOnWeb();

        mbody = new MuffetBody(manager, board);
        body = mbody;
        manager.add(mbody);

        // ACT: Check (0), Pay (1), Struggle (3).
        act.setOptions(List.of("Check", "Pay", "Struggle"), List.of(0, 1, 3));

        G.mnfight = TurnManager.SETUP; // the opening cutscene plays first
        turnAmt = 0;
        message = "* Muffet attacks!";
    }

    /** GML obj_purpleheart Create: the standard 3-strand web inside the box. */
    private void configureWeb() {
        web.type = 0;
        web.xmid = (BOX_L + BOX_R) / 2.0;
        web.xlen = 100;
        web.yspace = 40;
        web.yamt = 3;
        web.yzero = BOX_T + 30;
        web.yoff = 0;
        web.yadd = 0;
    }

    /** Put the heart on the middle strand at the start of an enemy turn. */
    private void centerOnWeb() {
        soul.webYno = 2;
        soul.webMoving = 0;
        soul.webSpace = 0;
        soul.x = web.xmid;
        soul.y = web.yzero + (soul.webYno - 1) * web.yspace + web.yoff;
    }

    @Override
    public void chooseAttack() {
        // After the sixteenth attack, the telegram arrives instead of an attack.
        if (turnAmt > 15) {
            beginEnding();
            G.turntimer = 999999; // hold the "enemy turn" while the cutscene plays
            G.attacked = 1;
            return;
        }

        configureWeb();
        centerOnWeb();
        G.turntimer = 180;
        G.firingrate = 10;
        dialogue = line(turnAmt);
        dialogueTimer = 150;   // the line clears itself partway in, so it stops blocking
        message = "* Muffet pours you a cup of spiders.";

        SpiderBulletGen gen = new SpiderBulletGen(manager, soul);
        gen.type = turnAmt;
        gen.turnAmt = turnAmt;
        gen.dmg = G.monsteratk[stats.slot] - atkdown;
        manager.add(gen);
        gen.buildPattern();

        if (turnAmt == 4 || turnAmt == 9 || turnAmt == 15) {
            petSpecial(gen);
        }

        turnAmt++;
        atkdown = 0;
        G.attacked = 1;
    }

    /**
     * GML turns 4/9/15: the pet special ("Breakfast / Lunch / Dinner"). The web rises,
     * the box stretches tall, the giant cupcake's maw climbs into the bottom, and the
     * player must keep hopping up to stay clear while spiders rappel down.
     */
    private void petSpecial(SpiderBulletGen gen) {
        G.turntimer = switch (turnAmt) {
            case 4 -> 620;   // breakfast
            case 15 -> 700;  // dinner
            default -> 660;  // lunch
        };
        // GML yadd2: the steady scroll speed once the web finishes rising (3/4/5).
        int yadd2 = switch (turnAmt) {
            case 9 -> 4;
            case 15 -> 5;
            default -> 3;
        };
        boolean fast = turnAmt == 9 || turnAmt == 15; // GML turnamt 10/16 → faster drops
        manager.add(new bullet.muffet.PetSpecial(manager, soul, board, gen, yadd2, fast));
    }

    /** This turn's spoken line — murder route overrides where Muffet's story differs. */
    private String line(int t) {
        if (murder && MURDER.containsKey(t)) {
            return MURDER.get(t);
        }
        return NORMAL[t];
    }

    @Override
    public void onAct(int whatiheard) {
        switch (whatiheard) {
            case 0 -> message = "* MUFFET - ATK 38.8 DEF 18.8  * If she invites you to her  parlor, excuse yourself.";
            case 1 -> pay();
            case 3 -> struggle();
            default -> message = "* ...";
        }
    }

    /** GML whatiheard == 1: bribe Muffet to lower her ATK this turn. */
    private void pay() {
        // No gold economy in the boss rush, so the bribe always lands (GML escalated
        // the price and could fail when broke). She drops her ATK by 2 for the turn.
        message = "* You pay " + bribePrice + "G.  * Muffet reduces her ATTACK for  this turn!";
        atkdown = 2;
        bribes++;
        bribePrice += switch (bribes) {
            case 1 -> 30;
            case 2 -> 40;
            case 3 -> 70;
            case 4 -> 50;
            default -> 300;
        };
    }

    /** GML whatiheard == 3: struggle in the web — the third try earns a discount. */
    private void struggle() {
        switch (struggle) {
            case 0 -> message = "* You struggle to escape the web.  * Muffet covers her mouth and  giggles at you.";
            case 1 -> message = "* You struggle to escape the web.  * Muffet laughs and claps her  hands.";
            case 2 -> {
                message = "* You struggle to escape the web.  * Muffet is so amused that she  gives you a discount!";
                bribePrice = Math.max(1, bribePrice / 2);
            }
            default -> message = "* You struggle to escape the web.  * Nothing happened.";
        }
        struggle++;
    }

    @Override
    public void onDamaged() {
        hurtTimer = 12;
        if (mbody != null) {
            mbody.hurt = true;
        }
    }

    @Override
    public void onDefeat() {
        // FIGHT brought her to 0 HP (the murder/grind path): she falls.
        stats.defeat();
        battleOver = true;
        banner = "* Muffet was defeated.";
        G.mnfight = -1;
        soul.hidden = true;
        if (web != null) {
            web.visible = false;
        }
        board.visible = false;
    }

    @Override
    public void update() {
        if (hurtTimer > 0 && --hurtTimer == 0 && mbody != null) {
            mbody.hurt = false;
        }
        // The spoken line clears itself partway through the enemy turn so the speech
        // bubble stops covering the box for the rest of the (often long) attack.
        if (G.mnfight == TurnManager.ENEMY_TURN && dialogueTimer > 0 && --dialogueTimer == 0) {
            dialogue = "";
        }
        // (WebBoard gates its own rendering on the enemy turn, so there's no per-frame
        // visibility juggling here.)

        if (introActive) {
            updateIntro();
            return;
        }
        if (ending) {
            updateEnding();
        }
    }

    // ---- Opening cutscene --------------------------------------------------

    private void updateIntro() {
        boolean advance = soul.confirmPressed;
        if (introStep < INTRO.length) {
            dialogue = INTRO[introStep];
            if (advance || ++introTimer >= INTRO_FRAMES[introStep]) {
                introStep++;
                introTimer = 0;
            }
            return;
        }
        // "You're trapped in a strange purple web!" → hand control to the player.
        introActive = false;
        dialogue = "";
        message = "* You're trapped in a strange  purple web!";
    }

    @Override
    public boolean introComplete() {
        return !introActive;
    }

    // ---- Spare ending (telegram) -------------------------------------------

    private void beginEnding() {
        ending = true;
        endStep = 0;
        endTimer = 0;
        endingLines = murder ? END_MURDER : END_NORMAL;
        soul.hidden = true;
        if (web != null) {
            web.visible = false;
        }
        dialogue = "";
        message = "* A telegram arrives...";
    }

    private void updateEnding() {
        if (G.mnfight != -1) {
            G.mnfight = -1; // freeze the turn machine while she talks
        }
        soul.hidden = true;
        boolean advance = soul.confirmPressed;
        if (endStep < endingLines.length) {
            if (endTimer == 0) {
                dialogue = endingLines[endStep];
            }
            if (advance || ++endTimer >= END_LINE_FRAMES) {
                endStep++;
                endTimer = 0;
            }
            return;
        }
        // "I'll SPARE you now~" — the fight ends peacefully.
        dialogue = "";
        battleOver = true;
        peacefulEnd = true;
        banner = "* Muffet spared you.";
    }

    @Override
    public void render(java.awt.Graphics2D g) {
        // Nothing of its own — MuffetBody draws Muffet, WebBoard the strands, and
        // BattleScene the speech bubble / HUD.
    }

    // ---- Dialogue tables ---------------------------------------------------

    private static final String[] NORMAL = {
        "Why so pale? You should be proud~",
        "Proud that you're going to make a delicious cake~ Ahuhuhu~",
        "Let you go? Don't be silly~",
        "Your SOUL is going to make every spider very happy~~~",
        "Oh, how rude of me! I almost forgot to introduce you to my pet~ "
            + "It's breakfast time, isn't it? Have fun, you two~",
        "The person who warned us about you...",
        "Offered us a LOT of money for your SOUL.",
        "They had such a sweet smile~ and... ahuhu~",
        "It's strange, but I swore I saw them in the shadows... Changing shape...?",
        "Oh, it's lunch time, isn't it? And I forgot to feed my pet~",
        "With that money, the spider clans can finally be reunited~",
        "You haven't heard? Spiders have been trapped in the RUINS for generations!",
        "Even if they go under the door, Snowdin's fatal cold is impassable alone.",
        "But with the money from your SOUL, we'll be able to rent them a heated limo~",
        "And with all of the leftovers...? We could have a nice vacation~ "
            + "Or even build a spider baseball field~",
        "But enough of that... It's time for dinner, isn't it? Ahuhuhu~",
    };

    /** Murder-route overrides keyed by turnAmt (turns not present use NORMAL). */
    private static final java.util.Map<Integer, String> MURDER = java.util.Map.of(
        3, "You're scaring off all my customers!",
        6, "Looked like a total nerd.",
        7, "She was very adamant I run away with her~~~ Ahuhuhu~~~",
        8, "She even left a route for me to escape from~",
        10, "She said she would block off the rest of Hotland after I followed her~",
        11, "Foolish nerd~ A spider NEVER leaves her web~ (Except to sell pastries~)",
        12, "Ah, but I do feel a little regret over it now...",
        13, "Yes, I should have wrapped her up when I had the chance~",
        14, "She looked like she would have made a juicy donut~~~"
    );

    private static final String[] END_NORMAL = {
        "You're still alive? Ahuhuhu~",
        "Oh, my pet~ Looks like it's time for dessert~",
        "Huh? A telegram from the spiders in the RUINS?",
        "What? They're saying that they saw you, and...",
        "... even if you are stingy, you never hurt a single spider!",
        "Oh my, this has all been a big misunderstanding~",
        "I thought you were someone that hated spiders~",
        "The person who asked for that SOUL...",
        "They must have meant a DIFFERENT human in a striped shirt~",
        "Sorry for all the trouble~ Ahuhuhu~",
        "I'll make it up to you~",
        "You can come back here any time... And, for no charge at all...",
        "I'll wrap you up and let you play with my pet again!",
        "Ahuhuhuhuhuhu~ Just kidding~",
        "I'll SPARE you now~",
    };

    private static final String[] END_MURDER = {
        "You're still alive? Ahuhuhu~",
        "Oh, my pet~ Looks like it's time for dessert~",
        "Huh? A telegram from the spiders in the RUINS?",
        "What? They're saying that they saw you, and...",
        "They say even if you are a hyper-violent murderer...",
        "You never laid a single finger on a spider!",
        "Oh my, this has all been a big misunderstanding~",
        "I thought you were someone that hated spiders~",
        "The person who warned me about you...",
        "They really had no idea what they were talking about~",
        "Sorry for all the trouble~ Ahuhuhu~",
        "I'll make it up to you~",
        "You can come back here any time... And, for no charge at all...",
        "I'll wrap you up and let you play with my pet again!",
        "Ahuhuhuhuhuhu~ Just kidding~",
        "I'll SPARE you now~",
    };
}
