package battle;

/**
 * The {@code SCR_BORDERSETUP} preset table: maps {@code global.border} preset
 * indices to the four combat-box coordinates [left, right, top, bottom].
 *
 * <p>Coordinates assume the Undertale 640x480 play field. Presets here cover the
 * default centered box plus the named variants the analyses call out (Undyne
 * 12/13, rising-spear taller box 14, Asgore swipe-widen 29/30). Bosses that need
 * a bespoke box can still drive {@link BulletBoard} directly.
 *
 * // GML: SCR_BORDERSETUP.gml (sets global.idealborder[0..3])
 */
public final class BorderSetup {

    private BorderSetup() {
    }

    /** Screen dimensions of the GML play field. */
    public static final int VIEW_W = 640;
    public static final int VIEW_H = 480;

    // The standard small command box, centered horizontally, sitting low.
    private static final double DEF_L = 32;
    private static final double DEF_R = 608;
    private static final double DEF_T = 250;
    private static final double DEF_B = 385;

    /**
     * Fill {@code out[0..3]} with [left, right, top, bottom] for the preset.
     * Unknown presets fall back to the default box.
     */
    public static void apply(int preset, double[] out) {
        double l = DEF_L, r = DEF_R, t = DEF_T, b = DEF_B;
        switch (preset) {
            case 0 -> { /* default box */ }
            case 1 -> { // wide intro box
                l = 32; r = 608; t = 240; b = 385;
            }
            case 2 -> { // small square (Papyrus / blue-soul jump room)
                l = 220; r = 420; t = 250; b = 385;
            }
            case 12 -> { // Undyne green-soul square
                l = 247; r = 393; t = 175; b = 321;
            }
            case 13 -> { // Undyne slightly larger
                l = 230; r = 410; t = 165; b = 331;
            }
            case 14 -> { // rising-spear taller box (Undying red)
                l = 247; r = 393; t = 120; b = 360;
            }
            case 24 -> { // Mettaton EX default combat box
                l = 235; r = 405; t = 250; b = 385;
            }
            case 25 -> { // Mettaton EX taller box
                l = 235; r = 405; t = 160; b = 385;
            }
            case 26 -> { // Mettaton EX narrow vertical box (frames 8/9/13/16)
                l = 295; r = 345; t = 250; b = 385;
            }
            case 27 -> { // Mettaton EX 4-wide box, taller — fits the 4 rewind rows
                l = 270; r = 370; t = 200; b = 385;
            }
            case 29 -> { // Asgore swipe — widen left
                l = 32; r = 393; t = 250; b = 385;
            }
            case 30 -> { // Asgore swipe — widen right
                l = 247; r = 608; t = 250; b = 385;
            }
            case 99 -> { // full-screen (special attacks / cutscenes)
                l = 20; r = 620; t = 20; b = 460;
            }
            default -> { /* default box */ }
        }
        out[0] = l;
        out[1] = r;
        out[2] = t;
        out[3] = b;
    }
}
