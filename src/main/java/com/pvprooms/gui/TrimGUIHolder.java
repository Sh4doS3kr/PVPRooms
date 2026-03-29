package com.pvprooms.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Inventory holder for all pages of the player-facing trim GUI. */
public class TrimGUIHolder implements InventoryHolder {

    public enum Page { MAIN, PICK_PATTERN, PICK_MATERIAL, REWARD }

    private final UUID   playerUUID;
    private final Page   page;
    /** Armor piece key being edited (e.g. "helmet"), null on MAIN. */
    private final String pieceKey;
    /** Selected pattern key, used on PICK_MATERIAL page. */
    private final String patternKey;
    /** Trim being shown in REWARD page (stored as "material:pattern" string). */
    private final String rewardTrim;

    private Inventory inventory;

    public TrimGUIHolder(UUID playerUUID, Page page, String pieceKey, String patternKey, String rewardTrim) {
        this.playerUUID = playerUUID;
        this.page       = page;
        this.pieceKey   = pieceKey;
        this.patternKey = patternKey;
        this.rewardTrim = rewardTrim;
    }

    public UUID   getPlayerUUID() { return playerUUID; }
    public Page   getPage()       { return page; }
    public String getPieceKey()   { return pieceKey; }
    public String getPatternKey() { return patternKey; }
    public String getRewardTrim() { return rewardTrim; }

    @Override public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inv)   { this.inventory = inv; }
}
