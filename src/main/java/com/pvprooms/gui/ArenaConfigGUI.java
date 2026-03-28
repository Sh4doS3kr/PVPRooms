package com.pvprooms.gui;

import com.pvprooms.model.ArenaTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * GUI for configuring per-arena game rules.
 *
 * Layout (27 slots):
 *   Slot 11 — Allow Explosions
 *   Slot 13 — Allow Block Break
 *   Slot 15 — Allow Block Place
 *   Slot 22 — Close / Back
 *
 * Each toggle shows green (enabled) or red (disabled) concrete.
 * Clicking toggles the setting immediately and saves.
 */
public class ArenaConfigGUI {

    public static final int SLOT_EXPLOSIONS  = 11;
    public static final int SLOT_BLOCK_BREAK = 13;
    public static final int SLOT_BLOCK_PLACE = 15;
    public static final int SLOT_CLOSE       = 22;

    public Inventory build(ArenaTemplate template) {
        ArenaConfigHolder holder = new ArenaConfigHolder(template);
        String title = "§8Mapa: §6" + template.getName();
        Inventory inv = Bukkit.createInventory(holder, 27, title);
        holder.setInventory(inv);

        // Glass pane filler
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(SLOT_EXPLOSIONS,  toggle(template.isAllowExplosions(),
                "§c☠ Explosiones",
                List.of("§7Permite que TNT y explosiones",
                        "§7destruyan bloques en esta arena.",
                        "",
                        "§7La arena se restaura automáticamente",
                        "§7al crear una nueva instancia.")));

        inv.setItem(SLOT_BLOCK_BREAK, toggle(template.isAllowBlockBreak(),
                "§6⛏ Romper Bloques",
                List.of("§7Permite que los jugadores",
                        "§7rompan bloques durante el duelo.",
                        "",
                        "§7La arena se restaura automáticamente",
                        "§7al crear una nueva instancia.")));

        inv.setItem(SLOT_BLOCK_PLACE, toggle(template.isAllowBlockPlace(),
                "§a⬜ Colocar Bloques",
                List.of("§7Permite que los jugadores",
                        "§7coloquen bloques durante el duelo.",
                        "",
                        "§7La arena se restaura automáticamente",
                        "§7al crear una nueva instancia.")));

        inv.setItem(SLOT_CLOSE, item(Material.BARRIER,
                "§cCerrar",
                List.of("§7Cierra este panel.")));

        return inv;
    }

    /** Refreshes a single toggle slot without rebuilding the whole inventory. */
    public void refreshToggle(Inventory inv, ArenaTemplate template, int slot) {
        switch (slot) {
            case SLOT_EXPLOSIONS  -> inv.setItem(slot, toggle(template.isAllowExplosions(),
                    "§c☠ Explosiones",
                    List.of("§7Permite que TNT y explosiones",
                            "§7destruyan bloques en esta arena.",
                            "",
                            "§7La arena se restaura automáticamente",
                            "§7al crear una nueva instancia.")));
            case SLOT_BLOCK_BREAK -> inv.setItem(slot, toggle(template.isAllowBlockBreak(),
                    "§6⛏ Romper Bloques",
                    List.of("§7Permite que los jugadores",
                            "§7rompan bloques durante el duelo.",
                            "",
                            "§7La arena se restaura automáticamente",
                            "§7al crear una nueva instancia.")));
            case SLOT_BLOCK_PLACE -> inv.setItem(slot, toggle(template.isAllowBlockPlace(),
                    "§a⬜ Colocar Bloques",
                    List.of("§7Permite que los jugadores",
                            "§7coloquen bloques durante el duelo.",
                            "",
                            "§7La arena se restaura automáticamente",
                            "§7al crear una nueva instancia.")));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private ItemStack toggle(boolean enabled, String name, List<String> baseLore) {
        Material mat  = enabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        String status = enabled ? "§a§lACTIVADO" : "§c§lDESACTIVADO";
        List<String> lore = new java.util.ArrayList<>(baseLore);
        lore.add("");
        lore.add("§8Estado: " + status);
        lore.add("§7Click para cambiar.");
        return item(mat, name, lore);
    }

    private ItemStack item(Material mat, String name) {
        return item(mat, name, List.of());
    }

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta m = is.getItemMeta();
        if (m != null) {
            m.setDisplayName(name);
            m.setLore(lore);
            is.setItemMeta(m);
        }
        return is;
    }
}
