package com.pvprooms.gui;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.managers.TrimManager;
import com.pvprooms.model.ArmorPiece;
import com.pvprooms.model.Trim;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * GUI for the trim roulette when opening physical crates.
 * Shows a spinning animation and gives a random trim.
 */
public class TrimRouletteGUI {

    private final PvPRoomsPro plugin;
    private final Random random = new Random();

    public TrimRouletteGUI(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    public void openRoulette(Player player, ArmorPiece piece, String crateType, boolean legendary) {
        TrimRouletteHolder holder = new TrimRouletteHolder(player.getUniqueId(), piece, crateType, legendary);
        Inventory inv = Bukkit.createInventory(holder, 54, "§5✦ §d§lRULETA DE TRIMS §5✦");
        holder.setInventory(inv);

        // Fill with spinning animation
        fillRoulette(inv, piece, legendary);

        player.openInventory(inv);

        // Schedule the spin animation
        startSpinAnimation(player, holder, piece, crateType, legendary);
    }

    private void fillRoulette(Inventory inv, ArmorPiece piece, boolean legendary) {
        // Fill border with decorative items
        ItemStack border = createBorderItem();
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            }
        }

        // Center area (slots 13, 22, 31, 40) will be the spinning items
        inv.setItem(13, createSpinItem(Material.PAPER, "§7Girando..."));
        inv.setItem(22, createSpinItem(Material.PAPER, "§7Girando..."));
        inv.setItem(31, createSpinItem(Material.PAPER, "§7Girando..."));
        inv.setItem(40, createSpinItem(Material.PAPER, "§7Girando..."));

        // Info panel
        inv.setItem(4, createInfoItem(piece));
        inv.setItem(49, createStartItem());
    }

    private void startSpinAnimation(Player player, TrimRouletteHolder holder, ArmorPiece piece, String crateType, boolean legendary) {
        // Run animation for 3 seconds
        for (int i = 0; i < 30; i++) {
            final int tick = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.getOpenInventory().getTopInventory().equals(holder.getInventory())) return;

                // Update spinning items
                updateSpinningItems(holder.getInventory(), piece, legendary, tick);
            }, i * 2); // Every 2 ticks (100ms)
        }

        // Final result after 3 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.getOpenInventory().getTopInventory().equals(holder.getInventory())) return;

            // Generate final trim
            Trim trim = plugin.getTrimManager().randomTrimForPiece(piece, legendary);
            
            // Unlock the trim for the player
            plugin.getTrimManager().unlockTrim(player.getUniqueId(), piece, trim.getPattern());
            
            // Show final result
            showFinalResult(holder.getInventory(), piece, trim);
            
            // Apply trim if player has armor equipped
            plugin.getTrimManager().applyTrimInstantly(player, piece, trim);
            
            // Send message
            String col = plugin.getTrimManager().patternColour(trim.getPattern());
            String mc = plugin.getTrimManager().materialColour(trim.getMaterial());
            player.sendMessage(plugin.prefix() + "§a¡Felicidades! Obtuviste: " + col + trim.getPattern() + " §7de §r" + mc + trim.getMaterial() + " §7para tu " + piece.getDisplayName());
            
            // Close after 2 seconds
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.closeInventory();
                }
            }, 40);
        }, 60); // After 3 seconds (60 ticks)
    }

    private void updateSpinningItems(Inventory inv, ArmorPiece piece, boolean legendary, int tick) {
        // Create random spinning items
        inv.setItem(13, createSpinItem(getRandomMaterial(), "§7Girando..."));
        inv.setItem(22, createSpinItem(getRandomMaterial(), "§7Girando..."));
        inv.setItem(31, createSpinItem(getRandomMaterial(), "§7Girando..."));
        inv.setItem(40, createSpinItem(getRandomMaterial(), "§7Girando..."));
    }

    private void showFinalResult(Inventory inv, ArmorPiece piece, Trim trim) {
        String col = plugin.getTrimManager().patternColour(trim.getPattern());
        String mc = plugin.getTrimManager().materialColour(trim.getMaterial());
        
        ItemStack result = createGlowItem(piece.getDisplayMaterial(), 
            piece.getSymbol() + " " + col + "§l" + piece.getDisplayName(),
            List.of(
                "§8━━━━━━━━━━━━━━━━━━",
                "§7Patrón: " + col + "§l" + trim.getPattern(),
                "§7Material: " + mc + "§l" + trim.getMaterial(),
                "§8━━━━━━━━━━━━━━━━━━",
                "",
                "§a✦ ¡TRIM DESBLOQUEADO! ✦",
                "",
                "§7Se ha aplicado a tu armadura"
            ));
        
        inv.setItem(22, result);
        
        // Clear other spinning slots
        inv.setItem(13, null);
        inv.setItem(31, null);
        inv.setItem(40, null);
    }

    private ItemStack createBorderItem() {
        ItemStack item = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§r");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSpinItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem(ArmorPiece piece) {
        return createGlowItem(Material.NETHER_STAR, "§b§lINFO",
            List.of(
                "§7Abriendo crate para:",
                "§f" + piece.getDisplayName(),
                "",
                "§7La ruleta te dará un",
                "§7trim aleatorio para esta pieza."
            ));
    }

    private ItemStack createStartItem() {
        return createGlowItem(Material.EMERALD, "§a§lRULETA GIRANDO",
            List.of(
                "§7¡La ruleta está girando!",
                "§7Espera el resultado...",
                "",
                "§e✨ ¡Suerte! ✨"
            ));
    }

    private ItemStack createGlowItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private Material getRandomMaterial() {
        Material[] materials = {Material.PAPER, Material.MAP, Material.BOOK, Material.FEATHER, Material.STRING};
        return materials[random.nextInt(materials.length)];
    }

    public static class TrimRouletteHolder implements InventoryHolder {
        private final UUID playerId;
        private final ArmorPiece piece;
        private final String crateType;
        private final boolean legendary;
        private Inventory inventory;

        public TrimRouletteHolder(UUID playerId, ArmorPiece piece, String crateType, boolean legendary) {
            this.playerId = playerId;
            this.piece = piece;
            this.crateType = crateType;
            this.legendary = legendary;
        }

        public UUID getPlayerId() { return playerId; }
        public ArmorPiece getPiece() { return piece; }
        public String getCrateType() { return crateType; }
        public boolean isLegendary() { return legendary; }

        @Override
        public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
    }
}
