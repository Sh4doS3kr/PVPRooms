package com.pvprooms.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Holder for the duel max-score selection GUI.
 * Stores the target UUID, selected kit name, and a callback for when a score is chosen.
 */
public class DuelScoreSelectHolder implements InventoryHolder {

    private final UUID targetUUID;
    private final String kitName;
    private final Consumer<Integer> onScoreSelected;

    public DuelScoreSelectHolder(UUID targetUUID, String kitName, Consumer<Integer> onScoreSelected) {
        this.targetUUID = targetUUID;
        this.kitName = kitName;
        this.onScoreSelected = onScoreSelected;
    }

    public UUID getTargetUUID()                 { return targetUUID; }
    public String getKitName()                  { return kitName; }
    public Consumer<Integer> getOnScoreSelected() { return onScoreSelected; }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
