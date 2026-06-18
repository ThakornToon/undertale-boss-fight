package boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import battle.BattleScene;
import bullet.undyne.GreenSpearGen;
import core.EntityManager;
import core.GlobalState;
import core.InputHandler;
import core.TurnManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives Undyne the Undying headlessly: her stats and un-spareable mercy, the GREEN
 * shield ↔ RED dodge schedule across the {@code order} escalation, and the
 * reform-and-die endgame (HP→0 does not kill her; she reforms for a short second
 * phase, then finally fades with "This world will live on...!").
 */
class UndyneBossTest {

    private static final GlobalState G = GlobalState.get();

    @BeforeEach
    void freshState() {
        G.newGame();
    }

    private static void runIntro(BattleScene scene) {
        G.maxhp = 1_000_000;
        G.hp = 1_000_000;
        int guard = 0;
        while (G.mnfight != TurnManager.MENU && guard < 3000) {
            scene.update();
            guard++;
        }
        assertTrue(G.mnfight == TurnManager.MENU, "intro should finish and open the menu");
    }

    /** Force one enemy turn, return the box top (distinguishes the boxes), drain to menu. */
    private static double turnBoxTop(BattleScene scene) {
        G.mnfight = TurnManager.ENTER_ENEMY;
        scene.update();                         // chooseAttack snaps the box + spawns the attack
        double top = G.idealborder[2];
        int guard = 0;
        while (G.mnfight != TurnManager.MENU && G.mnfight >= 0 && guard < 6000) {
            scene.update();
            guard++;
        }
        return top;
    }

    @Test
    void setupStatsAndCannotBeSpared() {
        BattleScene scene = new BattleScene(null, new InputHandler(), BossRegistry.UNDYNE);
        assertTrue(scene.boss() instanceof UndyneBoss, "UNDYNE group builds UndyneBoss");
        assertEquals(1500, G.monstermaxhp[G.myself], "Undyne HP 1500 (flag[351])");
        assertEquals(50, G.monsteratk[G.myself], "Undyne ATK 50");
        assertEquals(20, G.monsterdef[G.myself], "Undyne DEF 20");
        assertTrue(G.mercymod < 0, "mercymod hugely negative — she can never be spared");

        runIntro(scene);
        // SPARE is refused and never ends the fight.
        G.myfight = 4;
        G.mnfight = TurnManager.ACT_SPARE;
        scene.update();
        assertFalse(scene.boss().battleOver, "Undyne can never be spared");
    }

    @Test
    void greenRedScheduleAcrossOrders() {
        BattleScene scene = new BattleScene(null, new InputHandler(), BossRegistry.UNDYNE);
        runIntro(scene);   // order 1 (green) already fired during the intro; next is order 2

        // Boxes: GREEN top 175, RED follow top 165, RED rise top 120.
        double[] tops = new double[12];
        for (int i = 0; i < tops.length; i++) {   // orders 2,3,4,5,6,7,8,9,10,11,12,13
            tops[i] = turnBoxTop(scene);
        }
        // order 2-5 GREEN
        assertEquals(175, tops[0], 0.5, "order 2 is GREEN");
        assertEquals(175, tops[3], 0.5, "order 5 is GREEN");
        // order 6-8 RED follow (taller box, top 165)
        assertEquals(165, tops[4], 0.5, "order 6 is RED follow");
        assertEquals(165, tops[6], 0.5, "order 8 is RED follow");
        // order 9-11 GREEN again
        assertEquals(175, tops[7], 0.5, "order 9 is GREEN");
        // order 12 RED rising (small box, top 216)
        assertEquals(216, tops[10], 0.5, "order 12 is RED rising-spears");
    }

    @Test
    void hpZeroReformsThenFinallyDies() {
        BattleScene scene = new BattleScene(null, new InputHandler(), BossRegistry.UNDYNE);
        runIntro(scene);

        // Drop her to 0 and trigger the lethal-FIGHT hook: she must NOT die — she reforms.
        G.monsterhp[G.myself] = 0;
        scene.boss().onDefeat();
        assertFalse(scene.boss().battleOver, "HP 0 does not kill her — she reforms");

        // Drive the reform cutscene + the scripted second phase to the real death.
        int guard = 0;
        while (!scene.boss().battleOver && guard < 40000) {
            if (G.mnfight == TurnManager.MENU) {
                G.mnfight = TurnManager.ENTER_ENEMY;   // act for the player so phase 2 advances
            }
            scene.update();
            guard++;
        }
        assertTrue(scene.boss().battleOver, "the scripted second phase must end the fight");
        assertTrue(scene.boss().banner.contains("live on"),
                "she fades with \"This world will live on...!\"");
    }

    @Test
    void genocideRouteKillsHerWithTheUndyingDeath() {
        // GENOCIDE seed (the boss-select murder gate is >= 7).
        G.murderlv = 20;
        G.lv = 20;
        G.at = 40;
        BattleScene scene = new BattleScene(null, new InputHandler(), BossRegistry.UNDYNE);
        // The Undying form: a huge HP pool, killable only via the ×21 (min 600) multiplier.
        assertEquals(15000, G.monstermaxhp[G.myself], "genocide Undying HP pool");
        assertTrue(G.mercymod < 0, "still un-spareable on genocide");

        runIntro(scene);
        // Unlike normal, HP 0 in genocide really kills her (no reform).
        G.monsterhp[G.myself] = 0;
        scene.boss().onDefeat();
        int guard = 0;
        while (!scene.boss().battleOver && guard < 40000) {
            scene.update();
            guard++;
        }
        assertTrue(scene.boss().battleOver, "genocide death cutscene ends the fight");
        assertTrue(scene.boss().banner.contains("UNDYING"), "the UNDYING fades away");
    }

    @Test
    void greenSpearQueueAppliesScrSrDefaults() {
        EntityManager m = new EntityManager();
        GreenSpearGen gen = new GreenSpearGen(m, 320, 240, 10, 7);
        // scr_sr: gap 0 → 1 (non-saver), speed 0 → 1; the entry is still queued.
        gen.queue(0, 0, 0, 0);
        gen.queue(1, 1, 1.5, 2);
        assertEquals(2, gen.queued(), "both scr_sr calls queue a spear");
    }
}
