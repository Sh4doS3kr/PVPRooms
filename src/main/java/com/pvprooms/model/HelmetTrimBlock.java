package com.pvprooms.model;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Represents a special block that gives random helmet trims when used with a key.
 * This block always gives helmet trims regardless of the crate type.
 */
public class HelmetTrimBlock {

    private static NamespacedKey helmetTrimBlockKey;

    private static final Material BLOCK_MATERIAL = Material.GOLD_BLOCK;

    private HelmetTrimBlock() {}

    /** Must be called once on plugin enable before any item is created. */
    public static void init(org.bukkit.plugin.java.JavaPlugin plugin) {
        helmetTrimBlockKey = new NamespacedKey(plugin, "helmet_trim_block");
    }

    /** Creates a helmet trim block item for placing */
    public static ItemStack createBlockItem() {
        ItemStack item = new ItemStack(BLOCK_MATERIAL);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName("§6§l✦ §eBloque de Trims de Casco §6§l✦");
        meta.setLore(java.util.List.of(
            "§7Coloca este bloque en el mundo",
            "§7y úsalo con una llave para obtener",
            "§7un trim aleatorio para el casco.",
            "",
            "§8▸ Click derecho con llave"
        ));
        
        // Store persistent data to identify this block
        if (meta instanceof BlockStateMeta blockMeta) {
            blockMeta.getPersistentDataContainer().set(helmetTrimBlockKey, PersistentDataType.BYTE, (byte) 1);
        }
        
        item.setItemMeta(meta);
        return item;
    }

    /** Checks if a block is a helmet trim block */
    public static boolean isHelmetTrimBlock(Block block) {
        if (block.getType() != BLOCK_MATERIAL) return false;
        if (!(block.getState() instanceof ShulkerBox shulker)) return false;
        return shulker.getPersistentDataContainer().has(helmetTrimBlockKey, PersistentDataType.BYTE);
    }

    public static NamespacedKey getHelmetTrimBlockKey() { 
        return helmetTrimBlockKey; 
    }
}
