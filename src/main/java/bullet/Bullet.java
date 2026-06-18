package bullet;

import battle.Soul;
import battle.SoulMode;
import core.EntityManager;

/**
 * A collidable projectile. Adds an axis-aligned hitbox and the soul-collision
 * test that fires {@link #onHitSoul()} → {@link Soul#hurt(int)} unless the soul is
 * invulnerable. Variant flags that several bosses share live here so the Core stays
 * generic: {@code blue} (a blue bone only damages a <em>moving</em> blue soul —
 * Papyrus/Sans) and the {@code osc} vertical-oscillation trio.
 *
 * // GML: blt_parent_noborder + obj_heart collision event
 */
public abstract class Bullet extends AttackPattern {

    /** The soul this bullet collides against (GML: obj_heart is a singleton). */
    protected final Soul soul;

    /** GML: blue == 1 — only hurts when the blue soul is moving (scr_blueat). */
    public boolean blue;
    /** GML: osc / oscmin / oscmax — vertical bobbing once on-screen. */
    public double osc;
    public double oscmin = 20;
    public double oscmax = 20;
    protected double ystart;
    protected boolean drawn;

    protected Bullet(EntityManager manager, Soul soul) {
        super(manager);
        this.soul = soul;
        this.dmg = 6;
    }

    /** Hitbox half-width. Subclasses set the vertical extent via {@link #boxTop()}/{@link #boxBottom()}. */
    protected double halfWidth() {
        return 6;
    }

    /** Top edge (smaller y) of the bullet's collision rectangle. */
    protected abstract double boxTop();

    /** Bottom edge (larger y) of the bullet's collision rectangle. */
    protected abstract double boxBottom();

    /**
     * GML: scr_blueat — the blue soul "counts as moving" this frame. APPROX: GML
     * compared {@code obj_heart.xprevious/yprevious}; we use held input plus the
     * blue soul's vertical velocity (it is non-zero while jumping/falling).
     */
    protected boolean soulMoving() {
        return soul.leftHeld || soul.rightHeld || soul.upHeld || soul.downHeld
                || Math.abs(soul.vspeed) > 0.01 || Math.abs(soul.hspeed) > 0.01;
    }

    /** GML obj_heart collision event → event_user(1): take damage. */
    protected void onHitSoul() {
        if (blue && soul.mode.isBlue() && !soulMoving()) {
            return; // blue attack: standing still is safe
        }
        soul.hurt(dmg);
    }

    /** Axis-aligned overlap of the soul's hitbox with this bullet's rectangle. */
    protected boolean collidesSoul() {
        if (G.inv > 0) {
            return false; // global.invc < 1 guard
        }
        // GML required the soul horizontally close: abs(obj_heart.x - x) < 15.
        double bx = x + halfWidth(); // rectangle centred a touch right of x (GML x+3..x+9)
        if (Math.abs(soul.x - bx) >= halfWidth() + Soul.HALF) {
            return false;
        }
        double top = boxTop();
        double bottom = boxBottom();
        return soul.y + Soul.HALF > top && soul.y - Soul.HALF < bottom;
    }

    @Override
    public void update() {
        // GML: bones self-destroy once the enemy turn is over (turntimer < 0).
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        integrateMotion();
        oscillate();
        if (collidesSoul()) {
            onHitSoul();
        }
        cullOffscreen();
    }

    /** GML Step: bob vertically between ystart-oscmax and ystart-oscmin once drawn. */
    protected void oscillate() {
        markDrawnIfOnscreen();
        if (drawn && osc != 0) {
            if (y <= ystart - oscmax || y >= ystart - oscmin) {
                osc = -osc;
            }
            y += osc;
        }
    }

    /** GML: drawn = 1 once the bone is inside the box horizontally; latches ystart. */
    protected void markDrawnIfOnscreen() {
        if (!drawn && x > G.idealborder[0] - 5 && x < G.idealborder[1] - 4) {
            drawn = true;
            ystart = y;
        }
    }

    /** GML: destroy once the bullet has slid past the far border. */
    protected void cullOffscreen() {
        if (x < G.idealborder[0] - 10 && hspeed < 0) {
            manager.destroy(this);
        } else if (x > G.idealborder[1] + 10 && hspeed > 0) {
            manager.destroy(this);
        }
    }
}
