package battle;

import core.Entity;
import core.EntityManager;
import core.GlobalState;
import core.TurnManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Stroke;

/**
 * Mettaton EX's ratings meter (GML: {@code obj_ratingsmaster}) — the thing you win
 * by, not HP. Holds {@link GlobalState#ratings} (starts 4000), the rolling queue of
 * labelled deltas that scroll past ("Fashion +1500", "Disappoint -100", …), and the
 * history used to draw the wobbling {@code RATINGS n} headline plus the
 * axes/target/history graph exactly as the GML Draw event does.
 *
 * <p>The category table and per-use scaling are a direct port of the GML
 * {@code event_user(0)} block. Getting hit pushes a small delta (Violence normally,
 * Disappoint if you Boasted, Justice if you Heel-turned); Boast trickles ratings up
 * through the enemy turn; idling drains them.
 *
 * // GML: obj_ratingsmaster
 */
public final class RatingsMaster extends Entity {

    private static final GlobalState G = GlobalState.get();

    // GML curtype categories.
    public static final int VIOLENCE = 1;
    public static final int DISAPPOINT = 2;
    public static final int JUSTICE = 3;
    public static final int ACTION = 4;
    public static final int HYPERACTION = 5;
    public static final int FASHION = 6;
    public static final int FETCHING = 7;
    public static final int GARBAGE = 8;
    public static final int DRAMATIC = 11;
    public static final int WRITING = 12;

    // Rolling delta queue (GML rq / rq_v / rq_s).
    private final String[] rq = new String[6];
    private final int[] rqv = new int[6];
    private final double[] rqs = new double[6];
    // Pink history polyline (GML rp).
    private final double[] rp = new double[11];
    private final int[] typeuse = new int[15];

    private int alarm5 = 6;
    private int accu;
    private double siner;
    private int checkhp;
    private int timeloss;
    private int oO;
    private int oOb;

    /** GML boastmode: "I won't get hit" — ratings tick up through the enemy turn. */
    public boolean boastmode;
    /** GML heel: the audience roots for your destruction this turn. */
    public boolean heel;
    /** GML essay: the turn-5 essay score, applied as a Writing rating. */
    public int essay;
    /** Mirror of obj_mettatonex.turns (≥20 doubles positive deltas). */
    public int turns;
    /** GML active: drives whether the HUD draws (off during the death cutscene). */
    public boolean active = true;

    public RatingsMaster(EntityManager manager) {
        super(manager);
        this.x = 20;
        this.y = 10;
        this.depth = -20; // HUD, in front of the box/bullets
        G.ratings = 4000;
        for (int i = 0; i < 6; i++) {
            rq[i] = "";
            rqv[i] = 0;
            rqs[i] = 900; // start aged-out so nothing shows until a real delta
        }
        int seed = (int) Math.floor(Math.random() * 8);
        for (int i = 0; i < 10; i++) {
            rp[i] = 4000 - Math.random() * 500;
            if (i == seed) {
                rp[i] = G.ratings;
            }
        }
        checkhp = G.hp;
    }

    /** Push a custom labelled delta (e.g. the per-shot "Action" ratings) and apply it. */
    public void pushRating(String label, int value) {
        for (int i = 5; i > 0; i--) {
            rqv[i] = rqv[i - 1];
            rqs[i] = rqs[i - 1];
            rq[i] = rq[i - 1];
        }
        int v = value;
        if (turns >= 20 && v > 0) {
            v *= 2;
        }
        rq[0] = label;
        rqv[0] = v;
        rqs[0] = 0;
        G.ratings += v;
    }

    /** GML event_user(0): push a labelled delta of the given category and apply it. */
    public void addRating(int curtype) {
        for (int i = 5; i > 0; i--) {
            rqv[i] = rqv[i - 1];
            rqs[i] = rqs[i - 1];
            rq[i] = rq[i - 1];
        }
        String label = "";
        int v = 0;
        int u = typeuse[Math.min(curtype, typeuse.length - 1)];
        switch (curtype) {
            case VIOLENCE -> {
                label = "Violence";
                v = switch (u) { case 0 -> 50; case 1 -> 25; case 2 -> 20; case 3 -> 15; default -> 10; };
            }
            case DISAPPOINT -> {
                label = "Disappoint";
                v = u >= 20 ? -1 : (u >= 5 ? -50 : -100);
                boastmode = false;
            }
            case JUSTICE -> { label = "Justice"; v = 100; }
            case ACTION -> {
                label = "Action";
                v = switch (u) { case 0 -> 300; case 1 -> 200; case 2 -> 150; case 3 -> 100; default -> 50; };
            }
            case HYPERACTION -> {
                label = "HyperAction";
                v = switch (u) { case 0 -> 400; case 1 -> 300; case 2, 3 -> 200; default -> 100; };
            }
            case FASHION -> { label = "Fashion"; v = 1500; }
            case FETCHING -> { label = "Fetching"; v = u == 0 ? 700 : 1; }
            case GARBAGE -> { label = "EatingGarbage?"; v = -50; }
            case DRAMATIC -> {
                label = "Dramatic";
                v = 100;
                if (G.hp < G.maxhp / 1.5) v = 150;
                if (G.hp < G.maxhp / 2) v = 250;
                if (G.hp < G.maxhp / 4) v = 400;
                if (G.hp < 4) v = 500;
                if (G.hp == 1) v = 600;
            }
            case WRITING -> { label = "Writing"; v = essay; }
            default -> { label = "OnBrandFood"; v = 200; }
        }
        typeuse[Math.min(curtype, typeuse.length - 1)]++;
        if (turns >= 20 && v > 0) {
            v *= 2;
        }
        rq[0] = label;
        rqv[0] = v;
        rqs[0] = 0;
        G.ratings += v;
    }

    @Override
    public void update() {
        siner++;

        // GML alarm[5]: shift the pink history left every 6 frames.
        if (--alarm5 <= 0) {
            for (int i = 9; i > 0; i--) {
                rp[i] = rp[i - 1];
            }
            rp[0] = G.ratings - Math.random() * (G.ratings / 2.0);
            if (accu == 6) {
                rp[0] = G.ratings;
            }
            accu++;
            if (accu == 10) {
                accu = 0;
            }
            alarm5 = 6;
        }

        // GML: getting hit changes the ratings (Violence / Disappoint / Justice).
        if (checkhp > G.hp) {
            int curtype = VIOLENCE;
            if (boastmode) {
                curtype = DISAPPOINT;
                boastmode = false;
            }
            if (heel) {
                curtype = JUSTICE;
            }
            addRating(curtype);
        }
        checkhp = G.hp;

        // GML: Boast trickles ratings up while the enemy turn runs.
        if (boastmode && G.turntimer > 0 && G.mnfight == TurnManager.ENEMY_TURN) {
            oOb = oOb == 0 ? 1 : 0;
            G.ratings += oOb == 0 ? 1 : 2;
            if (turns >= 20) {
                G.ratings += 2;
            }
        }
        // Boast / Heel last only for the turn they were declared.
        if (G.mnfight == TurnManager.MENU) {
            boastmode = false;
            heel = false;
        }

        // GML: idling in the menu slowly bleeds ratings (capped).
        if (G.mnfight == TurnManager.MENU && G.myfight == 0) {
            timeloss++;
            if (++oO > 3) {
                oO = 0;
            }
            if (timeloss < 4000 && oO == 0 && G.ratings > 0) {
                G.ratings--;
            }
        }
    }

    @Override
    public void render(Graphics2D g) {
        if (!active) {
            return;
        }
        // GML: "RATINGS n" headline, wobbling on the sine.
        g.setColor(Color.WHITE);
        g.setFont(util.Fonts.ui(28f));
        int hx = (int) (x + 20 + Math.sin(siner / 4));
        int hy = (int) (y + 30 + Math.cos(siner / 4));
        g.drawString("RATINGS " + G.ratings, hx, hy);

        // GML: the scrolling/fading delta popups under the graph.
        g.setFont(util.Fonts.ui(13f));
        for (int i = 0; i < 6; i++) {
            if (rq[i] == null || rq[i].isEmpty()) {
                continue;
            }
            rqs[i] += 1.0 * (i + 2) / 2.0;
            float alpha = 1f;
            if (rqs[i] > 120) {
                alpha = (float) ((170 - rqs[i]) / 50.0);
            }
            if (alpha <= 0f) {
                continue;
            }
            Composite oc = setAlpha(g, alpha);
            String val = rqv[i] >= 0 ? "+" + rqv[i] : Integer.toString(rqv[i]);
            g.setColor(rqv[i] >= 0 ? new Color(0x00FF00) : new Color(0xFF0000));
            int ry = (int) (y + 140 + i * 12);
            g.drawString(rq[i], (int) (x + 60), ry);
            g.drawString(val, (int) (x + 150), ry);
            restore(g, oc);
        }

        // GML: the axes + target (cyan = 10000) + current (yellow) + history (pink).
        Stroke old = g.getStroke();
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(3f));
        g.drawLine((int) (x + 10), (int) (y + 40), (int) (x + 10), (int) (y + 130));
        g.drawLine((int) (x + 10), (int) (y + 130), (int) (x + 180), (int) (y + 130));
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(0x00FFFF)); // cyan reference (the 10000 line)
        g.drawLine((int) (x + 10), (int) (y + 55), (int) (x + 180), (int) (y + 55));
        double ratingsy = G.ratings * 0.0075;
        g.setColor(new Color(0xFFFF00)); // yellow current-ratings line
        g.drawLine((int) (x + 10), (int) (y + 130 - ratingsy), (int) (x + 180), (int) (y + 130 - ratingsy));
        g.setColor(new Color(0xFF00FF)); // pink history polyline
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < 9; i++) {
            double y0 = rp[i] * 0.0075;
            double y1 = rp[i + 1] * 0.0075;
            g.drawLine((int) (x + 10 + i * 20), (int) (y + 130 - y0),
                    (int) (x + 30 + i * 20), (int) (y + 130 - y1));
        }
        g.setStroke(old);
    }

    private static Composite setAlpha(Graphics2D g, float a) {
        Composite old = g.getComposite();
        g.setComposite(java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, a))));
        return old;
    }

    private static void restore(Graphics2D g, Composite old) {
        g.setComposite(old);
    }
}
