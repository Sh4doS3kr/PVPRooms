package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Manages per-kit queues. When two players are in the same kit queue,
 * the matchmaking runnable picks them up and starts a duel.
 *
 * Queue structure: kitName → LinkedList<UUID>
 */
public class QueueManager {

    private final PvPRoomsPro plugin;

    /** Per-kit queue: kit name (lowercase) → ordered list of waiting player UUIDs */
    private final Map<String, LinkedList<UUID>> queues = new HashMap<>();

    /** Quick reverse lookup: player UUID → kit they queued for */
    private final Map<UUID, String> playerKitMap = new HashMap<>();

    /** Cooldown tracking: player UUID → System.currentTimeMillis() of last queue join */
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    private BukkitTask matchmakingTask;

    public QueueManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    /**
     * Starts the periodic matchmaking runnable.
     * Checks all queues every N ticks (configured via queue.check-interval).
     */
    public void startMatchmaking() {
        int interval = plugin.getConfig().getInt("queue.check-interval", 40);
        matchmakingTask = new BukkitRunnable() {
            @Override
            public void run() {
                processQueues();
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    /** Cancels the matchmaking runnable. */
    public void stopMatchmaking() {
        if (matchmakingTask != null) {
            matchmakingTask.cancel();
            matchmakingTask = null;
        }
    }

    // ── Queue operations ───────────────────────────────────────────────────

    /**
     * Adds a player to the queue for a specific kit.
     *
     * @return false if the player is already in a queue, in a duel, or on cooldown.
     */
    public boolean addToQueue(Player player, String kitName) {
        UUID uuid = player.getUniqueId();

        // Already queued or in duel
        if (playerKitMap.containsKey(uuid)) return false;
        if (plugin.getDuelManager().isInDuel(uuid)) return false;

        // Cooldown check
        long cooldownMs = plugin.getConfig().getLong("cooldowns.queue", 3) * 1000L;
        long lastJoin = cooldowns.getOrDefault(uuid, 0L);
        if (System.currentTimeMillis() - lastJoin < cooldownMs) return false;

        queues.computeIfAbsent(kitName.toLowerCase(), k -> new LinkedList<>()).add(uuid);
        playerKitMap.put(uuid, kitName.toLowerCase());
        cooldowns.put(uuid, System.currentTimeMillis());
        return true;
    }

    /**
     * Removes a player from their current queue.
     *
     * @return true if the player was actually in a queue.
     */
    public boolean removeFromQueue(UUID uuid) {
        String kitName = playerKitMap.remove(uuid);
        if (kitName == null) return false;
        LinkedList<UUID> queue = queues.get(kitName);
        if (queue != null) {
            queue.remove(uuid);
        }
        return true;
    }

    /** Returns the kit name the player is queued for, or null if not queued. */
    public String getQueuedKit(UUID uuid) {
        return playerKitMap.get(uuid);
    }

    public boolean isInQueue(UUID uuid) {
        return playerKitMap.containsKey(uuid);
    }

    /** Total number of players currently in any queue. */
    public int getTotalQueued() {
        return playerKitMap.size();
    }

    /** Number of players queued for a specific kit. */
    public int getQueueSize(String kitName) {
        LinkedList<UUID> q = queues.get(kitName.toLowerCase());
        return q == null ? 0 : q.size();
    }

    // ── Matchmaking ────────────────────────────────────────────────────────

    /**
     * Called on every tick of the matchmaking runnable.
     * For each kit queue with ≥2 players, pairs them up and starts a duel.
     */
    private void processQueues() {
        for (Map.Entry<String, LinkedList<UUID>> entry : queues.entrySet()) {
            String kitName = entry.getKey();
            LinkedList<UUID> queue = entry.getValue();

            // Purge disconnected players
            queue.removeIf(uuid -> plugin.getServer().getPlayer(uuid) == null);
            playerKitMap.entrySet().removeIf(e ->
                    e.getValue().equals(kitName) && plugin.getServer().getPlayer(e.getKey()) == null);

            while (queue.size() >= 2) {
                UUID uuid1 = queue.poll();
                UUID uuid2 = queue.poll();

                // Remove from kit map
                playerKitMap.remove(uuid1);
                playerKitMap.remove(uuid2);

                plugin.getDuelManager().startDuel(uuid1, uuid2, kitName);
            }
        }
    }
}
