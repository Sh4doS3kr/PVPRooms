package com.pvprooms.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Holder for the Kit Reorder GUI so InventoryListener can identify it. */
public class KitReorderHolder implements InventoryHolder {

    private final String kitName;
    private Inventory inventory;

    public KitReorderHolder(String kitName) {
        this.kitName = kitName;
    }

    public String getKitName() { return kitName; }

    @Override
    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inv) { this.inventory = inv; }
}
