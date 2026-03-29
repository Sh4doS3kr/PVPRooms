package com.pvprooms.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Holder for the Kit Reorder GUI so InventoryListener can identify it. */
public class KitReorderHolder implements InventoryHolder {

    private final String kitName;
    private final java.util.UUID playerUUID; // null = admin global edit; non-null = personal edit
    private Inventory inventory;

    /** Admin global edit constructor (legacy). */
    public KitReorderHolder(String kitName) {
        this(kitName, null);
    }

    /** Personal kit layout edit constructor. */
    public KitReorderHolder(String kitName, java.util.UUID playerUUID) {
        this.kitName    = kitName;
        this.playerUUID = playerUUID;
    }

    public String getKitName() { return kitName; }

    /** Returns the player UUID for a personal edit, or null for an admin global edit. */
    public java.util.UUID getPlayerUUID() { return playerUUID; }

    @Override
    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inv) { this.inventory = inv; }
}
