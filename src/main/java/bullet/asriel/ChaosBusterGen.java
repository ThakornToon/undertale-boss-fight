package bullet.asriel;

import battle.Soul;
import boss.AsrielBody;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import util.Assets;
import util.GMLHelper;

/**
 * CHAOS BUSTER / CHAOS BLASTER (GML: {@code obj_gunarm_firepattern}). Asriel's arm
 * becomes a cannon that <b>homes on the heart</b> and fires nine waves of {@link GunBolt}
 * spreads, each telegraphed by flashing aim lines, then spins up a charge meter, unhinges,
 * and fires a wide <b>rainbow beam</b>. The hard "CHAOS BLASTER" (turn 11) randomises the
 * wave types and ends on a {@link RegStar} starburst.
 *
 * <p>Geometry mirrors the GML exactly: the gun spawns at {@code (body.x+70, body.y+15)} —
 * the segmented-barrel tip, up and to Asriel's right — and that point is the sprite origin
 * it pivots around. {@code image_angle = point_direction(gun, heart) + 90}, so the whole
 * cannon swings to track the heart; bolts and the beam leave the paw/muzzle end
 * ({@code lengthdir(95.., image_angle-90)}) and fly straight at the heart. The finale spins
 * the gun (decelerating), SNAPS it back onto the heart, swaps to the unhinge sprite, holds,
 * then erupts the beam toward the heart's locked position.
 *
 * <p>The {@code ctimer} timeline runs at half-speed early then full speed (per the GML),
 * so the generator owns the (long) turn and ends it itself ({@code turntimer = 9999 → 0}).
 *
 * // GML: obj_gunarm_firepattern (+ obj_gunarm_bolt)
 */
public final class ChaosBusterGen extends AttackPattern {

    private final Soul soul;
    private final AsrielBody body;
    private final boolean hard;
    private final int dmgVal;

    private double ctimer;
    private double prevC;
    private boolean home = true;

    // GML image_angle. The barrel tip is the pivot; image_angle-90 is the firing direction
    // (straight at the heart). Set from the heart each frame while `home`, then frozen.
    private double imageAngle = 90;
    private double aaspeed;              // free-spin speed during the charge rev
    private boolean spinning;

    // Active wave.
    private boolean firing;
    private int type;
    private int maxfire;
    private int fire;
    private int fireTimer;
    private int lTimer;                  // telegraph-line countdown

    private boolean meter;
    private double metercounter;         // charge-gauge fill (driven from update())
    private boolean unhinged;            // gun has popped open (spr_asriel_gunarm_unhinge)
    private int unhingeFrame;
    private boolean blast;
    private double bt;                   // beam width
    private int btimer;
    private double recoilX;
    private double recoilY;
    private double jr;                   // starburst base angle
    private int flash;

    // GML wave table: {ctimer, type(0=random), maxfire, gap, lTimer}.
    private static final int[][] WAVES_N = {
        {1, 1, 6, 20, 20}, {28, 2, 6, 10, 10}, {54, 1, 5, 8, 8}, {78, 2, 5, 8, 8},
        {100, 1, 4, 8, 8}, {122, 2, 4, 8, 8}, {140, 1, 4, 6, 6}, {156, 2, 4, 6, 6},
        {170, 1, 8, 6, 6},
    };
    private static final int[][] WAVES_H = {
        {1, 1, 6, 20, 20}, {25, 0, 6, 10, 10}, {50, 0, 5, 8, 8}, {75, 2, 4, 7, 7},
        {95, 1, 4, 7, 7}, {115, 0, 4, 7, 7}, {132, 2, 4, 6, 6}, {156, 2, 4, 6, 6},
        {170, 1, 10, 6, 6},
    };

    // GML lengthdir distances from the barrel tip, along the firing direction.
    private static final double BOLT_OUT = 95;     // bolt spawn (event_user 1)
    private static final double TELE_OUT = 120;    // telegraph line start (Draw)
    private static final double BEAM_OUT = 115;    // beam start (Draw, blast)

    private final int spinStart;
    private final int meterT;
    private final int blastT;
    private final int endT;

    public ChaosBusterGen(EntityManager manager, Soul soul, AsrielBody body,
                          boolean hard, int dmg) {
        super(manager);
        this.soul = soul;
        this.body = body;
        this.hard = hard;
        this.dmgVal = dmg;
        this.depth = -7;
        this.spinStart = hard ? 215 : 205;
        this.meterT = hard ? 200 : 190;
        this.blastT = hard ? 270 : 275;
        this.endT = hard ? 310 : 315;
        this.jr = GMLHelper.random(360);
    }

    // GML create: gen = instance_create(body.x + 70, body.y + 15, obj_gunarm_firepattern).
    private double gunX() {
        return body.x + 70 + recoilX;
    }

    private double gunY() {
        return body.y + 15 + recoilY;
    }

    /** Firing direction (image_angle - 90): straight at the heart while homing, then frozen. */
    private double fireDir() {
        return imageAngle - 90;
    }

    private double aimAtHeart() {
        return GMLHelper.point_direction(gunX(), gunY(), soul.x, soul.y) + 90;
    }

    private static boolean crossed(double prev, double cur, double t) {
        return prev < t && cur >= t;
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        if (home) {
            imageAngle = aimAtHeart();       // GML: track the heart while homing
        }
        prevC = ctimer;
        ctimer += 0.5;
        double knee = hard ? 19.5 : 27.5;
        if (ctimer >= knee) {
            ctimer += 0.5;          // GML: timeline speeds up after the first wave
        }

        // Start scheduled waves.
        int[][] waves = hard ? WAVES_H : WAVES_N;
        for (int[] w : waves) {
            if (crossed(prevC, ctimer, w[0])) {
                startWave(w);
            }
        }
        // Fire the active wave's bolts. GML alarm[5]: after the wave's initial `gap` delay the
        // bolts rattle out every 2 frames as a tight VOLLEY, then nothing until the next wave.
        if (firing && fire < maxfire) {
            if (--fireTimer <= 0) {
                fireWave();
                fire++;
                fireTimer = 2;
            }
        }

        // Charge gauge.
        if (crossed(prevC, ctimer, meterT)) {
            meter = true;
            util.Audio.play("/audio/mus_sfx_segapower.ogg", 0.9);
        }
        if (meter && !blast) {
            metercounter += 1;
        }

        // Charge rev: the cannon free-spins around the barrel tip, decelerating, then SNAPS
        // back onto the heart and LOCKS — the telegraphed "about to fire" halt.
        if (crossed(prevC, ctimer, spinStart)) {
            home = false;
            spinning = true;
            aaspeed = hard ? 90 : 45;
        }
        if (spinning) {
            imageAngle += aaspeed;
            aaspeed -= hard ? 6 : 3;
            if (aaspeed <= 0) {
                aaspeed = 0;
                spinning = false;
                ctimer = 255;                // GML: jump straight to the lock beat
                imageAngle = aimAtHeart();   // SNAP onto the heart and freeze (home stays false)
            }
        }
        // Pop the gun open and hold, locked on the heart, before the beam.
        if (crossed(prevC, ctimer, 257)) {
            unhinged = true;
        }
        if (unhinged && !blast && unhingeFrame < 5) {
            unhingeFrame++;
        }

        // Beam.
        if (crossed(prevC, ctimer, blastT)) {
            blast = true;
            bt = 70;        // GML: the charged BLAST beam is bt=70
            btimer = 0;
            // NB: do NOT re-aim here — the gun locked onto the heart back at the spin-end
            // (~20 frames ago) and must stay frozen, so the beam fires where it locked even
            // if the player has since moved.
            util.Audio.play("/audio/mus_sfx_rainbowbeam_1.ogg");
        }
        if (hard && blast && ctimer >= 272 && ctimer <= 284 && ((int) ctimer % 2 == 0)) {
            starburst();
        }
        if (blast) {
            updateBeam();
        }
        if (ctimer >= endT) {
            G.turntimer = 0;        // end the turn
            manager.destroy(this);
        }
    }

    private void startWave(int[] w) {
        type = w[1] == 0 ? GMLHelper.choose(new int[] {1, 2}) : w[1];
        maxfire = w[2];
        lTimer = w[4];
        fire = 0;
        fireTimer = w[3];       // the initial delay before this wave's volley rattles out
        firing = true;
        home = true;
    }

    /** GML event_user(1): fire this wave's fan of bolts from the paw/muzzle. */
    private void fireWave() {
        util.Audio.play("/audio/mus_sfx_a_bullet.ogg", 0.7);
        double dir = fireDir();
        double mx = gunX() + GMLHelper.lengthdir_x(BOLT_OUT, dir);
        double my = gunY() + GMLHelper.lengthdir_y(BOLT_OUT, dir);
        if (type == 1) {
            for (int i = 0; i < 3; i++) {
                manager.add(new GunBolt(manager, soul, mx, my, dir, dir - 20 + 20 * i, 20, dmgVal));
            }
        } else {
            for (int i = 0; i < 4; i++) {
                manager.add(new GunBolt(manager, soul, mx, my, dir, dir - 30 + 20 * i, 20, dmgVal));
            }
        }
    }

    /** GML Chaos Blaster finale: a ring of accelerating stars from the muzzle. */
    private void starburst() {
        jr += 8;
        double dir = fireDir();
        double mx = gunX() + GMLHelper.lengthdir_x(BEAM_OUT, dir);
        double my = gunY() + GMLHelper.lengthdir_y(BEAM_OUT, dir);
        for (int i = 0; i < 24; i++) {
            RegStar rs = new RegStar(manager, soul, jr + 15 * i, 8, -0.1, dmgVal);
            rs.x = mx;
            rs.y = my;
            manager.add(rs);
        }
    }

    private void updateBeam() {
        if (lTimer > 0) {
            lTimer = 0;
        }
        // Screen-shake recoil while the beam is wide (GML jitters x/y).
        if (bt > 4) {
            recoilX = GMLHelper.random(6) - GMLHelper.random(6);
            recoilY = -16 - GMLHelper.random(15);
        } else {
            recoilX = 0;
            recoilY = 0;
        }
        // Beam damage: heart within bt/2 of the locked ray.
        double dir = fireDir();
        double mx = gunX() + GMLHelper.lengthdir_x(BEAM_OUT, dir);
        double my = gunY() + GMLHelper.lengthdir_y(BEAM_OUT, dir);
        double bx = GMLHelper.lengthdir_x(1, dir);
        double by = GMLHelper.lengthdir_y(1, dir);
        double t = (soul.x - mx) * bx + (soul.y - my) * by;
        if (t < 0) {
            t = 0;
        }
        double cx = mx + bx * t;
        double cy = my + by * t;
        double dist = Math.hypot(soul.x - cx, soul.y - cy);
        if (G.inv <= 0 && dist < bt / 2) {
            soul.hurt(dmgVal);
        }
        // Unhinge muzzle flicker (GML toggles image_index 4/5 while firing).
        unhingeFrame = unhingeFrame == 5 ? 4 : 5;
        metercounter -= 1.25;       // GML: the charge gauge drains as the beam fires
        btimer++;
        if (btimer > 15) {
            bt -= 3;
            if (bt < 3) {
                bt = 0;
                blast = false;
                meter = false;
            }
        }
    }

    @Override
    public void render(Graphics2D g) {
        if (++flash > 2) {
            flash = 0;
        }
        if (blast) {
            drawBeam(g);        // beam behind the gun so the muzzle sits on top
        }
        drawGun(g);
        if (meter) {
            drawMeter(g);
        }
        if (lTimer > 0) {
            drawTelegraph(g);
            lTimer--;
        }
    }

    private void drawGun(Graphics2D g) {
        String name = unhinged ? "spr_asriel_gunarm_unhinge_" + unhingeFrame : "spr_asriel_gunarm_0";
        int ox = unhinged ? 22 : 17;        // sprite origins (gunarm 17,20 · unhinge 22,24)
        int oy = unhinged ? 24 : 20;
        BufferedImage img = Assets.sprite(name);
        if (img == null) {
            return;
        }
        // GML draw_sprite_ext at the barrel tip (gun x,y), rotated by image_angle (CCW), ×2.
        AffineTransform old = g.getTransform();
        g.translate(gunX(), gunY());
        g.rotate(-Math.toRadians(imageAngle));
        g.scale(2, 2);
        g.drawImage(img, -ox, -oy, null);
        g.setTransform(old);
    }

    private void drawTelegraph(Graphics2D g) {
        // GML draw: bold flashing aim lines (white → orange → yellow) at FULL opacity, 600px,
        // so the player clearly reads where each volley is about to land.
        Color base = flash == 0 ? Color.WHITE
                : (flash == 1 ? new Color(255, 161, 64) : new Color(255, 255, 0));
        g.setColor(base);
        Stroke os = g.getStroke();
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double dir = fireDir();
        double mx = gunX() + GMLHelper.lengthdir_x(TELE_OUT, dir);
        double my = gunY() + GMLHelper.lengthdir_y(TELE_OUT, dir);
        // GML offsets: type1 image_angle-104/-90/-77 → dir-14/0/+13; type2 -110/-96/-84/-70.
        double[] offs = type == 1 ? new double[] {-14, 0, 13} : new double[] {-20, -6, 6, 20};
        for (double o : offs) {
            double d = dir + o;
            g.drawLine((int) mx, (int) my,
                    (int) (mx + GMLHelper.lengthdir_x(600, d)), (int) (my + GMLHelper.lengthdir_y(600, d)));
        }
        g.setStroke(os);
    }

    /**
     * GML Draw: the segmented charge gauge is its own sprite ({@code spr_asriel_gunarm_meter},
     * one rung per sub-image) drawn at the gun's {@code (x,y)} with the gun's {@code image_angle}
     * and hue-cycled per rung. Sharing the gun's origin/rotation keeps every rung square on the
     * barrel — no hand-placed offsets to drift out of line.
     */
    private void drawMeter(Graphics2D g) {
        int filled = Math.min(7, (int) metercounter);
        AffineTransform old = g.getTransform();
        g.translate(gunX(), gunY());
        g.rotate(-Math.toRadians(imageAngle));
        g.scale(2, 2);
        for (int i = 0; i < filled; i++) {
            BufferedImage seg = Assets.sprite("spr_asriel_gunarm_meter_" + i);
            if (seg == null) {
                continue;
            }
            // GML make_color_hsv(metercounter*12 - i*24, 180, 255): a rolling rainbow up the gauge.
            float hue = (float) ((((metercounter * 12 - i * 24) % 256) + 256) % 256 / 255.0);
            g.drawImage(tint(seg, Color.getHSBColor(hue, 0.7f, 1f)), -22, -24, null);   // meter origin 22,24
        }
        g.setTransform(old);
    }

    /** Recolour a white sprite to {@code c}, preserving its alpha (for the gauge rungs). */
    private static BufferedImage tint(BufferedImage src, Color c) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int rgb = c.getRGB() & 0x00FFFFFF;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = src.getRGB(x, y) >>> 24;
                if (a != 0) {
                    out.setRGB(x, y, (a << 24) | rgb);
                }
            }
        }
        return out;
    }

    private void drawBeam(Graphics2D g) {
        double dir = fireDir();
        double mx = gunX() + GMLHelper.lengthdir_x(BEAM_OUT, dir);
        double my = gunY() + GMLHelper.lengthdir_y(BEAM_OUT, dir);
        double ex = mx + GMLHelper.lengthdir_x(600, dir);
        double ey = my + GMLHelper.lengthdir_y(600, dir);
        Stroke os = g.getStroke();
        // GML mus_sfx_a_rainbowbeam: a solid soft rainbow beam that cycles hue as it fires —
        // a saturated outer band with a lighter (pastel, NOT pure white) core.
        float hue = (float) ((btimer * 0.045) % 1.0);
        Color outer = Color.getHSBColor(hue, 0.9f, 1f);
        Color core = Color.getHSBColor(hue, 0.45f, 1f);
        g.setStroke(new BasicStroke((float) (bt / 1.4), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(outer.getRed(), outer.getGreen(), outer.getBlue(), 230));
        g.drawLine((int) mx, (int) my, (int) ex, (int) ey);
        g.setStroke(new BasicStroke((float) Math.max(3, bt / 2.6), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(core);
        g.drawLine((int) mx, (int) my, (int) ex, (int) ey);
        g.setStroke(os);
    }
}
