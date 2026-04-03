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
            // Skip non-critical scoreboard updates during severe lag
            var lagMon = plugin.getLagMonitor();
            if (lagMon != null && lagMon.isSevere()) return;

            for (Player p : Bukkit.getOnlinePlayers()) {
                UUID uuid = p.getUniqueId();
                if (lobbyPlayers.containsKey(uuid)) {
                    showLobbyScoreboard(p);
                } else if (queuePlayers.containsKey(uuid)) {
                    refreshQueueScoreboard(p);
                } else if (spectatorDuels.containsKey(uuid)) {
                    // Refresh spectator scoreboard with live health/score
                    UUID duelId = spectatorDuels.get(uuid);
                    Duel duel = plugin.getDuelManager().getDuelById(duelId);
                    if (duel != null && duel.getState() == Duel.State.FIGHTING) {
                        refreshSpectatorScoreboard(p, duel);
                    }
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
        Objective obj = getOrCreate(player, "pvpqueue", "&6&l⚔ &e&lEN COLA");

        int s = 0;
        Tier qTier = plugin.getTierManager().getTier(player.getUniqueId(), kitName);
        int  qElo  = plugin.getEloManager().getElo(player.getUniqueId());
        int  qPts  = plugin.getTierManager().getPoints(player.getUniqueId(), kitName);
        String waitStr = elapsedSeconds < 60
                ? "§a" + elapsedSeconds + "§7s"
                : "§a" + (elapsedSeconds / 60) + "§7m §a" + (elapsedSeconds % 60) + "§7s";
        int inQueue = plugin.getQueueManager().getTotalQueued();

        tl(obj, s++, "§8§m━━━━━━━━━━━━━━━━━━━",                                    12);
        tl(obj, s++, " ",                                                          11);
        tl(obj, s++, leg("&f&l" + kitName.toUpperCase()),                          10);
        tl(obj, s++, leg("  " + qTier.colour + "▸ " + qTier.displayName + " &8• &6" + qElo + " ELO"), 9);
        tl(obj, s++, " ",                                                          8);
        tl(obj, s++, leg("&e⏳ &fTiempo: " + waitStr),                             7);
        tl(obj, s++, leg("&a👥 &fEn cola: &a" + inQueue),                          6);
        tl(obj, s++, " ",                                                          5);
        tl(obj, s++, leg("&7&oBuscando rival..."),                                 4);
        tl(obj, s++, " ",                                                          3);
        tl(obj, s++, "§8§m━━━━━━━━━━━━━━━━━━━",                                    2);
        tl(obj, s++, leg("  &c/pvpleave &8para salir"),                            1);
        tl(obj, s,   pingLine(player),                                             0);
    }

    // ── Duel scoreboard ────────────────────────────────────────────────────

    public void updateDuelScoreboard(Player player, Duel duel) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        if (player == null) return;

        UUID opponentUUID = duel.getOpponent(player.getUniqueId());
        Player opponent   = opponentUUID != null ? Bukkit.getPlayer(opponentUUID) : null;
        String opponentName = opponent != null ? opponent.getName() : "???";

        Objective obj = getOrCreate(player, "pvpduel", "&c&l⚔ &4&lEN DUELO");

        int s = 0;
        tl(obj, s++, "§8§m━━━━━━━━━━━━━━━━━━━", 15);
        tl(obj, s++, " ", 14);

        // Time / Status
        if (duel.getState() == Duel.State.COUNTDOWN) {
            tl(obj, s++, leg("&e&l⏳ &fPreparando..."), 13);
        } else {
            long elapsed = duel.getElapsedSeconds();
            String time  = String.format("§a%d§7:§a%02d", elapsed / 60, elapsed % 60);
            tl(obj, s++, leg("&e⏱ &fTiempo: " + time), 13);
        }

        // Kit, Mode label (TIER / ELO) & Round
        tl(obj, s++, leg("&b⚔ &fKit: &b" + duel.getKitName()), 12);
        if (duel.isRanked()) {
            int myW = duel.getWins(player.getUniqueId());
            int opW = duel.getWins(duel.getOpponent(player.getUniqueId()));
            int boTotal = duel.getWinsNeeded() * 2 - 1; // BO7 = 7
            tl(obj, s++, leg("&b[TIER] &8• &6" + myW + " &8- &evs &c" + opW + " &8(a " + boTotal + ")"), 11);
        } else {
            tl(obj, s++, leg("&a[ELO] &8• &6" + plugin.getEloManager().getElo(player.getUniqueId()) + " ELO"), 11);
        }
        
        tl(obj, s++, " ", 10);
        tl(obj, s++, "§8§m━━━━━━━━━━━━━━━━━━━", 9);
        tl(obj, s++, " ", 8);

        // Opponent info
        tl(obj, s++, leg("&c☠ &fvs &c&l" + opponentName), 7);
        if (opponent != null) {
            int opPing = opponent.getPing();
            String pingCol = opPing < 50 ? "§a" : opPing < 100 ? "§e" : opPing < 150 ? "§6" : "§c";
            
            if (duel.isBo3()) {
                Tier opKitTier = plugin.getTierManager().getTier(opponentUUID, duel.getKitName());
                tl(obj, s++, leg("  &7Tier: " + opKitTier.colour + opKitTier.displayName + " &8• " + pingCol + opPing + "ms"), 6);
            } else {
                int opElo = plugin.getEloManager().getElo(opponentUUID);
                Tier opTier = plugin.getTierManager().getTier(opponentUUID, duel.getKitName());
                tl(obj, s++, leg("  &7" + opTier.colour + opTier.displayName + " &8• &6" + opElo + " &8• " + pingCol + opPing + "ms"), 6);
            }
        }

        tl(obj, s++, " ", 5);

        // My stats
        if (duel.isBo3()) {
            Tier myKitTier = plugin.getTierManager().getTier(player.getUniqueId(), duel.getKitName());
            int  myPts     = plugin.getTierManager().getPoints(player.getUniqueId(), duel.getKitName());
            tl(obj, s++, leg("&a★ &fTu tier: " + myKitTier.colour + myKitTier.displayName), 4);
            tl(obj, s++, leg("  &7Puntos: &6" + Math.max(0, myPts)), 3);
        } else {
            int  myElo  = plugin.getEloManager().getElo(player.getUniqueId());
            Tier myTier = plugin.getTierManager().getTier(player.getUniqueId(), duel.getKitName());
            tl(obj, s++, leg("&a★ &f" + myTier.colour + myTier.displayName + " &8• &6" + myElo + " ELO"), 4);
        }

        tl(obj, s++, " ", 2);
        tl(obj, s++, "§8§m━━━━━━━━━━━━━━━━━━━", 1);
        tl(obj, s++, pingLine(player), 0);
        tl(obj, s,   regionLine(), -1);
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
        String rankStr    = rank == -1 ? "§7#-" : "§e#" + rank;
        Tier lobbyTier    = plugin.getTierManager().getBestTier(player.getUniqueId());
        com.pvprooms.model.TierTitle title = plugin.getTierManager().getTitle(player.getUniqueId());

        Objective obj = getOrCreate(player, "pvplobby", "&6&l✦ &e&lPvPRooms");
        lobbyPlayers.put(player.getUniqueId(), true);

        int s = 0;
        tl(obj, s++, "§8§m━━━━━━━━━━━━━━━━━━━",                                                       15);
        tl(obj, s++, " ",                                                                              14);
        tl(obj, s++, leg("&a👥 &fOnline: &a" + online),                                                13);
        tl(obj, s++, leg("&c⚔ &fDuelos: &c" + activeMatches + " &8• &eEn cola: &e" + inQueue),       12);
        tl(obj, s++, " ",                                                                              11);
        tl(obj, s++, "§8§m━━━━━━━━━━━━━━━━━━━",                                                       10);
        tl(obj, s++, " ",                                                                              9);
        tl(obj, s++, leg("&e⭐ &fTu Rango"),                                                          8);
        tl(obj, s++, leg("  " + lobbyTier.colour + "▸ &l" + lobbyTier.displayName + " &8• &6" + elo + " ELO"), 7);
        tl(obj, s++, leg("  &7Posición: " + rankStr),                                                 6);
        tl(obj, s++, " ",                                                                              5);
        tl(obj, s++, leg("&d✧ &fInsignia"),                                                           4);
        tl(obj, s++, leg("  " + title.colour + title.symbol + " &7" + title.name),                    3);
        tl(obj, s++, " ",                                                                              2);
        tl(obj, s++, "§8§m━━━━━━━━━━━━━━━━━━━",                                                       1);
        tl(obj, s++, leg("  &a/queue &8para jugar"),                                                   0);
        tl(obj, s,   pingLine(player),                                                                -1);
    }

    // ── Bot Duel scoreboard ─────────────────────────────────────────────────

    public void showBotDuelScoreboard(Player player, com.pvprooms.bot.BotManager.BotDuel botDuel) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        if (player == null || botDuel == null) return;

        Objective obj = getOrCreate(player, "pvpbotduel", "&c&l⚔ &4&lPRÁCTICA BOT");
        lobbyPlayers.remove(player.getUniqueId());
        queuePlayers.remove(player.getUniqueId());

        long elapsed = botDuel.getElapsedSeconds();
        String time = String.format("§a%d§7:§a%02d", elapsed / 60, elapsed % 60);
        String diffColor = switch(botDuel.difficulty) {
            case EASY -> "§a";
            case MEDIUM -> "§e";
            case HARD -> "§c";
            case HACKER -> "§4§l";
            case ADAPTIVE -> "§d§l";
            case DUMMY -> "§7";
        };

        // Get bot health
        String botHealthStr = "§c???";
        net.citizensnpcs.api.npc.NPC botNpc = plugin.getBotManager().getPlayerBot(player.getUniqueId());
        if (botNpc != null && botNpc.isSpawned() && botNpc.getEntity() instanceof org.bukkit.entity.LivingEntity living) {
            double health = living.getHealth();
            double maxHealth = living.getMaxHealth();
            int hearts = (int) Math.ceil(health / 2);
            int maxHearts = (int) Math.ceil(maxHealth / 2);
            String healthColor = health > maxHealth * 0.5 ? "§a" : health > maxHealth * 0.25 ? "§e" : "§c";
            botHealthStr = healthColor + String.format("%.1f", health) + "§7/§c" + (int)maxHealth + " §c❤";
        }

        int s = 0;
        tl(obj, s++, "§8§m━━━━━━━━━━━━━━━━━━━", 15);
        tl(obj, s++, " ", 14);
        tl(obj, s++, leg("&e⏱ &fTiempo: " + time), 13);
        tl(obj, s++, leg("&b⚔ &fKit: &b" + botDuel.kitName), 12);
        tl(obj, s++, " ", 11);
        tl(obj, s++, "§8§m━━━━━━━━━━━━━━━━━━━", 10);
        tl(obj, s++, " ", 9);
        tl(obj, s++, leg("&c☠ &fvs Bot " + diffColor + botDuel.difficulty.name()), 8);
        tl(obj, s++, leg("  &fVida: " + botHealthStr), 7);
        tl(obj, s++, " ", 6);
        tl(obj, s++, "§8§m━━━━━━━━━━━━━━━━━━━", 5);
        tl(obj, s++, leg("&7No afecta ELO/Tier"), 4);
        tl(obj, s++, " ", 3);
        tl(obj, s++, "§8§m━━━━━━━━━━━━━━━━━━━", 2);
        tl(obj, s++, leg("  &c/pvpleave &8para salir"), 1);
        tl(obj, s++, pingLine(player), 0);
        tl(obj, s,   regionLine(), -1);
    }

    // ── Spectator scoreboard ─────────────────────────────────────────────────

    /** Spectators watching a duel: uuid → duelId */
    private final Map<UUID, UUID> spectatorDuels = new HashMap<>();

    public void showSpectatorScoreboard(Player spectator, Duel duel) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        if (spectator == null || duel == null) return;

        UUID uuid = spectator.getUniqueId();
        spectatorDuels.put(uuid, duel.getId());
        lobbyPlayers.remove(uuid);
        queuePlayers.remove(uuid);

        refreshSpectatorScoreboard(spectator, duel);
    }

    private void refreshSpectatorScoreboard(Player spectator, Duel duel) {
        Player p1 = Bukkit.getPlayer(duel.getPlayer1());
        Player p2 = Bukkit.getPlayer(duel.getPlayer2());
        String name1 = p1 != null ? p1.getName() : "?";
        String name2 = p2 != null ? p2.getName() : "?";

        int w1 = duel.getWins1();
        int w2 = duel.getWins2();
        int boTotal = duel.getWinsNeeded() * 2 - 1; // BO7 = 7
        String scoreStr = "§a" + w1 + " §7- §c" + w2 + " §8(a " + boTotal + ")";

        // Health info
        String hp1 = p1 != null ? formatHealth(p1) : "§c???";
        String hp2 = p2 != null ? formatHealth(p2) : "§c???";

        Objective obj = getOrCreate(spectator, "pvpspec", "&e&l👁 &6&lESPECTANDO");

        int s = 0;
        tl(obj, s++, "§8§m━━━━━━━━━━━━━━━━━━━", 15);
        tl(obj, s++, " ", 14);
        tl(obj, s++, leg("&b⚔ &fKit: &b" + duel.getKitName()), 13);
        tl(obj, s++, leg("&e⏱ &fModo: " + (duel.isBo3() ? "&6Tier (BO7)" : "&aELO")), 12);
        tl(obj, s++, " ", 11);
        tl(obj, s++, "§8§m━━━━━━━━━━━━━━━━━━━", 10);
        tl(obj, s++, " ", 9);
        tl(obj, s++, leg("&a▸ &f" + name1), 8);
        tl(obj, s++, leg("  &7Vida: " + hp1 + " &8• &6" + w1 + " wins"), 7);
        tl(obj, s++, " ", 6);
        tl(obj, s++, leg("&c▸ &f" + name2), 5);
        tl(obj, s++, leg("  &7Vida: " + hp2 + " &8• &6" + w2 + " wins"), 4);
        tl(obj, s++, " ", 3);
        tl(obj, s++, "§8§m━━━━━━━━━━━━━━━━━━━", 2);
        tl(obj, s++, leg("  &c/pvpleave &8para salir"), 1);
        tl(obj, s,   pingLine(spectator), 0);
    }

    private String formatHealth(Player p) {
        double hp = p.getHealth();
        double max = 20.0;
        var attr = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attr != null) max = attr.getValue();
        String col = hp > max * 0.5 ? "§a" : hp > max * 0.25 ? "§e" : "§c";
        return col + String.format("%.0f", hp) + "§7/§f" + (int) max + " §c❤";
    }

    // ── Clear ──────────────────────────────────────────────────────────────

    public void clearScoreboard(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        activeBoards.remove(uuid);
        lobbyPlayers.remove(uuid);
        queuePlayers.remove(uuid);
        queueJoinTimes.remove(uuid);
        spectatorDuels.remove(uuid);
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void restoreLobbyScoreboard(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        queuePlayers.remove(uuid);
        queueJoinTimes.remove(uuid);
        spectatorDuels.remove(uuid);
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

        // Remove BELOW_NAME health from main scoreboard first (prevents duplicate health bars)
        Scoreboard mainBoard = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective mainHealthObj = mainBoard.getObjective(DisplaySlot.BELOW_NAME);
        if (mainHealthObj != null) {
            try { mainHealthObj.unregister(); } catch (Exception ignored) {}
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

    // ── Ping / region helpers ──────────────────────────────────────────────

    private String pingLine(Player player) {
        int ping = player.getPing();
        String colour;
        if      (ping <  50)  colour = "§a";
        else if (ping < 100)  colour = "§e";
        else if (ping < 150)  colour = "§6";
        else                  colour = "§c";
        return "§7Ping: " + colour + ping + "ms";
    }

    private String regionLine() {
        String raw = plugin.getServerRegion();
        String display = switch (raw.toLowerCase()) {
            case "eu", "europe", "eu-west", "eu-central" -> "§bEU §7• Europa";
            case "na", "us", "us-east", "us-west"       -> "§bNA §7• Norteamérica";
            case "sa", "latam", "br"                    -> "§bSA §7• Sudamérica";
            case "as", "asia", "sg", "ap"               -> "§bAS §7• Asia";
            case "oc", "au", "oceania"                  -> "§bOC §7• Oceanía";
            case "af", "africa"                         -> "§bAF §7• África";
            default -> "§b" + raw.toUpperCase();
        };
        return "§7Región: " + display;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Component comp(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    private String leg(String text) {
        return text.replace('&', '§');
    }
}
