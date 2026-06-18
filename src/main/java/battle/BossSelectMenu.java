package battle;

import boss.BossRegistry;
import core.Game;
import core.GlobalState;
import core.InputHandler;
import core.InputHandler.Key;
import core.Scene;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * The title / boss-select screen (architecture: {@code BossSelectMenu}). Because
 * this port has "no story", the menu is where run state is <em>seeded</em> instead
 * of being earned through a playthrough (README Core Requirement 11): the player
 * picks the boss and dials in their own stats before the fight starts.
 *
 * <p>Adjustable rows:
 * <ul>
 *   <li><b>BOSS</b> — which fight to start (only Papyrus is implemented yet).</li>
 *   <li><b>MAX HP</b> — starting max HP, with realistic LV presets (20 = LV1 … 99 =
 *       LV20) plus a sandbox 999.</li>
 *   <li><b>MODE</b> — <b>NORMAL</b> or <b>GENOCIDE</b>. GENOCIDE seeds a high murder
 *       level ({@code global.murderlv}) so bosses fall in one hit and switch to their
 *       genocide behaviour and dialogue (e.g. Papyrus's "I believe in you" speech),
 *       and buffs the player's stats accordingly.</li>
 * </ul>
 *
 * <p>Controls: Up/Down pick a row, Left/Right change its value, Z starts the fight.
 *
 * // GML: there was no such menu (the route/stats came from the save file); this is
 * // the boss-rush stand-in for scr_battlegroup + the persistent flags it relied on.
 */
public final class BossSelectMenu implements Scene {

    private static final GlobalState G = GlobalState.get();

    /** Realistic LV→HP presets (UT: maxhp = 16 + 4·LV up to LV19, 99 at LV20) + sandbox. */
    private static final int[] MAX_HP = { 20, 24, 32, 44, 60, 76, 92, 99, 999 };

    /** GENOCIDE seeds these stats; NORMAL is the LV1 neutral baseline. */
    private static final int GENOCIDE_LV = 20;
    private static final int GENOCIDE_AT = 40;

    // Mettaton is one entry with two routes: NORMAL → EX, GENOCIDE → NEO (resolved
    // in startFight). The group here is the NORMAL (EX) default.
    private static final String[] BOSSES =
            { "PAPYRUS", "SANS", "ASGORE", "UNDYNE", "METTATON", "MUFFET" };
    private static final int[] BOSS_GROUPS = {
        BossRegistry.PAPYRUS, BossRegistry.SANS, BossRegistry.ASGORE, BossRegistry.UNDYNE,
        BossRegistry.METTATON_EX, BossRegistry.MUFFET,
    };

    private static final int ROW_BOSS = 0;
    private static final int ROW_HP = 1;
    private static final int ROW_MODE = 2;
    private static final int ROW_COUNT = 3;

    private final Game game;
    private final InputHandler input;

    private int row;
    private int bossIdx;
    private int hpIdx = 0;          // default 20 HP (LV1 — realistic)
    private boolean genocideMode;   // default false (NORMAL / neutral route)

    public BossSelectMenu(Game game) {
        this.game = game;
        this.input = game.input();
    }

    @Override
    public void enter() {
        G.newGame();           // start every visit from a clean slate
        util.Audio.loop("/audio/mus_menu6.ogg");   // title-screen theme
    }

    // No exit() override: the next scene's own Audio.loop() replaces this track.
    // (Stopping it here would kill the boss music the incoming BattleScene starts in
    //  its constructor — which runs before setScene calls this scene's exit().)

    @Override
    public void update() {
        if (input.pressed(Key.UP)) {
            row = (row + ROW_COUNT - 1) % ROW_COUNT;
            util.Audio.play(util.Audio.SFX_MOVE);
        }
        if (input.pressed(Key.DOWN)) {
            row = (row + 1) % ROW_COUNT;
            util.Audio.play(util.Audio.SFX_MOVE);
        }
        if (input.pressed(Key.LEFT)) {
            change(-1);
            util.Audio.play(util.Audio.SFX_MOVE);
        }
        if (input.pressed(Key.RIGHT)) {
            change(+1);
            util.Audio.play(util.Audio.SFX_MOVE);
        }
        if (input.pressed(Key.CONFIRM)) {
            util.Audio.play(util.Audio.SFX_SELECT);
            startFight();
        }
    }

    private void change(int dir) {
        // All rows wrap around at the ends (past the last option loops to the first),
        // like the MODE toggle — no dead stops at the edges.
        switch (row) {
            case ROW_BOSS -> bossIdx = wrap(bossIdx + dir, BOSSES.length);
            case ROW_HP -> hpIdx = wrap(hpIdx + dir, MAX_HP.length);
            case ROW_MODE -> genocideMode = !genocideMode;
            default -> { }
        }
    }

    /** Wrap an index into [0, n) so it loops front↔back. */
    private static int wrap(int v, int n) {
        return ((v % n) + n) % n;
    }

    /** Seed the chosen run state into {@link GlobalState}, then enter the battle. */
    private void startFight() {
        G.newGame();
        int maxhp = MAX_HP[hpIdx];
        G.maxhp = maxhp;
        G.hp = maxhp;
        // GENOCIDE seeds a high murder level (the route gate, ≥ 7) and a matching
        // LOVE/ATK buff so bosses fall in one hit, exactly as a real genocide run
        // does. NORMAL stays at the LV1 neutral baseline (chip damage floors at 1).
        if (genocideMode) {
            G.murderlv = GENOCIDE_LV;
            G.lv = GENOCIDE_LV;
            G.at = GENOCIDE_AT;
            G.osflavor = 1;
        } else {
            G.murderlv = 0;
            G.lv = 1;
            G.at = 0;
            G.osflavor = 0;
        }

        // Mettaton's route: GENOCIDE → NEO (the one-shot cutscene), NORMAL → EX.
        int group = BOSS_GROUPS[bossIdx];
        if (group == BossRegistry.METTATON_EX && genocideMode) {
            group = BossRegistry.METTATON_NEO;
        }

        game.setScene(new BattleScene(game, input, group));
    }

    private boolean genocide() {
        return genocideMode;
    }

    // ---- Render ------------------------------------------------------------

    @Override
    public void render(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, Game.WIDTH, Game.HEIGHT);

        g.setColor(Color.WHITE);
        g.setFont(util.Fonts.title(40f));
        drawCentered(g, "UNDERTALE", 100);
        g.setFont(util.Fonts.title(17f));
        drawCentered(g, "BOSS FIGHT", 140);

        int x = 150;
        int y = 205;
        int step = 56;
        drawRow(g, ROW_BOSS, x, y,            "BOSS",   BOSSES[bossIdx]);
        drawRow(g, ROW_HP,   x, y + step,     "MAX HP", Integer.toString(MAX_HP[hpIdx]));
        drawRow(g, ROW_MODE, x, y + step * 2, "MODE",   routeLabel());

        // Route note.
        g.setFont(util.Fonts.ui(18f));
        g.setColor(genocide() ? new Color(0xFF, 0x40, 0x40) : new Color(0x80, 0x80, 0x80));
        drawCentered(g, genocide()
                ? "* The murderer's route. Bosses fall in one hit and speak differently."
                : "* The neutral route. Bosses fight you normally.", y + step * 3 + 12);

        // Two control-hint rows: the menu keys, then the in-battle keys.
        g.setFont(util.Fonts.ui(17f));
        g.setColor(new Color(0xC0, 0xC0, 0xC0));
        drawCentered(g, "Up/Down: Select     Left/Right: Change     Z: Fight", Game.HEIGHT - 52);
        g.setColor(new Color(0x90, 0x90, 0x90));
        drawCentered(g, "In battle:  ESC Pause     R Boss Select (while paused)     M Mute Sound",
                Game.HEIGHT - 26);
    }

    private void drawRow(Graphics2D g, int rowId, int x, int y, String label, String value) {
        boolean sel = row == rowId;
        g.setFont(util.Fonts.ui(28f));
        // Heart cursor on the selected row.
        if (sel) {
            java.awt.image.BufferedImage heart = util.Assets.sprite("spr_heart_1");
            if (heart != null) {
                g.drawImage(heart, x - 42, y - 20, 22, 22, null);
            } else {
                g.setColor(Color.RED);
                g.fillRect(x - 40, y - 18, 18, 18);
            }
        }
        g.setColor(sel ? Color.YELLOW : Color.WHITE);
        g.drawString(label, x, y);

        int valX = x + 260;
        g.drawString(value, valX, y);
        // Crisp triangle ◄ ► flanking the value while this row is selected, so it's
        // obvious which row Left/Right changes.
        if (sel) {
            int vw = g.getFontMetrics().stringWidth(value);
            int aw = 13;
            int ah = 22;
            int cy = y - 9;
            g.setColor(Color.YELLOW);
            int lx = valX - 28;                       // left arrow, pointing left
            g.fillPolygon(new int[] { lx, lx + aw, lx + aw },
                    new int[] { cy, cy - ah / 2, cy + ah / 2 }, 3);
            int rx = valX + vw + 16;                  // right arrow, pointing right
            g.fillPolygon(new int[] { rx + aw, rx, rx },
                    new int[] { cy, cy - ah / 2, cy + ah / 2 }, 3);
        }
    }

    private void drawCentered(Graphics2D g, String text, int y) {
        int w = g.getFontMetrics().stringWidth(text);
        g.drawString(text, (Game.WIDTH - w) / 2, y);
    }

    private String routeLabel() {
        return genocide() ? "GENOCIDE" : "NORMAL";
    }
}
