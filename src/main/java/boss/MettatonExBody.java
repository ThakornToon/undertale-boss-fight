package boss;

import battle.Soul;
import battle.SoulMode;
import bullet.PlayerBullet;
import core.EntityManager;
import core.GlobalState;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * Mettaton EX's body (GML: {@code obj_mettb_body}). The winged box-form humanoid,
 * composed each frame from two legs, two arms, the upper body, the heart-shaped
 * core (his weak point) and a face, all at the GML 2× scale and bobbing on a single
 * sine. The legs/arms/face re-randomise on the {@code dancewait} cadence — that's
 * the dance battle, and lowering {@code dancewait} speeds it up.
 *
 * <p>The body owns the player-gun loop for the yellow soul: each frame the soul
 * fires it spawns a {@link PlayerBullet} that travels up toward {@link #heartBox()};
 * a hit runs {@link #onHeartHit}, which the controller wires to apply HP damage and
 * an Action rating. Arms blow off at turn 14 ({@link #noarm}); the body white-fades
 * on the HP-death / ratings endings ({@link #fadewhite}).
 *
 * // GML: obj_mettb_body (legs/arms/spr_mettb_upperbody/heart/face)
 */
public final class MettatonExBody extends BossBody {

    private static final GlobalState G = GlobalState.get();
    private static final double SCALE = 2.0;

    // GML body spawn x; y tracks the box top each frame (obj_uborder.y - 136).
    private static final double BODY_X = 240;
    private double bodyY = 114;

    // GML leg pose table (indices 0..4): sprite, x/y offset, leg height.
    private static final String[] LEG_SPRITE = {
        "spr_mettleg1_0", "spr_mettleg2_0", "spr_mettleg3_0", "spr_mettleg4_0", "spr_mettleg5_0",
    };
    // Per-sprite GML offsets, aligned to LEG_SPRITE order (mettleg1..5). Index 2
    // (mettleg3, the tall vertical leg) is the neutral standing pose.
    private static final int[] LEG_XOFF = { -14, -16, -10, -18, -10 };
    private static final int[] LEG_YOFF = { 10, 6, 14, 2, 14 };
    private static final int[] LEG_H = { 36, 8, 30, 42, 60 };
    private static final int STAND_LEG = 2;
    // The arm-pose sprites (spr_mettarm1..8) the dance cycles through.
    private static final String[] ARM_SPRITE = {
        "spr_mettarm1_0", "spr_mettarm2_0", "spr_mettarm3_0", "spr_mettarm4_0",
        "spr_mettarm5_0", "spr_mettarm6_0", "spr_mettarm7_0", "spr_mettarm8_0",
    };

    /** GML sprite origins (xorig, yorigin) — parts anchor here, not top-left. */
    private static final java.util.Map<String, int[]> ORIGIN = java.util.Map.ofEntries(
            // The PNG is trimmed to its content bbox (orig canvas was ~68px tall;
            // GML yorig 65 lived in that canvas, bbox_top 32). Trimmed origin = 65-32.
            java.util.Map.entry("spr_mettb_upperbody", new int[] { 36, 33 }),
            java.util.Map.entry("spr_mettb_upperbodyheart", new int[] { 36, 65 }),
            // Legs/arms PNGs are also trimmed to bbox: origin = GML xorig/yorig
            // minus the bbox top-left offset (see spr_*.sprite.gmx).
            java.util.Map.entry("spr_mettleg1", new int[] { 4, 4 }),
            java.util.Map.entry("spr_mettleg2", new int[] { 6, 2 }),
            java.util.Map.entry("spr_mettleg3", new int[] { 7, 7 }),
            java.util.Map.entry("spr_mettleg4", new int[] { 3, 5 }),
            java.util.Map.entry("spr_mettleg5", new int[] { 3, 3 }),
            java.util.Map.entry("spr_mettarm1", new int[] { 32, 0 }),
            java.util.Map.entry("spr_mettarm2", new int[] { 34, 7 }),
            java.util.Map.entry("spr_mettarm3", new int[] { 25, 1 }),
            java.util.Map.entry("spr_mettarm4", new int[] { 21, 34 }),
            java.util.Map.entry("spr_mettarm5", new int[] { 3, 0 }),
            java.util.Map.entry("spr_mettarm6", new int[] { 21, 24 }),
            java.util.Map.entry("spr_mettarm7", new int[] { 51, -1 }),
            java.util.Map.entry("spr_mettarm8", new int[] { 2, 56 }),
            java.util.Map.entry("spr_mettface1", new int[] { 19, 17 }),
            java.util.Map.entry("spr_mettface_defeated", new int[] { 19, 17 }),
            java.util.Map.entry("spr_mettface_general", new int[] { 19, 17 }),
            java.util.Map.entry("spr_mettface_hurt", new int[] { 19, 17 }));

    private double siner;
    private int legl;
    private int legr;
    private int arml;
    private int armr;
    private int faceno;
    private double legh = LEG_H[0] * 2;

    /** GML dancewait: frames between pose changes (the dance tempo). */
    public int dancewait = 25;
    private int danceTimer = 25;
    /** GML dance: 1 = re-randomise poses on the cadence; -1 = hold a final pose. */
    public int dance = 1;

    /** GML noarm: the arms have blown off (turn 14). */
    public boolean noarm;
    /** GML pause: 0 idle · 1 / 2 = took a hit (show a hurt face). */
    public int pause;
    private int hurt = 2;
    private int hurtface;
    /** GML face_set: draw the "defeated"-set faces keyed on faceemotion. */
    public boolean faceSet;
    /** GML endface: the call-in finale face set. */
    public boolean endface;
    /** GML heartdead: the core is gone (stops drawing it / disables the hitbox). */
    public boolean heartdead;

    /** GML myalpha: the body dims during the YELLOW-soul attack turn (like Undyne). */
    private float myalpha = 1f;

    /** GML fadewhite: the white-out death ramp. */
    public boolean fadewhite;
    private double whiteval;
    private boolean fadeComplete;

    // ---- Player-gun wiring (set by the controller) -------------------------
    public Soul soul;
    /** Ratings awarded per gun hit (destroy = 5, heart = 15); wired by the controller. */
    public java.util.function.IntConsumer onScore = v -> { };

    public MettatonExBody(EntityManager manager) {
        super(manager);
        // Behind the combat box (box depth 1000) so the box's black interior occludes
        // the legs that dangle into it — only the upper body shows above the box, as
        // in the reference. The heart core sits above the box top, so it stays visible.
        this.depth = 1100;
        restPose();
    }

    /** Neutral standing pose (both legs vertical, arms down, calm face). */
    private void restPose() {
        legl = STAND_LEG;
        legr = STAND_LEG;
        arml = 0;
        armr = 0;
        faceno = 0;
        legh = LEG_H[STAND_LEG] * 2.0;
    }

    public boolean fadeComplete() {
        return fadeComplete;
    }

    /** GML event_user(12): the arms blow off. */
    public void blowOffArms() {
        noarm = true;
    }

    /** GML event_user(0): re-randomise legs, arms, and face (a dance beat). */
    public void randomizePose() {
        legl = (int) Math.floor(Math.random() * LEG_SPRITE.length);
        legr = (int) Math.floor(Math.random() * LEG_SPRITE.length);
        arml = (int) Math.floor(Math.random() * ARM_SPRITE.length);
        armr = (int) Math.floor(Math.random() * ARM_SPRITE.length);
        faceno = (int) Math.floor(Math.random() * 9);
        legh = Math.max(LEG_H[legl], LEG_H[legr]) * 2.0;
    }

    @Override
    public void update() {
        siner++;
        // GML: the body hangs above the box top.
        bodyY = G.idealborder[2] - 136;

        // GML myalpha: dim the body during the YELLOW-soul attack turn (like Undyne's
        // darkify), brighten back at the player's menu. Held bright during endings.
        float target = (G.mnfight == core.TurnManager.ENEMY_TURN && !fadewhite) ? 0.45f : 1f;
        myalpha += (target - myalpha) * 0.25f;

        // GML hurt/pause → hurt face.
        if (pause == 1 && hurt == 0) {
            hurt = 1;
            hurtface = Math.random() < 0.5 ? 0 : 1;
        } else if (pause == 2 && hurt == 0) {
            hurt = 1;
            hurtface = 2;
        }
        if (pause == 0) {
            hurt = 0;
        }

        // GML alarm[5]: re-pose on the dancewait cadence, but only while idle at the
        // menu (myfight==0 && mnfight==0 in GML) — he holds his pose during attacks.
        if (dance == 1 && G.mnfight == core.TurnManager.MENU) {
            if (--danceTimer <= 0) {
                randomizePose();
                danceTimer = Math.max(1, dancewait);
            }
        }

        // Yellow gun: spawn a player bullet from the soul each time it fires. The
        // chest heart drawn on the body is NOT a target (it's decoration) — bullets
        // only hit the shootable attack pieces (and the popped-out HeartCore). HP
        // damage to Mettaton comes from FIGHT.
        if (soul != null && soul.mode == SoulMode.YELLOW && soul.firedThisFrame) {
            manager.add(new PlayerBullet(manager, soul.x, soul.y, onScore));
        }

        // Advance the death fade here (not in render) so it completes even headless.
        if (fadewhite) {
            whiteval += 0.2;
            if (whiteval >= 44) {
                fadeComplete = true;
            }
        }
    }

    @Override
    public void render(Graphics2D g) {
        double x = BODY_X;
        double y = bodyY;
        double sw = Math.sin(siner / 3.5);
        double cw = Math.cos(siner / 3.5);

        if (!noarm) {
            // GML: arms anchor at y-legh+80 (shoulder level); the chain-arm sprites
            // hang down from there to the hands out at the sides.
            double armY = y - legh + 80 + cw * 2;
            draw(g, ARM_SPRITE[arml], x + 36 + sw, armY, SCALE, SCALE);
            draw(g, ARM_SPRITE[armr], x + 110 + sw, armY, -SCALE, SCALE);
        }
        draw(g, LEG_SPRITE[legr], x + 90 + LEG_XOFF[legr], y + 120 + LEG_YOFF[legr] - legh, SCALE, SCALE);
        draw(g, LEG_SPRITE[legl], x + 90 - LEG_XOFF[legl] - 32, y + 120 + LEG_YOFF[legl] - legh, -SCALE, SCALE);

        draw(g, "spr_mettb_upperbody_0", x + 72 + sw, y - legh + 134 + cw * 2, SCALE, SCALE);
        if (!heartdead) {
            draw(g, "spr_mettb_upperbodyheart_0", x + 72 + 66 + sw, y - legh + 134 + 108 + cw * 2, SCALE, SCALE);
        }
        drawFace(g, x, y, cw);

        if (fadewhite) {
            renderFadeWhite(g);
        }
    }

    private void drawFace(Graphics2D g, double x, double y, double cw) {
        double fx = x + 68;
        double fy = y + 40 - legh + cw * 3;
        String sprite;
        int frame;
        if (endface) {
            sprite = "spr_mettface_general_";
            frame = G.faceemotion;
        } else if (hurt == 1) {
            sprite = "spr_mettface_hurt_";
            frame = hurtface;
        } else if (hurt == 0 && !faceSet) {
            sprite = "spr_mettface1_";
            frame = faceno;
        } else {
            sprite = "spr_mettface_defeated_";
            frame = G.faceemotion;
        }
        draw(g, sprite + frame, fx, fy, SCALE, SCALE);
    }

    private void renderFadeWhite(Graphics2D g) {
        Composite oc = g.getComposite();
        if (whiteval <= 10) {
            g.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, (float) Math.min(1.0, whiteval / 10.0)));
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, core.Game.WIDTH, core.Game.HEIGHT);
        } else {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, core.Game.WIDTH, core.Game.HEIGHT);
            g.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, (float) Math.min(1.0, -1 + whiteval / 10.0)));
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, core.Game.WIDTH, core.Game.HEIGHT);
        }
        g.setComposite(oc);
    }

    /** GML draw_sprite_ext: map the sprite origin to (x,y), 2× (negative xscale mirrors). */
    private void draw(Graphics2D g, String sprite, double x, double y, double xscale, double yscale) {
        BufferedImage img = Assets.sprite(sprite);
        if (img == null) {
            return;
        }
        int[] o = origin(sprite);
        Composite oc = null;
        if (myalpha < 0.99f) {
            oc = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, myalpha)));
        }
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.scale(xscale, yscale);
        g.drawImage(img, -o[0], -o[1], null);
        g.setTransform(old);
        if (oc != null) {
            g.setComposite(oc);
        }
    }

    /** Look up a sprite's GML origin (strips the trailing _frame index). */
    private static int[] origin(String sprite) {
        int i = sprite.lastIndexOf('_');
        String base = sprite;
        if (i > 0 && sprite.substring(i + 1).chars().allMatch(Character::isDigit)) {
            base = sprite.substring(0, i);
        }
        return ORIGIN.getOrDefault(base, new int[] { 0, 0 });
    }
}
