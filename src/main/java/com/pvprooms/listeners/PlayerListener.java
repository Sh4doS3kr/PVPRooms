package com.pvprooms.listeners;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Duel;
import com.pvprooms.managers.WallManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import com.pvprooms.model.ArenaTemplate;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Shulker;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import java.util.UUID;

/**
 * Listens for player events that affect duel and queue state.
 *
 * Handles:
 *  - Player disconnect while in queue or duel
 *  - Player death inside a duel world
 *  - Item drop prevention during duels
 *  - Food level management
 *  - Block break/place prevention for spectators
 */
public class PlayerListener implements Listener {

    private final PvPRoomsPro plugin;

    public PlayerListener(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ── Join ───────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                plugin.getScoreboardManager().showLobbyScoreboard(event.getPlayer()), 5L);
    }

    // ── Disconnect ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        handlePlayerExit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        handlePlayerExit(event.getPlayer());
    }

    private void handlePlayerExit(Player player) {
        UUID uuid = player.getUniqueId();

        // Remove from queue
        if (plugin.getQueueManager().isInQueue(uuid)) {
            plugin.getQueueManager().removeFromQueue(uuid);
        }

        // End duel if in one (opponent wins)
        Duel duel = plugin.getDuelManager().getDuelByPlayer(uuid);
        if (duel != null && duel.getState() != Duel.State.ENDED) {
            UUID opponentUUID = duel.getOpponent(uuid);
            if (opponentUUID != null) {
                plugin.getDuelManager().endDuel(duel, opponentUUID, "disconnect");
            } else {
                plugin.getDuelManager().endDuel(duel, null, "disconnect");
            }
        }

        // Remove scoreboard reference
        plugin.getScoreboardManager().clearScoreboard(player);
    }

    // ── Death ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        UUID uuid = dead.getUniqueId();

        Duel duel = plugin.getDuelManager().getDuelByPlayer(uuid);
        if (duel == null || duel.getState() != Duel.State.FIGHTING) return;

        // Suppress default death behaviour
        event.setDeathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);

        // Determine winner
        UUID winnerUUID = duel.getOpponent(uuid);

        // Schedule duel end on next tick (death handling must complete first)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead.isDead()) {
                dead.spigot().respawn();
            }
            plugin.getDuelManager().endDuel(duel, winnerUUID, "death");
        }, 1L);
    }

    // ── Respawn ────────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // If they just died in a duel, respawn at lobby
        // The endDuel method will also teleport them, but just in case:
        Duel duel = plugin.getDuelManager().getDuelByPlayer(player.getUniqueId());
        if (duel == null || duel.getState() == Duel.State.ENDED) {
            event.setRespawnLocation(plugin.getLobbySpawn());
        }
    }

    // ── Item drop prevention ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Duel duel = plugin.getDuelManager().getDuelByPlayer(player.getUniqueId());
        if (duel != null && duel.getState() != Duel.State.ENDED) {
            // Allow drops during fight; some kits involve throwing items
            // Change to event.setCancelled(true) if you want to prevent it
        }
    }

    // ── Food level ─────────────────────────────────────────────────────────

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Duel duel = plugin.getDuelManager().getDuelByPlayer(player.getUniqueId());
        if (duel != null && duel.getState() == Duel.State.COUNTDOWN) {
            // Lock food at 20 during countdown
            event.setCancelled(true);
        }
    }

    // ── Wall setup tool selection ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onWallToolInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("pvprooms.admin")) return;
        if (!plugin.getWallManager().isSetupTool(player.getInventory().getItemInMainHand())) return;

        org.bukkit.block.Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        String wallId = plugin.getWallManager().getToolWallId(player.getInventory().getItemInMainHand());
        if (wallId == null) return;

        org.bukkit.event.block.Action action = event.getAction();
        if (action == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            plugin.getWallManager().setPos1(player.getUniqueId(), clicked.getLocation());
            player.sendMessage(plugin.prefix() + "§7[§e" + wallId + "§7] §ePunto §6A§e: §f"
                    + clicked.getX() + ", " + clicked.getY() + ", " + clicked.getZ());
        } else if (action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            plugin.getWallManager().setPos2(player.getUniqueId(), clicked.getLocation());
            player.sendMessage(plugin.prefix() + "§7[§e" + wallId + "§7] §ePunto §6B§e: §f"
                    + clicked.getX() + ", " + clicked.getY() + ", " + clicked.getZ());
            if (plugin.getWallManager().hasFullSelection(player.getUniqueId())) {
                player.sendMessage(plugin.prefix()
                        + "§a¡Selección completa! Ahora: §f/admin setupwall " + wallId + " <tipo_bloque>");
            }
        }
    }

    // ── Fire protection in lobby ───────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        String lobbyWorld = plugin.getConfig().getString("general.lobby-world", "world");
        if (event.getBlock().getWorld().getName().equals(lobbyWorld)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        String lobbyWorld = plugin.getConfig().getString("general.lobby-world", "world");
        if (event.getBlock().getWorld().getName().equals(lobbyWorld)) {
            event.setCancelled(true);
        }
    }

    // ── Advancement messages ────────────────────────────────────────────────

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        event.message(null);
    }

    // ── Block break / place by spectators ─────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        // Bloquear en el mundo lobby salvo OPs
        String lobbyWorld = plugin.getConfig().getString("general.lobby-world", "world");
        if (event.getBlock().getWorld().getName().equals(lobbyWorld) && !player.isOp()) {
            event.setCancelled(true);
            player.sendMessage(plugin.prefix() + "§cNo puedes romper bloques en el spawn.");
            return;
        }
        // Mundos de instancia de arena (PvP)
        String instancePrefix = plugin.getConfig().getString("arenas.instance-prefix", "pvp_match_");
        String blockWorld = event.getBlock().getWorld().getName();
        if (blockWorld.startsWith(instancePrefix)) {
            Duel duel = plugin.getDuelManager().getDuelByWorldName(blockWorld);
            // Espectadores: nunca pueden romper bloques
            if (duel != null && duel.isSpectator(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            // Comprobar permiso de la arena
            ArenaTemplate template = duel != null ? duel.getArenaTemplate() : null;
            if (template == null || !template.isAllowBlockBreak()) {
                event.setCancelled(true);
            }
            return;
        }
        // Bloquear a espectadores en cualquier arena
        Duel duel = plugin.getDuelManager().getDuelByPlayer(player.getUniqueId());
        if (duel != null && duel.isSpectator(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        // Bloquear en el mundo lobby salvo OPs
        String lobbyWorld = plugin.getConfig().getString("general.lobby-world", "world");
        if (event.getBlock().getWorld().getName().equals(lobbyWorld) && !player.isOp()) {
            event.setCancelled(true);
            player.sendMessage(plugin.prefix() + "§cNo puedes colocar bloques en el spawn.");
            return;
        }
        // Mundos de instancia de arena (PvP)
        String instancePrefix = plugin.getConfig().getString("arenas.instance-prefix", "pvp_match_");
        String blockWorld = event.getBlock().getWorld().getName();
        if (blockWorld.startsWith(instancePrefix)) {
            Duel duel = plugin.getDuelManager().getDuelByWorldName(blockWorld);
            if (duel != null && duel.isSpectator(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            ArenaTemplate template = duel != null ? duel.getArenaTemplate() : null;
            if (template == null || !template.isAllowBlockPlace()) {
                event.setCancelled(true);
            }
            return;
        }
        // Bloquear a espectadores
        Duel duel = plugin.getDuelManager().getDuelByPlayer(player.getUniqueId());
        if (duel != null && duel.isSpectator(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ── Mob spawn prevention (global) ───────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (isMob(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        applyNoMobGamerules(event.getWorld());
    }

    private boolean isMob(org.bukkit.entity.Entity entity) {
        return entity instanceof Monster
                || entity instanceof Slime
                || entity instanceof Ghast
                || entity instanceof Phantom
                || entity instanceof Shulker;
    }

    public static void applyNoMobGamerules(org.bukkit.World world) {
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(org.bukkit.GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(org.bukkit.GameRule.DO_TRADER_SPAWNING, false);
    }

    // ── Explosion control in arenas ──────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.getLocation().getWorld(), event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.getBlock().getWorld(), event.blockList());
    }

    private void handleExplosion(org.bukkit.World world, java.util.List<org.bukkit.block.Block> blockList) {
        if (world == null) return;
        String worldName = world.getName();
        String lobbyWorld = plugin.getConfig().getString("general.lobby-world", "world");
        // Always protect lobby
        if (worldName.equals(lobbyWorld)) { blockList.clear(); return; }
        String instancePrefix = plugin.getConfig().getString("arenas.instance-prefix", "pvp_match_");
        if (!worldName.startsWith(instancePrefix)) return;
        Duel duel = plugin.getDuelManager().getDuelByWorldName(worldName);
        ArenaTemplate template = duel != null ? duel.getArenaTemplate() : null;
        // If explosions not allowed, prevent block damage (entity damage still applies)
        if (template == null || !template.isAllowExplosions()) {
            blockList.clear();
        }
    }

    // ── Teleport-out prevention ────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Block /tp commands that bypass the arena during countdown
        Player player = event.getPlayer();
        Duel duel = plugin.getDuelManager().getDuelByPlayer(player.getUniqueId());
        if (duel != null && duel.getState() == Duel.State.COUNTDOWN) {
            PlayerTeleportEvent.TeleportCause cause = event.getCause();
            if (cause == PlayerTeleportEvent.TeleportCause.COMMAND
                    || cause == PlayerTeleportEvent.TeleportCause.PLUGIN) {
                // Allow only teleports within the same instance world
                if (event.getTo() != null
                        && !event.getTo().getWorld().getName().equals(duel.getInstanceWorldName())) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.prefix() + "§cNo puedes teletransportarte durante la cuenta atrás.");
                }
            }
        }
    }

}
