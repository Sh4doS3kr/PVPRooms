package com.pvprooms.listeners;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.gui.AdminPanelGUI;
import com.pvprooms.gui.AdminPanelHolder;
import com.pvprooms.gui.ArenaConfigGUI;
import com.pvprooms.gui.ArenaConfigHolder;
import com.pvprooms.gui.KitEditorGUI;
import com.pvprooms.gui.KitEditorHolder;
import com.pvprooms.gui.KitGUI;
import com.pvprooms.gui.KitSelectHolder;
import com.pvprooms.gui.KitReorderHolder;
import com.pvprooms.gui.QueueModeGUI;
import com.pvprooms.gui.QueueModeHolder;
import com.pvprooms.model.ArenaTemplate;
import com.pvprooms.model.Duel;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
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

        // ── Queue Mode selection GUI (ELO vs TIER) ────────────────────────
        if (event.getInventory().getHolder() instanceof QueueModeHolder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == QueueModeGUI.SLOT_ELO) {
                player.closeInventory();
                plugin.getKitGUI().open(player);
            } else if (slot == QueueModeGUI.SLOT_TIER) {
                player.closeInventory();
                plugin.getKitGUI().openTierMode(player);
            }
            return;
        }

        // ── Kit Selection GUI (ELO o TIER) ─────────────────────────────────
        if (event.getInventory().getHolder() instanceof KitSelectHolder holder) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) return;
            String kitName = plugin.getKitGUI().extractKitName(clicked);
            if (kitName == null || !plugin.getKitManager().kitExists(kitName)) return;
            // Right-click → open reorder GUI (admin only)
            if (event.isRightClick() && player.hasPermission("pvprooms.admin")) {
                player.closeInventory();
                plugin.getKitGUI().openReorder(player, kitName);
                return;
            }
            player.closeInventory();
            if (holder.isTierMode()) {
                boolean joined = plugin.getQueueManager().addToTierQueue(player, kitName);
                if (joined) {
                    com.pvprooms.model.Tier myTier = plugin.getTierManager().getTier(player.getUniqueId(), kitName);
                    player.sendMessage(plugin.prefix() + "§a¡Entraste en la cola TIER "
                            + myTier.formatted() + "§a de §e" + kitName + "§a! Buscando rival...");
                    plugin.getScoreboardManager().showQueueScoreboard(player, kitName);
                } else if (plugin.getDuelManager().isInDuel(player.getUniqueId())) {
                    player.sendMessage(plugin.prefix() + "§cYa estás en un duelo.");
                } else if (plugin.getQueueManager().isInQueue(player.getUniqueId())) {
                    player.sendMessage(plugin.prefix() + "§cYa estás en la cola.");
                } else {
                    player.sendMessage(plugin.prefix() + "§cCooldown activo. Espera un momento.");
                }
            } else {
                boolean joined = plugin.getQueueManager().addToQueue(player, kitName);
                if (joined) {
                    player.sendMessage(plugin.prefix() + "§a¡Entraste en la cola ELO de §e" + kitName + "§a! Buscando rival...");
                    plugin.getScoreboardManager().showQueueScoreboard(player, kitName);
                } else if (plugin.getDuelManager().isInDuel(player.getUniqueId())) {
                    player.sendMessage(plugin.prefix() + "§cYa estás en un duelo.");
                } else if (plugin.getQueueManager().isInQueue(player.getUniqueId())) {
                    player.sendMessage(plugin.prefix() + "§cYa estás en la cola.");
                } else {
                    player.sendMessage(plugin.prefix() + "§cCooldown activo. Espera un momento.");
                }
            }
            return;
        }

        // ── Arena Config GUI ──────────────────────────────────────────
        if (event.getInventory().getHolder() instanceof ArenaConfigHolder holder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            ArenaTemplate template = holder.getTemplate();
            boolean changed = false;
            switch (slot) {
                case ArenaConfigGUI.SLOT_EXPLOSIONS  -> { template.setAllowExplosions(!template.isAllowExplosions());  changed = true; }
                case ArenaConfigGUI.SLOT_BLOCK_BREAK -> { template.setAllowBlockBreak(!template.isAllowBlockBreak());  changed = true; }
                case ArenaConfigGUI.SLOT_BLOCK_PLACE -> { template.setAllowBlockPlace(!template.isAllowBlockPlace());  changed = true; }
                case ArenaConfigGUI.SLOT_CLOSE       -> { player.closeInventory(); return; }
            }
            if (changed) {
                plugin.getArenaManager().saveArenas();
                plugin.getArenaConfigGUI().refreshToggle(event.getInventory(), template, slot);
                String state = switch (slot) {
                    case ArenaConfigGUI.SLOT_EXPLOSIONS  -> (template.isAllowExplosions()  ? "§aACTIVADO" : "§cDESACTIVADO") + " §7Explosiones";
                    case ArenaConfigGUI.SLOT_BLOCK_BREAK -> (template.isAllowBlockBreak()  ? "§aACTIVADO" : "§cDESACTIVADO") + " §7Romper bloques";
                    case ArenaConfigGUI.SLOT_BLOCK_PLACE -> (template.isAllowBlockPlace()  ? "§aACTIVADO" : "§cDESACTIVADO") + " §7Colocar bloques";
                    default -> "";
                };
                player.sendMessage(plugin.prefix() + state + "§7 en §e" + template.getName() + "§7.");
            }
            return;
        }

        // ── Admin Panel GUI ────────────────────────────────────────────────
        if (event.getInventory().getHolder() instanceof AdminPanelHolder) {
            event.setCancelled(true);
            int raw = event.getRawSlot();
            handleAdminPanelClick(player, raw);
            return;
        }

        // ── Kit Editor GUI ─────────────────────────────────────────────────
        if (event.getInventory().getHolder() instanceof KitEditorHolder holder) {
            int raw = event.getRawSlot();

            // Separator panes — always block
            if (raw == KitEditorGUI.PANE_SLOT_1 || raw == KitEditorGUI.PANE_SLOT_2) {
                event.setCancelled(true);
                return;
            }
            // SAVE
            if (raw == KitEditorGUI.SAVE_SLOT) {
                event.setCancelled(true);
                saveKitFromEditor(event.getInventory(), holder, player);
                player.closeInventory();
                return;
            }
            // CANCEL
            if (raw == KitEditorGUI.CANCEL_SLOT) {
                event.setCancelled(true);
                player.sendMessage(plugin.prefix() + "§7Edición cancelada.");
                player.closeInventory();
                return;
            }
            // Allow all other clicks (inventory + armor slots)
            return;
        }

        // ── Kit Reorder GUI ────────────────────────────────────────
        if (event.getInventory().getHolder() instanceof KitReorderHolder) {
            int raw       = event.getRawSlot();
            int chestSize = event.getInventory().getSize();
            var action    = event.getAction();
            // Cancel any action that would move items outside the chest area
            if (raw < 0 || raw >= chestSize
                    || action == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || action == org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP
                    || action == org.bukkit.event.inventory.InventoryAction.HOTBAR_MOVE_AND_READD
                    || action == org.bukkit.event.inventory.InventoryAction.DROP_ALL_CURSOR
                    || action == org.bukkit.event.inventory.InventoryAction.DROP_ONE_CURSOR
                    || action == org.bukkit.event.inventory.InventoryAction.DROP_ALL_SLOT
                    || action == org.bukkit.event.inventory.InventoryAction.DROP_ONE_SLOT) {
                event.setCancelled(true);
            }
            return;
        }

        // ── Prevent inventory manipulation inside duel worlds ──────────────
        Duel duel = plugin.getDuelManager().getDuelByPlayer(player.getUniqueId());
        if (duel != null) {
            if (event.getClickedInventory() != null
                    && event.getClickedInventory() != player.getInventory()) {
                event.setCancelled(true);
            }
        }
    }

    // ── Kit editor: save logic ─────────────────────────────────────────────

    private void saveKitFromEditor(Inventory inv, KitEditorHolder holder, Player player) {
        // Slots 0-35 → kit inventory
        ItemStack[] contents = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            contents[i] = isReal(it) ? it.clone() : null;
        }

        // Slots 36-39 → armor (Bukkit order: [0]=boots [1]=legs [2]=chest [3]=helmet)
        ItemStack[] armor = new ItemStack[4];
        armor[3] = toArmor(inv.getItem(KitEditorGUI.HELMET_SLOT));     // slot 36
        armor[2] = toArmor(inv.getItem(KitEditorGUI.CHESTPLATE_SLOT)); // slot 37
        armor[1] = toArmor(inv.getItem(KitEditorGUI.LEGGINGS_SLOT));   // slot 38
        armor[0] = toArmor(inv.getItem(KitEditorGUI.BOOTS_SLOT));      // slot 39

        ItemStack offhand = toArmor(inv.getItem(KitEditorGUI.OFFHAND_SLOT)); // slot 40

        plugin.getKitManager().setKitFromEditor(holder.getKitName(), contents, armor, offhand);
        player.sendMessage(plugin.prefix() + "§a¡Kit §e" + holder.getKitName() + "§a guardado correctamente!");
    }

    /** True if the item is a real item (not null, not AIR, not a placeholder pane). */
    private boolean isReal(ItemStack item) {
        return item != null && item.getType() != Material.AIR
                && item.getType() != KitEditorGUI.PLACEHOLDER_MAT
                && item.getType() != Material.GRAY_STAINED_GLASS_PANE
                && item.getType() != Material.LIME_WOOL
                && item.getType() != Material.RED_WOOL;
    }

    /** Returns null if the slot holds a placeholder, otherwise the cloned item. */
    private ItemStack toArmor(ItemStack item) {
        return isReal(item) ? item.clone() : null;
    }

    // ── Admin panel click logic ────────────────────────────────────────────

    private void handleAdminPanelClick(Player player, int slot) {
        switch (slot) {
            case AdminPanelGUI.SLOT_ELO_RESET ->
                player.sendMessage(plugin.prefix() + "§7Uso: §e/admin elo reset <jugador>");
            case AdminPanelGUI.SLOT_ELO_SET ->
                player.sendMessage(plugin.prefix() + "§7Uso: §e/admin elo set <jugador> <valor>");
            case AdminPanelGUI.SLOT_ELO_GET ->
                player.sendMessage(plugin.prefix() + "§7Uso: §e/admin elo get <jugador>");
            case AdminPanelGUI.SLOT_ELO_RESETALL -> {
                player.closeInventory();
                plugin.getEloManager().resetAllElo();
                player.sendMessage(plugin.prefix() + "§c⚠ ELO de TODOS los jugadores restablecido a §e"
                        + plugin.getEloManager().getDefaultElo() + "§c.");
                plugin.getLogger().warning(player.getName() + " ha restablecido el ELO de todos los jugadores.");
            }
            case AdminPanelGUI.SLOT_KICK ->
                player.sendMessage(plugin.prefix() + "§7Uso: §e/admin kick <jugador>");
            case AdminPanelGUI.SLOT_FORCEEND ->
                player.sendMessage(plugin.prefix() + "§7Uso: §e/admin forceend <jugador>");
            case AdminPanelGUI.SLOT_RELOAD -> {
                plugin.reloadConfig();
                player.sendMessage(plugin.prefix() + "§aconfig.yml recargado correctamente.");
            }
            case AdminPanelGUI.SLOT_INFO -> {
                player.closeInventory();
                player.performCommand("admin info");
            }
            case AdminPanelGUI.SLOT_CLOSE ->
                player.closeInventory();
            default -> { /* separator or info slot — do nothing */ }
        }
    }

    // ── Inventory drop prevention during duels ─────────────────────────────

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof KitReorderHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        // Read the 36 reorder slots and save as the new kit contents order
        ItemStack[] newContents = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            ItemStack it = event.getInventory().getItem(i);
            newContents[i] = (it != null && it.getType() != Material.AIR) ? it.clone() : null;
        }
        boolean saved = plugin.getKitManager().setKitFromEditor(
                holder.getKitName(), newContents,
                plugin.getKitManager().getKit(holder.getKitName()).getArmorContents(),
                plugin.getKitManager().getKit(holder.getKitName()).getOffhand());
        if (saved) {
            player.sendMessage(plugin.prefix() + "§aOrden del kit §e" + holder.getKitName() + "§a guardado.");
        }
    }
}
