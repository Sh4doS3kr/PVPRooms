package com.pvprooms.gui;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Tier;
import com.pvprooms.model.TierTitle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI for viewing player profile and stats.
 */
public class ProfileGUI implements Listener {

    private final PvPRoomsPro plugin;
    private static final String GUI_TITLE = "★ Tu Perfil";

    public ProfileGUI(PvPRoomsPro plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(GUI_TITLE, NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.BOLD, true));

        UUID uuid = player.getUniqueId();

        // Fill background
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, glass);
        }

        // Player head with basic info (slot 4)
        inv.setItem(4, createPlayerHead(player));

        // Stats row 1 (slots 19-25)
        inv.setItem(19, createEloItem(uuid));
        inv.setItem(20, createRankItem(uuid));
        inv.setItem(21, createTierItem(uuid));
        inv.setItem(22, createTitleItem(uuid));
        inv.setItem(23, createPointsItem(uuid));
        inv.setItem(24, createWinsItem(uuid));
        inv.setItem(25, createKDRItem(uuid));

        // Stats row 2 (slots 28-34)
        inv.setItem(28, createLossesItem(uuid));
        inv.setItem(29, createKillsItem(uuid));
        inv.setItem(30, createDeathsItem(uuid));
        inv.setItem(31, createStreakItem(uuid));
        inv.setItem(32, createBestStreakItem(uuid));
        inv.setItem(33, createWinrateItem(uuid));
        inv.setItem(34, createGamesItem(uuid));
        
        // Accuracy stat (slot 40 - center of row 3)
        inv.setItem(40, createAccuracyItem(uuid));

        // Kit tiers row (slots 37-43)
        inv.setItem(37, createKitTiersItem(uuid));
        inv.setItem(43, createEliteTiersInfoItem());

        // Decorative items
        inv.setItem(10, createItem(Material.PURPLE_STAINED_GLASS_PANE, " "));
        inv.setItem(16, createItem(Material.PURPLE_STAINED_GLASS_PANE, " "));
        inv.setItem(46, createItem(Material.PURPLE_STAINED_GLASS_PANE, " "));
        inv.setItem(52, createItem(Material.PURPLE_STAINED_GLASS_PANE, " "));

        // Close button (slot 49)
        inv.setItem(49, createCloseItem());

        player.openInventory(inv);
    }

    // ── Item Creators ──────────────────────────────────────────────────────

    private ItemStack createPlayerHead(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);

        UUID uuid = player.getUniqueId();
        Tier tier = plugin.getTierManager().getBestTier(uuid);
        TierTitle title = plugin.getTierManager().getTitle(uuid);
        int elo = plugin.getEloManager().getElo(uuid);

        meta.displayName(Component.text(player.getName(), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Tier: ", NamedTextColor.GRAY)
                .append(Component.text(tier.displayName, tierColor(tier)))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Título: ", NamedTextColor.GRAY)
                .append(Component.text(title.symbol + " " + title.name, titleColor(title)))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("ELO: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(elo), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEloItem(UUID uuid) {
        int elo = plugin.getEloManager().getElo(uuid);
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("ELO", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text("" + elo, NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Tu puntuación de ranking", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRankItem(UUID uuid) {
        int rank = plugin.getEloManager().getRank(uuid);
        String rankStr = rank > 0 ? "#" + rank : "Sin ranking";
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Ranking", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text(rankStr, NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Tu posición en el leaderboard", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTierItem(UUID uuid) {
        Tier tier = plugin.getTierManager().getBestTier(uuid);
        ItemStack item = new ItemStack(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Mejor Tier", tierColor(tier))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text(tier.displayName, tierColor(tier))
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Tu tier más alto en cualquier kit", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTitleItem(UUID uuid) {
        TierTitle title = plugin.getTierManager().getTitle(uuid);
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Título", titleColor(title))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text(title.symbol + " " + title.name, titleColor(title))
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Tu título basado en puntos", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPointsItem(UUID uuid) {
        int points = plugin.getTierManager().getTotalPoints(uuid);
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Puntos Totales", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text(String.valueOf(points), NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Suma de puntos en todos los kits", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createWinsItem(UUID uuid) {
        var stats = plugin.getStatsManager().getStats(uuid);
        ItemStack item = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Victorias", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text(String.valueOf(stats.wins()), NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLossesItem(UUID uuid) {
        var stats = plugin.getStatsManager().getStats(uuid);
        ItemStack item = new ItemStack(Material.RED_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Derrotas", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text(String.valueOf(stats.losses()), NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createKillsItem(UUID uuid) {
        var stats = plugin.getStatsManager().getStats(uuid);
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Kills", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text(String.valueOf(stats.kills()), NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDeathsItem(UUID uuid) {
        var stats = plugin.getStatsManager().getStats(uuid);
        ItemStack item = new ItemStack(Material.SKELETON_SKULL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Muertes", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text(String.valueOf(stats.deaths()), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createKDRItem(UUID uuid) {
        var stats = plugin.getStatsManager().getStats(uuid);
        ItemStack item = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("K/D Ratio", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        if (stats.hasKDR()) {
            meta.lore(List.of(
                    Component.empty(),
                    Component.text(String.format("%.2f", stats.getKDR()), NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false)
            ));
        } else {
            int gamesLeft = com.pvprooms.managers.StatsManager.MIN_GAMES_FOR_KDR - (stats.wins() + stats.losses());
            meta.lore(List.of(
                    Component.empty(),
                    Component.text("\u2014", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Juega " + gamesLeft + " partida" + (gamesLeft == 1 ? "" : "s") + " más", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createStreakItem(UUID uuid) {
        var stats = plugin.getStatsManager().getStats(uuid);
        ItemStack item = new ItemStack(Material.FIRE_CHARGE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Racha Actual", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text(String.valueOf(stats.currentStreak()), NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBestStreakItem(UUID uuid) {
        var stats = plugin.getStatsManager().getStats(uuid);
        ItemStack item = new ItemStack(Material.MAGMA_CREAM);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Mejor Racha", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text(String.valueOf(stats.bestStreak()), NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createWinrateItem(UUID uuid) {
        var stats = plugin.getStatsManager().getStats(uuid);
        int total = stats.wins() + stats.losses();
        double wr = total > 0 ? (stats.wins() * 100.0 / total) : 0;
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Winrate", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text(String.format("%.1f%%", wr), NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGamesItem(UUID uuid) {
        var stats = plugin.getStatsManager().getStats(uuid);
        int games = stats.wins() + stats.losses();
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Partidas Jugadas", NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text(String.valueOf(games), NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createAccuracyItem(UUID uuid) {
        var stats = plugin.getStatsManager().getStats(uuid);
        double accuracy = stats.getAccuracy();
        ItemStack item = new ItemStack(Material.TARGET);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Precisión", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text(String.format("%.1f%%", accuracy), NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Golpes: " + stats.hits(), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Intentos: " + stats.swings(), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createKitTiersItem(UUID uuid) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Tiers por Kit", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        Map<String, Integer> kitPoints = plugin.getTierManager().getKitPoints(uuid);
        if (kitPoints.isEmpty()) {
            lore.add(Component.text("Sin datos de kits", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            for (Map.Entry<String, Integer> entry : kitPoints.entrySet()) {
                String kitName = entry.getKey();
                int points = entry.getValue();
                Tier tier = Tier.fromPoints(points);
                lore.add(Component.text("• " + capitalize(kitName) + ": ", NamedTextColor.GRAY)
                        .append(Component.text(tier.displayName, tierColor(tier)))
                        .append(Component.text(" (" + points + " pts)", NamedTextColor.DARK_GRAY))
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
        lore.add(Component.empty());

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEliteTiersInfoItem() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("🏆 Tiers de Élite (Verificación)", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Requieren cumplir requisitos extra y verificación:", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        
        // Tier badges
        lore.add(Component.text(" HT3 ", NamedTextColor.GOLD)
                .append(Component.text(" LT2 ", NamedTextColor.RED))
                .append(Component.text(" HT2 ", NamedTextColor.DARK_RED))
                .append(Component.text(" LT1 ", NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(" HT1 ", NamedTextColor.DARK_PURPLE))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        
        // Process explanation
        lore.add(Component.text("📋 Proceso de verificación:", NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("1. Abre ticket en ", NamedTextColor.WHITE)
                .append(Component.text("tiers.mlmc.lat/tickets.html", NamedTextColor.AQUA)
                        .decoration(TextDecoration.UNDERLINED, true))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("2. Se te asignará un Tester de tu nivel", NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("3. Debes GRABAR el test completo", NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("4. El Tester evalúa tu nivel y decide tu tier", NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        
        // Requirements
        lore.add(Component.text("✓ Requisitos adicionales:", NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("• Mínimo ", NamedTextColor.GRAY)
                .append(Component.text("5 victorias", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" contra jugadores HT3+", NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("• Mantener el tier durante ", NamedTextColor.GRAY)
                .append(Component.text("7 días", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" consecutivos", NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("• Sin abandonos en las últimas ", NamedTextColor.GRAY)
                .append(Component.text("20 partidas", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("• Revisión anti-boost por el sistema", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("⚡ ¡Los mejores jugadores del servidor!", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, true));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✖ Cerrar", NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private NamedTextColor tierColor(Tier tier) {
        return switch (tier) {
            case LT5 -> NamedTextColor.BLUE;
            case HT5 -> NamedTextColor.AQUA;
            case LT4 -> NamedTextColor.GREEN;
            case HT4 -> NamedTextColor.DARK_GREEN;
            case LT3 -> NamedTextColor.YELLOW;
            case HT3 -> NamedTextColor.GOLD;
            case LT2 -> NamedTextColor.RED;
            case HT2 -> NamedTextColor.DARK_RED;
            case LT1 -> NamedTextColor.LIGHT_PURPLE;
            case HT1 -> NamedTextColor.DARK_PURPLE;
            default -> NamedTextColor.GRAY;
        };
    }

    private NamedTextColor titleColor(TierTitle title) {
        if (title.colour == null) return NamedTextColor.GRAY;
        return switch (title.colour) {
            case "§7" -> NamedTextColor.GRAY;
            case "§a" -> NamedTextColor.GREEN;
            case "§b" -> NamedTextColor.AQUA;
            case "§e" -> NamedTextColor.YELLOW;
            case "§6" -> NamedTextColor.GOLD;
            case "§c" -> NamedTextColor.RED;
            case "§d" -> NamedTextColor.LIGHT_PURPLE;
            case "§5" -> NamedTextColor.DARK_PURPLE;
            default -> NamedTextColor.WHITE;
        };
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    // ── Event Handler ──────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().title().toString();
        if (!title.contains(GUI_TITLE)) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType() == Material.BARRIER) {
            player.closeInventory();
        }
    }
}
