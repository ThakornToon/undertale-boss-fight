package util;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Loads and caches game assets from {@code src/main/resources/} (sprites and,
 * later, sounds). Mirrors GML's sprite/sound resource lookup: load once by name,
 * reuse the cached handle.
 *
 * <p>Missing assets are tolerated — {@link #image} returns {@code null} and logs
 * once — so the Core compiles and runs before any real art is imported. Boss and
 * bullet rendering will guard on null.
 *
 * // GML: sprite_index / sound resources + draw_sprite
 */
public final class Assets {

    private Assets() {
    }

    private static final Map<String, BufferedImage> IMAGE_CACHE = new HashMap<>();
    private static final Map<String, Boolean> MISSING_LOGGED = new HashMap<>();

    /** Root within the classpath where sprites live. */
    private static final String SPRITE_ROOT = "/images/";

    /**
     * Load a sprite by frame name (no extension), e.g. {@code "spr_heart_0"} →
     * {@code resources/images/spr_heart_0.png}. This is the form boss/bullet
     * rendering should use — it mirrors a GML {@code sprite_index} name. Returns a
     * cached image or {@code null} if the resource is absent.
     */
    public static BufferedImage sprite(String name) {
        return image(name.endsWith(".png") ? name : name + ".png");
    }

    /**
     * Load a sprite by resource path (relative to {@code resources/images/}),
     * e.g. {@code "spr_heart_0.png"}. Returns a cached image or {@code null} if
     * the resource is absent.
     */
    public static BufferedImage image(String path) {
        BufferedImage cached = IMAGE_CACHE.get(path);
        if (cached != null) {
            return cached;
        }
        String resource = path.startsWith("/") ? path : SPRITE_ROOT + path;
        try (InputStream in = Assets.class.getResourceAsStream(resource)) {
            if (in == null) {
                logMissingOnce(resource);
                return null;
            }
            BufferedImage img = ImageIO.read(in);
            if (img != null) {
                IMAGE_CACHE.put(path, img);
            }
            return img;
        } catch (IOException e) {
            logMissingOnce(resource + " (" + e.getMessage() + ")");
            return null;
        }
    }

    private static void logMissingOnce(String resource) {
        if (MISSING_LOGGED.putIfAbsent(resource, Boolean.TRUE) == null) {
            System.err.println("[Assets] missing resource: " + resource);
        }
    }

    /** Drop every cached asset (e.g. on scene teardown / hot reload). */
    public static void clearCache() {
        IMAGE_CACHE.clear();
        MISSING_LOGGED.clear();
    }
}
