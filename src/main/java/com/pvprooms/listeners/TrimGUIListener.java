package com.pvprooms.listeners;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.gui.TrimGUI;
import com.pvprooms.gui.TrimGUIHolder;
import com.pvprooms.model.ArmorPiece;
import com.pvprooms.model.Trim;
import org.bukkit.Material;
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

        // Handle crate selection GUI
        String title = event.getView().getTitle();
        if (title.contains("SELECCIONA PIEZA")) {
            handleCrateSelection(event.getRawSlot(), player, event.getCurrentItem());
            return;
        }

        TrimGUI gui = plugin.getTrimGUI();

        switch (h.getPage()) {

            case MAIN -> handleMain(event.getRawSlot(), event.isShiftClick(), player, h, gui);

            case PICK_PATTERN -> handlePickPattern(event.getRawSlot(), player, h, gui);

            case PICK_MATERIAL -> handlePickMaterial(event.getRawSlot(), player, h, gui);

            case REWARD -> handleReward(event.getRawSlot(), player, h, gui);
        }
    }

    private void handleCrateSelection(int slot, Player player, org.bukkit.inventory.ItemStack item) {
        if (slot == 22) { // Close button
            player.closeInventory();
            return;
        }

        // Check for crate_piece data
        if (!item.hasItemMeta()) return;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        var key = new org.bukkit.NamespacedKey(plugin, "crate_piece");
        if (!pdc.has(key, org.bukkit.persistence.PersistentDataType.STRING)) return;

        String pieceKey = pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING);
        ArmorPiece piece = ArmorPiece.fromName(pieceKey);
        if (piece == null) return;

        // Check for key in inventory
        int keySlot = findKeySlot(player);
        if (keySlot == -1) {
            player.sendMessage(plugin.prefix() + "§cNo tienes llaves de crate.");
            return;
        }

        // Consume key
        var inv = player.getInventory();
        var keyItem = inv.getItem(keySlot);
        if (keyItem.getAmount() > 1) {
            keyItem.setAmount(keyItem.getAmount() - 1);
        } else {
            inv.setItem(keySlot, null);
        }

        // Open roulette
        player.closeInventory();
        plugin.getTrimRouletteGUI().openRoulette(player, piece, "normal", false);
    }

    private int findKeySlot(Player player) {
        var contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (com.pvprooms.model.TrimCrate.isKey(contents[i])) return i;
        }
        return -1;
    }

    // ── MAIN page ─────────────────────────────────────────────────────────

    private void handleMain(int slot, boolean shift, Player player, TrimGUIHolder h, TrimGUI gui) {
        // New armor piece slots: 11=HELMET, 15=CHESTPLATE, 29=LEGGINGS, 33=BOOTS
        ArmorPiece piece = pieceFromMainSlot(slot);
        if (piece != null) {
            if (shift) {
                plugin.getTrimManager().clearPlayerTrim(player.getUniqueId(), piece);
                // Also remove trim from current armor instantly
                plugin.getTrimManager().applyAllTrimsInstantly(player);
                player.sendMessage(plugin.prefix() + "§aTrim de §f" + piece.getDisplayName() + " §aeliminado.");
                gui.openMain(player);
            } else {
                gui.openPickPattern(player, piece.name().toLowerCase());
            }
            return;
        }

        switch (slot) {
            case 49 -> { // Clear all
                plugin.getTrimManager().clearAllTrims(player.getUniqueId());
                // Also remove all trims from current armor instantly
                plugin.getTrimManager().applyAllTrimsInstantly(player);
                player.sendMessage(plugin.prefix() + "§aTodos tus trims han sido eliminados.");
                gui.openMain(player);
            }
            case 45 -> player.closeInventory(); // Close
        }
    }

    // ── PICK_PATTERN page ─────────────────────────────────────────────────

    private static final int[] PATTERN_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private void handlePickPattern(int slot, Player player, TrimGUIHolder h, TrimGUI gui) {
        if (slot == 45) { gui.openMain(player); return; } // Back
        if (slot == 49) { // Clear this piece
            ArmorPiece piece = ArmorPiece.fromName(h.getPieceKey());
            if (piece != null) {
                plugin.getTrimManager().clearPlayerTrim(player.getUniqueId(), piece);
                // Also remove trim from current armor instantly
                plugin.getTrimManager().applyAllTrimsInstantly(player);
                player.sendMessage(plugin.prefix() + "§aTrim de §f" + piece.getDisplayName() + " §aeliminado.");
            }
            gui.openMain(player);
            return;
        }

        // BLOCK LOCKED TRIMS (BARRIER = locked)
        var clicked = player.getOpenInventory().getItem(slot);
        if (clicked != null && clicked.getType() == Material.BARRIER) {
            player.sendMessage(plugin.prefix() + "§c¡Este patrón está bloqueado! Usa crates para desbloquearlo.");
            return;
        }

        String pattern = patternFromSlot(slot, plugin.getTrimManager().getPatternKeys(), PATTERN_SLOTS);
        if (pattern == null) return;

        gui.openPickMaterial(player, h.getPieceKey(), pattern);
    }

    // ── PICK_MATERIAL page ────────────────────────────────────────────────

    private static final int[] MATERIAL_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private void handlePickMaterial(int slot, Player player, TrimGUIHolder h, TrimGUI gui) {
        if (slot == 45) { // Back to pick pattern
            gui.openPickPattern(player, h.getPieceKey());
            return;
        }

        String material = materialFromSlot(slot, plugin.getTrimManager().getMaterialKeys(), MATERIAL_SLOTS);
        if (material == null) return;

        ArmorPiece piece = ArmorPiece.fromName(h.getPieceKey());
        if (piece == null) { gui.openMain(player); return; }

        Trim trim = new Trim(material, h.getPatternKey());
        plugin.getTrimManager().setPlayerTrim(player.getUniqueId(), piece, trim);
        
        // Apply trim instantly to current armor if player is wearing something
        plugin.getTrimManager().applyTrimInstantly(player, piece, trim);

        String col = plugin.getTrimManager().patternColour(trim.getPattern());
        String mc  = plugin.getTrimManager().materialColour(material);
        player.sendMessage(plugin.prefix() + "§a✓ §7Trim §f" + piece.getDisplayName()
                + " §7→ " + col + cap(trim.getPattern()) + " §7de §r" + mc + cap(material));

        gui.openMain(player);
    }

    // ── REWARD page ───────────────────────────────────────────────────────

    private void handleReward(int slot, Player player, TrimGUIHolder h, TrimGUI gui) {
        if (slot == 49) { // Discard
            player.sendMessage(plugin.prefix() + "§cTrim descartado.");
            player.closeInventory();
            return;
        }

        // Reward page armor slots: 20, 21, 23, 24
        ArmorPiece piece = pieceFromRewardSlot(slot);
        if (piece == null) return;

        Trim trim = Trim.fromString(h.getRewardTrim());
        if (trim == null) { player.closeInventory(); return; }

        plugin.getTrimManager().setPlayerTrim(player.getUniqueId(), piece, trim);
        
        // Apply trim instantly to current armor if player is wearing something
        plugin.getTrimManager().applyTrimInstantly(player, piece, trim);
        
        String col = plugin.getTrimManager().patternColour(trim.getPattern());
        String mc  = plugin.getTrimManager().materialColour(trim.getMaterial());
        player.sendMessage(plugin.prefix() + "§a✦ §7Trim §f" + piece.getDisplayName()
                + " §7equipado: " + col + cap(trim.getPattern())
                + " §7de §r" + mc + cap(trim.getMaterial()));
        player.closeInventory();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Returns the ArmorPiece for the slot positions used in MAIN page. */
    private static ArmorPiece pieceFromMainSlot(int slot) {
        return switch (slot) {
            case 11 -> ArmorPiece.HELMET;
            case 15 -> ArmorPiece.CHESTPLATE;
            case 29 -> ArmorPiece.LEGGINGS;
            case 33 -> ArmorPiece.BOOTS;
            default -> null;
        };
    }

    /** Returns the ArmorPiece for the slot positions used in REWARD page. */
    private static ArmorPiece pieceFromRewardSlot(int slot) {
        return switch (slot) {
            case 20 -> ArmorPiece.HELMET;
            case 21 -> ArmorPiece.CHESTPLATE;
            case 23 -> ArmorPiece.LEGGINGS;
            case 24 -> ArmorPiece.BOOTS;
            default -> null;
        };
    }

    private static String patternFromSlot(int slot, java.util.List<String> keys, int[] validSlots) {
        for (int i = 0; i < validSlots.length && i < keys.size(); i++) {
            if (validSlots[i] == slot) return keys.get(i);
        }
        return null;
    }

    private static String materialFromSlot(int slot, java.util.List<String> keys, int[] validSlots) {
        for (int i = 0; i < validSlots.length && i < keys.size(); i++) {
            if (validSlots[i] == slot) return keys.get(i);
        }
        return null;
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
