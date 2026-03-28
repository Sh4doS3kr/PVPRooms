package com.pvprooms.listeners;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Duel;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;

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

    // ── Block break / place by spectators ─────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Duel duel = plugin.getDuelManager().getDuelByPlayer(player.getUniqueId());
        if (duel != null && duel.isSpectator(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Duel duel = plugin.getDuelManager().getDuelByPlayer(player.getUniqueId());
        if (duel != null && duel.isSpectator(player.getUniqueId())) {
            event.setCancelled(true);
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
