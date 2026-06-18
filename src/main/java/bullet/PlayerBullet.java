package bullet;

import core.EntityManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.function.IntConsumer;
import util.Assets;

/**
 * The yellow shoot soul's bullet (GML: {@code obj_heartshot} fired by
 * {@code obj_heart.shot}). Travels straight up from the soul; on hitting a
 * {@link Shootable} attack piece it fires its {@link Shootable#onShot()} (destroy /
 * detonate / toggle / block) and reports the ratings earned ({@link
 * Shootable#shotRating()}) through {@link #onScore}. Decoupled from the boss via the
 * score callback, so the Core needn't know the EX classes.
 *
 * // GML: obj_heartshot (player projectile vs the shootable attack objects)
 */
public final class PlayerBullet extends AttackPattern {

    private static final double SPEED = 11;

    /** Adds the ratings earned for a destroy / heart hit. */
    private final IntConsumer onScore;

    public PlayerBullet(EntityManager manager, double x, double y, IntConsumer onScore) {
        super(manager);
        this.x = x;
        this.y = y;
        this.onScore = onScore;
        this.depth = -50; // above enemy bullets (-1), below the soul (-100)
    }

    @Override
    public void update() {
        y -= SPEED;

        final boolean[] consumed = { false };
        manager.with(AttackPattern.class, e -> {
            if (consumed[0] || !(e instanceof Shootable s)) {
                return;
            }
            if (s.hitBy(x, y) && s.onShot()) {
                int r = s.shotRating(); // 0 block/toggle · 5 destroy · 15 heart
                if (r > 0 && onScore != null) {
                    onScore.accept(r);
                }
                consumed[0] = true;
            }
        });
        if (consumed[0]) {
            manager.destroy(this);
            return;
        }
        if (y < -12) {
            manager.destroy(this);
        }
    }

    @Override
    public void render(Graphics2D g) {
        BufferedImage img = Assets.sprite("spr_heartshot_0");
        if (img != null) {
            g.drawImage(img, (int) (x - img.getWidth()), (int) (y - img.getHeight()),
                    img.getWidth() * 2, img.getHeight() * 2, null);
            return;
        }
        // Fallback: a small bright-yellow pellet.
        g.setColor(Color.YELLOW);
        g.fillRect((int) (x - 2), (int) (y - 5), 4, 10);
    }
}
