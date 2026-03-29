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
 *
 * Anti-flicker design: each player gets one Scoreboard per state.
 * player.setScoreboard() is called ONLY when the state changes (lobby→duel etc.).
 * Subsequent updates only modify Team prefixes (a single packet, no board reset),
 * which eliminates the ~1-tick blank that caused the visible flicker.
 *
 * Each sidebar line uses a fixed invisible "dummy" entry (§0-§f) whose visible
 * text is provided entirely by a Team prefix.
 */
public class ScoreboardManager {

    private final PvPRoomsPro plugin;

    /** Tracks which players currently have a custom scoreboard assigned */
    private final Map<UUID, Scoreboard> activeBoards = new HashMap<>();

    /** Players currently showing the lobby scoreboard */
    private final Map<UUID, Boolean> lobbyPlayers = new HashMap<>();

    /** Players in queue: uuid → kitName */
    private final Map<UUID, String> queuePlayers = new HashMap<>();

    /** Timestamp (ms) when a player entered the queue scoreboard */
    private final Map<UUID, Long> queueJoinTimes = new HashMap<>();

    /** Periodic update task for lobby and queue scoreboards */
    private BukkitTask updateTask;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Invisible dummy entries — each is a unique §-color code with no printable char.
     * Teams wrap them so only the prefix text is visible.
     */
    private static final String[] SLOTS = {
        "\u00a70","\u00a71","\u00a72","\u00a73","\u00a74","\u00a75","\u00a76","\u00a77",
        "\u00a78","\u00a79","\u00a7a","\u00a7b","\u00a7c","\u00a7d","\u00a7e","\u00a7f",
        "\u00a7l","\u00a7r"   // \u00a7l = §l (bold), \u00a7r = §r (reset) — invisible as standalone entries
    };

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
                } else if (queuePlayers.containsKey(uuid)) {
                    refreshQueueScoreboard(p);
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

    public void showQueueScoreboard(Player player, String kitName) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        UUID uuid = player.getUniqueId();
        queuePlayers.put(uuid, kitName);
        queueJoinTimes.putIfAbsent(uuid, System.currentTimeMillis());
        lobbyPlayers.remove(uuid);
        buildQueueScoreboard(player, kitName, 0);
    }

    private void refreshQueueScoreboard(Player player) {
        UUID uuid = player.getUniqueId();
        String kitName = queuePlayers.get(uuid);
        if (kitName == null) return;
        long elapsed = (System.currentTimeMillis() - queueJoinTimes.getOrDefault(uuid, System.currentTimeMillis())) / 1000;
        buildQueueScoreboard(player, kitName, elapsed);
    }

    private void buildQueueScoreboard(Player player, String kitName, long elapsedSeconds) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        Objective obj = getOrCreate(player, "pvpqueue", "&c&lPvPRooms");

        int s = 0;
        // Use kit-specific tier from TierManager (same source as the web page)
        Tier qTier = plugin.getTierManager().getTier(player.getUniqueId(), kitName);
        int  qElo  = plugin.getEloManager().getElo(player.getUniqueId());
        int  qPts  = plugin.getTierManager().getPoints(player.getUniqueId(), kitName);
        String waitStr = elapsedSeconds < 60
                ? "§e" + elapsedSeconds + "§fs"
                : "§e" + (elapsedSeconds / 60) + "§fm §e" + (elapsedSeconds % 60) + "§fs";

        tl(obj, s++, " ",                                                                          10);
        tl(obj, s++, leg("&e&l» &fKit:  &e" + kitName),                                          9);
        tl(obj, s++, leg("&e&l» &fTier: " + qTier.colour + "&l" + qTier.displayName),            8);
        tl(obj, s++, leg("&e&l» &fELO: &6" + qElo + (qPts >= 0 ? "  &8| &7Pts: &6" + qPts : "")), 7);
        tl(obj, s++, " ",                                                          6);
        tl(obj, s++, leg("&e&l» &fEn cola: &a" + plugin.getQueueManager().getTotalQueued()), 5);
        tl(obj, s++, " ",                                                          4);
        tl(obj, s++, leg("&7⏳ Esperando: " + waitStr),                           3);
        tl(obj, s++, leg("&7¡Buscando rival..."),                                 2);
        tl(obj, s++, leg("&7Usa &f/pvpleave &7para salir"),                       1);
        tl(obj, s++, " ",                                                          0);
        tl(obj, s,   pingLine(player),                                            -1);
    }

    // ── Duel scoreboard ────────────────────────────────────────────────────

    public void updateDuelScoreboard(Player player, Duel duel) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        if (player == null) return;

        UUID opponentUUID = duel.getOpponent(player.getUniqueId());
        Player opponent   = opponentUUID != null ? Bukkit.getPlayer(opponentUUID) : null;
        String opponentName = opponent != null ? opponent.getName() : "Unknown";
        String titleCfg   = plugin.getConfig().getString("scoreboard.title", "&c&lPvPRooms");

        Objective obj = getOrCreate(player, "pvpduel", titleCfg);

        int s = 0;
        tl(obj, s++, " ", 14);

        if (duel.getState() == Duel.State.COUNTDOWN) {
            tl(obj, s++, leg("&e⏳ Preparando..."), 13);
        } else {
            long elapsed = duel.getElapsedSeconds();
            String time  = String.format("%d:%02d", elapsed / 60, elapsed % 60);
            tl(obj, s++, leg("&e&l» &fTiempo: &a" + time), 13);
        }

        tl(obj, s++, " ", 12);
        tl(obj, s++, leg("&e&l» &fKit: &e" + duel.getKitName()), 11);
        if (duel.isBo3()) {
            int myW = duel.getWins(player.getUniqueId());
            int opW = duel.getWins(duel.getOpponent(player.getUniqueId()));
            int rnd = Math.min(duel.getCurrentRound(), 3);
            tl(obj, s++, leg("&e&l» &fRonda &e" + rnd + "&7/3  &a" + myW + "&7-&c" + opW), 10);
        }
        tl(obj, s++, " ", 9);
        tl(obj, s++, leg("&e&l» &fRival: &c" + opponentName), 8);
        if (opponent != null) {
            int opPing = opponent.getPing();
            String pingCol = opPing < 50 ? "§a" : opPing < 100 ? "§e" : opPing < 150 ? "§6" : "§c";
            tl(obj, s++, leg("&e&l» &fPing rival: " + pingCol + opPing + "ms"), 7);
        }

        if (duel.isBo3()) {
            Tier myKitTier = plugin.getTierManager().getTier(player.getUniqueId(), duel.getKitName());
            int  myPts     = plugin.getTierManager().getPoints(player.getUniqueId(), duel.getKitName());
            com.pvprooms.model.TierTitle myTitle = plugin.getTierManager().getTitle(player.getUniqueId());
            tl(obj, s++, leg("&e&l» &fTier &8(" + duel.getKitName() + "&8): " + myKitTier.colour + myKitTier.displayName), 7);
            tl(obj, s++, leg("&e&l» &fPuntos: &6" + Math.max(0, myPts)), 6);
            if (opponent != null) {
                Tier opKitTier = plugin.getTierManager().getTier(opponentUUID, duel.getKitName());
                tl(obj, s++, leg("&e&l» &fTier rival: &c" + opKitTier.colour + opKitTier.displayName), 4);
            }
        } else {
            int  myElo  = plugin.getEloManager().getElo(player.getUniqueId());
            Tier myTier = plugin.getTierManager().getTier(player.getUniqueId(), duel.getKitName());
            tl(obj, s++, leg("&e&l» &fTier:     " + myTier.colour + "&l" + myTier.displayName), 7);
            tl(obj, s++, leg("&e&l» &fTu ELO:   &6" + myElo), 6);
            if (opponent != null) {
                int  opElo  = plugin.getEloManager().getElo(opponentUUID);
                Tier opTier = plugin.getTierManager().getTier(opponentUUID, duel.getKitName());
                tl(obj, s++, leg("&e&l» &fRival: &c" + opElo + " ELO &7(" + opTier.colour + opTier.displayName + "&7)"), 5);
            }
        }

        tl(obj, s++, " ", 3);
        tl(obj, s,   pingLine(player), 2);
    }

    // ── Lobby scoreboard ───────────────────────────────────────────────────

    public void showLobbyScoreboard(Player player) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        if (player == null) return;

        int elo           = plugin.getEloManager().getElo(player.getUniqueId());
        int rank          = plugin.getEloManager().getRank(player.getUniqueId());
        int online        = Bukkit.getOnlinePlayers().size();
        int activeMatches = plugin.getDuelManager().getActiveDuelCount();
        int inQueue       = plugin.getQueueManager().getTotalQueued();
        String hora       = LocalTime.now().format(TIME_FMT);
        String rankStr    = rank == -1 ? "§7-" : "§e#" + rank;
        // Use TierManager as single source of truth — same as the web page
        Tier lobbyTier    = plugin.getTierManager().getBestTier(player.getUniqueId());
        com.pvprooms.model.TierTitle title = plugin.getTierManager().getTitle(player.getUniqueId());

        Objective obj = getOrCreate(player, "pvplobby", "&6&lPvPRooms");
        lobbyPlayers.put(player.getUniqueId(), true);

        int s = 0;
        tl(obj, s++, " ",                                                                              15);
        tl(obj, s++, leg("&e&l» &fJugadores online"),                                                 14);
        tl(obj, s++, leg("  &7" + online + " conectados"),                                            13);
        tl(obj, s++, " ",                                                                              12);
        tl(obj, s++, leg("&e&l» &fDuelos activos"),                                                   11);
        tl(obj, s++, leg("  &7" + activeMatches + " en curso · " + inQueue + " en cola"),             10);
        tl(obj, s++, " ",                                                                              9);
        tl(obj, s++, leg("&e&l» &fTu Tier"),                                                          8);
        tl(obj, s++, leg("  " + lobbyTier.colour + "&l" + lobbyTier.displayName),                     7);
        tl(obj, s++, leg("&e&l» &fELO / Pos"),                                                        6);
        tl(obj, s++, leg("  &6" + elo + " ELO  &8| " + rankStr),                                     5);
        tl(obj, s++, " ",                                                                              4);
        tl(obj, s++, leg("&e&l» &fInsignia"),                                                         3);
        tl(obj, s++, leg("  " + title.colour + title.symbol + " &r" + title.name),                    2);
        tl(obj, s++, " ",                                                                              1);
        tl(obj, s++, leg("&a/queue &7para combatir"),                                                  0);
        tl(obj, s,   pingLine(player),                                                                -1);
    }

    // ── Clear ──────────────────────────────────────────────────────────────

    public void clearScoreboard(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        activeBoards.remove(uuid);
        lobbyPlayers.remove(uuid);
        queuePlayers.remove(uuid);
        queueJoinTimes.remove(uuid);
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void restoreLobbyScoreboard(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        queuePlayers.remove(uuid);
        queueJoinTimes.remove(uuid);
        // Force lobby board creation on next call by removing stale duel/queue board
        activeBoards.remove(uuid);
        showLobbyScoreboard(player);
    }

    // ── Anti-flicker core ──────────────────────────────────────────────────

    /**
     * Returns the Objective for the given player and board type.
     * If the player already has a board of the SAME type, it is reused
     * (player.setScoreboard is NOT called again — zero flicker).
     * If the type changed, a fresh Scoreboard is created and assigned once.
     */
    private Objective getOrCreate(Player player, String objName, String title) {
        UUID uuid = player.getUniqueId();
        Scoreboard board = activeBoards.get(uuid);

        if (board != null) {
            Objective existing = board.getObjective(objName);
            if (existing != null) {
                existing.displayName(comp(title));
                return existing;   // ← same board, no setScoreboard() call
            }
        }

        // Remove BELOW_NAME health from ALL scoreboards (prevents duplicate health bars)
        // Main scoreboard
        Scoreboard mainBoard = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective mainHealthObj = mainBoard.getObjective(DisplaySlot.BELOW_NAME);
        if (mainHealthObj != null) {
            try { mainHealthObj.unregister(); } catch (Exception ignored) {}
        }
        // Also try removing by common health objective names
        for (String name : new String[]{"health", "Health", "showhealth", "hp", "hearts"}) {
            Objective o = mainBoard.getObjective(name);
            if (o != null) try { o.unregister(); } catch (Exception ignored) {}
        }
        
        // Player's current scoreboard
        Scoreboard playerBoard = player.getScoreboard();
        if (playerBoard != null && playerBoard != mainBoard) {
            Objective playerHealthObj = playerBoard.getObjective(DisplaySlot.BELOW_NAME);
            if (playerHealthObj != null) {
                try { playerHealthObj.unregister(); } catch (Exception ignored) {}
            }
        }

        board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective(objName, Criteria.DUMMY, comp(title));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        for (int i = 0; i < SLOTS.length; i++) {
            Team t = board.registerNewTeam("t" + i);
            t.addEntry(SLOTS[i]);
        }

        activeBoards.put(uuid, board);
        player.setScoreboard(board);   // ← called exactly once per state
        return obj;
    }

    /**
     * Sets sidebar line at slot {@code slot} (0 = top) to {@code text} at
     * vertical position {@code score}.  Updates only the Team prefix →
     * one network packet, no board recreation, no visible flash.
     */
    private void tl(Objective obj, int slot, String text, int score) {
        if (slot < 0 || slot >= SLOTS.length) return;
        Scoreboard board = obj.getScoreboard();
        if (board == null) return;

        Team t = board.getTeam("t" + slot);
        if (t != null) {
            String prefix = text.length() > 64 ? text.substring(0, 64)  : text;
            String suffix = text.length() > 64 ? text.substring(64, Math.min(128, text.length())) : "";
            t.setPrefix(prefix);
            t.setSuffix(suffix);
        }

        Score s = obj.getScore(SLOTS[slot]);
        s.setScore(score);
        s.numberFormat(NumberFormat.blank());
    }

    // ── Ping / region helper ───────────────────────────────────────────────

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

    private Component comp(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    private String leg(String text) {
        return text.replace('&', '§');
    }
}
