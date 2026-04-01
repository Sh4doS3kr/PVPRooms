package com.pvprooms.listeners;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.gui.StaffMenuHolder;
import com.pvprooms.managers.StaffManager;
import com.pvprooms.model.Duel;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * Handles staff mode interactions: hotbar item right-clicks,
 * GUI menu clicks, vanish on join, frozen player movement.
 */
public class StaffListener implements Listener {

    private final PvPRoomsPro plugin;

    public StaffListener(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ── Vanish: hide from players who join after ────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getStaffManager().applyVanishOnJoin(event.getPlayer());
    }

    // ── Staff item right-click ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!plugin.getStaffManager().isInStaffMode(player.getUniqueId())) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!StaffManager.isStaffItem(item)) return;

        event.setCancelled(true);
        String name = item.getItemMeta().getDisplayName();

        if (name.contains("VANISH")) {
            plugin.getStaffManager().toggleVanish(player);

        } else if (name.contains("JUGADORES")) {
            openPlayersMenu(player);

        } else if (name.contains("PARTIDAS ACTIVAS")) {
            openMatchesMenu(player);

        } else if (name.contains("VUELO")) {
            plugin.getStaffManager().toggleFly(player);

        } else if (name.contains("FORZAR FIN")) {
            openForceEndMenu(player);

        } else if (name.contains("CONGELAR")) {
            openFreezeMenu(player);

        } else if (name.contains("INFO JUGADOR")) {
            openPlayerInfoMenu(player);

        } else if (name.contains("ESTADÍSTICAS")) {
            showServerStats(player);

        } else if (name.contains("SALIR STAFF")) {
            plugin.getStaffManager().exitStaffMode(player);
        }
    }

    // ── Prevent staff items from being dropped / moved ─────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        Player p = event.getPlayer();
        if (!plugin.getStaffManager().isInStaffMode(p.getUniqueId())) return;
        if (StaffManager.isStaffItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    // ── Frozen player movement block ────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!plugin.getStaffManager().isFrozen(p.getUniqueId())) return;
        Location from = event.getFrom();
        Location to   = event.getTo();
        if (to == null) return;
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            event.setTo(from.clone().setDirection(to.getDirection()));
            p.sendActionBar(ChatColor.RED + "❄ Estás congelado por un staff");
        }
    }

    @EventHandler
    public void onFrozenDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (plugin.getStaffManager().isFrozen(p.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onFrozenHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (plugin.getStaffManager().isFrozen(p.getUniqueId())) event.setCancelled(true);
    }

    // ── GUI click handler ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player staff)) return;
        if (!(event.getInventory().getHolder() instanceof StaffMenuHolder holder)) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE
                || clicked.getType() == Material.AIR) return;
        if (!clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) return;

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        switch (holder.getType()) {
            case PLAYERS -> {
                // Find matching player by name
                Player target = Bukkit.getPlayerExact(displayName);
                if (target == null) { staff.sendMessage(plugin.prefix() + "§cJugador no encontrado."); return; }
                if (event.isRightClick()) {
                    // Right click → open info
                    staff.closeInventory();
                    showPlayerInfo(staff, target);
                } else {
                    // Left click → teleport
                    staff.closeInventory();
                    staff.teleport(target.getLocation());
                    staff.sendMessage(plugin.prefix() + "§aTeleportado a §f" + target.getName());
                }
            }
            case MATCHES -> {
                // displayName is "MatchId" or player names
                // The lore contains the world name as first line
                List<String> lore = clicked.getItemMeta().getLore();
                if (lore == null || lore.isEmpty()) return;
                String worldName = ChatColor.stripColor(lore.get(0));
                if (event.isRightClick()) {
                    // Right click → force end
                    Duel duel = plugin.getDuelManager().getDuelByWorldName(worldName);
                    if (duel == null) { staff.sendMessage(plugin.prefix() + "§cPartida no encontrada."); return; }
                    staff.closeInventory();
                    plugin.getDuelManager().endDuel(duel, null, "staff_force");
                    staff.sendMessage(plugin.prefix() + "§cPartida finalizada forzadamente.");
                } else {
                    // Left click → teleport to match world
                    World w = Bukkit.getWorld(worldName);
                    if (w == null || w.getPlayers().isEmpty()) { staff.sendMessage(plugin.prefix() + "§cArena no disponible."); return; }
                    staff.closeInventory();
                    staff.teleport(w.getPlayers().get(0).getLocation());
                    staff.sendMessage(plugin.prefix() + "§aTeleportado a la partida.");
                }
            }
            case FREEZE -> {
                Player target = Bukkit.getPlayerExact(displayName);
                if (target == null) { staff.sendMessage(plugin.prefix() + "§cJugador no encontrado."); return; }
                staff.closeInventory();
                plugin.getStaffManager().toggleFreeze(staff, target);
            }
            case INFO -> {
                if (displayName.startsWith("Kick: ")) {
                    String targetName = displayName.substring(6);
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target != null) {
                        staff.closeInventory();
                        target.kickPlayer("§c§lExpulsado por un staff.");
                        staff.sendMessage(plugin.prefix() + "§c" + targetName + " §cha sido expulsado.");
                    }
                } else if (displayName.startsWith("TP a: ")) {
                    String targetName = displayName.substring(6);
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target != null) {
                        staff.closeInventory();
                        staff.teleport(target.getLocation());
                        staff.sendMessage(plugin.prefix() + "§aTeleportado a §f" + targetName);
                    }
                } else if (displayName.startsWith("TP aquí: ")) {
                    String targetName = displayName.substring(9);
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target != null) {
                        staff.closeInventory();
                        target.teleport(staff.getLocation());
                        staff.sendMessage(plugin.prefix() + "§a" + targetName + " §ateleportado a ti.");
                        target.sendMessage(plugin.prefix() + "§7Un staff te ha teleportado.");
                    }
                } else if (displayName.startsWith("Congelar: ") || displayName.startsWith("Descongelar: ")) {
                    String targetName = displayName.contains(": ") ? displayName.substring(displayName.indexOf(": ") + 2) : "";
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target != null) {
                        staff.closeInventory();
                        plugin.getStaffManager().toggleFreeze(staff, target);
                    }
                }
            }
        }
    }

    // ── GUI builders ────────────────────────────────────────────────────────

    private void openPlayersMenu(Player staff) {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        int size = Math.max(9, ((online.size() / 9) + 1) * 9);
        if (size > 54) size = 54;
        Inventory inv = Bukkit.createInventory(new StaffMenuHolder(StaffMenuHolder.Type.PLAYERS),
                size, ChatColor.translateAlternateColorCodes('&', "&8Jugadores Online &7(" + online.size() + ")"));

        int slot = 0;
        for (Player p : online) {
            if (slot >= size) break;
            boolean inDuel  = plugin.getDuelManager().isInDuel(p.getUniqueId());
            boolean inQueue = plugin.getQueueManager().isInQueue(p.getUniqueId());
            boolean isFrozen = plugin.getStaffManager().isFrozen(p.getUniqueId());
            String status = inDuel ? "§cEn duelo" : inQueue ? "§eEn cola" : "§aEn lobby";
            if (isFrozen) status += " §b❄Congelado";

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(p);
            meta.setDisplayName("§f§l" + p.getName());
            meta.setLore(List.of(
                    "§7Estado: " + status,
                    "§7Ping: §e" + p.getPing() + "ms",
                    "§7ELO: §6" + plugin.getEloManager().getElo(p.getUniqueId()),
                    "",
                    "§aClick izq §7para teleportarte",
                    "§eClick der §7para ver info"
            ));
            head.setItemMeta(meta);
            inv.setItem(slot++, head);
        }

        // Fill rest with filler
        ItemStack filler = filler();
        for (int i = slot; i < size; i++) inv.setItem(i, filler);
        staff.openInventory(inv);
    }

    private void openMatchesMenu(Player staff) {
        Collection<Duel> duels = plugin.getDuelManager().getActiveDuels();
        int size = Math.max(9, ((duels.size() / 9) + 1) * 9);
        if (size > 54) size = 54;
        Inventory inv = Bukkit.createInventory(new StaffMenuHolder(StaffMenuHolder.Type.MATCHES),
                size, ChatColor.translateAlternateColorCodes('&', "&8Partidas Activas &7(" + duels.size() + ")"));

        int slot = 0;
        for (Duel duel : duels) {
            if (slot >= size) break;
            Player p1 = Bukkit.getPlayer(duel.getPlayer1());
            Player p2 = Bukkit.getPlayer(duel.getPlayer2());
            String n1 = p1 != null ? p1.getName() : "?";
            String n2 = p2 != null ? p2.getName() : "?";
            String mode = duel.isRanked() ? "§b[TIER]" : "§a[ELO]";
            String state = duel.getState() == Duel.State.FIGHTING ? "§aEn curso" : "§eContando...";

            ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§f§l" + n1 + " §cvs §f§l" + n2);
            meta.setLore(List.of(
                    duel.getInstanceWorldName(),  // used for TP/force-end
                    "§7Kit: §e" + duel.getKitName() + "  " + mode,
                    "§7Estado: " + state,
                    "§7Tiempo: §a" + formatTime(duel.getElapsedSeconds()),
                    "",
                    "§aClick izq §7para teleportarte",
                    "§cClick der §7para finalizar partida"
            ));
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }

        if (duels.isEmpty()) {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta m = none.getItemMeta();
            m.setDisplayName("§cNo hay partidas activas");
            none.setItemMeta(m);
            inv.setItem(4, none);
        }

        ItemStack filler = filler();
        for (int i = slot; i < size; i++) if (inv.getItem(i) == null) inv.setItem(i, filler);
        staff.openInventory(inv);
    }

    private void openForceEndMenu(Player staff) {
        // Reuse matches menu but only shows force-end context
        openMatchesMenu(staff);
        staff.sendMessage(plugin.prefix() + "§7Click §cderecho §7en una partida para finalizarla.");
    }

    private void openFreezeMenu(Player staff) {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        int size = Math.max(9, ((online.size() / 9) + 1) * 9);
        if (size > 54) size = 54;
        Inventory inv = Bukkit.createInventory(new StaffMenuHolder(StaffMenuHolder.Type.FREEZE),
                size, ChatColor.translateAlternateColorCodes('&', "&8Congelar Jugador"));

        int slot = 0;
        for (Player p : online) {
            if (slot >= size || p.equals(staff)) continue;
            boolean frozen = plugin.getStaffManager().isFrozen(p.getUniqueId());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(p);
            meta.setDisplayName("§f§l" + p.getName());
            meta.setLore(List.of(
                    frozen ? "§b❄ Congelado" : "§aLibre",
                    "",
                    "§7Click para " + (frozen ? "§adescongelar" : "§ccongelar")
            ));
            head.setItemMeta(meta);
            inv.setItem(slot++, head);
        }

        ItemStack filler = filler();
        for (int i = 0; i < size; i++) if (inv.getItem(i) == null) inv.setItem(i, filler);
        staff.openInventory(inv);
    }

    private void openPlayerInfoMenu(Player staff) {
        openPlayersMenu(staff); // left click opens info submenu
    }

    private void showPlayerInfo(Player staff, Player target) {
        int size = 27;
        Inventory inv = Bukkit.createInventory(new StaffMenuHolder(StaffMenuHolder.Type.INFO),
                size, ChatColor.translateAlternateColorCodes('&', "&8Info: &f" + target.getName()));

        // Player head
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        skullMeta.setOwningPlayer(target);
        skullMeta.setDisplayName("§f§l" + target.getName());
        boolean inDuel  = plugin.getDuelManager().isInDuel(target.getUniqueId());
        boolean inQueue = plugin.getQueueManager().isInQueue(target.getUniqueId());
        boolean frozen  = plugin.getStaffManager().isFrozen(target.getUniqueId());
        skullMeta.setLore(List.of(
                "§7ELO: §6" + plugin.getEloManager().getElo(target.getUniqueId()),
                "§7Tier: " + plugin.getTierManager().getBestTier(target.getUniqueId()).formatted(),
                "§7Ping: §e" + target.getPing() + "ms",
                "§7Estado: " + (inDuel ? "§cEn duelo" : inQueue ? "§eEn cola" : "§aEn lobby"),
                frozen ? "§b❄ Congelado" : ""
        ));
        head.setItemMeta(skullMeta);
        inv.setItem(4, head);

        // TP to player
        inv.setItem(10, StaffManager.makeItem(Material.COMPASS, "§aTP a: " + target.getName(),
                List.of("§7Click para teleportarte", "§7a " + target.getName())));

        // Summon player
        inv.setItem(12, StaffManager.makeItem(Material.ENDER_PEARL, "§eTP aquí: " + target.getName(),
                List.of("§7Click para traer", "§7a " + target.getName() + " §7a tu posición")));

        // Freeze
        boolean isFrozen = plugin.getStaffManager().isFrozen(target.getUniqueId());
        inv.setItem(14, StaffManager.makeItem(Material.ICE,
                (isFrozen ? "§aDescongelar: " : "§cCongelar: ") + target.getName(),
                List.of("§7Click para " + (isFrozen ? "§adescongelar" : "§ccongelar"))));

        // Kick
        inv.setItem(16, StaffManager.makeItem(Material.BARRIER, "§cKick: " + target.getName(),
                List.of("§7Click para expulsar", "§ca §f" + target.getName())));

        // Fill
        ItemStack filler = filler();
        for (int i = 0; i < size; i++) if (inv.getItem(i) == null) inv.setItem(i, filler);
        staff.openInventory(inv);
    }

    private void showServerStats(Player staff) {
        int online = Bukkit.getOnlinePlayers().size();
        int duels  = plugin.getDuelManager().getActiveDuelCount();
        int queue  = plugin.getQueueManager().getTotalQueued();

        staff.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        staff.sendMessage("§6§l  ◆ §e§lEstadísticas del Servidor");
        staff.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        staff.sendMessage("§a👥 Jugadores online: §f" + online);
        staff.sendMessage("§c⚔ Partidas activas: §f" + duels);
        staff.sendMessage("§e⏳ En cola: §f" + queue);
        staff.sendMessage("§d⏱ TPS: §f" + String.format("%.2f", Bukkit.getServer().getTPS()[0]));
        staff.sendMessage("§b💾 RAM usada: §f" + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024 + "MB §7/ §f" + Runtime.getRuntime().maxMemory() / 1024 / 1024 + "MB");
        staff.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private ItemStack filler() {
        ItemStack g = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = g.getItemMeta();
        m.setDisplayName(" ");
        g.setItemMeta(m);
        return g;
    }

    private String formatTime(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
