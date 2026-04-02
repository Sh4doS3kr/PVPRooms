package com.pvprooms.gui;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.bot.BotDifficulty;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI for selecting bot practice difficulty and kit.
 */
public class BotPracticeGUI implements Listener {

    private final PvPRoomsPro plugin;
    private static final String TITLE_KIT = "§8⚔ §6Práctica vs Bot §8- §eElige Kit";
    private static final String TITLE_DIFF = "§8⚔ §6Práctica vs Bot §8- §eDificultad";
    
    private final Map<UUID, String> selectedKit = new HashMap<>();

    public BotPracticeGUI(PvPRoomsPro plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Opens the kit selection menu.
     * Only shows kits that have a connected arena configured.
     */
    public void open(Player player) {
        if (!plugin.getBotManager().isCitizensEnabled()) {
            player.sendMessage(plugin.prefix() + "§cEl plugin Citizens no está instalado.");
            player.sendMessage(plugin.prefix() + "§7Instala Citizens para practicar vs bots.");
            return;
        }

        // Only show kits that have a connected arena
        List<String> allKits = plugin.getKitManager().getKitNames();
        List<String> connectedKits = allKits.stream()
                .filter(kit -> {
                    String arena = plugin.getKitManager().getConnectedArena(kit);
                    if (arena == null || arena.trim().isEmpty()) return false;
                    var template = plugin.getArenaManager().getArena(arena.trim());
                    return template != null && template.isFullyConfigured();
                })
                .toList();

        if (connectedKits.isEmpty()) {
            player.sendMessage(plugin.prefix() + "§cNo hay kits con arena configurada para practicar.");
            player.sendMessage(plugin.prefix() + "§7Pide a un admin que conecte kits a arenas.");
            return;
        }

        int size = Math.min(54, ((connectedKits.size() / 9) + 1) * 9);
        if (size < 9) size = 9;

        Inventory inv = Bukkit.createInventory(null, size, Component.text(TITLE_KIT));

        for (int i = 0; i < connectedKits.size() && i < 54; i++) {
            String kitName = connectedKits.get(i);
            inv.setItem(i, createKitItem(kitName));
        }

        player.openInventory(inv);
    }

    /**
     * Opens the difficulty selection menu.
     */
    public void openDifficultyMenu(Player player, String kitName) {
        selectedKit.put(player.getUniqueId(), kitName);
        
        Inventory inv = Bukkit.createInventory(null, 36, Component.text(TITLE_DIFF));

        // Difficulty items
        inv.setItem(10, createDifficultyItem(BotDifficulty.EASY, Material.LIME_WOOL));
        inv.setItem(12, createDifficultyItem(BotDifficulty.MEDIUM, Material.YELLOW_WOOL));
        inv.setItem(14, createDifficultyItem(BotDifficulty.HARD, Material.RED_WOOL));
        inv.setItem(16, createDifficultyItem(BotDifficulty.HACKER, Material.BLACK_WOOL));
        
        // Adaptive AI mode (special)
        inv.setItem(22, createAdaptiveItem(player));

        // Back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("« Volver", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        inv.setItem(31, back);

        player.openInventory(inv);
    }
    
    private ItemStack createAdaptiveItem(Player player) {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        
        meta.displayName(Component.text("✦ ADAPTIVO", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        
        // Check if player has enough learning data
        boolean hasData = plugin.getBotManager().getAdaptiveAI().hasEnoughData(player.getUniqueId());
        
        if (hasData) {
            meta.lore(List.of(
                    Component.empty(),
                    Component.text("» El bot aprende de TI", NamedTextColor.LIGHT_PURPLE)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("» Imita tu estilo de combate", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("» Se adapta mientras juegas", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("✓ Datos de aprendizaje listos", NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Click para empezar", NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false)
            ));
        } else {
            meta.lore(List.of(
                    Component.empty(),
                    Component.text("» El bot aprende de TI", NamedTextColor.LIGHT_PURPLE)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("» Imita tu estilo de combate", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("» Se adapta mientras juegas", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("⚠ Pocos datos - Juega más para", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("  que el bot aprenda mejor", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Click para empezar", NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false)
            ));
        }
        
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createKitItem(String kitName) {
        // Pick material based on kit name
        Material material = switch (kitName.toLowerCase()) {
            case "sword"       -> Material.DIAMOND_SWORD;
            case "axe"         -> Material.DIAMOND_AXE;
            case "mace"        -> Material.MACE;
            case "nethpot", "pot" -> Material.SPLASH_POTION;
            case "uhc"         -> Material.GOLDEN_APPLE;
            case "smp"         -> Material.SHIELD;
            case "crystal"     -> Material.END_CRYSTAL;
            case "spear", "trident" -> Material.TRIDENT;
            case "explosivo"   -> Material.TNT;
            case "bow"         -> Material.BOW;
            case "crossbow"    -> Material.CROSSBOW;
            default            -> Material.DIAMOND_SWORD;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(kitName, NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text("Click para practicar", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("con este kit vs Bot", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDifficultyItem(BotDifficulty difficulty, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        Component name = Component.text(difficulty.displayName.replace("§", ""))
                .decoration(TextDecoration.ITALIC, false);
        
        // Parse color from displayName
        NamedTextColor color = switch (difficulty) {
            case EASY -> NamedTextColor.GREEN;
            case MEDIUM -> NamedTextColor.YELLOW;
            case HARD -> NamedTextColor.RED;
            case HACKER -> NamedTextColor.DARK_RED;
            case ADAPTIVE -> NamedTextColor.LIGHT_PURPLE;
        };
        
        meta.displayName(Component.text(difficulty.name(), color)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, difficulty == BotDifficulty.HACKER));
        
        String reactionDesc = switch (difficulty) {
            case EASY -> "Reacción lenta, falla mucho";
            case MEDIUM -> "Reacción normal, precisión media";
            case HARD -> "Reacción rápida, muy preciso";
            case HACKER -> "Reacción muy rápida, muy preciso";
            case ADAPTIVE -> "Se adapta a tu estilo";
        };

        String healDesc = switch (difficulty) {
            case EASY -> "Cura al " + difficulty.healThreshold + "% HP";
            case MEDIUM -> "Cura al " + difficulty.healThreshold + "% HP";
            case HARD -> "Cura al " + difficulty.healThreshold + "% HP";
            case HACKER -> "Cura al " + difficulty.healThreshold + "% HP";
            case ADAPTIVE -> "Aprende cuándo curas tú";
        };

        meta.lore(List.of(
                Component.empty(),
                Component.text("» " + reactionDesc, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("» " + healDesc, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Click para empezar", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        String title = event.getView().title().toString();
        if (!title.contains("Práctica vs Bot")) return;
        
        event.setCancelled(true);
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Kit selection menu
        if (title.contains("Elige Kit")) {
            String kitName = getKitNameFromItem(clicked);
            if (kitName != null) {
                openDifficultyMenu(player, kitName);
            }
            return;
        }

        // Difficulty selection menu
        if (title.contains("Dificultad")) {
            // Back button
            if (clicked.getType() == Material.ARROW) {
                open(player);
                return;
            }

            BotDifficulty difficulty = getDifficultyFromItem(clicked);
            if (difficulty != null) {
                String kit = selectedKit.remove(player.getUniqueId());
                if (kit != null) {
                    player.closeInventory();
                    plugin.getBotManager().startBotDuel(player, kit, difficulty);
                }
            }
        }
    }

    private String getKitNameFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta.displayName() == null) return null;
        
        // Extract kit name from display name
        String displayName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(meta.displayName());
        
        // Verify it's a valid kit
        if (plugin.getKitManager().getKitNames().contains(displayName)) {
            return displayName;
        }
        return null;
    }

    private BotDifficulty getDifficultyFromItem(ItemStack item) {
        if (item == null) return null;
        
        return switch (item.getType()) {
            case LIME_WOOL -> BotDifficulty.EASY;
            case YELLOW_WOOL -> BotDifficulty.MEDIUM;
            case RED_WOOL -> BotDifficulty.HARD;
            case BLACK_WOOL -> BotDifficulty.HACKER;
            case ENDER_EYE -> BotDifficulty.ADAPTIVE;
            default -> null;
        };
    }
}
