package com.pvprooms.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** InventoryHolder that identifies the admin panel GUI. */
public class AdminPanelHolder implements InventoryHolder {
    private Inventory inventory;

    @Override
    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inv) { this.inventory = inv; }
}
