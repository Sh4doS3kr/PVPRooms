package com.pvprooms.listeners;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

/**
 * Handles all lobby-specific events:
 * - No damage in spawn
 * - No interactions (chests, trapdoors, etc.)
 * - Lobby item usage
 * - Item drop prevention
 * - Shift+click player invite
 */
public class LobbyListener implements Listener {

    private final PvPRoomsPro plugin;

    public LobbyListener(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ── No Damage in Lobby ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isInLobby(player)) return;

        // Cancel ALL damage in lobby (fall, void, fire, etc.)
        event.setCancelled(true);
    }

    // ── Block All Interactions ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isInLobby(player)) return;

        ItemStack item = event.getItem();

        // Handle lobby items (works for everyone including OPs)
        if (item != null && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            if (handleLobbyItem(player, item)) {
                event.setCancelled(true);
                return;
            }
        }

        // OPs can interact with blocks
        if (player.isOp() || player.hasPermission("pvprooms.admin")) return;

        // Block all other block interactions for non-OPs
        if (event.getClickedBlock() != null) {
            Material blockType = event.getClickedBlock().getType();

            // Block interactable blocks
            if (isInteractable(blockType)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!isInLobby(player)) return;

        Entity rightClicked = event.getRightClicked();

        // Shift + Right-click on player = party invite
        if (rightClicked instanceof Player target && player.isSneaking()) {
            event.setCancelled(true);
            plugin.getPartyManager().invitePlayer(player, target);
            return;
        }

        // Block other entity interactions (armor stands, item frames, etc.)
        if (!(rightClicked instanceof Player) && !player.isOp()) {
            event.setCancelled(true);
        }
    }

    // ── Prevent Item Drop ──────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!isInLobby(player)) return;

        // Prevent dropping lobby items
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (plugin.getLobbyManager().isLobbyItem(dropped)) {
            event.setCancelled(true);
        }
    }

    // ── Prevent Moving Lobby Items ─────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isInLobby(player)) return;

        ItemStack clicked = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // Prevent moving lobby items
        if (clicked != null && plugin.getLobbyManager().isLobbyItem(clicked)) {
            event.setCancelled(true);
            return;
        }
        if (cursor != null && plugin.getLobbyManager().isLobbyItem(cursor)) {
            event.setCancelled(true);
        }
    }

    // ── Swap Hand Prevention ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!isInLobby(player)) return;

        // Prevent swapping lobby items
        if (plugin.getLobbyManager().isLobbyItem(event.getMainHandItem()) ||
            plugin.getLobbyManager().isLobbyItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private boolean isInLobby(Player player) {
        String lobbyWorld = plugin.getConfig().getString("general.lobby-world", "world");
        return player.getWorld().getName().equals(lobbyWorld);
    }

    private boolean handleLobbyItem(Player player, ItemStack item) {
        var lm = plugin.getLobbyManager();

        if (lm.isQueueItem(item)) {
            // Open queue GUI
            plugin.getServer().dispatchCommand(player, "queue");
            return true;
        }

        if (lm.isQuickMatchItem(item)) {
            // Quick match - join random queue
            handleQuickMatch(player);
            return true;
        }

        if (lm.isPartyItem(item)) {
            // Open party GUI
            plugin.getPartyGUI().open(player);
            return true;
        }

        if (lm.isProfileItem(item)) {
            // Open profile GUI
            plugin.getProfileGUI().open(player);
            return true;
        }

        if (lm.isSettingsItem(item)) {
            // TODO: Open settings GUI
            player.sendMessage(plugin.prefix() + "§7Ajustes próximamente...");
            return true;
        }

        return false;
    }

    private void handleQuickMatch(Player player) {
        // Get a random kit from available kits
        var kits = plugin.getKitManager().getKitNames();
        if (kits.isEmpty()) {
            player.sendMessage(plugin.prefix() + "§cNo hay kits disponibles.");
            return;
        }

        // Pick a random kit
        String randomKit = kits.get((int) (Math.random() * kits.size()));

        // Check if player is already in queue
        if (plugin.getQueueManager().isInQueue(player.getUniqueId())) {
            player.sendMessage(plugin.prefix() + "§cYa estás en cola. Usa §e/pvpleave §cpara salir.");
            return;
        }

        // Check if player is in a duel
        if (plugin.getDuelManager().isInDuel(player.getUniqueId())) {
            player.sendMessage(plugin.prefix() + "§cNo puedes unirte a cola durante un duelo.");
            return;
        }

        // Add to queue with random kit
        plugin.getQueueManager().addToQueue(player, randomKit);
        player.sendMessage(plugin.prefix() + "§a¡Partida rápida! Buscando rival con §e" + randomKit + "§a...");
    }

    private boolean isInteractable(Material material) {
        String name = material.name();
        return name.contains("CHEST") ||
               name.contains("BARREL") ||
               name.contains("FURNACE") ||
               name.contains("SMOKER") ||
               name.contains("BLAST") ||
               name.contains("HOPPER") ||
               name.contains("DROPPER") ||
               name.contains("DISPENSER") ||
               name.contains("SHULKER") ||
               name.contains("TRAPDOOR") ||
               name.contains("DOOR") ||
               name.contains("GATE") ||
               name.contains("BUTTON") ||
               name.contains("LEVER") ||
               name.contains("PRESSURE_PLATE") ||
               name.contains("ANVIL") ||
               name.contains("ENCHANTING") ||
               name.contains("BREWING") ||
               name.contains("GRINDSTONE") ||
               name.contains("STONECUTTER") ||
               name.contains("LOOM") ||
               name.contains("CARTOGRAPHY") ||
               name.contains("SMITHING") ||
               name.contains("BEACON") ||
               name.contains("LECTERN") ||
               name.contains("COMPOSTER") ||
               name.contains("BED") ||
               name.contains("RESPAWN_ANCHOR") ||
               name.contains("CRAFTING") ||
               material == Material.NOTE_BLOCK ||
               material == Material.JUKEBOX ||
               material == Material.BELL ||
               material == Material.CAMPFIRE ||
               material == Material.SOUL_CAMPFIRE ||
               material == Material.FLOWER_POT ||
               material == Material.DECORATED_POT ||
               material == Material.CHISELED_BOOKSHELF;
    }
}
