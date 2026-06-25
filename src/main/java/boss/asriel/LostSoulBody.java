package boss.asriel;

import boss.BossBody;
import core.EntityManager;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import util.Assets;

/**
 * The ghostly silhouette(s) of Asriel's stolen friend(s), floating above the box during a
 * Lost-Soul SAVE encounter (GML: the Lost Souls are drawn as flat grey shapes of the
 * friend). Each part reuses the friend's battle/overworld sprite recoloured to a single
 * light grey; paired encounters (Sans & Papyrus, Toriel & Asgore) add a second part.
 */
public final class LostSoulBody extends BossBody {

    private record Part(String sprite, double scale, int cx, int cy) { }

    private final List<Part> parts = new ArrayList<>();
    private final Map<String, BufferedImage> cache = new HashMap<>();
    private double siner;

    public LostSoulBody(EntityManager manager, String sprite, double scale, int cx, int cy) {
        super(manager);
        this.depth = 1100;
        parts.add(new Part(sprite, scale, cx, cy));
    }

    /** Add a second silhouette (for a paired encounter); returns this for chaining. */
    public LostSoulBody add(String sprite, double scale, int cx, int cy) {
        parts.add(new Part(sprite, scale, cx, cy));
        return this;
    }

    @Override
    public void update() {
        siner++;
    }

    @Override
    public void render(Graphics2D g) {
        int bob = (int) Math.round(Math.sin(siner / 18.0) * 4);
        for (Part p : parts) {
            BufferedImage img = cache.computeIfAbsent(p.sprite(), s -> {
                BufferedImage src = Assets.sprite(s);
                return src == null ? null : toSilhouette(src);
            });
            if (img == null) {
                continue;
            }
            int w = (int) (img.getWidth() * p.scale());
            int h = (int) (img.getHeight() * p.scale());
            g.drawImage(img, p.cx() - w / 2, p.cy() - h / 2 + bob, w, h, null);
        }
    }

    /** Recolour every opaque pixel to a flat light grey (the Lost-Soul ghost look). */
    private static BufferedImage toSilhouette(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int a = (src.getRGB(x, y) >>> 24) & 0xFF;
                if (a != 0) {
                    out.setRGB(x, y, (a << 24) | 0x00E6E6EE);   // near-white ghost
                }
            }
        }
        return out;
    }
}
