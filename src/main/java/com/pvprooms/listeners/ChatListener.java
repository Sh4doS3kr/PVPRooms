package com.pvprooms.listeners;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Tier;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Chat manager that formats all chat messages.
 * Format: [Tier] [LuckPerms prefix] username >> message
 * 
 * Placeholder for tier: %pvptiers_tier% or use getTierPrefix(player)
 */
public class ChatListener implements Listener {

    private final PvPRoomsPro plugin;
    // Use ampersand serializer with hex color support
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    public ChatListener(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        event.setCancelled(true);

        Player player = event.getPlayer();
        Component message = event.message();

        // Get tier prefix (e.g., §c[HT5])
        String tierPrefix = getTierPrefix(player.getUniqueId());
        
        // Get LuckPerms prefix
        String lpPrefix = getLuckPermsPrefix(player);

        // Build the formatted message
        // Format: [Tier] [LuckPerms prefix] username >> message
        Component formatted = Component.empty();

        // Add tier prefix first (e.g., [HT5] with tier color)
        formatted = formatted.append(LEGACY.deserialize(tierPrefix));
        formatted = formatted.append(Component.text(" "));

        // Add LuckPerms prefix if exists
        if (lpPrefix != null && !lpPrefix.isEmpty()) {
            formatted = formatted.append(LEGACY.deserialize(lpPrefix));
            formatted = formatted.append(Component.text(" "));
        }

        // Add username (white, no bold)
        formatted = formatted.append(
                Component.text(player.getName(), NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, false)
        );

        // Add separator >> (dark gray, no bold)
        formatted = formatted.append(
                Component.text(" >> ", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.BOLD, false)
        );

        // Add message (white, no bold)
        formatted = formatted.append(
                message.color(NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, false)
        );

        // Broadcast to all players
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(formatted);
        }

        // Also log to console
        Bukkit.getConsoleSender().sendMessage(formatted);
    }
    
    /**
     * Gets the tier prefix for a player.
     * Format: [TIER] with tier color (e.g., §c[HT5], §a[LT3])
     * 
     * Use this as placeholder: %pvptiers_tier%
     */
    public String getTierPrefix(UUID uuid) {
        Tier tier = plugin.getTierManager().getBestTier(uuid);
        if (tier == null || tier == Tier.UNRANKED) {
            return "§7[?]";
        }
        return tier.colour + "[" + tier.displayName + "]";
    }
    
    /**
     * Gets the full tier display string for external use.
     * Returns: "HT5" or "LT3" etc.
     */
    public static String getTierDisplay(PvPRoomsPro plugin, UUID uuid) {
        Tier tier = plugin.getTierManager().getBestTier(uuid);
        return tier != null ? tier.displayName : "?";
    }
    
    /**
     * Gets the tier color code for external use.
     * Returns: "§c" for HT, "§a" for LT, etc.
     */
    public static String getTierColor(PvPRoomsPro plugin, UUID uuid) {
        Tier tier = plugin.getTierManager().getBestTier(uuid);
        return tier != null ? tier.colour : "§7";
    }

    /**
     * Gets the LuckPerms prefix for a player using reflection.
     * Returns empty string if LuckPerms is not installed or player has no prefix.
     */
    private String getLuckPermsPrefix(Player player) {
        try {
            // Check if LuckPerms is installed
            if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
                return "";
            }

            // Use reflection to get LuckPerms API
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Method getMethod = providerClass.getMethod("get");
            Object luckPerms = getMethod.invoke(null);
            if (luckPerms == null) return "";

            // Get UserManager
            Method getUserManagerMethod = luckPerms.getClass().getMethod("getUserManager");
            Object userManager = getUserManagerMethod.invoke(luckPerms);
            if (userManager == null) return "";

            // Get User
            Method getUserMethod = userManager.getClass().getMethod("getUser", java.util.UUID.class);
            Object user = getUserMethod.invoke(userManager, player.getUniqueId());
            if (user == null) return "";

            // Get CachedData
            Method getCachedDataMethod = user.getClass().getMethod("getCachedData");
            Object cachedData = getCachedDataMethod.invoke(user);
            if (cachedData == null) return "";

            // Get MetaData
            Method getMetaDataMethod = cachedData.getClass().getMethod("getMetaData");
            Object metaData = getMetaDataMethod.invoke(cachedData);
            if (metaData == null) return "";

            // Get Prefix
            Method getPrefixMethod = metaData.getClass().getMethod("getPrefix");
            Object prefix = getPrefixMethod.invoke(metaData);
            
            return prefix != null ? prefix.toString() : "";

        } catch (Exception e) {
            // LuckPerms not available or error - silent fail
            return "";
        }
    }
}
