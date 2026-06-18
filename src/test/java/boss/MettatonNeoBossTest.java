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
 * Drives Mettaton NEO headlessly: the genocide-only cutscene boss. He never attacks
 * (mercymod = -999999 skips his turn instantly), the FIGHT is a forced one-shot kill,
 * and the killing blow plays the long death monologue before he explodes.
 */
class MettatonNeoBossTest {

    private static final GlobalState G = GlobalState.get();

    @BeforeEach
    void freshState() {
        G.newGame();
        G.murderlv = 20;
        G.maxhp = 1_000_000;
        G.hp = 1_000_000;
    }

    @Test
    void statsAndMercy() {
        new BattleScene(null, new InputHandler(), BossRegistry.METTATON_NEO);
        assertEquals(30000, G.monstermaxhp[G.myself], "NEO HP from scr_monstersetup");
        assertEquals(-999999, G.mercymod, "mercymod = -999999 skips his turn / blocks spare");
        assertEquals(TurnManager.MENU, G.mnfight, "no intro cutscene — the menu opens at once");
    }

    @Test
    void enemyTurnSkipsInstantlyWithNoBullets() {
        BattleScene scene = new BattleScene(null, new InputHandler(), BossRegistry.METTATON_NEO);
        G.mnfight = TurnManager.ENTER_ENEMY;
        int guard = 0;
        boolean sawBullets = false;
        while (G.mnfight != TurnManager.MENU && guard < 200) {
            scene.update();
            if (scene.bulletCount() > 0) {
                sawBullets = true;
            }
            guard++;
        }
        assertEquals(TurnManager.MENU, G.mnfight, "his turn must skip straight back to the menu");
        assertFalse(sawBullets, "NEO never fires a bullet");
    }

    @Test
    void killingBlowPlaysLongMonologueThenExplodes() {
        BattleScene scene = new BattleScene(null, new InputHandler(), BossRegistry.METTATON_NEO);

        scene.boss().onDefeat();   // the forced one-shot kill routes here
        assertFalse(scene.boss().battleOver, "the death cutscene plays before the fight ends");

        java.awt.image.BufferedImage frame = new java.awt.image.BufferedImage(
                640, 480, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = frame.createGraphics();

        boolean sawHoldingBack = false;
        boolean sawAlphys = false;
        int guard = 0;
        while (!scene.boss().battleOver && guard < 4000) {
            scene.update();
            scene.render(g);   // also catches draw NPEs during the fade-white
            String line = scene.boss().dialogue;
            if (line != null && line.contains("HOLDING BACK")) {
                sawHoldingBack = true;
            }
            if (line != null && line.contains("HUMANS WILL LIVE ON")) {
                sawAlphys = true;
            }
            guard++;
        }
        g.dispose();

        assertTrue(sawHoldingBack, "the long monologue includes 'YOU WERE HOLDING BACK.'");
        assertTrue(sawAlphys, "...and ends knowing Alphys and the humans will live on");
        assertTrue(scene.boss().battleOver, "after the explosion the fight ends");
    }
}
