package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.ArenaTemplate;
import com.pvprooms.model.Duel;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central duel lifecycle manager.
 *
 * Responsibilities:
 *  - Creating and ending duels
 *  - Countdown logic
 *  - Saving and restoring player inventories
 *  - ELO updates
 *  - Scoreboard updates
 *  - Spectator management
 */
public class DuelManager {

    private final PvPRoomsPro plugin;

    /** Active duels by duel UUID */
    private final Map<UUID, Duel> activeDuels = new ConcurrentHashMap<>();

    /** Quick lookup: player UUID → duel UUID */
    private final Map<UUID, UUID> playerDuelMap = new ConcurrentHashMap<>();

    /** Saved player inventories before a duel starts: player UUID → snapshot */
    private final Map<UUID, PlayerSnapshot> inventorySnapshots = new ConcurrentHashMap<>();

    /** Players frozen during countdown (only arenas without walls) */
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();
    
    /** Tracks ELO wins for trim key rewards (every 2 wins = 1 key) */
    private final Map<UUID, Integer> eloWinsForKey = new ConcurrentHashMap<>();

    // ── Ping equalization & combat action bar ────────────────────────────
    private final Map<UUID, Integer> duelSwings = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> duelHits   = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> actionBarTasks = new ConcurrentHashMap<>();
    private final Set<UUID> bypassPingDelay = ConcurrentHashMap.newKeySet();

    public DuelManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ── Precision tracking (per-duel, resets on duel end) ────────────────
    public void recordDuelSwing(UUID uuid) { duelSwings.merge(uuid, 1, Integer::sum); }
    public void recordDuelHit(UUID uuid)   { duelHits.merge(uuid, 1, Integer::sum); }
    public int  getDuelSwings(UUID uuid)   { return duelSwings.getOrDefault(uuid, 0); }
    public int  getDuelHits(UUID uuid)     { return duelHits.getOrDefault(uuid, 0); }

    // ── Ping equalization bypass (prevents re-processing delayed damage) ─
    public void    addBypassPingDelay(UUID uuid)     { bypassPingDelay.add(uuid); }
    public boolean consumeBypassPingDelay(UUID uuid) { return bypassPingDelay.remove(uuid); }

    // ── Combat action bar ────────────────────────────────────────────────
    private void startActionBarTask(Duel duel) {
        if (actionBarTasks.containsKey(duel.getId())) return;
        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (duel.getState() != Duel.State.FIGHTING) return;
            Player p1 = Bukkit.getPlayer(duel.getPlayer1());
            Player p2 = Bukkit.getPlayer(duel.getPlayer2());
            if (p1 == null || p2 == null) return;
            sendCombatActionBar(p1, p2, duel.getPlayer1());
            sendCombatActionBar(p2, p1, duel.getPlayer2());
        }, 10L, 10L).getTaskId();
        actionBarTasks.put(duel.getId(), taskId);
    }

    private void sendCombatActionBar(Player player, Player opponent, UUID playerUUID) {
        int myPing = player.getPing();
        int opPing = opponent.getPing();
        int diff = myPing - opPing;

        int swings = getDuelSwings(playerUUID);
        int hits   = getDuelHits(playerUUID);
        String precision = swings > 0 ? Math.round((float) hits / swings * 100) + "%" : "---";

        String diffColor = diff > 0 ? "§c" : "§a";
        String diffSign  = diff > 0 ? "+" : "";

        String msg = "§eDiferencia de ping: " + diffColor + diffSign + diff + "ms"
                + " §8| §bPrecisión: §f" + precision;
        player.sendActionBar(net.kyori.adventure.text.Component.text(msg));
    }

    private void stopActionBarTask(Duel duel) {
        Integer taskId = actionBarTasks.remove(duel.getId());
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
        duelSwings.remove(duel.getPlayer1());
        duelSwings.remove(duel.getPlayer2());
        duelHits.remove(duel.getPlayer1());
        duelHits.remove(duel.getPlayer2());
    }

    // ── Duel creation ──────────────────────────────────────────────────────

    /**
     * Initiates a duel between two players using a given kit.
     * Called by QueueManager once two players are matched.
     */
    /** Overload without bo3 — keeps ELO duels unchanged. */
    public void startDuel(UUID uuid1, UUID uuid2, String kitName) {
        startDuel(uuid1, uuid2, kitName, false, 1);
    }

    public void startDuel(UUID uuid1, UUID uuid2, String kitName, boolean bo3) {
        startDuel(uuid1, uuid2, kitName, bo3, bo3 ? 4 : 1);
    }

    public void startDuel(UUID uuid1, UUID uuid2, String kitName, boolean bo3, int customWinsNeeded) {
        Player p1 = Bukkit.getPlayer(uuid1);
        Player p2 = Bukkit.getPlayer(uuid2);

        if (p1 == null || p2 == null) return;

        // Usar arena vinculada al kit, o una aleatoria si no hay vinculación
        String connectedArena = plugin.getKitManager().getConnectedArena(kitName);
        if (connectedArena != null) connectedArena = connectedArena.trim();
        ArenaTemplate template = null;
        if (connectedArena != null && !connectedArena.isEmpty()) {
            template = plugin.getArenaManager().getArena(connectedArena);
            if (template == null || !template.isFullyConfigured()) {
                p1.sendMessage(plugin.prefix() + "§e⚠ Arena vinculada al kit no disponible. Usando arena aleatoria...");
                p2.sendMessage(plugin.prefix() + "§e⚠ Arena vinculada al kit no disponible. Usando arena aleatoria...");
                template = null;
            }
        }
        if (template == null) template = plugin.getArenaManager().getRandomArena();
        if (template == null) {
            p1.sendMessage(plugin.prefix() + "§cNo hay arenas disponibles. Pide a un admin que configure una.");
            p2.sendMessage(plugin.prefix() + "§cNo hay arenas disponibles. Pide a un admin que configure una.");
            return;
        }

        // Try to borrow a pre-created pool world (fast, no HDD copy needed)
        World instanceWorld = plugin.getWorldPoolManager().borrowWorld(template);
        String instanceWorldName;

        if (instanceWorld != null) {
            instanceWorldName = instanceWorld.getName();
        } else {
            // Pool empty — fall back to on-demand copy
            String matchId = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            instanceWorldName = plugin.getConfig().getString("arenas.instance-prefix", "pvp_match_") + matchId;
            instanceWorld = plugin.getArenaInstanceManager().createInstance(template, matchId);
            if (instanceWorld == null) {
                p1.sendMessage(plugin.prefix() + "§cError al crear la instancia de arena. Contacta a un admin.");
                p2.sendMessage(plugin.prefix() + "§cError al crear la instancia de arena. Contacta a un admin.");
                return;
            }
        }

        Duel duel = new Duel(uuid1, uuid2, kitName, instanceWorldName, template);
        duel.setRanked(bo3);
        duel.setWinsNeeded(customWinsNeeded);
        activeDuels.put(duel.getId(), duel);
        playerDuelMap.put(uuid1, duel.getId());
        playerDuelMap.put(uuid2, duel.getId());

        // Save inventories before teleport
        saveSnapshot(p1);
        saveSnapshot(p2);

        // Prepare players
        preparePlayer(p1);
        preparePlayer(p2);

        // Notify match found & clear queue scoreboard before teleport
        String modeTag = bo3 ? " §8[§bBO7§8]" : (customWinsNeeded > 1 ? " §8[§eBest of " + customWinsNeeded + "§8]" : "");
        p1.sendMessage(plugin.prefix() + "§a¡Partida encontrada! §8› §evs §f" + p2.getName() + " §8[Kit: §e" + kitName + "§8]" + modeTag);
        p2.sendMessage(plugin.prefix() + "§a¡Partida encontrada! §8› §evs §f" + p1.getName() + " §8[Kit: §e" + kitName + "§8]" + modeTag);
        plugin.getScoreboardManager().clearScoreboard(p1);
        plugin.getScoreboardManager().clearScoreboard(p2);

        // Teleport async — chunk loading happens off the main thread, eliminating the
        // ~4000ms spike caused by synchronous chunk I/O during cross-world teleport.
        final Player fp1 = p1, fp2 = p2;
        final World iw = instanceWorld;
        final String kit = kitName;
        CompletableFuture.allOf(
                fp1.teleportAsync(template.getSpawn1(iw)),
                fp2.teleportAsync(template.getSpawn2(iw))
        ).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            // Guard: if the duel ended while teleport was in-flight, don't apply kit
            if (duel.getState() == Duel.State.ENDED) return;
            Player cp1 = Bukkit.getPlayer(fp1.getUniqueId());
            Player cp2 = Bukkit.getPlayer(fp2.getUniqueId());
            if (cp1 == null || cp2 == null) {
                endDuel(duel, cp1 != null ? duel.getPlayer1() : (cp2 != null ? duel.getPlayer2() : null), "disconnect");
                return;
            }
            plugin.getKitManager().applyKit(cp1, kit);
            plugin.getKitManager().applyKit(cp2, kit);
            startCountdown(duel, iw);
        }));
    }

    // ── Countdown ─────────────────────────────────────────────────────────

    private void startCountdown(Duel duel, World world) {
        int seconds = plugin.getConfig().getInt("duels.countdown", 5);
        boolean noWalls = !plugin.getWallManager().hasWalls(duel.getArenaTemplate().getName());

        // Freeze players during countdown only when there are no walls to contain them
        if (noWalls) {
            frozenPlayers.add(duel.getPlayer1());
            frozenPlayers.add(duel.getPlayer2());
        }

        int taskId = new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                Player p1 = Bukkit.getPlayer(duel.getPlayer1());
                Player p2 = Bukkit.getPlayer(duel.getPlayer2());

                if (p1 == null || p2 == null) {
                    frozenPlayers.remove(duel.getPlayer1());
                    frozenPlayers.remove(duel.getPlayer2());
                    cancel();
                    endDuel(duel, p1 != null ? duel.getPlayer1() : duel.getPlayer2(), "desconexión");
                    return;
                }

                if (remaining > 0) {
                    sendTitle(p1, "§e§l" + remaining, "§7¡Prepárate!");
                    sendTitle(p2, "§e§l" + remaining, "§7¡Prepárate!");
                    p1.sendMessage(plugin.prefix() + "§eLa partida empieza en §6" + remaining + "§e...");
                    p2.sendMessage(plugin.prefix() + "§eLa partida empieza en §6" + remaining + "§e...");

                    // Tick sonido: pitch crece conforme baja la cuenta (0.8 → 1.8)
                    float pitch = 0.8f + (1.0f / seconds) * (seconds - remaining);
                    p1.playSound(p1.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);
                    p2.playSound(p2.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);

                    remaining--;
                } else {
                    // Unfreeze before fight starts
                    frozenPlayers.remove(duel.getPlayer1());
                    frozenPlayers.remove(duel.getPlayer2());

                    duel.setState(Duel.State.FIGHTING);
                    duel.setStartTimeMillis(System.currentTimeMillis());

                    // Crystal kit: initialize attack speed based on held item at fight start
                    if ("crystal".equalsIgnoreCase(duel.getKitName())) {
                        for (Player cp : new Player[]{p1, p2}) {
                            var spd = cp.getAttribute(org.bukkit.attribute.Attribute.ATTACK_SPEED);
                            if (spd != null) {
                                org.bukkit.inventory.ItemStack held = cp.getInventory().getItemInMainHand();
                                spd.setBaseValue(held.getType() == org.bukkit.Material.END_CRYSTAL ? 1024.0 : 4.0);
                            }
                        }
                    }

                    sendTitle(p1, "§c§l¡PELEA!", "");
                    sendTitle(p2, "§c§l¡PELEA!", "");
                    p1.sendMessage(plugin.prefix() + "§c§l¡Comienza el duelo!");
                    p2.sendMessage(plugin.prefix() + "§c§l¡Comienza el duelo!");

                    // Sonido de inicio de combate
                    p1.playSound(p1.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                    p2.playSound(p2.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);

                    // Abrir el muro de la arena (si hay configurado)
                    plugin.getWallManager().animateOpen(
                            duel.getArenaTemplate().getName(), world);

                    // Health holograms disabled
                    // plugin.getHealthHologramManager().startHolograms(duel, world);

                    // Mostrar scoreboard de duelo inmediatamente
                    plugin.getScoreboardManager().updateDuelScoreboard(p1, duel);
                    plugin.getScoreboardManager().updateDuelScoreboard(p2, duel);

                    startDurationTimer(duel);
                    startActionBarTask(duel);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L).getTaskId();

        duel.setCountdownTask(taskId);
    }

    /** Starts a max-duration timer that ends the duel in a draw if time runs out. */
    private void startDurationTimer(Duel duel) {
        // Time limit disabled - duels have no time limit
    }

    // ── Duel end ───────────────────────────────────────────────────────────

    /**
     * Ends a duel. winnerUUID may be null for a draw.
     * Handles ELO updates, cleanup, and world destruction.
     */
    public void endDuel(Duel duel, UUID winnerUUID, String reason) {
        // Multi-round interception: a single-round death should start the next round
        if (duel.isMultiRound()
                && "death".equals(reason)
                && duel.getState() == Duel.State.FIGHTING
                && winnerUUID != null) {
            handleBo3Round(duel, winnerUUID);
            return;
        }
        if (duel.getState() == Duel.State.ENDED) return;
        duel.setState(Duel.State.ENDED);
        duel.setWinner(winnerUUID);

        // Cancel pending tasks
        if (duel.getCountdownTask()  != -1) Bukkit.getScheduler().cancelTask(duel.getCountdownTask());
        if (duel.getDurationTask()   != -1) Bukkit.getScheduler().cancelTask(duel.getDurationTask());
        if (duel.getScoreboardTask() != -1) Bukkit.getScheduler().cancelTask(duel.getScoreboardTask());
        stopActionBarTask(duel);
        // Ensure players are never left frozen
        frozenPlayers.remove(duel.getPlayer1());
        frozenPlayers.remove(duel.getPlayer2());

        // Cerrar el muro antes de destruir el mundo
        World instanceWorld = Bukkit.getWorld(duel.getInstanceWorldName());
        if (instanceWorld != null) {
            plugin.getWallManager().animateClose(
                    duel.getArenaTemplate().getName(), instanceWorld);
        }

        // Eliminar holograma de vida
        plugin.getHealthHologramManager().stopHolograms(duel.getId());

        Player p1 = Bukkit.getPlayer(duel.getPlayer1());
        Player p2 = Bukkit.getPlayer(duel.getPlayer2());
        UUID loserUUID = winnerUUID == null ? null
                : winnerUUID.equals(duel.getPlayer1()) ? duel.getPlayer2() : duel.getPlayer1();

        // Rating update — only TIER mode affects rating/stats; normal duels are FRIENDLY
        if (winnerUUID != null && loserUUID != null) {
            Player winner = Bukkit.getPlayer(winnerUUID);
            Player loser  = Bukkit.getPlayer(loserUUID);
            String winnerName = winner != null ? winner.getName() : winnerUUID.toString();
            String loserName  = loser  != null ? loser.getName()  : loserUUID.toString();

            if (duel.isRanked()) {
                // TIER mode: update tier points + record stats
                plugin.getStatsManager().recordWin(winnerUUID, winnerName);
                plugin.getStatsManager().recordLoss(loserUUID, loserName);
                plugin.getStatsManager().recordKill(winnerUUID, winnerName);
                plugin.getStatsManager().recordDeath(loserUUID, loserName);
                plugin.getTierManager().recordResult(winnerUUID, loserUUID, duel.getKitName());
                announceResultTier(p1, p2, winnerUUID, loserUUID, duel.getKitName());
            } else {
                // FRIENDLY duel: NO ELO changes, NO stats, just fun
                announceResultFriendly(p1, p2, winnerUUID, loserUUID, duel.getKitName());
            }
        }

        // Remove spectators
        for (UUID specUUID : duel.getSpectators()) {
            playerDuelMap.remove(specUUID); // CRITICAL: Remove from tracking map
            Player spec = Bukkit.getPlayer(specUUID);
            if (spec != null) {
                spec.sendMessage(plugin.prefix() + "§7El duelo ha terminado. Volviendo al lobby...");
                plugin.getScoreboardManager().restoreLobbyScoreboard(spec);
                restoreSpectator(spec);
                spec.teleport(plugin.getLobbySpawn());
            }
        }

        // Restore and teleport combatants — force-respawn dead players first
        Location lobby = plugin.getLobbySpawn();
        safeRestoreAndTeleport(p1, lobby);
        safeRestoreAndTeleport(p2, lobby);

        // Give trim key AFTER restore so the snapshot doesn't overwrite it
        if (winnerUUID != null && !duel.isRanked()) {
            giveTrimKeyReward(Bukkit.getPlayer(winnerUUID), winnerUUID);
        }

        // Remove from tracking maps
        activeDuels.remove(duel.getId());
        playerDuelMap.remove(duel.getPlayer1());
        playerDuelMap.remove(duel.getPlayer2());

        // Restaurar scoreboard de lobby
        if (p1 != null) plugin.getScoreboardManager().restoreLobbyScoreboard(p1);
        if (p2 != null) plugin.getScoreboardManager().restoreLobbyScoreboard(p2);

        // ── Safety net: guarantee ALL combatants reach lobby ──
        // Catches edge cases where teleport fails (death screen, respawn timing, etc.)
        final UUID uid1 = duel.getPlayer1(), uid2 = duel.getPlayer2();
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                ensureAtLobby(uid1);
                ensureAtLobby(uid2);
            }, 5L);  // 0.25s later
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                ensureAtLobby(uid1);
                ensureAtLobby(uid2);
            }, 20L); // 1s later — final guarantee
        }

        // Destroy or return the arena world to the pool
        String worldName = duel.getCurrentWorldName();
        Runnable worldCleanup = () -> {
            if (plugin.getWorldPoolManager().isPoolWorld(worldName)) {
                plugin.getWorldPoolManager().returnWorld(worldName, duel.getArenaTemplate());
            } else {
                plugin.getArenaInstanceManager().destroyInstance(worldName);
            }
        };
        // During shutdown the scheduler rejects new tasks — run synchronously instead
        if (!plugin.isEnabled()) {
            worldCleanup.run();
        } else {
            new BukkitRunnable() {
                @Override public void run() { worldCleanup.run(); }
            }.runTaskLater(plugin, 5L);
        }
    }

    // ── BO3 round logic ────────────────────────────────────────────────────

    /**
     * Called after a round ends in a ranked (Tier) duel.
     * Increments the winner's round count, then either starts the next round
     * or finalises the match if someone has reached the required wins.
     * Tier matches: BO7 (first to 4 wins)
     */
    private void handleBo3Round(Duel duel, UUID roundWinnerUUID) {
        duel.addWin(roundWinnerUUID);

        int w1 = duel.getWins1();
        int w2 = duel.getWins2();
        int round = duel.getCurrentRound() - 1; // addWin already incremented it

        Player p1 = Bukkit.getPlayer(duel.getPlayer1());
        Player p2 = Bukkit.getPlayer(duel.getPlayer2());
        Player roundWinner = Bukkit.getPlayer(roundWinnerUUID);
        String winnerName  = roundWinner != null ? roundWinner.getName() : "?";

        // Record kill/death for each round in BO3
        UUID roundLoserUUID = roundWinnerUUID.equals(duel.getPlayer1()) ? duel.getPlayer2() : duel.getPlayer1();
        Player roundLoser = Bukkit.getPlayer(roundLoserUUID);
        String loserName = roundLoser != null ? roundLoser.getName() : "?";
        plugin.getStatsManager().recordKill(roundWinnerUUID, winnerName);
        plugin.getStatsManager().recordDeath(roundLoserUUID, loserName);

        // Stop holograms immediately
        plugin.getHealthHologramManager().stopHolograms(duel.getId());

        // Round result chat message — each player sees THEIR score in green, rival in red
        int p1Score = w1, p2Score = w2;
        if (p1 != null) {
            p1.sendMessage(plugin.prefix() + "§e⚔ §6§l" + winnerName
                    + " §egana! §8[§a" + p1Score + " §7- §c" + p2Score + "§8]");
        }
        if (p2 != null) {
            p2.sendMessage(plugin.prefix() + "§e⚔ §6§l" + winnerName
                    + " §egana! §8[§a" + p2Score + " §7- §c" + p1Score + "§8]");
        }

        // Step 1: clear death-cam spectator mode, then teleport to spawns
        if (p1 != null && p1.getGameMode() == GameMode.SPECTATOR) p1.setGameMode(GameMode.SURVIVAL);
        if (p2 != null && p2.getGameMode() == GameMode.SPECTATOR) p2.setGameMode(GameMode.SURVIVAL);
        World worldNow = Bukkit.getWorld(duel.getInstanceWorldName());
        if (worldNow != null) {
            if (p1 != null) p1.teleport(duel.getArenaTemplate().getSpawn1(worldNow));
            if (p2 != null) p2.teleport(duel.getArenaTemplate().getSpawn2(worldNow));
        }

        // Titles are handled by the death handler in PlayerListener (lightning + title sequence)

        // Check for match winner (first to winsNeeded)
        int needed = duel.getWinsNeeded();
        UUID matchWinner = w1 >= needed ? duel.getPlayer1() : (w2 >= needed ? duel.getPlayer2() : null);
        if (matchWinner != null) {
            // Full match decided — death handler already showed 6s title, run cleanup now
            endDuel(duel, matchWinner, "bo3_finished");
            return;
        }

        // ── Next round ────────────────────────────────────────────────────
        // Cancel running duration timer
        if (duel.getDurationTask() != -1) {
            Bukkit.getScheduler().cancelTask(duel.getDurationTask());
            duel.setDurationTask(-1);
        }

        String oldWorldName = duel.getCurrentWorldName();
        World oldWorld = Bukkit.getWorld(oldWorldName);
        if (oldWorld != null) plugin.getWallManager().animateClose(duel.getArenaTemplate().getName(), oldWorld);

        // Prepare state for next round
        duel.setState(Duel.State.COUNTDOWN);

        // ── Try pool swap (instant — no HDD copy during the round) ──────────
        World freshWorld = plugin.getWorldPoolManager().borrowWorld(duel.getArenaTemplate());
        if (freshWorld != null) {
            // Got a clean pool world immediately
            String freshWorldName = freshWorld.getName();
            duel.setCurrentWorldName(freshWorldName);

            // Return the dirty world to pool for async reset
            plugin.getWorldPoolManager().returnWorld(oldWorldName, duel.getArenaTemplate());

            // Spectators: async teleport to fresh world (fire-and-forget)
            Location specLoc = duel.getArenaTemplate().getSpawn1(freshWorld).add(0, 2, 0);
            for (UUID specUUID : duel.getSpectators()) {
                Player spec = Bukkit.getPlayer(specUUID);
                if (spec != null) spec.teleportAsync(specLoc);
            }

            Player rp1 = Bukkit.getPlayer(duel.getPlayer1());
            Player rp2 = Bukkit.getPlayer(duel.getPlayer2());
            if (rp1 == null || rp2 == null) {
                UUID winner = rp1 != null ? duel.getPlayer1() : (rp2 != null ? duel.getPlayer2() : null);
                endDuel(duel, winner, "disconnect");
                return;
            }
            preparePlayer(rp1);
            preparePlayer(rp2);
            final Player frp1 = rp1, frp2 = rp2;
            final World fw = freshWorld;
            CompletableFuture.allOf(
                    frp1.teleportAsync(duel.getArenaTemplate().getSpawn1(fw)),
                    frp2.teleportAsync(duel.getArenaTemplate().getSpawn2(fw))
            ).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (duel.getState() == Duel.State.ENDED) return;
                Player cp1 = Bukkit.getPlayer(frp1.getUniqueId());
                Player cp2 = Bukkit.getPlayer(frp2.getUniqueId());
                if (cp1 == null || cp2 == null) {
                    UUID winner = cp1 != null ? duel.getPlayer1() : (cp2 != null ? duel.getPlayer2() : null);
                    endDuel(duel, winner, "disconnect");
                    return;
                }
                plugin.getKitManager().applyKit(cp1, duel.getKitName());
                plugin.getKitManager().applyKit(cp2, duel.getKitName());
                duel.setStartTimeMillis(System.currentTimeMillis());
                startCountdown(duel, fw);
            }));
            return;
        }

        // ── Fallback: reset current world in-place (pool empty) ─────────────
        plugin.getArenaInstanceManager().resetInstance(
                oldWorldName,
                duel.getArenaTemplate(),
                () -> {
                    Player rp1 = Bukkit.getPlayer(duel.getPlayer1());
                    Player rp2 = Bukkit.getPlayer(duel.getPlayer2());
                    World w    = Bukkit.getWorld(duel.getCurrentWorldName());
                    if (rp1 == null || rp2 == null || w == null) {
                        UUID winner = rp1 != null ? duel.getPlayer1() : (rp2 != null ? duel.getPlayer2() : null);
                        endDuel(duel, winner, "disconnect");
                        return;
                    }
                    // Spectators: async teleport back
                    Location specLoc2 = duel.getArenaTemplate().getSpawn1(w).add(0, 2, 0);
                    for (UUID specUUID : duel.getSpectators()) {
                        Player spec = Bukkit.getPlayer(specUUID);
                        if (spec != null) spec.teleportAsync(specLoc2);
                    }
                    preparePlayer(rp1);
                    preparePlayer(rp2);
                    final Player frp1 = rp1, frp2 = rp2;
                    final World rw = w;
                    CompletableFuture.allOf(
                            frp1.teleportAsync(duel.getArenaTemplate().getSpawn1(rw)),
                            frp2.teleportAsync(duel.getArenaTemplate().getSpawn2(rw))
                    ).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (duel.getState() == Duel.State.ENDED) return;
                        Player cp1 = Bukkit.getPlayer(frp1.getUniqueId());
                        Player cp2 = Bukkit.getPlayer(frp2.getUniqueId());
                        if (cp1 == null || cp2 == null) {
                            UUID win2 = cp1 != null ? duel.getPlayer1() : (cp2 != null ? duel.getPlayer2() : null);
                            endDuel(duel, win2, "disconnect");
                            return;
                        }
                        plugin.getKitManager().applyKit(cp1, duel.getKitName());
                        plugin.getKitManager().applyKit(cp2, duel.getKitName());
                        duel.setStartTimeMillis(System.currentTimeMillis());
                        startCountdown(duel, rw);
                    }));
                }
        );
    }

    /** Ends a duel by looking up the duel from a player UUID. */
    public void endDuelByPlayer(UUID playerUUID, UUID winnerUUID, String reason) {
        Duel duel = getDuelByPlayer(playerUUID);
        if (duel != null) endDuel(duel, winnerUUID, reason);
    }

    // ── Spectator management ───────────────────────────────────────────────

    /**
     * Adds a spectator to an active duel.
     * The spectator is made invisible to combatants.
     */
    public boolean addSpectator(Player spectator, Player target) {
        UUID duelId = playerDuelMap.get(target.getUniqueId());
        if (duelId == null) return false;
        Duel duel = activeDuels.get(duelId);
        if (duel == null || duel.getState() == Duel.State.ENDED) return false;

        saveSnapshot(spectator);
        prepareSpectator(spectator);
        duel.addSpectator(spectator.getUniqueId());
        playerDuelMap.put(spectator.getUniqueId(), duelId);

        // Teleport to player 1 location
        Player p1 = Bukkit.getPlayer(duel.getPlayer1());
        if (p1 != null) {
            spectator.teleport(p1.getLocation().add(0, 2, 0));
        }

        // Hide spectator from combatants
        hideSpectatorFromAll(spectator, duel);

        // Show spectator scoreboard with duel info
        plugin.getScoreboardManager().showSpectatorScoreboard(spectator, duel);
        return true;
    }

    public void removeSpectatorFromDuel(Player spectator, Duel duel) {
        duel.removeSpectator(spectator.getUniqueId());
        playerDuelMap.remove(spectator.getUniqueId());
        restoreSpectator(spectator);
        spectator.teleport(plugin.getLobbySpawn());
        showSpectatorToAll(spectator, duel);
    }

    // ── Player preparation / restoration ──────────────────────────────────

    private void saveSnapshot(Player player) {
        inventorySnapshots.put(player.getUniqueId(), new PlayerSnapshot(player));
    }

    /**
     * Force-respawn if dead, restore inventory, and teleport to lobby.
     * If the player is still in the death screen, schedule a delayed retry.
     */
    private void safeRestoreAndTeleport(Player player, Location lobby) {
        if (player == null) return;
        // Force respawn if still dead
        if (player.isDead()) {
            try { player.spigot().respawn(); } catch (Exception ignored) {}
        }
        // Clear spectator mode from death cam
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        restorePlayer(player);
        player.teleport(lobby);
    }

    /**
     * Safety net: if the player is online but NOT in the lobby world, force-teleport them.
     * If they ARE at lobby, clean up any leftover snapshot.
     * Handles edge cases where the initial teleport silently failed.
     */
    private void ensureAtLobby(UUID uuid) {
        if (uuid == null) return;
        Player p = Bukkit.getPlayer(uuid);
        if (p == null || !p.isOnline()) return;
        // Already in a new duel? Don't interfere
        if (playerDuelMap.containsKey(uuid)) return;
        // Already in an FFA? Don't interfere
        if (isInFFA(uuid)) return;

        Location lobby = plugin.getLobbySpawn();
        String lobbyWorld = lobby.getWorld() != null ? lobby.getWorld().getName() : "";
        String playerWorld = p.getWorld().getName();

        // Always clear frozen state — safety net
        frozenPlayers.remove(uuid);

        if (!playerWorld.equals(lobbyWorld)) {
            if (p.isDead()) {
                try { p.spigot().respawn(); } catch (Exception ignored) {}
            }
            if (p.getGameMode() == GameMode.SPECTATOR) {
                p.setGameMode(GameMode.SURVIVAL);
            }
            restorePlayer(p);
            p.teleport(lobby);
            plugin.getScoreboardManager().restoreLobbyScoreboard(p);
        } else {
            // Player IS at lobby — restore snapshot if it wasn't applied yet
            // (handles the "lobby with arena items" bug)
            PlayerSnapshot snap = inventorySnapshots.get(uuid);
            if (snap != null) {
                restorePlayer(p);
                plugin.getScoreboardManager().restoreLobbyScoreboard(p);
            }
            // Safety: always reset movement state at lobby
            p.setGravity(true);
            p.setAllowFlight(false);
            p.setFlying(false);
            p.setWalkSpeed(0.2f);
        }
    }

    private void restorePlayer(Player player) {
        PlayerSnapshot snap = inventorySnapshots.remove(player.getUniqueId());
        if (snap != null) snap.restore(player);
        healPlayer(player);
        player.setGameMode(GameMode.SURVIVAL);
        // Safety resets — prevent stuck-in-air / broken movement
        player.setGravity(true);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setWalkSpeed(0.2f);  // Vanilla default
        player.setFlySpeed(0.1f);   // Vanilla default
        frozenPlayers.remove(player.getUniqueId()); // Clear any leftover freeze
        var atkSpeed = player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_SPEED);
        if (atkSpeed != null) atkSpeed.setBaseValue(4.0);
    }

    private void restoreSpectator(Player player) {
        PlayerSnapshot snap = inventorySnapshots.remove(player.getUniqueId());
        if (snap != null) snap.restore(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        // Make visible again
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, player);
            player.showPlayer(plugin, online);
        }
    }

    private void preparePlayer(Player player) {
        healPlayer(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        player.setFireTicks(0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        // Safety resets — prevent stuck-in-air / broken movement
        player.setGravity(true);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setWalkSpeed(0.2f);  // Vanilla default
        player.setFlySpeed(0.1f);   // Vanilla default
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    private void prepareSpectator(Player spectator) {
        spectator.getInventory().clear();
        spectator.setGameMode(GameMode.SPECTATOR);
        spectator.setAllowFlight(true);
        spectator.setFlying(true);
    }

    private void healPlayer(Player player) {
        var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            player.setHealth(maxHealthAttr.getValue());
        }
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }

    private void hideSpectatorFromAll(Player spectator, Duel duel) {
        Player p1 = Bukkit.getPlayer(duel.getPlayer1());
        Player p2 = Bukkit.getPlayer(duel.getPlayer2());
        if (p1 != null) p1.hidePlayer(plugin, spectator);
        if (p2 != null) p2.hidePlayer(plugin, spectator);
    }

    private void showSpectatorToAll(Player spectator, Duel duel) {
        Player p1 = Bukkit.getPlayer(duel.getPlayer1());
        Player p2 = Bukkit.getPlayer(duel.getPlayer2());
        if (p1 != null) p1.showPlayer(plugin, spectator);
        if (p2 != null) p2.showPlayer(plugin, spectator);
    }

    // ── Announce result ────────────────────────────────────────────────────

    private void announceResult(Player p1, Player p2, UUID winnerUUID, UUID loserUUID,
                                String kitName, int winGain, int lossChange) {
        Player winner = Bukkit.getPlayer(winnerUUID);
        Player loser  = Bukkit.getPlayer(loserUUID);

        String winnerName = winner != null ? winner.getName() : "Unknown";
        String loserName  = loser  != null ? loser.getName()  : "Unknown";

        com.pvprooms.model.Tier wTier = plugin.getTierManager().getBestTier(winnerUUID);
        com.pvprooms.model.Tier lTier = plugin.getTierManager().getBestTier(loserUUID);

        if (winner != null) {
            int newElo = plugin.getEloManager().getElo(winnerUUID);
            winner.sendMessage(plugin.prefix()
                    + "§a§l¡GANASTE! §avs §e" + loserName
                    + "  §7(+" + winGain + " ELO → §e" + newElo + "§7)");
            winner.sendMessage(plugin.prefix()
                    + "§7Rango: " + wTier.colour + "§l" + wTier.displayName
                    + "  §8| §7Kit: §f" + kitName);
        }
        if (loser != null) {
            int newElo = plugin.getEloManager().getElo(loserUUID);
            loser.sendMessage(plugin.prefix()
                    + "§c§lPERDISTE §cvs §e" + winnerName
                    + "  §7(-" + lossChange + " ELO → §e" + newElo + "§7)");
            loser.sendMessage(plugin.prefix()
                    + "§7Rango: " + lTier.colour + "§l" + lTier.displayName
                    + "  §8| §7Kit: §f" + kitName);
        }
    }

    /** Announces a FRIENDLY duel result — no ELO or tier points shown. */
    private void announceResultFriendly(Player p1, Player p2, UUID winnerUUID, UUID loserUUID, String kitName) {
        Player winner = Bukkit.getPlayer(winnerUUID);
        Player loser  = Bukkit.getPlayer(loserUUID);
        String winnerName = winner != null ? winner.getName() : "Unknown";
        String loserName  = loser  != null ? loser.getName()  : "Unknown";

        if (winner != null) {
            winner.sendMessage(plugin.prefix() + "§a§l¡GANASTE! §avs §e" + loserName + "  §7(Amistoso)");
            winner.sendMessage(plugin.prefix() + "§7Kit: §f" + kitName + "  §8| §7Sin cambios de ELO");
        }
        if (loser != null) {
            loser.sendMessage(plugin.prefix() + "§c§lPERDISTE §cvs §e" + winnerName + "  §7(Amistoso)");
            loser.sendMessage(plugin.prefix() + "§7Kit: §f" + kitName + "  §8| §7Sin cambios de ELO");
        }
    }

    /** Announces a TIER-mode match result using tier points instead of ELO. */
    private void announceResultTier(Player p1, Player p2, UUID winnerUUID, UUID loserUUID, String kitName) {
        Player winner = Bukkit.getPlayer(winnerUUID);
        Player loser  = Bukkit.getPlayer(loserUUID);
        String winnerName = winner != null ? winner.getName() : "?";
        String loserName  = loser  != null ? loser.getName()  : "?";

        com.pvprooms.model.Tier wTier = plugin.getTierManager().getTier(winnerUUID, kitName);
        com.pvprooms.model.Tier lTier = plugin.getTierManager().getTier(loserUUID,  kitName);
        com.pvprooms.model.TierTitle wTitle = plugin.getTierManager().getTitle(winnerUUID);
        com.pvprooms.model.TierTitle lTitle = plugin.getTierManager().getTitle(loserUUID);

        if (winner != null) {
            int pts = plugin.getTierManager().getPoints(winnerUUID, kitName);
            winner.sendMessage(plugin.prefix()
                    + "§a§l¡VICTORIA! §avs §e" + loserName
                    + "  §8[§bTIER §e" + kitName + "§8]  "
                    + wTier.colour + wTier.displayName
                    + "  §8› §7" + pts + " pts");
            winner.sendMessage(plugin.prefix() + "§7Insignia: " + wTitle.formatted());
        }
        if (loser != null) {
            int pts = plugin.getTierManager().getPoints(loserUUID, kitName);
            loser.sendMessage(plugin.prefix()
                    + "§c§lDERROTA §cvs §e" + winnerName
                    + "  §8[§bTIER §e" + kitName + "§8]  "
                    + lTier.colour + lTier.displayName
                    + "  §8› §7" + pts + " pts");
            loser.sendMessage(plugin.prefix() + "§7Insignia: " + lTitle.formatted());
        }
    }

    // ── Query helpers ──────────────────────────────────────────────────────

    /** Returns true if this player is currently frozen during a countdown. */
    public boolean isFrozen(UUID uuid) {
        return frozenPlayers.contains(uuid);
    }

    public Map<UUID, UUID> getPlayerDuelMap() {
        return playerDuelMap;
    }

    /** Force-unfreeze a player (safety net for stuck states). */
    public void unfreezePlayer(UUID uuid) {
        frozenPlayers.remove(uuid);
    }

    public boolean isInDuel(UUID uuid) {
        return playerDuelMap.containsKey(uuid);
    }

    public Duel getDuelByPlayer(UUID uuid) {
        UUID duelId = playerDuelMap.get(uuid);
        return duelId != null ? activeDuels.get(duelId) : null;
    }

    public Duel getDuelByWorldName(String worldName) {
        for (Duel duel : activeDuels.values()) {
            if (duel.getCurrentWorldName().equals(worldName)) return duel;
        }
        return null;
    }

    public int getActiveDuelCount()            { return activeDuels.size(); }
    public Collection<Duel> getActiveDuels()   { return activeDuels.values(); }
    public Duel getDuelById(UUID duelId)       { return activeDuels.get(duelId); }

    // ── Utility ────────────────────────────────────────────────────────────

    private void sendTitle(Player player, String title, String subtitle) {
        sendTitle(player, title, subtitle, 40);
    }

    private void sendTitle(Player player, String title, String subtitle, int stayTicks) {
        player.sendTitle(
                ChatColor.translateAlternateColorCodes('&', title),
                ChatColor.translateAlternateColorCodes('&', subtitle),
                10, stayTicks, 20
        );
    }

    // ── FFA (Free For All) Match ───────────────────────────────────────────
    
    /** Active FFA matches: match ID -> set of participant UUIDs */
    private final Map<UUID, Set<UUID>> ffaMatches = new ConcurrentHashMap<>();
    /** Player UUID -> FFA match ID */
    private final Map<UUID, UUID> playerFFAMap = new ConcurrentHashMap<>();
    /** FFA match ID -> instance world name */
    private final Map<UUID, String> ffaWorlds = new ConcurrentHashMap<>();
    /** FFA match ID -> kit name */
    private final Map<UUID, String> ffaKits = new ConcurrentHashMap<>();
    /** FFA match ID -> arena template (for block/explosion permissions) */
    private final Map<UUID, ArenaTemplate> ffaTemplates = new ConcurrentHashMap<>();
    /** Players who died in FFA and are spectating: player UUID -> match ID */
    private final Map<UUID, UUID> ffaDeadSpectators = new ConcurrentHashMap<>();
    
    /**
     * Starts a FFA match with multiple players.
     */
    public void startFFAMatch(List<Player> participants, String kitName, ArenaTemplate arena) {
        if (participants.size() < 2) return;
        
        UUID matchId = UUID.randomUUID();
        String matchIdShort = matchId.toString().replace("-", "").substring(0, 10);
        String instanceWorldName = plugin.getConfig().getString("arenas.instance-prefix", "pvp_match_") + matchIdShort;
        
        // Create world instance
        World instanceWorld = plugin.getArenaInstanceManager().createInstance(arena, matchIdShort);
        if (instanceWorld == null) {
            for (Player p : participants) {
                p.sendMessage(plugin.prefix() + "§cError al crear la arena. Contacta a un admin.");
            }
            return;
        }
        
        // Register match
        Set<UUID> participantUUIDs = ConcurrentHashMap.newKeySet();
        for (Player p : participants) {
            participantUUIDs.add(p.getUniqueId());
            playerFFAMap.put(p.getUniqueId(), matchId);
        }
        ffaMatches.put(matchId, participantUUIDs);
        ffaWorlds.put(matchId, instanceWorldName);
        ffaKits.put(matchId, kitName);
        ffaTemplates.put(matchId, arena);
        
        // Prepare and teleport all players
        Location spawn1 = arena.getSpawn1(instanceWorld);
        Location spawn2 = arena.getSpawn2(instanceWorld);
        Location center = spawn1.clone().add(spawn2).multiply(0.5);
        
        int i = 0;
        double radius = 5.0;
        for (Player p : participants) {
            saveSnapshot(p);
            preparePlayer(p);
            
            // Spread players in a circle around center
            double angle = (2 * Math.PI * i) / participants.size();
            Location spawnLoc = center.clone().add(
                Math.cos(angle) * radius, 
                0, 
                Math.sin(angle) * radius
            );
            spawnLoc.setY(spawn1.getY());
            p.teleport(spawnLoc);
            
            plugin.getKitManager().applyKit(p, kitName);
            plugin.getScoreboardManager().clearScoreboard(p);
            i++;
        }
        
        // Announce
        for (Player p : participants) {
            p.sendMessage(plugin.prefix() + "§a§l¡FFA INICIADO! §7Kit: §e" + kitName);
            p.sendMessage(plugin.prefix() + "§7Jugadores: §f" + participants.size() + " §8| §7Último en pie gana!");
        }
        
        // Start countdown
        startFFACountdown(matchId, participants, instanceWorld, arena);
    }
    
    private void startFFACountdown(UUID matchId, List<Player> participants, World world, ArenaTemplate arena) {
        int seconds = plugin.getConfig().getInt("duels.countdown", 5);
        
        // Freeze all players
        for (Player p : participants) {
            frozenPlayers.add(p.getUniqueId());
        }
        
        new BukkitRunnable() {
            int remaining = seconds;
            
            @Override
            public void run() {
                Set<UUID> alive = ffaMatches.get(matchId);
                if (alive == null) {
                    cancel();
                    return;
                }
                
                List<Player> onlinePlayers = new ArrayList<>();
                for (UUID uuid : alive) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) onlinePlayers.add(p);
                }
                
                if (onlinePlayers.size() < 2) {
                    for (UUID uuid : alive) frozenPlayers.remove(uuid);
                    cancel();
                    endFFAMatch(matchId, onlinePlayers.isEmpty() ? null : onlinePlayers.get(0));
                    return;
                }
                
                if (remaining > 0) {
                    for (Player p : onlinePlayers) {
                        sendTitle(p, "§e§l" + remaining, "§7¡Prepárate para el FFA!");
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.8f + (1.0f / seconds) * (seconds - remaining));
                    }
                    remaining--;
                } else {
                    // Unfreeze and start
                    for (UUID uuid : alive) frozenPlayers.remove(uuid);
                    
                    for (Player p : onlinePlayers) {
                        sendTitle(p, "§c§l¡PELEA!", "§7Último en pie gana");
                        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                        p.sendMessage(plugin.prefix() + "§c§l¡COMIENZA EL FFA!");
                    }
                    
                    plugin.getWallManager().animateOpen(arena.getName(), world);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
    
    /** Called when a player dies in FFA */
    public void handleFFADeath(Player dead, Player killer) {
        UUID deadUUID = dead.getUniqueId();
        UUID matchId = playerFFAMap.get(deadUUID);
        if (matchId == null) return;

        Set<UUID> alive = ffaMatches.get(matchId);
        if (alive == null) return;

        alive.remove(deadUUID);
        // Keep in playerFFAMap — isInFFA() must stay true while spectating
        ffaDeadSpectators.put(deadUUID, matchId);

        // Switch to spectator so they can watch the rest of the match
        dead.setGameMode(GameMode.SPECTATOR);
        dead.setAllowFlight(true);
        dead.setFlying(true);

        // Teleport back into the arena world to spectate
        String worldName = ffaWorlds.get(matchId);
        ArenaTemplate template = ffaTemplates.get(matchId);
        if (worldName != null && template != null) {
            World ffaWorld = Bukkit.getWorld(worldName);
            if (ffaWorld != null) {
                dead.teleport(template.getSpawn1(ffaWorld).clone().add(0, 5, 0));
            }
        }

        dead.sendMessage(plugin.prefix() + "§c¡Has sido eliminado! §7Ahora eres espectador. Usa §f/pvpleave §7para salir.");
        if (killer != null) {
            dead.sendMessage(plugin.prefix() + "§7Eliminado por: §c" + killer.getName());
        }

        // Announce to remaining
        for (UUID uuid : alive) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(plugin.prefix() + "§c" + dead.getName() + " §7ha sido eliminado. §fQuedan §e" + alive.size() + " §fjugadores.");
                if (killer != null && p.equals(killer)) {
                    p.sendMessage(plugin.prefix() + "§a+1 Kill §7- Eliminaste a §c" + dead.getName());
                }
            }
        }

        // Check for winner
        if (alive.size() == 1) {
            UUID winnerUUID = alive.iterator().next();
            Player winner = Bukkit.getPlayer(winnerUUID);
            endFFAMatch(matchId, winner);
        }
    }
    
    private void endFFAMatch(UUID matchId, Player winner) {
        Set<UUID> participants = ffaMatches.remove(matchId);
        String worldName = ffaWorlds.remove(matchId);
        String kitName = ffaKits.remove(matchId);
        ffaTemplates.remove(matchId);

        // Collect dead spectators watching this match
        List<UUID> deadSpecs = new ArrayList<>();
        ffaDeadSpectators.entrySet().removeIf(e -> {
            if (e.getValue().equals(matchId)) { deadSpecs.add(e.getKey()); return true; }
            return false;
        });

        if (participants != null) {
            for (UUID uuid : participants) {
                playerFFAMap.remove(uuid);
                frozenPlayers.remove(uuid);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    restorePlayer(p);
                    p.teleport(plugin.getLobbySpawn());
                    plugin.getScoreboardManager().restoreLobbyScoreboard(p);

                    if (winner != null) {
                        if (p.equals(winner)) {
                            sendTitle(p, "§6§l¡VICTORIA!", "§7¡Has ganado el FFA!");
                            p.sendMessage(plugin.prefix() + "§6§l¡GANASTE EL FFA! §a¡Felicidades!");
                            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                        } else {
                            p.sendMessage(plugin.prefix() + "§e" + winner.getName() + " §7ha ganado el FFA.");
                        }
                    }
                }
            }
        }

        // Restore dead spectators and send them home
        for (UUID specUUID : deadSpecs) {
            playerFFAMap.remove(specUUID);
            Player spec = Bukkit.getPlayer(specUUID);
            if (spec != null) {
                restorePlayer(spec);
                spec.teleport(plugin.getLobbySpawn());
                plugin.getScoreboardManager().restoreLobbyScoreboard(spec);
                spec.sendMessage(plugin.prefix() + "§7La partida FFA ha terminado.");
                if (winner != null) {
                    spec.sendMessage(plugin.prefix() + "§e" + winner.getName() + " §7ha ganado el FFA.");
                }
            }
        }

        // Delete world instance
        if (worldName != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getArenaInstanceManager().destroyInstance(worldName);
            }, 60L);
        }
    }
    
    /** Check if player is in a FFA match (active fighter or dead spectator) */
    public boolean isInFFA(UUID uuid) {
        return playerFFAMap.containsKey(uuid);
    }

    /** Returns true if the player died in FFA and is currently spectating */
    public boolean isFFASpectator(UUID uuid) {
        return ffaDeadSpectators.containsKey(uuid);
    }

    /** Removes an FFA dead spectator from the match (e.g. /pvpleave or disconnect) */
    public void removeFFASpectator(UUID uuid) {
        ffaDeadSpectators.remove(uuid);
        playerFFAMap.remove(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            restorePlayer(p);
            p.teleport(plugin.getLobbySpawn());
            plugin.getScoreboardManager().restoreLobbyScoreboard(p);
        }
    }
    
    /** Get FFA match ID for a player */
    public UUID getFFAMatchId(UUID uuid) {
        return playerFFAMap.get(uuid);
    }

    /** Get the kit name for the FFA match a player is in, or null if not in FFA */
    public String getFFAKit(UUID uuid) {
        UUID matchId = playerFFAMap.get(uuid);
        if (matchId == null) return null;
        return ffaKits.get(matchId);
    }
    
    /** Get ArenaTemplate for a FFA match by world name */
    public ArenaTemplate getFFATemplateByWorldName(String worldName) {
        for (Map.Entry<UUID, String> entry : ffaWorlds.entrySet()) {
            if (entry.getValue().equals(worldName)) {
                return ffaTemplates.get(entry.getKey());
            }
        }
        return null;
    }

    // ── 2v2 Team Duel ──────────────────────────────────────────────────────

    /** 2v2 match ID -> Team A (set of 2 UUIDs) */
    private final Map<UUID, Set<UUID>> teamAMap = new ConcurrentHashMap<>();
    /** 2v2 match ID -> Team B (set of 2 UUIDs) */
    private final Map<UUID, Set<UUID>> teamBMap = new ConcurrentHashMap<>();
    /** Tracks which FFA matches are actually 2v2 (match ID set) */
    private final Set<UUID> is2v2Match = ConcurrentHashMap.newKeySet();

    /**
     * Starts a 2v2 team duel. Players are split into two teams of 2.
     * Team A = participants[0,1], Team B = participants[2,3].
     * Uses the FFA infrastructure for world/tracking, with team overlay.
     */
    public void start2v2Match(List<Player> participants, String kitName, ArenaTemplate arena) {
        if (participants.size() != 4) {
            for (Player p : participants)
                p.sendMessage(plugin.prefix() + "§cEl modo 2v2 requiere exactamente 4 jugadores.");
            return;
        }

        UUID matchId = UUID.randomUUID();
        String matchIdShort = matchId.toString().replace("-", "").substring(0, 10);
        String instanceWorldName = plugin.getConfig().getString("arenas.instance-prefix", "pvp_match_") + matchIdShort;

        // Create world instance
        World instanceWorld = plugin.getArenaInstanceManager().createInstance(arena, matchIdShort);
        if (instanceWorld == null) {
            for (Player p : participants)
                p.sendMessage(plugin.prefix() + "§cError al crear la arena. Contacta a un admin.");
            return;
        }

        // Register match using FFA maps (2v2 piggybacks on FFA tracking)
        Set<UUID> participantUUIDs = ConcurrentHashMap.newKeySet();
        for (Player p : participants) {
            participantUUIDs.add(p.getUniqueId());
            playerFFAMap.put(p.getUniqueId(), matchId);
        }
        ffaMatches.put(matchId, participantUUIDs);
        ffaWorlds.put(matchId, instanceWorldName);
        ffaKits.put(matchId, kitName);
        ffaTemplates.put(matchId, arena);

        // Register teams
        Set<UUID> teamA = ConcurrentHashMap.newKeySet();
        teamA.add(participants.get(0).getUniqueId());
        teamA.add(participants.get(1).getUniqueId());
        Set<UUID> teamB = ConcurrentHashMap.newKeySet();
        teamB.add(participants.get(2).getUniqueId());
        teamB.add(participants.get(3).getUniqueId());
        teamAMap.put(matchId, teamA);
        teamBMap.put(matchId, teamB);
        is2v2Match.add(matchId);

        // Teleport teams: Team A near spawn1, Team B near spawn2
        Location spawn1 = arena.getSpawn1(instanceWorld);
        Location spawn2 = arena.getSpawn2(instanceWorld);

        // Team A: spread around spawn1
        teleportTeam(participants.get(0), participants.get(1), spawn1);
        // Team B: spread around spawn2
        teleportTeam(participants.get(2), participants.get(3), spawn2);

        // Prepare all players
        for (Player p : participants) {
            saveSnapshot(p);
            preparePlayer(p);
        }
        // Teleport AFTER prepare (prepare clears inventory)
        teleportTeam(participants.get(0), participants.get(1), spawn1);
        teleportTeam(participants.get(2), participants.get(3), spawn2);
        for (Player p : participants) {
            plugin.getKitManager().applyKit(p, kitName);
            plugin.getScoreboardManager().clearScoreboard(p);
        }

        // Team name colors
        String teamANames = "§a" + participants.get(0).getName() + " §7& §a" + participants.get(1).getName();
        String teamBNames = "§c" + participants.get(2).getName() + " §7& §c" + participants.get(3).getName();

        // Announce
        for (Player p : participants) {
            p.sendMessage(plugin.prefix() + "§6§l¡2v2 INICIADO! §7Kit: §e" + kitName);
            p.sendMessage(plugin.prefix() + "§aEquipo 1: " + teamANames);
            p.sendMessage(plugin.prefix() + "§cEquipo 2: " + teamBNames);

            // Tell each player their teammate
            UUID pUUID = p.getUniqueId();
            UUID teammateUUID = getTeammate(matchId, pUUID);
            Player teammate = teammateUUID != null ? Bukkit.getPlayer(teammateUUID) : null;
            if (teammate != null) {
                p.sendMessage(plugin.prefix() + "§7Tu compañero: §e" + teammate.getName());
            }
        }

        // Start countdown (reuses FFA countdown with custom title)
        start2v2Countdown(matchId, participants, instanceWorld, arena);
    }

    private void teleportTeam(Player p1, Player p2, Location spawn) {
        // Offset players slightly so they don't overlap
        Location loc1 = spawn.clone().add(1.5, 0, 0);
        Location loc2 = spawn.clone().add(-1.5, 0, 0);
        loc1.setYaw(spawn.getYaw());
        loc1.setPitch(spawn.getPitch());
        loc2.setYaw(spawn.getYaw());
        loc2.setPitch(spawn.getPitch());
        p1.teleport(loc1);
        p2.teleport(loc2);
    }

    private void start2v2Countdown(UUID matchId, List<Player> participants, World world, ArenaTemplate arena) {
        int seconds = plugin.getConfig().getInt("duels.countdown", 5);
        for (Player p : participants) frozenPlayers.add(p.getUniqueId());

        new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                Set<UUID> alive = ffaMatches.get(matchId);
                if (alive == null) { cancel(); return; }

                List<Player> online = new ArrayList<>();
                for (UUID uuid : alive) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) online.add(p);
                }

                if (online.size() < 2) {
                    for (UUID uuid : alive) frozenPlayers.remove(uuid);
                    cancel();
                    end2v2Match(matchId, null);
                    return;
                }

                if (remaining > 0) {
                    for (Player p : online) {
                        sendTitle(p, "§e§l" + remaining, "§7¡Prepárate para el 2v2!");
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.8f + (1.0f / seconds) * (seconds - remaining));
                    }
                    remaining--;
                } else {
                    for (UUID uuid : alive) frozenPlayers.remove(uuid);
                    for (Player p : online) {
                        sendTitle(p, "§c§l¡PELEA!", "§7¡2v2 — Elimina al equipo rival!");
                        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                        p.sendMessage(plugin.prefix() + "§c§l¡COMIENZA EL 2v2!");
                    }
                    plugin.getWallManager().animateOpen(arena.getName(), world);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Called when a player dies in a 2v2 match.
     * If both teammates are dead, the other team wins.
     */
    public void handle2v2Death(Player dead, Player killer) {
        UUID deadUUID = dead.getUniqueId();
        UUID matchId = playerFFAMap.get(deadUUID);
        if (matchId == null) return;

        Set<UUID> alive = ffaMatches.get(matchId);
        if (alive == null) return;

        alive.remove(deadUUID);
        ffaDeadSpectators.put(deadUUID, matchId);

        // Switch to spectator
        dead.setGameMode(GameMode.SPECTATOR);
        dead.setAllowFlight(true);
        dead.setFlying(true);

        String worldName = ffaWorlds.get(matchId);
        ArenaTemplate template = ffaTemplates.get(matchId);
        if (worldName != null && template != null) {
            World w = Bukkit.getWorld(worldName);
            if (w != null) dead.teleport(template.getSpawn1(w).clone().add(0, 5, 0));
        }

        dead.sendMessage(plugin.prefix() + "§c¡Has sido eliminado! §7Observando a tu equipo...");
        if (killer != null) {
            dead.sendMessage(plugin.prefix() + "§7Eliminado por: §c" + killer.getName());
        }

        // Announce
        for (UUID uuid : alive) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(plugin.prefix() + "§c" + dead.getName() + " §7ha sido eliminado.");
                if (killer != null && p.equals(killer)) {
                    p.sendMessage(plugin.prefix() + "§a+1 Kill §7- Eliminaste a §c" + dead.getName());
                }
            }
        }

        // Check if an entire team is eliminated
        Set<UUID> teamA = teamAMap.get(matchId);
        Set<UUID> teamB = teamBMap.get(matchId);
        if (teamA == null || teamB == null) return;

        boolean teamAAlive = false, teamBAlive = false;
        for (UUID uuid : teamA) { if (alive.contains(uuid)) { teamAAlive = true; break; } }
        for (UUID uuid : teamB) { if (alive.contains(uuid)) { teamBAlive = true; break; } }

        if (!teamAAlive) {
            // Team A eliminated → Team B wins
            end2v2Match(matchId, teamB);
        } else if (!teamBAlive) {
            // Team B eliminated → Team A wins
            end2v2Match(matchId, teamA);
        }
    }

    private void end2v2Match(UUID matchId, Set<UUID> winningTeam) {
        Set<UUID> participants = ffaMatches.remove(matchId);
        String worldName = ffaWorlds.remove(matchId);
        ffaKits.remove(matchId);
        ffaTemplates.remove(matchId);
        Set<UUID> teamA = teamAMap.remove(matchId);
        Set<UUID> teamB = teamBMap.remove(matchId);
        is2v2Match.remove(matchId);

        // Collect dead spectators
        List<UUID> deadSpecs = new ArrayList<>();
        ffaDeadSpectators.entrySet().removeIf(e -> {
            if (e.getValue().equals(matchId)) { deadSpecs.add(e.getKey()); return true; }
            return false;
        });

        // Build winner names for display
        String winnerNames = "???";
        if (winningTeam != null) {
            StringBuilder sb = new StringBuilder();
            for (UUID uuid : winningTeam) {
                Player p = Bukkit.getPlayer(uuid);
                if (sb.length() > 0) sb.append(" §7& §e");
                sb.append(p != null ? p.getName() : uuid.toString().substring(0, 8));
            }
            winnerNames = sb.toString();
        }

        // Restore all living participants
        if (participants != null) {
            for (UUID uuid : participants) {
                playerFFAMap.remove(uuid);
                frozenPlayers.remove(uuid);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    restorePlayer(p);
                    p.teleport(plugin.getLobbySpawn());
                    plugin.getScoreboardManager().restoreLobbyScoreboard(p);

                    if (winningTeam != null) {
                        if (winningTeam.contains(uuid)) {
                            sendTitle(p, "§6§l¡VICTORIA!", "§7¡Tu equipo ha ganado el 2v2!");
                            p.sendMessage(plugin.prefix() + "§6§l¡GANASTE EL 2v2! §a¡Felicidades!");
                            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                        } else {
                            p.sendMessage(plugin.prefix() + "§e" + winnerNames + " §7ha ganado el 2v2.");
                        }
                    }
                }
            }
        }

        // Restore dead spectators
        for (UUID specUUID : deadSpecs) {
            playerFFAMap.remove(specUUID);
            Player spec = Bukkit.getPlayer(specUUID);
            if (spec != null) {
                restorePlayer(spec);
                spec.teleport(plugin.getLobbySpawn());
                plugin.getScoreboardManager().restoreLobbyScoreboard(spec);
                if (winningTeam != null) {
                    if (winningTeam.contains(specUUID)) {
                        sendTitle(spec, "§6§l¡VICTORIA!", "§7¡Tu equipo ha ganado el 2v2!");
                        spec.sendMessage(plugin.prefix() + "§6§l¡GANASTE EL 2v2! §a¡Felicidades!");
                        spec.playSound(spec.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    } else {
                        spec.sendMessage(plugin.prefix() + "§e" + winnerNames + " §7ha ganado el 2v2.");
                    }
                }
            }
        }

        // Destroy world
        if (worldName != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    plugin.getArenaInstanceManager().destroyInstance(worldName), 60L);
        }
    }

    /** Returns true if the given FFA match is actually a 2v2 */
    public boolean is2v2(UUID matchId) {
        return matchId != null && is2v2Match.contains(matchId);
    }

    /** Returns the teammate UUID of a player in a 2v2 match, or null */
    public UUID getTeammate(UUID matchId, UUID playerUUID) {
        if (matchId == null) return null;
        Set<UUID> teamA = teamAMap.get(matchId);
        Set<UUID> teamB = teamBMap.get(matchId);
        if (teamA != null && teamA.contains(playerUUID)) {
            for (UUID u : teamA) { if (!u.equals(playerUUID)) return u; }
        }
        if (teamB != null && teamB.contains(playerUUID)) {
            for (UUID u : teamB) { if (!u.equals(playerUUID)) return u; }
        }
        return null;
    }

    /** Returns true if two players are on the same team in a 2v2 match */
    public boolean areTeammates(UUID uuid1, UUID uuid2) {
        UUID matchId = playerFFAMap.get(uuid1);
        if (matchId == null || !is2v2Match.contains(matchId)) return false;
        Set<UUID> teamA = teamAMap.get(matchId);
        Set<UUID> teamB = teamBMap.get(matchId);
        if (teamA != null && teamA.contains(uuid1) && teamA.contains(uuid2)) return true;
        if (teamB != null && teamB.contains(uuid1) && teamB.contains(uuid2)) return true;
        return false;
    }

    // ── Inner class: Player snapshot ───────────────────────────────────────

    /**
     * Captures a player's full state before a duel so it can be restored afterwards.
     */
    private static class PlayerSnapshot {
        private final ItemStack[] contents;
        private final ItemStack[] armorContents;
        private final ItemStack offhand;
        private final int foodLevel;
        private final double health;
        private final GameMode gameMode;
        private final Location location;
        private final Collection<PotionEffect> effects;

        PlayerSnapshot(Player player) {
            this.contents      = player.getInventory().getContents().clone();
            this.armorContents = player.getInventory().getArmorContents().clone();
            this.offhand       = player.getInventory().getItemInOffHand().clone();
            this.foodLevel     = player.getFoodLevel();
            this.health        = player.getHealth();
            this.gameMode      = player.getGameMode();
            this.location      = player.getLocation().clone();
            this.effects       = new ArrayList<>(player.getActivePotionEffects());
        }

        void restore(Player player) {
            player.getInventory().setContents(contents);
            player.getInventory().setArmorContents(armorContents);
            player.getInventory().setItemInOffHand(offhand);
            player.setFoodLevel(foodLevel);
            player.setGameMode(gameMode);
            for (PotionEffect e : player.getActivePotionEffects()) {
                player.removePotionEffect(e.getType());
            }
            for (PotionEffect e : effects) {
                player.addPotionEffect(e);
            }
            player.updateInventory();
        }
    }
    
    // ── Trim Key Rewards ───────────────────────────────────────────────────
    
    /**
     * Gives a trim key every 2 ELO wins.
     * Tracks wins per player and rewards when threshold is reached.
     */
    private void giveTrimKeyReward(Player winner, UUID winnerUUID) {
        if (winner == null) return;
        
        // Increment win counter
        int wins = eloWinsForKey.getOrDefault(winnerUUID, 0) + 1;
        eloWinsForKey.put(winnerUUID, wins);
        
        // Check if player has reached 2 wins
        if (wins >= 2) {
            // Reset counter
            eloWinsForKey.put(winnerUUID, 0);
            
            // Give trim key
            ItemStack key = com.pvprooms.model.TrimCrate.createKey();
            
            // Try to add to inventory, drop if full
            if (winner.getInventory().firstEmpty() != -1) {
                winner.getInventory().addItem(key);
            } else {
                winner.getWorld().dropItemNaturally(winner.getLocation(), key);
            }
            
            // Notify player
            winner.sendMessage(plugin.prefix() + "§a§l¡RECOMPENSA! §eHas ganado una §6Llave de Crate de Trims §epor 2 victorias en ELO!");
            winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        } else {
            // Notify progress
            winner.sendMessage(plugin.prefix() + "§7Progreso llave de trim: §e" + wins + "§7/§a2 §7victorias");
        }
    }
}
