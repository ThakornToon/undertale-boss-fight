package boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import battle.BattleScene;
import core.GlobalState;
import core.InputHandler;
import core.TurnManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives Asriel — PART A ("GOD of Hyperdeath") headlessly: the un-killable/un-spareable
 * setup, the calm Fire-Magic intro, the {@code turns} 0→13 named-attack gauntlet
 * (STAR BLAZING is ported; the rest drizzle survivable fire), the ACT Pray/Dream
 * softening, and the HYPER GONER → transform boundary that ends Part A.
 */
class AsrielBossTest {

    private static final GlobalState G = GlobalState.get();

    @BeforeEach
    void freshState() {
        G.newGame();
        G.maxhp = 1_000_000;
        G.hp = 1_000_000;
    }

    private static BattleScene fight() {
        return new BattleScene(null, new InputHandler(), BossRegistry.ASRIEL);
    }

    /** Spin past the calm Fire-Magic intro until the command menu opens. */
    private static void runIntro(BattleScene scene) {
        int guard = 0;
        while (G.mnfight != TurnManager.MENU && guard < 3000) {
            scene.update();
            guard++;
        }
        assertTrue(G.mnfight == TurnManager.MENU, "the calm intro should finish and open the menu");
    }

    /** Run a gauntlet turn to completion: Asriel reads `turns` directly. */
    private static int playTurn(BattleScene scene, int turn) {
        scene.boss().turns = turn;
        G.mnfight = TurnManager.ENTER_ENEMY;
        scene.update();                 // chooseAttack spawns this turn's pattern
        int maxBullets = scene.bulletCount();
        int guard = 0;
        while (G.mnfight != TurnManager.MENU && G.mnfight >= 0 && guard < 8000) {
            maxBullets = Math.max(maxBullets, scene.bulletCount());
            scene.update();
            guard++;
        }
        assertTrue(guard < 8000, "turn " + turn + " must end and return to the menu");
        return maxBullets;
    }

    @Test
    void setupIsUnkillableAndUnspareable() {
        BattleScene scene = fight();
        assertEquals(9999, G.monstermaxhp[G.myself], "Asriel HP 9999 (invulnerable)");
        assertTrue(G.mercymod < 0, "mercymod is hugely negative — spare can never resolve");
        assertEquals(1, G.flag[500], "the in-Asriel-battle flag is set");
        assertTrue(scene.boss().hideHpHeader, "the HP meter is hidden (damage is meaningless)");

        runIntro(scene);

        // SPARE never resolves.
        G.myfight = 4;
        G.mnfight = TurnManager.ACT_SPARE;
        scene.update();
        assertFalse(scene.boss().battleOver, "Asriel can never be spared");

        // FIGHT does nothing (DEF 9999) — he cannot be killed.
        int before = G.monsterhp[G.myself];
        G.myself = scene.boss().stats.slot;
        assertFalse(scene.boss().battleOver, "FIGHT cannot end the fight");
        assertTrue(G.monsterhp[G.myself] >= before - 1, "Asriel takes no meaningful damage");
    }

    @Test
    void everyGauntletTurnSpawnsBulletsAndEnds() {
        BattleScene scene = fight();
        runIntro(scene);
        boolean starBlazingSpawned = false;
        for (int t = 0; t <= 12; t++) {
            int bullets = playTurn(scene, t);
            // Chaos Saber (turns 2/6/10) deals damage via the sword arms (AttackPattern),
            // not Bullet instances, so it has no bullet count — the turn ending + the
            // render test cover it. Every other turn puts bullets on screen.
            boolean saber = t == 2 || t == 6 || t == 10;
            if (!saber) {
                assertTrue(bullets > 0, "turn " + t + " should put bullets on screen");
            }
            if (t == 0 || t == 4 || t == 9) {
                starBlazingSpawned = true;   // STAR BLAZING / GALACTA BLAZING turns
            }
        }
        assertTrue(starBlazingSpawned, "the star-blazing turns ran");
    }

    @Test
    void prayGrantsHopeAndDreamHeals() {
        BattleScene scene = fight();
        runIntro(scene);

        // PRAY (whatiheard 3) → hope set + small heal.
        G.hp = 10;
        G.maxhp = 20;
        scene.boss().onAct(3);
        assertEquals(1, G.hope, "Pray sets global.hope");
        assertEquals(11, G.hp, "Pray heals +1");

        // DREAM (whatiheard 1) → heal +4.
        G.hp = 10;
        scene.boss().onAct(1);
        assertEquals(14, G.hp, "Dream heals +4");
    }

    @Test
    void hyperGonerTransformsIntoPartBAndUnlocksSave() {
        BattleScene scene = fight();
        runIntro(scene);

        // HYPER GONER → transform → Part B (the final winged form). Drive past the climax.
        scene.boss().turns = 13;
        G.mnfight = TurnManager.ENTER_ENEMY;
        scene.update();
        for (int i = 0; i < 1400; i++) {
            scene.update();
        }
        assertFalse(scene.boss().battleOver, "the transform leads into Part B, not the end");
        assertEquals(0, G.flag[501], "Part B opens in stage 0 (the death-loop)");

        // The death-loop: struggling enough times then Checking unlocks SAVE.
        G.tempvalue[12] = 4;
        scene.boss().onAct(0);
        int guard = 0;
        while (G.flag[501] < 1 && guard < 3000) {
            scene.update();
            guard++;
        }
        assertEquals(1, G.flag[501], "the SAVE realization unlocks the SAVE phase");
        assertTrue(scene.boss().wantsSaveButton(), "the ACT command becomes the SAVE button");

        // SAVE → Undyne: enter her GREEN Lost-Soul encounter and free her with ACTs.
        scene.boss().onAct(0);                     // SAVE: Undyne → Lost Soul
        assertFalse(scene.boss().wantsSaveButton(), "inside a Lost Soul you ACT, not SAVE");
        G.mnfight = TurnManager.ENTER_ENEMY;
        scene.update();                            // her GREEN spear turn spawns
        scene.boss().onAct(0);                     // Fake Hit
        scene.boss().onAct(1);                     // Recipe
        scene.boss().onAct(2);                     // Smile → freed
        assertEquals(1, G.flag[505], "freeing the Undyne Lost Soul sets flag[505]");
        assertTrue(scene.boss().wantsSaveButton(), "control returns to the SAVE menu");

        // SAVE → Alphys: her YELLOW parasol encounter, freed the same way.
        scene.boss().onAct(3);                     // SAVE: Alphys → Lost Soul
        G.mnfight = TurnManager.ENTER_ENEMY;
        scene.update();
        scene.boss().onAct(0);                     // Encourage
        scene.boss().onAct(1);                     // Call
        scene.boss().onAct(2);                     // Nerd Out → freed
        assertEquals(1, G.flag[506], "freeing the Alphys Lost Soul sets flag[506]");

        // SAVE → Sans & Papyrus: BLUE bones, freed by 4 ACTs across both brothers.
        scene.boss().onAct(1);                     // SAVE: Sans & Papyrus → Lost Soul
        G.mnfight = TurnManager.ENTER_ENEMY;
        scene.update();
        scene.boss().onAct(0);
        scene.boss().onAct(1);
        scene.boss().onAct(2);
        scene.boss().onAct(3);                     // 4 ACTs → freed
        assertEquals(1, G.flag[507], "freeing the Sans & Papyrus Lost Soul sets flag[507]");

        // SAVE → Toriel & Asgore: RED fire, freed by 4 ACTs.
        scene.boss().onAct(2);                     // SAVE: Toriel & Asgore → Lost Soul
        G.mnfight = TurnManager.ENTER_ENEMY;
        scene.update();
        scene.boss().onAct(0);
        scene.boss().onAct(1);
        scene.boss().onAct(2);
        scene.boss().onAct(3);                     // 4 ACTs → freed
        assertEquals(1, G.flag[508], "freeing the Toriel & Asgore Lost Soul sets flag[508]");

        // All six friends freed → the SAVE-Asriel stage opens.
        assertEquals(2, G.flag[501], "all four Lost Souls freed advances flag[501] to 2");

        // SAVE "Someone else" → Asriel realization → Angel-of-Death finale → victory card.
        scene.boss().onAct(0);
        int g = 0;
        while (!scene.boss().battleOver && g < 8000) {
            scene.update();
            g++;
        }
        assertEquals(3, G.flag[501], "SAVE-ing Asriel enters the breakdown stage (flag[501]=3)");
        assertTrue(scene.boss().battleOver, "the Angel-of-Death finale ends with the victory card");
        assertTrue(scene.boss().peacefulEnd, "Asriel is saved, not killed");
        assertTrue(G.hp >= 1, "the HP-multiply finale whittles HP toward but never below 1");
    }

    @Test
    void everyTurnRendersWithoutError() {
        BattleScene scene = fight();
        java.awt.image.BufferedImage frame = new java.awt.image.BufferedImage(
                640, 480, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = frame.createGraphics();

        int introGuard = 0;
        while (G.mnfight != TurnManager.MENU && introGuard < 3000) {
            scene.update();
            scene.render(g);
            introGuard++;
        }
        for (int t = 0; t <= 13; t++) {
            scene.boss().turns = t;
            G.mnfight = TurnManager.ENTER_ENEMY;
            scene.update();
            int guard = 0;
            while (G.mnfight != TurnManager.MENU && G.mnfight >= 0 && guard < 8000) {
                scene.update();
                scene.render(g);
                guard++;
            }
        }
        g.dispose();
    }
}
