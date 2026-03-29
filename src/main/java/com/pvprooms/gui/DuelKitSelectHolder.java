package com.pvprooms.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Holder for the duel kit selection GUI.
 * Stores the target player's UUID for the duel.
 */
public class DuelKitSelectHolder implements InventoryHolder {
    
    private final UUID targetUUID;
    
    public DuelKitSelectHolder(UUID targetUUID) {
        this.targetUUID = targetUUID;
    }
    
    public UUID getTargetUUID() {
        return targetUUID;
    }
    
    @Override
    public Inventory getInventory() {
        return null;
    }
}
