package com.pvprooms.gui;

import com.pvprooms.managers.EloManager;
import com.pvprooms.managers.TierManager;
import com.pvprooms.model.Tier;
import org.bukkit.Bukkit;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * GUI shown when a player runs /queue.
 * Three options: join the ELO queue, TIER queue, or practice vs Bot.
 *
 * Layout (27 slots):
 *   Slot 10 — ELO  mode
 *   Slot 13 — BOT  mode (practice)
 *   Slot 16 — TIER mode
 */
public class QueueModeGUI {

    public static final int SLOT_ELO  = 10;
    public static final int SLOT_BOT  = 13;
    public static final int SLOT_TIER = 16;

    public Inventory build(EloManager eloManager, TierManager tierManager, UUID uuid) {
        QueueModeHolder holder = new QueueModeHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, "§8Selecciona el modo de §6juego");
        holder.setInventory(inv);

        // Filler
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        int playerElo = eloManager.getElo(uuid);
        Tier eloTier = Tier.forPlayer(eloManager, uuid);
        
        // For TIER queue, use points-based tier (best tier across all kits)
        Tier pointsTier = tierManager.getBestTier(uuid);
        int totalPoints = tierManager.getTotalPoints(uuid);

        // ELO button - shows ELO-based tier
        inv.setItem(SLOT_ELO, item(Material.COMPARATOR,
                "§e§l⚔ Cola ELO",
                List.of("§7Entra en la cola de ELO.",
                        "§7Rivales de cualquier rango.",
                        "",
                        "§fTu ELO: §e" + playerElo,
                        "§fTu Tier ELO: " + eloTier.formatted(),
                        "",
                        "§aClick para seleccionar kit.")));

        // TIER button - tier assigned manually via tickets
        inv.setItem(SLOT_TIER, item(pointsTier.icon,
                "§b§l🏆 Cola TIER",
                List.of("§7Entra en la cola de TIER.",
                        "§7Solo te enfrentarás a rivales",
                        "§7de tu mismo rango (±1 nivel).",
                        "",
                        "§fTu Tier: " + pointsTier.formatted(),
                        "§fPuntos:  §e" + totalPoints,
                        "§7Solicita test: §btiers.mlmc.lat",
                        "",
                        "§aClick para seleccionar kit.")));

        // BOT button - practice mode
        inv.setItem(SLOT_BOT, item(Material.ARMOR_STAND,
                "§6§l🤖 Práctica vs Bot",
                List.of("§7Entrena contra un bot con IA.",
                        "§7Elige dificultad y kit.",
                        "",
                        "§8• §aFácil §8- Reacción lenta",
                        "§8• §eMedio §8- Equilibrado",
                        "§8• §cDifícil §8- Muy preciso",
                        "§8• §4HACKER §8- Perfecto",
                        "",
                        "§c¡No afecta ELO ni Tier!",
                        "",
                        "§aClick para practicar.")));

        return inv;
    }

    // ── Helper ────────────────────────────────────────────────────────────

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

    private ItemStack item(Material mat, String name) {
        return item(mat, name, List.of());
    }
}
