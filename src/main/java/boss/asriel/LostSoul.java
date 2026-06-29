package boss.asriel;

import battle.Soul;
import battle.SoulMode;
import boss.BossBody;
import core.EntityManager;
import java.util.List;

/**
 * One of the six monster SOULs Asriel stole, met as a <b>Lost Soul</b> during Part B's
 * SAVE phase. Each encounter is cleared with <b>ACT, never damage</b>: the friend attacks
 * with their signature pattern while the player ACTs to reach them; after enough ACTs the
 * SOUL remembers and is freed (its {@code global.flag[505..508]} is set).
 *
 * <p>Concrete encounters supply the silhouette body, soul mode, box, attack and ACT graph.
 * {@link boss.AsrielBoss} drives the active encounter through these hooks.
 *
 * // GML: the SAVE → flag[505..508] Lost-Soul sub-battles
 */
public abstract class LostSoul {

    /** Display name (also the SAVE-menu label). */
    public final String name;
    /** {@code global.flag[]} index set when this friend is freed (505..508). */
    public final int flag;
    /** How many ACTs free this soul. */
    public final int actsToFree;
    /** ACTs performed so far. */
    public int acts;

    protected LostSoul(String name, int flag, int actsToFree) {
        this.name = name;
        this.flag = flag;
        this.actsToFree = actsToFree;
    }

    public boolean freed() {
        return acts >= actsToFree;
    }

    /** The grey silhouette drawn above the box. */
    public abstract BossBody createBody(EntityManager manager);

    public abstract SoulMode soulMode();

    /** Combat box {left, right, top, bottom}. */
    public abstract double[] border();

    public abstract List<String> actLabels();

    public abstract List<Integer> actIds();

    /** Spawn this enemy turn's attack; set {@code turntimer} on {@code G}. */
    public abstract void chooseAttack(EntityManager manager, Soul soul);

    /**
     * Per-frame hook while the encounter is active (default no-op). Used by the YELLOW
     * encounters to spawn the soul's player-bullets, which the boss body normally owns.
     */
    public void update(EntityManager manager, Soul soul) {
    }

    /** Handle one ACT (whatiheard); return the result line. Increment {@link #acts} when it lands. */
    public abstract String onAct(int whatiheard);

    /** The line the freed friend says (shown when the SOUL is saved). */
    public abstract String freedLine();

    /** "* The Lost Soul appeared." style intro. */
    public String introLine() {
        return "* The Lost Soul appeared.";
    }
}
