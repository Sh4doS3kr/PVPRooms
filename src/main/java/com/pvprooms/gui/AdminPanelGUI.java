package com.pvprooms.gui;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * Admin panel GUI — 54 slots (6 rows).
 *
 * Row 0 : Live server stats (read-only info items)
 * Row 1 : ELO management buttons
 * Row 2 : Player management buttons
 * Row 3 : Server tools
 * Row 4 : (separator)
 * Row 5 : Close button
 */
public class AdminPanelGUI {

    // ── Action slot constants ─────────────────────────────────────────────
    public static final int SLOT_ELO_RESET     = 9;
    public static final int SLOT_ELO_SET       = 10;
    public static final int SLOT_ELO_GET       = 11;
    public static final int SLOT_ELO_RESETALL  = 12;
    public static final int SLOT_KICK          = 18;
    public static final int SLOT_FORCEEND      = 19;
    public static final int SLOT_RELOAD        = 27;
    public static final int SLOT_INFO          = 28;
    public static final int SLOT_PRESET_KITS   = 29;
    public static final int SLOT_RESET_ALL     = 36;
    public static final int SLOT_CLOSE         = 49;

    private final PvPRoomsPro plugin;

    public AdminPanelGUI(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        AdminPanelHolder holder = new AdminPanelHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.translateAlternateColorCodes('&', "&4&l⚙ Panel de Administración"));
        holder.setInventory(inv);

        // ── Row 0: Live stats ──────────────────────────────────────────────
        int online       = Bukkit.getOnlinePlayers().size();
        int duels        = plugin.getDuelManager().getActiveDuelCount();
        int queued       = plugin.getQueueManager().getTotalQueued();
        int kits         = plugin.getKitManager().getAllKits().size();
        int arenas       = plugin.getArenaManager().getAllArenas().size();
        int registeredPlayers = plugin.getEloManager().getPlayerCount();

        inv.setItem(0, info(Material.PLAYER_HEAD,  "&a&lJugadores online",
                List.of("&f" + online + " &7conectados")));
        inv.setItem(1, info(Material.DIAMOND_SWORD, "&c&lDuelos activos",
                List.of("&f" + duels + " &7en curso")));
        inv.setItem(2, info(Material.COMPASS,       "&e&lEn cola",
                List.of("&f" + queued + " &7jugadores")));
        inv.setItem(3, info(Material.IRON_CHESTPLATE, "&b&lKits",
                List.of("&f" + kits + " &7kits creados")));
        inv.setItem(4, info(Material.GRASS_BLOCK,  "&2&lArenas",
                List.of("&f" + arenas + " &7arenas registradas")));
        inv.setItem(5, info(Material.BOOK,         "&6&lJugadores registrados",
                List.of("&f" + registeredPlayers + " &7con ELO")));
        for (int i = 6; i <= 8; i++) inv.setItem(i, sep());

        // ── Row 1: ELO management ──────────────────────────────────────────
        inv.setItem(SLOT_ELO_RESET, btn(Material.GOLD_NUGGET, "&e&lELO: Resetear jugador",
                List.of("&7Resetea el ELO de un jugador al valor", "&7por defecto (&e" + plugin.getEloManager().getDefaultElo() + "&7).",
                        "", "&fUso: &e/admin elo reset <jugador>")));
        inv.setItem(SLOT_ELO_SET, btn(Material.GOLD_INGOT, "&e&lELO: Establecer valor",
                List.of("&7Asigna un ELO específico a un jugador.", "", "&fUso: &e/admin elo set <jugador> <valor>")));
        inv.setItem(SLOT_ELO_GET, btn(Material.PAPER, "&e&lELO: Ver jugador",
                List.of("&7Muestra el ELO de un jugador.", "", "&fUso: &e/admin elo get <jugador>")));
        inv.setItem(SLOT_ELO_RESETALL, btn(Material.NETHER_STAR, "&c&l⚠ ELO: Resetear TODOS",
                List.of("&cRESETEA el ELO de TODOS los jugadores", "&cal valor por defecto.", "",
                        "&4¡Esta acción no se puede deshacer!", "", "&fUso: &e/admin elo resetall")));
        for (int i = 13; i <= 17; i++) inv.setItem(i, sep());

        // ── Row 2: Player management ───────────────────────────────────────
        inv.setItem(SLOT_KICK, btn(Material.LEATHER_BOOTS, "&c&lKick de cola/duelo",
                List.of("&7Saca a un jugador de la cola o termina", "&7su duelo actual (el rival gana).", "",
                        "&fUso: &e/admin kick <jugador>")));
        inv.setItem(SLOT_FORCEEND, btn(Material.TNT, "&c&lTerminar duelo forzado",
                List.of("&7Termina el duelo de un jugador en empate.", "", "&fUso: &e/admin forceend <jugador>")));
        for (int i = 20; i <= 26; i++) inv.setItem(i, sep());

        // ── Row 3: Config tools ────────────────────────────────────────────
        inv.setItem(SLOT_RELOAD, btn(Material.COMPARATOR, "&a&lRecargar configuración",
                List.of("&7Recarga config.yml sin reiniciar.", "", "&fUso: &e/admin reload")));
        inv.setItem(SLOT_INFO, btn(Material.WRITABLE_BOOK, "&b&lInfo del plugin",
                List.of("&7Muestra versión y estado del plugin.", "", "&fUso: &e/admin info")));
        inv.setItem(SLOT_PRESET_KITS, btn(Material.CHEST, "&6&l⚔ Instalar Kits Oficiales",
                List.of("&7Instala los kits oficiales de PvP:", "",
                        "&e• Sword &8- Classic 1.9+",
                        "&e• AxePvP &8- Vanilla Axe combat", 
                        "&e• Nethpot &8- Netherite + Potions",
                        "&e• UHC &8- Ultra Hardcore",
                        "&e• SMP &8- Survival full gear",
                        "&e• Crystal &8- Crystal PvP",
                        "&e• Mace &8- 1.21 Mace combat", "",
                        "&aClick para instalar todos los kits")));
        for (int i = 30; i <= 35; i++) inv.setItem(i, sep());

        // ── Row 4: Danger zone ───────────────────────────────────────────────
        inv.setItem(SLOT_RESET_ALL, btn(Material.BARRIER, "&4&l☠ RESETEAR TODO",
                List.of("&c¡PELIGRO! Esto borrará TODOS los datos:", "",
                        "&7• ELO de todos los jugadores",
                        "&7• Tiers de todos los jugadores", 
                        "&7• Puntos de todos los kits",
                        "&7• Estadísticas y rankings", "",
                        "&4¡ESTA ACCIÓN NO SE PUEDE DESHACER!", "",
                        "&fUso: &e/admin resetall confirm")));
        for (int i = 37; i <= 44; i++) inv.setItem(i, sep());

        // ── Row 5: Close ───────────────────────────────────────────────────
        for (int i = 45; i <= 53; i++) inv.setItem(i, sep());
        inv.setItem(SLOT_CLOSE, btn(Material.RED_WOOL, "&c&lCerrar panel", List.of("&7Cierra este menú.")));

        player.openInventory(inv);
    }

    // ── Item builders ─────────────────────────────────────────────────────

    private ItemStack info(Material mat, String name, List<String> lore) {
        return build(mat, name, lore, true);
    }

    private ItemStack btn(Material mat, String name, List<String> lore) {
        return build(mat, name, lore, false);
    }

    private ItemStack build(Material mat, String name, List<String> lore, boolean glow) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(lore.stream()
                .map(l -> ChatColor.translateAlternateColorCodes('&', l))
                .collect(java.util.stream.Collectors.toList()));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack sep() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta();
        if (m != null) { m.setDisplayName("§8"); pane.setItemMeta(m); }
        return pane;
    }
}
