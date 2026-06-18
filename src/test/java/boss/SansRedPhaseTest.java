package boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import battle.BattleScene;
import core.GlobalState;
import core.InputHandler;
import core.TurnManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the Sans fight headlessly with a FIGHT every turn to prove the
 * screenshot-referenced red phase holds together: 26 player turns cycle back to
 * the menu (including the chained multi-part turns 14.x/16.x/20.x/24.x), the
 * silent turns 24-26 say nothing, and the final 26.x chain runs through the
 * "survive THIS" speech into the special attack and the sleep ending.
 */
class SansRedPhaseTest {

    private static final GlobalState G = GlobalState.get();

    @BeforeEach
    void freshState() {
        G.newGame();
    }

    @Test
    void fightEveryTurnRunsRedPhaseAndReachesSleepEnding() {
        InputHandler input = new InputHandler();
        BattleScene scene = new BattleScene(null, input, BossRegistry.SANS);
        SansBoss boss = (SansBoss) scene.boss();

        // Survive the whole gauntlet standing still (KARMA caps below max HP).
        G.maxhp = 1000000;
        G.hp = 1000000;

        // Intro speech + opening barrage end at the player menu.
        int guard = 0;
        while (G.mnfight != TurnManager.MENU && guard++ < 20000) {
            scene.update();
        }
        assertTrue(guard < 20000, "the intro should reach the player menu");

        boolean sawWelp = false;
        boolean spawnedOnChainTurns = true;

        // 26 FIGHT turns ([silent first action] + [Attack #1..#25]).
        for (int t = 1; t <= 26; t++) {
            boss.onDefeat();                      // a landed FIGHT = Sans dodges
            G.mnfight = TurnManager.ENTER_ENEMY;
            boolean spawned = false;
            boolean spoke = false;
            int fg = 0;
            while (G.mnfight != TurnManager.MENU && fg++ < 60000) {
                scene.update();
                if (scene.bulletCount() > 0) {
                    spawned = true;
                }
                if (!boss.dialogue.isEmpty()) {
                    spoke = true;
                }
                if ("welp, it was worth a shot.".equals(boss.dialogue)) {
                    sawWelp = true;
                }
            }
            assertTrue(fg < 60000, "enemy turn " + t + " should end (hitTry=" + boss.turns + ")");
            assertEquals(t, boss.turns, "hitTry should advance once per turn");
            if (t >= 15 && t != 14) {
                spawnedOnChainTurns &= spawned;   // every red turn fires a pattern
            }
            if (t == 1 || (t >= 24 && t <= 26)) {
                assertTrue(!spoke, "turn " + t + " must be silent during the enemy turn");
            }
        }
        assertTrue(sawWelp, "hitTry 15 should speak 'welp...' before the 14.x chain");
        assertTrue(spawnedOnChainTurns, "every red turn should spawn its pattern");

        // The final turn: 26.x chain → "survive THIS" speech → special → sleep.
        boss.onDefeat();
        G.mnfight = TurnManager.ENTER_ENEMY;
        boolean sawSurvive = false;
        guard = 0;
        while (!boss.battleOver && guard++ < 200000) {
            scene.update();
            if ("survive THIS, and i'll show you my special attack!".equals(boss.dialogue)) {
                sawSurvive = true;
            }
        }
        assertTrue(sawSurvive, "the PRE_SPECIAL speech should play after the 26.x chain");
        assertTrue(boss.battleOver, "the sleep window expiring should end the fight");
        assertTrue(boss.banner.contains("woke up"),
                "without the fake-FIGHT press the ending is 'Sans woke up' (was: " + boss.banner + ")");
    }

    @Test
    void playerDeathRunsTheShatterAnimationWithoutEndingViaBanner() {
        InputHandler input = new InputHandler();
        BattleScene scene = new BattleScene(null, input, BossRegistry.SANS);
        SansBoss boss = (SansBoss) scene.boss();

        // Reach the player menu, then kill the soul outright.
        int guard = 0;
        while (G.mnfight != TurnManager.MENU && guard++ < 20000) {
            scene.update();
        }
        G.hp = 0;

        // The death sequence drives itself for many frames (crack → shatter) and,
        // with no host Game to switch scenes, simply keeps running — the point is
        // it must not throw (update OR render), and death is NOT routed through the
        // battleOver banner.
        java.awt.image.BufferedImage canvas =
                new java.awt.image.BufferedImage(640, 480, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = canvas.createGraphics();
        for (int i = 0; i < 200; i++) {
            scene.update();
            scene.render(g);   // exercise the crack/shatter draw path headlessly
        }
        g.dispose();
        assertTrue(!boss.battleOver, "player death plays its own animation, not the win/lose banner");
    }

    @Test
    void sparingAtTheTurningPointIsATrapThatKillsThePlayer() {
        InputHandler input = new InputHandler();
        BattleScene scene = new BattleScene(null, input, BossRegistry.SANS);
        SansBoss boss = (SansBoss) scene.boss();

        G.maxhp = 1000000;
        G.hp = 1000000;

        // Reach the player menu, then try to SPARE — Sans's "mercy" is bait.
        int guard = 0;
        while (G.mnfight != TurnManager.MENU && guard++ < 20000) {
            scene.update();
        }
        assertTrue(G.mnfight == TurnManager.MENU, "the intro should reach the player menu");

        boss.onSpare();   // the off-guard player gets blasted point-blank

        java.awt.image.BufferedImage canvas =
                new java.awt.image.BufferedImage(640, 480, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = canvas.createGraphics();
        guard = 0;
        while (G.hp > 0 && guard++ < 600) {
            scene.update();
            scene.render(g);   // exercise the surprise-blast + shatter draw path
        }
        g.dispose();
        assertEquals(0, G.hp, "sparing the turning-point trap drops the player to 0 HP");
        assertTrue(!boss.peacefulEnd, "the spare trap is a death, never a peaceful win");
        assertTrue(!boss.battleOver, "death plays the shatter animation, not the win banner");
    }

    @Test
    void mercyDeathEndingShowsWoundedSansAndWins() {
        InputHandler input = new InputHandler();
        BattleScene scene = new BattleScene(null, input, BossRegistry.SANS);
        SansBoss boss = (SansBoss) scene.boss();

        // Reach the player menu, then land the final hit (the fake FIGHT button).
        int guard = 0;
        while (G.mnfight != TurnManager.MENU && guard++ < 20000) {
            scene.update();
        }
        boss.mercyDeath();   // switches Sans to the wounded art (injured)

        // Drive the ending out + render the wounded Sans headlessly (must not throw).
        java.awt.image.BufferedImage canvas =
                new java.awt.image.BufferedImage(640, 480, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = canvas.createGraphics();
        guard = 0;
        while (!boss.battleOver && guard++ < 2000) {
            scene.update();
            scene.render(g);
        }
        scene.render(g);
        g.dispose();
        assertTrue(boss.battleOver, "the fake-FIGHT hit should end the fight");
        assertTrue(boss.peacefulEnd, "the mercy_death ending is a (peaceful) win");
    }
}
