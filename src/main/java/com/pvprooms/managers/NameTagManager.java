package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Tier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
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
 * Format: PlayerName §7[§bLT5§7]
 */
public class NameTagManager {

    private final PvPRoomsPro plugin;
    private final Map<UUID, Tier> cachedTiers = new HashMap<>();
    private BukkitTask updateTask;
    private Scoreboard mainBoard;

    public NameTagManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    public void start() {
        mainBoard = Bukkit.getScoreboardManager().getMainScoreboard();
        
        // Update all players on start
        for (Player p : Bukkit.getOnlinePlayers()) {
            updatePlayer(p);
        }
        
        // Periodic update task (every 5 seconds)
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                Tier currentTier = plugin.getTierManager().getBestTier(p.getUniqueId());
                Tier cached = cachedTiers.get(p.getUniqueId());
                
                // Only update if tier changed
                if (cached == null || cached != currentTier) {
                    updatePlayer(p);
                }
            }
        }, 100L, 100L); // 5 seconds
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        // Clean up teams
        for (Player p : Bukkit.getOnlinePlayers()) {
            removePlayer(p);
        }
    }

    /**
     * Updates a player's display name with their tier suffix.
     * Called on join and when tier changes.
     */
    public void updatePlayer(Player player) {
        if (player == null) return;
        
        UUID uuid = player.getUniqueId();
        Tier tier = plugin.getTierManager().getBestTier(uuid);
        cachedTiers.put(uuid, tier);
        
        // Build suffix: §7[§bLT5§7]
        String suffix = buildSuffix(tier);
        
        // Update tab list name
        Component tabName = LegacyComponentSerializer.legacySection().deserialize(
            player.getName() + " " + suffix
        );
        player.playerListName(tabName);
        
        // Update nametag using teams
        updateNameTag(player, suffix);
    }

    /**
     * Removes player from nametag system (on quit).
     */
    public void removePlayer(Player player) {
        if (player == null) return;
        cachedTiers.remove(player.getUniqueId());
        
        String teamName = getTeamName(player);
        Team team = mainBoard.getTeam(teamName);
        if (team != null) {
            team.unregister();
        }
    }

    /**
     * Builds the tier suffix string.
     * Format: §7[§cHT1§7] with tier-specific color
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

    /**
     * Updates the nametag above player's head using scoreboard teams.
     */
    private void updateNameTag(Player player, String suffix) {
        String teamName = getTeamName(player);
        
        // Get or create team for this player
        Team team = mainBoard.getTeam(teamName);
        if (team == null) {
            team = mainBoard.registerNewTeam(teamName);
        }
        
        // Set suffix (appears after name above head)
        team.suffix(LegacyComponentSerializer.legacySection().deserialize(" " + suffix));
        
        // Add player to team if not already
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
        
        // Apply the main scoreboard to all players so they see the nametags
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getScoreboard() == Bukkit.getScoreboardManager().getMainScoreboard()) {
                // Already using main board
            }
        }
    }

    private String getTeamName(Player player) {
        // Team names must be ≤16 chars
        String name = "tier_" + player.getName();
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    /**
     * Forces an immediate update for a player (e.g., after tier change).
     */
    public void forceUpdate(Player player) {
        updatePlayer(player);
    }

    /**
     * Forces update for all online players.
     */
    public void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updatePlayer(p);
        }
    }
}
