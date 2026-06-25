package boss;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import battle.BattleScene;
import battle.DamageSystem;
import battle.Soul;
import battle.SoulMode;
import bullet.Bullet;
import bullet.bones.SizeBone;
import core.EntityManager;
import core.GlobalState;
import core.InputHandler;
import core.TurnManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the Papyrus fight loop headlessly (no window) to prove the turn machine
 * cycles menu → enemy turn → bones → countdown → menu and eventually reaches
 * Papyrus's MERCY offer, and that a bone can damage the soul.
 */
class PapyrusBossTest {

    private static final GlobalState G = GlobalState.get();

    @BeforeEach
    void freshState() {
        G.newGame();
    }

    @Test
    void fightLoopCyclesAndReachesSpare() {
        InputHandler input = new InputHandler();
        BattleScene scene = new BattleScene(null, input, BossRegistry.PAPYRUS);

        // The test "player" stands still (no input is injected), so give it a large
        // HP buffer to survive the bombardment a real player would jump over — the
        // point here is to exercise the full turn progression to Papyrus's MERCY.
        G.maxhp = 100000;
        G.hp = 100000;

        // First frame: setup (mnfight 99) hands control to the player menu.
        scene.update();
        assertTrue(G.mnfight == TurnManager.MENU, "intro should open the menu");

        boolean spawnedBonesAtLeastOnce = false;
        int turnGuard = 0;

        while (G.mercymod < 100 && turnGuard < 60) {
            turnGuard++;

            // Simulate the player ending their turn (as choosing FIGHT/ACT would).
            G.mnfight = TurnManager.ENTER_ENEMY;
            scene.update();          // chooseAttack spawns the wave, enters enemy turn

            if (scene.bulletCount() > 0) {
                spawnedBonesAtLeastOnce = true;
            }

            // Run the enemy turn to completion (turntimer counts down to menu).
            int frameGuard = 0;
            while (G.mnfight != TurnManager.MENU && frameGuard < 5000) {
                scene.update();
                frameGuard++;
            }
            assertTrue(frameGuard < 5000, "enemy turn should end and return to menu");
        }

        assertTrue(spawnedBonesAtLeastOnce, "bone waves should spawn during enemy turns");
        assertTrue(G.mercymod >= 100,
                "Papyrus should reach his MERCY offer after the gauntlet (mercymod was "
                        + G.mercymod + ")");

        // Choose MERCY → Spare and confirm the fight ends.
        G.myfight = 4;
        G.mnfight = TurnManager.ACT_SPARE;
        scene.update();

        assertTrue(scene.boss().battleOver, "sparing Papyrus should end the battle");
    }

    @Test
    void keyboardDrivesMenuFightFlow() {
        InputHandler input = new InputHandler();
        BattleScene scene = new BattleScene(null, input, BossRegistry.PAPYRUS);
        G.maxhp = 100;
        G.hp = 100;

        scene.update(); // setup → player menu (FIGHT highlighted)
        assertTrue(G.mnfight == TurnManager.MENU, "intro should open the menu");

        int hpBefore = G.monsterhp[G.myself];

        // Tap CONFIRM three times via the KeyEventDispatcher path (the real input
        // route): FIGHT → start the attack bar → stop it → dismiss the result.
        tapConfirm(input, scene); // MAIN: pick FIGHT → attack bar
        tapConfirm(input, scene); // FIGHTBAR: stop the bar → deal damage → result
        assertTrue(G.monsterhp[G.myself] < hpBefore,
                "stopping the attack bar should damage Papyrus");
        assertTrue(G.mnfight == TurnManager.MENU,
                "result text should still be showing (turn not yet handed over)");

        tapConfirm(input, scene); // RESULT: confirm → hand the turn to the enemy
        assertTrue(G.mnfight == TurnManager.ENTER_ENEMY || G.mnfight == TurnManager.ENEMY_TURN,
                "dismissing the result should start the enemy turn");
    }

    @Test
    void genocideRouteOneShotsAndGivesGenocideSpeech() {
        InputHandler input = new InputHandler();
        // SIN 7+ is the genocide route — seed it before the boss runs setup().
        G.murderlv = 7;
        BattleScene scene = new BattleScene(null, input, BossRegistry.PAPYRUS);
        G.maxhp = 92;
        G.hp = 92;
        G.at = 14;

        scene.update(); // setup → player menu

        // Papyrus's DEF should have collapsed so any hit is lethal (GML: -20000).
        assertTrue(G.monsterdef[G.myself] < -1000,
                "genocide Papyrus should have a one-shot DEF");

        tapConfirm(input, scene); // MAIN: FIGHT → attack bar
        tapConfirm(input, scene); // FIGHTBAR: stop → deal the lethal hit

        // The lethal hit starts the decapitation cutscene; the fight is NOT over yet.
        assertFalse(scene.boss().battleOver, "death cutscene should play before ending");

        // Run the cutscene: the head falls, then the dying words type out one line
        // at a time in the speech bubble until the battle finally ends.
        boolean sawBelieve = false;
        int guard = 0;
        while (!scene.boss().battleOver && guard < 3000) {
            scene.update();
            String line = scene.boss().dialogue;
            if (line != null && line.contains("I BELIEVE IN YOU")) {
                sawBelieve = true;
            }
            guard++;
        }

        assertTrue(sawBelieve,
                "genocide death speech should type out the 'I BELIEVE IN YOU' line");
        assertTrue(scene.boss().battleOver, "the cutscene should end the battle");
        assertTrue(scene.boss().banner.contains("GENOCIDE"), "banner should mark the route");
    }

    /** Press+release CONFIRM (Z) through the dispatcher, one logic frame. */
    private static void tapConfirm(InputHandler input, BattleScene scene) {
        java.awt.Component src = new java.awt.Canvas();
        input.dispatchKeyEvent(new java.awt.event.KeyEvent(
                src, java.awt.event.KeyEvent.KEY_PRESSED, 0L, 0,
                java.awt.event.KeyEvent.VK_Z, 'z'));
        input.poll();          // Game.tick() does this each frame
        scene.update();        // the menu reads the edge-detected press
        input.dispatchKeyEvent(new java.awt.event.KeyEvent(
                src, java.awt.event.KeyEvent.KEY_RELEASED, 0L, 0,
                java.awt.event.KeyEvent.VK_Z, 'z'));
        input.poll();          // clear the press so the next tap is a fresh edge
    }

    @Test
    void boneDamagesMovingSoul() {
        EntityManager em = new EntityManager();
        DamageSystem damage = new DamageSystem();
        G.inbattle = true;
        G.turntimer = 100;
        G.idealborder[0] = 192;
        G.idealborder[1] = 442;
        G.idealborder[2] = 250;
        G.idealborder[3] = 385;

        Soul soul = new Soul(em);
        soul.setMode(SoulMode.BLUE);
        soul.x = 300;
        soul.y = 360;

        SizeBone bone = new SizeBone(em, soul);
        bone.x = soul.x - 9; // hitbox centred on the soul
        bone.y = soul.y - 5; // bone top just above the soul → vertical overlap

        int before = G.hp;
        bone.update();           // collision → soul.hurt
        damage.resolve();        // apply the pending hit through DEF

        assertTrue(G.hp < before, "a (non-blue) bone overlapping the soul should hurt it");
    }

    @Test
    void blueBoneIsSafeWhenStill() {
        EntityManager em = new EntityManager();
        DamageSystem damage = new DamageSystem();
        G.turntimer = 100;
        G.idealborder[0] = 192;
        G.idealborder[1] = 442;
        G.idealborder[2] = 250;
        G.idealborder[3] = 385;

        Soul soul = new Soul(em);
        soul.setMode(SoulMode.BLUE);
        soul.x = 300;
        soul.y = 360;
        soul.vspeed = 0;
        soul.jumpStage = Soul.GROUNDED;  // not moving
        // no input held → scr_blueat() == 0

        SizeBone bone = new SizeBone(em, soul);
        bone.blue = true;
        bone.x = soul.x - 9;
        bone.y = soul.y - 5;

        int before = G.hp;
        bone.update();
        damage.resolve();

        assertFalse(G.hp < before, "a blue bone must not hurt a stationary blue soul");
    }

    /**
     * The finale's super-bone climb (GML {@code blt_coolbus}) must unlock the instant a
     * coolbus bone crosses into the box ({@code x < idealborder[1]}) — not when the super
     * bone merely nears it. While that holds and the soul hugs the super bone's left face
     * holding UP, the ceiling tracks the soul up ({@code idealborder[2] = heart.y - 20})
     * and the soul gets a steady {@code vspeed = -2} push.
     */
    @Test
    void climbUnlocksWhenCoolbusEntersBox() throws Exception {
        InputHandler input = new InputHandler();
        BattleScene scene = new BattleScene(null, input, BossRegistry.PAPYRUS);
        G.maxhp = 100000;
        G.hp = 100000;

        scene.update(); // setup → menu

        Boss boss = scene.boss();
        Soul soul = (Soul) read(scene, "soul");
        Object board = read(scene, "board");

        // Jump straight to the "regular attack" finale: spawn the coolbus bridge + super bone.
        write(boss, "fighto", 16);
        G.mnfight = TurnManager.ENTER_ENEMY;
        scene.update();

        Bullet superBone = (Bullet) read(boss, "superBone");
        @SuppressWarnings("unchecked")
        java.util.List<Bullet> coolbus = (java.util.List<Bullet>) read(boss, "coolbusBones");
        java.lang.reflect.Method climbStep = boss.getClass().getDeclaredMethod("climbStep");
        climbStep.setAccessible(true);

        G.mnfight = TurnManager.ENEMY_TURN;
        soul.setMode(SoulMode.BLUE);
        soul.upHeld = true;
        soul.x = superBone.x - 5;          // hugging the super bone's left face
        soul.y = 200;
        soul.yprevious = 205;              // rising
        soul.vspeed = 0;

        // Coolbus still outside the box → the ceiling stays at its resting height.
        climbStep.invoke(boss);
        assertTrue((double) read(board, "climbTop") == -1.0,
                "climb must not unlock before a coolbus bone enters the box");

        // The frame a coolbus bone crosses the right border → the climb is live.
        coolbus.get(0).x = G.idealborder[1] - 1;
        soul.upHeld = true;
        soul.y = 200;
        soul.yprevious = 205;
        soul.vspeed = 0;
        climbStep.invoke(boss);
        assertTrue((double) read(board, "climbTop") == soul.y - 20,
                "ceiling should track the soul (idealborder[2] = heart.y - 20) once coolbus is in");
        assertTrue(soul.vspeed == -2.0, "the climb should give a steady upward push");
    }

    private static Object read(Object o, String field) throws Exception {
        java.lang.reflect.Field f = o.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.get(o);
    }

    private static void write(Object o, String field, Object value) throws Exception {
        java.lang.reflect.Field f = o.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(o, value);
    }
}
