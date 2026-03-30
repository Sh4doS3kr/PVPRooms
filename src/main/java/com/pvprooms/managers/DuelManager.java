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
    private final Map<UUID, PlayerSnapshot> inventorySnapshots = new HashMap<>();

    /** Players frozen during countdown (only arenas without walls) */
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();

    public DuelManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ── Duel creation ──────────────────────────────────────────────────────

    /**
     * Initiates a duel between two players using a given kit.
     * Called by QueueManager once two players are matched.
     */
    /** Overload without bo3 — keeps ELO duels unchanged. */
    public void startDuel(UUID uuid1, UUID uuid2, String kitName) {
        startDuel(uuid1, uuid2, kitName, false);
    }

    public void startDuel(UUID uuid1, UUID uuid2, String kitName, boolean bo3) {
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

        // ID único de partida (UUID corto para el nombre del mundo)
        String matchId = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String instanceWorldName = plugin.getConfig().getString("arenas.instance-prefix", "pvp_match_") + matchId;

        // Clonar mundo plantilla — cada pareja obtiene su propia copia
        World instanceWorld = plugin.getArenaInstanceManager().createInstance(template, matchId);
        if (instanceWorld == null) {
            p1.sendMessage(plugin.prefix() + "§cError al crear la instancia de arena. Contacta a un admin.");
            p2.sendMessage(plugin.prefix() + "§cError al crear la instancia de arena. Contacta a un admin.");
            return;
        }

        Duel duel = new Duel(uuid1, uuid2, kitName, instanceWorldName, template);
        duel.setBo3(bo3);
        activeDuels.put(duel.getId(), duel);
        playerDuelMap.put(uuid1, duel.getId());
        playerDuelMap.put(uuid2, duel.getId());

        // Save inventories before teleport
        saveSnapshot(p1);
        saveSnapshot(p2);

        // Prepare players
        preparePlayer(p1);
        preparePlayer(p2);

        // Teleport to instance spawns
        p1.teleport(template.getSpawn1(instanceWorld));
        p2.teleport(template.getSpawn2(instanceWorld));

        // Give kit
        plugin.getKitManager().applyKit(p1, kitName);
        plugin.getKitManager().applyKit(p2, kitName);

        // Quitar scoreboard de cola antes de empezar
        plugin.getScoreboardManager().clearScoreboard(p1);
        plugin.getScoreboardManager().clearScoreboard(p2);

        // Notificar emparejamiento
        String modeTag = bo3 ? " §8[§bBO3§8]" : "";
        p1.sendMessage(plugin.prefix() + "§a¡Partida encontrada! §8» §evs §f" + p2.getName() + " §8[Kit: §e" + kitName + "§8]" + modeTag);
        p2.sendMessage(plugin.prefix() + "§a¡Partida encontrada! §8» §evs §f" + p1.getName() + " §8[Kit: §e" + kitName + "§8]" + modeTag);

        // Start countdown
        startCountdown(duel, instanceWorld);
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
        // BO3 interception: a single-round death should start the next round
        if (duel.isBo3()
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

        // Rating update — BO3 (TIER mode) uses TierPoints; normal duels use ELO
        if (winnerUUID != null && loserUUID != null) {
            // Record stats for leaderboards
            Player winner = Bukkit.getPlayer(winnerUUID);
            Player loser  = Bukkit.getPlayer(loserUUID);
            String winnerName = winner != null ? winner.getName() : winnerUUID.toString();
            String loserName  = loser  != null ? loser.getName()  : loserUUID.toString();
            plugin.getStatsManager().recordWin(winnerUUID, winnerName);
            plugin.getStatsManager().recordLoss(loserUUID, loserName);
            plugin.getStatsManager().recordKill(winnerUUID, winnerName);
            plugin.getStatsManager().recordDeath(loserUUID, loserName);
            
            if (duel.isBo3()) {
                // TIER mode: update tier points, skip ELO
                plugin.getTierManager().recordResult(winnerUUID, loserUUID, duel.getKitName());
                announceResultTier(p1, p2, winnerUUID, loserUUID, duel.getKitName());
            } else {
                // ELO mode: update ELO, then sync TierManager so both systems agree
                int[] changes = plugin.getEloManager().processResult(
                        winnerUUID, winnerName, loserUUID, loserName);
                // Sync TierManager from new ELO — keeps web page and scoreboard consistent
                plugin.getTierManager().syncFromElo(winnerUUID, duel.getKitName(),
                        plugin.getEloManager().getElo(winnerUUID));
                plugin.getTierManager().syncFromElo(loserUUID, duel.getKitName(),
                        plugin.getEloManager().getElo(loserUUID));
                announceResult(p1, p2, winnerUUID, loserUUID, duel.getKitName(), changes[0], changes[1]);
            }
        }

        // Remove spectators
        for (UUID specUUID : duel.getSpectators()) {
            Player spec = Bukkit.getPlayer(specUUID);
            if (spec != null) {
                spec.sendMessage(plugin.prefix() + "§7El duelo ha terminado. Volviendo al lobby...");
                plugin.getScoreboardManager().restoreLobbyScoreboard(spec);
                restoreSpectator(spec);
                spec.teleport(plugin.getLobbySpawn());
            }
        }

        // Restore and teleport combatants
        Location lobby = plugin.getLobbySpawn();
        if (p1 != null) { restorePlayer(p1); p1.teleport(lobby); }
        if (p2 != null) { restorePlayer(p2); p2.teleport(lobby); }

        // Remove from tracking maps
        activeDuels.remove(duel.getId());
        playerDuelMap.remove(duel.getPlayer1());
        playerDuelMap.remove(duel.getPlayer2());

        // Restaurar scoreboard de lobby
        if (p1 != null) plugin.getScoreboardManager().restoreLobbyScoreboard(p1);
        if (p2 != null) plugin.getScoreboardManager().restoreLobbyScoreboard(p2);

        // Destroy arena instance (delayed 1 tick to let teleports process)
        String worldName = duel.getInstanceWorldName();
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getArenaInstanceManager().destroyInstance(worldName);
            }
        }.runTaskLater(plugin, 5L);
    }

    // ── BO3 round logic ────────────────────────────────────────────────────

    /**
     * Called after a round ends in a BO3 duel.
     * Increments the winner's round count, then either starts the next round
     * or finalises the match if someone has reached 2 wins.
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

        // Round result chat message
        String roundMsg = plugin.prefix()
                + "§e⚔ Ronda " + round + ": §6§l" + winnerName
                + " §egana! §8[§a" + w1 + "§7-§c" + w2 + "§8]";
        if (p1 != null) p1.sendMessage(roundMsg);
        if (p2 != null) p2.sendMessage(roundMsg);

        // Step 1: teleport both players to their spawns immediately
        World worldNow = Bukkit.getWorld(duel.getInstanceWorldName());
        if (worldNow != null) {
            if (p1 != null) p1.teleport(duel.getArenaTemplate().getSpawn1(worldNow));
            if (p2 != null) p2.teleport(duel.getArenaTemplate().getSpawn2(worldNow));
        }

        // Step 2: 0.2s later show the score title (X-X)
        final int fw1 = w1, fw2 = w2;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player rp1 = Bukkit.getPlayer(duel.getPlayer1());
            Player rp2 = Bukkit.getPlayer(duel.getPlayer2());
            String topLine = "§a" + fw1 + " §8▶ §c" + fw2;
            String subLine = "§f" + winnerName + " §7gana la ronda";
            if (rp1 != null) sendTitle(rp1, topLine, subLine, 80);
            if (rp2 != null) sendTitle(rp2, topLine, subLine, 80);
        }, 4L); // 4 ticks ≈ 0.2s

        // Check for match winner (first to 2)
        UUID matchWinner = w1 >= 2 ? duel.getPlayer1() : (w2 >= 2 ? duel.getPlayer2() : null);
        if (matchWinner != null) {
            // Full match decided — run cleanup after titles are visible
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    endDuel(duel, matchWinner, "bo3_finished"), 40L);
            return;
        }

        // ── Next round ────────────────────────────────────────────────────
        // Cancel running duration timer
        if (duel.getDurationTask() != -1) {
            Bukkit.getScheduler().cancelTask(duel.getDurationTask());
            duel.setDurationTask(-1);
        }

        World world = Bukkit.getWorld(duel.getInstanceWorldName());
        if (world != null) plugin.getWallManager().animateClose(duel.getArenaTemplate().getName(), world);

        // Prepare and start next countdown after title has had time to display
        duel.setState(Duel.State.COUNTDOWN);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player rp1 = Bukkit.getPlayer(duel.getPlayer1());
            Player rp2 = Bukkit.getPlayer(duel.getPlayer2());
            World w    = Bukkit.getWorld(duel.getInstanceWorldName());
            if (rp1 == null || rp2 == null || w == null) {
                UUID winner = rp1 != null ? duel.getPlayer1() : (rp2 != null ? duel.getPlayer2() : null);
                endDuel(duel, winner, "disconnect");
                return;
            }
            preparePlayer(rp1);
            preparePlayer(rp2);
            rp1.teleport(duel.getArenaTemplate().getSpawn1(w));
            rp2.teleport(duel.getArenaTemplate().getSpawn2(w));
            plugin.getKitManager().applyKit(rp1, duel.getKitName());
            plugin.getKitManager().applyKit(rp2, duel.getKitName());
            duel.setStartTimeMillis(System.currentTimeMillis());
            startCountdown(duel, w);
        }, 20L); // 1s delay lets score title display before next countdown
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
        if (duel == null || duel.getState() != Duel.State.FIGHTING) return false;

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

    private void restorePlayer(Player player) {
        PlayerSnapshot snap = inventorySnapshots.remove(player.getUniqueId());
        if (snap != null) snap.restore(player);
        healPlayer(player);
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
        var maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
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
            sendTitle(winner, "§a§l¡VICTORIA!", wTier.colour + wTier.displayName
                    + " §7+" + winGain + " ELO");
        }
        if (loser != null) {
            int newElo = plugin.getEloManager().getElo(loserUUID);
            loser.sendMessage(plugin.prefix()
                    + "§c§lPERDISTE §cvs §e" + winnerName
                    + "  §7(-" + lossChange + " ELO → §e" + newElo + "§7)");
            loser.sendMessage(plugin.prefix()
                    + "§7Rango: " + lTier.colour + "§l" + lTier.displayName
                    + "  §8| §7Kit: §f" + kitName);
            sendTitle(loser, "§c§lDERROTA", lTier.colour + lTier.displayName
                    + " §7-" + lossChange + " ELO");
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
                    + "  §8» §7" + pts + " pts");
            winner.sendMessage(plugin.prefix() + "§7Insignia: " + wTitle.formatted());
            sendTitle(winner, "§a§l¡VICTORIA!", wTier.colour + wTier.displayName, 80);
        }
        if (loser != null) {
            int pts = plugin.getTierManager().getPoints(loserUUID, kitName);
            loser.sendMessage(plugin.prefix()
                    + "§c§lDERROTA §cvs §e" + winnerName
                    + "  §8[§bTIER §e" + kitName + "§8]  "
                    + lTier.colour + lTier.displayName
                    + "  §8» §7" + pts + " pts");
            loser.sendMessage(plugin.prefix() + "§7Insignia: " + lTitle.formatted());
            sendTitle(loser, "§c§lDERROTA", lTier.colour + lTier.displayName, 80);
        }
    }

    // ── Query helpers ──────────────────────────────────────────────────────

    /** Returns true if this player is currently frozen during a countdown. */
    public boolean isFrozen(UUID uuid) {
        return frozenPlayers.contains(uuid);
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
            if (duel.getInstanceWorldName().equals(worldName)) return duel;
        }
        return null;
    }

    public int getActiveDuelCount()            { return activeDuels.size(); }
    public Collection<Duel> getActiveDuels()   { return activeDuels.values(); }

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
}
