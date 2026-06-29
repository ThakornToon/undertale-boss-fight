package battle;

import core.Entity;
import core.EntityManager;
import core.GlobalState;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;
import util.GMLHelper;

/**
 * The player heart (GML: {@code obj_heart}). Implements every movement mode the
 * eight bosses need: red free-move, blue gravity/jump (4 directions), green
 * directional-block, and yellow shoot.
 *
 * <p>Input is delivered through the {@code left/right/up/down}{@code Held} and
 * {@code confirmPressed} fields, which {@code BattleScene} populates each frame
 * from {@link core.InputHandler}. The soul itself stays decoupled from input and
 * the combat box: it reads box bounds straight from
 * {@link GlobalState#idealborder}, exactly as {@code obj_heart} reads
 * {@code global.idealborder} in GML.
 *
 * // GML: obj_heart
 */
public final class Soul extends Entity {

    private static final GlobalState G = GlobalState.get();

    /** Half-extent of the heart's hitbox in pixels (16x16 sprite). */
    public static final double HALF = 8.0;

    // ---- Movement state -----------------------------------------------------
    public SoulMode mode = SoulMode.RED;
    /** GML: obj_heart.movement mirror, kept in sync with {@link #mode}. */
    public int movement = SoulMode.RED.movement;
    public double hspeed;
    public double vspeed;
    /** GML: obj_heart.yprevious — last frame's y, so callers can tell if it's rising. */
    public double yprevious;
    /**
     * Free-move / horizontal speed in px/frame. GML's base soul speed is 2; we
     * run faster so the heart feels snappier and more responsive to steer.
     */
    public double moveSpeed = 6.0;

    // Blue-soul jump state (GML: obj_heart movement 2/11/12/13 + jumpstage).
    /** GML jumpstage == 2: the soul is airborne (rising or falling). */
    public static final int AIRBORNE = 2;
    /** GML jumpstage == 1: the soul is resting against its gravity wall. */
    public static final int GROUNDED = 1;
    /** GML: obj_heart.jumpstage — 0 uninitialised, 1 grounded, 2 airborne. */
    public int jumpStage;

    /** Launch impulse against gravity (GML: {@code vspeed = -6} on jump). */
    private static final double JUMP_VELOCITY = 6.0;
    /** Terminal speed; GML's gravity bands stop accelerating past 8 px/frame. */
    private static final double FALL_MAX = 8.0;

    // Green-soul block state. facing: 0=right,1=up,2=left,3=down.
    public int facing = 1;

    // Purple-soul (web) state — GML obj_purpleheart. The web geometry (line spacing,
    // count, scroll) lives on {@link WebBoard}; these track where the heart rides it.
    /** The web this purple soul is locked to (set by the boss on entering PURPLE). */
    public WebBoard web;
    /** GML yno — the strand the heart currently rides (1-indexed). */
    public int webYno = 2;
    /** GML moving — 0 resting, 1 hopping to the strand above, 2 to the one below. */
    public int webMoving;
    /** GML space — progress (0..yspace) of the current strand-to-strand hop. */
    public double webSpace;

    // Yellow-soul shoot state.
    public boolean shot;
    /** Set true on a frame the soul fires; consumed by the active boss/body. */
    public boolean firedThisFrame;
    /** GML: cadence between shots while the fire key is held (frames). */
    private static final int FIRE_RATE = 7;
    private int shootCooldown;

    // Per-attack overrides.
    public boolean ignoreBorder;
    public int slamPain;

    /** Hide the combat heart while the player command menu owns the box. */
    public boolean hidden;

    /** GML: obj_heart.image_alpha — cutscene fades (Sans special / transforms). */
    public float imageAlpha = 1.0f;

    // ---- Injected input (set by BattleScene from InputHandler) --------------
    public boolean leftHeld;
    public boolean rightHeld;
    public boolean upHeld;
    public boolean downHeld;
    /** Edge-triggered up/down (GML keyboard_check_pressed) — the purple line-hop. */
    public boolean upPressed;
    public boolean downPressed;
    public boolean confirmPressed;
    /** GML: the fire key held (yellow shoot soul fires while held). */
    public boolean confirmHeld;

    public Soul(EntityManager manager) {
        super(manager);
        this.depth = -100; // soul draws above bullets
    }

    public void setMode(SoulMode m) {
        this.mode = m;
        this.movement = m.movement;
        this.hspeed = 0;
        this.vspeed = 0;
        this.shot = (m == SoulMode.YELLOW);
        this.jumpStage = 0;
        if (m == SoulMode.PURPLE) {
            this.webYno = 2;
            this.webMoving = 0;
            this.webSpace = 0;
        }
    }

    @Override
    public void update() {
        firedThisFrame = false;
        yprevious = y;
        switch (mode) {
            case RED -> updateRed();
            case BLUE, BLUE_UP, BLUE_LEFT, BLUE_RIGHT -> updateBlue();
            case GREEN -> updateGreen();
            case YELLOW -> updateYellow();
            case PURPLE -> updatePurple();
        }
        // The purple soul positions itself on the web (it's never free in the box), so
        // it skips the box clamp; every other mode clamps to idealborder.
        if (!ignoreBorder && mode != SoulMode.PURPLE) {
            clampToBorder();
        }
    }

    // GML: obj_heart movement==1 — free 4-directional move.
    private void updateRed() {
        double dx = (rightHeld ? 1 : 0) - (leftHeld ? 1 : 0);
        double dy = (downHeld ? 1 : 0) - (upHeld ? 1 : 0);
        x += dx * moveSpeed;
        y += dy * moveSpeed;
    }

    // GML: obj_heart movement==2 (and directional 11/12/13) — gravity + jump.
    private void updateBlue() {
        // Gravity axis depends on the directional variant (matches GML movement
        // ints: 2=down, 11=right, 12=up, 13=left).
        double gx = 0, gy = 0;
        switch (mode) {
            case BLUE -> gy = 1;
            case BLUE_UP -> gy = -1;
            case BLUE_LEFT -> gx = -1;
            case BLUE_RIGHT -> gx = 1;
            default -> gy = 1;
        }

        if (gx == 0) {
            // Vertical gravity: arrows move horizontally, jump along gravity axis.
            x += ((rightHeld ? 1 : 0) - (leftHeld ? 1 : 0)) * moveSpeed;
            boolean jumpKey = (gy > 0) ? upHeld : downHeld;
            if (grounded() && jumpKey) {
                vspeed = -gy * JUMP_VELOCITY;   // launch against gravity
                jumpStage = AIRBORNE;
            }
            vspeed = blueGravity(vspeed, gy, jumpKey);
            y += vspeed;
        } else {
            // Horizontal gravity: arrows move vertically, jump along gravity axis.
            y += ((downHeld ? 1 : 0) - (upHeld ? 1 : 0)) * moveSpeed;
            boolean jumpKey = (gx > 0) ? leftHeld : rightHeld;
            if (grounded() && jumpKey) {
                hspeed = -gx * JUMP_VELOCITY;
                jumpStage = AIRBORNE;
            }
            hspeed = blueGravity(hspeed, gx, jumpKey);
            x += hspeed;
        }
        resolveBlueFloor(gx, gy);
    }

    /**
     * Blue-soul jump arc — the exact piecewise gravity from GML {@code obj_heart}
     * (movement 2/11/12/13). Velocity decelerates in four bands as it climbs and
     * accelerates as it falls, which gives Undertale's distinctive jump feel: a
     * floaty launch, a slow decel through the middle, a noticeable <em>hang</em> at
     * the apex, then a snappy fall to terminal speed. A single symmetric gravity
     * constant cannot reproduce the apex hang — the bands are the whole point.
     *
     * <p>Variable height: while still rising ({@code a <= -1}), releasing the jump
     * key snaps upward momentum to {@code -1}, so a tap is a small hop and full
     * height needs only a brief hold. (GML's {@code osflavor} check; we always use
     * the {@code keyboard_check} branch.)
     *
     * <p>{@code gravSign} is the gravity direction (+1 = pulls toward larger
     * coordinates). Working in {@code a = vel * gravSign} folds all four blue
     * directions into one signed model: {@code a > 0} falls with gravity, {@code a
     * < 0} rises against it — so the four mirrored GML blocks collapse to one.
     */
    private double blueGravity(double vel, double gravSign, boolean jumpKey) {
        double a = vel * gravSign;
        // Variable jump height — cut the climb short the moment the key is released.
        if (!jumpKey && a <= -1) {
            a = -1;
        }
        // GML's four acceleration bands (see obj_heart Step, movement == 2).
        if (a > 0.5 && a < FALL_MAX) {
            a += 0.6;             // accelerating fall
        } else if (a > -1 && a <= 0.5) {
            a += 0.2;             // the apex hang
        } else if (a > -4 && a <= -1) {
            a += 0.5;             // mid-rise deceleration
        } else if (a <= -4) {
            a += 0.2;             // floaty launch just off the floor
        }
        if (a > FALL_MAX) {
            a = FALL_MAX;         // clamp to terminal fall speed
        }
        return a * gravSign;
    }

    /** Land the blue soul against the wall its gravity points toward. */
    private void resolveBlueFloor(double gx, double gy) {
        double[] b = G.idealborder;
        if (gy > 0 && y >= b[3] - HALF) {
            y = b[3] - HALF;
            vspeed = 0;
            landed();
        } else if (gy < 0 && y <= b[2] + HALF) {
            y = b[2] + HALF;
            vspeed = 0;
            landed();
        } else if (gx > 0 && x >= b[1] - HALF) {
            x = b[1] - HALF;
            hspeed = 0;
            landed();
        } else if (gx < 0 && x <= b[0] + HALF) {
            x = b[0] + HALF;
            hspeed = 0;
            landed();
        }
    }

    /** GML: jumpstage == 1 — the blue soul is resting against its gravity wall. */
    public boolean grounded() {
        return jumpStage == GROUNDED;
    }

    /**
     * GML obj_heart: slam_pain — being thrown into a wall hurts on impact (Sans's
     * special). Cleared after firing so each slam hurts exactly once; the slammer
     * re-arms {@link #slamPain} before the next throw.
     */
    private void landed() {
        jumpStage = GROUNDED;
        if (slamPain > 0) {
            hurt(slamPain);
            slamPain = 0;
        }
    }

    // GML: obj_heart movement==3 — locked position, arrows rotate the shield.
    private void updateGreen() {
        if (rightHeld) {
            facing = 0;
        } else if (upHeld) {
            facing = 1;
        } else if (leftHeld) {
            facing = 2;
        } else if (downHeld) {
            facing = 3;
        }
        // No translation: the green soul stays put.
    }

    // GML: obj_heart shot==1 (Mettaton EX) — free 4-directional movement inside the
    // box, firing a bullet straight up on a fixed cadence while the key is held. The
    // EX body consumes {@link #firedThisFrame} to spawn the PlayerBullet.
    private void updateYellow() {
        double dx = (rightHeld ? 1 : 0) - (leftHeld ? 1 : 0);
        double dy = (downHeld ? 1 : 0) - (upHeld ? 1 : 0);
        x += dx * moveSpeed;
        y += dy * moveSpeed;
        if (shootCooldown > 0) {
            shootCooldown--;
        }
        // Fire ONLY on confirmHeld — BattleScene gates that to the enemy turn, so the
        // yellow soul can't shoot while the player is in the menu or a cutscene is
        // running. (confirmPressed stays ungated for those: bosses read it to skip
        // dialogue, so it must not drive the gun.) A held key fires on its first
        // frame too, so tap-firing during the enemy turn is unaffected.
        if (confirmHeld && shootCooldown <= 0) {
            firedThisFrame = true;
            shootCooldown = FIRE_RATE;
        }
    }

    /**
     * GML: {@code obj_purpleheart} Step — the Muffet web soul. The heart is bound to
     * the {@link WebBoard} strands: free left/right within {@code xmid±xlen}, and an
     * UP/DOWN press hops it one strand at a time (animating over {@code yspace/3} per
     * frame). It never floats free. During the pet special the web itself rises
     * ({@code type 3} climb → {@code type 1} steady scroll), dragging the heart toward
     * the bottom strand — the pet's maw — so the player must keep climbing or take a
     * hit. GML's {@code type} field is always 0 (the move/hop block always runs);
     * {@link WebBoard#type} is GML's {@code ttype} (the rise mode).
     */
    private void updatePurple() {
        WebBoard w = web;
        if (w == null) {
            return;
        }
        // Free horizontal slide along the current strand.
        if (leftHeld && x > w.xmid - w.xlen) {
            x -= 4;
        }
        if (rightHeld && x < w.xmid + w.xlen) {
            x += 4;
        }
        // UP/DOWN hop to the adjacent strand (edge-triggered; one hop at a time).
        if (upPressed && webMoving == 0 && webYno > 1) {
            webMoving = 1;
        }
        if (webMoving == 1) {
            webSpace += w.yspace / 3.0;
            y = w.yzero + (webYno - 1) * w.yspace - webSpace + w.yoff;
            if (webSpace >= w.yspace) {
                webYno--;
                webSpace = 0;
                webMoving = 0;
            }
        }
        if (downPressed && webMoving == 0 && webYno < w.yamt) {
            webMoving = 2;
        }
        if (webMoving == 2) {
            webSpace += w.yspace / 3.0;
            y = w.yzero + (webYno - 1) * w.yspace + webSpace + w.yoff;
            if (webSpace >= w.yspace) {
                webYno++;
                webSpace = 0;
                webMoving = 0;
            }
        }

        // GML ttype == 1: the web steadily scrolls down; the heart drifts toward the
        // bottom strand and falling off it (yno past the last strand) is a hit.
        if (w.type == 1) {
            w.yoff += w.yadd;
            y += w.yadd;
            if (w.yoff > w.yspace) {
                webYno++;
                if (webYno > w.yamt) {
                    webYno = w.yamt;
                    hurt(6); // GML: dmg = 6; scr_damagestandard_x()
                }
                w.yoff = 0;
                if (webMoving == 0) {
                    y = w.yzero + (webYno - 1) * w.yspace + webSpace + w.yoff;
                }
            }
        }

        // GML ttype == 3: the cupcake-rise transition — the web climbs to y=100,
        // gaining a strand every yspace of rise, then switches to the steady scroll.
        if (w.type == 3) {
            if (w.yzero > 100) {
                w.yzero -= 4;
            }
            y -= 4;
            w.yz2 += 4;
            if (w.yz2 > w.yspace) {
                w.yz2 -= w.yspace;
                w.yamt++;
            }
            if (w.yzero <= 100) {
                w.yzero = 100;
                w.type = 1;
                w.yadd = w.yadd2;
            }
        }
    }

    /** GML: clamp obj_heart to global.idealborder unless an attack frees it. */
    public void clampToBorder() {
        double[] b = G.idealborder;
        if (b[1] > b[0]) { // only clamp once a real box exists
            x = GMLHelper.clamp(x, b[0] + HALF, b[1] - HALF);
        }
        if (b[3] > b[2]) {
            y = GMLHelper.clamp(y, b[2] + HALF, b[3] - HALF);
        }
    }

    /**
     * GML: a bullet collides with the soul. Sets the i-frame window and flags a
     * pending hit on {@link GlobalState} for {@code DamageSystem} to resolve —
     * keeps the soul decoupled from the damage pipeline.
     */
    public void hurt(int dmg) {
        if (G.inv > 0) {
            return; // still invulnerable
        }
        G.damage = dmg;
        G.takedamage = 1;
        G.inv = 30; // ~1s i-frames; DamageSystem may override
    }

    /**
     * GML: Sans special — switch the blue soul to a slam direction. The slam
     * velocity itself is set by the caller (it varies per slam), so this just
     * arms the directional gravity; {@link #slamPain} carries the impact damage.
     */
    public void applySlam(SoulMode dir) {
        setMode(dir);
    }

    @Override
    public void render(Graphics2D g) {
        if (hidden || imageAlpha <= 0f) {
            return; // the menu draws its own heart cursor during the player turn
        }
        java.awt.Composite oldComposite = null;
        if (imageAlpha < 1.0f) {
            oldComposite = g.getComposite();
            g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, Math.min(1.0f, imageAlpha)));
        }
        // GML: draw_sprite(obj_heart sprite_index) — colour comes from the sprite.
        BufferedImage img = Assets.sprite(spriteForMode());
        if (img != null) {
            g.drawImage(img, (int) (x - HALF), (int) (y - HALF),
                    (int) (HALF * 2), (int) (HALF * 2), null);
        } else {
            // Fallback vector heart if the sprite is missing.
            g.setColor(colorForMode());
            int s = (int) (HALF * 2);
            g.fillRect((int) (x - HALF), (int) (y - HALF), s, s);
        }
        if (oldComposite != null) {
            g.setComposite(oldComposite);
        }
    }

    /**
     * Heart sprite matching the current movement mode (GML: obj_heart frames). The
     * {@code _1} frames are the brighter/fuller colour variant, used for the live
     * combat soul so the heart reads clearly against the black box.
     */
    private String spriteForMode() {
        return switch (mode) {
            case GREEN -> "spr_heartgreen_1";
            case YELLOW -> "spr_heartyellow_1";
            case BLUE -> "spr_heartblue_1";
            case BLUE_UP -> "spr_heartblue_u_1";
            case BLUE_LEFT -> "spr_heartblue_l_1";
            case BLUE_RIGHT -> "spr_heartblue_r_1";
            case PURPLE -> "spr_heartpurple_1";
            case RED -> "spr_heart_1";
        };
    }

    private Color colorForMode() {
        return switch (mode) {
            case GREEN -> Color.GREEN;
            case YELLOW -> Color.YELLOW;
            case PURPLE -> new Color(128, 0, 128);
            case RED -> Color.RED;
            default -> Color.CYAN; // blue variants
        };
    }
}
