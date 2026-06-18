package bullet.muffet;

import battle.Soul;
import battle.WebBoard;
import bullet.Bullet;
import core.EntityManager;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * A spider that rappels straight down the web (GML: {@code obj_vertspider}), used in
 * the pet special. It spawns two strands above the top of the web in one of the
 * evenly spaced columns, then "falls" by riding the web's upward scroll ({@code yadd})
 * — so as the web climbs, the spider descends through it — while drifting sideways and
 * bouncing off the box walls. It despawns once it drops past the bottom of the screen.
 *
 * // GML: obj_vertspider
 */
public final class VertSpider extends Bullet {

    private final WebBoard web;
    /** Spawn the spider faster on the heavier pet-special turns (GML turn 10/16). */
    public boolean fast;

    private double fakey;
    private double fakeyoff;
    private int fakeyamt;
    private int armTimer = 2;  // GML alarm[0]: pick a sideways drift after 2 frames

    public VertSpider(EntityManager manager, Soul soul) {
        super(manager, soul);
        this.web = soul.web;
        this.depth = -1;
        if (web != null) {
            // GML Create: start two strands above the top, in a 22px-spaced column.
            double xfactor = (int) (Math.random() * (web.xlen / 10)) * 22;
            x = web.xmid - web.xlen + xfactor;
            y = web.yzero - web.yspace * 2 + web.yoff;
            fakey = y;
        }
    }

    @Override
    public void update() {
        if (G.turntimer <= 0 || web == null) {
            manager.destroy(this);
            return;
        }
        if (armTimer > 0 && --armTimer == 0) {
            double s = fast ? 1.5 : 1.0;
            hspeed = Math.random() < 0.5 ? -s : s;
        }
        x += hspeed;

        // GML Step: descend in lockstep with the web's rise, adding a strand of drop
        // each time the scroll wraps.
        fakeyoff += web.yadd;
        if (fakeyoff > web.yspace) {
            fakeyoff = 0;
            fakeyamt++;
        }
        y = fakey + fakeyoff + fakeyamt * web.yspace;
        if (y > 400) {
            manager.destroy(this);
            return;
        }

        // Bounce off the box walls (GML: obj_rborder / obj_lborder).
        if (hspeed > 0 && x > G.idealborder[1] - 6) {
            x -= hspeed;
            hspeed = -hspeed;
        } else if (hspeed < 0 && x < G.idealborder[0] + 6) {
            x -= hspeed;
            hspeed = -hspeed;
        }

        if (collidesSoul()) {
            onHitSoul();
        }
    }

    @Override
    protected double boxTop() {
        return y - 6;
    }

    @Override
    protected double boxBottom() {
        return y + 6;
    }

    @Override
    protected boolean collidesSoul() {
        if (G.inv > 0) {
            return false;
        }
        return Math.abs(soul.x - x) < 6 + Soul.HALF && Math.abs(soul.y - y) < 6 + Soul.HALF;
    }

    @Override
    public void render(Graphics2D g) {
        BufferedImage img = Assets.sprite("spr_spiderbullet1_0");
        if (img == null) {
            return;
        }
        g.drawImage(img, (int) (x - img.getWidth() / 2.0),
                (int) (y - img.getHeight() / 2.0), null);
    }
}
