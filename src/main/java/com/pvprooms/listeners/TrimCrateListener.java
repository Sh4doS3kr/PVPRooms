package com.pvprooms.listeners;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.ArmorPiece;
import com.pvprooms.model.Trim;
import com.pvprooms.model.TrimCrate;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Handles the trim crate opening mechanic.
 *
 * Trigger: player clicks a TrimCrate item in their own inventory while they
 * have at least one TrimCrate key somewhere in their inventory.
 *
 * Result:
 *  - Consumes 1 crate + 1 key.
 *  - 10% chance → legendary crate reward; 90% → normal.
 *  - Opens the TrimGUI reward page for the player to choose which piece to equip.
 */
public class TrimCrateListener implements Listener {

    private final PvPRoomsPro plugin;

    public TrimCrateListener(PvPRoomsPro plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getClickedInventory() instanceof PlayerInventory)) return;

        ItemStack clicked = event.getCurrentItem();
        if (!TrimCrate.isCrate(clicked)) return;

        // Find a key in the player's inventory
        int keySlot = findKeySlot(player);
        if (keySlot == -1) {
            event.setCancelled(true);
            player.sendMessage(plugin.prefix()
                    + "§cNecesitas una §6§lLlave de Crate §cpara abrir esto.");
            return;
        }

        event.setCancelled(true);

        // Determine if legendary (10%) — ignore whether crate is pre-legendary;
        // any normal crate has a 10% chance to upgrade on open
        boolean legendary = Math.random() < 0.10;
        if (TrimCrate.isLegendary(clicked)) legendary = true;

        // Check if it's a themed crate and get the armor piece
        ArmorPiece themedPiece = TrimCrate.getArmorPiece(clicked);
        boolean isThemed = themedPiece != null;

        // Consume 1 crate
        if (clicked.getAmount() > 1) clicked.setAmount(clicked.getAmount() - 1);
        else event.getClickedInventory().setItem(event.getSlot(), null);

        // Consume 1 key
        ItemStack keyItem = player.getInventory().getItem(keySlot);
        if (keyItem != null) {
            if (keyItem.getAmount() > 1) keyItem.setAmount(keyItem.getAmount() - 1);
            else player.getInventory().setItem(keySlot, null);
        }

        // Generate trim based on crate type
        Trim trim;
        if (isThemed) {
            trim = plugin.getTrimManager().randomTrimForPiece(themedPiece, legendary);
        } else {
            trim = plugin.getTrimManager().randomTrim(legendary);
        }

        String col = plugin.getTrimManager().patternColour(trim.getPattern());
        String mc  = plugin.getTrimManager().materialColour(trim.getMaterial());
        
        if (isThemed) {
            player.sendMessage(plugin.prefix() + "§e§l✦ " + themedPiece.getSymbol() + " §fCrate de " + themedPiece.getDisplayName() + " §e§l✦");
            player.sendMessage(plugin.prefix() + "§7Obtuviste: " + col + cap(trim.getPattern())
                    + " §7de §r" + mc + cap(trim.getMaterial()) + " §7para " + themedPiece.getDisplayName());
            player.sendMessage(plugin.prefix() + "§7§l🔒 Trim desbloqueado específicamente para " + themedPiece.getDisplayName());
        } else {
            player.sendMessage(plugin.prefix() + (legendary ? "§5§l✦ §dCrate Legendario §5§l✦" : "§b✦ §eCrate de Trims §b✦"));
            player.sendMessage(plugin.prefix() + "§7Obtuviste: " + col + cap(trim.getPattern())
                    + " §7de §r" + mc + cap(trim.getMaterial()));
            player.sendMessage(plugin.prefix() + "§7§l🔒 Trim desbloqueado para una pieza aleatoria de armadura");
        }

        plugin.getTrimGUI().openReward(player, trim);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static int findKeySlot(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (TrimCrate.isKey(contents[i])) return i;
        }
        return -1;
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
