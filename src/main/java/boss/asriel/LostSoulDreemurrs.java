package boss.asriel;

import battle.Soul;
import battle.SoulMode;
import boss.BossBody;
import bullet.asgore.ConvergingFireGen;
import bullet.asgore.RandomHandGen;
import core.EntityManager;
import core.GlobalState;
import java.util.List;

/**
 * The paired <b>Toriel &amp; Asgore</b> Lost Soul (GML SAVE entry → {@code flag[508]}). The
 * Dreemurrs fight with RED-soul fire — partial fireball-circles spiral inward and hands
 * sweep the edges trailing flame — and are freed after four ACTs across both parents
 * (Toriel: Hug/Mercy · Asgore: Stare/Hug). Reuses the Asgore fight's converging-fire and
 * flying-hand generators.
 *
 * <p>Canon freed lines: Toriel <i>"Your fate is up to you now!"</i> · Asgore
 * <i>"You are our future!"</i>
 */
public final class LostSoulDreemurrs extends LostSoul {

    private static final GlobalState G = GlobalState.get();
    // GML obj_ripoff_toriel → SCR_BORDERSETUP border 29 (the Dreemurrs' RED fire box).
    private static final double[] BOX = { 207, 427, 250, 385 };

    private final boolean[] done = new boolean[4];
    private int turn;

    public LostSoulDreemurrs() {
        super("Toriel & Asgore", 508, 4);
    }

    @Override
    public BossBody createBody(EntityManager manager) {
        return new LostSoulBody(manager, "spr_torielboss_0", 1.5, 150, 110)
                .add("spr_asgore_d_0", 2.2, 492, 112);
    }

    @Override
    public SoulMode soulMode() {
        return SoulMode.RED;
    }

    @Override
    public double[] border() {
        return BOX;
    }

    @Override
    public List<String> actLabels() {
        return List.of("Toriel: Hug", "Toriel: Mercy", "Asgore: Stare", "Asgore: Hug");
    }

    @Override
    public List<Integer> actIds() {
        return List.of(0, 1, 2, 3);
    }

    @Override
    public void chooseAttack(EntityManager manager, Soul soul) {
        // Two SEPARATE beats, alternating turn by turn — the hands belong to their OWN attack
        // and never mix with the fire-ring:
        //   • even turns = the spiralling fire-circles (obj_cfiregen) closing inward;
        //   • odd  turns = a relentless drizzle of fire-trailing hands (obj_randomhandgen) that
        //                  keeps sweeping in from every edge for the whole turn (not a single
        //                  two-hand burst that ends).
        if (turn % 2 == 0) {
            G.turntimer = 220;
            manager.add(new ConvergingFireGen(manager, soul, Math.min(turn / 2, 2)));
        } else {
            G.turntimer = 220;
            manager.add(new RandomHandGen(manager, soul, Math.max(30, 44 - (turn / 2) * 4)));
        }
        turn++;
    }

    @Override
    public String onAct(int whatiheard) {
        String line = switch (whatiheard) {
            case 0 -> "* You hug the TORIEL Lost Soul.  * She holds you close.";
            case 1 -> "* You ask the TORIEL Lost Soul  for mercy.";
            case 2 -> "* You meet the ASGORE Lost  Soul's gaze.";
            default -> "* You hug the ASGORE Lost Soul.  * His paws tremble.";
        };
        if (whatiheard >= 0 && whatiheard < done.length && !done[whatiheard]) {
            done[whatiheard] = true;
            acts++;
        }
        if (!freed() && acts > 0) {
            line += "  * The Lost Souls are remembering...";
        }
        return line;
    }

    @Override
    public String freedLine() {
        return "Your fate is up to you now! / You are our future!";
    }

    @Override
    public String introLine() {
        return "* The Lost Souls appeared.  * (It's TORIEL and ASGORE.)";
    }
}
