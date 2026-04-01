package com.pvprooms.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.function.Consumer;

/**
 * Holder for the duel challenge kit selection GUI.
 * Stores a callback to execute when a kit is selected.
 */
public class DuelChallengeKitHolder implements InventoryHolder {
    
    private final Consumer<String> onKitSelected;
    
    public DuelChallengeKitHolder(Consumer<String> onKitSelected) {
        this.onKitSelected = onKitSelected;
    }
    
    public Consumer<String> getOnKitSelected() {
        return onKitSelected;
    }
    
    @Override
    public Inventory getInventory() {
        return null;
    }
}
