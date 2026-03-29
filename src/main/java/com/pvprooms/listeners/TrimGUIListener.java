package com.pvprooms.listeners;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.gui.TrimGUI;
import com.pvprooms.gui.TrimGUIHolder;
import com.pvprooms.model.ArmorPiece;
import com.pvprooms.model.Trim;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Handles all click events inside the player trim management GUI.
 */
public class TrimGUIListener implements Listener {

    private final PvPRoomsPro plugin;

    public TrimGUIListener(PvPRoomsPro plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof TrimGUIHolder h)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        TrimGUI gui = plugin.getTrimGUI();

        switch (h.getPage()) {

            case MAIN -> handleMain(event.getRawSlot(), event.isShiftClick(), player, h, gui);

            case PICK_PATTERN -> handlePickPattern(event.getRawSlot(), player, h, gui);

            case PICK_MATERIAL -> handlePickMaterial(event.getRawSlot(), player, h, gui);

            case REWARD -> handleReward(event.getRawSlot(), player, h, gui);
        }
    }

    // ── MAIN page ─────────────────────────────────────────────────────────

    private void handleMain(int slot, boolean shift, Player player, TrimGUIHolder h, TrimGUI gui) {
        // Armor piece slots: 20=HELMET, 22=CHESTPLATE, 24=LEGGINGS, 26=BOOTS
        ArmorPiece piece = pieceFromMainSlot(slot);
        if (piece != null) {
            if (shift) {
                plugin.getTrimManager().clearPlayerTrim(player.getUniqueId(), piece);
                player.sendMessage(plugin.prefix() + "§7Trim de §f" + piece.getDisplayName() + " §7eliminado.");
                gui.openMain(player);
            } else {
                gui.openPickPattern(player, piece.name().toLowerCase());
            }
            return;
        }

        switch (slot) {
            case 49 -> { // Clear all
                plugin.getTrimManager().clearAllTrims(player.getUniqueId());
                player.sendMessage(plugin.prefix() + "§7Todos tus trims han sido eliminados.");
                gui.openMain(player);
            }
            case 45 -> player.closeInventory(); // Close
        }
    }

    // ── PICK_PATTERN page ─────────────────────────────────────────────────

    private void handlePickPattern(int slot, Player player, TrimGUIHolder h, TrimGUI gui) {
        if (slot == 49) { gui.openMain(player); return; } // Back
        if (isBorder(slot)) return;

        String pattern = patternFromSlot(slot, plugin.getTrimManager().getPatternKeys());
        if (pattern == null) return;

        gui.openPickMaterial(player, h.getPieceKey(), pattern);
    }

    // ── PICK_MATERIAL page ────────────────────────────────────────────────

    private void handlePickMaterial(int slot, Player player, TrimGUIHolder h, TrimGUI gui) {
        if (slot == 49) { // Back to pick pattern
            gui.openPickPattern(player, h.getPieceKey());
            return;
        }
        if (isBorder(slot)) return;

        String material = materialFromSlot(slot, plugin.getTrimManager().getMaterialKeys());
        if (material == null) return;

        ArmorPiece piece = ArmorPiece.fromName(h.getPieceKey());
        if (piece == null) { gui.openMain(player); return; }

        Trim trim = new Trim(material, h.getPatternKey());
        plugin.getTrimManager().setPlayerTrim(player.getUniqueId(), piece, trim);

        String col = plugin.getTrimManager().patternColour(trim.getPattern());
        String mc  = plugin.getTrimManager().materialColour(material);
        player.sendMessage(plugin.prefix() + "§7Trim §f" + piece.getDisplayName()
                + " §7→ " + col + cap(trim.getPattern()) + " §7de §r" + mc + cap(material));

        gui.openMain(player);
    }

    // ── REWARD page ───────────────────────────────────────────────────────

    private void handleReward(int slot, Player player, TrimGUIHolder h, TrimGUI gui) {
        if (slot == 49) { // Discard
            player.sendMessage(plugin.prefix() + "§7Trim descartado.");
            player.closeInventory();
            return;
        }

        // Armor piece slots: 20, 22, 24, 26
        ArmorPiece piece = pieceFromMainSlot(slot);
        if (piece == null) return;

        Trim trim = Trim.fromString(h.getRewardTrim());
        if (trim == null) { player.closeInventory(); return; }

        plugin.getTrimManager().setPlayerTrim(player.getUniqueId(), piece, trim);
        String col = plugin.getTrimManager().patternColour(trim.getPattern());
        String mc  = plugin.getTrimManager().materialColour(trim.getMaterial());
        player.sendMessage(plugin.prefix() + "§a✦ §7Trim §f" + piece.getDisplayName()
                + " §7equipado: " + col + cap(trim.getPattern())
                + " §7de §r" + mc + cap(trim.getMaterial()));
        player.closeInventory();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Returns the ArmorPiece for the slot positions used in MAIN and REWARD pages. */
    private static ArmorPiece pieceFromMainSlot(int slot) {
        return switch (slot) {
            case 20 -> ArmorPiece.HELMET;
            case 22 -> ArmorPiece.CHESTPLATE;
            case 24 -> ArmorPiece.LEGGINGS;
            case 26 -> ArmorPiece.BOOTS;
            default -> null;
        };
    }

    private static String patternFromSlot(int slot, java.util.List<String> keys) {
        return itemFromSlot(slot, keys);
    }

    private static String materialFromSlot(int slot, java.util.List<String> keys) {
        return itemFromSlot(slot, keys);
    }

    /** Maps a raw inventory slot (10–43, skipping borders) to a key list index. */
    private static String itemFromSlot(int slot, java.util.List<String> keys) {
        int index = 0;
        for (int s = 10; s <= 43; s++) {
            if (s % 9 == 0 || s % 9 == 8) continue;
            if (s == slot) return index < keys.size() ? keys.get(index) : null;
            index++;
        }
        return null;
    }

    private static boolean isBorder(int slot) {
        return slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8;
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
