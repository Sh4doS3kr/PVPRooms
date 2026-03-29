package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Tier;
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

    /** Per-kit ELO queue: kit name (lowercase) → ordered list of waiting player UUIDs */
    private final Map<String, LinkedList<UUID>> queues = new HashMap<>();

    /** Tier queue: "TIER:kitName" → ordered list of waiting player UUIDs */
    private final Map<String, LinkedList<UUID>> tierQueues = new HashMap<>();

    /** Quick reverse lookup: player UUID → queue key (kit for ELO, "TIER:kit" for tier) */
    private final Map<UUID, String> playerKitMap = new HashMap<>();

    /** Whether each queued player is in TIER mode */
    private final Map<UUID, Boolean> playerTierMode = new HashMap<>();

    /** Join timestamp for tier-queue expansion after timeout */
    private final Map<UUID, Long> tierJoinTimes = new HashMap<>();

    /** Cooldown tracking: player UUID → System.currentTimeMillis() of last queue join */
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    /** Pending duel pairs from /duel command: challenger UUID → target UUID */
    private final Map<UUID, UUID> pendingDuelPairs = new HashMap<>();

    private static final long TIER_EXPAND_MS = 45_000L; // expand to ±1 tier after 45s

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

    /** Adds a player to the ELO queue for a specific kit. */
    public boolean addToQueue(Player player, String kitName) {
        UUID uuid = player.getUniqueId();
        if (playerKitMap.containsKey(uuid)) return false;
        if (plugin.getDuelManager().isInDuel(uuid)) return false;

        long cooldownMs = plugin.getConfig().getLong("cooldowns.queue", 3) * 1000L;
        if (System.currentTimeMillis() - cooldowns.getOrDefault(uuid, 0L) < cooldownMs) return false;

        queues.computeIfAbsent(kitName.toLowerCase(), k -> new LinkedList<>()).add(uuid);
        playerKitMap.put(uuid, kitName.toLowerCase());
        playerTierMode.put(uuid, false);
        cooldowns.put(uuid, System.currentTimeMillis());
        return true;
    }

    /** Adds a player to the TIER queue for a specific kit. */
    public boolean addToTierQueue(Player player, String kitName) {
        UUID uuid = player.getUniqueId();
        if (playerKitMap.containsKey(uuid)) return false;
        if (plugin.getDuelManager().isInDuel(uuid)) return false;

        long cooldownMs = plugin.getConfig().getLong("cooldowns.queue", 3) * 1000L;
        if (System.currentTimeMillis() - cooldowns.getOrDefault(uuid, 0L) < cooldownMs) return false;

        // Use per-kit tier from TierManager (independent of ELO)
        Tier tier = plugin.getTierManager().getTier(uuid, kitName);
        // Unranked players start matching at LT5 level
        if (tier == Tier.UNRANKED) tier = Tier.LT5;
        String key = tierKey(tier, kitName);

        tierQueues.computeIfAbsent(key, k -> new LinkedList<>()).add(uuid);
        playerKitMap.put(uuid, key);
        playerTierMode.put(uuid, true);
        tierJoinTimes.put(uuid, System.currentTimeMillis());
        cooldowns.put(uuid, System.currentTimeMillis());
        return true;
    }

    /**
     * Removes a player from their current queue.
     *
     * @return true if the player was actually in a queue.
     */
    public boolean removeFromQueue(UUID uuid) {
        String key = playerKitMap.remove(uuid);
        if (key == null) return false;
        boolean wasTier = Boolean.TRUE.equals(playerTierMode.remove(uuid));
        tierJoinTimes.remove(uuid);
        LinkedList<UUID> queue = wasTier ? tierQueues.get(key) : queues.get(key);
        if (queue != null) queue.remove(uuid);
        return true;
    }

    /** Returns a human-readable queue descriptor for display (kit name or TIER:kit). */
    public String getQueuedKit(UUID uuid) {
        String key = playerKitMap.get(uuid);
        if (key == null) return null;
        return key.startsWith("TIER:") ? key.replace("TIER:", "") + " §7[TIER]" : key;
    }

    public boolean isInTierQueue(UUID uuid) {
        return Boolean.TRUE.equals(playerTierMode.get(uuid));
    }

    public boolean isInQueue(UUID uuid) {
        return playerKitMap.containsKey(uuid);
    }

    /** Total number of players currently in any queue. */
    public int getTotalQueued() {
        return playerKitMap.size();
    }

    /** Number of players in ELO queue for a specific kit. */
    public int getQueueSize(String kitName) {
        LinkedList<UUID> q = queues.get(kitName.toLowerCase());
        return q == null ? 0 : q.size();
    }

    /** Number of players in TIER queue for a specific kit (all tiers combined). */
    public int getTierQueueSize(String kitName) {
        int total = 0;
        String suffix = ":" + kitName.toLowerCase();
        for (Map.Entry<String, LinkedList<UUID>> e : tierQueues.entrySet()) {
            if (e.getKey().endsWith(suffix)) total += e.getValue().size();
        }
        return total;
    }

    // ── Matchmaking ────────────────────────────────────────────────────────

    /**
     * Called on every tick of the matchmaking runnable.
     * For each kit queue with ≥2 players, pairs them up and starts a duel.
     */
    private void processQueues() {
        // ── ELO queues (unchanged behaviour) ─────────────────────────────────
        for (Map.Entry<String, LinkedList<UUID>> entry : queues.entrySet()) {
            String kitName = entry.getKey();
            LinkedList<UUID> queue = entry.getValue();

            queue.removeIf(uuid -> plugin.getServer().getPlayer(uuid) == null);
            playerKitMap.entrySet().removeIf(e ->
                    e.getValue().equals(kitName) && plugin.getServer().getPlayer(e.getKey()) == null);

            while (queue.size() >= 2) {
                UUID uuid1 = queue.poll();
                UUID uuid2 = queue.poll();
                playerKitMap.remove(uuid1);
                playerKitMap.remove(uuid2);
                playerTierMode.remove(uuid1);
                playerTierMode.remove(uuid2);
                plugin.getDuelManager().startDuel(uuid1, uuid2, kitName);
            }
        }

        // ── TIER queues ───────────────────────────────────────────────
        long now = System.currentTimeMillis();
        for (Map.Entry<String, LinkedList<UUID>> entry : tierQueues.entrySet()) {
            String key = entry.getKey();               // e.g. "TIER:BRONCE:sword"
            LinkedList<UUID> queue = entry.getValue();

            queue.removeIf(uuid -> {
                if (plugin.getServer().getPlayer(uuid) == null) {
                    playerKitMap.remove(uuid);
                    playerTierMode.remove(uuid);
                    tierJoinTimes.remove(uuid);
                    return true;
                }
                return false;
            });

            // Exact-tier match
            while (queue.size() >= 2) {
                UUID uuid1 = queue.poll();
                UUID uuid2 = queue.poll();
                playerKitMap.remove(uuid1);
                playerKitMap.remove(uuid2);
                playerTierMode.remove(uuid1);
                playerTierMode.remove(uuid2);
                tierJoinTimes.remove(uuid1);
                tierJoinTimes.remove(uuid2);
                String kitName = kitFromTierKey(key);
                plugin.getDuelManager().startDuel(uuid1, uuid2, kitName, true);
            }

            // After TIER_EXPAND_MS seconds, allow ±1 tier matching
            if (queue.size() == 1) {
                UUID waiter = queue.peek();
                Long joinTime = tierJoinTimes.get(waiter);
                if (joinTime != null && now - joinTime >= TIER_EXPAND_MS) {
                    // Try to find a partner in adjacent tier queues
                    Tier myTier = tierFromKey(key);
                    String kitName = kitFromTierKey(key);
                    UUID partner = findAdjacentTierPartner(myTier, kitName, waiter);
                    if (partner != null) {
                        queue.poll();
                        playerKitMap.remove(waiter);
                        playerKitMap.remove(partner);
                        playerTierMode.remove(waiter);
                        playerTierMode.remove(partner);
                        tierJoinTimes.remove(waiter);
                        tierJoinTimes.remove(partner);
                        plugin.getDuelManager().startDuel(waiter, partner, kitName, true);
                    }
                }
            }
        }
    }

    private UUID findAdjacentTierPartner(Tier myTier, String kitName, UUID exclude) {
        for (Tier t : Tier.values()) {
            if (t == myTier || !myTier.isAdjacent(t)) continue;
            LinkedList<UUID> adj = tierQueues.get(tierKey(t, kitName));
            if (adj != null && !adj.isEmpty()) {
                return adj.peek();
            }
        }
        return null;
    }

    /** Removes a specific UUID from an adjacent tier queue (after being paired). */
    private void removeFromTierQueue(UUID uuid, String kitName) {
        for (Tier t : Tier.values()) {
            LinkedList<UUID> q = tierQueues.get(tierKey(t, kitName));
            if (q != null && q.remove(uuid)) return;
        }
    }

    // ── Key helpers ───────────────────────────────────────────────

    private String tierKey(Tier tier, String kitName) {
        return "TIER:" + tier.name() + ":" + kitName.toLowerCase();
    }

    private String kitFromTierKey(String key) {
        // key = "TIER:BRONCE:sword"
        String[] parts = key.split(":", 3);
        return parts.length == 3 ? parts[2] : key;
    }

    private Tier tierFromKey(String key) {
        String[] parts = key.split(":", 3);
        if (parts.length >= 2) {
            try { return Tier.valueOf(parts[1]); } catch (IllegalArgumentException ignored) {}
        }
        return Tier.UNRANKED;
    }

    // ── Duel pairs (from /duel command) ────────────────────────────────────

    /** Stores a pending duel pair. Called when a duel request is accepted. */
    public void storeDuelPair(UUID challenger, UUID target) {
        pendingDuelPairs.put(challenger, target);
    }

    /** Gets and removes the pending duel target for a challenger. */
    public UUID consumeDuelPair(UUID challenger) {
        return pendingDuelPairs.remove(challenger);
    }

    /** Checks if a player has a pending duel pair. */
    public boolean hasDuelPair(UUID challenger) {
        return pendingDuelPairs.containsKey(challenger);
    }

    /** Starts a duel from a pending pair with the selected kit. */
    public void startDuelFromPair(UUID challenger, String kitName) {
        UUID target = consumeDuelPair(challenger);
        if (target != null) {
            plugin.getDuelManager().startDuel(challenger, target, kitName);
        }
    }

}
