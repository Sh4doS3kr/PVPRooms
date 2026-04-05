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
import com.pvprooms.gui.DuelKitSelectHolder;
import com.pvprooms.gui.DuelChallengeKitHolder;
import com.pvprooms.gui.DuelScoreSelectHolder;
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
import org.bukkit.event.inventory.InventoryDragEvent;
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

    // ── Drag protection: prevent dragging player items into protected GUIs ──

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory inv = event.getInventory();

        // Kit Editor: block any drag that touches slots outside the editor (non-OP only)
        if (inv.getHolder() instanceof KitEditorHolder) {
            if (!event.getWhoClicked().isOp()) {
                int editorSize = inv.getSize();
                for (int slot : event.getRawSlots()) {
                    if (slot >= editorSize) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
            return;
        }

        // All other custom GUIs: block drags entirely
        if (inv.getHolder() instanceof KitSelectHolder
                || inv.getHolder() instanceof AdminPanelHolder
                || inv.getHolder() instanceof ArenaConfigHolder
                || inv.getHolder() instanceof QueueModeHolder
                || inv.getHolder() instanceof DuelKitSelectHolder
                || inv.getHolder() instanceof DuelChallengeKitHolder
                || inv.getHolder() instanceof DuelScoreSelectHolder
                || inv.getHolder() instanceof KitReorderHolder) {
            event.setCancelled(true);
        }
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
            } else if (slot == QueueModeGUI.SLOT_BOT) {
                player.closeInventory();
                plugin.getBotPracticeGUI().open(player);
            }
            return;
        }

        // ── Duel Kit Selection GUI (/duel command) ──────────────────────────
        if (event.getInventory().getHolder() instanceof DuelKitSelectHolder holder) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;
            String kitName = plugin.getKitGUI().extractKitName(clicked);
            if (kitName == null || !plugin.getKitManager().kitExists(kitName)) return;
            player.closeInventory();
            // Start the duel with the selected kit
            plugin.getQueueManager().startDuelFromPair(player.getUniqueId(), kitName);
            return;
        }

        // ── Duel Score Selection GUI (/duel <player> command) ─────────────────
        if (event.getInventory().getHolder() instanceof DuelScoreSelectHolder scoreHolder) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            // Extract score from display name (e.g. "§e§l7 puntos" → 7)
            String raw = ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).trim();
            try {
                int score = Integer.parseInt(raw.split(" ")[0]);
                player.closeInventory();
                if (scoreHolder.getOnScoreSelected() != null) {
                    scoreHolder.getOnScoreSelected().accept(score);
                }
            } catch (NumberFormatException ignored) { }
            return;
        }

        // ── Duel Challenge Kit Selection GUI (/duel <player> command) ────────
        if (event.getInventory().getHolder() instanceof DuelChallengeKitHolder holder) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;
            String kitName = plugin.getKitGUI().extractKitName(clicked);
            if (kitName == null || !plugin.getKitManager().kitExists(kitName)) return;
            player.closeInventory();
            // Execute the callback with the selected kit name
            if (holder.getOnKitSelected() != null) {
                holder.getOnKitSelected().accept(kitName);
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
            // Right-click → open personal kit editor for ANY player
            if (event.isRightClick()) {
                player.closeInventory();
                plugin.getKitGUI().openPersonalReorder(player, kitName);
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
            int editorSize = event.getInventory().getSize(); // 45 slots
            var action = event.getAction();

            // Block ANY interaction from the player's own inventory (bottom half).
            // This prevents lobby/spawn items from being moved into the kit editor.
            // OPs can freely move items from their inventory.
            if (!player.isOp() && raw >= editorSize) {
                event.setCancelled(true);
                return;
            }

            // Block shift-click & hotbar swap — these can move items from player inv (non-OP)
            if (!player.isOp()
                    && (action == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || action == org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP
                    || action == org.bukkit.event.inventory.InventoryAction.HOTBAR_MOVE_AND_READD)) {
                event.setCancelled(true);
                return;
            }

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
            // Allow rearranging items within the editor GUI (slots 0-44)
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
            case AdminPanelGUI.SLOT_PRESET_KITS -> {
                player.closeInventory();
                int count = com.pvprooms.util.PresetKits.installAllPresets(plugin);
                player.sendMessage(plugin.prefix() + "§a✔ Se han instalado §e" + count + " §akits oficiales:");
                player.sendMessage("§7  • §bSword §8- §7Classic 1.9+");
                player.sendMessage("§7  • §6AxePvP §8- §7Vanilla Axe combat");
                player.sendMessage("§7  • §dNethpot §8- §7Netherite + Potions");
                player.sendMessage("§7  • §eUHC §8- §7Ultra Hardcore");
                player.sendMessage("§7  • §2SMP §8- §7Survival full gear");
                player.sendMessage("§7  • §5Crystal §8- §7Crystal PvP");
                player.sendMessage("§7  • §8Mace §8- §71.21 Mace combat");
                player.sendMessage(plugin.prefix() + "§7Usa §e/queue §7para probarlos.");
            }
            case AdminPanelGUI.SLOT_CLOSE ->
                player.closeInventory();
            default -> { /* separator or info slot — do nothing */ }
        }
    }

    // ── Prevent item theft via shift+click in plugin GUIs ─────────────────────
    
    /**
     * Global protection: Clear cursor when closing ANY plugin GUI to prevent
     * the shift+click + Escape exploit that allows stealing items.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginGuiClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        
        // Check if this is a plugin GUI (has a custom holder or known title)
        var holder = event.getInventory().getHolder();
        boolean isPluginGui = holder instanceof KitSelectHolder
                || holder instanceof DuelKitSelectHolder
                || holder instanceof DuelChallengeKitHolder
                || holder instanceof DuelScoreSelectHolder
                || holder instanceof QueueModeHolder
                || holder instanceof AdminPanelHolder
                || holder instanceof ArenaConfigHolder
                || holder instanceof com.pvprooms.gui.TrimRouletteGUI.TrimRouletteHolder
                || holder instanceof com.pvprooms.gui.TrimRouletteGUI.PreviewHolder;
        
        // Also check by title for GUIs without custom holders
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (!isPluginGui) {
            isPluginGui = title.contains("Tu Perfil")
                    || title.contains("Party")
                    || title.contains("Invitar")
                    || title.contains("Bot Practice")
                    || title.contains("RULETA")
                    || title.contains("Pág");
        }
        
        if (isPluginGui) {
            // Clear any item on cursor to prevent theft
            ItemStack cursor = player.getItemOnCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                player.setItemOnCursor(null);
            }
        }
    }

    // ── Kit Reorder save on close ─────────────────────────────────────────────

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof KitReorderHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        // Read the 36 reorder slots
        ItemStack[] newContents = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            ItemStack it = event.getInventory().getItem(i);
            newContents[i] = (it != null && it.getType() != Material.AIR) ? it.clone() : null;
        }

        if (holder.getPlayerUUID() != null) {
            // Personal save — only affects this player
            plugin.getPersonalKitManager().setPersonalLayout(holder.getPlayerUUID(), holder.getKitName(), newContents);
            player.sendMessage(plugin.prefix() + "§aKit personal §e" + holder.getKitName() + "§a guardado. §8(Solo tú)");
        } else {
            // Admin global save
            boolean saved = plugin.getKitManager().setKitFromEditor(
                    holder.getKitName(), newContents,
                    plugin.getKitManager().getKit(holder.getKitName()).getArmorContents(),
                    plugin.getKitManager().getKit(holder.getKitName()).getOffhand());
            if (saved) {
                player.sendMessage(plugin.prefix() + "§aOrden del kit §e" + holder.getKitName() + "§a guardado.");
            }
        }
    }
}
