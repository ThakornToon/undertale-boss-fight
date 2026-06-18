package battle;

import core.GlobalState;

/**
 * KARMA ("KR") poison ticker — Sans only, off by default. While
 * {@link GlobalState#km} &gt; 0, HP drains on a timer whose rate scales with the
 * current KARMA amount and with the player's i-frame buildup. It never kills
 * directly: HP is floored at 1.
 *
 * <p>Lives in {@code battle} and is composed by {@link DamageSystem}; its
 * {@link #tick()} runs in the End-Step phase, matching the GML body's
 * end-of-Step KARMA code.
 *
 * // GML: global.km poison (obj_sansb_body, end of Step Event)
 */
public final class KarmaTicker {

    private static final GlobalState G = GlobalState.get();

    /** Max KARMA the meter holds. */
    public static final int KM_CAP = 40;

    /** Whether the ticker is armed (only Sans turns it on). */
    public boolean enabled;

    /** Countdown between HP ticks (GML: km_t). */
    private int kmTimer;

    /** GML: cap km at 40 and at hp-1 so it can't kill. */
    public void addKarma(int amount) {
        if (!enabled) {
            return;
        }
        G.km = Math.min(Math.min(G.km + amount, KM_CAP), Math.max(0, G.hp - 1));
    }

    /** End-of-Step tick: drain HP while KARMA is active. */
    public void tick() {
        if (!enabled || G.km <= 0) {
            return;
        }
        // Re-clamp: KARMA can never reduce HP to 0.
        G.km = Math.min(G.km, Math.max(0, G.hp - 1));
        if (G.km <= 0) {
            return;
        }

        if (kmTimer > 0) {
            kmTimer--;
            return;
        }

        // Rate scales with how much KARMA is built up (more km = faster ticks),
        // matching the GML cascade (km_t thresholds 1/2/5/15/30 per km tier), and
        // slowed by a km_bonus while the player has long i-frames built up.
        int rate;
        if (G.km >= 40) {
            rate = 1;
        } else if (G.km >= 30) {
            rate = 2;
        } else if (G.km >= 20) {
            rate = 5;
        } else if (G.km >= 10) {
            rate = 15;
        } else {
            rate = 30;
        }
        // GML km_bonus: heavy i-frame buildup eases the poison a little.
        if (G.inv >= 75) {
            rate += 3;
        } else if (G.inv >= 45) {
            rate += 1;
        }

        if (G.hp > 1) {
            G.hp--;     // never kills directly
            G.km--;
        }
        kmTimer = rate;
    }

    public void reset() {
        enabled = false;
        kmTimer = 0;
        G.km = 0;
    }
}
