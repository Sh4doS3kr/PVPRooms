package com.pvprooms.listeners;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.gui.KitGUI;
import com.pvprooms.model.Duel;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles inventory-related events.
 *
 * Responsibilities:
 *  - Intercepts clicks inside the Kit Selection GUI and joins the player into queue
 *  - Prevents players in duels from dropping or moving items via inventory
 */
public class InventoryListener implements Listener {

    private final PvPRoomsPro plugin;

    public InventoryListener(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ── Kit GUI click ──────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = ChatColor.stripColor(event.getView().getTitle());

        // Detect Kit Selection GUI
        if (title.contains("KIT_SELECT")) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) return;

            String kitName = plugin.getKitGUI().extractKitName(clicked);
            if (kitName == null || !plugin.getKitManager().kitExists(kitName)) return;

            // Close GUI first, then add to queue
            player.closeInventory();

            boolean joined = plugin.getQueueManager().addToQueue(player, kitName);
            if (joined) {
                player.sendMessage(plugin.prefix() + "§aYou joined the §e" + kitName + " §aqueue! Searching for an opponent...");
                plugin.getScoreboardManager().showQueueScoreboard(player, kitName);
            } else if (plugin.getDuelManager().isInDuel(player.getUniqueId())) {
                player.sendMessage(plugin.prefix() + "§cYou are already in a duel.");
            } else if (plugin.getQueueManager().isInQueue(player.getUniqueId())) {
                player.sendMessage(plugin.prefix() + "§cYou are already in the queue.");
            } else {
                player.sendMessage(plugin.prefix() + "§cCooldown active. Please wait a moment.");
            }
            return;
        }

        // Prevent inventory manipulation inside duel worlds
        Duel duel = plugin.getDuelManager().getDuelByPlayer(player.getUniqueId());
        if (duel != null) {
            // Allow item clicks inside the player's own inventory but cancel upper inventory
            // (e.g. chest access) – prevent exploitation
            if (event.getClickedInventory() != null
                    && event.getClickedInventory() != player.getInventory()) {
                event.setCancelled(true);
            }
        }
    }

    // ── Inventory drop prevention during duels ─────────────────────────────

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Nothing to clean up currently; reserved for future drag-drop prevention
    }
}
