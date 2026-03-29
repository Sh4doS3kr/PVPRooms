package com.pvprooms.model;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import com.pvprooms.model.ArmorPiece;

import java.util.List;

/**
 * Factory class for trim crate (shulker box) and crate key (tripwire hook) items.
 * Uses PersistentDataContainer tags to distinguish crate/key items from regular blocks.
 *
 *  - Normal crate  → §b cyan shulker box  (90% drop chance)
 *  - Legendary crate → §5 purple shulker box (10% drop chance)
 *  - Themed crate  → §e themed shulker box (specific armor piece)
 *  - Crate key     → tripwire hook
 */
public final class TrimCrate {

    public static final String VALUE_NORMAL    = "normal";
    public static final String VALUE_LEGENDARY = "legendary";
    public static final String VALUE_THEMED    = "themed";

    private static NamespacedKey crateKey;
    private static NamespacedKey keyTag;
    private static NamespacedKey armorPieceTag;

    private TrimCrate() {}

    /** Must be called once on plugin enable before any item is created. */
    public static void init(JavaPlugin plugin) {
        crateKey = new NamespacedKey(plugin, "trim_crate_type");
        keyTag   = new NamespacedKey(plugin, "trim_crate_key");
        armorPieceTag = new NamespacedKey(plugin, "trim_crate_armor_piece");
    }

    // ── Item factories ────────────────────────────────────────────────────

    public static ItemStack createNormalCrate() {
        ItemStack item = new ItemStack(Material.CYAN_SHULKER_BOX);
        ItemMeta meta  = item.getItemMeta();
        meta.setDisplayName("§b§l✦ §eCrate de Trims §b§l✦");
        meta.setLore(List.of(
                "§7Contiene un §etrim aleatorio§7.",
                "",
                "§e✦ §7Usa una §6Llave de Crate§7 para abrirlo.",
                "§8Haz click con la llave en el inventario."
        ));
        meta.setCustomModelData(9899);
        meta.getPersistentDataContainer().set(crateKey, PersistentDataType.STRING, VALUE_NORMAL);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createLegendaryCrate() {
        ItemStack item = new ItemStack(Material.PURPLE_SHULKER_BOX);
        ItemMeta meta  = item.getItemMeta();
        meta.setDisplayName("§5§l✦ §dCrate Legendario de Trims §5§l✦");
        meta.setLore(List.of(
                "§7Contiene un trim §5§lLEGENDARIO§7.",
                "§7Patrones exclusivos y raros.",
                "",
                "§e✦ §7Usa una §6Llave de Crate§7 para abrirlo.",
                "§8(10% de probabilidad al abrir un crate normal)"
        ));
        meta.setCustomModelData(9900);
        meta.getPersistentDataContainer().set(crateKey, PersistentDataType.STRING, VALUE_LEGENDARY);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createThemedCrate(ArmorPiece piece) {
        ItemStack item = new ItemStack(Material.YELLOW_SHULKER_BOX);
        ItemMeta meta  = item.getItemMeta();
        meta.setDisplayName("§e§l✦ " + piece.getSymbol() + " §fCrate de " + piece.getDisplayName() + " §e§l✦");
        meta.setLore(List.of(
                "§7Contiene un trim aleatorio para §e" + piece.getDisplayName() + "§7.",
                "§7Patrones exclusivos para esta pieza.",
                "",
                "§e✦ §7Usa una §6Llave de Crate§7 para abrirlo.",
                "§8(Trims específicos para " + piece.getDisplayName() + ")"
        ));
        meta.setCustomModelData(9902);
        meta.getPersistentDataContainer().set(crateKey, PersistentDataType.STRING, VALUE_THEMED);
        meta.getPersistentDataContainer().set(armorPieceTag, PersistentDataType.STRING, piece.name());
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createKey() {
        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta  = item.getItemMeta();
        meta.setDisplayName("§6§l✦ §eLlave de Crate de Trims §6§l✦");
        meta.setLore(List.of(
                "§7Usa esta llave en un §eCrate de Trims§7",
                "§7para obtener un trim aleatorio.",
                "",
                "§8Haz click en el crate con esta llave activa."
        ));
        meta.setCustomModelData(9901);
        meta.getPersistentDataContainer().set(keyTag, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    // ── Predicates ────────────────────────────────────────────────────────

    public static boolean isCrate(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(crateKey, PersistentDataType.STRING);
    }

    public static boolean isLegendary(ItemStack item) {
        if (!isCrate(item)) return false;
        return VALUE_LEGENDARY.equals(item.getItemMeta().getPersistentDataContainer()
                .get(crateKey, PersistentDataType.STRING));
    }

    public static boolean isThemed(ItemStack item) {
        if (!isCrate(item)) return false;
        return VALUE_THEMED.equals(item.getItemMeta().getPersistentDataContainer()
                .get(crateKey, PersistentDataType.STRING));
    }

    public static ArmorPiece getArmorPiece(ItemStack item) {
        if (!isThemed(item)) return null;
        String pieceName = item.getItemMeta().getPersistentDataContainer()
                .get(armorPieceTag, PersistentDataType.STRING);
        return pieceName != null ? ArmorPiece.valueOf(pieceName) : null;
    }

    public static boolean isKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(keyTag, PersistentDataType.BYTE);
    }

    public static NamespacedKey getCrateKey() { return crateKey; }
    public static NamespacedKey getKeyTag()   { return keyTag; }
}
