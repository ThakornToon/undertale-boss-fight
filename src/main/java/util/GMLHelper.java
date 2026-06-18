package util;

/**
 * Ports of the GameMaker built-in math/utility functions the battle code leans
 * on. GML angles are in <b>degrees</b> with a <b>screen-space</b> convention:
 * 0° points right, 90° points <em>up</em> (y grows downward, so trig on y is
 * negated). Every function here preserves that convention so ported formulas
 * port verbatim.
 *
 * // GML: lengthdir_x/y, point_direction, point_distance, random_range, choose, ...
 */
public final class GMLHelper {

    private GMLHelper() {
    }

    private static final double DEG2RAD = Math.PI / 180.0;
    private static final double RAD2DEG = 180.0 / Math.PI;

    /** GML: lengthdir_x(len, dir). */
    public static double lengthdir_x(double len, double dir) {
        return len * Math.cos(dir * DEG2RAD);
    }

    /** GML: lengthdir_y(len, dir). Note the negation — GML's y is inverted. */
    public static double lengthdir_y(double len, double dir) {
        return -len * Math.sin(dir * DEG2RAD);
    }

    /** GML: dcos(deg). */
    public static double dcos(double deg) {
        return Math.cos(deg * DEG2RAD);
    }

    /** GML: dsin(deg). */
    public static double dsin(double deg) {
        return Math.sin(deg * DEG2RAD);
    }

    /** GML: point_direction(x1, y1, x2, y2). Returns degrees in [0, 360). */
    public static double point_direction(double x1, double y1, double x2, double y2) {
        double d = Math.atan2(-(y2 - y1), x2 - x1) * RAD2DEG;
        return ((d % 360) + 360) % 360;
    }

    /** GML: point_distance(x1, y1, x2, y2). */
    public static double point_distance(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** GML: random_range(min, max) — uniform real in [min, max). */
    public static double random_range(double min, double max) {
        return min + Math.random() * (max - min);
    }

    /** GML: random(x) — uniform real in [0, x). */
    public static double random(double x) {
        return Math.random() * x;
    }

    /** GML: irandom(n) — uniform int in [0, n] inclusive. */
    public static int irandom(int n) {
        return (int) Math.floor(Math.random() * (n + 1));
    }

    /** GML: irandom_range(min, max) — uniform int in [min, max] inclusive. */
    public static int irandom_range(int min, int max) {
        return min + (int) Math.floor(Math.random() * (max - min + 1));
    }

    /** GML: choose(a, b, ...) — one argument at random. */
    @SafeVarargs
    public static <T> T choose(T... options) {
        return options[(int) Math.floor(Math.random() * options.length)];
    }

    /** GML: choose(...) over ints. */
    public static int choose(int... options) {
        return options[(int) Math.floor(Math.random() * options.length)];
    }

    /** GML: clamp(val, min, max). */
    public static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    /** GML: clamp(val, min, max) for ints. */
    public static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    /** GML: sign(x) — -1, 0, or 1. */
    public static int sign(double x) {
        return (int) Math.signum(x);
    }

    /**
     * GML: angle_difference(a, b) — smallest signed degrees to rotate b onto a,
     * result in (-180, 180].
     */
    public static double angle_difference(double a, double b) {
        double d = (a - b) % 360;
        if (d < -180) {
            d += 360;
        } else if (d > 180) {
            d -= 360;
        }
        return d;
    }

    /** GML: approach a toward b by step amt (a common UT idiom). */
    public static double approach(double a, double b, double amt) {
        if (a < b) {
            return Math.min(a + amt, b);
        }
        return Math.max(a - amt, b);
    }
}
