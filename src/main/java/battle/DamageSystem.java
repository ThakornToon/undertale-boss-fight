package battle;

import core.GlobalState;
import util.GMLHelper;

/**
 * The damage pipeline: {@code scr_damagestandard} (player takes damage),
 * {@code scr_attackcalc} (player ATK against the monster), and the per-boss
 * override hooks that make the same code serve every fight — forced one-hit kill
 * (NEO), a player-damage multiplier (Undyne ×21, min 600), and the Asgore
 * {@code fivedamage} "leave at low HP" branch. The KARMA poison ticker (Sans) is
 * composed here and runs in End-Step.
 *
 * <p>Each boss configures the override fields in its setup; the override branch
 * lives in one place so no boss needs to touch this class's structure.
 *
 * // GML: scr_damagestandard.gml + scr_attackcalc.gml + per-boss overrides
 */
public final class DamageSystem {

    private static final GlobalState G = GlobalState.get();

    /** Damage fraction a worst-timed (box-edge) FIGHT strike keeps; centre = 1.0. */
    private static final double SKILL_FLOOR = 0.40;

    /** KARMA ticker, off unless a boss enables it. */
    public final KarmaTicker karma = new KarmaTicker();

    // ---- Per-boss override hooks (set in boss setup) ------------------------
    /** NEO: the player's hit always kills, regardless of monster HP. */
    public boolean forcedKill;
    /** Undyne: multiply the player's computed damage (×21). */
    public int playerDamageMultiplier = 1;
    /** Undyne: floor for the multiplied damage (600). */
    public int playerMinDamage = 0;
    /** Asgore: if a hit would leave the monster at/below this HP, fire the
     *  {@code fivedamage} cutscene instead of continuing. -1 disables. */
    public int fivedamageThreshold = -1;

    /** Reset overrides + KARMA between fights. */
    public void reset() {
        forcedKill = false;
        playerDamageMultiplier = 1;
        playerMinDamage = 0;
        fivedamageThreshold = -1;
        karma.reset();
    }

    // ---- Player takes damage ------------------------------------------------

    /**
     * Per-frame resolution of pending soul collisions. {@link Soul#hurt(int)}
     * sets {@code global.takedamage}; here we apply it through DEF, start the
     * hurt animation, and count down the i-frame window.
     *
     * // GML: scr_damagestandard
     */
    public void resolve() {
        if (G.takedamage == 1) {
            applyPlayerDamage(G.damage);
            G.takedamage = 0;
            G.damage = 0;
        }
        if (G.inv > 0) {
            G.inv--;
        }
        if (G.damagetimer > 0) {
            G.damagetimer--;
        }
    }

    /** GML: scr_damagestandard — subtract DEF (LV-scaled), floor at 1, apply. */
    public void applyPlayerDamage(int rawDmg) {
        // UT: defence reduces incoming damage; a minimum of 1 still lands.
        int reduced = rawDmg - (G.df + (G.lv - 1)) / 5;
        int taken = Math.max(1, reduced);
        G.hp = Math.max(0, G.hp - taken);
        G.hurtanim = 1;
        G.damagetimer = 30;
        util.Audio.play(util.Audio.SFX_HURT);   // GML: snd_hurt when the SOUL is hit
    }

    /** Direct hurt that bypasses the soul collision flag (scripted damage). */
    public void playerHurt(int rawDmg) {
        if (G.inv > 0) {
            return;
        }
        applyPlayerDamage(rawDmg);
        G.inv = 30;
    }

    // ---- Player attacks the monster ----------------------------------------

    /**
     * Compute and apply the player's FIGHT damage to the monster in {@code slot}.
     * Returns the damage dealt. Honors all override hooks.
     *
     * // GML: scr_attackcalc + the FIGHT damage application
     *
     * @param slot   monster slot (global.myself)
     * @param timing 0..1 attack-bar accuracy (1.0 = perfect center hit)
     */
    public int monsterHurt(int slot, double timing) {
        if (forcedKill) {
            // NEO: hp + 4000 + random — guaranteed lethal, then zero it out.
            int lethal = G.monsterhp[slot] + 4000 + GMLHelper.irandom(20);
            G.monsterhp[slot] = 0;
            return lethal;
        }

        // scr_attackcalc: full-power base ATK, then DEF, then the boss overrides.
        double accuracy = GMLHelper.clamp(timing, 0.0, 1.0);
        int dmg = (G.at + GMLHelper.irandom(2)) - G.monsterdef[slot];
        if (dmg < 1) {
            dmg = 1;
        }

        if (playerDamageMultiplier > 1) {
            dmg *= playerDamageMultiplier;             // Undyne ×21
            dmg = Math.max(dmg, playerMinDamage);      // …min 600
        }

        // Skill check: the centre of the FIGHT bar is a full hit; the further off
        // centre it stopped, the less damage lands (down to SKILL_FLOOR at the edge).
        // Applied last so it scales even the multiplier/min-floor bosses (Asgore etc.),
        // which would otherwise deal a flat number regardless of timing.
        dmg = (int) Math.round(dmg * (SKILL_FLOOR + (1.0 - SKILL_FLOOR) * accuracy));
        if (dmg < 1) {
            dmg = 1;
        }

        int resulting = G.monsterhp[slot] - dmg;

        // Asgore: a hit that would drop the monster too low triggers the
        // "leave at low HP" cutscene instead of killing.
        if (fivedamageThreshold >= 0 && resulting <= fivedamageThreshold) {
            G.fivedamage = 1;
            G.monsterhp[slot] = Math.max(1, fivedamageThreshold);
            return dmg;
        }

        G.monsterhp[slot] = Math.max(0, resulting);
        return dmg;
    }

    public boolean isMonsterDefeated(int slot) {
        return G.monsterhp[slot] <= 0;
    }

    /** End-Step phase: run the KARMA ticker. */
    public void endStep() {
        karma.tick();
    }
}
