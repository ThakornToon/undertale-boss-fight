package bullet.asriel;

import battle.Soul;
import boss.AsrielBody;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import util.Assets;
import util.GMLHelper;

/**
 * One of Chaos Saber's two swords (GML: {@code obj_asriel_swordarm}). Both arms rest RAISED
 * holding a sword UP in a tight V. When an arm slashes, Asriel's body leans the OPPOSITE way
 * (anticipation) and that arm's blade ({@code spr_asriel_swordextend}) <b>plunges down into
 * the box</b> while a white {@code spr_asriel_swordsmear} crescent flashes over HALF the
 * screen on the slashing side — standing in that half is the hit. The other arm stays raised.
 *
 * <p>The crescent matches the GML draw exactly: drawn from its top-left origin at
 * {@code (x ∓ 40, y - 168)} with {@code xscale = side·2}, {@code yscale = 2.5} — a half-screen
 * "D" hanging from the top, so Asriel's body stays visible (it does NOT balloon over him).
 *
 * <p>The {@code finale} slash holds both arms in the V with an electric flourish, opens the
 * box out, then plunges both blades straight down and shatters them into a rain of
 * {@link SwordTwinkle} diamonds.
 *
 * // GML: obj_asriel_swordarm (+ obj_asriel_swordmaster)
 */
public final class SwordArm extends AttackPattern {

    private final Soul soul;
    private final AsrielBody body;
    private final int side;          // +1 right arm, -1 left arm
    private final int dmgVal;

    private int f = -1;              // slash frame; -1 = idle (sword held raised)
    private boolean finale;
    private float smear;             // crescent alpha (1 → 0)
    private double myLean;           // body lean this arm asks for (opposite its slash side)


    // GML obj_asriel_swordarm timing (30 FPS, 1:1 with the reference): the arm rests raised,
    // then on a strike the blade PLUNGES into the box (crescent flash) and eases back up. The
    // finale first HOLDS the raised V with sparks (the box opens) before the plunge + shatter.
    private static final int WIND = 6;             // brief coil while the arm stays raised
    private static final int FIN_HOLD = 12;         // finale: hold the sparking V before plunging
    private static final int PLUNGE = 5;            // blade drives down into the box (crescent on)
    private static final int HOLD = 4;              // blade buried in the box (hazard continues)
    private static final int RECOVER = 9;           // ease back up to the raised V
    private static final double REST_DEG = 22;      // raised-V angle (swords up, slightly out)
    private static final double PLUNGE_DEPTH = 22;  // how far the blade drives down on a strike

    public SwordArm(EntityManager manager, Soul soul, AsrielBody body, int side, int dmg) {
        super(manager);
        this.soul = soul;
        this.body = body;
        this.side = side;
        this.dmgVal = dmg;
        // In FRONT of the box (box depth 1000) so the crescent tints the hazard region and the
        // blade plunges visibly INTO the box — but behind the heart / stars / bubble (lower
        // depth), which draw on top. (Was 1090 = behind the box, which hid the blade.)
        this.depth = 999;
    }

    private double handX() {
        return body.x + body.leanX() + side * 36;   // GML arm at body.x ± 36, follows the lean
    }

    private double handY() {
        return body.y + 35;
    }

    private double boxCenterX() {
        return (G.idealborder[0] + G.idealborder[1]) / 2.0;
    }

    /** Distance from box centre to a 1/3 line — the finale's per-side crescent boundary. */
    private double boxThird() {
        return (G.idealborder[1] - G.idealborder[0]) / 6.0;
    }

    public boolean busy() {
        return f >= 0;
    }

    /** The body lean this arm wants right now (0 when idle) — the GML opposite-side wind-up. */
    public double lean() {
        return myLean;
    }

    public void slash(boolean fin) {
        f = 0;
        finale = fin;
        util.Audio.play("/audio/mus_sfx_a_pullback.ogg", 0.9);
    }

    /** First frame of the downward plunge (after the wind / finale hold). */
    private int strikeStart() {
        return finale ? FIN_HOLD : WIND;
    }

    /** Last frame the blade is buried (and the half-box hazard is live). */
    private int strikeEnd() {
        return strikeStart() + PLUNGE + HOLD;
    }

    private int doneFrame() {
        return strikeStart() + PLUNGE + HOLD + RECOVER;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        // GML: the body leans OPPOSITE the slashing arm through the strike — a BIG lean (the
        // reference tilts Asriel right over into the swing), not a timid one.
        double leanTarget = (f >= 1 && f <= strikeEnd()) ? -side * 24 : 0;
        myLean = GMLHelper.approach(myLean, leanTarget, 3.0);
        if (f < 0) {
            if (smear > 0) {
                smear -= 0.10f;
            }
            return;     // idle, sword held overhead in the V
        }
        f++;
        int strikeStart = strikeStart();
        if (f == strikeStart) {
            util.Audio.play("/audio/mus_sfx_cinematiccut.ogg", 0.6);
        }
        // GML smear=5 → a SHORT crescent flash as the blade plunges (regular AND finale).
        boolean crescentOn = f >= strikeStart && f <= strikeStart + PLUNGE;
        if (crescentOn) {
            smear = Math.min(1f, smear + 0.40f);
        } else if (smear > 0) {
            smear -= 0.16f;
        }
        // Standing in the crescent's covered band is the hit: a regular slash covers HALF the
        // box (boundary at centre); the finale's double slash covers only a THIRD on this side
        // (boundary inset toward the edge), so the middle third is safe.
        if (f >= strikeStart && f <= strikeEnd() && G.inv <= 0) {
            double bound = boxCenterX() + (finale ? side * boxThird() : 0);
            boolean inWhite = (side > 0) ? soul.x >= bound : soul.x <= bound;
            if (inWhite) {
                soul.hurt(dmgVal);
            }
        }
        if (f > doneFrame()) {
            f = -1;
        }
    }

    /** 0 = blade up (arm raised), 1 = blade fully plunged into the box. Eased smoothly. */
    private double plungeAmt() {
        if (f < 0) {
            return 0;
        }
        int s = f - strikeStart();
        if (s < 0) {
            return 0;                                       // still holding the raised V
        }
        if (s <= PLUNGE) {
            return smoothstep(s / (double) PLUNGE);         // drive down into the box
        }
        if (s <= PLUNGE + HOLD) {
            return 1;                                       // hold the plunge
        }
        return 1 - smoothstep((s - PLUNGE - HOLD) / (double) RECOVER);   // ease back up to the V
    }

    /** Hermite smoothstep — slow in / slow out, so the motion never jumps a frame. */
    private static double smoothstep(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    @Override
    public void render(Graphics2D g) {
        // Contain the slash visuals to the play area: clip off anything below the box bottom so
        // the plunging blade / crescent don't spill into the HUD ("เลยพื้นที่"). clipRect
        // intersects the existing clip, so any outer clip is preserved.
        java.awt.Shape oldClip = g.getClip();
        g.clipRect(0, 0, core.Game.WIDTH, (int) Math.ceil(G.idealborder[3]));

        // The crescent. Two parts so it ALWAYS fills the box hazard band exactly ("พอดีกับกรอบ"):
        //   (1) the big "D" arc as flair ABOVE the box, and
        //   (2) a clean filled rectangle for the box's hazard band — a single slash covers the
        //       slash-side HALF (inner edge at box centre), the finale covers a THIRD per side
        //       (inner edge at the 1/3 line). The sprite's curved bottom can't cleanly fill the
        //       box on its own, so the rect guarantees the half/third is solid top-to-bottom.
        if (smear > 0) {
            float a = Math.min(1f, Math.max(0f, smear));
            double inner = boxCenterX() + (finale ? side * boxThird() : 0);
            double boxTop = G.idealborder[2];
            double boxBottom = G.idealborder[3];
            BufferedImage sm = Assets.sprite("spr_asriel_swordsmear_0");
            if (sm != null) {
                // (1) The arc, clipped to ABOVE the box so it never double-covers it; its flat
                //     edge aligns with the rect's inner edge so the two read as one shape.
                g.setClip(oldClip);
                g.clipRect(0, 0, core.Game.WIDTH, (int) Math.floor(boxTop));
                Composite oc = g.getComposite();
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
                AffineTransform old = g.getTransform();
                g.translate(inner - side * 4, handY() - 168);
                g.scale(side * 2.0, 2.5);
                g.drawImage(sm, 0, 0, null);
                g.setTransform(old);
                g.setComposite(oc);
                // restore the play-area clip for the rest of render()
                g.setClip(oldClip);
                g.clipRect(0, 0, core.Game.WIDTH, (int) Math.ceil(boxBottom));
            }
            // (2) The clean box-band fill.
            double rx0 = (side > 0) ? inner : G.idealborder[0];
            double rx1 = (side > 0) ? G.idealborder[1] : inner;
            Composite oc = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            g.setColor(java.awt.Color.WHITE);
            g.fillRect((int) Math.round(rx0), (int) Math.round(boxTop),
                    (int) Math.round(rx1 - rx0), (int) Math.round(boxBottom - boxTop));
            g.setComposite(oc);
        }
        double plunge = plungeAmt();
        if (busy() && plunge > 0.01) {
            // Slashing arm: the long blade plunges DOWN into the box (GML swaps the arm sprite
            // to spr_asriel_swordextend). Regular slashes rotate the blade outward as it drives
            // in; the finale plunges straight down with the shatter sprite.
            // Always the LONG blade — the finale's _shatter sprite is half-height and never
            // reached the box, so the finale blades "disappeared" (user feedback).
            BufferedImage blade = Assets.sprite("spr_asriel_swordextend_0");
            if (blade != null) {
                Composite oc = g.getComposite();
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        (float) Math.min(1, plunge * 2.5)));
                double bx = handX();
                double by = handY() + plunge * PLUNGE_DEPTH;
                // Plunge straight down from the hand so the blade drives into the box on the
                // SLASH side (the hazard / crescent side) — not swept across to the other half.
                draw(g, blade, bx, by, side * 2.0, 2.5, 0, 8, 10);
                g.setComposite(oc);
            }
        } else {
            // Idle / pre-strike: the arm rests RAISED, holding its sword UP in a tight V.
            BufferedImage arm = Assets.sprite("spr_asriel_swordarm_0");
            if (arm != null) {
                draw(g, arm, handX(), handY(), side * 2.0, 2.0, side * REST_DEG, 17, 75);
            }
            // Finale flourish: flicker the electric power-arm while holding the V before plunging.
            if (finale && f >= 0 && f < FIN_HOLD && (f % 2 == 0)) {
                BufferedImage pw = Assets.sprite("spr_asriel_swordarm_power_0");
                if (pw != null) {
                    draw(g, pw, handX(), handY(), side * 2.0, 2.0, side * REST_DEG, 17, 75);
                }
            }
        }
        g.setClip(oldClip);
    }

    /** draw_sprite_ext: origin → (px,py), scaled (negative x mirrors), rotated CW by deg. */
    private void draw(Graphics2D g, BufferedImage img, double px, double py,
                      double xscale, double yscale, double deg, int ox, int oy) {
        AffineTransform old = g.getTransform();
        g.translate(px, py);
        g.rotate(Math.toRadians(deg));
        g.scale(xscale, yscale);
        g.drawImage(img, -ox, -oy, null);
        g.setTransform(old);
    }
}
