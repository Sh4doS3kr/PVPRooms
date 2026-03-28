package com.pvprooms.gui;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Kit;
import com.pvprooms.model.Tier;
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

    public static final String GUI_TITLE_TAG      = "§0KIT_SELECT§r";
    public static final String GUI_TITLE_TAG_TIER  = "§0KIT_TIER§r";

    private final PvPRoomsPro plugin;

    public KitGUI(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ── Open GUI ───────────────────────────────────────────────────────────

    /** Opens in ELO mode. */
    public void open(Player player) { openInternal(player, false); }

    /** Opens in TIER mode (shows tier info, routes to tier queue). */
    public void openTierMode(Player player) { openInternal(player, true); }

    private void openInternal(Player player, boolean tierMode) {
        Collection<Kit> kits = plugin.getKitManager().getAllKits();
        if (kits.isEmpty()) {
            player.sendMessage(plugin.prefix() + "§cNo hay kits disponibles todavía.");
            return;
        }
        int size = calculateSize(kits.size());
        String tag   = tierMode ? GUI_TITLE_TAG_TIER : GUI_TITLE_TAG;
        String label = tierMode ? "&8Selecciona Kit &b[TIER] &8— " : "&8Selecciona Kit &c[ELO] &8— ";
        String title = ChatColor.translateAlternateColorCodes('&', label + tag);
        Inventory inv = Bukkit.createInventory(null, size, title);
        int slot = 0;
        for (Kit kit : kits) {
            if (slot >= size) break;
            inv.setItem(slot, tierMode ? buildKitItemTier(kit, player) : buildKitItem(kit, player));
            slot++;
        }
        ItemStack filler = buildFiller();
        for (int i = slot; i < size; i++) inv.setItem(i, filler);
        player.openInventory(inv);
    }

    // ── Item builders ──────────────────────────────────────────────────────

    private ItemStack buildKitItem(Kit kit, Player viewer) {
        int queueSize = plugin.getQueueManager().getQueueSize(kit.getName());
        int myElo     = plugin.getEloManager().getElo(viewer.getUniqueId());
        Tier myTier   = Tier.fromElo(myElo);
        return buildItem(kit.getIconMaterial(),
                "§e§l" + capitalize(kit.getName()),
                List.of("§7Click para entrar en cola ELO",
                        "",
                        "§fEn cola: §a" + queueSize,
                        "§fTu ELO:  §e" + myElo,
                        "§fTu Tier: " + myTier.formatted()));
    }

    private ItemStack buildKitItemTier(Kit kit, Player viewer) {
        int queueSize = plugin.getQueueManager().getTierQueueSize(kit.getName());
        int myElo     = plugin.getEloManager().getElo(viewer.getUniqueId());
        Tier myTier   = Tier.fromElo(myElo);
        return buildItem(kit.getIconMaterial(),
                "§b§l" + capitalize(kit.getName()),
                List.of("§7Click para entrar en cola TIER",
                        "§7Solo rivales de " + myTier.formatted() + " §7(±1)",
                        "",
                        "§fEn cola TIER: §a" + queueSize,
                        "§fTu ELO:       §e" + myElo,
                        "§fTu Tier:      " + myTier.formatted()));
    }

    private ItemStack buildItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
        meta.setDisplayName(name);
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
