package boss;

import battle.SoulMode;
import core.EntityManager;
import core.TurnManager;
import java.awt.Graphics2D;
import java.util.List;

/**
 * Mettaton NEO (GML: {@code obj_mettaton_neo} + {@code obj_mneo_body}) — the
 * genocide-only, cutscene-only fight. NEO strikes a fabulous pose and is destroyed
 * in a single blow: he displays 90 ATK / 9 DEF but {@code mercymod = -999999} skips
 * his turn the instant it would begin (he never fires a bullet), and the player's
 * FIGHT is a forced one-shot kill ({@link battle.DamageSystem#forcedKill}).
 *
 * <p>On the killing blow he shudders, the face turns to {@code faceemotion 6}, and a
 * death monologue types out before he white-fades and explodes. The GML branches the
 * monologue on {@code murderlv} (≥ 15 → a short "fan club" line); we always play the
 * iconic long monologue the reference supplies (GML's {@code murderlv < 15} branch).
 *
 * // GML: obj_mettaton_neo (cutscene boss; forcedKill + instant turn-skip)
 */
public final class MettatonNeoBoss extends Boss {

    private MettatonNeoBody nbody;

    // ---- Death cutscene (GML: con 20 → 27) ---------------------------------
    private boolean dying;
    private int step;
    private int timer;
    private boolean exploding;

    /** GML faceemotion per line (\\E codes) — spr_mneo_face frames 0..6. */
    private static final int[] FACE = { 6, 6, 6, 4, 5, 6, 5, 1, 0, 0, 3 };
    private static final int[] LINE_FRAMES =
        { 70, 45, 70, 80, 60, 75, 75, 60, 80, 70, 95 };
    // The long monologue (GML murderlv < 15 branch) the user supplied. '&' = newline.
    private static final String[] MONOLOGUE = {
        "G... GUESS SHE SHOULD HAVE WORKED MORE ON THE DEFENSES...",
        "...",
        "YOU MAY HAVE DEFEATED ME... BUT...",
        "I KNOW. I CAN TELL FROM THAT STRIKE, DARLING.",
        "YOU WERE HOLDING BACK.",
        "YES, ASGORE WILL FALL EASILY TO YOU...",
        "BUT YOU WON'T HARM HUMANITY, WILL YOU?",
        "YOU AREN'T ABSOLUTELY EVIL.",
        "IF YOU WERE TRYING TO BE, THEN YOU MESSED UP.",
        "AND SO LATE INTO THE SHOW, TOO.",
        "HA... HA. AT LEAST NOW, I CAN REST EASY. KNOWING ALPHYS AND THE HUMANS WILL LIVE ON...!",
    };

    public MettatonNeoBoss(EntityManager manager) {
        // GML scr_monstersetup (monstertype): HP 30000, ATK 10, DEF -40000 — but he
        // displays "90 ATK / 9 DEF" on CHECK, and the forced one-shot kill makes the
        // actual numbers academic.
        super(manager, new Monster(0, "Mettaton NEO", 30000, 90, 9, 0));
    }

    // ---- Boss SPI ----------------------------------------------------------

    @Override
    public String musicPath() {
        return "/audio/mus_mettaton_neo.ogg";   // "Power of NEO"
    }

    @Override
    public void setup() {
        stats.setup();

        nbody = new MettatonNeoBody(manager);
        body = nbody;
        manager.add(nbody);

        // GML: mercymod = -999999 — his turn is cut the instant it begins, and the
        // mercy threshold can never be met (no spare). Set after reset(), which clears it.
        mercy.reset();
        G.mercymod = -999999;

        // GML: takedamage = monsterhp + 4000 + rand — the FIGHT always kills.
        damage.forcedKill = true;

        // RED soul (never actually used — he never attacks).
        soul.setMode(SoulMode.RED);
        board.setPreset(24, true);
        board.visible = false;
        soul.hidden = true;

        // ACT: only CHECK.
        act.setOptions(List.of("Check"), List.of(0));

        message = "* Mettaton NEO blocks the way!";
        turns = 0;
        // No intro cutscene — the menu opens straight away.
        G.mnfight = TurnManager.MENU;
        G.myfight = 0;
    }

    @Override
    public void chooseAttack() {
        // GML: mercymod == -999999 → turntimer = -1; mnfight = 3. There is no attack;
        // the enemy turn ends the moment it starts and control returns to the player.
        turns++;
        G.turntimer = 0;
        G.attacked = 1;
        message = "* Mettaton NEO is too busy posing to attack.";
    }

    @Override
    public void onAct(int whatiheard) {
        if (whatiheard == 0) {
            message = "* METTATON NEO - 90 ATK 9 DEF  * Dr. Alphys's greatest  invention.";
        } else {
            message = "* ...";
        }
    }

    @Override
    public void onDefeat() {
        if (dying) {
            return;
        }
        // GML: the killing blow — shudder, face 6, freeze the idle, begin the cutscene.
        stats.defeat();
        dying = true;
        step = 0;
        timer = 0;
        G.mnfight = -1;   // frozen cutscene
        G.myfight = -1;
        soul.hidden = true;
        board.visible = false;
        G.faceemotion = 6;
        nbody.pause = true;
        nbody.shake = true;
        dialogue = "";
        message = "";
    }

    @Override
    public void update() {
        if (!dying) {
            return;
        }
        timer++;

        // GML alarm[8] = 11: the shudder holds briefly before he speaks.
        if (step == 0) {
            if (timer >= 16) {
                nbody.shake = false;
                step = 1;
                timer = 0;
            }
            return;
        }

        int line = step - 1;
        if (line < MONOLOGUE.length) {
            if (timer == 1) {
                G.faceemotion = FACE[line];
                dialogue = MONOLOGUE[line];
            }
            boolean advance = soul.confirmPressed || timer >= LINE_FRAMES[line];
            if (advance) {
                step++;
                timer = 0;
            }
            return;
        }

        // GML con 24 → 26: the body white-fades and explodes.
        if (!exploding) {
            exploding = true;
            dialogue = "";
            nbody.fadewhite = true;
            G.xp += 10000;   // GML con 26 (murderlv < 15 branch)
            G.kills++;
            G.flag[425] = 1;
        }
        if (nbody.fadeComplete()) {
            battleOver = true;
            banner = "* Mettaton NEO was obliterated.";
        }
    }

    @Override
    public boolean introComplete() {
        return true;
    }

    @Override
    public void render(Graphics2D g) {
        // The body draws itself; nothing extra at the controller level.
    }
}
