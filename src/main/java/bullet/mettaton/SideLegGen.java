package bullet.mettaton;

import battle.Soul;
import bullet.AttackPattern;
import core.EntityManager;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import util.Assets;

/**
 * The finale side-leg walls (example_17) — Mettaton's version of Sans's bone walls,
 * but legs. A red frame and an "!" telegraph half the box, then a wall of legs fills
 * that half (hurts on contact); the soul ducks to the safe half. The active half
 * alternates each cycle.
 *
 * // GML: obj_mettattackgen finale leg walls
 */
public final class SideLegGen extends AttackPattern {

    private static final int WARN = 28;
    private static final int ACTIVE = 30;

    private final Soul soul;
    private int t;
    private boolean active;
    private boolean leftSide;

    public SideLegGen(EntityManager manager, Soul soul) {
        super(manager);
        this.soul = soul;
        this.leftSide = Math.random() < 0.5;
        this.depth = -3;
    }

    private double center() {
        return (G.idealborder[0] + G.idealborder[1]) / 2.0;
    }

    private boolean soulOnActiveSide() {
        return leftSide ? soul.x < center() : soul.x > center();
    }

    @Override
    public void update() {
        if (G.turntimer <= 0) {
            manager.destroy(this);
            return;
        }
        t++;
        if (!active) {
            if (t >= WARN) {
                active = true;
                t = 0;
            }
        } else {
            if (G.inv <= 0 && soulOnActiveSide()) {
                soul.hurt(6);
            }
            if (t >= ACTIVE) {
                active = false;
                t = 0;
                leftSide = !leftSide;
            }
        }
    }

    @Override
    public void render(Graphics2D g) {
        double[] b = G.idealborder;
        double c = center();
        int hx = (int) (leftSide ? b[0] : c);
        int hw = (int) (c - b[0]);
        int top = (int) b[2];
        int hh = (int) (b[3] - b[2]);
        if (!active) {
            // Telegraph: a translucent red wash + a "!" on the threatened half.
            Composite oc = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
            g.setColor(new Color(0xFF, 0x20, 0x20));
            g.fillRect(hx, top, hw, hh);
            g.setComposite(oc);
            g.setColor(Color.RED);
            g.setFont(util.Fonts.ui(26f));
            g.drawString("!", hx + hw / 2 - 6, top + hh / 2 + 8);
        } else {
            // The wall of legs filling the half. The right-zone legs use the flipped
            // sprite so the feet point inward, toward the centre of the box.
            BufferedImage img = Assets.sprite(leftSide ? "spr_mettleg2_0" : "spr_mettleg2_flip_0");
            for (int i = 0; i < 5; i++) {
                int ly = top + 10 + i * (hh / 5);
                if (img != null) {
                    g.drawImage(img, hx, ly, hw, img.getHeight() * 2, null);
                } else {
                    g.setColor(Color.WHITE);
                    g.fillRect(hx, ly, hw, 12);
                }
            }
        }
    }
}
