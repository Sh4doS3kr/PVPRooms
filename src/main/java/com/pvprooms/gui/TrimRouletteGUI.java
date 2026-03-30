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
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.Registry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
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
        
        // Mark animation as active (prevent closing)
        startAnimation(player.getUniqueId());

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
        
        // Generate final trim FIRST
        Trim finalTrim = plugin.getTrimManager().randomTrimForPiece(piece, legendary);
        
        // Build spin list with 40+ trims, final trim will be at the end so it lands in center
        List<Trim> spinTrims = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            spinTrims.add(plugin.getTrimManager().randomTrimForPiece(piece, legendary));
        }
        // Insert final trim at position that will land in center slot (position 3 from end)
        int finalPosition = spinTrims.size() - 3;
        spinTrims.set(finalPosition, finalTrim);
        
        // Calculate total animation ticks
        int totalSpins = 45; // How many positions to scroll through
        int[] delays = new int[totalSpins];
        int totalTicks = 0;
        
        for (int i = 0; i < totalSpins; i++) {
            // Speed decreases as animation progresses (starts fast, slows down)
            if (i < 15) delays[i] = 1;
            else if (i < 25) delays[i] = 2;
            else if (i < 32) delays[i] = 3;
            else if (i < 38) delays[i] = 4;
            else if (i < 42) delays[i] = 6;
            else delays[i] = 10;
            totalTicks += delays[i];
        }
        
        // Run animation - scroll through all items
        int currentTick = 0;
        for (int i = 0; i < totalSpins; i++) {
            final int scrollPosition = i;
            final int scheduleTick = currentTick;
            
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.getOpenInventory().getTopInventory().equals(holder.getInventory())) return;

                // Update spinning items - show 7 items centered on current scroll position
                Inventory inv = holder.getInventory();
                for (int j = 0; j < spinSlots.length; j++) {
                    int trimIndex = (scrollPosition + j) % spinTrims.size();
                    Trim trim = spinTrims.get(trimIndex);
                    boolean isCenter = (j == 3); // Center slot (index 3 of 7 slots)
                    inv.setItem(spinSlots[j], createTrimmedArmorItem(trim, piece, isCenter));
                }
                
                // Update pointer arrows
                inv.setItem(13, createPointerItem());
                inv.setItem(31, createPointerItem());
                
                // Play tick sound
                float pitch = 0.8f + (scrollPosition / (float)totalSpins) * 1.2f;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, pitch);
            }, scheduleTick);
            
            currentTick += delays[i];
        }

        // Final result after animation completes
        final int finalTotalTicks = totalTicks;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.getOpenInventory().getTopInventory().equals(holder.getInventory())) return;
            
            // Unlock the FULL trim for the player (pattern + material)
            plugin.getTrimManager().unlockFullTrim(player.getUniqueId(), piece, finalTrim);
            
            // Mark animation as complete
            holder.setAnimationComplete(true);
            endAnimation(player.getUniqueId());
            
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
            
            // Close after 3 seconds (animation is already complete, so they can close manually too)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() instanceof TrimRouletteHolder) {
                    player.closeInventory();
                }
            }, 60);
        }, finalTotalTicks + 15);
    }
    
    /** Creates a netherite armor item with the actual trim applied */
    private ItemStack createTrimmedArmorItem(Trim trim, ArmorPiece piece, boolean highlighted) {
        // Get netherite armor material for this piece
        Material armorMat = getNetheriteArmorMaterial(piece);
        ItemStack item = new ItemStack(armorMat);
        
        // Apply the actual trim to the armor
        if (item.getItemMeta() instanceof ArmorMeta armorMeta) {
            try {
                TrimPattern pattern = Registry.TRIM_PATTERN.get(
                    org.bukkit.NamespacedKey.minecraft(trim.getPattern().toLowerCase()));
                TrimMaterial material = Registry.TRIM_MATERIAL.get(
                    org.bukkit.NamespacedKey.minecraft(trim.getMaterial().toLowerCase()));
                
                if (pattern != null && material != null) {
                    armorMeta.setTrim(new ArmorTrim(material, pattern));
                }
            } catch (Exception ignored) {}
            
            String col = plugin.getTrimManager().patternColour(trim.getPattern());
            armorMeta.setDisplayName(col + trim.getPattern());
            armorMeta.setLore(List.of("§7" + trim.getMaterial()));
            
            if (highlighted) {
                armorMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
                armorMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            armorMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(armorMeta);
        }
        
        return item;
    }
    
    /** Gets the netherite armor material for the given piece */
    private Material getNetheriteArmorMaterial(ArmorPiece piece) {
        return switch (piece) {
            case HELMET -> Material.NETHERITE_HELMET;
            case CHESTPLATE -> Material.NETHERITE_CHESTPLATE;
            case LEGGINGS -> Material.NETHERITE_LEGGINGS;
            case BOOTS -> Material.NETHERITE_BOOTS;
        };
    }
    
    private ItemStack createPointerItem() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e§l▼");
        item.setItemMeta(meta);
        return item;
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

    // ═══════════════════════════════════════════════════════════════════════════════
    // PREVIEW GUI - Shows all possible trims when left-clicking a crate
    // ═══════════════════════════════════════════════════════════════════════════════
    
    public void openPreview(Player player, ArmorPiece piece, boolean legendary) {
        Inventory inv = Bukkit.createInventory(null, 54, "§d§l" + piece.getDisplayName() + " §7- Posibles Trims");
        
        // Fill border
        ItemStack border = createBorderItem();
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        
        // Get patterns based on legendary flag
        List<String> patterns = legendary ? 
            plugin.getTrimManager().getLegendaryPatternKeys() : 
            plugin.getTrimManager().getNormalPatternKeys();
        List<String> materials = plugin.getTrimManager().getMaterialKeys();
        
        // Show all possible pattern+material combinations (sample)
        int slot = 9;
        for (String pattern : patterns) {
            if (slot >= 45) break;
            // Pick a random material for display
            String material = materials.get(random.nextInt(materials.size()));
            Trim trim = new Trim(material, pattern);
            inv.setItem(slot, createTrimmedArmorItem(trim, piece, false));
            slot++;
        }
        
        // Info item
        inv.setItem(4, createGlowItem(Material.BOOK, "§e§lPOSIBLES TRIMS",
            List.of(
                "§7Estos son los trims que podrías",
                "§7obtener al abrir esta crate.",
                "",
                "§7Tipo: " + (legendary ? "§5§lLEGENDARIO" : "§b§lNORMAL"),
                "§7Patrones: §f" + patterns.size(),
                "§7Materiales: §f" + materials.size(),
                "",
                "§aUsa una llave para abrir la crate!"
            )));
        
        player.openInventory(inv);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // HOLDER CLASS - Tracks animation state
    // ═══════════════════════════════════════════════════════════════════════════════
    
    // Track players with active animations (can't close inventory)
    private static final Set<UUID> activeAnimations = new HashSet<>();
    
    public static boolean hasActiveAnimation(UUID playerId) {
        return activeAnimations.contains(playerId);
    }
    
    public static void startAnimation(UUID playerId) {
        activeAnimations.add(playerId);
    }
    
    public static void endAnimation(UUID playerId) {
        activeAnimations.remove(playerId);
    }

    public static class TrimRouletteHolder implements InventoryHolder {
        private final UUID playerId;
        private final ArmorPiece piece;
        private final String crateType;
        private final boolean legendary;
        private Inventory inventory;
        private boolean animationComplete = false;

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
        public boolean isAnimationComplete() { return animationComplete; }
        public void setAnimationComplete(boolean complete) { this.animationComplete = complete; }

        @Override
        public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
    }
}
