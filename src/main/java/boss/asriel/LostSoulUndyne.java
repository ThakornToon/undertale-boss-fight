package boss.asriel;

import battle.Soul;
import battle.SoulMode;
import boss.BossBody;
import bullet.undyne.SpearBlocker;
import core.EntityManager;
import core.GlobalState;
import java.util.List;

/**
 * The <b>Undyne</b> Lost Soul (GML SAVE entry → {@code flag[505]}). She fights in GREEN
 * mode — spears thrown from all sides that you block by turning the shield — and is freed
 * after three ACTs (Fake Hit · Recipe · Smile · Clash). Reuses the Undyne fight's
 * {@link SpearBlocker} shield engine.
 *
 * <p>Canon freed line: <i>"Well, some humans are OK, I guess!"</i>
 */
public final class LostSoulUndyne extends LostSoul {

    private static final GlobalState G = GlobalState.get();
    // GML obj_ripoff_undyne: the green-shield SPEAR turn opens border 12 — a small square
    // (room-centre ±40), spears coming from all four sides.
    private static final double[] GREEN_BOX = { 280, 360, 200, 280 };

    private boolean fakeHit;
    private boolean recipe;
    private boolean smile;

    public LostSoulUndyne() {
        super("Undyne", 505, 3);
    }

    @Override
    public BossBody createBody(EntityManager manager) {
        return new LostSoulBody(manager, "spr_undyne_d_0", 3.0, 320, 96);
    }

    @Override
    public SoulMode soulMode() {
        return SoulMode.GREEN;
    }

    @Override
    public double[] border() {
        return GREEN_BOX;
    }

    @Override
    public List<String> actLabels() {
        return List.of("Fake Hit", "Recipe", "Smile", "Clash");
    }

    @Override
    public List<Integer> actIds() {
        return List.of(0, 1, 2, 3);
    }

    @Override
    public void chooseAttack(EntityManager manager, Soul soul) {
        G.turntimer = 300;          // the blocker ends the turn when its queue empties
        SpearBlocker blocker = new SpearBlocker(manager, soul);
        blocker.lesson = Math.min(3, acts + 1);
        blocker.dmg = 4;            // gentle — you cannot die in Part B anyway
        manager.add(blocker);
    }

    @Override
    public String onAct(int whatiheard) {
        String line;
        switch (whatiheard) {
            case 0 -> {
                line = "* You let one of her spears  graze you on purpose.  * Her fury wavers.";
                if (!fakeHit) {
                    fakeHit = true;
                    acts++;
                }
            }
            case 1 -> {
                line = "* You think back to the  cooking lesson.  * The Lost Soul's grip loosens.";
                if (!recipe) {
                    recipe = true;
                    acts++;
                }
            }
            case 2 -> {
                line = "* You smile at the Lost Soul.  * Something flickers in her eye.";
                if (!smile) {
                    smile = true;
                    acts++;
                }
            }
            default -> line = "* You clash your spirit against  the Lost Soul's spears!";
        }
        if (!freed() && acts > 0) {
            line += "  * The Lost Soul is remembering...";
        }
        return line;
    }

    @Override
    public String freedLine() {
        return "Well, some humans are OK, I guess!";
    }

    @Override
    public String introLine() {
        return "* The Lost Soul appeared.  * (It's UNDYNE.)";
    }
}
