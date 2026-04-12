package com.pvprooms.listeners;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.commands.PhysicalCrateCommand;
import com.pvprooms.gui.TrimRouletteGUI;
import com.pvprooms.model.ArmorPiece;
import com.pvprooms.model.PhysicalTrimCrate;
import com.pvprooms.model.TrimCrate;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles interactions with physical trim crates placed in the world.
 */
public class PhysicalTrimCrateListener implements Listener {

    private final PvPRoomsPro plugin;

    public PhysicalTrimCrateListener(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        
        Player player = event.getPlayer();
        
        // ═══════════════════════════════════════════════════════════════════
        // LEFT CLICK: Show preview of possible trims
        // ═══════════════════════════════════════════════════════════════════
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && PhysicalTrimCrate.isPhysicalCrate(block)) {
            event.setCancelled(true);
            
            ArmorPiece piece = PhysicalTrimCrate.getArmorPiece(block);
            boolean legendary = PhysicalTrimCrate.isLegendary(block);
            
            if (piece != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 1.2f);
                plugin.getTrimRouletteGUI().openPreview(player, piece, legendary);
            }
            return;
        }
        
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        // ═══════════════════════════════════════════════════════════════════
        // SETUP MODE: Convert block to crate
        // ═══════════════════════════════════════════════════════════════════
        if (PhysicalCrateCommand.isInSetupMode(player.getUniqueId())) {
            event.setCancelled(true);
            
            var setupData = PhysicalCrateCommand.getSetupData(player.getUniqueId());
            if (setupData == null) return;
            
            // Check if block is a shulker box (required for persistent data)
            if (!isShulkerBox(block.getType())) {
                // Replace the block with a shulker box
                Material shulkerType = setupData.legendary() ? Material.PURPLE_SHULKER_BOX : Material.CYAN_SHULKER_BOX;
                block.setType(shulkerType);
            }
            
            // Set persistent data on the shulker
            if (block.getState() instanceof ShulkerBox shulker) {
                shulker.getPersistentDataContainer().set(
                    PhysicalTrimCrate.getCrateTypeKey(), PersistentDataType.STRING, "normal");
                shulker.getPersistentDataContainer().set(
                    PhysicalTrimCrate.getArmorPieceKey(), PersistentDataType.STRING, setupData.piece().name());
                shulker.getPersistentDataContainer().set(
                    PhysicalTrimCrate.getLegendaryKey(), PersistentDataType.BYTE, (byte) (setupData.legendary() ? 1 : 0));
                shulker.setCustomName("§d§l✦ " + setupData.piece().getSymbol() + " §fCrate de " + setupData.piece().getDisplayName() + 
                    (setupData.legendary() ? " §5§lLEGENDARIA" : "") + " §d§l✦");
                shulker.update();
                
                player.sendMessage(plugin.prefix() + "§a§l¡CRATE COLOCADA!");
                player.sendMessage(plugin.prefix() + "§7Crate de §d" + setupData.piece().getDisplayName() + 
                    (setupData.legendary() ? " §5§lLEGENDARIA" : "") + " §7creada en §e" + 
                    block.getX() + ", " + block.getY() + ", " + block.getZ());
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                
                // Exit setup mode
                PhysicalCrateCommand.exitSetupMode(player.getUniqueId());
            } else {
                player.sendMessage(plugin.prefix() + "§cError al crear la crate. Intenta con otro bloque.");
            }
            return;
        }
        
        // ═══════════════════════════════════════════════════════════════════
        // NORMAL MODE: Open crate with key
        // ═══════════════════════════════════════════════════════════════════
        if (!PhysicalTrimCrate.isPhysicalCrate(block)) return;

        // Prevent shulker from opening normally
        event.setCancelled(true);
        
        String crateType = PhysicalTrimCrate.getCrateType(block);
        ArmorPiece piece = PhysicalTrimCrate.getArmorPiece(block);
        boolean legendary = PhysicalTrimCrate.isLegendary(block);

        if (piece == null || crateType == null) return;
        
        // Check if player has a key
        ItemStack itemInHand = event.getItem();
        if (!isCrateKey(itemInHand)) {
            player.sendMessage(plugin.prefix() + "§cNecesitas una §6" + piece.getDisplayName() + " Key §cpara abrir esta crate.");
            player.sendMessage(plugin.prefix() + "§7Obtén llaves completando partidas o comprándolas.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            return;
        }
        
        // Generic TrimCrate keys (e.g. from ELO rewards) work on any crate piece
        boolean isGenericKey = TrimCrate.isKey(itemInHand);
        if (!isGenericKey) {
            // Piece-specific key: must match the crate's armor piece
            ArmorPiece keyPiece = PhysicalCrateCommand.getKeyPiece(itemInHand);
            if (keyPiece == null || keyPiece != piece) {
                String keyPieceName = keyPiece != null ? keyPiece.getDisplayName() : "desconocida";
                player.sendMessage(plugin.prefix() + "§cEsta llave es de §6" + keyPieceName + "§c, necesitas una §6" + piece.getDisplayName() + " Key§c.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                return;
            }
        }

        // Consume one key from hand
        if (itemInHand.getAmount() > 1) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
        
        // Play opening sound
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);

        // Open the roulette GUI
        plugin.getTrimRouletteGUI().openRoulette(player, piece, crateType, legendary);
    }
    
    private boolean isShulkerBox(Material material) {
        return material.name().contains("SHULKER_BOX");
    }

    /** Checks if an item is a crate key */
    private boolean isCrateKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        // Check by piece-specific key tag (new system)
        if (PhysicalCrateCommand.getKeyPiece(item) != null) return true;
        // Check by TrimCrate key tag (legacy)
        if (TrimCrate.isKey(item)) return true;
        // Fallback: check by material and name
        return item.getType() == Material.TRIPWIRE_HOOK && 
               item.getItemMeta().getDisplayName() != null &&
               item.getItemMeta().getDisplayName().contains("Key");
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // When placing a shulker box crate item, transfer persistent data
        ItemStack item = event.getItemInHand();
        if (!isShulkerBox(item.getType())) return;
        if (!item.hasItemMeta()) return;
        
        // Check if item has crate data
        var meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(PhysicalTrimCrate.getCrateTypeKey(), PersistentDataType.STRING)) return;
        
        Block block = event.getBlockPlaced();
        if (!(block.getState() instanceof ShulkerBox shulker)) return;
        
        // Transfer data from item to block
        String type = meta.getPersistentDataContainer().get(PhysicalTrimCrate.getCrateTypeKey(), PersistentDataType.STRING);
        String piece = meta.getPersistentDataContainer().get(PhysicalTrimCrate.getArmorPieceKey(), PersistentDataType.STRING);
        Byte legendary = meta.getPersistentDataContainer().get(PhysicalTrimCrate.getLegendaryKey(), PersistentDataType.BYTE);
        
        shulker.getPersistentDataContainer().set(PhysicalTrimCrate.getCrateTypeKey(), PersistentDataType.STRING, type != null ? type : "normal");
        shulker.getPersistentDataContainer().set(PhysicalTrimCrate.getArmorPieceKey(), PersistentDataType.STRING, piece != null ? piece : "HELMET");
        shulker.getPersistentDataContainer().set(PhysicalTrimCrate.getLegendaryKey(), PersistentDataType.BYTE, legendary != null ? legendary : 0);
        shulker.setCustomName(meta.getDisplayName());
        shulker.update();
        
        event.getPlayer().sendMessage(plugin.prefix() + "§a¡Crate colocada! Usa una §6Llave de Crate §apara abrirla.");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!PhysicalTrimCrate.isPhysicalCrate(block)) return;

        // Always cancel breaking crates - use left click to preview instead
        event.setCancelled(true);
        
        Player player = event.getPlayer();
        
        // Show preview instead of breaking
        ArmorPiece piece = PhysicalTrimCrate.getArmorPiece(block);
        boolean legendary = PhysicalTrimCrate.isLegendary(block);
        
        if (piece != null) {
            player.sendMessage(plugin.prefix() + "§7Usa §eclick izquierdo §7para ver lo que puedes ganar.");
            player.sendMessage(plugin.prefix() + "§7Usa una §6" + piece.getDisplayName() + " Key §7para abrir.");
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // Prevent closing roulette GUI during animation
    // ═══════════════════════════════════════════════════════════════════
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        
        // If player closes during roulette animation → skip and deliver result
        if (TrimRouletteGUI.hasActiveAnimation(player.getUniqueId())) {
            TrimRouletteGUI.TrimRouletteHolder holder = TrimRouletteGUI.getActiveHolder(player.getUniqueId());
            if (holder != null) {
                plugin.getTrimRouletteGUI().skipAnimation(player, holder);
            } else {
                // Safety: clear stuck animation flag
                TrimRouletteGUI.endAnimation(player.getUniqueId());
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        // Prevent clicking during roulette animation
        if (event.getInventory().getHolder() instanceof TrimRouletteGUI.TrimRouletteHolder holder) {
            event.setCancelled(true);
            return;
        }
        
        // Handle preview GUI clicks (pagination)
        if (event.getInventory().getHolder() instanceof TrimRouletteGUI.PreviewHolder holder) {
            event.setCancelled(true);
            
            if (!(event.getWhoClicked() instanceof Player player)) return;
            
            int slot = event.getRawSlot();
            
            // Close button
            if (slot == 49) {
                TrimRouletteGUI.removePreviewData(player.getUniqueId());
                player.closeInventory();
                return;
            }
            
            // Previous page (slot 45)
            if (slot == 45 && holder.getPage() > 0) {
                plugin.getTrimRouletteGUI().openPreview(player, holder.getPiece(), holder.isLegendary(), holder.getPage() - 1);
                return;
            }
            
            // Next page (slot 53)
            TrimRouletteGUI.PreviewData data = TrimRouletteGUI.getPreviewData(player.getUniqueId());
            if (slot == 53 && data != null && holder.getPage() < data.getTotalPages() - 1) {
                plugin.getTrimRouletteGUI().openPreview(player, holder.getPiece(), holder.isLegendary(), holder.getPage() + 1);
                return;
            }
        }
    }
}
