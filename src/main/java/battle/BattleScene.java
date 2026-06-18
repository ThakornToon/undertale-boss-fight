package battle;

import boss.Boss;
import boss.BossRegistry;
import core.EntityManager;
import core.Game;
import core.GlobalState;
import core.InputHandler;
import core.InputHandler.Key;
import core.Scene;
import core.TurnManager;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * The active fight. Wires the Core battle engine together (architecture §3) and
 * runs the per-frame update order, then hands the {@code mnfight} turn machine to
 * {@link TurnManager}. The boss fills the SPI hooks; this scene only plumbs Core to
 * the boss and feeds the soul its input each frame.
 *
 * <p>Frame order matches the architecture's execution flow: begin-step → entities
 * update (boss, body, bullets, soul, board, menu) → {@code turns.tick()} →
 * {@code damage.resolve()} → end-step → flush.
 *
 * // GML: the battle room loop (obj_battle* coordinating mnfight)
 */
public final class BattleScene implements Scene {

    private static final GlobalState G = GlobalState.get();

    /** Frames the end-of-fight outcome lingers before auto-returning to the menu. */
    private static final int END_HOLD = 150; // ~5 s at 30 FPS

    /** The fallen human's name shown on the status row (no name-entry in the rush). */
    private static final String PLAYER_NAME = "FRISK";

    private final Game game;
    private final InputHandler input;
    private final EntityManager entities = new EntityManager();
    private final TurnManager turns = new TurnManager();
    private final DamageSystem damage = new DamageSystem();
    private final MercySystem mercy = new MercySystem();
    private final ActDispatcher act = new ActDispatcher();

    private final BulletBoard board;
    private final Soul soul;
    private final BattleMenu menu;
    private final Boss boss;

    private int prevMnfight = TurnManager.SETUP;
    /** Counts up once the fight is over, driving the return to the boss-select menu. */
    private int endTimer;

    // ---- Player FIGHT attack animation (GML: obj_slice / spr_strike) -----------
    /** Current frame of the strike overlay; -1 = not playing. */
    private int strikeFrame = -1;
    private int strikeTimer = 0;
    private static final int STRIKE_FRAMES = 6;
    private static final int STRIKE_TICKS  = 3; // game ticks per animation frame
    /** Draw the strike sprite at this scale (GML: image_xscale ≈ 1.5–3.5). */
    private static final int STRIKE_SCALE  = 4; // 14×12 → 56×48 px

    // ---- Player-death animation (GML: the soul shatters, then GAME OVER) -------
    /** 0 none · 1 the heart cracks · 2 the shards scatter and fall. */
    private int deathPhase;
    private int deathTimer;
    private double deathX;
    private double deathY;
    /** Each shard: {x, y, vx, vy, spriteFrame}. */
    private final java.util.List<double[]> shards = new java.util.ArrayList<>();
    private static final int BREAK_FRAMES = 34;   // the cracked heart holds
    private static final int SHATTER_FRAMES = 60;  // the shards fall, then menu

    public BattleScene(Game game, InputHandler input, int battlegroup) {
        this.game = game;
        this.input = input;

        this.board = new BulletBoard(entities);
        this.soul = new Soul(entities);
        this.menu = new BattleMenu(entities, input, act);

        // Build the boss on the scene's own instance list so the bones it spawns
        // land in this manager (GML: all instances share one room).
        this.boss = BossRegistry.create(battlegroup, entities);

        // Boss gets references to the systems it configures (stats/box/soul/mercy).
        boss.attach(soul, board, damage, mercy, act);

        // ACT options route to the boss's ACT graph (GML: whatiheard dispatch).
        act.onAct = boss::onAct;

        wireTurnHooks();
        wireMenuHooks();

        // Build the instance list (GML instance_create order ~ draw/update order).
        entities.add(board);
        entities.add(boss);
        entities.add(soul);
        entities.add(menu);
        entities.flush();

        G.enterBattle(battlegroup);
        damage.reset();
        boss.setup();      // creates and registers the boss body
        // GML: Asgore destroys the SPARE button (broken mercy) — drop it from the row.
        menu.mercyHidden = mercy.isBrokenMercy();
        entities.flush();

        // Start the boss theme on the single music channel (Sans self-manages its
        // own ramped music, so it returns null here). Stopped in exit().
        if (boss.musicPath() != null) {
            util.Audio.loop(boss.musicPath());
        }
    }

    /** Scene torn down (returning to the menu / player died): silence the music. */
    @Override
    public void exit() {
        util.Audio.stopMusic();
    }

    private void wireTurnHooks() {
        turns.introComplete = boss::introComplete;
        turns.chooseAttack = boss::chooseAttack;
        turns.onSpecial = boss::onSpecial;
        turns.resolveActOrSpare = this::resolveActOrSpare;
    }

    private void wireMenuHooks() {
        // FIGHT: deal the player's hit using the attack-bar accuracy. The menu then
        // shows the result text and only hands the turn to the enemy on confirm.
        menu.onFight = accuracy -> {
            int slot = G.myself;
            int dealt = damage.monsterHurt(slot, accuracy);
            if (accuracy > 0) {
                util.Audio.play(util.Audio.SFX_DAMAGE);   // the strike lands
                strikeFrame = 0;
                strikeTimer = 0;
            }
            boss.onDamaged();
            if (damage.isMonsterDefeated(slot)) {
                // The boss ends the fight itself: instantly (default), or after a
                // death cutscene it drives in its own update() (Papyrus's decap).
                boss.onDefeat();
                return;
            }
            boss.message = accuracy <= 0
                    ? "* You miss!"
                    : "* You strike! (" + dealt + " dmg)";
        };
        // ITEM: no inventory in the boss rush — just narrate and spend the turn.
        menu.onItem = () -> boss.message = "* You have no items to use.";
        // MERCY → Spare: accept if the boss can be spared, else they block the way.
        menu.onSpare = () -> {
            if (mercy.canSpare()) {
                boss.onSpare();   // ends the battle (battleOver + banner)
            } else {
                boss.message = "* " + boss.stats.name + " blocks the way!";
            }
        };
        // The MAIN/RESULT panels show the boss's current narration line in the box.
        menu.flavor = () -> boss.message;
    }

    /** TurnManager mnfight==4: resolve the chosen ACT or SPARE, then proceed. */
    private void resolveActOrSpare() {
        if (G.myfight == 4) {
            // MERCY → Spare.
            if (mercy.canSpare()) {
                boss.onSpare();
                return; // boss freezes the loop (battleOver)
            }
            boss.message = "* " + boss.stats.name + " blocks the way!";
        }
        // ACT finished (or spare refused): the enemy now attacks.
        G.mnfight = TurnManager.ENTER_ENEMY;
    }

    // ---- Scene -------------------------------------------------------------

    /** Test/host accessor: the active boss controller. */
    public Boss boss() {
        return boss;
    }

    /** Test/host accessor: live count of active bullets on screen. */
    public int bulletCount() {
        return entities.count(bullet.Bullet.class);
    }

    /** ESC pause overlay: freezes the fight; R returns to menu, M toggles mute. */
    private boolean paused;

    @Override
    public void update() {
        // (Mute/M is handled globally in Game.tick so it works on every screen.)
        // ESC toggles the pause overlay; while paused everything is frozen and only
        // R (back to boss select) and M (mute) respond.
        if (input.pressed(Key.PAUSE)) {
            paused = !paused;
        }
        if (paused) {
            if (input.pressed(Key.RESTART) && game != null) {
                game.setScene(new BossSelectMenu(game)); // R → boss-select menu
            }
            return;
        }

        // The player died: everything else freezes while the soul shatters.
        if (deathPhase != 0) {
            stepDeath();
            return;
        }
        if (boss.battleOver) {
            // The outcome banner draws its own framed box, so silence the combat
            // board's border to avoid a doubled frame behind it.
            board.visible = false;
            // Hold on the outcome, then return to the boss-select menu — either when
            // the player presses Z or after the banner has had its moment on screen.
            endTimer++;
            if (game != null && (endTimer > END_HOLD || input.pressed(Key.CONFIRM))) {
                game.setScene(new BossSelectMenu(game)); // back to boss select
            }
            return;
        }
        if (G.hp <= 0) {
            startDeath();
            return;
        }

        if (strikeFrame >= 0) {
            if (++strikeTimer >= STRIKE_TICKS) {
                strikeTimer = 0;
                if (++strikeFrame >= STRIKE_FRAMES) {
                    strikeFrame = -1;
                }
            }
        }

        feedSoulInput();

        // The box widens into the menu/dialogue box on the player turn and the
        // combat heart hides (the menu draws its own cursor); both revert for the
        // enemy turn so bullets fly in the tight combat box.
        boolean playerMenu = G.mnfight == TurnManager.MENU;
        boolean cutscene = G.mnfight < 0;   // death / transform: no menu, no soul
        boolean setupTalk = G.mnfight == TurnManager.SETUP; // boss intro speech
        board.menuMode = playerMenu;
        soul.hidden = playerMenu || cutscene || setupTalk;

        entities.beginStepAll();
        entities.updateAll();
        // The menu may have just ended the player turn; snap the box to the combat
        // size before TurnManager.chooseAttack spawns this turn's bullets.
        if (G.mnfight == TurnManager.ENTER_ENEMY) {
            board.snapToCombat();
        }
        turns.tick();
        damage.resolve();
        damage.endStep();
        entities.endStepAll();
        entities.flush();

        onMnfightChanged();
    }

    /** The soul only takes movement input during the enemy turn. */
    private void feedSoulInput() {
        boolean enemyTurn = G.mnfight == TurnManager.ENEMY_TURN;
        soul.leftHeld = enemyTurn && input.held(Key.LEFT);
        soul.rightHeld = enemyTurn && input.held(Key.RIGHT);
        soul.upHeld = enemyTurn && input.held(Key.UP);
        soul.downHeld = enemyTurn && input.held(Key.DOWN);
        // Edge-triggered up/down for the purple web soul's one-strand-at-a-time hop.
        soul.upPressed = enemyTurn && input.pressed(Key.UP);
        soul.downPressed = enemyTurn && input.pressed(Key.DOWN);
        soul.confirmPressed = input.pressed(Key.CONFIRM);
        // The yellow shoot soul fires while Z is held during the enemy turn.
        soul.confirmHeld = enemyTurn && input.held(Key.CONFIRM);
    }

    // ---- Player-death animation ---------------------------------------------

    /** GML: the soul is dead — freeze the fight and crack the heart in place. */
    private void startDeath() {
        deathPhase = 1;
        deathTimer = 0;
        deathX = soul.x;
        deathY = soul.y;
        shards.clear();
        util.Audio.stopMusic();                       // the music cuts on death
        util.Audio.play("/audio/snd_heartshot.wav");  // GML: the soul shatters
    }

    /** Drive the crack → shatter → return-to-menu sequence. */
    private void stepDeath() {
        deathTimer++;
        if (deathPhase == 1) {
            if (deathTimer >= BREAK_FRAMES) {
                spawnShards();
                deathPhase = 2;
                deathTimer = 0;
            }
        } else if (deathPhase == 2) {
            for (double[] s : shards) {
                s[0] += s[2];
                s[1] += s[3];
                s[3] += 0.45;   // gravity pulls the pieces down
            }
            if (deathTimer >= SHATTER_FRAMES && game != null) {
                game.setScene(new BossSelectMenu(game));
            }
        }
    }

    /** GML: the cracked heart bursts into six shards flung up and outward. */
    private void spawnShards() {
        int n = 6;
        for (int i = 0; i < n; i++) {
            double ang = 245 - i * (110.0 / (n - 1));      // a fan from up-left to up-right
            double sp = 3.0 + Math.random() * 2.0;
            double vx = Math.cos(Math.toRadians(ang)) * sp;
            double vy = -Math.abs(Math.sin(Math.toRadians(ang)) * sp) - 1.5;
            shards.add(new double[] { deathX, deathY, vx, vy, i % 4 });
        }
    }

    /** GML: obj_slice draw — spr_strike overlay centered on the boss sprite. */
    private void renderStrike(Graphics2D g) {
        if (strikeFrame < 0) {
            return;
        }
        java.awt.image.BufferedImage img =
                util.Assets.sprite("spr_strike_" + strikeFrame);
        if (img == null) {
            return;
        }
        int w = img.getWidth()  * STRIKE_SCALE;
        int h = img.getHeight() * STRIKE_SCALE;
        int cx = Game.WIDTH / 2;
        int cy = 120; // GML: mons.y — upper centre where the boss sprite sits
        g.drawImage(img, cx - w / 2, cy - h / 2, w, h, null);
    }

    /** The death scene: black screen, the breaking heart, then the falling shards. */
    private void renderDeath(Graphics2D g) {
        if (deathPhase == 1) {
            java.awt.image.BufferedImage brk = util.Assets.sprite("spr_heartbreak_0");
            if (brk != null) {
                g.drawImage(brk, (int) (deathX - 10), (int) (deathY - 8), 20, 16, null);
            } else {
                g.setColor(Color.RED);
                g.fillRect((int) (deathX - 8), (int) (deathY - 8), 16, 16);
            }
        } else {
            for (double[] s : shards) {
                java.awt.image.BufferedImage img =
                        util.Assets.sprite("spr_heartshards_" + (int) s[4]);
                if (img != null) {
                    g.drawImage(img, (int) s[0], (int) s[1],
                            img.getWidth() * 2, img.getHeight() * 2, null);
                } else {
                    g.setColor(Color.RED);
                    g.fillRect((int) s[0], (int) s[1], 6, 6);
                }
            }
        }
    }

    /** Reset the menu when control returns to the player. */
    private void onMnfightChanged() {
        if (G.mnfight != prevMnfight) {
            if (G.mnfight == TurnManager.MENU) {
                menu.reset();
                boss.dialogue = ""; // boss stops talking once it's the player's turn
            }
            prevMnfight = G.mnfight;
        }
    }

    @Override
    public void render(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, Game.WIDTH, Game.HEIGHT);
        // On death everything goes black and still — only the breaking heart shows.
        if (deathPhase != 0) {
            renderDeath(g);
            return;
        }
        entities.renderAll(g);
        renderStrike(g);
        renderUi(g);
        if (paused) {
            renderPauseOverlay(g);
        }
    }

    /** The ESC pause screen: dim the fight and show the pause controls. */
    private void renderPauseOverlay(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(0, 0, Game.WIDTH, Game.HEIGHT);

        g.setColor(Color.WHITE);
        g.setFont(util.Fonts.title(34f));
        drawCentered(g, "PAUSED", 180);

        g.setFont(util.Fonts.ui(20f));
        drawCentered(g, "ESC  -  Resume", 250);
        drawCentered(g, "R  -  Return to boss select", 285);
        drawCentered(g, "M  -  Sound: " + (util.Audio.isMuted() ? "OFF" : "ON"), 320);
    }

    /** Center a string horizontally at baseline y. */
    private void drawCentered(Graphics2D g, String text, int y) {
        int w = g.getFontMetrics().stringWidth(text);
        g.drawString(text, (Game.WIDTH - w) / 2, y);
    }

    private void renderUi(Graphics2D g) {
        g.setFont(util.Fonts.ui(14f));

        // The white speech bubble shows ONLY the boss's spoken line (boss.dialogue),
        // never notifications/flavor (boss.message). Notifications live in the menu
        // box during the player turn instead.
        if (boss.dialogue != null && !boss.dialogue.isEmpty() && !boss.battleOver) {
            renderSpeechBubble(g, boss.dialogue);
        }

        // Status row, sitting in the gap between the box and the button row (like the
        // real game's "FAS  LV 1     HP [bar] 05 / 20"). The player name + LV anchor
        // the left so the HP block no longer hugs the box edge on an empty row.
        int statusY = 410;
        g.setFont(util.Fonts.ui(16f));
        g.setColor(Color.WHITE);
        g.drawString(PLAYER_NAME, 60, statusY);
        g.drawString("LV " + G.lv, 175, statusY);

        // HP label + fixed-width bar with a proportional fill (any max HP 20 … 999).
        // Dark "missing HP" track, then the yellow current-HP fill over it.
        g.setFont(util.Fonts.ui(14f));
        int barX = 320;
        java.awt.image.BufferedImage hpName = util.Assets.sprite("spr_hpname");
        if (hpName != null) {
            g.drawImage(hpName, barX - 50, statusY - 13, 46, 20, null);
        } else {
            g.setColor(Color.WHITE);
            g.drawString("HP", barX - 50, statusY);
        }
        int barW = Math.min(120, Math.max(60, G.maxhp));
        double frac = G.maxhp > 0 ? Math.max(0, G.hp) / (double) G.maxhp : 0;
        g.setColor(new Color(0x80, 0x00, 0x00));
        g.fillRect(barX, statusY - 11, barW, 14);
        g.setColor(Color.YELLOW);
        int hpW = (int) Math.round(barW * frac);
        g.fillRect(barX, statusY - 11, hpW, 14);
        // KARMA (Sans): the top of the current HP shows magenta — the part the
        // poison is about to eat.
        int kr = Math.min(G.km, Math.max(0, G.hp));
        if (kr > 0) {
            int krW = Math.min(hpW, (int) Math.round(barW * (kr / (double) Math.max(1, G.maxhp))));
            g.setColor(new Color(0xFF, 0x00, 0xFF));
            g.fillRect(barX + hpW - krW, statusY - 11, krW, 14);
        }
        g.setColor(kr > 0 ? new Color(0xFF, 0x00, 0xFF) : Color.WHITE);
        g.drawString(G.hp + " / " + G.maxhp, barX + barW + 10, statusY);
        if (kr > 0) {
            g.drawString("KR", barX + barW + 110, statusY);
        }

        // Monster HP header (suppressed when the boss draws its own meter, e.g. EX).
        if (!boss.hideHpHeader) {
            g.drawString(boss.stats.name + "  " + Math.max(0, G.monsterhp[G.myself]) + " HP", 40, 30);
        }

        // End-of-fight banner — always rendered as a notification inside the combat
        // box (a spare win, a wake-up, or any other outcome), never floating over
        // the HUD where it would collide with the status row.
        if (boss.battleOver && boss.banner != null && !boss.banner.isEmpty()) {
            renderEndTextInBox(g, boss.banner);
        }
    }

    /** The wide command box the menu uses (BulletBoard.MENU_BOX); the banner reuses it. */
    private static final int[] BANNER_BOX = { 40, 250, 560, 135 }; // x, y, w, h

    /**
     * Render the end-of-fight outcome the way the menu shows a narration line: a
     * clean white-bordered box with white "* " text wrapped inside it (matching the
     * normal message box), rather than yellow text floating over the HUD.
     */
    private void renderEndTextInBox(Graphics2D g, String text) {
        int x = BANNER_BOX[0];
        int y = BANNER_BOX[1];
        int w = BANNER_BOX[2];
        int h = BANNER_BOX[3];

        // The box border, drawn like BulletBoard's 5px white frame.
        java.awt.Stroke old = g.getStroke();
        g.setStroke(new java.awt.BasicStroke(5f));
        g.setColor(Color.WHITE);
        g.drawRect(x, y, w, h);
        g.setStroke(old);

        // The text inside, in the menu's box font, left-aligned with a "* " bullet.
        g.setFont(util.Fonts.ui(22f));
        g.setColor(Color.WHITE);
        java.awt.FontMetrics fm = g.getFontMetrics();
        int lineH = 30;
        String body = text.startsWith("*") ? text : "* " + text;
        int textX = x + 36;
        int top = y + 44;
        int maxChars = Math.max(8, (w - 72) / fm.charWidth('M'));
        int line = 0;
        for (String row : wrapText(body, maxChars)) {
            g.drawString(row, textX, top + line * lineH);
            line++;
        }
    }

    /**
     * Draw the boss's line as an Undertale speech bubble: a white rounded panel
     * with black text and a little tail pointing left toward the boss sprite.
     */
    private void renderSpeechBubble(Graphics2D g, String text) {
        g.setFont(util.Fonts.ui(18f));
        java.awt.FontMetrics fm = g.getFontMetrics();
        int pad = 14;
        int lineH = fm.getHeight();
        int bubbleX = 360;
        int bubbleY = 40;
        int maxChars = 22;
        java.util.List<String> lines = wrapText(text, maxChars);

        int textW = 0;
        for (String line : lines) {
            textW = Math.max(textW, fm.stringWidth(line));
        }
        int w = textW + pad * 2;
        int h = lines.size() * lineH + pad * 2;
        if (bubbleX + w > Game.WIDTH - 10) {
            bubbleX = Game.WIDTH - 10 - w; // keep it on screen for long lines
        }

        // Tail pointing toward the boss (to the left of the bubble).
        g.setColor(Color.WHITE);
        g.fillPolygon(
                new int[] { bubbleX, bubbleX - 18, bubbleX },
                new int[] { bubbleY + 24, bubbleY + 34, bubbleY + 50 }, 3);
        // Bubble body.
        g.fillRoundRect(bubbleX, bubbleY, w, h, 18, 18);
        g.setColor(Color.BLACK);
        g.setStroke(new java.awt.BasicStroke(2f));
        g.drawRoundRect(bubbleX, bubbleY, w, h, 18, 18);

        int ty = bubbleY + pad + fm.getAscent();
        for (String line : lines) {
            g.drawString(line, bubbleX + pad, ty);
            ty += lineH;
        }
    }

    /** Greedy word-wrap so a bubble line fits the panel width. */
    private static java.util.List<String> wrapText(String text, int maxChars) {
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
