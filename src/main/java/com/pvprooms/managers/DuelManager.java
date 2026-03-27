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

    public DuelManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ── Duel creation ──────────────────────────────────────────────────────

    /**
     * Initiates a duel between two players using a given kit.
     * Called by QueueManager once two players are matched.
     */
    public void startDuel(UUID uuid1, UUID uuid2, String kitName) {
        Player p1 = Bukkit.getPlayer(uuid1);
        Player p2 = Bukkit.getPlayer(uuid2);

        if (p1 == null || p2 == null) return;

        // Pick a random fully-configured arena template
        ArenaTemplate template = plugin.getArenaManager().getRandomArena();
        if (template == null) {
            p1.sendMessage(plugin.prefix() + "§cNo arenas are available. Ask an admin to configure one.");
            p2.sendMessage(plugin.prefix() + "§cNo arenas are available. Ask an admin to configure one.");
            return;
        }

        String matchId = Long.toHexString(System.currentTimeMillis());
        String instanceWorldName = plugin.getConfig().getString("arenas.instance-prefix", "pvp_match_") + matchId;

        // Clone arena template
        World instanceWorld = plugin.getArenaInstanceManager().createInstance(template, matchId);
        if (instanceWorld == null) {
            p1.sendMessage(plugin.prefix() + "§cFailed to create arena instance. Contact an admin.");
            p2.sendMessage(plugin.prefix() + "§cFailed to create arena instance. Contact an admin.");
            return;
        }

        Duel duel = new Duel(uuid1, uuid2, kitName, instanceWorldName, template);
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

        // Notify
        p1.sendMessage(plugin.prefix() + "§aMatch found! vs §e" + p2.getName() + " §7[Kit: §f" + kitName + "§7]");
        p2.sendMessage(plugin.prefix() + "§aMatch found! vs §e" + p1.getName() + " §7[Kit: §f" + kitName + "§7]");

        // Start countdown
        startCountdown(duel, instanceWorld);
    }

    // ── Countdown ─────────────────────────────────────────────────────────

    private void startCountdown(Duel duel, World world) {
        int seconds = plugin.getConfig().getInt("duels.countdown", 5);

        int taskId = new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                Player p1 = Bukkit.getPlayer(duel.getPlayer1());
                Player p2 = Bukkit.getPlayer(duel.getPlayer2());

                if (p1 == null || p2 == null) {
                    cancel();
                    endDuel(duel, p1 != null ? duel.getPlayer1() : duel.getPlayer2(), "opponent disconnected");
                    return;
                }

                if (remaining > 0) {
                    // Freeze players during countdown
                    p1.setVelocity(p1.getVelocity().zero());
                    p2.setVelocity(p2.getVelocity().zero());

                    sendTitle(p1, "§e" + remaining, "§7Get ready!");
                    sendTitle(p2, "§e" + remaining, "§7Get ready!");
                    p1.sendMessage(plugin.prefix() + "§eMatch starting in §6" + remaining + "§e...");
                    p2.sendMessage(plugin.prefix() + "§eMatch starting in §6" + remaining + "§e...");
                    remaining--;
                } else {
                    // Fight!
                    duel.setState(Duel.State.FIGHTING);
                    duel.setStartTimeMillis(System.currentTimeMillis());
                    sendTitle(p1, "§c§lFIGHT!", "");
                    sendTitle(p2, "§c§lFIGHT!", "");
                    p1.sendMessage(plugin.prefix() + "§cFight!");
                    p2.sendMessage(plugin.prefix() + "§cFight!");
                    plugin.getScoreboardManager().updateDuelScoreboard(p1, duel);
                    plugin.getScoreboardManager().updateDuelScoreboard(p2, duel);
                    startDurationTimer(duel);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L).getTaskId();

        duel.setCountdownTask(taskId);
        plugin.getScoreboardManager().updateDuelScoreboard(
                Bukkit.getPlayer(duel.getPlayer1()), duel);
        plugin.getScoreboardManager().updateDuelScoreboard(
                Bukkit.getPlayer(duel.getPlayer2()), duel);
    }

    /** Starts a max-duration timer that ends the duel in a draw if time runs out. */
    private void startDurationTimer(Duel duel) {
        int maxDuration = plugin.getConfig().getInt("duels.max-duration", 300);
        if (maxDuration <= 0) return;

        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (duel.getState() != Duel.State.FIGHTING) { cancel(); return; }
                Player p1 = Bukkit.getPlayer(duel.getPlayer1());
                Player p2 = Bukkit.getPlayer(duel.getPlayer2());
                if (p1 != null) p1.sendMessage(plugin.prefix() + "§eTime limit reached. Match drawn.");
                if (p2 != null) p2.sendMessage(plugin.prefix() + "§eTime limit reached. Match drawn.");
                endDuel(duel, null, "time limit");
            }
        }.runTaskLater(plugin, maxDuration * 20L).getTaskId();

        duel.setDurationTask(taskId);
    }

    // ── Duel end ───────────────────────────────────────────────────────────

    /**
     * Ends a duel. winnerUUID may be null for a draw.
     * Handles ELO updates, cleanup, and world destruction.
     */
    public void endDuel(Duel duel, UUID winnerUUID, String reason) {
        if (duel.getState() == Duel.State.ENDED) return;
        duel.setState(Duel.State.ENDED);
        duel.setWinner(winnerUUID);

        // Cancel pending tasks
        if (duel.getCountdownTask()  != -1) Bukkit.getScheduler().cancelTask(duel.getCountdownTask());
        if (duel.getDurationTask()   != -1) Bukkit.getScheduler().cancelTask(duel.getDurationTask());
        if (duel.getScoreboardTask() != -1) Bukkit.getScheduler().cancelTask(duel.getScoreboardTask());

        Player p1 = Bukkit.getPlayer(duel.getPlayer1());
        Player p2 = Bukkit.getPlayer(duel.getPlayer2());
        UUID loserUUID = winnerUUID == null ? null
                : winnerUUID.equals(duel.getPlayer1()) ? duel.getPlayer2() : duel.getPlayer1();

        // ELO update (only if not a draw)
        if (winnerUUID != null && loserUUID != null) {
            Player winner = Bukkit.getPlayer(winnerUUID);
            Player loser  = Bukkit.getPlayer(loserUUID);
            String winnerName = winner != null ? winner.getName() : winnerUUID.toString();
            String loserName  = loser  != null ? loser.getName()  : loserUUID.toString();

            int[] changes = plugin.getEloManager().processResult(
                    winnerUUID, winnerName, loserUUID, loserName);

            announceResult(p1, p2, winnerUUID, changes[0], changes[1]);
        }

        // Remove spectators
        for (UUID specUUID : duel.getSpectators()) {
            Player spec = Bukkit.getPlayer(specUUID);
            if (spec != null) {
                spec.sendMessage(plugin.prefix() + "§7The duel has ended. Returning you to lobby.");
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

        // Remove scoreboards
        if (p1 != null) plugin.getScoreboardManager().clearScoreboard(p1);
        if (p2 != null) plugin.getScoreboardManager().clearScoreboard(p2);

        // Destroy arena instance (delayed 1 tick to let teleports process)
        String worldName = duel.getInstanceWorldName();
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getArenaInstanceManager().destroyInstance(worldName);
            }
        }.runTaskLater(plugin, 5L);
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

    private void announceResult(Player p1, Player p2, UUID winnerUUID, int winGain, int lossChange) {
        Player winner = Bukkit.getPlayer(winnerUUID);
        Player loser  = p1 != null && p1.getUniqueId().equals(winnerUUID) ? p2 : p1;

        String winnerName = winner != null ? winner.getName() : "Unknown";
        String loserName  = loser  != null ? loser.getName()  : "Unknown";

        if (winner != null) {
            int newElo = plugin.getEloManager().getElo(winnerUUID);
            winner.sendMessage(plugin.prefix() + "§aYou won against §e" + loserName
                    + "§a! §7(+" + winGain + " ELO → §e" + newElo + "§7)");
            sendTitle(winner, "§a§lYOU WIN!", "§7+" + winGain + " ELO");
        }
        if (loser != null) {
            int newElo = plugin.getEloManager().getElo(loser.getUniqueId());
            loser.sendMessage(plugin.prefix() + "§cYou lost against §e" + winnerName
                    + "§c. §7(-" + lossChange + " ELO → §e" + newElo + "§7)");
            sendTitle(loser, "§c§lYOU LOST", "§7-" + lossChange + " ELO");
        }

        // Spectator announcements handled when the duel ends and spectators are removed
    }

    // ── Query helpers ──────────────────────────────────────────────────────

    public boolean isInDuel(UUID uuid) {
        return playerDuelMap.containsKey(uuid);
    }

    public Duel getDuelByPlayer(UUID uuid) {
        UUID duelId = playerDuelMap.get(uuid);
        return duelId != null ? activeDuels.get(duelId) : null;
    }

    public Duel getDuelById(UUID duelId) {
        return activeDuels.get(duelId);
    }

    public Collection<Duel> getActiveDuels() {
        return Collections.unmodifiableCollection(activeDuels.values());
    }

    public int getActiveDuelCount() {
        return activeDuels.size();
    }

    // ── Utility ────────────────────────────────────────────────────────────

    private void sendTitle(Player player, String title, String subtitle) {
        player.sendTitle(
                ChatColor.translateAlternateColorCodes('&', title),
                ChatColor.translateAlternateColorCodes('&', subtitle),
                10, 40, 10
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
