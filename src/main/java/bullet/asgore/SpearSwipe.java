package bullet.asgore;

import battle.Soul;
import boss.AsgoreBody;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import util.Assets;

/**
 * Asgore's trident swipe (GML: {@code obj_asgore_spearswipegen} +
 * {@code obj_asgore_spearswipe}). Fandom pattern 6. Each swing is a colour-cue
 * attack: Asgore's eyes flash a colour, then he slashes and the whole box flashes
 * that colour the instant the blade connects. <b>Light blue</b> (GML type 1) means
 * hold still; <b>orange</b> (type 2) means keep moving — getting it wrong on the
 * connect frame is the only way to be hit (the paws/blade themselves do no damage).
 *
 * <p>This is a close port of the GML so it reads smoothly like the real fight: a
 * silhouette + flashing eyes warning previews the colours, then {@code amt} swings
 * play through the seven {@code spr_asgore_swipe_*} frames at {@code cutspeed},
 * mirroring left/right each swing, the spear layer tinted the swing colour and the
 * box flashing on the connect frame. Everything draws <em>in front</em> of the box
 * (so the slash crosses it); {@link AsgoreBody} hides itself while {@code swiping}.
 *
 * // GML: obj_asgore_spearswipegen + obj_asgore_spearswipe
 */
public final class SpearSwipe extends AttackPattern {

    /** GML image_blend 16754964 (BGR) = light blue; 4235519 = orange. */
    private static final Color BLUE = new Color(20, 160, 255);
    private static final Color ORANGE = new Color(255, 160, 64);

    private static final int FRAMES = 7;        // spr_asgore_swipe_* : index 0..6
    private static final int CONNECT = 5;       // GML: the hit lands at image_index 5
    private static final int FLASH_FRAMES = 7;  // the connect burst fades over this many frames

    // GML world placement of the swing sprite (gen at body-50; swipe x += 180).
    private static final int SWING_X = 338;     // origin x=98 → body content centres ~322
    private static final int SWING_Y = 8;
    private static final double SWING_SCALE = 2.0;
    private static final int SWING_ORIGIN_X = 98;

    private final Soul soul;
    private final AsgoreBody body;
    public int diff;

    // GML difficulty table (obj_asgore_spearswipegen event 2).
    private final int amt;
    private final double cutspeed;
    private final int swipewait;
    private final int initswipewait;
    private final int flashtimer;
    private final int typeamt;
    /** GML type[]: 1 = blue (don't move), 2 = orange (keep moving). */
    private final int[] type = new int[8];

    private enum State { WARN, WINDUP, SWING, DONE }
    private State state = State.WARN;
    private int warnIndex;
    private int warnTimer;
    private int swing;
    private boolean mirror;
    private double frame;
    private int timer;
    private boolean struck;
    private int flash;          // box colour-flash countdown on the connect frame

    private static final Map<String, BufferedImage> TINT_CACHE = new HashMap<>();

    public SpearSwipe(EntityManager manager, Soul soul, AsgoreBody body, int diff) {
        super(manager);
        this.soul = soul;
        this.body = body;
        this.diff = diff;
        this.depth = -5;        // in front of the box so the slash and flash read on top.

        // GML obj_asgore_spearswipegen + obj_asgore_spearswipe difficulty table.
        switch (diff) {
            case 0 -> { amt = 1; cutspeed = 0.5; swipewait = 2; initswipewait = 5; flashtimer = 12; typeamt = 1; }
            case 1 -> { amt = 2; cutspeed = 0.5; swipewait = 0; initswipewait = 8; flashtimer = 9; typeamt = 2; }
            case 2 -> { amt = 2; cutspeed = 1.0; swipewait = 3; initswipewait = 4; flashtimer = 7; typeamt = 2; }
            default -> { amt = 3; cutspeed = 1.0; swipewait = 3; initswipewait = 3; flashtimer = 7; typeamt = 3; }
        }
        // GML: type[0] = 1, the rest random blue/orange.
        type[0] = 1;
        for (int i = 1; i < type.length; i++) {
            type[i] = util.GMLHelper.choose(new int[] { 1, 2 });
        }
        warnTimer = flashtimer;
    }

    @Override
    public void update() {
        body.swiping = true;        // AsgoreBody draws nothing; we draw the swing.
        switch (state) {
            case WARN -> {
                if (--warnTimer <= 0) {
                    warnIndex++;
                    warnTimer = flashtimer;
                    if (warnIndex > typeamt) {
                        state = State.WINDUP;
                        timer = initswipewait;
                        frame = 0;
                    }
                }
            }
            case WINDUP -> {
                if (--timer <= 0) {
                    state = State.SWING;
                    frame = 0;
                    struck = false;
                }
            }
            case SWING -> {
                frame += cutspeed;
                if (!struck && frame >= CONNECT) {
                    resolveHit();
                    struck = true;
                    flash = FLASH_FRAMES;
                }
                if (frame >= FRAMES - 1) {
                    swing++;
                    mirror = !mirror;       // GML flips image_xscale each swing
                    if (swing >= amt) {
                        state = State.DONE;
                        timer = 30;
                    } else {
                        state = State.WINDUP;
                        timer = swipewait;
                    }
                }
            }
            case DONE -> {
                if (--timer <= 0) {
                    body.swiping = false;
                    G.turntimer = 0;        // hand the turn back to the player menu
                    manager.destroy(this);
                }
            }
        }
        if (flash > 0) {
            flash--;
        }
    }

    /** GML connect frame: blue hits a moving soul, orange hits a still one. */
    private void resolveHit() {
        boolean moving = soul.leftHeld || soul.rightHeld || soul.upHeld || soul.downHeld;
        boolean hit = (type[swing] == 1) ? moving : !moving;
        if (hit) {
            soul.hurt(6);
        }
    }

    private Color swingColor() {
        return type[Math.min(swing, type.length - 1)] == 1 ? BLUE : ORANGE;
    }

    @Override
    public void render(Graphics2D g) {
        if (state == State.WARN) {
            renderWarning(g);
            return;
        }
        // The connect burst flashes BEHIND Asgore (he stays white on top of it), then
        // the swing sprite draws over it — matching the big colour flash in the video.
        if (flash > 0) {
            renderConnectFlash(g);
        }
        renderSwing(g);
    }

    /** The flash-silhouette of Asgore with his eyes flashing the upcoming colours. */
    private void renderWarning(Graphics2D g) {
        BufferedImage sil = Assets.sprite("spr_asgore_flashsilhouette_0");
        if (sil != null) {
            g.drawImage(sil, 158, 8,
                    (int) (sil.getWidth() * SWING_SCALE), (int) (sil.getHeight() * SWING_SCALE), null);
        }
        // Eyes flash during the first half of each window — a bright colored glow in
        // the eye sockets that telegraphs the upcoming swing's colour.
        if (warnTimer > flashtimer / 2) {
            Color c = type[Math.min(warnIndex, type.length - 1)] == 1 ? BLUE : ORANGE;
            drawEye(g, c, 305, 70);
            drawEye(g, c, 332, 70);
        }
    }

    private void drawEye(Graphics2D g, Color c, int cx, int cy) {
        // A soft outer glow + a bright core, so the colour cue pops on the silhouette.
        Composite old = g.getComposite();
        g.setColor(c);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        g.fillOval(cx - 11, cy - 11, 22, 22);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        g.fillOval(cx - 5, cy - 5, 10, 10);
        g.setComposite(old);
    }

    /**
     * The impact flash: a big bright burst of the swing colour centred on Asgore that
     * floods the area above the box and the box interior, fading over a few frames
     * (GML's full-screen colour flash on the connect frame). Asgore (drawn after)
     * stays visible white on top, and the soul (higher up the draw order) shows in
     * the flooded box.
     */
    private void renderConnectFlash(Graphics2D g) {
        float a = Math.min(0.9f, flash / (float) FLASH_FRAMES * 0.95f);
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        g.setColor(swingColor());
        // The burst over Asgore + the upper field.
        g.fillOval(320 - 200, 150 - 200, 400, 410);
        // Flood the combat box so the slash visibly "lands" inside it.
        int l = (int) G.idealborder[0];
        int t = (int) G.idealborder[2];
        g.fillRect(l, t, (int) (G.idealborder[1] - l), (int) (G.idealborder[3] - t));
        g.setComposite(old);
    }

    /** Draw the swing: Asgore's body (white) + the spear/slash tinted the swing colour. */
    private void renderSwing(Graphics2D g) {
        int fi = (int) Math.min(FRAMES - 1, Math.max(0, frame));
        BufferedImage base = Assets.sprite("spr_asgore_swipe_nospear_" + fi);
        BufferedImage spear = tint("spr_asgore_swipe_spear_" + fi, swingColor());
        // The origin (x=98, near centre of the 196-wide sprite) maps to SWING_X; the
        // left edge is the same whether mirrored or not since 98 ≈ 196-98.
        int left = (int) (SWING_X - SWING_ORIGIN_X * SWING_SCALE);
        drawSwingLayer(g, base, left);
        drawSwingLayer(g, spear, left);
    }

    /** Blit one swing layer, horizontally flipped on alternate swings (GML xscale flip). */
    private void drawSwingLayer(Graphics2D g, BufferedImage img, int left) {
        if (img == null) {
            return;
        }
        int w = (int) (img.getWidth() * SWING_SCALE);
        int h = (int) (img.getHeight() * SWING_SCALE);
        int sw = img.getWidth();
        int sh = img.getHeight();
        if (mirror) {
            // Flip the source horizontally (src x runs sw → 0).
            g.drawImage(img, left, SWING_Y, left + w, SWING_Y + h, sw, 0, 0, sh, null);
        } else {
            g.drawImage(img, left, SWING_Y, left + w, SWING_Y + h, 0, 0, sw, sh, null);
        }
    }

    /**
     * Return a colour-multiplied copy of a white line-art sprite (cached). GML tints
     * the spear/eye layers with {@code image_blend}; this reproduces that multiply.
     */
    private static BufferedImage tint(String name, Color c) {
        String key = name + "#" + c.getRGB();
        BufferedImage cached = TINT_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        BufferedImage src = Assets.sprite(name);
        if (src == null) {
            return null;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        double rf = c.getRed() / 255.0;
        double gf = c.getGreen() / 255.0;
        double bf = c.getBlue() / 255.0;
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) {
                    continue;
                }
                int r = (int) (((argb >> 16) & 0xFF) * rf);
                int gg = (int) (((argb >> 8) & 0xFF) * gf);
                int b = (int) ((argb & 0xFF) * bf);
                out.setRGB(x, y, (a << 24) | (r << 16) | (gg << 8) | b);
            }
        }
        TINT_CACHE.put(key, out);
        return out;
    }
}
