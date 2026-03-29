package com.pvprooms.gui;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.managers.TrimManager;
import com.pvprooms.model.ArmorPiece;
import com.pvprooms.model.Trim;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        Inventory inv = Bukkit.createInventory(holder, 54, "§5✦ §d§lARMOR TRIMS §5✦");
        holder.setInventory(inv);

        // Premium background
        fillPremiumBackground(inv);

        // Title decoration - top center
        inv.setItem(4, buildGlowItem(Material.ARMOR_STAND, "§d§l⚔ TUS TRIMS §d§l⚔",
                List.of("§7Personaliza tu armadura con",
                        "§7patrones y materiales únicos.",
                        "",
                        "§8▸ Click en una pieza para editar")));

        // Player head - slot 13 (center)
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        skullMeta.setOwningPlayer(player);
        skullMeta.setDisplayName("§e§l" + player.getName());
        List<String> headLore = new ArrayList<>();
        headLore.add("§8━━━━━━━━━━━━━━━━━━");
        int configured = (int) trims.values().stream().filter(t -> t != null).count();
        headLore.add("§7Trims configurados: §a" + configured + "§7/§f4");
        headLore.add("§8━━━━━━━━━━━━━━━━━━");
        skullMeta.setLore(headLore);
        head.setItemMeta(skullMeta);
        inv.setItem(13, head);

        // Armor mannequin layout (vertical)
        // Helmet - slot 10 (left of head)
        // Chestplate - slot 22 (below head)
        // Leggings - slot 31 (below chestplate)
        // Boots - slot 40 (below leggings)
        
        // Or horizontal centered layout:
        // Row 2: [deco] [HELMET] [head] [CHEST] [deco]
        // Row 3: [deco] [LEGS] [info] [BOOTS] [deco]
        
        // Let's do a nice centered layout
        inv.setItem(11, buildPieceItemPremium(ArmorPiece.HELMET, trims.get(ArmorPiece.HELMET), tm));
        inv.setItem(15, buildPieceItemPremium(ArmorPiece.CHESTPLATE, trims.get(ArmorPiece.CHESTPLATE), tm));
        inv.setItem(29, buildPieceItemPremium(ArmorPiece.LEGGINGS, trims.get(ArmorPiece.LEGGINGS), tm));
        inv.setItem(33, buildPieceItemPremium(ArmorPiece.BOOTS, trims.get(ArmorPiece.BOOTS), tm));

        // Decorative connectors
        inv.setItem(12, buildDecor(Material.PURPLE_STAINED_GLASS_PANE, "§d§m   "));
        inv.setItem(14, buildDecor(Material.PURPLE_STAINED_GLASS_PANE, "§d§m   "));
        inv.setItem(20, buildDecor(Material.MAGENTA_STAINED_GLASS_PANE, "§5↓"));
        inv.setItem(24, buildDecor(Material.MAGENTA_STAINED_GLASS_PANE, "§5↓"));
        inv.setItem(30, buildDecor(Material.PURPLE_STAINED_GLASS_PANE, "§d§m   "));
        inv.setItem(32, buildDecor(Material.PURPLE_STAINED_GLASS_PANE, "§d§m   "));

        // Info panel - center (slot 22, 31)
        inv.setItem(22, buildGlowItem(Material.NETHER_STAR, "§b§lINFORMACIÓN",
                List.of("§7Los trims son decoraciones",
                        "§7visuales para tu armadura.",
                        "",
                        "§d▸ §fPatrones: §7Diseño del trim",
                        "§6▸ §fMateriales: §7Color del trim",
                        "",
                        "§8Obtén trims de §5Crates§8!")));

        inv.setItem(31, buildItem(Material.SMITHING_TABLE, "§e§lCÓMO FUNCIONA",
                List.of("§71. §fClick §7en una pieza de armadura",
                        "§72. §fElige §7un patrón",
                        "§73. §fElige §7un material",
                        "§74. §f¡Listo! §7Tu trim se aplicará")));

        // Quick actions - bottom row
        inv.setItem(47, buildGlowItem(Material.EXPERIENCE_BOTTLE, "§a§lPATRONES DESBLOQUEADOS",
                List.of("§7Tienes acceso a:",
                        "§f• §7" + tm.getPatternKeys().size() + " patrones normales",
                        "§5• §d" + tm.getLegendaryPatternKeys().size() + " patrones legendarios",
                        "",
                        "§8Desbloquea más en §5Crates§8!")));

        inv.setItem(49, buildItem(Material.BARRIER, "§c§lLIMPIAR TODO",
                List.of("§7Elimina todos tus trims.",
                        "",
                        "§c⚠ §7Esta acción no se puede deshacer.",
                        "",
                        "§8▸ Click para limpiar")));

        inv.setItem(51, buildGlowItem(Material.CHEST, "§6§lABRIR CRATE",
                List.of("§7Abre un Trim Crate para obtener",
                        "§7patrones y materiales aleatorios.",
                        "",
                        "§8▸ Click para ver tus crates")));

        // Close button
        inv.setItem(45, buildItem(Material.DARK_OAK_DOOR, "§c✖ §7Cerrar", List.of("§8▸ Click para salir")));

        player.openInventory(inv);
    }

    public void openCrateSelection(Player player) {
        TrimGUIHolder holder = new TrimGUIHolder(player.getUniqueId(),
                TrimGUIHolder.Page.MAIN, null, null, null);
        Inventory inv = Bukkit.createInventory(holder, 27, "§6✦ §e§lSELECCIONA PIEZA §6✦");
        holder.setInventory(inv);

        fillPremiumBackground(inv);

        inv.setItem(4, buildGlowItem(Material.TRIPWIRE_HOOK, "§e§l⚡ ABRIR CRATE §e§l⚡",
                List.of("§7Selecciona la pieza de armadura",
                        "§7para la que quieres obtener un trim.",
                        "",
                        "§8▸ Click en una pieza")));

        // Armor pieces in center row
        inv.setItem(10, buildCratePieceItem(Material.DIAMOND_HELMET, "HELMET", "Casco"));
        inv.setItem(12, buildCratePieceItem(Material.DIAMOND_CHESTPLATE, "CHESTPLATE", "Pechera"));
        inv.setItem(14, buildCratePieceItem(Material.DIAMOND_LEGGINGS, "LEGGINGS", "Pantalones"));
        inv.setItem(16, buildCratePieceItem(Material.DIAMOND_BOOTS, "BOOTS", "Botas"));

        inv.setItem(22, buildItem(Material.DARK_OAK_DOOR, "§c✖ §7Cerrar", List.of("§8▸ Click para salir")));

        player.openInventory(inv);
    }

    private ItemStack buildCratePieceItem(Material mat, String pieceKey, String displayName) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e§l" + displayName.toUpperCase());
        meta.setLore(List.of(
                "§7Obtén un trim aleatorio para",
                "§7tu §f" + displayName + "§7.",
                "",
                "§8▸ Click para abrir crate"
        ));
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "crate_piece"),
                org.bukkit.persistence.PersistentDataType.STRING,
                pieceKey
        );
        item.setItemMeta(meta);
        return item;
    }

    public void openPickPattern(Player player, String pieceKey) {
        TrimManager tm = plugin.getTrimManager();
        ArmorPiece piece = ArmorPiece.fromName(pieceKey);
        Set<String> unlockedPatterns = tm.getUnlockedTrims(player.getUniqueId(), piece);
        
        TrimGUIHolder holder = new TrimGUIHolder(player.getUniqueId(),
                TrimGUIHolder.Page.PICK_PATTERN, pieceKey, null, null);
        Inventory inv = Bukkit.createInventory(holder, 54,
                "§5✦ §d§lELIGE PATRÓN §5✦ §7" + cap(pieceKey));
        holder.setInventory(inv);

        fillPremiumBackground(inv);

        // Title
        inv.setItem(4, buildGlowItem(Material.PAPER, "§d§lPATRONES DESBLOQUEADOS",
                List.of("§7Selecciona un patrón para tu",
                        "§f" + cap(pieceKey) + "§7.",
                        "",
                        "§a✦ §fDesbloqueados: " + unlockedPatterns.size(),
                        "§7Usa crates para obtener más")));

        List<String> patterns = tm.getPatternKeys();
        int[] contentSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int idx = 0;
        for (String pattern : patterns) {
            if (idx >= contentSlots.length) break;
            
            boolean isUnlocked = unlockedPatterns.contains(pattern);
            boolean legendary = tm.getLegendaryPatternKeys().contains(pattern);
            String colour = tm.patternColour(pattern);
            Material icon = PATTERN_ICONS.getOrDefault(pattern, Material.PAPER);
            
            List<String> lore = new ArrayList<>();
            lore.add("§8━━━━━━━━━━━━━━━━━━");
            lore.add("§7Patrón: " + colour + "§l" + cap(pattern));
            
            if (isUnlocked) {
                lore.add("");
                lore.add("§a✦ §7DESBLOQUEADO");
                lore.add("§7▸ Click para seleccionar");
            } else {
                lore.add("");
                lore.add("§c✦ §7BLOQUEADO");
                lore.add("§7▸ Usa crates para desbloquear");
            }
            
            if (legendary) {
                lore.add("");
                lore.add("§5★ §d§lLEGENDARIO §5★");
            }
            
            lore.add("§8━━━━━━━━━━━━━━━━━━");
            lore.add("");
            
            ItemStack item = isUnlocked 
                ? buildGlowItem(icon, colour + "§l" + cap(pattern), lore)
                : buildItem(Material.BARRIER, "§c" + cap(pattern), List.of("§7Bloqueado", "§7▸ Usa crates para desbloquear"));
            
            inv.setItem(contentSlots[idx], item);
            idx++;
        }

        // Back button
        inv.setItem(45, buildItem(Material.ARROW, "§c← §7Volver", List.of("§8▸ Volver al menú principal")));
        
        // Clear this piece button
        inv.setItem(49, buildItem(Material.BARRIER, "§c§lQUITAR TRIM",
                List.of("§7Quita el trim de tu §f" + cap(pieceKey) + "§7.",
                        "",
                        "§8▸ Click para quitar")));

        player.openInventory(inv);
    }

    public void openPickMaterial(Player player, String pieceKey, String patternKey) {
        TrimManager tm = plugin.getTrimManager();
        TrimGUIHolder holder = new TrimGUIHolder(player.getUniqueId(),
                TrimGUIHolder.Page.PICK_MATERIAL, pieceKey, patternKey, null);
        Inventory inv = Bukkit.createInventory(holder, 54,
                "§5✦ §6§lELIGE MATERIAL §5✦");
        holder.setInventory(inv);

        fillPremiumBackground(inv);

        // Title with pattern info
        String patternCol = tm.patternColour(patternKey);
        inv.setItem(4, buildGlowItem(PATTERN_ICONS.getOrDefault(patternKey, Material.PAPER), 
                "§6§lMATERIALES DISPONIBLES",
                List.of("§7Patrón: " + patternCol + "§l" + cap(patternKey),
                        "§7Pieza: §f" + cap(pieceKey),
                        "",
                        "§7Selecciona un material para",
                        "§7darle color a tu trim.")));

        List<String> materials = tm.getMaterialKeys();
        int[] contentSlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int idx = 0;
        for (String mat : materials) {
            if (idx >= contentSlots.length) break;
            String colour = tm.materialColour(mat);
            Material icon = MATERIAL_ICONS.getOrDefault(mat, Material.IRON_INGOT);
            
            List<String> lore = new ArrayList<>();
            lore.add("§8━━━━━━━━━━━━━━━━━━");
            lore.add("§7Material: " + colour + "§l" + cap(mat));
            lore.add("§7Patrón: " + patternCol + cap(patternKey));
            lore.add("§7Pieza: §f" + cap(pieceKey));
            lore.add("§8━━━━━━━━━━━━━━━━━━");
            lore.add("");
            lore.add("§a▸ Click para aplicar");
            
            inv.setItem(contentSlots[idx], buildGlowItem(icon, colour + "§l" + cap(mat), lore));
            idx++;
        }

        // Preview indicator
        inv.setItem(13, buildItem(Material.SPYGLASS, "§e§lPREVISUALIZACIÓN",
                List.of("§7Tu trim se verá así:",
                        "",
                        "§7Patrón: " + patternCol + cap(patternKey),
                        "§7+ Material: §fSelecciona abajo")));

        // Back button
        inv.setItem(45, buildItem(Material.ARROW, "§c← §7Volver", List.of("§8▸ Volver a patrones")));

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
        boolean legendary = tm.getLegendaryPatternKeys().contains(trim.getPattern());

        TrimGUIHolder holder = new TrimGUIHolder(player.getUniqueId(),
                TrimGUIHolder.Page.REWARD, null, null, trim.toString());
        Inventory inv = Bukkit.createInventory(holder, 54, 
                legendary ? "§5✦ §d§l¡LEGENDARIO! §5✦" : "§6✦ §e§lRECOMPENSA §6✦");
        holder.setInventory(inv);

        fillPremiumBackground(inv);

        // Celebration decorations
        inv.setItem(3, buildDecor(Material.GOLD_NUGGET, "§6✦"));
        inv.setItem(5, buildDecor(Material.GOLD_NUGGET, "§6✦"));
        if (legendary) {
            inv.setItem(2, buildDecor(Material.AMETHYST_SHARD, "§5★"));
            inv.setItem(6, buildDecor(Material.AMETHYST_SHARD, "§5★"));
        }

        // Trim info — center top (slot 13)
        Material patternIcon = PATTERN_ICONS.getOrDefault(trim.getPattern(), Material.PAPER);
        List<String> trimLore = new ArrayList<>();
        trimLore.add("§8━━━━━━━━━━━━━━━━━━━━━━");
        trimLore.add("");
        trimLore.add("§7Patrón:   " + colour + "§l" + cap(trim.getPattern()));
        trimLore.add("§7Material: " + matCol + "§l" + cap(trim.getMaterial()));
        trimLore.add("");
        if (legendary) {
            trimLore.add("§5★ §d§lPATRÓN LEGENDARIO §5★");
            trimLore.add("");
        }
        trimLore.add("§8━━━━━━━━━━━━━━━━━━━━━━");
        trimLore.add("");
        trimLore.add("§e▸ Elige una pieza de armadura abajo");
        
        inv.setItem(13, buildGlowItem(patternIcon,
                colour + "§l" + cap(trim.getPattern()) + " §fde §r" + matCol + "§l" + cap(trim.getMaterial()),
                trimLore));

        // Armor piece choices — row 3 (slots 20, 21, 23, 24 - centered)
        ArmorPiece[] pieces = ArmorPiece.values();
        int[] slots = {20, 21, 23, 24};
        Map<ArmorPiece, Trim> current = tm.getPlayerTrims(player.getUniqueId());
        for (int i = 0; i < pieces.length; i++) {
            ArmorPiece piece = pieces[i];
            Trim existing = current.get(piece);
            List<String> lore = new ArrayList<>();
            lore.add("§8━━━━━━━━━━━━━━━━━━");
            lore.add("§7Equipar en: §f§l" + piece.getDisplayName());
            lore.add("");
            if (existing != null) {
                lore.add("§c⚠ §7Reemplazará:");
                lore.add("§8  " + cap(existing.getPattern()) + " de " + cap(existing.getMaterial()));
                lore.add("");
            }
            lore.add("§a▸ Click para equipar");
            inv.setItem(slots[i], buildGlowItem(piece.getDisplayMaterial(),
                    piece.getSymbol() + " §f§l" + piece.getDisplayName(), lore));
        }

        // Center decoration
        inv.setItem(22, buildDecor(Material.END_ROD, "§7↑"));

        // Info text
        inv.setItem(31, buildItem(Material.BOOK, "§e§lINFO",
                List.of("§7Selecciona la pieza de armadura",
                        "§7donde quieres aplicar este trim.",
                        "",
                        "§8El trim se aplicará automáticamente",
                        "§8cuando uses un kit con esa pieza.")));

        // Discard — slot 49
        inv.setItem(49, buildItem(Material.BARRIER, "§c§lDESCARTAR",
                List.of("§7Pierdes este trim sin aplicarlo.",
                        "",
                        "§c⚠ §7Esta acción no se puede deshacer.",
                        "",
                        "§8▸ Click para descartar")));

        player.openInventory(inv);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private ItemStack buildPieceItemPremium(ArmorPiece piece, Trim current, TrimManager tm) {
        List<String> lore = new ArrayList<>();
        lore.add("§8━━━━━━━━━━━━━━━━━━");
        
        if (current != null) {
            String col = tm.patternColour(current.getPattern());
            String mc  = tm.materialColour(current.getMaterial());
            lore.add("");
            lore.add("§7Patrón:   " + col + "§l" + cap(current.getPattern()));
            lore.add("§7Material: " + mc + "§l" + cap(current.getMaterial()));
            lore.add("");
            lore.add("§8━━━━━━━━━━━━━━━━━━");
            lore.add("");
            lore.add("§a▸ Click §7para cambiar");
            lore.add("§c▸ Shift+Click §7para limpiar");
        } else {
            lore.add("");
            lore.add("§8Sin trim configurado");
            lore.add("");
            lore.add("§8━━━━━━━━━━━━━━━━━━");
            lore.add("");
            lore.add("§a▸ Click §7para añadir trim");
        }
        
        String title = piece.getSymbol() + " §f§l" + piece.getDisplayName();
        if (current != null) {
            String col = tm.patternColour(current.getPattern());
            title = piece.getSymbol() + " " + col + "§l" + piece.getDisplayName();
        }
        
        return current != null 
            ? buildGlowItem(piece.getDisplayMaterial(), title, lore)
            : buildItem(piece.getDisplayMaterial(), title, lore);
    }

    private void fillPremiumBackground(Inventory inv) {
        // Gradient border with purple theme
        ItemStack corner = buildDecor(Material.BLACK_STAINED_GLASS_PANE, "§r");
        ItemStack edge1 = buildDecor(Material.PURPLE_STAINED_GLASS_PANE, "§r");
        ItemStack edge2 = buildDecor(Material.MAGENTA_STAINED_GLASS_PANE, "§r");
        ItemStack inner = buildDecor(Material.GRAY_STAINED_GLASS_PANE, "§r");
        
        // Top row - gradient
        inv.setItem(0, corner);
        inv.setItem(1, edge1);
        inv.setItem(2, edge2);
        inv.setItem(3, edge1);
        inv.setItem(4, null); // Title slot
        inv.setItem(5, edge1);
        inv.setItem(6, edge2);
        inv.setItem(7, edge1);
        inv.setItem(8, corner);
        
        // Bottom row - gradient
        inv.setItem(45, corner);
        inv.setItem(46, edge1);
        inv.setItem(47, null); // Action slot
        inv.setItem(48, edge2);
        inv.setItem(49, null); // Action slot
        inv.setItem(50, edge2);
        inv.setItem(51, null); // Action slot
        inv.setItem(52, edge1);
        inv.setItem(53, corner);
        
        // Side borders
        for (int row = 1; row < 5; row++) {
            inv.setItem(row * 9, edge1);
            inv.setItem(row * 9 + 8, edge1);
        }
        
        // Fill remaining empty slots with subtle background
        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, inner);
            }
        }
    }

    private ItemStack buildDecor(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildGlowItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    ItemStack buildItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
