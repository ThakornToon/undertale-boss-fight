package core;

/**
 * The single home for every {@code global.*} variable used by the GML battle
 * engine. Per the architecture contract, NO other class caches a copy of a
 * global — every system reads and writes through {@link #get()}.
 *
 * <p>GML globals are untyped, mutable, and shared; this mirrors them as public
 * mutable fields on a singleton. Arrays are sized generously so index-based GML
 * access (e.g. {@code global.flag[351]}) ports as-is.
 *
 * // GML: global.* variables (scattered across every object/script)
 */
public final class GlobalState {

    private static final GlobalState INSTANCE = new GlobalState();

    /** GML has exactly one global scope. */
    public static GlobalState get() {
        return INSTANCE;
    }

    private GlobalState() {
        newGame();
    }

    // ---- Turn state machine -------------------------------------------------
    // GML: global.mnfight / global.myfight / global.turntimer / ...
    /** Battle phase. 99 setup · 2 menu · 3 transition→enemy · 1 enemy turn · 4 ACT/SPARE · 5 special · negatives = frozen. */
    public int mnfight;
    /** Selected menu action: 0 fight, 2 act/check, 4 mercy, -1/19/99 busy. */
    public int myfight;
    /** Counts down the enemy turn (frames). */
    public int turntimer;
    /** Bullet spawn cadence. */
    public int firingrate;
    /** Which battle menu is active. */
    public int bmenuno;
    /** Which button/coordinate is highlighted in the active menu. */
    public int bmenucoord;
    /** True while a fight is in progress. */
    public boolean inbattle;
    /** Current attack-type selector handed to the body. */
    public int attacktype;
    /** Spawn-once guard: 0 = attack not yet spawned this enemy turn. */
    public int attacked;

    // ---- Player stats -------------------------------------------------------
    // GML: global.hp / global.maxhp / global.at / global.df / global.lv / global.xp
    public int hp;
    public int maxhp;
    public int at;
    public int df;
    public int lv;
    public int xp;
    /** Invulnerability / i-frame window (global.inv). */
    public int inv;
    /** KARMA poison accumulator (global.km / "KR"), Sans only. */
    public int km;

    // ---- Combat box ---------------------------------------------------------
    // GML: global.idealborder[0..3] = left, right, top, bottom
    public final double[] idealborder = new double[4];
    /** Border preset index fed to SCR_BORDERSETUP (global.border). */
    public int border;
    /** Fade-to-black climax flag. */
    public boolean darkify;
    /** Screen-shake intensity. */
    public int shakify;

    // ---- Monster stats (indexed by slot `myself`) ---------------------------
    // GML: global.monsterhp[] / global.monstermaxhp[] / global.monsteratk[] / global.monsterdef[]
    public static final int MONSTER_SLOTS = 16;
    public final int[] monsterhp = new int[MONSTER_SLOTS];
    public final int[] monstermaxhp = new int[MONSTER_SLOTS];
    public final int[] monsteratk = new int[MONSTER_SLOTS];
    public final int[] monsterdef = new int[MONSTER_SLOTS];
    /** Active monster slot (global.myself). */
    public int myself;
    /** Mercy/spare progress for the active monster. 2 = broken mercy (Asgore). */
    public int mercy;
    /** Per-boss mercy threshold modifier mirror (global.mercymod). */
    public int mercymod;

    // ---- Presentation -------------------------------------------------------
    // GML: global.faceemotion / global.flag[20] / global.hurtanim / ...
    /** Which expression sprite the boss face shows. */
    public int faceemotion;
    /** Hurt-shudder animation state: 1→3→2. */
    public int hurtanim;
    /** Counts down the floating damage-number / shudder window. */
    public int damagetimer;
    /** Asgore "leave at low HP" hook flag. */
    public int fivedamage;
    /** Mettaton EX ratings score (win condition, not HP). */
    public int ratings;
    /** Asriel ACT Hope/Dream accumulator (global.hope). */
    public int hope;

    // ---- Damage scratch -----------------------------------------------------
    // GML: global.damage / global.takedamage
    /** Damage value being applied this frame. */
    public int damage;
    /** Pending-damage flag set by Soul collision, consumed by DamageSystem. */
    public int takedamage;

    // ---- Persistent + run state --------------------------------------------
    // GML: global.flag[] / global.tempvalue[] / undertale.ini
    public final int[] flag = new int[1000];
    public final int[] tempvalue = new int[100];
    /** Kill count (route detection input). */
    public int kills;
    /**
     * The player's "sin" / murder level (GML: {@code scr_murderlv()} result). Seeded
     * by the boss-select menu instead of being derived from a story playthrough.
     * Drives route gates — e.g. Papyrus turns genocide once this is ≥ 7.
     */
    public int murderlv;
    /** Hard-mode / genocide flavor toggle (global.osflavor). */
    public int osflavor;
    /** Selected boss group id, drives scr_battlegroup / BossRegistry. */
    public int battlegroup;

    // ---- Reset helpers ------------------------------------------------------

    /** Fresh process state: player stats and all scratch/flags cleared. */
    public void newGame() {
        mnfight = 99;
        myfight = 0;
        turntimer = 0;
        firingrate = 0;
        bmenuno = 0;
        bmenucoord = 0;
        inbattle = false;
        attacktype = 0;
        attacked = 0;

        lv = 1;
        maxhp = 20;
        hp = maxhp;
        at = 0;
        df = 0;
        xp = 0;
        inv = 0;
        km = 0;

        java.util.Arrays.fill(idealborder, 0.0);
        border = 0;
        darkify = false;
        shakify = 0;

        java.util.Arrays.fill(monsterhp, 0);
        java.util.Arrays.fill(monstermaxhp, 0);
        java.util.Arrays.fill(monsteratk, 0);
        java.util.Arrays.fill(monsterdef, 0);
        myself = 0;
        mercy = 0;
        mercymod = 0;

        faceemotion = 0;
        hurtanim = 0;
        damagetimer = 0;
        fivedamage = 0;
        ratings = 0;
        hope = 0;

        damage = 0;
        takedamage = 0;

        java.util.Arrays.fill(flag, 0);
        java.util.Arrays.fill(tempvalue, 0);
        kills = 0;
        murderlv = 0;
        osflavor = 0;
        battlegroup = 0;
    }

    /** Enter a battle against the given group; seeds per-battle turn state. */
    public void enterBattle(int group) {
        battlegroup = group;
        inbattle = true;
        mnfight = 99;
        myfight = 0;
        attacked = 0;
        turntimer = 0;
    }
}
