package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Duel;
import com.pvprooms.model.Tier;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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

    /** Players currently showing the lobby scoreboard */
    private final Map<UUID, Boolean> lobbyPlayers = new HashMap<>();

    /** Periodic update task for lobby and queue scoreboards */
    private BukkitTask updateTask;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public ScoreboardManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    /** Inicia la tarea periódica que refresca los scoreboards del lobby. */
    public void startLobbyTask() {
        int interval = plugin.getConfig().getInt("scoreboard.update-interval", 20);
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                UUID uuid = p.getUniqueId();
                if (lobbyPlayers.containsKey(uuid)) {
                    showLobbyScoreboard(p);
                }
            }
        }, interval, interval);
    }

    /** Detiene la tarea periódica. */
    public void stopLobbyTask() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
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
        Tier qTier = Tier.forPlayer(plugin.getEloManager(), player.getUniqueId());
        setLine(obj, leg("&e&l» &fKit:  &e" + kitName), line--);
        setLine(obj, leg("&e&l» &fTier: " + qTier.colour + qTier.displayName), line--);
        setLine(obj, leg("&e&l» &fELO:  &6" + plugin.getEloManager().getElo(player.getUniqueId())), line--);
        setLine(obj, "  ", line--);
        setLine(obj, leg("&e&l» &fEn cola: &a" + plugin.getQueueManager().getTotalQueued()), line--);
        setLine(obj, "   ", line--);
        setLine(obj, leg("&7⏳ Buscando rival..."), line--);
        setLine(obj, leg("&7Usa &f/pvpleave &7para salir"), line--);
        setLine(obj, "     ", line--);
        setLine(obj, pingLine(player), line--);

        player.setScoreboard(board);
        activeBoards.put(player.getUniqueId(), board);
        lobbyPlayers.remove(player.getUniqueId()); // no sobreescribir con el de lobby
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
            setLine(obj, leg("&e⏳ Preparando..."), line--);
        } else {
            long elapsed = duel.getElapsedSeconds();
            String time = String.format("%d:%02d", elapsed / 60, elapsed % 60);
            setLine(obj, leg("&e&l» &fTiempo: &a" + time), line--);
        }

        setLine(obj, "  ", line--);
        setLine(obj, leg("&e&l» &fKit: &e" + duel.getKitName()), line--);
        setLine(obj, "   ", line--);
        setLine(obj, leg("&e&l» &fRival: &c" + opponentName), line--);

        int myElo  = plugin.getEloManager().getElo(player.getUniqueId());
        Tier myTier = Tier.forPlayer(plugin.getEloManager(), player.getUniqueId());
        setLine(obj, leg("&e&l» &fTier:     " + myTier.colour + myTier.displayName), line--);
        setLine(obj, leg("&e&l» &fTu ELO:   &6" + myElo), line--);

        if (opponent != null) {
            int opElo   = plugin.getEloManager().getElo(opponentUUID);
            Tier opTier = Tier.forPlayer(plugin.getEloManager(), opponentUUID);
            setLine(obj, leg("&e&l» &fELO rival: &c" + opElo + " &7(" + opTier.colour + opTier.displayName + "&7)"), line--);
        }

        setLine(obj, "    ", line--);
        setLine(obj, pingLine(player), line--);

        player.setScoreboard(board);
        activeBoards.put(player.getUniqueId(), board);
    }

    // ── Lobby scoreboard ───────────────────────────────────────────────────

    /**
     * Muestra el scoreboard del lobby a un jugador que no está en duelo ni en cola.
     */
    public void showLobbyScoreboard(Player player) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        if (player == null) return;

        int elo          = plugin.getEloManager().getElo(player.getUniqueId());
        int rank         = plugin.getEloManager().getRank(player.getUniqueId());
        int online       = Bukkit.getOnlinePlayers().size();
        int activeMatches= plugin.getDuelManager().getActiveDuelCount();
        int inQueue      = plugin.getQueueManager().getTotalQueued();
        String hora      = LocalTime.now().format(TIME_FMT);

        String rankStr = rank == -1 ? "§7Sin rango" : "§e#" + rank;

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("pvplobby", Criteria.DUMMY, comp("&6&lPvPRooms"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int line = 15;
        setLine(obj, " ", line--);
        setLine(obj, leg("&e&l» &fJugadores online"), line--);
        setLine(obj, leg("  &7" + online + " conectados"), line--);
        setLine(obj, "  ", line--);
        setLine(obj, leg("&e&l» &fDuelos activos"), line--);
        setLine(obj, leg("  &7" + activeMatches + " en curso · " + inQueue + " en cola"), line--);
        setLine(obj, "   ", line--);
        Tier lobbyTier = Tier.forPlayer(plugin.getEloManager(), player.getUniqueId());
        setLine(obj, leg("&e&l» &fTu Tier"), line--);
        setLine(obj, leg("  " + lobbyTier.colour + "&l" + lobbyTier.displayName), line--);
        setLine(obj, leg("&e&l» &fTu ELO"), line--);
        setLine(obj, leg("  &6" + elo + " ELO  " + rankStr), line--);
        setLine(obj, "    ", line--);
        setLine(obj, leg("&e&l» &fHora"), line--);
        setLine(obj, leg("  &7" + hora), line--);
        setLine(obj, "     ", line--);
        setLine(obj, leg("&a/queue &7para combatir"), line--);
        setLine(obj, "      ", line--);
        setLine(obj, pingLine(player), line--);

        player.setScoreboard(board);
        activeBoards.put(player.getUniqueId(), board);
        lobbyPlayers.put(player.getUniqueId(), true);
    }

    // ── Clear ──────────────────────────────────────────────────────────────

    /**
     * Quita el scoreboard personalizado del jugador y restaura el por defecto.
     * Llama a este método al entrar en duelo/cola o al salir del servidor.
     */
    public void clearScoreboard(Player player) {
        if (player == null) return;
        activeBoards.remove(player.getUniqueId());
        lobbyPlayers.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /**
     * Restaura el scoreboard de lobby (llamar al terminar duelo/cola).
     */
    public void restoreLobbyScoreboard(Player player) {
        if (player == null) return;
        showLobbyScoreboard(player);
    }

    // ── Ping / region helper ───────────────────────────────────────────────

    /**
     * Builds a formatted ping+region string, e.g. "§7(eu) §a42ms".
     * Colour: green <50 · yellow <100 · gold <150 · red ≥150.
     */
    private String pingLine(Player player) {
        String region = plugin.getServerRegion();
        int ping = player.getPing();
        String colour;
        if      (ping <  50)  colour = "§a";
        else if (ping < 100)  colour = "§e";
        else if (ping < 150)  colour = "§6";
        else                  colour = "§c";
        return "§7(" + region + ") " + colour + ping + "ms";
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void setLine(Objective obj, String entry, int score) {
        Score s = obj.getScore(entry);
        s.setScore(score);
        s.numberFormat(NumberFormat.blank());
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
