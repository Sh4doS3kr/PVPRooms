package com.pvprooms.model;

import org.bukkit.Material;

/**
 * Maps each armor slot to a human-readable label, icon material and display symbol.
 * Bukkit armor array layout: [0] = boots, [1] = leggings, [2] = chestplate, [3] = helmet.
 */
public enum ArmorPiece {

    HELMET    (3, "Casco",       "§e⛑",  Material.DIAMOND_HELMET),
    CHESTPLATE(2, "Pechera",     "§b⚔",  Material.DIAMOND_CHESTPLATE),
    LEGGINGS  (1, "Pantalones",  "§a🩲", Material.DIAMOND_LEGGINGS),
    BOOTS     (0, "Botas",       "§6👢", Material.DIAMOND_BOOTS);

    private final int     armorSlot;
    private final String  displayName;
    private final String  symbol;
    private final Material displayMaterial;

    ArmorPiece(int armorSlot, String displayName, String symbol, Material displayMaterial) {
        this.armorSlot       = armorSlot;
        this.displayName     = displayName;
        this.symbol          = symbol;
        this.displayMaterial = displayMaterial;
    }

    public int      getArmorSlot()        { return armorSlot; }
    public String   getDisplayName()      { return displayName; }
    public String   getSymbol()           { return symbol; }
    public Material getDisplayMaterial()  { return displayMaterial; }

    /** Finds an ArmorPiece by its Bukkit armor array slot index. */
    public static ArmorPiece fromSlot(int slot) {
        for (ArmorPiece ap : values()) if (ap.armorSlot == slot) return ap;
        return null;
    }

    /** Case-insensitive lookup by enum name. */
    public static ArmorPiece fromName(String name) {
        if (name == null) return null;
        for (ArmorPiece ap : values()) if (ap.name().equalsIgnoreCase(name)) return ap;
        return null;
    }
}
