package com.pvprooms.listeners;

import com.pvprooms.PvPRoomsPro;
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

/**
 * Chat manager that formats all chat messages.
 * Format: [LuckPerms prefix] username >> message
 */
public class ChatListener implements Listener {

    private final PvPRoomsPro plugin;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public ChatListener(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        event.setCancelled(true);

        Player player = event.getPlayer();
        Component message = event.message();

        // Get LuckPerms prefix
        String prefix = getLuckPermsPrefix(player);

        // Build the formatted message
        // Format: [prefix] username >> message
        Component formatted = Component.empty();

        // Add prefix if exists (with colors from LuckPerms)
        if (prefix != null && !prefix.isEmpty()) {
            formatted = formatted.append(LEGACY.deserialize(prefix));
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
