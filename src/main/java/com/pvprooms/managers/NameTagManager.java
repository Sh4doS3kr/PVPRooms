package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Tier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages player tier suffixes that appear:
 * - In the tab list (player list)
 * - Above player heads (nametags)
 * - In chat messages
 * 
 * Format: PlayerName §8[§bLT5§8]
 */
public class NameTagManager implements Listener {

    private final PvPRoomsPro plugin;
    private final Map<UUID, Tier> cachedTiers = new HashMap<>();
    private BukkitTask updateTask;

    public NameTagManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Register as listener
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        // Delayed start to ensure all players are loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                updatePlayerForAll(p);
            }
        }, 20L);
        
        // Periodic update task (every 2 seconds)
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                Tier currentTier = plugin.getTierManager().getBestTier(p.getUniqueId());
                Tier cached = cachedTiers.get(p.getUniqueId());
                
                // Always update tab name, check tier for nametag update
                updateTabName(p);
                
                if (cached == null || cached != currentTier) {
                    updatePlayerForAll(p);
                }
            }
        }, 40L, 40L); // 2 seconds
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Update joining player's nametag for all existing players
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updatePlayerForAll(player);
            
            // Update all other players' nametags for the joining player
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)) {
                    updatePlayerForViewer(other, player);
                }
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Tier tier = plugin.getTierManager().getBestTier(player.getUniqueId());
        String suffix = buildSuffix(tier);
        
        // Format: PlayerName [LT5] » message
        Component nameWithSuffix = Component.text(player.getName() + " ")
            .color(NamedTextColor.WHITE)
            .append(LegacyComponentSerializer.legacySection().deserialize(suffix))
            .append(Component.text(" » ").color(NamedTextColor.GRAY));
        
        event.renderer((source, sourceDisplayName, message, viewer) -> 
            nameWithSuffix.append(message.color(NamedTextColor.WHITE))
        );
    }

    /**
     * Updates a player's tab list name with tier suffix.
     */
    private void updateTabName(Player player) {
        if (player == null || !player.isOnline()) return;
        
        Tier tier = plugin.getTierManager().getBestTier(player.getUniqueId());
        String suffix = buildSuffix(tier);
        
        Component tabName = LegacyComponentSerializer.legacySection().deserialize(
            player.getName() + " " + suffix
        );
        player.playerListName(tabName);
    }

    /**
     * Updates a player's nametag for ALL online players (including themselves).
     */
    public void updatePlayerForAll(Player target) {
        if (target == null || !target.isOnline()) return;
        
        UUID uuid = target.getUniqueId();
        Tier tier = plugin.getTierManager().getBestTier(uuid);
        cachedTiers.put(uuid, tier);
        
        // Update tab name
        updateTabName(target);
        
        // Update nametag for all viewers
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updatePlayerForViewer(target, viewer);
        }
    }

    /**
     * Updates a target player's nametag as seen by a specific viewer.
     * Uses the viewer's scoreboard to set up teams.
     */
    private void updatePlayerForViewer(Player target, Player viewer) {
        if (target == null || viewer == null) return;
        if (!target.isOnline() || !viewer.isOnline()) return;
        
        Scoreboard board = viewer.getScoreboard();
        if (board == null) return;
        
        Tier tier = plugin.getTierManager().getBestTier(target.getUniqueId());
        String suffix = buildSuffix(tier);
        String teamName = getTeamName(target);
        
        // Get or create team on viewer's scoreboard
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }
        
        // Set suffix
        team.suffix(LegacyComponentSerializer.legacySection().deserialize(" " + suffix));
        
        // Add target to team
        if (!team.hasEntry(target.getName())) {
            team.addEntry(target.getName());
        }
    }

    /**
     * Removes player from nametag system (on quit).
     */
    public void removePlayer(Player player) {
        if (player == null) return;
        cachedTiers.remove(player.getUniqueId());
        
        String teamName = getTeamName(player);
        
        // Remove from all online players' scoreboards
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard board = viewer.getScoreboard();
            if (board != null) {
                Team team = board.getTeam(teamName);
                if (team != null) {
                    team.unregister();
                }
            }
        }
    }

    /**
     * Builds the tier suffix string.
     * Format: §8[§cHT1§8] with tier-specific color
     */
    public String buildSuffix(Tier tier) {
        if (tier == null || tier == Tier.UNRANKED) {
            return "§8[§7?§8]";
        }
        return "§8[" + tier.colour + tier.displayName + "§8]";
    }

    /**
     * Gets the suffix Component for a player (for chat formatting).
     */
    public Component getSuffixComponent(Player player) {
        Tier tier = plugin.getTierManager().getBestTier(player.getUniqueId());
        return LegacyComponentSerializer.legacySection().deserialize(buildSuffix(tier));
    }

    /**
     * Gets the formatted name with suffix for chat messages.
     */
    public String getFormattedName(Player player) {
        Tier tier = plugin.getTierManager().getBestTier(player.getUniqueId());
        return player.getName() + " " + buildSuffix(tier);
    }

    private String getTeamName(Player player) {
        // Team names must be ≤16 chars
        String name = "pvpt_" + player.getName();
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    /**
     * Forces an immediate update for a player.
     */
    public void forceUpdate(Player player) {
        updatePlayerForAll(player);
    }

    /**
     * Forces update for all online players.
     */
    public void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updatePlayerForAll(p);
        }
    }
}
