package boss;

import core.EntityManager;
import java.util.function.Function;

/**
 * Maps a {@code battlegroup} id to a boss factory (GML: {@code scr_battlegroup},
 * which spawned the right boss object for {@code global.battlegroup}). Adding a boss
 * is a one-line registration here plus its {@code Boss}/{@code BossBody} classes —
 * no Core change, by design.
 *
 * // GML: scr_battlegroup.gml
 */
public final class BossRegistry {

    /** Battlegroup ids (GML: global.battlegroup). */
    public static final int PAPYRUS = 1;
    public static final int SANS = 2;
    public static final int ASGORE = 3;
    public static final int UNDYNE = 4;
    public static final int METTATON_NEO = 5;
    public static final int METTATON_EX = 6;
    public static final int MUFFET = 7;

    private BossRegistry() {
    }

    /** GML: scr_battlegroup — build the controller for a battlegroup id. */
    public static Boss create(int battlegroup, EntityManager manager) {
        Function<EntityManager, Boss> factory = switch (battlegroup) {
            case PAPYRUS -> PapyrusBoss::new;
            case SANS -> SansBoss::new;
            case ASGORE -> AsgoreBoss::new;
            case UNDYNE -> UndyneBoss::new;
            case METTATON_NEO -> MettatonNeoBoss::new;
            case METTATON_EX -> MettatonExBoss::new;
            case MUFFET -> MuffetBoss::new;
            default -> PapyrusBoss::new;
        };
        return factory.apply(manager);
    }
}
