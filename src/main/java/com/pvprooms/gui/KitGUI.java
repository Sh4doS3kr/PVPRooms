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

    /** @deprecated Usar KitSelectHolder para detectar el GUI. */
    @Deprecated
    public static final String GUI_TITLE_TAG      = "KIT_SELECT";
    @Deprecated
    public static final String GUI_TITLE_TAG_TIER  = "KIT_TIER";

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
        String label = tierMode ? "&8Selecciona Kit &b[TIER]" : "&8Selecciona Kit &c[ELO]";
        String title = ChatColor.translateAlternateColorCodes('&', label);
        Inventory inv = Bukkit.createInventory(new KitSelectHolder(tierMode), size, title);
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
        Tier myTier   = Tier.forPlayer(plugin.getEloManager(), viewer.getUniqueId());
        return buildItem(kit.getIconMaterial(),
                "§e§l" + capitalize(kit.getName()),
                List.of("§7Click para entrar en cola ELO",
                        "§7Click derecho §fpara editar kit",
                        "",
                        "§fEn cola: §a" + queueSize,
                        "§fTu ELO:  §e" + myElo,
                        "§fTu Tier: " + myTier.formatted()));
    }

    private ItemStack buildKitItemTier(Kit kit, Player viewer) {
        int queueSize  = plugin.getQueueManager().getTierQueueSize(kit.getName());
        int myElo      = plugin.getEloManager().getElo(viewer.getUniqueId());
        Tier kitTier   = plugin.getTierManager().getTier(viewer.getUniqueId(), kit.getName());
        int  kitPts    = Math.max(0, plugin.getTierManager().getPoints(viewer.getUniqueId(), kit.getName()));
        com.pvprooms.model.TierTitle myTitle = plugin.getTierManager().getTitle(viewer.getUniqueId());
        return buildItem(kit.getIconMaterial(),
                "§b§l" + capitalize(kit.getName()),
                List.of("§7Click para entrar en cola TIER",
                        "§7Click derecho §fpara editar kit",
                        "§7Solo rivales de " + kitTier.formatted() + "§7 (±1)",
                        "",
                        "§fEn cola TIER: §a" + queueSize,
                        "§fTu ELO:        §e" + myElo,
                        "§fTier " + capitalize(kit.getName()) + ": " + kitTier.formatted(),
                        "§fPuntos:        §6" + kitPts,
                        "§fInsignia:      " + myTitle.formatted()));
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

    // ── Kit Reorder GUI ──────────────────────────────────────────────────────

    public static final String REORDER_TITLE_PREFIX        = "§8Reordenar kit: §e";
    public static final String PERSONAL_REORDER_TITLE_PRE = "§8Mi kit: §b";

    /**
     * Opens a 36-slot chest with the GLOBAL kit contents (admin use).
     * Closing the inventory saves globally.
     */
    public void openReorder(Player player, String kitName) {
        Kit kit = plugin.getKitManager().getKit(kitName);
        if (kit == null) {
            player.sendMessage(plugin.prefix() + "§cKit '§e" + kitName + "§c' no encontrado.");
            return;
        }
        KitReorderHolder holder = new KitReorderHolder(kitName.toLowerCase()); // null UUID = global
        Inventory inv = Bukkit.createInventory(holder, 36,
                REORDER_TITLE_PREFIX + capitalize(kitName));
        holder.setInventory(inv);

        ItemStack[] contents = kit.getContents();
        for (int i = 0; i < Math.min(contents.length, 36); i++) {
            if (contents[i] != null && contents[i].getType() != Material.AIR) {
                inv.setItem(i, contents[i].clone());
            }
        }
        player.openInventory(inv);
    }

    /**
     * Opens a 36-slot chest pre-filled with the player's PERSONAL kit layout
     * (falls back to the global kit if no personal layout exists).
     * Closing saves the layout only for this player.
     */
    public void openPersonalReorder(Player player, String kitName) {
        Kit kit = plugin.getKitManager().getKit(kitName);
        if (kit == null) {
            player.sendMessage(plugin.prefix() + "§cKit '§e" + kitName + "§c' no encontrado.");
            return;
        }
        KitReorderHolder holder = new KitReorderHolder(kitName.toLowerCase(), player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 36,
                PERSONAL_REORDER_TITLE_PRE + capitalize(kitName));
        holder.setInventory(inv);

        // Use personal layout if exists, otherwise fall back to global
        ItemStack[] personal = plugin.getPersonalKitManager().getPersonalLayout(player.getUniqueId(), kitName);
        ItemStack[] contents = (personal != null) ? personal : kit.getContents();
        for (int i = 0; i < Math.min(contents.length, 36); i++) {
            if (contents[i] != null && contents[i].getType() != Material.AIR) {
                inv.setItem(i, contents[i].clone());
            }
        }
        player.openInventory(inv);
        player.sendMessage(plugin.prefix() + "§7Edita tu kit §e" + capitalize(kitName) + "§7. §8(Solo afecta a ti)");
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
