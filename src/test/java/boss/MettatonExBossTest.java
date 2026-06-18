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
 * Drives Mettaton EX headlessly: the ratings meter, the 20-turn taunt/tempo machine,
 * the bomb attacks, and both endings — HP death (FIGHT/gun him to zero) and the
 * RATINGS call-in victory.
 */
class MettatonExBossTest {

    private static final GlobalState G = GlobalState.get();

    @BeforeEach
    void freshState() {
        G.newGame();
        G.maxhp = 1_000_000;
        G.hp = 1_000_000;
    }

    private static BattleScene scene() {
        return new BattleScene(null, new InputHandler(), BossRegistry.METTATON_EX);
    }

    @Test
    void statsSoulHeaderAndRatings() {
        BattleScene s = scene();
        assertEquals(1600, G.monstermaxhp[G.myself], "EX HP from scr_monstersetup");
        assertEquals(4000, G.ratings, "ratings start at 4000");
        assertTrue(s.boss().hideHpHeader, "the RATINGS meter replaces the HP header");
        assertEquals(TurnManager.MENU, G.mnfight, "the show opens straight on the menu");
    }

    @Test
    void turnsSetTheTauntsAndRainBombs() {
        BattleScene s = scene();

        // Turn 1: "ABSOLUTELY beautiful!" (the user's [PA0] line), attacktype 30.
        s.boss().turns = 0;
        G.mnfight = TurnManager.ENTER_ENEMY;
        s.update();
        assertEquals(1, s.boss().turns);
        assertTrue(s.boss().dialogue.contains("ABSOLUTELY beautiful!"),
                "turn 1 taunt should match the trimmed [PA0] sequence");
        assertEquals(30, G.attacktype, "attacktype = 29 + turns");

        // Turn 2: "Lights! Camera! Action!" ([PA1]).
        s.boss().turns = 1;
        G.mnfight = TurnManager.ENTER_ENEMY;
        s.update();
        assertTrue(s.boss().dialogue.contains("Lights! Camera! Action!"),
                "turn 2 taunt should be the next line");

        // Let the bomb generator put bullets on screen.
        boolean spawned = false;
        int guard = 0;
        while (G.mnfight != TurnManager.MENU && guard < 400) {
            if (s.bulletCount() > 0) {
                spawned = true;
            }
            s.update();
            guard++;
        }
        assertTrue(spawned, "the bomb generator should rain bullets during the turn");

        // Turn 14 blows the arms off ("...who needs arms with legs like these?").
        s.boss().turns = 13;
        G.mnfight = TurnManager.ENTER_ENEMY;
        s.update();
        assertTrue(s.boss().dialogue.contains("arms"), "turn 14 is the arms-off taunt");
    }

    @Test
    void poseRaisesRatings() {
        BattleScene s = scene();
        int before = G.ratings;
        s.boss().onAct(1); // Pose (Dramatic)
        assertTrue(G.ratings > before, "posing dramatically should raise ratings");
    }

    @Test
    void hpDeathEndingPlaysTheDeathMonologue() {
        BattleScene s = scene();
        s.boss().onDefeat(); // FIGHT/gun him to zero
        assertFalse(s.boss().battleOver, "the death cutscene plays before the fight ends");

        boolean sawWrong = false;
        boolean sawAudience = false;
        int guard = 0;
        while (!s.boss().battleOver && guard < 4000) {
            s.update();
            String line = s.boss().dialogue;
            if (line != null && line.contains("So I was wrong")) {
                sawWrong = true;
            }
            if (line != null && line.contains("great audience")) {
                sawAudience = true;
            }
            guard++;
        }
        assertTrue(sawWrong, "the HP-death monologue: 'So I was wrong.'");
        assertTrue(sawAudience, "...ending on 'You've been a great audience!'");
        assertTrue(s.boss().battleOver, "the fight ends after the explosion");
        assertFalse(s.boss().peacefulEnd, "an HP kill is not a peaceful end");
    }

    @Test
    void ratingsVictoryPlaysTheCallInFinale() {
        BattleScene s = scene();
        // Once ratings clear the milestone on a late turn, the call-in finale starts.
        G.ratings = 12000;
        s.boss().turns = 19;
        G.mnfight = TurnManager.MENU;
        G.myfight = 0;

        boolean sawRatingsLine = false;
        int guard = 0;
        while (!s.boss().battleOver && guard < 4000) {
            s.update();
            String line = s.boss().dialogue;
            if (line != null && line.contains("LOOK AT THESE RATINGS")) {
                sawRatingsLine = true;
            }
            guard++;
        }
        assertTrue(sawRatingsLine, "the ratings victory opens with 'OOH, LOOK AT THESE RATINGS!'");
        assertTrue(s.boss().battleOver, "the finale ends the fight");
        assertTrue(s.boss().peacefulEnd, "a ratings win is peaceful — he leaves, not dies");
    }

    @Test
    void everyTurnRendersWithoutError() {
        BattleScene s = scene();
        java.awt.image.BufferedImage frame = new java.awt.image.BufferedImage(
                640, 480, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = frame.createGraphics();

        for (int turn = 1; turn <= 20; turn++) {
            s.boss().turns = turn - 1;
            G.mnfight = TurnManager.ENTER_ENEMY;
            s.update();
            int guard = 0;
            while (G.mnfight != TurnManager.MENU && G.mnfight >= 0 && guard < 600) {
                s.update();
                s.render(g);
                guard++;
            }
        }
        g.dispose();
    }
}
