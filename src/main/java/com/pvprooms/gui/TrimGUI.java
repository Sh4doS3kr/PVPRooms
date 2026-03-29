package com.pvprooms.gui;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.managers.TrimManager;
import com.pvprooms.model.ArmorPiece;
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
import java.util.UUID;

/**
 * Builds all pages of the player-facing trim management GUI.
 *
 * Pages:
 *   MAIN         – overview of current trims per armor piece
 *   PICK_PATTERN – select a pattern for the chosen piece
 *   PICK_MATERIAL– select a material after a pattern is chosen
 *   REWARD       – shown after opening a TrimCrate; choose piece to equip
 */
public class TrimGUI {

    // ── Pattern → icon Material ───────────────────────────────────────────
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

    // ── Material → icon Material ──────────────────────────────────────────
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

    public TrimGUI(PvPRoomsPro plugin) { this.plugin = plugin; }

    // ── Public openers ────────────────────────────────────────────────────

    public void openMain(Player player) {
        TrimManager tm = plugin.getTrimManager();
        Map<ArmorPiece, Trim> trims = tm.getPlayerTrims(player.getUniqueId());

        TrimGUIHolder holder = new TrimGUIHolder(player.getUniqueId(),
                TrimGUIHolder.Page.MAIN, null, null, null);
        Inventory inv = Bukkit.createInventory(holder, 54, "§5⚙ §dMis Trims de Armadura");
        holder.setInventory(inv);

        fillBorder(inv);

        ArmorPiece[] pieces = ArmorPiece.values();
        // Slots 20, 22, 24, 26 for the 4 pieces
        int[] pieceSlots = {20, 22, 24, 26};
        for (int i = 0; i < pieces.length; i++) {
            ArmorPiece piece = pieces[i];
            Trim current = trims.get(piece);
            inv.setItem(pieceSlots[i], buildPieceItem(piece, current, tm));
        }

        // Clear-all button — slot 49
        inv.setItem(49, buildItem(Material.BARRIER, "§c§lLimpiar todos los trims",
                List.of("§7Elimina todos tus trims personales.")));

        // Close — slot 45
        inv.setItem(45, buildItem(Material.DARK_OAK_DOOR, "§7Cerrar", List.of()));

        player.openInventory(inv);
    }

    public void openPickPattern(Player player, String pieceKey) {
        TrimManager tm = plugin.getTrimManager();
        TrimGUIHolder holder = new TrimGUIHolder(player.getUniqueId(),
                TrimGUIHolder.Page.PICK_PATTERN, pieceKey, null, null);
        Inventory inv = Bukkit.createInventory(holder, 54,
                "§5Patrón — " + cap(pieceKey));
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
            List<String> lore = new ArrayList<>(List.of(
                    "§8Patrón: " + colour + cap(pattern),
                    legendary ? "§5§lLEGENDARIO" : "§7Normal",
                    "",
                    "§7Click para seleccionar."
            ));
            inv.setItem(slot, buildItem(icon, colour + "§l" + cap(pattern), lore));
            slot++;
        }

        inv.setItem(49, buildItem(Material.ARROW, "§7← Volver", List.of()));
        player.openInventory(inv);
    }

    public void openPickMaterial(Player player, String pieceKey, String patternKey) {
        TrimManager tm = plugin.getTrimManager();
        TrimGUIHolder holder = new TrimGUIHolder(player.getUniqueId(),
                TrimGUIHolder.Page.PICK_MATERIAL, pieceKey, patternKey, null);
        Inventory inv = Bukkit.createInventory(holder, 54,
                "§5Material — " + cap(patternKey));
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
                            "", "§7Click para aplicar.")));
            slot++;
        }

        inv.setItem(49, buildItem(Material.ARROW, "§7← Volver", List.of()));
        player.openInventory(inv);
    }

    /**
     * Opens the crate-reward page showing the won trim.
     * The player clicks an armor piece slot to equip the trim to that piece.
     */
    public void openReward(Player player, Trim trim) {
        TrimManager tm = plugin.getTrimManager();
        String colour = tm.patternColour(trim.getPattern());
        String matCol = tm.materialColour(trim.getMaterial());

        TrimGUIHolder holder = new TrimGUIHolder(player.getUniqueId(),
                TrimGUIHolder.Page.REWARD, null, null, trim.toString());
        Inventory inv = Bukkit.createInventory(holder, 54, "§6✦ Recompensa del Crate §6✦");
        holder.setInventory(inv);

        fillBorder(inv);

        // Trim info — center top (slot 13)
        Material patternIcon = PATTERN_ICONS.getOrDefault(trim.getPattern(), Material.PAPER);
        inv.setItem(13, buildItem(patternIcon,
                colour + "§l" + cap(trim.getPattern()) + " §fde §r" + matCol + cap(trim.getMaterial()),
                List.of("§8Patrón:   " + colour + cap(trim.getPattern()),
                        "§8Material: " + matCol + cap(trim.getMaterial()),
                        "",
                        "§7Elige una pieza de armadura para equipar",
                        "§7este trim. §8(No se puede deshacer aquí)")));

        // Armor piece choices — row 3 (slots 20, 22, 24, 26)
        ArmorPiece[] pieces = ArmorPiece.values();
        int[] slots = {20, 22, 24, 26};
        Map<ArmorPiece, Trim> current = tm.getPlayerTrims(player.getUniqueId());
        for (int i = 0; i < pieces.length; i++) {
            ArmorPiece piece = pieces[i];
            Trim existing = current.get(piece);
            List<String> lore = new ArrayList<>();
            lore.add("§7Equipar en: §f" + piece.getDisplayName());
            if (existing != null) {
                lore.add("§8Reemplazará: §7" + cap(existing.getPattern()) + " de " + cap(existing.getMaterial()));
            }
            lore.add(""); lore.add("§aClick para equipar.");
            inv.setItem(slots[i], buildItem(piece.getDisplayMaterial(),
                    piece.getSymbol() + " §f" + piece.getDisplayName(), lore));
        }

        // Discard — slot 49
        inv.setItem(49, buildItem(Material.BARRIER, "§c§lDescartar trim",
                List.of("§7Pierdes este trim sin aplicarlo.")));

        player.openInventory(inv);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private ItemStack buildPieceItem(ArmorPiece piece, Trim current, TrimManager tm) {
        List<String> lore = new ArrayList<>();
        if (current != null) {
            String col = tm.patternColour(current.getPattern());
            String mc  = tm.materialColour(current.getMaterial());
            lore.add("§8Patrón:   " + col + cap(current.getPattern()));
            lore.add("§8Material: " + mc  + cap(current.getMaterial()));
            lore.add("");
            lore.add("§7Click para cambiar.");
            lore.add("§cShift+Click para limpiar.");
        } else {
            lore.add("§8Sin trim configurado.");
            lore.add("");
            lore.add("§7Click para añadir un trim.");
        }
        String title = piece.getSymbol() + " §f§l" + piece.getDisplayName();
        if (current != null) title += " §8(" + cap(current.getPattern()) + ")";
        return buildItem(piece.getDisplayMaterial(), title, lore);
    }

    private void fillBorder(Inventory inv) {
        ItemStack border = buildItem(Material.GRAY_STAINED_GLASS_PANE, "§r", List.of());
        for (int i = 0; i < 9; i++)  inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        for (int i = 0; i < 6; i++) {
            inv.setItem(i * 9,     border);
            inv.setItem(i * 9 + 8, border);
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
