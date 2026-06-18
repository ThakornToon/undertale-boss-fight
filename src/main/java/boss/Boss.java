package boss;

import battle.ActDispatcher;
import battle.BulletBoard;
import battle.DamageSystem;
import battle.MercySystem;
import battle.Soul;
import core.Entity;
import core.EntityManager;
import core.GlobalState;

/**
 * Abstract boss controller — the turn-logic half of the controller/body split.
 * Core ({@code BattleScene} + {@code TurnManager}) calls the SPI hooks; the
 * concrete boss fills them. The boss owns its {@link Monster} stat block, its
 * {@link BossBody}, and its universal {@code turns} counter, and reads/writes all
 * shared state through {@link GlobalState}.
 *
 * <p>{@code BattleScene} injects references to the battle systems the boss needs
 * (soul, board, damage, mercy, act) before calling {@link #setup()}, so a boss can
 * mutate the box, set the soul mode, configure damage overrides, and supply ACT
 * options without reaching into Core internals.
 *
 * // GML: obj_monsterparent boss controllers (obj_papyrusboss, obj_sansb, ...)
 */
public abstract class Boss extends Entity {

    protected static final GlobalState G = GlobalState.get();

    /** This boss's monster slot (global.myself). */
    public final Monster stats;
    public BossBody body;

    /** Universal turn counter shared by the boss-SPI table (GML: turns/fighto). */
    public int turns;

    /** Set true when the fight has ended (spare or kill); freezes the loop. */
    public boolean battleOver;
    /**
     * Suppress the top-left "Name N HP" header. Mettaton EX replaces it with the
     * RATINGS meter, which occupies the same corner.
     */
    public boolean hideHpHeader;
    /**
     * True when the fight ended with nobody dead — the player spared the boss (or
     * was spared). The scene shows this outcome inside the combat box (rather than
     * as a floating banner) before returning to the boss-select menu.
     */
    public boolean peacefulEnd;
    /** End-of-battle banner the scene displays (e.g. spare/kill outcome). */
    public String banner = "";
    /**
     * Notification / flavor line (GML: global.msg[0] "Papyrus is ..."). Shown in
     * the menu box and as action results — never in the speech bubble.
     */
    public String message = "";

    /**
     * The boss's actual spoken line — only this drives the white speech bubble.
     * Empty when the boss isn't talking, so notifications never pop a bubble.
     */
    public String dialogue = "";

    // ---- Injected by BattleScene before setup() ----------------------------
    protected Soul soul;
    protected BulletBoard board;
    protected DamageSystem damage;
    protected MercySystem mercy;
    protected ActDispatcher act;

    protected Boss(EntityManager manager, Monster stats) {
        super(manager);
        this.stats = stats;
        this.depth = 10; // GML obj_papyrusboss depth
    }

    /** Wire the battle systems in. Called once by {@link battle.BattleScene}. */
    public final void attach(Soul soul, BulletBoard board, DamageSystem damage,
                             MercySystem mercy, ActDispatcher act) {
        this.soul = soul;
        this.board = board;
        this.damage = damage;
        this.mercy = mercy;
        this.act = act;
    }

    // ---- Boss SPI (Core calls these; the boss fills them) ------------------

    /** GML: Create event — scr_monstersetup, initial soul mode, border, mercymod. */
    public abstract void setup();

    /**
     * The looping battle theme for this fight, as a {@code resources/audio/*} path
     * ({@link battle.BattleScene} starts it after {@link #setup()} and stops it on
     * scene exit). Return {@code null} for no theme, or for a boss that drives its
     * own music with volume ramps (Sans). GML: the {@code caster_loop(mus_*)} call.
     */
    public String musicPath() {
        return null;
    }

    /** GML: mnfight==3 — pick this turn's attack, spawn it, set turntimer/border. */
    public abstract void chooseAttack();

    /** GML: an ACT submenu option was chosen (whatiheard). */
    public void onAct(int whatiheard) {
    }

    /** GML: the player chose MERCY → Spare and it was accepted. */
    public void onSpare() {
    }

    /** GML: the boss took a FIGHT hit this turn (reaction hook). */
    public void onDamaged() {
    }

    /**
     * GML: scr_monsterdefeat — the boss was defeated by a FIGHT hit. The default
     * ends the fight immediately with a generic banner; a boss may override to play
     * a death cutscene first (it then ends the fight itself when the cutscene is
     * done). Core calls this once, on the lethal hit.
     */
    public void onDefeat() {
        stats.defeat();
        battleOver = true;
        if (banner == null || banner.isEmpty()) {
            banner = "You won the fight.";
        }
        G.mnfight = -1;
    }

    /** True once the intro cutscene has finished and the menu may open. */
    public boolean introComplete() {
        return true;
    }

    /** Boss-specific mnfight==5 special/transform handler. Default: no-op. */
    public void onSpecial() {
    }
}
