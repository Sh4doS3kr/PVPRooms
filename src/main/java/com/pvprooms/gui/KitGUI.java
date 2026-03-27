package com.pvprooms.gui;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Kit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Builds and opens the Kit Selection GUI.
 *
 * When a player clicks a kit item inside the GUI the InventoryListener
 * intercepts the click and calls QueueManager#addToQueue accordingly.
 *
 * The title format is intentionally prefixed with "§0§r§0KIT_SELECT§r"
 * so InventoryListener can identify the GUI without ambiguity.
 */
public class KitGUI {

    /** Identifier embedded in the GUI title so listeners can detect it. */
    public static final String GUI_TITLE_TAG = "§0KIT_SELECT§r";

    private final PvPRoomsPro plugin;

    public KitGUI(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ── Open GUI ───────────────────────────────────────────────────────────

    /**
     * Opens the kit selection inventory for the given player.
     * Each available kit is represented by its icon material.
     */
    public void open(Player player) {
        Collection<Kit> kits = plugin.getKitManager().getAllKits();

        if (kits.isEmpty()) {
            player.sendMessage(plugin.prefix() + "§cNo kits are available yet. Ask an admin to create some.");
            return;
        }

        int size = calculateSize(kits.size());
        String title = ChatColor.translateAlternateColorCodes('&',
                "&8Select a &cKit &8— " + GUI_TITLE_TAG);

        Inventory inv = Bukkit.createInventory(null, size, title);

        int slot = 0;
        for (Kit kit : kits) {
            if (slot >= size) break;
            inv.setItem(slot, buildKitItem(kit, player));
            slot++;
        }

        // Fill remaining slots with glass panes
        ItemStack filler = buildFiller();
        for (int i = slot; i < size; i++) {
            inv.setItem(i, filler);
        }

        player.openInventory(inv);
    }

    // ── Item builders ──────────────────────────────────────────────────────

    /**
     * Builds the display item for a kit.
     * The item name contains the kit name and lore shows queue size.
     */
    private ItemStack buildKitItem(Kit kit, Player viewer) {
        ItemStack item = new ItemStack(kit.getIconMaterial());
        ItemMeta meta = item.getItemMeta();

        int queueSize = plugin.getQueueManager().getQueueSize(kit.getName());
        int myElo = plugin.getEloManager().getElo(viewer.getUniqueId());

        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                "&e&l" + capitalize(kit.getName())));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.translateAlternateColorCodes('&', "&7Click to join queue"));
        lore.add("");
        lore.add(ChatColor.translateAlternateColorCodes('&', "&fIn queue: &a" + queueSize));
        lore.add(ChatColor.translateAlternateColorCodes('&', "&fYour ELO: &e" + myElo));
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildFiller() {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(" ");
        glass.setItemMeta(meta);
        return glass;
    }

    // ── Utility ────────────────────────────────────────────────────────────

    /**
     * Extracts the kit name from a GUI item's display name.
     * Returns null if the item is not a valid kit item.
     */
    public String extractKitName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) return null;
        String raw = ChatColor.stripColor(meta.getDisplayName()).trim().toLowerCase();
        if (raw.isBlank() || raw.equals(" ")) return null;
        return raw;
    }

    /** Determines the inventory size (multiples of 9) needed to display N kits. */
    private int calculateSize(int kitCount) {
        int needed = kitCount + 4; // some filler around kits
        int rows = (int) Math.ceil(needed / 9.0);
        rows = Math.max(1, Math.min(rows, 6));
        return rows * 9;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
