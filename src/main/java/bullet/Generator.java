package bullet;

import core.EntityManager;

/**
 * A timed bullet emitter — the GML {@code *_gen} objects that live for several
 * frames, emit on a cadence, then destroy themselves. Concrete generators set the
 * difficulty knobs ({@code diff}/{@code lv}/{@code factor}/{@code type}/{@code h_mode})
 * and implement {@link #emit()}; this base owns the {@code firingrate} cadence and
 * the lifetime countdown.
 *
 * <p>Papyrus spawns its bones directly from the controller, so no Papyrus generator
 * subclasses exist yet — this is the shared base later bosses extend.
 *
 * // GML: obj_*gen (timed bullet generators)
 */
public abstract class Generator extends AttackPattern {

    /** GML: global.firingrate — frames between emissions. */
    public int rate = 15;
    /** Remaining emissions; negative = unlimited until {@link #life} ends. */
    public int count = -1;
    /** Frames to live before self-destructing; negative = until count hits 0. */
    public int life = -1;
    /** Difficulty knobs shared by the generator catalog. */
    public int diff;
    public int lv;
    public int factor;
    public int type;
    public int hMode;

    private int cooldown;

    protected Generator(EntityManager manager) {
        super(manager);
    }

    /** Emit one wave of bullets. */
    protected abstract void emit();

    @Override
    public void update() {
        if (cooldown <= 0 && (count != 0)) {
            emit();
            if (count > 0) {
                count--;
            }
            cooldown = rate;
        } else {
            cooldown--;
        }
        if (life > 0) {
            life--;
            if (life == 0) {
                manager.destroy(this);
            }
        } else if (count == 0) {
            manager.destroy(this);
        }
    }

    @Override
    public void render(java.awt.Graphics2D g) {
        // Generators are invisible spawn points.
    }
}
