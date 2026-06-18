package boss;

import core.Entity;
import core.EntityManager;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;

/**
 * The full-screen white blink between Sans's chained sub-attacks (the screenshot
 * reference's {@code _f} cuts): a scene-change flash that pops bright and fades
 * over a handful of frames. Presentation only — it never touches game state.
 */
final class ScreenFlash extends Entity {

    private static final int LIFE = 10;
    private int life = LIFE;

    ScreenFlash(EntityManager manager) {
        super(manager);
        this.depth = -9999;   // smallest depth draws last → covers the playfield
    }

    @Override
    public void update() {
        if (--life <= 0) {
            manager.destroy(this);
        }
    }

    @Override
    public void render(Graphics2D g) {
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, life / (float) LIFE))));
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 640, 480);
        g.setComposite(old);
    }
}
