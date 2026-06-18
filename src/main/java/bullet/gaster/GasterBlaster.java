package bullet.gaster;

import battle.KarmaTicker;
import battle.Soul;
import bullet.Bullet;
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
 * A Gaster Blaster (GML: {@code obj_gasterblaster}). Eases in toward
 * ({@code idealx}, {@code idealy}) while rotating to {@code idealrot}, waits
 * {@code pause} frames, opens its jaw (charge frames 0→4), then fires: the skull
 * recoils away while a thick beam shoots from its mouth along the facing
 * direction, holds for {@code terminal} frames, and fades out. The beam line is
 * the hitbox.
 *
 * <p>Angles use the GML screen convention (degrees, 90° = up). The skull faces
 * {@code idealrot}; the beam travels along {@code idealrot − 90} and the recoil
 * along {@code idealrot + 90}, exactly as in the GML.
 *
 * // GML: obj_gasterblaster (spr_gasterblaster frames 0-5)
 */
public final class GasterBlaster extends Bullet {

    private static final double SPRITE_W = 43;
    private static final double SPRITE_H = 57;
    private static final double ORIGIN_X = 21;
    private static final double ORIGIN_Y = 28;

    private final KarmaTicker karma;

    public double idealx = 200;
    public double idealy = 200;
    public double idealrot = 90;
    public double xscale = 1;
    public double yscale = 1;
    /** Frames between arriving and opening the jaw. */
    public int pause = 8;
    /** Frames the beam holds at full width before fading. */
    public int terminal = 10;

    private int con = 1;
    private int wait;
    private int frame;          // sprite frame 0..5
    private int btimer;
    private double beamWidth;   // GML: bt
    private double fade = 1;
    private double speed;       // recoil speed
    private double bbsiner;

    public GasterBlaster(EntityManager manager, Soul soul, KarmaTicker karma) {
        super(manager, soul);
        this.karma = karma;
        this.dmg = 1;
        this.depth = -50; // above bones, below the soul
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        switch (con) {
            case 1 -> flyIn();
            case 2 -> { // jaw opening
                frame++;
                if (frame >= 4) {
                    frame = 4;
                    con = 3;
                    btimer = 0;
                    util.Audio.play("/audio/snd_laz.wav");  // GML: the blaster fires
                }
            }
            case 3 -> fire();
            default -> { }
        }
    }

    private void flyIn() {
        x += Math.floor((idealx - x) / 3);
        y += Math.floor((idealy - y) / 3);
        x += Math.signum(idealx - x);
        y += Math.signum(idealy - y);
        if (Math.abs(x - idealx) < 3) {
            x = idealx;
        }
        if (Math.abs(y - idealy) < 3) {
            y = idealy;
        }
        imageAngle += Math.floor((idealrot - imageAngle) / 3);
        imageAngle += Math.signum(idealrot - imageAngle);
        if (Math.abs(imageAngle - idealrot) < 3) {
            imageAngle = idealrot;
        }
        if (x == idealx && y == idealy && imageAngle == idealrot) {
            wait++;
            if (wait >= pause) {
                con = 2;
                frame = 0;
            }
        }
    }

    private void fire() {
        frame = (btimer % 2 == 0) ? 5 : 4;   // jaw rattles between frames 4/5
        double recoilDir = idealrot + 90;
        btimer++;
        if (btimer < 5) {
            speed++;
            beamWidth += Math.floor(35 * xscale / 4);
        } else {
            speed += 4;
        }
        x += GMLHelper.lengthdir_x(speed, recoilDir);
        y += GMLHelper.lengthdir_y(speed, recoilDir);
        if (btimer > 5 + terminal) {
            beamWidth *= 0.8;
            fade -= 0.1;
            if (beamWidth <= 2) {
                manager.destroy(this);
                return;
            }
        }
        bbsiner++;
        if (fade >= 0.8 && G.inv <= 0 && beamHitsSoul()) {
            soul.hurt(dmg);
            karma.addKarma(10);   // GML obj_gasterblaster: innate_karma = 10
        }
    }

    /** Distance from the soul to the beam ray < half the beam width. */
    private boolean beamHitsSoul() {
        double dir = imageAngle - 90;
        double sx = x + GMLHelper.lengthdir_x(70 * xscale / 2, dir);
        double sy = y + GMLHelper.lengthdir_y(70 * xscale / 2, dir);
        double ex = x + GMLHelper.lengthdir_x(1000, dir);
        double ey = y + GMLHelper.lengthdir_y(1000, dir);
        double vx = ex - sx;
        double vy = ey - sy;
        double t = ((soul.x - sx) * vx + (soul.y - sy) * vy) / (vx * vx + vy * vy);
        t = Math.max(0, Math.min(1, t));
        double px = sx + vx * t;
        double py = sy + vy * t;
        double dist = Math.hypot(soul.x - px, soul.y - py);
        return dist < beamWidth / 2 + Soul.HALF;
    }

    @Override
    protected double boxTop() {
        return y;
    }

    @Override
    protected double boxBottom() {
        return y;
    }

    @Override
    public void render(Graphics2D g) {
        if (con == 3 && beamWidth > 0) {
            double dir = imageAngle - 90;
            double sx = x + GMLHelper.lengthdir_x(70 * xscale / 2, dir);
            double sy = y + GMLHelper.lengthdir_y(70 * xscale / 2, dir);
            double ex = x + GMLHelper.lengthdir_x(1000, dir);
            double ey = y + GMLHelper.lengthdir_y(1000, dir);
            double wobble = Math.sin(bbsiner / 1.5) * beamWidth / 4;
            Stroke old = g.getStroke();
            java.awt.Composite oldComp = g.getComposite();
            if (fade < 1) {
                g.setComposite(java.awt.AlphaComposite.getInstance(
                        java.awt.AlphaComposite.SRC_OVER, (float) Math.max(0, fade)));
            }
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke((float) Math.max(1, beamWidth + wobble),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine((int) sx, (int) sy, (int) ex, (int) ey);
            g.setStroke(old);
            g.setComposite(oldComp);
        }
        BufferedImage img = Assets.sprite("spr_gasterblaster_" + frame);
        if (img == null) {
            img = Assets.sprite("spr_gasterblaster_0");
        }
        if (img != null) {
            AffineTransform tx = g.getTransform();
            g.translate(x, y);
            // GML rotates counter-clockwise in screen space.
            g.rotate(-Math.toRadians(imageAngle));
            g.drawImage(img, (int) (-ORIGIN_X * xscale), (int) (-ORIGIN_Y * yscale),
                    (int) (SPRITE_W * xscale), (int) (SPRITE_H * yscale), null);
            g.setTransform(tx);
        } else {
            g.setColor(Color.WHITE);
            g.fillOval((int) (x - 12), (int) (y - 12), 24, 24);
        }
    }
}
