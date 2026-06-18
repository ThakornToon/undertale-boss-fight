package boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import battle.Soul;
import battle.SoulMode;
import core.EntityManager;
import core.GlobalState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Locks in the authentic GML blue-soul jump (obj_heart, movement == 2): the
 * four-band gravity arc, its peak height, variable jump height, and the
 * grounded/airborne {@code jumpstage} contract. These are the numbers the
 * Papyrus and Sans bone layouts are authored against, so a regression here
 * silently makes patterns un-dodgeable.
 */
class SoulJumpTest {

    private static final GlobalState G = GlobalState.get();

    /** A 250-px-wide, 135-px-tall box, like Papyrus's combat box. */
    private static final double LEFT = 192, RIGHT = 442, TOP = 250, FLOOR = 385;

    @BeforeEach
    void freshState() {
        G.newGame();
        G.idealborder[0] = LEFT;
        G.idealborder[1] = RIGHT;
        G.idealborder[2] = TOP;
        G.idealborder[3] = FLOOR;
    }

    /** A blue soul resting on the floor of the box, ready to jump. */
    private Soul grounded(EntityManager em) {
        Soul soul = new Soul(em);
        soul.setMode(SoulMode.BLUE);
        soul.x = (LEFT + RIGHT) / 2;
        soul.y = FLOOR - Soul.HALF;
        soul.jumpStage = Soul.GROUNDED;
        return soul;
    }

    /** Run one full jump (key held until back on the floor) and report the apex. */
    private double simulateJump(Soul soul, int holdFrames) {
        double startY = soul.y;
        double peak = startY;
        for (int f = 0; f < 200; f++) {
            soul.upHeld = f < holdFrames;
            soul.update();
            peak = Math.min(peak, soul.y);
            if (f > 0 && soul.grounded()) {
                break; // landed
            }
        }
        return startY - peak; // height climbed, in px
    }

    @Test
    void fullHeldJumpClearsTheTallBonesButStaysInTheBox() {
        Soul soul = grounded(new EntityManager());
        double peak = simulateJump(soul, 999);
        // GML's launch (-6) + four bands peak at ~64 px: clears the 60-px bones,
        // well under the ~127-px ceiling (FLOOR-TOP minus the heart).
        assertTrue(peak > 60 && peak < 100,
                "a full-held blue jump should peak ~64 px, was " + peak);
        assertTrue(soul.grounded(), "the soul must come back to rest on the floor");
    }

    @Test
    void tappedJumpIsShorterThanAHeldJump() {
        double tap = simulateJump(grounded(new EntityManager()), 3);
        double held = simulateJump(grounded(new EntityManager()), 999);
        assertTrue(tap < held - 20,
                "variable height: a 3-frame tap (" + tap + ") must be well below a held jump (" + held + ")");
        assertTrue(tap > 0, "even a tap should leave the floor");
    }

    @Test
    void cannotJumpAgainWhileAirborne() {
        Soul soul = grounded(new EntityManager());
        soul.upHeld = true;
        soul.update();                 // launch
        assertEquals(Soul.AIRBORNE, soul.jumpStage, "holding UP from the floor launches the soul");
        double vAfterLaunch = soul.vspeed;
        soul.upHeld = false;           // release, then mash again mid-air
        soul.update();
        soul.upHeld = true;
        soul.update();
        // Still rising or falling under gravity — the second press must not re-launch.
        assertTrue(soul.vspeed > vAfterLaunch,
                "a mid-air UP press must not reset the jump (no double-jump)");
    }
}
