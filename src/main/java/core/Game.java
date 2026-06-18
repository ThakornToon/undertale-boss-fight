package core;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferStrategy;
import javax.swing.JFrame;

/**
 * The host window and the fixed-timestep game loop. GML runs at 30 steps/sec
 * with alarms/timers expressed in frames; this loop ticks logic at exactly
 * 30 FPS so every {@code turntimer} / {@code alarm[n]} value ports as-is. Render
 * is decoupled and simply draws the latest state.
 *
 * // GML: the room loop + global game speed (30)
 */
public final class Game extends Canvas {

    public static final int WIDTH = 640;
    public static final int HEIGHT = 480;
    /** GML game speed: 30 steps per second. */
    public static final double FPS = 30.0;

    private final InputHandler input = new InputHandler();
    private Scene current;
    private volatile boolean running;

    public Game() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);
        // Let the arrow keys and Tab reach our KeyListener instead of being
        // swallowed by AWT focus traversal — the soul/menu need every arrow key.
        setFocusTraversalKeysEnabled(false);
        addKeyListener(input);
        // Catch every key while the window is focused, even if the Canvas itself
        // loses keyboard focus (common on macOS) — this is what actually makes
        // WASD / arrows / Space reach the game.
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(input);
        // A heavyweight Canvas can silently lose keyboard focus; clicking the
        // window grabs it back so the player is never stuck with dead keys.
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
            }
        });
    }

    public InputHandler input() {
        return input;
    }

    /** GML: room_goto. */
    public void setScene(Scene scene) {
        if (current != null) {
            current.exit();   // let the outgoing scene release resources (music)
        }
        this.current = scene;
        if (scene != null) {
            scene.enter();
        }
    }

    /** Open the window and run the loop until the process exits. */
    public void start(String title) {
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.add(this);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        // Pull the window to the foreground (macOS often opens Java frames behind
        // other apps) and grab keyboard focus once the native peer is realized.
        frame.toFront();
        frame.requestFocus();
        requestFocusInWindow();
        javax.swing.SwingUtilities.invokeLater(this::requestFocusInWindow);

        createBufferStrategy(2);
        running = true;
        loop();
    }

    private void loop() {
        final double frameNanos = 1_000_000_000.0 / FPS;
        double accumulator = 0;
        long last = System.nanoTime();

        while (running) {
            long now = System.nanoTime();
            accumulator += now - last;
            last = now;

            // Catch up on logic in fixed 1/30 s steps (frame-locked game logic),
            // then draw the latest state once per loop iteration.
            while (accumulator >= frameNanos) {
                tick();
                accumulator -= frameNanos;
            }
            draw();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    private void tick() {
        input.poll();
        // Mute (M) is global — it works on every screen, not just in a fight.
        if (input.pressed(InputHandler.Key.MUTE)) {
            util.Audio.toggleMute();
        }
        if (current != null) {
            current.update();
        }
    }

    private void draw() {
        BufferStrategy bs = getBufferStrategy();
        if (bs == null) {
            return;
        }
        Graphics2D g = (Graphics2D) bs.getDrawGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        if (current != null) {
            current.render(g);
        }
        g.dispose();
        bs.show();
    }
}
