package bullet;

/**
 * A Mettaton EX bullet the player's yellow {@link PlayerBullet} can hit — parasol
 * Mettatons, plus-bombs, black-circle boxes, the disco ball, the moving legs. The
 * gun scans for these each frame and, on overlap, fires {@link #onShot()} (destroy /
 * detonate / toggle). {@link #onShot()} returns whether the gun bullet is consumed.
 *
 * // GML: obj_heartshot collision with the shootable attack objects
 */
public interface Shootable {

    /** True if the point {@code (x,y)} is on this bullet. */
    boolean hitBy(double x, double y);

    /** React to a gun hit. Returns true if the gun bullet is consumed (stops here). */
    boolean onShot();

    /**
     * Ratings awarded for this gun hit: 0 if nothing was destroyed (a block or a
     * colour/state toggle), 5 for a normal destroy, 15 for the heart core. Read right
     * after {@link #onShot()}.
     */
    default int shotRating() {
        return 5;
    }
}
