package bullet.undyne;

import battle.Soul;
import battle.SoulMode;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import util.GMLHelper;

/**
 * The GREEN-soul rotating shield (GML: {@code obj_spearblocker}). The heart is
 * locked at the box centre; the player rotates a shield with the arrow keys to face
 * each incoming {@link GreenSpear} before it reaches the centre. This object owns the
 * whole green turn: it spawns the {@link GreenSpearGen}, fills it with the
 * per-{@code lesson} choreography (the {@code scr_sr} tables, ported verbatim), and
 * resolves block/hit collision against the spears each frame.
 *
 * <p><b>Collision (faithful to the GML geometry, simplified to four sides):</b> a
 * spear is <em>blocked</em> if, while it is within the shield radius, the player is
 * facing the side it threatens from; it <em>hits</em> if it reaches the centre
 * unblocked. The drawn shield eases toward the faced side for feel; blocking itself
 * is responsive to the held key (GML used {@code idealdir} for the block test).
 *
 * // GML: obj_spearblocker (+ scr_sr lesson tables)
 */
public final class SpearBlocker extends AttackPattern {

    private static final double SHIELD_R = 30;
    private static final double BLOCK_R = 34;   // block while inside this radius
    private static final double HIT_R = 12;     // unblocked spear reaching here hurts

    private final Soul soul;
    private GreenSpearGen gen;

    /** GML: lesson — which spear choreography to fire. */
    public int lesson;
    /** GML: rating — spacing/difficulty handed to the generator. */
    public double rating = 10;
    public int dmg = 7;
    /** GML: refuse — this lesson ends with a boss phase transition (6/11/20/…). */
    public boolean refuse;
    /** Send the one blue finisher spear at the end — only on a GREEN→RED switch turn. */
    public boolean fireFinisher;

    // Shield facing: 0 left · 1 right · 2 down · 3 up (matches GreenSpear.site).
    private int blockSide = 3;          // GML idealdir starts 270 (UP)
    private double drawAngle = 270;     // eased visual angle (GML degrees)
    private int flash;
    private int buffer;
    private boolean finisherFired;      // the one blue spear at the turn's end
    private int finisherTimer;          // frames since the finisher fired

    public SpearBlocker(EntityManager manager, Soul soul) {
        super(manager);
        this.soul = soul;
        this.depth = -50;               // shield draws above the spears
    }

    /** GML event_user(1): build the generator and queue this lesson's spears. */
    public void begin() {
        gen = new GreenSpearGen(manager, soul.x, soul.y, rating, dmg);
        manager.add(gen);
        buildLesson(lesson, gen);
    }

    /** The shield's outward angle in GML degrees, for the faced side. */
    private static double sideAngle(int side) {
        return switch (side) {
            case 0 -> 0;     // left  (GML idealdir 0)
            case 1 -> 180;   // right
            case 2 -> 90;    // down
            default -> 270;  // up
        };
    }

    @Override
    public void update() {
        if (gen == null) {
            begin();
        }

        // GREEN shield phase (until the finisher hands control to the RED dodge).
        if (!finisherFired) {
            gen.cx = soul.x;       // keep the shield centred on the (locked) heart
            gen.cy = soul.y;
            if (soul.leftHeld) {
                blockSide = 0;
            } else if (soul.rightHeld) {
                blockSide = 1;
            } else if (soul.downHeld) {
                blockSide = 2;
            } else if (soul.upHeld) {
                blockSide = 3;
            }
            double target = sideAngle(blockSide);
            double diff = GMLHelper.angle_difference(target, drawAngle);
            drawAngle += diff * 0.5;
            if (Math.abs(diff) < 2) {
                drawAngle = target;
            }
            if (flash > 0) {
                flash--;
            }
            resolveSpears();
        }

        // GML green→red swap: once the queue is spent and the spears have cleared,
        // on a switch turn Undyne turns the heart RED and hurls one blue spear across
        // the (unchanged) box — the player dodges it by moving — then the turn ends.
        if (gen.done && fireFinisher && !finisherFired && !manager.exists(GreenSpear.class)) {
            finisherFired = true;
            soul.setMode(SoulMode.RED);
            soul.x = (G.idealborder[0] + G.idealborder[1]) / 2.0;
            soul.y = (G.idealborder[2] + G.idealborder[3]) / 2.0;
            boolean fromLeft = GMLHelper.irandom(1) == 0;
            double sy = (G.idealborder[2] + G.idealborder[3]) / 2.0;
            manager.add(new BlueFinisher(manager, soul, fromLeft, sy, 6));
        }

        buffer++;
        if (finisherFired) {
            finisherTimer++;
        }
        boolean spentAndClear;
        if (fireFinisher) {
            // The blue spear is added on a deferred flush, so wait a few frames before
            // testing for it (else it "doesn't exist yet" and the turn ends instantly).
            spentAndClear = finisherFired && finisherTimer > 5
                    && !manager.exists(BlueFinisher.class);
        } else {
            spentAndClear = gen.done && !manager.exists(GreenSpear.class) && buffer > 20;
        }
        if (G.turntimer <= 1 || spentAndClear) {
            G.turntimer = 0;
            manager.destroy(gen);
            manager.destroy(this);
        }
    }

    /** Block or hit each live spear (GML spearblocker collision events 4 / 5). */
    private void resolveSpears() {
        manager.with(GreenSpear.class, s -> {
            if (s.blocked) {
                return;
            }
            double d = s.distanceToCenter();
            if (d <= BLOCK_R && blockSide == s.blockSide()) {
                manager.destroy(s);   // a blocked spear vanishes at once (no fade)
                flash = 5;
                util.Audio.play("/audio/snd_bell.wav");  // GML: snd_play(sound0=snd_bell)
                return;
            }
            if (d <= HIT_R) {
                soul.hurt(s.dmg);
                manager.destroy(s);   // consume it so it doesn't multi-hit
            }
        });
    }

    @Override
    public void render(Graphics2D g) {
        if (finisherFired) {
            return;   // RED dodge phase — the shield is gone
        }
        double cx = soul.x;
        double cy = soul.y;
        Stroke os = g.getStroke();
        // Faint guide circle (GML draw_circle, dark green).
        g.setColor(new Color(0x00, 0x80, 0x00));
        g.setStroke(new BasicStroke(1f));
        g.drawOval((int) (cx - SHIELD_R), (int) (cy - SHIELD_R),
                (int) (SHIELD_R * 2), (int) (SHIELD_R * 2));
        // The shield bar: a thick arc on the faced side (perpendicular to its angle).
        double t = Math.toRadians(drawAngle);
        // GML draws a line of half-length r perpendicular to dir, centred on the rim.
        double ox = -Math.cos(t) * SHIELD_R;   // outward point (GML y inverted handled below)
        double oy = Math.sin(t) * SHIELD_R;
        double px = -Math.sin(t) * (SHIELD_R - 2);
        double py = -Math.cos(t) * (SHIELD_R - 2);
        g.setStroke(new BasicStroke(4f));
        g.setColor(flash > 1 ? Color.WHITE : new Color(0x40, 0xA0, 0xFF));
        g.drawLine((int) (cx + ox - px), (int) (cy + oy - py),
                (int) (cx + ox + px), (int) (cy + oy + py));
        g.setStroke(os);
    }

    // ---- scr_sr helper -----------------------------------------------------

    private static void q(GreenSpearGen gen, int dir, int type, double gap, double speed) {
        gen.queue(dir, type, gap, speed);
    }

    /**
     * Port of the per-{@code lesson} {@code scr_sr} tables from {@code obj_spearblocker}
     * (the green User-Defined-1 event). Each {@code scr_sr(dir, type, gap, speed)}
     * call is transcribed verbatim; lessons that also set {@code rating}/{@code dmg}/
     * {@code turntimer} do so here.
     */
    private void buildLesson(int lesson, GreenSpearGen gen) {
        switch (lesson) {
            case -51 -> {
                q(gen, 4, 0, 0, 0); q(gen, 4, 0, 0, 0); q(gen, 4, 0, 0, 0);
                q(gen, 4, 0, 2, 0);
                for (int i = 0; i < 8; i++) {
                    q(gen, 4, 0, 1, 1.4);
                }
            }
            case -50 -> {
                q(gen, 3, 0, 0, 0); q(gen, 3, 0, 0, 0); q(gen, 3, 0, 0, 0);
                q(gen, 1, 0, 0, 0); q(gen, 1, 0, 0, 0);
                q(gen, 0, 0, 0, 0); q(gen, 0, 0, 0, 0);
                q(gen, 3, 0, 0, 0);
            }
            case -40 -> {
                for (int i = 0; i < 15; i++) {
                    q(gen, 4, 0, 0, 2);
                }
                gen.dmg = 1;
            }
            case -39 -> {
                rating = 12;
                gen.rating = 12;
                for (int i = 0; i < 12; i++) {
                    q(gen, 4, 0, 0, 1.2);
                }
                gen.dmg = 1;
                gen.rating += 2;
                G.turntimer = 300;
            }
            case -38 -> {
                q(gen, 0, 0, 0, 0.8); q(gen, 1, 0, 0, 0.8); q(gen, 2, 0, 0, 0.8); q(gen, 3, 0, 0, 0.8);
                q(gen, 0, 0, 0, 0.8); q(gen, 1, 0, 0, 0.8); q(gen, 2, 0, 0, 0.8); q(gen, 3, 0, 0, 0.8);
                gen.rating = 16;
                G.turntimer = 300;
                gen.dmg = 1;
            }
            case -37 -> {
                G.turntimer = 300;
                q(gen, 0, 0, 0, 0.5); q(gen, 1, 0, 0, 0.5); q(gen, 0, 0, 0, 0.5); q(gen, 1, 0, 0, 0.5);
                q(gen, 1, 0, 0, 0.5); q(gen, 0, 0, 0, 0.5); q(gen, 0, 0, 0, 0.5); q(gen, 1, 0, 0, 0.5);
                gen.rating = 20;
                gen.dmg = 1;
            }
            case -5 -> {
                q(gen, 3, 0, 2, 0.5); q(gen, 3, 0, 2, 0.5); q(gen, 3, 0, 6.5, 0.5);
                q(gen, 1, 0, 0, 1.6); q(gen, 2, 0, 0, 1.6); q(gen, 0, 0, 0, 1.6); q(gen, 3, 0, 0, 1.6);
                q(gen, 0, 0, 0, 1.6); q(gen, 2, 0, 0, 1.6); q(gen, 1, 0, 0, 1.6); q(gen, 2, 0, 0, 1.6);
                q(gen, 0, 0, 0, 1.6); q(gen, 3, 0, 0, 1.6);
            }
            case -6 -> {
                q(gen, 0, 0, 0, 1.8); q(gen, 1, 0, 0, 1.8); q(gen, 0, 0, 0.5, 1.8); q(gen, 0, 0, 0, 1.8);
                q(gen, 1, 0, 0, 1.8); q(gen, 1, 0, 0, 1.8); q(gen, 0, 0, 0, 1.8); q(gen, 1, 0, 0.5, 1.8);
                q(gen, 1, 0, 0, 1.8); q(gen, 0, 0, 0, 1.8);
                q(gen, 1, 0, 0, 2); q(gen, 0, 0, 0, 2); q(gen, 1, 0, 0, 2); q(gen, 0, 0, 0, 2);
            }
            case -7 -> {
                for (int i = 0; i < 18; i++) {
                    q(gen, 4, 0, 0, 0.4);
                }
                refuse = true;
            }
            case -8 -> {
                q(gen, 3, 0, 0, 1); q(gen, 0, 0, 0, 1.8); q(gen, 2, 0, 0, 1); q(gen, 1, 0, 0, 1.8);
                q(gen, 0, 0, 0, 1); q(gen, 3, 0, 0, 0.5); q(gen, 2, 0, 0, 0.47); q(gen, 1, 0, 0, 1.8);
                q(gen, 0, 0, 0, 1);
            }
            case -9 -> {
                q(gen, 3, 0, 0.5, 2); q(gen, 3, 0, 0.5, 2); q(gen, 3, 0, 0.5, 2); q(gen, 3, 0, 0, 2);
                q(gen, 0, 0, 0.5, 2); q(gen, 3, 0, 0.5, 2); q(gen, 3, 0, 0.5, 2); q(gen, 3, 0, 0.5, 2);
                q(gen, 3, 0, 0, 2); q(gen, 1, 0, 0.5, 2); q(gen, 3, 0, 0.5, 2); q(gen, 3, 0, 0.5, 2);
                q(gen, 3, 0, 1, 2); q(gen, 0, 0, 0.5, 2); q(gen, 3, 0, 0.5, 2); q(gen, 3, 0, 0.5, 2);
                q(gen, 3, 0, 1, 2); q(gen, 1, 0, 0.5, 2); q(gen, 3, 0, 0.5, 2); q(gen, 3, 0, 0.5, 2);
                q(gen, 3, 0, 0.5, 2);
            }
            case -10 -> {
                q(gen, 0, 0, 0, 0); q(gen, 3, 0, 0, 0); q(gen, 0, 0, 0, 0); q(gen, 3, 0, 0, 0);
                q(gen, 0, 0, 0, 0); q(gen, 3, 0, 0, 0); q(gen, 0, 0, 0, 0); q(gen, 3, 0, 0, 0);
                q(gen, 0, 1, 0, 0); q(gen, 3, 1, 0, 0); q(gen, 0, 1, 0, 0); q(gen, 3, 1, 0, 0);
                q(gen, 0, 1, 0, 0); q(gen, 3, 1, 0, 0);
                refuse = true;
            }
            case -11 -> {
                q(gen, 1, 1, 1.25, 2); q(gen, 3, 1, 1.25, 2); q(gen, 0, 1, 1.25, 2); q(gen, 2, 1, 2, 2);
                q(gen, 3, 0, 1.25, 2); q(gen, 0, 0, 1.25, 2); q(gen, 2, 0, 1.25, 2); q(gen, 1, 0, 2, 2);
                q(gen, 2, 1, 1.25, 2); q(gen, 0, 1, 1.25, 2); q(gen, 1, 1, 1.25, 2); q(gen, 3, 1, 1.25, 2);
            }
            case -12 -> {
                for (int i = 0; i < 2; i++) {
                    q(gen, 0, 0, 0, 1.3); q(gen, 1, 0, 0, 1.3); q(gen, 3, 0, 0.1, 1.3); q(gen, 2, 1, 2.2, 1.3);
                    q(gen, 0, 0, 0, 1.3); q(gen, 1, 0, 0, 1.3); q(gen, 2, 0, 0.1, 1.3); q(gen, 3, 1, 2.2, 1.3);
                }
            }
            case -13 -> {
                q(gen, 0, 0, 0, 1.5); q(gen, 0, 1, 2, 1.5); q(gen, 2, 0, 0, 1.5); q(gen, 2, 1, 2, 1.5);
                q(gen, 1, 0, 0, 1.5); q(gen, 1, 1, 2.2, 1.5); q(gen, 3, 0, 0, 1.5); q(gen, 3, 1, 2, 1.5);
                q(gen, 0, 0, 0, 1.5); q(gen, 0, 1, 2, 1.5); q(gen, 2, 0, 0, 1.5); q(gen, 2, 1, 2, 1.5);
                q(gen, 1, 0, 0, 1.5); q(gen, 1, 1, 2.2, 1.5);
            }
            case -14 -> {
                for (int i = 0; i < 24; i++) {
                    q(gen, 4, 0, 0, 0.3);
                }
                refuse = true;
            }
            case 0, 19 -> {
                q(gen, 0, 0, 1, 0); q(gen, 0, 0, 1, 0); q(gen, 0, 0, 0.5, 0); q(gen, 0, 0, 0.5, 0);
                q(gen, 0, 0, 1, 0); q(gen, 0, 2, 1, 0); q(gen, 0, 2, 1, 0); q(gen, 1, 2, 0, 0);
                q(gen, 1, 2, 0, 0);
            }
            case 1 -> {
                for (int i = 0; i < 3; i++) {
                    q(gen, 3, 0, 1, 0.5);
                }
            }
            case 2 -> {
                q(gen, 3, 0, 1, 0.625); q(gen, 3, 0, 1, 0.625);
                q(gen, 0, 0, 1, 0.625); q(gen, 0, 0, 1, 0.625);
                q(gen, 1, 0, 1, 0.625); q(gen, 1, 0, 1, 0.625);
            }
            case 3 -> {
                q(gen, 0, 0, 0, 0.75); q(gen, 1, 0, 0, 0.75); q(gen, 0, 0, 0, 0.75); q(gen, 1, 0, 0, 0.75);
                q(gen, 1, 0, 0, 0.75); q(gen, 0, 0, 0, 0.75); q(gen, 0, 0, 0, 0.75); q(gen, 2, 0, 0, 0.75);
            }
            case 4 -> {
                q(gen, 3, 0, 0, 0); q(gen, 1, 0, 0, 0); q(gen, 2, 0, 0, 0); q(gen, 0, 0, 0, 0);
                q(gen, 3, 0, 0, 0); q(gen, 1, 0, 0, 0); q(gen, 2, 0, 0, 0); q(gen, 0, 0, 0, 0);
                q(gen, 3, 0, 0.5, 0); q(gen, 3, 0, 0.5, 0); q(gen, 3, 0, 0.5, 0); q(gen, 3, 0, 0.5, 0);
            }
            case 5 -> {
                q(gen, 0, 0, 0, 0); q(gen, 3, 0, 0.5, 0); q(gen, 3, 0, 0, 0); q(gen, 1, 0, 0, 0);
                q(gen, 3, 0, 0.5, 0); q(gen, 3, 0, 0, 0); q(gen, 0, 0, 0, 0); q(gen, 3, 0, 0, 0);
                q(gen, 2, 0, 0, 0);
            }
            case 6 -> {
                q(gen, 0, 0, 0, 0); q(gen, 3, 0, 0, 0); q(gen, 2, 0, 0, 0); q(gen, 1, 0, 0, 0);
                q(gen, 0, 0, 0, 0); q(gen, 3, 0, 0, 0); q(gen, 2, 0, 0, 0); q(gen, 1, 0, 0, 0);
                q(gen, 2, 0, 0, 0); q(gen, 3, 0, 0, 0);
                refuse = true;
            }
            case 7, 8 -> {
                q(gen, 3, 0, 1.2, 0); q(gen, 2, 0, 1.2, 0); q(gen, 3, 0, 0, 0); q(gen, 2, 0, 0, 0);
                q(gen, 2, 0, 0.8, 0); q(gen, 3, 0, 0.8, 0); q(gen, 2, 0, 0.8, 0); q(gen, 3, 0, 0.8, 0);
            }
            case 9 -> {
                double rr = rating;
                if (rating >= 11) {
                    rr--;
                }
                q(gen, 0, 0, 2, 3 / rr);
                q(gen, 3, 0, 1, 1.5); q(gen, 1, 0, 1, 1.5); q(gen, 2, 0, 1, 1.5);
                q(gen, 3, 0, 1, 1.5); q(gen, 1, 0, 1, 1.5); q(gen, 2, 0, 1, 1.5);
            }
            case 10 -> {
                double rr = 0;
                if (rating <= 11) {
                    rr = 0.5;
                }
                q(gen, 0, 0, 0.1, 1); q(gen, 0, 0, 0.1, 1.5); q(gen, 0, 0, 2 + rr, 2);
                q(gen, 1, 0, 0.1, 1); q(gen, 1, 0, 0.1, 1.5); q(gen, 1, 0, 2 + rr, 2);
                q(gen, 2, 0, 1, 2); q(gen, 3, 0, 1, 2); q(gen, 2, 0, 1, 2); q(gen, 3, 0, 1, 2);
            }
            case 11 -> {
                q(gen, 4, 0, 0, 0); q(gen, 4, 0, 0, 0); q(gen, 4, 0, 0, 0); q(gen, 4, 0, 2.2, 0);
                q(gen, 4, 0, 1, 1.5); q(gen, 4, 0, 1, 1.5); q(gen, 4, 0, 1, 1.5); q(gen, 4, 0, 2.2, 1.5);
                q(gen, 4, 0, 1, 2); q(gen, 4, 0, 1, 2); q(gen, 4, 0, 1, 2); q(gen, 4, 0, 1, 2);
                refuse = true;
            }
            case 12, 13 -> {
                q(gen, 1, 0, 0, 0); q(gen, 0, 0, 0, 0); q(gen, 1, 0, 0, 0); q(gen, 0, 0, 0, 0);
                q(gen, 1, 1, 0, 0);
            }
            case 14 -> {
                q(gen, 0, 0, 0, 0); q(gen, 1, 0, 0, 0); q(gen, 2, 1, 2, 0); q(gen, 2, 0, 0, 0);
                q(gen, 0, 0, 0, 0); q(gen, 1, 0, 0, 0); q(gen, 3, 0, 0, 0); q(gen, 2, 1, 1, 0);
            }
            case 15 -> {
                q(gen, 0, 0, 1, 1.6); q(gen, 2, 0, 1, 1.6); q(gen, 1, 0, 1, 1.6); q(gen, 3, 0, 1.6, 1.6);
                q(gen, 1, 1, 1.2, 0); q(gen, 3, 1, 1.2, 0); q(gen, 0, 1, 1.2, 0); q(gen, 2, 1, 1.2, 0);
            }
            case 16 -> {
                q(gen, 1, 0, 0, 0); q(gen, 1, 0, 0, 0); q(gen, 0, 0, 0, 0); q(gen, 0, 0, 0, 0);
                q(gen, 2, 2, 3, 0); q(gen, 3, 2, 2, 0); q(gen, 2, 0, 3, 0);
                q(gen, 1, 0, 1, 2); q(gen, 0, 0, 1, 2);
            }
            case 17 -> {
                q(gen, 4, 0, 0, 1.6); q(gen, 4, 0, 0, 1.6); q(gen, 4, 0, 0, 1.6); q(gen, 4, 1, 1.6, 0);
                q(gen, 4, 0, 0, 0); q(gen, 4, 0, 0, 0); q(gen, 0, 2, 1, 0); q(gen, 4, 0, 0, 0);
            }
            case 18 -> {
                for (int i = 0; i < 10; i++) {
                    q(gen, 4, 0, 0, 2);
                }
            }
            case 20 -> {
                for (int i = 0; i < 3; i++) {
                    q(gen, 1, 1, 1.25, 1.5); q(gen, 3, 1, 1.25, 1.5);
                    q(gen, 0, 1, 1.25, 1.5); q(gen, 2, 1, 1.25, 1.5);
                }
                refuse = true;
            }
            default -> {
                if (lesson >= -36 && lesson < -25) {
                    G.turntimer = 300;
                    gen.rating = 34;
                    for (int i = 0; i < 3; i++) {
                        q(gen, 3, 0, 1, 0.25);
                    }
                    gen.dmg = 1;
                } else {
                    // lesson > 20 (and any unlisted): the late "..." pattern.
                    q(gen, 4, 0, 0, 1.6); q(gen, 4, 0, 0, 1.6); q(gen, 4, 0, 0, 1.6); q(gen, 4, 1, 2, 0);
                    q(gen, 4, 0, 0, 0); q(gen, 4, 0, 0, 0); q(gen, 0, 2, 1, 0); q(gen, 4, 0, 0, 0);
                }
            }
        }
    }
}
