package battle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Turns a chosen ACT submenu option into a {@code whatiheard} id and routes it to
 * the active boss. This class owns only the menu→{@code whatiheard} plumbing and
 * the shared CHECK text; the per-boss ACT graph (insult/flirt, challenge/plead,
 * pose/boast, talk, hope/dream, spare, …) lives in each boss's {@code onAct}
 * handler, reached through the {@link #onAct} hook {@code BattleScene} wires.
 *
 * // GML: the ACT menu → "whatiheard" routing (per-boss ACT branches)
 */
public final class ActDispatcher {

    /** The ACT options offered this turn (labels). The boss sets these. */
    private final List<String> options = new ArrayList<>();
    /** Parallel {@code whatiheard} ids; index-aligned with {@link #options}. */
    private final List<Integer> whatiheard = new ArrayList<>();

    /** Boss-supplied handler: receives the chosen {@code whatiheard} id. */
    public IntConsumer onAct = id -> { };

    /** Shared CHECK line; bosses may override per CHECK option. */
    public String checkText = "* Check.";

    /** Boss replaces the ACT option set at the start of its turn / setup. */
    public void setOptions(List<String> labels, List<Integer> ids) {
        options.clear();
        whatiheard.clear();
        options.addAll(labels);
        whatiheard.addAll(ids);
    }

    public List<String> optionLabels() {
        return options;
    }

    public int optionCount() {
        return options.size();
    }

    /** GML: player picked ACT option {@code index} → set whatiheard, run graph. */
    public void choose(int index) {
        if (index < 0 || index >= whatiheard.size()) {
            return;
        }
        onAct.accept(whatiheard.get(index));
    }
}
