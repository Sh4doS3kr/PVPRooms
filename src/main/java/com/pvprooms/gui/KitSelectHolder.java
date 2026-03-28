package com.pvprooms.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Holder para el GUI de selección de kit. Permite detectarlo sin depender del título. */
public class KitSelectHolder implements InventoryHolder {

    private final boolean tierMode;

    public KitSelectHolder(boolean tierMode) {
        this.tierMode = tierMode;
    }

    public boolean isTierMode() { return tierMode; }

    @Override
    public Inventory getInventory() { return null; }
}
