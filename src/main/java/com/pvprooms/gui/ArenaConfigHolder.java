package com.pvprooms.gui;

import com.pvprooms.model.ArenaTemplate;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ArenaConfigHolder implements InventoryHolder {
    private final ArenaTemplate template;
    private Inventory inventory;

    public ArenaConfigHolder(ArenaTemplate template) { this.template = template; }

    public ArenaTemplate getTemplate() { return template; }

    @Override public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }
}
