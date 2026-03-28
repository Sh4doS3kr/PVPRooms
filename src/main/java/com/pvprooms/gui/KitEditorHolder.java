package com.pvprooms.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * InventoryHolder that identifies a kit editor GUI.
 * Stored as the holder of the editor inventory so InventoryListener
 * can detect and route clicks correctly.
 */
public class KitEditorHolder implements InventoryHolder {

    private final String kitName;
    private final UUID playerUUID;
    private Inventory inventory;

    public KitEditorHolder(String kitName, UUID playerUUID) {
        this.kitName   = kitName;
        this.playerUUID = playerUUID;
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    public String getKitName()   { return kitName; }
    public UUID   getPlayerUUID() { return playerUUID; }
}
