package battle;

import core.Entity;
import core.EntityManager;
import core.GlobalState;
import core.InputHandler;
import core.InputHandler.Key;
import core.TurnManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import util.Assets;

/**
 * The player command menu: FIGHT / ACT / ITEM / MERCY and their sub-menus, the
 * FIGHT attack-timing bar, and the "press to continue" result text — all of which
 * run while {@code global.mnfight == 2} so the turn never leaves the player's hands
 * until they dismiss the action result. Navigation reads {@link InputHandler}; the
 * box text/options draw inside the widened box ({@link BulletBoard} menu mode).
 *
 * <p>The four action hooks ({@link #onFight}/{@link #onItem}/{@link #onSpare} and
 * the ACT dispatch) only perform their effect and set the boss's narration line;
 * the menu then shows that line and, on confirm, hands the turn to the enemy. This
 * keeps {@link TurnManager} untouched — it still owns the enemy-turn machine.
 *
 * // GML: the bmenu / FIGHT-ACT-ITEM-MERCY button system + the attack bar
 */
public final class BattleMenu extends Entity {

    private static final GlobalState G = GlobalState.get();

    private enum Phase { MAIN, ACT, ITEM, MERCY, FIGHTBAR, RESULT }

    private static final String[] MAIN_BUTTONS = { "FIGHT", "ACT", "ITEM", "MERCY" };
    private static final String[] MERCY_OPTIONS = { "Spare", "Flee" };

    // GML: the four bottom-row command button sprites. Frame 0 is the yellow
    // highlighted (selected) face; frame 1 is the orange idle face with its icon.
    private static final String[] BUTTON_IDLE = {
        "spr_fightbt_1", "spr_actbt_center_1", "spr_itembt_1", "spr_sparebt_1"
    };
    private static final String[] BUTTON_SEL = {
        "spr_fightbt_0", "spr_actbt_center_0", "spr_itembt_0", "spr_sparebt_0"
    };
    /** Button sprite size (110x40) and the x of each across the 640px row. */
    private static final int BTN_W = 110;
    private static final int BTN_H = 40;
    private static final int BTN_Y = 426;
    private static final int[] BTN_X = { 30, 185, 340, 495 };

    /** Box-text font: large pixel-ish monospaced, like Undertale's menu text. */
    private static final java.awt.Font BOX_FONT =
            util.Fonts.ui(22f);

    private final InputHandler input;
    private final ActDispatcher act;

    private Phase phase = Phase.MAIN;
    private int selected;

    /**
     * GML: {@code obj_sparebt.visible = 0} (Asgore). When set, the MERCY button is
     * removed from the command row entirely — the player can only FIGHT / ACT / ITEM.
     */
    public boolean mercyHidden;

    // FIGHT attack-bar state: a line sweeps 0→1 across the box; confirm stops it.
    private double fightPos;
    private static final double FIGHT_SPEED = 0.028;

    // What to run once the player dismisses the action-result text.
    private Runnable resultNext = () -> { };
    // A locally-authored result line (e.g. Flee); null = use the boss flavor line.
    private String localResult;

    /** FIGHT confirmed with the given attack accuracy (0 miss … 1 bullseye). */
    public java.util.function.DoubleConsumer onFight = acc -> { };
    /** ITEM confirmed; sets the boss's result line (no inventory in the rush). */
    public Runnable onItem = () -> { };
    /** SPARE confirmed; attempts the spare and sets the boss's result line. */
    public Runnable onSpare = () -> { };
    /** The narration line shown inside the box (MAIN flavor / action result). */
    public java.util.function.Supplier<String> flavor = () -> "";

    public BattleMenu(EntityManager manager, InputHandler input, ActDispatcher act) {
        super(manager);
        this.input = input;
        this.act = act;
        this.depth = -1000; // UI draws on top
    }

    /** Re-arm the menu at the start of a player turn. */
    public void reset() {
        phase = Phase.MAIN;
        selected = 0;
        fightPos = 0;
        localResult = null;
        G.bmenuno = 0;
        G.bmenucoord = 0;
    }

    @Override
    public void update() {
        if (G.mnfight != TurnManager.MENU) {
            return; // the menu only lives during the player command phase
        }
        switch (phase) {
            case MAIN -> updateMain();
            case ACT -> updateList(act.optionCount(), this::confirmAct);
            case ITEM -> updateList(1, i -> { onItem.run(); enterResult(); });
            case MERCY -> updateList(MERCY_OPTIONS.length, this::confirmMercy);
            case FIGHTBAR -> updateFightBar();
            case RESULT -> updateResult();
        }
        G.bmenucoord = selected;
    }

    /** Number of command buttons shown — MERCY is dropped when {@link #mercyHidden}. */
    private int buttonCount() {
        return mercyHidden ? MAIN_BUTTONS.length - 1 : MAIN_BUTTONS.length;
    }

    private void updateMain() {
        if (input.pressed(Key.RIGHT)) {
            int prev = selected;
            selected = Math.min(selected + 1, buttonCount() - 1);
            if (selected != prev) {
                util.Audio.play(util.Audio.SFX_MOVE);
            }
        } else if (input.pressed(Key.LEFT)) {
            int prev = selected;
            selected = Math.max(selected - 1, 0);
            if (selected != prev) {
                util.Audio.play(util.Audio.SFX_MOVE);
            }
        }
        if (input.pressed(Key.CONFIRM)) {
            util.Audio.play(util.Audio.SFX_SELECT);
            switch (selected) {
                case 0 -> { G.myfight = 0; enterFightBar(); }
                case 1 -> enterPanel(Phase.ACT);
                case 2 -> enterPanel(Phase.ITEM);
                case 3 -> enterPanel(Phase.MERCY);
                default -> { }
            }
        }
    }

    private void updateList(int count, java.util.function.IntConsumer confirm) {
        int n = Math.max(1, count);
        if (input.pressed(Key.DOWN)) {
            int prev = selected;
            selected = Math.min(selected + 1, n - 1);
            if (selected != prev) {
                util.Audio.play(util.Audio.SFX_MOVE);
            }
        } else if (input.pressed(Key.UP)) {
            int prev = selected;
            selected = Math.max(selected - 1, 0);
            if (selected != prev) {
                util.Audio.play(util.Audio.SFX_MOVE);
            }
        }
        if (input.pressed(Key.CONFIRM)) {
            util.Audio.play(util.Audio.SFX_SELECT);
            confirm.accept(selected);
        } else if (input.pressed(Key.CANCEL)) {
            util.Audio.play(util.Audio.SFX_MOVE);
            enterPanel(Phase.MAIN);
        }
    }

    private void confirmAct(int index) {
        G.myfight = 2;
        act.choose(index);   // routes to boss.onAct, which sets the result line
        enterResult();
    }

    private void confirmMercy(int index) {
        if (index == 0) {
            onSpare.run();   // sets the boss's result line or ends the battle
            enterResult();
        } else {
            G.myfight = 4;   // Flee — meaningless against a boss
            enterResult();
            localResult = "* You can't escape from this fight!";
        }
    }

    // ---- FIGHT attack bar ---------------------------------------------------

    private void enterFightBar() {
        phase = Phase.FIGHTBAR;
        fightPos = 0;
    }

    private void updateFightBar() {
        fightPos += FIGHT_SPEED;
        if (input.pressed(Key.CONFIRM)) {
            // Distance from the centre → accuracy (centre 1.0, edges 0.0).
            double acc = Math.max(0.0, 1.0 - Math.abs(fightPos - 0.5) * 2.0);
            resolveFight(acc);
        } else if (fightPos >= 1.0) {
            resolveFight(0.0); // the line ran off the end → a miss
        }
    }

    private void resolveFight(double accuracy) {
        onFight.accept(accuracy);
        enterResult();
    }

    // ---- Action result ("press to continue") --------------------------------

    /** Show the boss's result line, then hand the turn to the enemy on confirm. */
    private void enterResult() {
        phase = Phase.RESULT;
        localResult = null; // default: show the boss's flavor/result line
        resultNext = () -> G.mnfight = TurnManager.ENTER_ENEMY;
    }

    private void updateResult() {
        if (input.pressed(Key.CONFIRM)) {
            resultNext.run();
        }
    }

    private void enterPanel(Phase p) {
        phase = p;
        selected = 0;
    }

    // ---- Rendering ----------------------------------------------------------

    @Override
    public void render(Graphics2D g) {
        if (G.mnfight != TurnManager.MENU) {
            return;
        }
        renderButtons(g);
        g.setFont(BOX_FONT);
        switch (phase) {
            case ACT -> renderList(g, act.optionLabels());
            case MERCY -> renderList(g, List.of(MERCY_OPTIONS));
            case ITEM -> renderList(g, List.of("(no items)"));
            case MAIN -> renderFlavor(g, flavor.get());
            case FIGHTBAR -> renderFightBar(g);
            case RESULT -> renderResult(g);
        }
    }

    /** GML: draw the FIGHT/ACT/ITEM/MERCY button sprites with the heart cursor. */
    private void renderButtons(Graphics2D g) {
        boolean onMain = phase == Phase.MAIN;
        for (int i = 0; i < buttonCount(); i++) {
            boolean sel = onMain && i == selected;
            BufferedImage img = Assets.sprite(sel ? BUTTON_SEL[i] : BUTTON_IDLE[i]);
            if (img != null) {
                g.drawImage(img, BTN_X[i], BTN_Y, BTN_W, BTN_H, null);
            } else {
                g.setColor(sel ? Color.YELLOW : new Color(0xFF, 0x99, 0x00));
                g.drawRect(BTN_X[i], BTN_Y, BTN_W, BTN_H);
                g.drawString(MAIN_BUTTONS[i], BTN_X[i] + 30, BTN_Y + 27);
            }
            if (sel) {
                drawCursor(g, BTN_X[i] - 22, BTN_Y + BTN_H / 2 - 8);
            }
        }
    }

    /** GML: list the sub-menu options inside the box, heart cursor on the choice. */
    private void renderList(Graphics2D g, List<String> labels) {
        double[] b = G.idealborder;
        int textX = (int) b[0] + 70;          // room for the heart cursor at left
        int top = (int) b[2] + 30;
        // Tighten the spacing when there are enough options to overflow the box
        // (Mettaton EX has four ACT options: Check / Pose / Boast / Heel Turn).
        int lineH = labels.size() > 3 ? 28 : 34;
        for (int i = 0; i < labels.size(); i++) {
            int y = top + i * lineH;
            if (i == selected) {
                drawCursor(g, (int) b[0] + 36, y - 14);
            }
            g.setColor(Color.WHITE);
            g.drawString("* " + labels.get(i), textX, y);
        }
    }

    /** GML: the MAIN panel shows the boss's narration line inside the box. */
    private void renderFlavor(Graphics2D g, String text) {
        drawBoxText(g, text);
    }

    /** RESULT: the action's outcome text, with a hint to press to continue. */
    private void renderResult(Graphics2D g) {
        String text = localResult != null ? localResult : flavor.get();
        drawBoxText(g, text);
    }

    /** Word-wrapped white text inside the box (shared by flavor/result). */
    private void drawBoxText(Graphics2D g, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        double[] b = G.idealborder;
        int textX = (int) b[0] + 70;
        int top = (int) b[2] + 38;
        int lineH = 30;
        int maxChars = Math.max(8, (int) ((b[1] - b[0] - 90) / 13));
        g.setColor(Color.WHITE);
        int line = 0;
        for (String row : wrap(text, maxChars)) {
            g.drawString(row, textX, top + line * lineH);
            line++;
        }
    }

    /** GML: the FIGHT attack target with the sweeping line; press to strike. */
    private void renderFightBar(Graphics2D g) {
        double[] b = G.idealborder;
        int barX = (int) b[0] + 24;
        int barW = (int) (b[1] - b[0]) - 48;
        int barCy = (int) ((b[2] + b[3]) / 2);
        int barH = 64;

        BufferedImage target = Assets.sprite("spr_target");
        if (target != null) {
            g.drawImage(target, barX, barCy - barH / 2, barW, barH, null);
        } else {
            g.setColor(new Color(0x40, 0x40, 0x40));
            g.fillRect(barX, barCy - barH / 2, barW, barH);
            g.setColor(Color.GREEN); // centre "bullseye" zone
            g.fillRect(barX + barW / 2 - 6, barCy - barH / 2, 12, barH);
        }

        int lineX = barX + (int) (fightPos * barW);
        BufferedImage choice = Assets.sprite("spr_targetchoice_0");
        if (choice != null) {
            g.drawImage(choice, lineX - 3, barCy - barH / 2 - 6, 6, barH + 12, null);
        } else {
            g.setColor(Color.WHITE);
            g.fillRect(lineX - 2, barCy - barH / 2 - 6, 4, barH + 12);
        }
    }

    /** Draw the red heart selection cursor (sprite, or a vector fallback). */
    private void drawCursor(Graphics2D g, int x, int y) {
        BufferedImage heart = Assets.sprite("spr_heart_1");
        if (heart != null) {
            g.drawImage(heart, x, y, 16, 16, null);
        } else {
            g.setColor(Color.RED);
            g.fillRect(x, y, 16, 16);
        }
    }

    /** Greedy word-wrap so flavor/result text fits inside the box width. */
    private static List<String> wrap(String text, int maxChars) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            if (cur.length() > 0 && cur.length() + 1 + word.length() > maxChars) {
                lines.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) {
                cur.append(' ');
            }
            cur.append(word);
        }
        if (cur.length() > 0) {
            lines.add(cur.toString());
        }
        return lines;
    }
}
