package com.pvprooms.gui;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.managers.TrimManager;
import com.pvprooms.model.ArmorPiece;
import com.pvprooms.model.Trim;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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
        // Horizontal spinning slots (row 3: slots 19-25)
        int[] spinSlots = {19, 20, 21, 22, 23, 24, 25};
        
        // Pre-generate some random trims for the spin animation
        List<Trim> spinTrims = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            spinTrims.add(plugin.getTrimManager().randomTrimForPiece(piece, legendary));
        }
        
        // Generate final trim now (so we can show it at the end)
        Trim finalTrim = plugin.getTrimManager().randomTrimForPiece(piece, legendary);
        
        // Run animation - starts fast, slows down
        int totalTicks = 0;
        for (int i = 0; i < 40; i++) {
            final int tick = i;
            // Speed decreases as animation progresses
            int delay = i < 20 ? 2 : (i < 30 ? 3 : (i < 35 ? 4 : 6));
            totalTicks += delay;
            
            final int currentTotalTicks = totalTicks;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.getOpenInventory().getTopInventory().equals(holder.getInventory())) return;

                // Update spinning items horizontally
                updateHorizontalSpin(holder.getInventory(), spinSlots, spinTrims, tick, piece, finalTrim);
                
                // Play tick sound (pitch increases as it slows)
                float pitch = 0.5f + (tick / 40f) * 1.5f;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, pitch);
            }, currentTotalTicks);
        }

        // Final result after animation
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.getOpenInventory().getTopInventory().equals(holder.getInventory())) return;
            
            // Unlock the trim for the player
            plugin.getTrimManager().unlockTrim(player.getUniqueId(), piece, finalTrim.getPattern());
            
            // Show final result with fanfare
            showFinalResult(holder.getInventory(), piece, finalTrim);
            
            // Apply trim if player has armor equipped
            plugin.getTrimManager().applyTrimInstantly(player, piece, finalTrim);
            
            // Play winning sounds
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            
            // Send message
            String col = plugin.getTrimManager().patternColour(finalTrim.getPattern());
            String mc = plugin.getTrimManager().materialColour(finalTrim.getMaterial());
            player.sendMessage("");
            player.sendMessage("§5§l✦ §d§l¡TRIM DESBLOQUEADO! §5§l✦");
            player.sendMessage("§7Obtuviste: " + col + "§l" + finalTrim.getPattern() + " §7de " + mc + "§l" + finalTrim.getMaterial());
            player.sendMessage("§7Para tu: §f" + piece.getDisplayName());
            player.sendMessage("");
            
            // Close after 3 seconds
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.closeInventory();
                }
            }, 60);
        }, totalTicks + 10);
    }
    
    private void updateHorizontalSpin(Inventory inv, int[] slots, List<Trim> trims, int tick, ArmorPiece piece, Trim finalTrim) {
        // Shift items to the left, new item comes from right
        for (int i = 0; i < slots.length; i++) {
            int trimIndex = (tick + i) % trims.size();
            Trim trim = trims.get(trimIndex);
            
            // Center slot (22) gets highlight
            boolean isCenter = slots[i] == 22;
            inv.setItem(slots[i], createTrimSpinItem(trim, piece, isCenter));
        }
        
        // Update pointer arrows
        inv.setItem(13, createPointerItem());
        inv.setItem(31, createPointerItem());
    }
    
    private ItemStack createTrimSpinItem(Trim trim, ArmorPiece piece, boolean highlighted) {
        String col = plugin.getTrimManager().patternColour(trim.getPattern());
        Material mat = highlighted ? piece.getDisplayMaterial() : getRandomArmorMaterial();
        
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(col + trim.getPattern());
        meta.setLore(List.of(
            "§7" + trim.getMaterial()
        ));
        if (highlighted) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createPointerItem() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e§l▼");
        item.setItemMeta(meta);
        return item;
    }
    
    private Material getRandomArmorMaterial() {
        Material[] mats = {Material.IRON_CHESTPLATE, Material.GOLDEN_CHESTPLATE, Material.DIAMOND_CHESTPLATE, 
                          Material.NETHERITE_CHESTPLATE, Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE};
        return mats[random.nextInt(mats.length)];
    }


    private void showFinalResult(Inventory inv, ArmorPiece piece, Trim trim) {
        String col = plugin.getTrimManager().patternColour(trim.getPattern());
        String mc = plugin.getTrimManager().materialColour(trim.getMaterial());
        
        // Clear spinning slots
        for (int i = 19; i <= 25; i++) {
            if (i != 22) inv.setItem(i, createBorderItem());
        }
        inv.setItem(13, createBorderItem());
        inv.setItem(31, createBorderItem());
        
        // Center result with glow
        ItemStack result = createGlowItem(piece.getDisplayMaterial(), 
            "§5§l✦ " + col + "§l" + trim.getPattern() + " §5§l✦",
            List.of(
                "§8━━━━━━━━━━━━━━━━━━",
                "",
                "§7Pieza: §f" + piece.getDisplayName(),
                "§7Patrón: " + col + "§l" + trim.getPattern(),
                "§7Material: " + mc + "§l" + trim.getMaterial(),
                "",
                "§8━━━━━━━━━━━━━━━━━━",
                "",
                "§a§l✦ ¡TRIM DESBLOQUEADO! ✦"
            ));
        
        inv.setItem(22, result);
        
        // Add celebratory items around
        inv.setItem(21, createGlowItem(Material.GOLD_NUGGET, "§e✨", List.of()));
        inv.setItem(23, createGlowItem(Material.GOLD_NUGGET, "§e✨", List.of()));
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
