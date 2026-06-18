package boss;

import core.EntityManager;
import core.Game;
import core.GlobalState;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * Papyrus's body (GML: {@code obj_papyrusbody} / {@code mypart1}). In the GML this
 * object is purely presentational — it draws the animated skeleton sprite while the
 * controller spawns every bone.
 *
 * <p>It also owns the <b>decapitation death cutscene</b> (GML: {@code event_user(3)}
 * spawning {@code obj_papyrusdeadhead} + {@code obj_papyrusdeadbody}): on a kill the
 * head detaches, hops, and falls to rest while the headless body fades away. The
 * controller ({@link PapyrusBoss}) drives the accompanying dialogue.
 *
 * // GML: obj_papyrusbody + obj_papyrusdeadhead/obj_papyrusdeadbody
 */
public final class PapyrusBody extends BossBody {

    // ---- Death cutscene state ----------------------------------------------
    /** The detached head is drawn at this scale so it doesn't read as a tiny skull. */
    private static final int HEAD_SCALE = 2;
    private boolean decap;
    private int dt;
    private String headSprite = "spr_papyrusboss_head_0";
    private double feetY;          // the figure's feet line (snapshot at decap start)
    private int fullW;
    private double headX;
    private double headY;
    private double headVY;
    private double headRestY;
    private double bodyAlpha = 1.0;

    public PapyrusBody(EntityManager manager) {
        super(manager);
        this.depth = 10;
    }

    /**
     * Begin the decapitation (GML: head pops off, body slumps headless). The head
     * shows the route-specific expression — the famous "I believe in you" face on
     * the genocide route, the plain skull otherwise.
     */
    public void startDecap(boolean genocide) {
        headSprite = genocide ? "spr_paphead_believe_0" : "spr_papyrusboss_head_0";

        BufferedImage full = Assets.sprite("spr_papyrusboss_0");
        int w = full != null ? full.getWidth() : 148;
        int h = full != null ? full.getHeight() : 208;
        double boxTop = GlobalState.get().idealborder[2];
        int bottom = (boxTop > 0 ? (int) boxTop : 250) - 6;
        feetY = bottom;
        fullW = w;
        double topY = feetY - h;

        BufferedImage head = Assets.sprite(headSprite);
        int hw = head != null ? head.getWidth() : 60;
        headX = Game.WIDTH / 2.0 - hw * HEAD_SCALE / 2.0;
        headY = topY;              // head starts where the figure's head was
        headVY = -3;               // a little hop up first…
        headRestY = topY + 90;     // …then settles lower
        dt = 0;
        decap = true;
    }

    @Override
    public void update() {
        if (!decap) {
            return;                // presentational only outside the death cutscene
        }
        dt++;
        headVY += 0.3;             // GML: gravity 0.2-ish, downward
        headY += headVY;
        if (headY > headRestY) {
            headY = headRestY;
            headVY = 0;
        }
        if (dt > 150) {            // once the head has settled, the body fades away
            bodyAlpha = Math.max(0.0, bodyAlpha - 0.015);
        }
    }

    @Override
    public void render(Graphics2D g) {
        if (decap) {
            renderDecap(g);
            return;
        }
        // GML: obj_papyrusbody draws spr_papyrusboss_anim centred above the box.
        BufferedImage img = Assets.sprite("spr_papyrusboss_0");
        if (img != null) {
            int cx = Game.WIDTH / 2;
            double boxTop = GlobalState.get().idealborder[2];
            int bottom = (boxTop > 0 ? (int) boxTop : 250) - 6;
            int x = cx - img.getWidth() / 2;
            int y = bottom - img.getHeight();
            g.drawImage(img, x, y, null);
            return;
        }
        renderFallback(g);
    }

    /** The headless body (fading) plus the detached head falling. */
    private void renderDecap(Graphics2D g) {
        BufferedImage bodyImg = Assets.sprite("spr_papyrusboss_body_0");
        if (bodyImg != null && bodyAlpha > 0.0) {
            Composite old = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) bodyAlpha));
            int bx = Game.WIDTH / 2 - bodyImg.getWidth() / 2;
            int by = (int) feetY - bodyImg.getHeight();
            g.drawImage(bodyImg, bx, by, null);
            g.setComposite(old);
        }
        BufferedImage head = Assets.sprite(headSprite);
        if (head != null) {
            g.drawImage(head, (int) headX, (int) headY,
                    head.getWidth() * HEAD_SCALE, head.getHeight() * HEAD_SCALE, null);
        } else {
            g.setColor(Color.WHITE);
            g.fillRect((int) headX, (int) headY, Math.min(60, fullW), 40);
        }
    }

    private void renderFallback(Graphics2D g) {
        int cx = Game.WIDTH / 2;
        int topY = 60;
        g.setColor(Color.WHITE);
        g.fillRect(cx - 22, topY, 44, 48);          // skull
        g.setColor(Color.BLACK);
        g.fillRect(cx - 14, topY + 16, 8, 8);       // eyes
        g.fillRect(cx + 6, topY + 16, 8, 8);
        g.setColor(new Color(0xC0, 0x10, 0x10));
        g.fillRect(cx - 26, topY + 50, 52, 10);     // red scarf
        g.setColor(Color.WHITE);
        g.setFont(util.Fonts.ui(14f));
        g.drawString("PAPYRUS", cx - 30, topY + 78);
    }
}
