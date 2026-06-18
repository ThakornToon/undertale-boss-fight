package core;

import java.awt.KeyEventDispatcher;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashSet;
import java.util.Set;

/**
 * Maps the keyboard to Undertale's controls (GML: {@code kb_*} checks). Exposes
 * both <em>held</em> state (for soul movement) and edge-detected <em>pressed</em>
 * state (for menu navigation and confirm), refreshed by {@link #poll()} once per
 * logic frame.
 *
 * <p>Bindings: move = arrow keys or WASD; confirm = Z / Enter / Space; cancel =
 * X / Shift / Backspace; menu = C / Ctrl.
 *
 * <p>Implemented as a {@link KeyEventDispatcher} (registered on the global
 * {@code KeyboardFocusManager}) as well as a {@link KeyListener}: the dispatcher
 * receives every key event while the game window is focused, regardless of which
 * child component holds focus — far more reliable than a Canvas's own focus on
 * macOS, where heavyweight components routinely lose keyboard focus.
 *
 * // GML: keyboard_check / keyboard_check_pressed (Z/X/C + arrows)
 */
public final class InputHandler implements KeyListener, KeyEventDispatcher {

    public enum Key { LEFT, RIGHT, UP, DOWN, CONFIRM, CANCEL, MENU, PAUSE, RESTART, MUTE }

    // Live keyboard state, mutated on the AWT event thread.
    private final Set<Key> down = new HashSet<>();
    // Snapshot taken at poll() so a logic frame sees a consistent state.
    private final Set<Key> held = new HashSet<>();
    private final Set<Key> prevHeld = new HashSet<>();

    /** Snapshot the live key state for this logic frame. Call once per frame. */
    public synchronized void poll() {
        prevHeld.clear();
        prevHeld.addAll(held);
        held.clear();
        held.addAll(down);
    }

    /** GML: keyboard_check(key) — key currently held. */
    public boolean held(Key k) {
        return held.contains(k);
    }

    /** GML: keyboard_check_pressed(key) — key went down this frame. */
    public boolean pressed(Key k) {
        return held.contains(k) && !prevHeld.contains(k);
    }

    /** GML: keyboard_check_released(key). */
    public boolean released(Key k) {
        return !held.contains(k) && prevHeld.contains(k);
    }

    // ---- KeyListener (AWT thread) ------------------------------------------

    @Override
    public synchronized void keyPressed(KeyEvent e) {
        Key k = map(e.getKeyCode());
        if (k != null) {
            down.add(k);
        }
    }

    @Override
    public synchronized void keyReleased(KeyEvent e) {
        Key k = map(e.getKeyCode());
        if (k != null) {
            down.remove(k);
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // unused
    }

    // ---- KeyEventDispatcher (catches keys app-wide while the window is focused) --

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        switch (e.getID()) {
            case KeyEvent.KEY_PRESSED -> keyPressed(e);
            case KeyEvent.KEY_RELEASED -> keyReleased(e);
            default -> { }
        }
        return false; // never consume — let normal dispatch continue
    }

    private static Key map(int code) {
        return switch (code) {
            case KeyEvent.VK_LEFT, KeyEvent.VK_A -> Key.LEFT;
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> Key.RIGHT;
            case KeyEvent.VK_UP, KeyEvent.VK_W -> Key.UP;
            case KeyEvent.VK_DOWN, KeyEvent.VK_S -> Key.DOWN;
            case KeyEvent.VK_Z, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> Key.CONFIRM;
            case KeyEvent.VK_X, KeyEvent.VK_SHIFT, KeyEvent.VK_BACK_SPACE -> Key.CANCEL;
            case KeyEvent.VK_C, KeyEvent.VK_CONTROL -> Key.MENU;
            case KeyEvent.VK_ESCAPE -> Key.PAUSE;
            case KeyEvent.VK_R -> Key.RESTART;
            case KeyEvent.VK_M -> Key.MUTE;
            default -> null;
        };
    }
}
