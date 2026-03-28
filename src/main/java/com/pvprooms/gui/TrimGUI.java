package com.pvprooms.gui;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.Arrays;

/**
 * GUI for selecting and applying armor trims.
 * Two-page interface: first select material, then pattern.
 */
@SuppressWarnings({"deprecation", "removal"})
public class TrimGUI implements Listener {
    
    private final PvPRoomsPro plugin;
    private Inventory materialInventory;
    private Inventory patternInventory;
    private Player player;
    private TrimMaterial selectedMaterial;
    private boolean isMaterialPage = true;

    public TrimGUI(PvPRoomsPro plugin) {
        this.plugin = plugin;
        initializeInventories();
    }

    private void initializeInventories() {
        // Create material selection inventory
        materialInventory = Bukkit.createInventory(null, 54, "§6§lSelect Trim Material");
        
        // Add trim materials
        int slot = 0;
        org.bukkit.Registry<TrimMaterial> materialRegistry = Bukkit.getRegistry(TrimMaterial.class);
        for (TrimMaterial material : materialRegistry) {
            ItemStack item = createMaterialItem(material);
            materialInventory.setItem(slot, item);
            slot++;
            if (slot >= 45) break; // Leave space for navigation
        }

        // Navigation items
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName("§cClose");
        closeItem.setItemMeta(closeMeta);
        materialInventory.setItem(49, closeItem);

        // Create pattern selection inventory
        patternInventory = Bukkit.createInventory(null, 54, "§6§lSelect Trim Pattern");
        
        // Add trim patterns
        slot = 0;
        org.bukkit.Registry<TrimPattern> patternRegistry = Bukkit.getRegistry(TrimPattern.class);
        for (TrimPattern pattern : patternRegistry) {
            ItemStack item = createPatternItem(pattern);
            patternInventory.setItem(slot, item);
            slot++;
            if (slot >= 45) break; // Leave space for navigation
        }

        // Back button for pattern inventory
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName("§eBack to Materials");
        backItem.setItemMeta(backMeta);
        patternInventory.setItem(49, backItem);
    }

    private ItemStack createMaterialItem(TrimMaterial material) {
        Material displayMaterial = getDisplayMaterialForTrimMaterial(material);
        ItemStack item = new ItemStack(displayMaterial);
        ItemMeta meta = item.getItemMeta();
        
        String materialKey = material.getKey().getKey();
        meta.setDisplayName("§e" + formatEnum(materialKey));
        
        String description = switch (materialKey) {
            case "quartz" -> "Clean white quartz trim";
            case "iron" -> "Sturdy iron trim";
            case "gold" -> "Elegant gold trim";
            case "diamond" -> "Precious diamond trim";
            case "netherite" -> "Powerful netherite trim";
            case "redstone" -> "Red energy trim";
            case "copper" -> "Weathered copper trim";
            case "emerald" -> "Vibrant emerald trim";
            case "lapis" -> "Deep blue lapis trim";
            case "amethyst" -> "Mystical amethyst trim";
            default -> "Unique " + formatEnum(materialKey) + " trim";
        };
        
        meta.setLore(Arrays.asList(
            "§7" + description,
            "",
            "§aClick to select this material"
        ));
        
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPatternItem(TrimPattern pattern) {
        Material displayMaterial = getDisplayMaterialForTrimPattern(pattern);
        ItemStack item = new ItemStack(displayMaterial);
        ItemMeta meta = item.getItemMeta();
        
        String patternKey = pattern.getKey().getKey();
        meta.setDisplayName("§e" + formatEnum(patternKey));
        
        String description = switch (patternKey) {
            case "sentry" -> "Guard-inspired pattern";
            case "vex" -> "Phantom-like pattern";
            case "wild" -> "Nature-inspired pattern";
            case "coast" -> "Ocean-themed pattern";
            case "dune" -> "Desert-inspired pattern";
            case "wayfinder" -> "Navigation pattern";
            case "raiser" -> "Elevated design pattern";
            case "shaper" -> "Artistic shaping pattern";
            case "host" -> "Hospitality pattern";
            case "ward" -> "Protective pattern";
            case "silence" -> "Quiet elegance pattern";
            case "tide" -> "Wave-like pattern";
            case "snout" -> "Animal-inspired pattern";
            case "rib" -> "Ribbed pattern";
            case "eye" -> "Watchful eye pattern";
            case "spire" -> "Tower-inspired pattern";
            default -> "Unique " + formatEnum(patternKey) + " pattern";
        };
        
        meta.setLore(Arrays.asList(
            "§7" + description,
            "",
            "§aClick to apply this pattern",
            "§7with material: §e" + (selectedMaterial != null ? formatEnum(selectedMaterial.getKey().getKey()) : "None")
        ));
        
        item.setItemMeta(meta);
        return item;
    }

    private Material getDisplayMaterialForTrimMaterial(TrimMaterial material) {
        String materialKey = material.getKey().getKey();
        return switch (materialKey) {
            case "quartz" -> Material.QUARTZ_BLOCK;
            case "iron" -> Material.IRON_BLOCK;
            case "gold" -> Material.GOLD_BLOCK;
            case "diamond" -> Material.DIAMOND_BLOCK;
            case "netherite" -> Material.NETHERITE_BLOCK;
            case "redstone" -> Material.REDSTONE_BLOCK;
            case "copper" -> Material.COPPER_BLOCK;
            case "emerald" -> Material.EMERALD_BLOCK;
            case "lapis" -> Material.LAPIS_BLOCK;
            case "amethyst" -> Material.AMETHYST_BLOCK;
            default -> Material.STONE;
        };
    }

    private Material getDisplayMaterialForTrimPattern(TrimPattern pattern) {
        String patternKey = pattern.getKey().getKey();
        return switch (patternKey) {
            case "sentry" -> Material.IRON_HELMET;
            case "vex" -> Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "wild" -> Material.OAK_LEAVES;
            case "coast" -> Material.SAND;
            case "dune" -> Material.SANDSTONE;
            case "wayfinder" -> Material.COMPASS;
            case "raiser" -> Material.STONE_BRICKS;
            case "shaper" -> Material.STONECUTTER;
            case "host" -> Material.OAK_DOOR;
            case "ward" -> Material.IRON_BARS;
            case "silence" -> Material.SNOW_BLOCK;
            case "tide" -> Material.WATER_BUCKET;
            case "snout" -> Material.PIGLIN_HEAD;
            case "rib" -> Material.BONE_BLOCK;
            case "eye" -> Material.ENDER_EYE;
            case "spire" -> Material.POINTED_DRIPSTONE;
            default -> Material.PAPER;
        };
    }

    public void open(Player player) {
        this.player = player;
        this.isMaterialPage = true;
        this.selectedMaterial = null;
        
        player.openInventory(materialInventory);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() != player) return;
        
        event.setCancelled(true);
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        if (isMaterialPage) {
            handleMaterialClick(clicked);
        } else {
            handlePatternClick(clicked);
        }
    }

    private void handleMaterialClick(ItemStack clicked) {
        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        String displayName = clicked.getItemMeta().getDisplayName();
        if (displayName.startsWith("§e")) {
            String materialKey = displayName.substring(2).replace(" ", "_").toLowerCase();
            // Find material by key
            org.bukkit.Registry<TrimMaterial> registry = Bukkit.getRegistry(TrimMaterial.class);
            for (TrimMaterial material : registry) {
                if (material.getKey().getKey().equalsIgnoreCase(materialKey)) {
                    selectedMaterial = material;
                    isMaterialPage = false;
                    updatePatternInventory();
                    player.openInventory(patternInventory);
                    player.sendMessage("§aSelected material: " + formatEnum(material.getKey().getKey()));
                    return;
                }
            }
            player.sendMessage("§cInvalid material selection.");
        }
    }

    private void handlePatternClick(ItemStack clicked) {
        if (clicked.getType() == Material.ARROW) {
            isMaterialPage = true;
            player.openInventory(materialInventory);
            return;
        }

        String displayName = clicked.getItemMeta().getDisplayName();
        if (displayName.startsWith("§e")) {
            String patternKey = displayName.substring(2).replace(" ", "_").toLowerCase();
            // Find pattern by key
            org.bukkit.Registry<TrimPattern> registry = Bukkit.getRegistry(TrimPattern.class);
            for (TrimPattern pattern : registry) {
                if (pattern.getKey().getKey().equalsIgnoreCase(patternKey)) {
                    applyTrimToArmor(selectedMaterial, pattern);
                    player.closeInventory();
                    return;
                }
            }
            player.sendMessage("§cInvalid pattern selection.");
        }
    }

    private void updatePatternInventory() {
        // Update all pattern items to show selected material
        for (int i = 0; i < 45; i++) {
            ItemStack item = patternInventory.getItem(i);
            if (item != null && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
                String displayName = item.getItemMeta().getDisplayName();
                if (displayName.startsWith("§e")) {
                    String patternKey = displayName.substring(2).replace(" ", "_").toLowerCase();
                    // Find pattern by key to recreate item
                    org.bukkit.Registry<TrimPattern> registry = Bukkit.getRegistry(TrimPattern.class);
                    for (TrimPattern pattern : registry) {
                        if (pattern.getKey().getKey().equalsIgnoreCase(patternKey)) {
                            ItemStack updatedItem = createPatternItem(pattern);
                            patternInventory.setItem(i, updatedItem);
                            break;
                        }
                    }
                }
            }
        }
    }

    private void applyTrimToArmor(TrimMaterial material, TrimPattern pattern) {
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (item == null || !isArmor(item.getType())) {
            player.sendMessage("§cYou must hold an armor piece to apply trim.");
            return;
        }

        if (!(item.getItemMeta() instanceof ArmorMeta)) {
            player.sendMessage("§cThis item cannot have trims.");
            return;
        }

        ArmorMeta meta = (ArmorMeta) item.getItemMeta();
        ArmorTrim trim = new ArmorTrim(material, pattern);
        meta.setTrim(trim);
        item.setItemMeta(meta);

        player.sendMessage("§aTrim applied successfully!");
        player.sendMessage("§7Material: " + formatEnum(material.getKey().getKey()));
        player.sendMessage("§7Pattern: " + formatEnum(pattern.getKey().getKey()));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() == player) {
            HandlerList.unregisterAll(this);
        }
    }

    private boolean isArmor(Material material) {
        return material.name().endsWith("_HELMET") ||
               material.name().endsWith("_CHESTPLATE") ||
               material.name().endsWith("_LEGGINGS") ||
               material.name().endsWith("_BOOTS");
    }

    private String formatEnum(String enumName) {
        return enumName.toLowerCase().replace("_", " ");
    }
}
