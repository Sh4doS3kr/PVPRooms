package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Duel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages per-player scoreboards for queue and duel states.
 * Uses the Paper Adventure scoreboard API (Paper 1.21 compatible).
 *
 * Each scoreboard is rebuilt from scratch every time it is shown/updated.
 * Sidebar lines are implemented as score entries with spacer strings
 * to avoid duplicate entry collisions.
 */
public class ScoreboardManager {

    private final PvPRoomsPro plugin;

    /** Tracks which players currently have a custom scoreboard assigned */
    private final Map<UUID, Scoreboard> activeBoards = new HashMap<>();

    public ScoreboardManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ── Queue scoreboard ───────────────────────────────────────────────────

    /**
     * Shows the queue scoreboard to a player waiting for a match.
     */
    public void showQueueScoreboard(Player player, String kitName) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective(
                "pvpqueue",
                Criteria.DUMMY,
                comp("&c&lPvPRooms")
        );
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int line = 10;
        setLine(obj, " ", line--);
        setLine(obj, leg("&fKit: &e" + kitName), line--);
        setLine(obj, leg("&fELO: &e" + plugin.getEloManager().getElo(player.getUniqueId())), line--);
        setLine(obj, "  ", line--);
        setLine(obj, leg("&fQueue: &a" + plugin.getQueueManager().getTotalQueued()), line--);
        setLine(obj, "   ", line--);
        setLine(obj, leg("&7Searching..."), line--);
        setLine(obj, "    ", line--);

        player.setScoreboard(board);
        activeBoards.put(player.getUniqueId(), board);
    }

    // ── Duel scoreboard ────────────────────────────────────────────────────

    /**
     * Shows or refreshes the duel scoreboard for an active fight.
     */
    public void updateDuelScoreboard(Player player, Duel duel) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        if (player == null) return;

        UUID opponentUUID = duel.getOpponent(player.getUniqueId());
        Player opponent = opponentUUID != null ? Bukkit.getPlayer(opponentUUID) : null;
        String opponentName = opponent != null ? opponent.getName() : "Unknown";

        String titleCfg = plugin.getConfig().getString("scoreboard.title", "&c&lPvPRooms");

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("pvpduel", Criteria.DUMMY, comp(titleCfg));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int line = 10;
        setLine(obj, " ", line--);

        if (duel.getState() == Duel.State.COUNTDOWN) {
            setLine(obj, leg("&eWaiting..."), line--);
        } else {
            long elapsed = duel.getElapsedSeconds();
            String time = String.format("%d:%02d", elapsed / 60, elapsed % 60);
            setLine(obj, leg("&fTime: &e" + time), line--);
        }

        setLine(obj, "  ", line--);
        setLine(obj, leg("&fKit: &e" + duel.getKitName()), line--);
        setLine(obj, "   ", line--);
        setLine(obj, leg("&fOpponent: &c" + opponentName), line--);

        int myElo = plugin.getEloManager().getElo(player.getUniqueId());
        setLine(obj, leg("&fYour ELO: &e" + myElo), line--);

        if (opponent != null) {
            int opElo = plugin.getEloManager().getElo(opponentUUID);
            setLine(obj, leg("&fOpp ELO: &e" + opElo), line--);
        }

        setLine(obj, "    ", line--);

        player.setScoreboard(board);
        activeBoards.put(player.getUniqueId(), board);
    }

    // ── Clear ──────────────────────────────────────────────────────────────

    /**
     * Removes the custom scoreboard from a player, restoring the server default.
     */
    public void clearScoreboard(Player player) {
        if (player == null) return;
        activeBoards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void setLine(Objective obj, String entry, int score) {
        Score s = obj.getScore(entry);
        s.setScore(score);
    }

    /** Converts a legacy &-color string to an Adventure Component. */
    private Component comp(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    /** Converts a legacy &-color string to a legacy §-color string (for score entries). */
    private String leg(String text) {
        return text.replace('&', '§');
    }
}
