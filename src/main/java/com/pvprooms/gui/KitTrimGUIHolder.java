package com.pvprooms.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Inventory holder for all pages of the admin kit-trim configuration GUI. */
public class KitTrimGUIHolder implements InventoryHolder {

    public enum Page { PICK_PIECE, PICK_PATTERN, PICK_MATERIAL }

    private final String kitName;
    private final Page   page;
    private final String pieceKey;
    private final String patternKey;

    private Inventory inventory;

    public KitTrimGUIHolder(String kitName, Page page, String pieceKey, String patternKey) {
        this.kitName    = kitName;
        this.page       = page;
        this.pieceKey   = pieceKey;
        this.patternKey = patternKey;
    }

    public String getKitName()    { return kitName; }
    public Page   getPage()       { return page; }
    public String getPieceKey()   { return pieceKey; }
    public String getPatternKey() { return patternKey; }

    @Override public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inv)   { this.inventory = inv; }
}
