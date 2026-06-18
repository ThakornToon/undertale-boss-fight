package battle;

import core.GlobalState;

/**
 * Mercy / spare resolution ({@code scr_mercystandard}). A monster becomes
 * spareable once its {@link GlobalState#mercy} meter reaches 100; {@code mercymod}
 * (set per boss — Papyrus 8000, Sans 999999, NEO −999999, Asgore broken) shifts
 * how fast mercy accrues and whether sparing is possible at all.
 *
 * <p>The broken-mercy state ({@code mercy == 2}, Asgore) hides the SPARE button:
 * mercy can never complete, so the player is forced to FIGHT.
 *
 * // GML: scr_mercystandard.gml + global.mercy / mercymod
 */
public final class MercySystem {

    private static final GlobalState G = GlobalState.get();

    public static final int FULL = 100;
    public static final int BROKEN = 2;

    /** Reset the mercy meter for a new fight. */
    public void reset() {
        G.mercy = 0;
        G.mercymod = 0;
    }

    /** GML: set this boss's mercy threshold modifier. */
    public void setMercyMod(int mod) {
        G.mercymod = mod;
    }

    /** GML: add to the mercy meter (e.g. after a successful ACT). */
    public void addMercy(int amount) {
        if (G.mercy == BROKEN) {
            return; // broken mercy never moves
        }
        G.mercy = Math.min(FULL, G.mercy + amount);
    }

    /** Asgore: shatter the SPARE option. */
    public void setBrokenMercy() {
        G.mercy = BROKEN;
    }

    public boolean isBrokenMercy() {
        return G.mercy == BROKEN;
    }

    /** Whether SPARE should be offered/visible at all. */
    public boolean isSpareAvailable() {
        return G.mercy != BROKEN;
    }

    /** GML: can the player spare right now? */
    public boolean canSpare() {
        if (G.mercy == BROKEN) {
            return false;
        }
        // A very large mercymod (Sans 999999) makes sparing always possible;
        // a very negative one (NEO) makes it impossible.
        if (G.mercymod >= FULL) {
            return true;
        }
        if (G.mercymod < 0) {
            return false;
        }
        return G.mercy >= FULL;
    }

    /** GML: the spare succeeded — flag the outcome for the boss to react. */
    public boolean spare() {
        if (!canSpare()) {
            return false;
        }
        G.myfight = 4;
        return true;
    }
}
