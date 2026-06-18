package boss;

import core.GlobalState;

/**
 * A monster's stat block and the {@code scr_monstersetup} / {@code scr_monsterdefeat}
 * logic for a single battle slot. Stats are seeded into {@link GlobalState}'s
 * per-slot monster arrays so the damage system and bosses read them exactly as the
 * GML reads {@code global.monsterhp[myself]} etc.
 *
 * // GML: scr_monstersetup.gml + scr_monsterdefeat.gml (one monstertype entry)
 */
public final class Monster {

    private static final GlobalState G = GlobalState.get();

    public final int slot;
    public final String name;
    public final int maxhp;
    public final int atk;
    public final int def;
    public final int xpreward;

    public Monster(int slot, String name, int maxhp, int atk, int def, int xpreward) {
        this.slot = slot;
        this.name = name;
        this.maxhp = maxhp;
        this.atk = atk;
        this.def = def;
        this.xpreward = xpreward;
    }

    /** GML: scr_monstersetup — seed this monster's stats into the active slot. */
    public void setup() {
        G.myself = slot;
        G.monstermaxhp[slot] = maxhp;
        G.monsterhp[slot] = maxhp;
        G.monsteratk[slot] = atk;
        G.monsterdef[slot] = def;
    }

    /** GML: scr_monsterdefeat — award XP on a kill. */
    public void defeat() {
        G.xp += xpreward;
    }

    public int hp() {
        return G.monsterhp[slot];
    }

    public void setHp(int hp) {
        G.monsterhp[slot] = hp;
    }
}
