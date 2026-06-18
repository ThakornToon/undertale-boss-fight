package core;

import java.util.function.BooleanSupplier;

/**
 * The {@code global.mnfight} state machine — the single place the turn-phase
 * constant is interpreted. Owns the shared turn boilerplate every boss repeats:
 * the {@code attacked==0} spawn-once guard reset, the {@code turntimer} countdown,
 * and the {@code turntimer<=0 → back to menu} turn-end.
 *
 * <p>Collaborators that live outside Core (the boss's attack chooser, the
 * ACT/SPARE resolver, the special/transform handler, the intro-complete gate) are
 * supplied as hooks so {@code TurnManager} never references boss or menu classes
 * directly. {@code BattleScene} wires them once. The battle menu updates itself
 * each frame and signals its choice purely through {@link GlobalState#myfight} /
 * {@link GlobalState#mnfight}, so the menu is not a hook here.
 *
 * // GML: the mnfight turn protocol (shared across every boss controller)
 */
public final class TurnManager {

    private static final GlobalState G = GlobalState.get();

    // Phase constants (GML global.mnfight values).
    public static final int SETUP = 99;
    public static final int MENU = 2;
    public static final int ENTER_ENEMY = 3;
    public static final int ENEMY_TURN = 1;
    public static final int ACT_SPARE = 4;
    public static final int SPECIAL = 5;

    // ---- Hooks supplied by BattleScene (default = harmless no-ops) ----------
    /** mnfight 99 → true once the boss intro Sequence has finished. */
    public BooleanSupplier introComplete = () -> true;
    /** mnfight 3 → boss picks this turn's attack and pushes it to the body. */
    public Runnable chooseAttack = () -> { };
    /** mnfight 4 → ActDispatcher / MercySystem resolution; sets next mnfight. */
    public Runnable resolveActOrSpare = () -> { };
    /** mnfight 5 → boss special/transform handler. */
    public Runnable onSpecial = () -> { };

    /** Call once per frame, after the boss/body/soul have updated. */
    public void tick() {
        switch (G.mnfight) {
            case SETUP -> {
                // 99: boss drives its intro Sequence; advance when scripted.
                if (introComplete.getAsBoolean()) {
                    startPlayerTurn();
                }
            }
            case MENU -> {
                // 2: BattleMenu is active and drives itself; on confirm it sets
                // global.myfight + global.mnfight. Nothing to do here.
            }
            case ENTER_ENEMY -> {
                // 3: transition into the enemy turn.
                G.attacked = 0;
                chooseAttack.run();   // boss sets body selector, turntimer, border
                G.mnfight = ENEMY_TURN;
            }
            case ENEMY_TURN -> {
                // 1: enemy turn running. The body spawns its pattern once while
                // attacked==0 (and sets attacked=1). We own the countdown.
                if (G.turntimer > 0) {
                    G.turntimer--;
                }
                if (G.turntimer <= 0) {
                    endEnemyTurn();
                }
            }
            case ACT_SPARE -> {
                // 4: ACT or SPARE chosen from the menu.
                resolveActOrSpare.run();
                if (G.mnfight == ACT_SPARE) {
                    // Hook left us here: default back to the menu.
                    startPlayerTurn();
                }
            }
            case SPECIAL -> {
                // 5: special / transform — fully boss-driven.
                onSpecial.run();
            }
            default -> {
                // Negatives (-1, -99): frozen during a death/transform cutscene.
                // Only the boss's Sequence runs; no turn logic here.
            }
        }
    }

    /** GML: hand control to the player command menu. */
    public void startPlayerTurn() {
        G.mnfight = MENU;
        G.myfight = 0;
        G.attacked = 0;
    }

    /** GML: enemy turn finished — return to the menu. */
    public void endEnemyTurn() {
        G.turntimer = 0;
        startPlayerTurn();
    }

    public boolean isEnemyTurn() {
        return G.mnfight == ENEMY_TURN;
    }

    public boolean isPlayerMenu() {
        return G.mnfight == MENU;
    }
}
