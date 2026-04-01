package com.pvprooms.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Generic holder for staff GUI menus. */
public class StaffMenuHolder implements InventoryHolder {
    public enum Type { PLAYERS, MATCHES, FREEZE, INFO }
    private final Type type;
    public StaffMenuHolder(Type type) { this.type = type; }
    public Type getType() { return type; }
    @Override public Inventory getInventory() { return null; }
}
