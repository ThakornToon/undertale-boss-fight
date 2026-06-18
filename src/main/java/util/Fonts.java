package util;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * The game's on-theme pixel fonts, loaded once from {@code resources/fonts/} and
 * handed out at any size. Replaces the stock {@code "Monospaced"} the UI used to
 * draw with, so menus / HUD / dialogue read like a retro game.
 *
 * <ul>
 *   <li>{@link #ui(float)} — <b>VT323</b>, a clean monospaced pixel font (the
 *       closest free match to Undertale's "Determination Mono"); used for all body
 *       text: menus, the combat box, the HUD, dialogue.</li>
 *   <li>{@link #title(float)} — <b>Press Start 2P</b>, a chunky NES-style face for
 *       headings (the boss-select title, "PAUSED").</li>
 * </ul>
 *
 * <p>Both are bundled TTFs (OFL). If a font can't be loaded (e.g. stripped from the
 * jar), a derived {@code Monospaced} of the same size is returned so text still
 * draws. Derived sizes are cached so we don't re-derive every frame.
 */
public final class Fonts {

    private Fonts() {
    }

    private static final Font UI_BASE = load("/fonts/vt323.ttf", Font.PLAIN);
    private static final Font TITLE_BASE = load("/fonts/press-start-2p.ttf", Font.PLAIN);

    private static final Map<String, Font> CACHE = new HashMap<>();

    /** Body font (VT323) at the given point size. */
    public static Font ui(float size) {
        return derive(UI_BASE, "ui", size);
    }

    /** Heading font (Press Start 2P) at the given point size. */
    public static Font title(float size) {
        return derive(TITLE_BASE, "title", size);
    }

    private static Font derive(Font base, String key, float size) {
        String id = key + size;
        Font cached = CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        Font f = base.deriveFont(size);
        CACHE.put(id, f);
        return f;
    }

    /** Load + register a bundled TTF; falls back to Monospaced if it can't be read. */
    private static Font load(String resource, int style) {
        try (InputStream in = Fonts.class.getResourceAsStream(resource)) {
            if (in != null) {
                Font f = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(style, 16f);
                // Registering lets the JVM hint/render it consistently across sizes.
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
                return f;
            }
        } catch (Exception e) {
            // fall through to the stock fallback
        }
        return new Font("Monospaced", style, 16);
    }
}
