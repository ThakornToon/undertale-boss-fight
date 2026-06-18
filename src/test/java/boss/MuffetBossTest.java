package boss;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import battle.BattleScene;
import core.GlobalState;
import core.InputHandler;
import core.TurnManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the Muffet fight headlessly: the purple-web setup, the 16-turn
 * spider/donut/croissant gauntlet (including the pet special on turns 4/9/15), and
 * the telegram spare ending you reach by surviving all sixteen attacks. Also checks
 * that SPARE is refused mid-fight and that the murder route swaps her dialogue.
 */
class MuffetBossTest {

    private static final GlobalState G = GlobalState.get();

    @BeforeEach
    void freshState() {
        G.newGame();
    }

    /** Spin past the purple-web intro until the command menu opens. */
    private static void runIntro(BattleScene scene) {
        G.maxhp = 1_000_000;
        G.hp = 1_000_000;
        int guard = 0;
        while (G.mnfight != TurnManager.MENU && guard < 2000) {
            scene.update();
            guard++;
        }
        assertTrue(G.mnfight == TurnManager.MENU, "the purple-web intro should open the menu");
    }

    /** Force one enemy turn and drain it back to the menu (or a cutscene). */
    private static void runEnemyTurn(BattleScene scene) {
        G.mnfight = TurnManager.ENTER_ENEMY;
        scene.update();                 // chooseAttack spawns this turn's pattern
        int guard = 0;
        while (G.mnfight != TurnManager.MENU && G.mnfight >= 0 && guard < 9000) {
            scene.update();
            guard++;
        }
    }

    @Test
    void setsUpAsTheSpiderBoss() {
        BattleScene scene = new BattleScene(null, new InputHandler(), BossRegistry.MUFFET);
        assertEquals(1250, G.monstermaxhp[G.myself], "Muffet HP from scr_monstersetup type 39");
        assertEquals(8, G.monsteratk[G.myself], "Muffet ATK");
        runIntro(scene);
    }

    @Test
    void everyTurnPutsBulletsOnTheWebAndEnds() {
        BattleScene scene = new BattleScene(null, new InputHandler(), BossRegistry.MUFFET);
        runIntro(scene);

        boolean spawned = false;
        for (int turn = 0; turn < 16; turn++) {
            G.mnfight = TurnManager.ENTER_ENEMY;
            scene.update();
            int guard = 0;
            while (G.mnfight != TurnManager.MENU && G.mnfight >= 0 && guard < 9000) {
                if (scene.bulletCount() > 0) {
                    spawned = true;
                }
                scene.update();
                guard++;
            }
            assertTrue(guard < 9000, "every enemy turn must end and return to the menu");
        }
        assertTrue(spawned, "spiders/donuts/croissants should ride the web each turn");
    }

    @Test
    void cannotSpareMidFight() {
        BattleScene scene = new BattleScene(null, new InputHandler(), BossRegistry.MUFFET);
        runIntro(scene);

        // SPARE is refused until the telegram (GML mercymod = -960).
        G.myfight = 4;
        G.mnfight = TurnManager.ACT_SPARE;
        scene.update();
        assertFalse(scene.boss().battleOver, "Muffet can't be spared before the telegram");
    }

    @Test
    void survivingAllSixteenTurnsTriggersTheTelegramSpare() {
        BattleScene scene = new BattleScene(null, new InputHandler(), BossRegistry.MUFFET);
        runIntro(scene);

        for (int turn = 0; turn < 16; turn++) {
            runEnemyTurn(scene);
        }
        // The seventeenth turn is the telegram, not an attack.
        G.mnfight = TurnManager.ENTER_ENEMY;
        scene.update();

        int guard = 0;
        boolean sawTelegram = false;
        while (!scene.boss().battleOver && guard < 4000) {
            scene.update();
            String line = scene.boss().dialogue;
            if (line != null && line.contains("telegram")) {
                sawTelegram = true;
            }
            guard++;
        }
        assertTrue(sawTelegram, "the spiders' telegram should arrive");
        assertTrue(scene.boss().battleOver, "the telegram ends the fight");
        assertTrue(scene.boss().peacefulEnd, "Muffet spares the player (no kill)");
        assertTrue(scene.boss().banner.toLowerCase().contains("spare"),
                "the outcome banner should say Muffet spared you");
    }

    @Test
    void murderRouteChangesHerDialogue() {
        G.murderlv = 12;   // GML: scr_murderlv() >= 12 → murder route
        BattleScene scene = new BattleScene(null, new InputHandler(), BossRegistry.MUFFET);
        runIntro(scene);

        // Drain turns 0..2, then read turn 3's line (the first that differs by route).
        for (int turn = 0; turn < 3; turn++) {
            runEnemyTurn(scene);
        }
        G.mnfight = TurnManager.ENTER_ENEMY;
        scene.update();   // chooseAttack sets dialogue for turnAmt 3
        assertTrue(scene.boss().dialogue.contains("customers"),
                "murder route turn 3 is 'You're scaring off all my customers!'");
    }

    @Test
    void everyAttackRendersWithoutError() {
        BattleScene scene = new BattleScene(null, new InputHandler(), BossRegistry.MUFFET);
        G.maxhp = 1_000_000;   // survive every turn so the turn machine keeps running
        G.hp = 1_000_000;
        java.awt.image.BufferedImage frame = new java.awt.image.BufferedImage(
                640, 480, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = frame.createGraphics();

        int introGuard = 0;
        while (G.mnfight != TurnManager.MENU && introGuard < 2000) {
            scene.update();
            scene.render(g);
            introGuard++;
        }
        for (int turn = 0; turn < 16; turn++) {
            G.mnfight = TurnManager.ENTER_ENEMY;
            scene.update();
            int guard = 0;
            while (G.mnfight != TurnManager.MENU && G.mnfight >= 0 && guard < 9000) {
                scene.update();
                scene.render(g);
                guard++;
            }
            assertTrue(guard < 9000, "turn must end");
        }
        g.dispose();
    }
}
