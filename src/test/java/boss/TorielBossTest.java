package boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import battle.BattleScene;
import battle.DamageSystem;
import battle.MercySystem;
import core.GlobalState;
import core.InputHandler;
import core.TurnManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the Toriel fight headlessly: her stat block, the five fire/hands attacks
 * rolled off {@code mycommand}, the refuse-to-fight spare route (the conversation
 * counter → relent monologue), the HP≤150 DEF-collapse kill trap with its death
 * monologue, and the GENOCIDE one-hit cold death line.
 */
class TorielBossTest {

    private static final GlobalState G = GlobalState.get();

    @BeforeEach
    void freshState() {
        G.newGame();
    }

    /** Toriel lets the player move first — there is no intro; the menu opens at once. */
    private static void runToMenu(BattleScene scene) {
        G.maxhp = 1_000_000;
        G.hp = 1_000_000;
        int guard = 0;
        while (G.mnfight != TurnManager.MENU && guard < 2000) {
            scene.update();
            guard++;
        }
        assertTrue(G.mnfight == TurnManager.MENU, "the player takes the first turn");
    }

    /** Force one enemy turn and drain it back to the menu (or into a cutscene). */
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
    void setsUpAsTheFirstBoss() {
        BattleScene scene = new BattleScene(null, new InputHandler(), BossRegistry.TORIEL);
        assertEquals(440, G.monstermaxhp[G.myself], "Toriel HP from scr_monstersetup type 10");
        assertEquals(6, G.monsteratk[G.myself], "Toriel ATK");
        assertEquals(1, G.monsterdef[G.myself], "Toriel DEF starts at 1");
        assertEquals(-20000, G.mercymod, "mercymod -20000: SPARE never lights via the meter");
        assertFalse(G.mercy == MercySystem.BROKEN, "the MERCY button stays on the row");
        runToMenu(scene);
    }

    @Test
    void everyAttackTurnSpawnsBulletsAndEnds() {
        BattleScene scene = new BattleScene(null, new InputHandler(), BossRegistry.TORIEL);
        runToMenu(scene);

        boolean spawned = false;
        for (int turn = 0; turn < 12; turn++) {
            // Chip her HP each turn so the turn reads as "being fought" (HP changed),
            // keeping the conversation counter low so she keeps attacking.
            G.monsterhp[G.myself] = 400 - turn * 5;
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
        assertTrue(spawned, "her fire/hand attacks should put bullets on the field");
    }

    @Test
    void refusingToFightEventuallySparesHer() {
        BattleScene scene = new BattleScene(null, new InputHandler(), BossRegistry.TORIEL);
        runToMenu(scene);

        // Never damage her: every undamaged turn advances the conversation counter,
        // and at 13 she stops attacking and runs the relent monologue.
        for (int turn = 0; turn < 14; turn++) {
            if (G.mnfight == TurnManager.MENU) {
                runEnemyTurn(scene);
            }
        }
        int guard = 0;
        while (!scene.boss().battleOver && guard < 8000) {
            scene.update();
            guard++;
        }
        assertTrue(scene.boss().battleOver, "the relent monologue ends the fight");
        assertTrue(scene.boss().peacefulEnd, "refusing to fight spares Toriel (no kill)");
        assertTrue(scene.boss().banner.toLowerCase().contains("spared"),
                "the outcome banner should say Toriel was spared");
    }

    @Test
    void normalFightCentredHitCapsAt42() {
        // Toriel's setup applies this min-damage compensation because NORMAL player ATK
        // is 0 (no weapon system). A perfectly-centred hit should land exactly 42, an
        // off-centre one less.
        DamageSystem d = new DamageSystem();
        d.reset();
        d.playerDamageMultiplier = 2;
        d.playerMinDamage = 42;
        G.at = 0;
        G.lv = 1;
        G.monsterdef[0] = 1;          // Toriel's normal DEF
        G.monsterhp[0] = 440;
        assertEquals(42, d.monsterHurt(0, 1.0), "centred NORMAL FIGHT caps at 42");
        G.monsterhp[0] = 440;
        assertTrue(d.monsterHurt(0, 0.0) < 42, "an off-centre hit lands less than 42");
    }

    @Test
    void lowHpCollapsesHerDefense() {
        BattleScene scene = new BattleScene(null, new InputHandler(), BossRegistry.TORIEL);
        runToMenu(scene);

        // GML Step hurtanim==2: a hit that leaves her at/below 150 HP drops DEF to -140.
        G.monsterhp[G.myself] = 150;
        scene.boss().onDamaged();
        assertEquals(-140, G.monsterdef[G.myself], "HP<=150 collapses Toriel's DEF (the lethal trap)");
    }

    @Test
    void killingHerPlaysTheDeathMonologue() {
        BattleScene scene = new BattleScene(null, new InputHandler(), BossRegistry.TORIEL);
        runToMenu(scene);

        G.monsterhp[G.myself] = 0;
        scene.boss().onDefeat();         // beginDeath
        boolean sawLine = false;
        int guard = 0;
        while (!scene.boss().battleOver && guard < 8000) {
            scene.update();
            String line = scene.boss().dialogue;
            if (line != null && line.contains("stronger")) {
                sawLine = true;
            }
            guard++;
        }
        assertTrue(sawLine, "her death monologue includes 'You are stronger than I thought...'");
        assertTrue(scene.boss().battleOver, "the death monologue ends the fight");
        assertTrue(scene.boss().banner.toLowerCase().contains("defeated"),
                "the outcome banner should say Toriel was defeated");
    }

    @Test
    void genocideIsAOneHitColdDeath() {
        G.murderlv = 7;   // GML scr_murderlv() >= 1 → DEF -9999, one-hit kill
        BattleScene scene = new BattleScene(null, new InputHandler(), BossRegistry.TORIEL);
        assertEquals(-9999, G.monsterdef[G.myself], "genocide sets DEF -9999 in setup");
        runToMenu(scene);

        G.monsterhp[G.myself] = 0;
        scene.boss().onDefeat();
        boolean sawColdLine = false;
        int guard = 0;
        while (!scene.boss().battleOver && guard < 8000) {
            scene.update();
            String line = scene.boss().dialogue;
            if (line != null && line.contains("hate me")) {
                sawColdLine = true;
            }
            guard++;
        }
        assertTrue(sawColdLine, "genocide death uses the cold 'really hate me that much?' line");
        assertTrue(scene.boss().battleOver, "the fight ends");
    }

    @Test
    void everyAttackRendersWithoutError() {
        BattleScene scene = new BattleScene(null, new InputHandler(), BossRegistry.TORIEL);
        runToMenu(scene);
        java.awt.image.BufferedImage frame = new java.awt.image.BufferedImage(
                640, 480, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = frame.createGraphics();

        for (int turn = 0; turn < 12; turn++) {
            G.monsterhp[G.myself] = 400 - turn * 5;   // keep her attacking
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
