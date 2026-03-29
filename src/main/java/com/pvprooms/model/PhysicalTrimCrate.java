package com.pvprooms.model;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a physical trim crate block placed in the world.
 * Different colors for different armor pieces and types.
 */
public class PhysicalTrimCrate {

    private static NamespacedKey crateTypeKey;
    private static NamespacedKey armorPieceKey;
    private static NamespacedKey legendaryKey;

    public static final String VALUE_NORMAL = "normal";
    public static final String VALUE_LEGENDARY = "legendary";
    public static final String VALUE_THEMED = "themed";

    // Color mapping for different crate types and armor pieces
    private static final Map<String, Material> CRATE_COLORS = new HashMap<>();
    
    static {
        CRATE_COLORS.put("helmet_normal", Material.CYAN_SHULKER_BOX);
        CRATE_COLORS.put("helmet_legendary", Material.PURPLE_SHULKER_BOX);
        CRATE_COLORS.put("helmet_themed", Material.YELLOW_SHULKER_BOX);
        
        CRATE_COLORS.put("chestplate_normal", Material.CYAN_SHULKER_BOX);
        CRATE_COLORS.put("chestplate_legendary", Material.PURPLE_SHULKER_BOX);
        CRATE_COLORS.put("chestplate_themed", Material.YELLOW_SHULKER_BOX);
        
        CRATE_COLORS.put("leggings_normal", Material.CYAN_SHULKER_BOX);
        CRATE_COLORS.put("leggings_legendary", Material.PURPLE_SHULKER_BOX);
        CRATE_COLORS.put("leggings_themed", Material.YELLOW_SHULKER_BOX);
        
        CRATE_COLORS.put("boots_normal", Material.CYAN_SHULKER_BOX);
        CRATE_COLORS.put("boots_legendary", Material.PURPLE_SHULKER_BOX);
        CRATE_COLORS.put("boots_themed", Material.YELLOW_SHULKER_BOX);
    }

    private PhysicalTrimCrate() {}

    /** Must be called once on plugin enable before any item is created. */
    public static void init(org.bukkit.plugin.java.JavaPlugin plugin) {
        crateTypeKey = new NamespacedKey(plugin, "physical_crate_type");
        armorPieceKey = new NamespacedKey(plugin, "physical_crate_piece");
        legendaryKey = new NamespacedKey(plugin, "physical_crate_legendary");
    }

    /** Creates a physical crate item for placing */
    public static ItemStack createCrateItem(String type, ArmorPiece piece, boolean legendary) {
        String key = piece.name().toLowerCase() + "_" + (legendary ? VALUE_LEGENDARY : type);
        Material material = CRATE_COLORS.getOrDefault(key, Material.CYAN_SHULKER_BOX);
        
        ItemStack item = new ItemStack(material);
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        
        // Set display name based on type and piece
        String displayName;
        if (type.equals(VALUE_THEMED)) {
            displayName = "§e§l✦ " + piece.getSymbol() + " §fCrate de " + piece.getDisplayName() + " §e§l✦";
        } else if (legendary) {
            displayName = "§5§l✦ §dCrate Legendario §5§l✦";
        } else {
            displayName = "§b✦ §eCrate de Trims §b✦";
        }
        
        meta.setDisplayName(displayName);
        
        // Store persistent data
        meta.getPersistentDataContainer().set(crateTypeKey, PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(armorPieceKey, PersistentDataType.STRING, piece.name());
        meta.getPersistentDataContainer().set(legendaryKey, PersistentDataType.BYTE, (byte) (legendary ? 1 : 0));
        
        item.setItemMeta(meta);
        return item;
    }

    /** Checks if a block is a physical trim crate */
    public static boolean isPhysicalCrate(Block block) {
        if (!(block.getState() instanceof ShulkerBox shulker)) return false;
        return shulker.getPersistentDataContainer().has(crateTypeKey, PersistentDataType.STRING);
    }

    /** Gets the crate type from a block */
    public static String getCrateType(Block block) {
        if (!isPhysicalCrate(block)) return null;
        ShulkerBox shulker = (ShulkerBox) block.getState();
        return shulker.getPersistentDataContainer().get(crateTypeKey, PersistentDataType.STRING);
    }

    /** Gets the armor piece from a block */
    public static ArmorPiece getArmorPiece(Block block) {
        if (!isPhysicalCrate(block)) return null;
        ShulkerBox shulker = (ShulkerBox) block.getState();
        String pieceName = shulker.getPersistentDataContainer().get(armorPieceKey, PersistentDataType.STRING);
        return pieceName != null ? ArmorPiece.valueOf(pieceName) : null;
    }

    /** Checks if a crate is legendary */
    public static boolean isLegendary(Block block) {
        if (!isPhysicalCrate(block)) return false;
        ShulkerBox shulker = (ShulkerBox) block.getState();
        Byte legendary = shulker.getPersistentDataContainer().get(legendaryKey, PersistentDataType.BYTE);
        return legendary != null && legendary == 1;
    }

    public static NamespacedKey getCrateTypeKey() { return crateTypeKey; }
    public static NamespacedKey getArmorPieceKey() { return armorPieceKey; }
    public static NamespacedKey getLegendaryKey() { return legendaryKey; }
}
