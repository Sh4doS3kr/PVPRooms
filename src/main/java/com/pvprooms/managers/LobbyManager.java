package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Manages lobby items and hotbar setup for players in spawn.
 */
public class LobbyManager {

    private final PvPRoomsPro plugin;

    // Item identifiers stored in persistent data
    public static final String LOBBY_ITEM_KEY = "pvprooms_lobby_item";
    public static final int CMI_CREEPER_LAUNCHER = 1006;
    public static final int CMI_GOLDEN_HEAD      = 2001;
    public static final int MAX_CREEPERS_PER_DUEL = 128;

    private final java.util.Map<java.util.UUID, Integer> creeperLaunchCount = new java.util.HashMap<>();

    public LobbyManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    public int getCreeperCount(java.util.UUID uuid) {
        return creeperLaunchCount.getOrDefault(uuid, 0);
    }

    public void incrementCreeperCount(java.util.UUID uuid) {
        creeperLaunchCount.merge(uuid, 1, Integer::sum);
    }

    public void resetCreeperCount(java.util.UUID uuid) {
        creeperLaunchCount.remove(uuid);
    }

    /**
     * Give lobby items to a player.
     */
    public void giveLobbyItems(Player player) {
        creeperLaunchCount.remove(player.getUniqueId());
        player.getInventory().clear();

        // Slot 0: Queue (Diamond Sword)
        player.getInventory().setItem(0, createQueueItem());

        // Slot 1: Quick Match (Golden Sword)
        player.getInventory().setItem(1, createQuickMatchItem());

        // Slot 4: Party Manager (Cake)
        player.getInventory().setItem(4, createPartyItem());

        // Slot 7: Profile (Player Head)
        player.getInventory().setItem(7, createProfileItem(player));

        // Slot 8: Settings (Redstone)
        player.getInventory().setItem(8, createSettingsItem());

        player.getInventory().setHeldItemSlot(2);
    }

    /**
     * Clear lobby items from player.
     */
    public void clearLobbyItems(Player player) {
        player.getInventory().clear();
    }

    /**
     * Check if player is in lobby world.
     */
    public boolean isInLobby(Player player) {
        String lobbyWorld = plugin.getConfig().getString("general.lobby-world", "world");
        return player.getWorld().getName().equals(lobbyWorld);
    }

    // ── Item Creators ──────────────────────────────────────────────────────

    private ItemStack createQueueItem() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Unirse a Cola", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text("Click derecho para buscar partida", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Elige tu kit y encuentra rival", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.setCustomModelData(1001);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createQuickMatchItem() {
        ItemStack item = new ItemStack(Material.GOLDEN_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Partida Rápida", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text("Click derecho para partida instantánea", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Te une con el primero en cola", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.setCustomModelData(1002);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPartyItem() {
        ItemStack item = new ItemStack(Material.CAKE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Gestión de Party", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text("Click derecho para gestionar tu party", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Crea, invita y gestiona tu grupo", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.setCustomModelData(1003);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createProfileItem(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(Component.text("Tu Perfil", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text("Click derecho para ver tu perfil", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Stats, tier, ELO y más", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.setCustomModelData(1004);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createCreeperLauncherItem() {
        ItemStack item = new ItemStack(Material.CREEPER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Lanzador de Creepers", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text("Click derecho para lanzar un creeper", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Cooldown: 5 segundos", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.setCustomModelData(CMI_CREEPER_LAUNCHER);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createGoldenHeadItem(int amount) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Golden Head", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text("Click derecho para consumir", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Regen II 5s + Absorción I 30s", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.setCustomModelData(CMI_GOLDEN_HEAD);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSettingsItem() {
        ItemStack item = new ItemStack(Material.REDSTONE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Ajustes", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text("Click derecho para ajustes", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Personaliza tu experiencia", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.setCustomModelData(1005);
        item.setItemMeta(meta);
        return item;
    }

    // ── Item identification ────────────────────────────────────────────────

    public boolean isQueueItem(ItemStack item) {
        return item != null && item.getType() == Material.DIAMOND_SWORD 
                && item.hasItemMeta() && item.getItemMeta().hasCustomModelData()
                && item.getItemMeta().getCustomModelData() == 1001;
    }

    public boolean isQuickMatchItem(ItemStack item) {
        return item != null && item.getType() == Material.GOLDEN_SWORD
                && item.hasItemMeta() && item.getItemMeta().hasCustomModelData()
                && item.getItemMeta().getCustomModelData() == 1002;
    }

    public boolean isPartyItem(ItemStack item) {
        return item != null && item.getType() == Material.CAKE
                && item.hasItemMeta() && item.getItemMeta().hasCustomModelData()
                && item.getItemMeta().getCustomModelData() == 1003;
    }

    public boolean isProfileItem(ItemStack item) {
        return item != null && item.getType() == Material.PLAYER_HEAD
                && item.hasItemMeta() && item.getItemMeta().hasCustomModelData()
                && item.getItemMeta().getCustomModelData() == 1004;
    }

    public boolean isSettingsItem(ItemStack item) {
        return item != null && item.getType() == Material.REDSTONE
                && item.hasItemMeta() && item.getItemMeta().hasCustomModelData()
                && item.getItemMeta().getCustomModelData() == 1005;
    }

    public boolean isCreeperLauncherItem(ItemStack item) {
        return item != null && item.getType() == Material.CREEPER_HEAD
                && item.hasItemMeta() && item.getItemMeta().hasCustomModelData()
                && item.getItemMeta().getCustomModelData() == CMI_CREEPER_LAUNCHER;
    }

    public boolean isGoldenHeadItem(ItemStack item) {
        return item != null && item.getType() == Material.PLAYER_HEAD
                && item.hasItemMeta() && item.getItemMeta().hasCustomModelData()
                && item.getItemMeta().getCustomModelData() == CMI_GOLDEN_HEAD;
    }

    public boolean isLobbyItem(ItemStack item) {
        return isQueueItem(item) || isQuickMatchItem(item) || isPartyItem(item)
                || isProfileItem(item) || isSettingsItem(item) || isCreeperLauncherItem(item);
    }
}
