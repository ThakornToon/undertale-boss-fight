package boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import battle.BattleScene;
import battle.MercySystem;
import core.GlobalState;
import core.InputHandler;
import core.TurnManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the Asgore fight headlessly: the broken-mercy start, the turn-counted
 * fire/hand/swipe gauntlet, and the {@code fivedamage} endgame where FIGHTing him
 * low makes him kneel and ends the fight (you can never SPARE).
 */
class AsgoreBossTest {

    private static final GlobalState G = GlobalState.get();

    @BeforeEach
    void freshState() {
        G.newGame();
    }

    /**
     * Run frames until the opening cutscene (narration + farewell) finishes, Asgore
     * takes his first attack turn (GML: "* ASGORE attacks!" → mnfight 3), and the
     * command menu finally opens. The cutscene auto-advances, so this just spins
     * frames; a high HP keeps the test soul alive through that first attack.
     */
    private static void runIntro(BattleScene scene) {
        if (G.maxhp < 1000) {
            G.maxhp = 1_000_000;
            G.hp = 1_000_000;
        }
        int guard = 0;
        while (G.mnfight != TurnManager.MENU && guard < 2000) {
            scene.update();
            guard++;
        }
        assertTrue(G.mnfight == TurnManager.MENU, "intro should finish and open the menu");
    }

    @Test
    void startsWithBrokenMercy() {
        InputHandler input = new InputHandler();
        BattleScene scene = new BattleScene(null, input, BossRegistry.ASGORE);

        // Mercy is broken from setup, before the intro even finishes.
        assertEquals(MercySystem.BROKEN, G.mercy,
                "Asgore destroys the SPARE button — mercy must be broken");
        assertEquals(3500, G.monstermaxhp[G.myself], "Asgore HP from scr_monstersetup");

        runIntro(scene); // the opening cutscene plays, the first attack runs, then the menu opens
    }

    @Test
    void enemyTurnsSpawnAttacksAcrossTheGauntlet() {
        InputHandler input = new InputHandler();
        BattleScene scene = new BattleScene(null, input, BossRegistry.ASGORE);
        G.maxhp = 1_000_000;
        G.hp = 1_000_000;

        runIntro(scene);

        boolean spawned = false;
        // Run the first dozen turns (fire columns, hands, sine fire, swipes, ...).
        for (int turn = 0; turn < 12; turn++) {
            G.mnfight = TurnManager.ENTER_ENEMY;
            scene.update();                 // chooseAttack spawns this turn's pattern

            int guard = 0;
            while (G.mnfight != TurnManager.MENU && guard < 8000) {
                if (scene.bulletCount() > 0) {
                    spawned = true;
                }
                scene.update();
                guard++;
            }
            assertTrue(guard < 8000, "every enemy turn must end and return to the menu");
        }
        assertTrue(spawned, "fire/hand patterns should put bullets on screen");
    }

    @Test
    void cannotSpareButFightingLowMakesHimKneel() {
        InputHandler input = new InputHandler();
        BattleScene scene = new BattleScene(null, input, BossRegistry.ASGORE);
        G.maxhp = 1_000_000;
        G.hp = 1_000_000;
        G.at = 40;                          // a strong run so the war of attrition ends quickly

        runIntro(scene); // play the intro, then the menu opens

        // SPARE is impossible — selecting it just gets blocked, never ends the fight.
        G.myfight = 4;
        G.mnfight = TurnManager.ACT_SPARE;
        scene.update();
        assertFalse(scene.boss().battleOver, "Asgore can never be spared");

        // FIGHT him down until the fivedamage branch fires.
        int turnGuard = 0;
        while (G.fivedamage != 1 && !scene.boss().battleOver && turnGuard < 120) {
            // Make sure we're at the menu before issuing FIGHT.
            int back = 0;
            while (G.mnfight != TurnManager.MENU && G.mnfight >= 0 && back < 8000) {
                scene.update();
                back++;
            }
            if (G.mnfight < 0 || scene.boss().battleOver) {
                break; // ending cutscene has begun
            }
            fightOnce(input, scene);
            turnGuard++;
        }
        assertTrue(G.fivedamage == 1 || scene.boss().battleOver,
                "FIGHTing Asgore low should trigger the fivedamage endgame");

        // Run the kneel cutscene to completion.
        int guard = 0;
        boolean sawAh = false;
        while (!scene.boss().battleOver && guard < 4000) {
            scene.update();
            String line = scene.boss().dialogue;
            if (line != null && line.startsWith("Ah")) {
                sawAh = true;
            }
            guard++;
        }
        assertTrue(sawAh, "Asgore should murmur 'Ah . . .' as he kneels");
        assertTrue(scene.boss().battleOver, "the kneel cutscene should end the fight");
        assertTrue(scene.boss().banner.toLowerCase().contains("kneel"),
                "the outcome banner should describe Asgore kneeling");
    }

    @Test
    void everyAttackRendersWithoutError() {
        InputHandler input = new InputHandler();
        BattleScene scene = new BattleScene(null, input, BossRegistry.ASGORE);
        G.maxhp = 1_000_000;
        G.hp = 1_000_000;

        java.awt.image.BufferedImage frame = new java.awt.image.BufferedImage(
                640, 480, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = frame.createGraphics();

        // Play (and render) the opening cutscene + first attack turn, then the menu.
        int introGuard = 0;
        while (G.mnfight != TurnManager.MENU && introGuard < 2000) {
            scene.update();
            scene.render(g);
            introGuard++;
        }
        scene.render(g);   // menu render

        // Walk a couple of laps through the whole attack table (incl. the swipes,
        // firestorm overlay, and the loop) rendering each frame to catch draw NPEs.
        for (int turn = 0; turn < 24; turn++) {
            G.mnfight = TurnManager.ENTER_ENEMY;
            scene.update();
            int guard = 0;
            while (G.mnfight != TurnManager.MENU && guard < 8000) {
                scene.update();
                scene.render(g);
                guard++;
            }
            assertTrue(guard < 8000, "turn must end");
        }
        g.dispose();
    }

    /** One full FIGHT: pick FIGHT, stop the bar, dismiss the result (→ enemy turn). */
    private static void fightOnce(InputHandler input, BattleScene scene) {
        tapConfirm(input, scene);   // MAIN: FIGHT → attack bar
        tapConfirm(input, scene);   // FIGHTBAR: stop → deal damage → result
        tapConfirm(input, scene);   // RESULT: dismiss → enter enemy turn (or cutscene)
    }

    /** Press+release CONFIRM (Z) through the dispatcher, one logic frame. */
    private static void tapConfirm(InputHandler input, BattleScene scene) {
        java.awt.Component src = new java.awt.Canvas();
        input.dispatchKeyEvent(new java.awt.event.KeyEvent(
                src, java.awt.event.KeyEvent.KEY_PRESSED, 0L, 0,
                java.awt.event.KeyEvent.VK_Z, 'z'));
        input.poll();
        scene.update();
        input.dispatchKeyEvent(new java.awt.event.KeyEvent(
                src, java.awt.event.KeyEvent.KEY_RELEASED, 0L, 0,
                java.awt.event.KeyEvent.VK_Z, 'z'));
        input.poll();
    }
}
