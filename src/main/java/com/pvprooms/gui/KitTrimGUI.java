package com.pvprooms.gui;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.managers.TrimManager;
import com.pvprooms.model.ArmorPiece;
import com.pvprooms.model.Kit;
import com.pvprooms.model.Trim;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds all pages of the admin kit-trim configuration GUI.
 *
 * Flow: PICK_PIECE → PICK_PATTERN → PICK_MATERIAL → (saves to kit and returns)
 */
public class KitTrimGUI {

    private static final Map<String, Material> PATTERN_ICONS = Map.ofEntries(
            Map.entry("bolt",      Material.LIGHTNING_ROD),
            Map.entry("coast",     Material.DRIED_KELP),
            Map.entry("dune",      Material.SANDSTONE),
            Map.entry("eye",       Material.ENDER_EYE),
            Map.entry("flow",      Material.CHERRY_LEAVES),
            Map.entry("host",      Material.CHORUS_FLOWER),
            Map.entry("raiser",    Material.BAMBOO),
            Map.entry("rib",       Material.BONE),
            Map.entry("sentry",    Material.IRON_SWORD),
            Map.entry("shaper",    Material.QUARTZ_BLOCK),
            Map.entry("silence",   Material.SCULK),
            Map.entry("snout",     Material.PIGLIN_HEAD),
            Map.entry("spire",     Material.DEEPSLATE_BRICKS),
            Map.entry("tide",      Material.PRISMARINE_CRYSTALS),
            Map.entry("vex",       Material.AMETHYST_SHARD),
            Map.entry("ward",      Material.IRON_DOOR),
            Map.entry("wayfinder", Material.MAP),
            Map.entry("wild",      Material.OAK_LEAVES)
    );

    private static final Map<String, Material> MATERIAL_ICONS = Map.ofEntries(
            Map.entry("amethyst",  Material.AMETHYST_SHARD),
            Map.entry("copper",    Material.COPPER_INGOT),
            Map.entry("diamond",   Material.DIAMOND),
            Map.entry("emerald",   Material.EMERALD),
            Map.entry("gold",      Material.GOLD_INGOT),
            Map.entry("iron",      Material.IRON_INGOT),
            Map.entry("lapis",     Material.LAPIS_LAZULI),
            Map.entry("netherite", Material.NETHERITE_INGOT),
            Map.entry("quartz",    Material.QUARTZ),
            Map.entry("redstone",  Material.REDSTONE)
    );

    private final PvPRoomsPro plugin;

    public KitTrimGUI(PvPRoomsPro plugin) { this.plugin = plugin; }

    // ── Public openers ────────────────────────────────────────────────────

    /** Page 1: choose which armor piece to edit for this kit. */
    public void openPickPiece(Player admin, String kitName) {
        Kit kit = plugin.getKitManager().getKit(kitName);
        if (kit == null) {
            admin.sendMessage(plugin.prefix() + "§cKit '§e" + kitName + "§c' no encontrado.");
            return;
        }
        TrimManager tm = plugin.getTrimManager();
        Map<ArmorPiece, Trim> kitTrims = kit.getTrims();

        KitTrimGUIHolder holder = new KitTrimGUIHolder(kitName,
                KitTrimGUIHolder.Page.PICK_PIECE, null, null);
        Inventory inv = Bukkit.createInventory(holder, 54,
                "§2⚙ §aKit Trims: §f" + cap(kitName));
        holder.setInventory(inv);
        fillBorder(inv);

        ArmorPiece[] pieces = ArmorPiece.values();
        int[] pieceSlots = {20, 22, 24, 26};
        for (int i = 0; i < pieces.length; i++) {
            ArmorPiece piece = pieces[i];
            Trim current = kitTrims.get(piece);
            inv.setItem(pieceSlots[i], buildPieceItem(piece, current, tm));
        }

        // Clear all kit trims — slot 49
        inv.setItem(49, buildItem(Material.BARRIER, "§c§lLimpiar todos los trims del kit",
                List.of("§7Elimina todos los trims por defecto de §f" + cap(kitName) + "§7.")));
        inv.setItem(45, buildItem(Material.DARK_OAK_DOOR, "§7Cerrar", List.of()));

        admin.openInventory(inv);
    }

    /** Page 2: choose pattern for the selected piece. */
    public void openPickPattern(Player admin, String kitName, String pieceKey) {
        TrimManager tm = plugin.getTrimManager();
        KitTrimGUIHolder holder = new KitTrimGUIHolder(kitName,
                KitTrimGUIHolder.Page.PICK_PATTERN, pieceKey, null);
        Inventory inv = Bukkit.createInventory(holder, 54,
                "§2Patrón kit §f" + cap(kitName) + " §2— §f" + cap(pieceKey));
        holder.setInventory(inv);
        fillBorder(inv);

        List<String> patterns = tm.getPatternKeys();
        int slot = 10;
        for (String pattern : patterns) {
            if (slot > 43) break;
            if (slot % 9 == 0 || slot % 9 == 8) { slot++; continue; }
            boolean legendary = tm.getLegendaryPatternKeys().contains(pattern);
            String colour = tm.patternColour(pattern);
            Material icon = PATTERN_ICONS.getOrDefault(pattern, Material.PAPER);
            inv.setItem(slot, buildItem(icon, colour + "§l" + cap(pattern),
                    List.of("§8Patrón: " + colour + cap(pattern),
                            legendary ? "§5§lLEGENDARIO" : "§7Normal",
                            "", "§7Click para seleccionar.")));
            slot++;
        }

        inv.setItem(49, buildItem(Material.ARROW, "§7← Volver", List.of()));
        admin.openInventory(inv);
    }

    /** Page 3: choose material for the selected piece+pattern. */
    public void openPickMaterial(Player admin, String kitName, String pieceKey, String patternKey) {
        TrimManager tm = plugin.getTrimManager();
        KitTrimGUIHolder holder = new KitTrimGUIHolder(kitName,
                KitTrimGUIHolder.Page.PICK_MATERIAL, pieceKey, patternKey);
        Inventory inv = Bukkit.createInventory(holder, 54,
                "§2Material kit §f" + cap(kitName) + " §2— §f" + cap(patternKey));
        holder.setInventory(inv);
        fillBorder(inv);

        List<String> materials = tm.getMaterialKeys();
        int slot = 10;
        for (String mat : materials) {
            if (slot > 43) break;
            if (slot % 9 == 0 || slot % 9 == 8) { slot++; continue; }
            String colour = tm.materialColour(mat);
            Material icon = MATERIAL_ICONS.getOrDefault(mat, Material.IRON_INGOT);
            inv.setItem(slot, buildItem(icon, colour + "§l" + cap(mat),
                    List.of("§8Material: " + colour + cap(mat),
                            "§8Patrón:   §f" + cap(patternKey),
                            "", "§7Click para aplicar al kit.")));
            slot++;
        }

        inv.setItem(49, buildItem(Material.ARROW, "§7← Volver", List.of()));
        admin.openInventory(inv);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private ItemStack buildPieceItem(ArmorPiece piece, Trim current, TrimManager tm) {
        List<String> lore = new ArrayList<>();
        if (current != null) {
            String col = tm.patternColour(current.getPattern());
            String mc  = tm.materialColour(current.getMaterial());
            lore.add("§8Patrón:   " + col + cap(current.getPattern()));
            lore.add("§8Material: " + mc  + cap(current.getMaterial()));
            lore.add(""); lore.add("§7Click para cambiar.");
            lore.add("§cShift+Click para limpiar.");
        } else {
            lore.add("§8Sin trim configurado.");
            lore.add(""); lore.add("§7Click para añadir un trim.");
        }
        String title = piece.getSymbol() + " §f§l" + piece.getDisplayName();
        if (current != null) title += " §8(" + cap(current.getPattern()) + ")";
        return buildItem(piece.getDisplayMaterial(), title, lore);
    }

    private void fillBorder(Inventory inv) {
        ItemStack glass = buildItem(Material.GREEN_STAINED_GLASS_PANE, "§r", List.of());
        for (int i = 0; i < 9; i++)   inv.setItem(i, glass);
        for (int i = 45; i < 54; i++) inv.setItem(i, glass);
        for (int i = 0; i < 6; i++) {
            inv.setItem(i * 9,     glass);
            inv.setItem(i * 9 + 8, glass);
        }
    }

    ItemStack buildItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
