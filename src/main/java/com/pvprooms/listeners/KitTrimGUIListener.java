package com.pvprooms.listeners;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.gui.KitTrimGUI;
import com.pvprooms.gui.KitTrimGUIHolder;
import com.pvprooms.model.ArmorPiece;
import com.pvprooms.model.Kit;
import com.pvprooms.model.Trim;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/**
 * Handles all click events inside the admin kit-trim configuration GUI.
 */
public class KitTrimGUIListener implements Listener {

    private final PvPRoomsPro plugin;

    public KitTrimGUIListener(PvPRoomsPro plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof KitTrimGUIHolder h)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        KitTrimGUI gui = plugin.getKitTrimGUI();

        switch (h.getPage()) {
            case PICK_PIECE   -> handlePickPiece(event.getRawSlot(), event.isShiftClick(), player, h, gui);
            case PICK_PATTERN -> handlePickPattern(event.getRawSlot(), player, h, gui);
            case PICK_MATERIAL -> handlePickMaterial(event.getRawSlot(), player, h, gui);
        }
    }

    // ── PICK_PIECE page ───────────────────────────────────────────────────

    private void handlePickPiece(int slot, boolean shift, Player player, KitTrimGUIHolder h, KitTrimGUI gui) {
        ArmorPiece piece = pieceFromSlot(slot);
        if (piece != null) {
            if (shift) {
                Kit kit = plugin.getKitManager().getKit(h.getKitName());
                if (kit != null) {
                    kit.clearTrim(piece);
                    plugin.getKitManager().saveKits();
                    player.sendMessage(plugin.prefix() + "§7Trim de §f" + piece.getDisplayName()
                            + " §7del kit §e" + h.getKitName() + " §7eliminado.");
                    gui.openPickPiece(player, h.getKitName());
                }
            } else {
                gui.openPickPattern(player, h.getKitName(), piece.name().toLowerCase());
            }
            return;
        }

        switch (slot) {
            case 49 -> { // Clear all kit trims
                Kit kit = plugin.getKitManager().getKit(h.getKitName());
                if (kit != null) {
                    kit.clearAllTrims();
                    plugin.getKitManager().saveKits();
                    player.sendMessage(plugin.prefix() + "§7Todos los trims del kit §e"
                            + h.getKitName() + " §7eliminados.");
                    gui.openPickPiece(player, h.getKitName());
                }
            }
            case 45 -> player.closeInventory();
        }
    }

    // ── PICK_PATTERN page ─────────────────────────────────────────────────

    private void handlePickPattern(int slot, Player player, KitTrimGUIHolder h, KitTrimGUI gui) {
        if (slot == 49) { gui.openPickPiece(player, h.getKitName()); return; }
        if (isBorder(slot)) return;

        String pattern = keyFromSlot(slot, plugin.getTrimManager().getPatternKeys());
        if (pattern == null) return;

        gui.openPickMaterial(player, h.getKitName(), h.getPieceKey(), pattern);
    }

    // ── PICK_MATERIAL page ────────────────────────────────────────────────

    private void handlePickMaterial(int slot, Player player, KitTrimGUIHolder h, KitTrimGUI gui) {
        if (slot == 49) {
            gui.openPickPattern(player, h.getKitName(), h.getPieceKey());
            return;
        }
        if (isBorder(slot)) return;

        String material = keyFromSlot(slot, plugin.getTrimManager().getMaterialKeys());
        if (material == null) return;

        ArmorPiece piece = ArmorPiece.fromName(h.getPieceKey());
        if (piece == null) { gui.openPickPiece(player, h.getKitName()); return; }

        Kit kit = plugin.getKitManager().getKit(h.getKitName());
        if (kit == null) { player.closeInventory(); return; }

        Trim trim = new Trim(material, h.getPatternKey());
        kit.setTrim(piece, trim);
        plugin.getKitManager().saveKits();

        String col = plugin.getTrimManager().patternColour(trim.getPattern());
        String mc  = plugin.getTrimManager().materialColour(material);
        player.sendMessage(plugin.prefix() + "§7Kit §e" + h.getKitName()
                + " §7— trim §f" + piece.getDisplayName()
                + " §7→ " + col + cap(trim.getPattern())
                + " §7de §r" + mc + cap(material));

        gui.openPickPiece(player, h.getKitName());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static ArmorPiece pieceFromSlot(int slot) {
        return switch (slot) {
            case 20 -> ArmorPiece.HELMET;
            case 22 -> ArmorPiece.CHESTPLATE;
            case 24 -> ArmorPiece.LEGGINGS;
            case 26 -> ArmorPiece.BOOTS;
            default -> null;
        };
    }

    private static String keyFromSlot(int slot, List<String> keys) {
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
