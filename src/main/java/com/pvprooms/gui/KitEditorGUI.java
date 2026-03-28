package com.pvprooms.gui;

import com.pvprooms.model.Kit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Opens a 45-slot (5-row) GUI for editing a kit's contents.
 *
 * Layout:
 *   Rows 0-3 (slots  0-35): Kit inventory (36 items)
 *   Row  4   (slots 36-44): Yelmo | Pechera | Polainas | Botas | Offhand | PANE | PANE | SAVE | CANCEL
 */
public class KitEditorGUI {

    // ── Slot constants ────────────────────────────────────────────────────
    public static final int HELMET_SLOT      = 36;
    public static final int CHESTPLATE_SLOT  = 37;
    public static final int LEGGINGS_SLOT    = 38;
    public static final int BOOTS_SLOT       = 39;
    public static final int OFFHAND_SLOT     = 40;
    public static final int PANE_SLOT_1      = 41;
    public static final int PANE_SLOT_2      = 42;
    public static final int SAVE_SLOT        = 43;
    public static final int CANCEL_SLOT      = 44;

    /** Material used for empty armor/offhand placeholders. */
    public static final Material PLACEHOLDER_MAT = Material.LIGHT_GRAY_STAINED_GLASS_PANE;

    // ── Open ──────────────────────────────────────────────────────────────

    public void open(Player player, Kit kit) {
        KitEditorHolder holder = new KitEditorHolder(kit.getName(), player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 45,
                ChatColor.translateAlternateColorCodes('&',
                        "&6&lEditor de Kit: &f" + kit.getName()));
        holder.setInventory(inv);

        // Slots 0-35: kit inventory
        ItemStack[] contents = kit.getContents();
        for (int i = 0; i < 36; i++) {
            if (contents[i] != null && contents[i].getType() != Material.AIR) {
                inv.setItem(i, contents[i].clone());
            }
        }

        // Bukkit armor order: [0]=boots [1]=leggings [2]=chestplate [3]=helmet
        ItemStack[] armor = kit.getArmorContents();
        inv.setItem(HELMET_SLOT,     armorSlot(armor.length > 3 ? armor[3] : null, "&e🪖 Yelmo &8(vacío)"));
        inv.setItem(CHESTPLATE_SLOT, armorSlot(armor.length > 2 ? armor[2] : null, "&e🧥 Pechera &8(vacío)"));
        inv.setItem(LEGGINGS_SLOT,   armorSlot(armor.length > 1 ? armor[1] : null, "&e👖 Polainas &8(vacío)"));
        inv.setItem(BOOTS_SLOT,      armorSlot(armor.length > 0 ? armor[0] : null, "&e👟 Botas &8(vacío)"));
        inv.setItem(OFFHAND_SLOT,    armorSlot(kit.getOffhand(),                   "&e🛡 Offhand &8(vacío)"));

        // Separator panes
        ItemStack sep = buildItem(Material.GRAY_STAINED_GLASS_PANE, "&8 ");
        inv.setItem(PANE_SLOT_1, sep);
        inv.setItem(PANE_SLOT_2, sep);

        // Save / Cancel buttons
        inv.setItem(SAVE_SLOT,   buildItem(Material.LIME_WOOL,  "&a&l✔ Guardar"));
        inv.setItem(CANCEL_SLOT, buildItem(Material.RED_WOOL,   "&c&l✘ Cancelar"));

        player.openInventory(inv);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Returns the real item if present, otherwise a named placeholder pane. */
    private ItemStack armorSlot(ItemStack item, String emptyLabel) {
        if (item != null && item.getType() != Material.AIR) return item.clone();
        return buildItem(PLACEHOLDER_MAT, emptyLabel);
    }

    private ItemStack buildItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            item.setItemMeta(meta);
        }
        return item;
    }
}
