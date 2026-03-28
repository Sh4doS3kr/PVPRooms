package com.pvprooms.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a PvP kit that can be applied to players.
 * Kits store armor, hotbar, and extra inventory items.
 */
public class Kit {

    private final String name;
    private ItemStack[] contents;      // full 36-slot player inventory
    private ItemStack[] armorContents; // 4-slot armor array
    private ItemStack offhand;
    private Material iconMaterial;     // icon shown in Kit GUI
    private String connectedArena;     // null = arena aleatoria; otherwise forced arena name

    public Kit(String name, ItemStack[] contents, ItemStack[] armorContents, ItemStack offhand) {
        this.name = name;
        this.contents = contents != null ? contents.clone() : new ItemStack[36];
        this.armorContents = armorContents != null ? armorContents.clone() : new ItemStack[4];
        this.offhand = offhand;
        this.iconMaterial = detectIcon();
    }

    /** Attempts to pick a representative icon from the kit's main hand slot. */
    private Material detectIcon() {
        if (contents != null && contents.length > 0 && contents[0] != null) {
            return contents[0].getType();
        }
        return Material.DIAMOND_SWORD;
    }

    // ── Getters / setters ──────────────────────────────────────────────────

    public String getName() { return name; }

    public ItemStack[] getContents() { return contents.clone(); }

    public void setContents(ItemStack[] contents) {
        this.contents = contents != null ? contents.clone() : new ItemStack[36];
        this.iconMaterial = detectIcon();
    }

    public ItemStack[] getArmorContents() { return armorContents.clone(); }

    public void setArmorContents(ItemStack[] armorContents) {
        this.armorContents = armorContents != null ? armorContents.clone() : new ItemStack[4];
    }

    public ItemStack getOffhand() { return offhand != null ? offhand.clone() : null; }

    public void setOffhand(ItemStack offhand) { this.offhand = offhand; }

    public Material getIconMaterial() { return iconMaterial; }
    public void setIconMaterial(Material iconMaterial) { this.iconMaterial = iconMaterial; }

    public String getConnectedArena() { return connectedArena; }
    public void setConnectedArena(String connectedArena) { this.connectedArena = connectedArena; }

    /**
     * Returns all non-null items across armor + inventory (used for display).
     */
    public List<ItemStack> getAllItems() {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack i : armorContents) {
            if (i != null && i.getType() != Material.AIR) items.add(i);
        }
        for (ItemStack i : contents) {
            if (i != null && i.getType() != Material.AIR) items.add(i);
        }
        return items;
    }
}
