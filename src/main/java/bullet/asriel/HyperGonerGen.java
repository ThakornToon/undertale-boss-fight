package bullet.asriel;

import battle.Soul;
import boss.AsrielBody;
import bullet.AttackPattern;
import core.EntityManager;
import core.Game;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import util.Assets;
import util.GMLHelper;

/**
 * HYPER GONER (GML: {@code obj_hg_wholescreen} + {@code obj_hg_body}). The turn-13
 * transition climax: the combat box "opens up" to fill the whole screen, a giant skull
 * face fades in and laughs, then it <b>charges a vacuum</b> — the heart is dragged toward
 * screen-centre while a green ring grows out from the middle. You survive by resisting the
 * pull and staying outside the ring (you cannot die here — HP is clamped to 1). After the
 * screen whites out, the turn ends and Asriel transforms (Part B).
 *
 * <p>Drawn in front of the box/bullets but behind the heart, so the heart stays visible on
 * top of the black field and the closing white-out.
 *
 * // GML: obj_hg_wholescreen + obj_hg_body
 */
public final class HyperGonerGen extends AttackPattern {

    private static final int CENTER_X = 320;
    private static final int CENTER_Y = 240;
    // The skull is composed top-left at (SKULL_X, SKULL_Y), centred on the screen.
    private static final int SKULL_X = 168;
    private static final int SKULL_Y = 24;

    private enum Phase { FADE_IN, PAUSE, CONTRACT, WOBBLE, VACUUM, DONE }

    private final Soul soul;
    private final AsrielBody body;
    private final int dmgVal;

    private Phase phase = Phase.FADE_IN;
    private int t;
    private float alpha;
    private double facey;
    private double facescale;
    private double siner;
    private int cCounter;
    private int flash;

    // The expanding "box opens up" rectangle (starts at the combat box, grows off-screen).
    private double xx;
    private double yy;
    private double xx2;
    private double yy2;

    public HyperGonerGen(EntityManager manager, Soul soul, AsrielBody body, int dmg) {
        super(manager);
        this.soul = soul;
        this.body = body;
        this.dmgVal = dmg;
        this.depth = -50;            // front of box/bullets, behind the heart (-100)
        this.xx = G.idealborder[0];
        this.yy = G.idealborder[2];
        this.xx2 = G.idealborder[1];
        this.yy2 = G.idealborder[3];
        soul.ignoreBorder = true;    // the heart can roam the whole screen now
    }

    @Override
    public void update() {
        // The box opens up: the rectangle grows past the screen edges.
        if (xx > -100) {
            xx -= 10;
        }
        if (yy > -100) {
            yy -= 10;
        }
        if (xx2 < 800) {
            xx2 += 10;
        }
        if (yy2 < 800) {
            yy2 += 10;
        }
        // Asriel fades out as the skull takes over.
        if (phase != Phase.VACUUM && phase != Phase.DONE) {
            body.setAlpha((float) Math.max(0, 1 - alpha));
        }

        switch (phase) {
            case FADE_IN -> {
                alpha += 0.05f;
                if (alpha >= 1f) {
                    alpha = 1f;
                    phase = Phase.PAUSE;
                    t = 0;
                }
            }
            case PAUSE -> {
                if (++t >= 20) {
                    util.Audio.play("/audio/mus_sfx_hypergoner_laugh.ogg");
                    phase = Phase.CONTRACT;
                }
            }
            case CONTRACT -> {
                facey -= 3.5;
                facescale -= 0.2;
                if (facescale < -1) {
                    phase = Phase.WOBBLE;
                    t = 0;
                }
            }
            case WOBBLE -> {
                siner++;
                facey += Math.sin(siner / 1.5) * 8;
                facescale += Math.sin(siner / 1.5) * 0.2;
                if (++t >= 75) {
                    util.Audio.play("/audio/mus_sfx_hypergoner_charge.ogg");
                    phase = Phase.VACUUM;
                    cCounter = 0;
                }
            }
            case VACUUM -> vacuum();
            case DONE -> { }
        }

        // GML: clamp the heart to the screen.
        soul.x = GMLHelper.clamp(soul.x, 0, Game.WIDTH);
        soul.y = GMLHelper.clamp(soul.y, 0, Game.HEIGHT);
    }

    private void vacuum() {
        if (alpha > 0.14f) {
            alpha -= 0.02f;
        }
        // Drag the heart toward centre (faster past the half-way point).
        double pull = cCounter < 180 ? 1 : 2;
        double pd = GMLHelper.point_direction(soul.x, soul.y, CENTER_X, CENTER_Y);
        soul.x += GMLHelper.lengthdir_x(pull, pd);
        soul.y += GMLHelper.lengthdir_y(pull, pd);

        // The green ring grows from the centre; being inside it hurts (but can't kill).
        double rad = Math.max(20, (cCounter - 180) / 1.5);
        if (cCounter < 295) {
            double dist = Math.hypot(soul.x - CENTER_X, soul.y - CENTER_Y);
            if (G.inv <= 0 && dist < rad - 5) {
                soul.hurt(dmgVal);
                if (G.hp < 1) {
                    G.hp = 1;            // canon: Hyper Goner cannot kill you
                }
            }
        }
        cCounter++;
        if (cCounter > 300) {
            soul.hidden = true;          // the heart vanishes into the white-out
        }
        if (cCounter > 320) {
            complete();
        }
    }

    private void complete() {
        phase = Phase.DONE;
        soul.ignoreBorder = false;
        soul.hidden = false;
        body.setAlpha(1f);
        G.turntimer = 0;                 // end the turn → the controller starts the transform
        manager.destroy(this);
    }

    @Override
    public void render(Graphics2D g) {
        if (++flash > 2) {
            flash = 0;
        }
        // The opening box: a black field with a white 4px border, expanding to fullscreen.
        g.setColor(Color.BLACK);
        g.fillRect((int) xx, (int) yy, (int) (xx2 - xx), (int) (yy2 - yy));
        if (phase != Phase.VACUUM && phase != Phase.DONE) {
            g.setColor(Color.WHITE);
            Stroke os = g.getStroke();
            g.setStroke(new BasicStroke(4f));
            g.drawRect((int) xx, (int) yy, (int) (xx2 - xx), (int) (yy2 - yy));
            g.setStroke(os);
        }

        if (phase == Phase.VACUUM) {
            renderVacuum(g);
        } else {
            renderSkull(g);
        }
    }

    /** GML obj_hg_body Draw (con < 3): the four-part skull face fading in. */
    private void renderSkull(Graphics2D g) {
        Composite oc = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, alpha)));
        part(g, "spr_hg_leftovers_0", SKULL_X, SKULL_Y + facey / 6, 2, 2);
        part(g, "spr_hg_horns_0", SKULL_X, SKULL_Y - facey / 2, 2, 2);
        part(g, "spr_hg_mainface_0", SKULL_X + 88, SKULL_Y + 72 + facey, 2, 2 + facescale);
        part(g, "spr_hg_jaws_0", SKULL_X + 104, SKULL_Y + 248 - facey / 2, 2, 2);
        g.setComposite(oc);
    }

    /** GML obj_hg_body Draw (con == 3): the laughing skull + the vacuum + the white-out. */
    private void renderVacuum(Graphics2D g) {
        Composite oc = g.getComposite();
        // The laughing skull, fading toward a faint outline.
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, alpha)));
        BufferedImage laugh = Assets.sprite("spr_hg_laughing_0");
        if (laugh != null) {
            g.drawImage(laugh, SKULL_X, SKULL_Y, laugh.getWidth() * 2, laugh.getHeight() * 2, null);
        }
        // White vacuum "speed lines" radiating from the centre to the four edges.
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, 1 - alpha)));
        g.setColor(new Color(220, 220, 220));
        Stroke os = g.getStroke();
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < 5; i++) {
            line(g, Game.WIDTH * Math.random(), Game.HEIGHT);
            line(g, Game.WIDTH * Math.random(), 0);
            line(g, 0, Game.HEIGHT * Math.random());
            line(g, Game.WIDTH, Game.HEIGHT * Math.random());
        }
        g.setComposite(oc);

        // The growing green ring.
        double rad = Math.max(20, (cCounter - 180) / 1.5);
        g.setColor(new Color(0, 200, 0));
        g.setStroke(new BasicStroke(3f));
        g.drawOval((int) (CENTER_X - rad), (int) (CENTER_Y - rad), (int) (rad * 2), (int) (rad * 2));
        g.setStroke(os);

        // The closing white-out: a growing white circle, then the whole screen.
        if (cCounter > 180) {
            float ca = (float) GMLHelper.clamp((cCounter - 180) / 60.0, 0, 1);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ca));
            g.setColor(Color.WHITE);
            int r = (int) ((cCounter - 180) / 1.5);
            g.fillOval(CENTER_X - r, CENTER_Y - r, r * 2, r * 2);
            float ra = (float) GMLHelper.clamp((cCounter - 210) / 80.0, 0, 1);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ra));
            g.fillRect(0, 0, Game.WIDTH, Game.HEIGHT);
            g.setComposite(oc);
        }
    }

    private void line(Graphics2D g, double ex, double ey) {
        double jx = CENTER_X + Math.random() * 10 - Math.random() * 10;
        double jy = CENTER_Y + Math.random() * 10 - Math.random() * 10;
        g.drawLine((int) jx, (int) jy, (int) ex, (int) ey);
    }

    private void part(Graphics2D g, String sprite, double px, double py, double sx, double sy) {
        BufferedImage img = Assets.sprite(sprite);
        if (img == null) {
            return;
        }
        g.drawImage(img, (int) px, (int) py, (int) (img.getWidth() * sx), (int) (img.getHeight() * sy), null);
    }
}
