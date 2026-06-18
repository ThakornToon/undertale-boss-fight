package battle;

/**
 * The player soul's movement mode (GML: {@code obj_heart.movement} plus the
 * yellow {@code shot} flag). Each constant carries the GML {@code movement}
 * integer so ported boss code that reads/writes {@code obj_heart.movement} maps
 * one-to-one, plus the sprite resource name.
 *
 * // GML: obj_heart movement modes (spr_heart / spr_heartblue / spr_heartgreen / spr_heartyellow)
 */
public enum SoulMode {

    /** Red free-move (Asgore, Undyne red, Asriel, Sans red). */
    RED(1, "spr_heart"),
    /** Blue gravity pulling DOWN, jump UP — the normal blue soul (Papyrus, Sans blue). */
    BLUE(2, "spr_heartblue"),
    /** Blue gravity pulling RIGHT, jump LEFT — Sans special slams (GML movement 11). */
    BLUE_RIGHT(11, "spr_heartblue"),
    /** Blue gravity pulling UP, jump DOWN (GML movement 12). */
    BLUE_UP(12, "spr_heartblue"),
    /** Blue gravity pulling LEFT, jump RIGHT (GML movement 13). */
    BLUE_LEFT(13, "spr_heartblue"),
    /** Green directional-block, locked position (both Undynes). */
    GREEN(3, "spr_heartgreen"),
    /** Yellow shoot, bottom-locked, fires upward (Mettaton EX). */
    YELLOW(1, "spr_heartyellow_flip"),
    /**
     * Purple web-lock (Muffet). The soul is bound to a set of horizontal web
     * lines: free left/right within {@code xmid±xlen}, ↑/↓ jump between adjacent
     * lines, and it can never float free. GML: {@code obj_purpleheart}
     * ({@code obj_heart.movement = -1} while the purple heart drives motion).
     */
    PURPLE(4, "spr_heartpurple");

    /** GML: obj_heart.movement value this mode sets. */
    public final int movement;
    public final String sprite;

    SoulMode(int movement, String sprite) {
        this.movement = movement;
        this.sprite = sprite;
    }

    public boolean isBlue() {
        return this == BLUE || this == BLUE_UP
                || this == BLUE_LEFT || this == BLUE_RIGHT;
    }
}
