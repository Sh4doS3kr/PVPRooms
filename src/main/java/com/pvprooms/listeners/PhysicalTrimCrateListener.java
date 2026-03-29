package com.pvprooms.listeners;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.gui.TrimRouletteGUI;
import com.pvprooms.model.ArmorPiece;
import com.pvprooms.model.PhysicalTrimCrate;
import com.pvprooms.model.Trim;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles interactions with physical trim crates placed in the world.
 */
public class PhysicalTrimCrateListener implements Listener {

    private final PvPRoomsPro plugin;

    public PhysicalTrimCrateListener(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        if (!PhysicalTrimCrate.isPhysicalCrate(block)) return;

        // Check if player has a key
        ItemStack itemInHand = event.getItem();
        if (!isCrateKey(itemInHand)) {
            event.getPlayer().sendMessage(plugin.prefix() + "§cNecesitas una §6Llave de Crate §7para abrir esta crate.");
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        String crateType = PhysicalTrimCrate.getCrateType(block);
        ArmorPiece piece = PhysicalTrimCrate.getArmorPiece(block);
        boolean legendary = PhysicalTrimCrate.isLegendary(block);

        if (piece == null || crateType == null) return;

        // Consume one key from hand
        if (itemInHand.getAmount() > 1) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // Open the roulette GUI
        plugin.getTrimRouletteGUI().openRoulette(player, piece, crateType, legendary);
    }

    /** Checks if an item is a crate key */
    private boolean isCrateKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getType() == Material.TRIPWIRE_HOOK && 
               item.getItemMeta().getDisplayName() != null &&
               item.getItemMeta().getDisplayName().contains("Llave de Crate");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!PhysicalTrimCrate.isPhysicalCrate(block)) return;

        // Only allow breaking with proper permission
        if (!event.getPlayer().hasPermission("pvprooms.admin")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.prefix() + "§cNo puedes romper crates físicas.");
            return;
        }

        // Drop the crate item when broken
        String crateType = PhysicalTrimCrate.getCrateType(block);
        ArmorPiece piece = PhysicalTrimCrate.getArmorPiece(block);
        boolean legendary = PhysicalTrimCrate.isLegendary(block);

        if (piece != null && crateType != null) {
            ItemStack crateItem = PhysicalTrimCrate.createCrateItem(crateType, piece, legendary);
            block.getWorld().dropItemNaturally(block.getLocation(), crateItem);
        }
    }
}
