package core;

import java.awt.Graphics2D;

/**
 * A top-level game screen driven by {@link Game}'s fixed-timestep loop. The two
 * concrete scenes are the boss-select menu and the battle itself. GML had no
 * "scene" concept (it used rooms); this is the minimal host abstraction the
 * architecture's {@code Game.setScene(Scene)} needs.
 *
 * // GML: a room (room_goto)
 */
public interface Scene {

    /** Called once when the scene becomes active. */
    default void enter() {
    }

    /** Called once when the scene is replaced — release resources (e.g. music). */
    default void exit() {
    }

    /** GML: one logic Step at the fixed 30 FPS tick. */
    void update();

    /** GML: the Draw pass for this scene. */
    void render(Graphics2D g);
}
