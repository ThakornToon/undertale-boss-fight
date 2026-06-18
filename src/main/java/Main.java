import battle.BossSelectMenu;
import core.Game;

/**
 * Entry point for the Undertale boss-rush. Boots the {@link Game} host loop and
 * opens the boss-select menu, where the player picks the fight and seeds their own
 * stats (max HP, NORMAL/GENOCIDE mode) before the battle starts.
 *
 * <p>Menu controls: Up/Down select a row, Left/Right change it, Z starts the fight.
 *
 * <p>Battle controls: arrow keys move the SOUL (during the enemy turn), Z confirms,
 * X cancels. FIGHT/ACT/ITEM/MERCY navigate with Left/Right + Z; submenus with
 * Up/Down. Hold UP during a bone wave to jump the blue soul.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Game game = new Game();
        game.setScene(new BossSelectMenu(game));
        game.start("Undertale Boss Fight");
    }
}
