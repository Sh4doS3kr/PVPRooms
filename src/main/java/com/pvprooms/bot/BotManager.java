package com.pvprooms.bot;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.ArenaTemplate;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Manages practice bot NPCs for PvP training.
 * Bots don't affect ELO or Tier points.
 */
public class BotManager {

    private final PvPRoomsPro plugin;
    private final Map<UUID, BotDuel> activeBotDuels = new HashMap<>();
    private final Map<UUID, NPC> playerBots = new HashMap<>();
    private boolean citizensEnabled = false;

    public BotManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
        checkCitizens();
    }

    private void checkCitizens() {
        if (Bukkit.getPluginManager().getPlugin("Citizens") != null) {
            citizensEnabled = true;
            plugin.getLogger().info("[BotManager] Citizens detectado - Sistema de bots activado");
        } else {
            plugin.getLogger().warning("[BotManager] Citizens no encontrado - Sistema de bots desactivado");
        }
    }

    public boolean isCitizensEnabled() {
        return citizensEnabled;
    }

    /**
     * Starts a practice duel against a bot.
     */
    public boolean startBotDuel(Player player, String kitName, BotDifficulty difficulty) {
        if (!citizensEnabled) {
            player.sendMessage(plugin.prefix() + "§cEl plugin Citizens no está instalado.");
            return false;
        }

        UUID uuid = player.getUniqueId();
        if (activeBotDuels.containsKey(uuid)) {
            player.sendMessage(plugin.prefix() + "§cYa estás en un duelo contra bot.");
            return false;
        }

        if (plugin.getDuelManager().isInDuel(uuid)) {
            player.sendMessage(plugin.prefix() + "§cNo puedes practicar durante un duelo real.");
            return false;
        }

        // Get random arena
        List<ArenaTemplate> arenas = new ArrayList<>();
        for (ArenaTemplate t : plugin.getArenaManager().getAllArenas()) {
            if (t.isFullyConfigured()) arenas.add(t);
        }
        if (arenas.isEmpty()) {
            player.sendMessage(plugin.prefix() + "§cNo hay arenas disponibles.");
            return false;
        }
        ArenaTemplate template = arenas.get(new Random().nextInt(arenas.size()));

        // Create instance world
        String matchId = "bot_" + uuid.toString().substring(0, 8);
        String instanceWorldName = "arena_" + matchId;
        World instanceWorld = plugin.getArenaInstanceManager().createInstance(template, matchId);
        
        if (instanceWorld == null) {
            player.sendMessage(plugin.prefix() + "§cError al crear la arena. Contacta a un admin.");
            return false;
        }

        // Get spawn locations FIRST
        Location spawn1 = template.getSpawn1(instanceWorld);
        Location spawn2 = template.getSpawn2(instanceWorld);
        
        // Force load ALL chunks in the arena area (critical for preventing void falls)
        int minX = Math.min(spawn1.getBlockX(), spawn2.getBlockX()) - 32;
        int maxX = Math.max(spawn1.getBlockX(), spawn2.getBlockX()) + 32;
        int minZ = Math.min(spawn1.getBlockZ(), spawn2.getBlockZ()) - 32;
        int maxZ = Math.max(spawn1.getBlockZ(), spawn2.getBlockZ()) + 32;
        
        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
                instanceWorld.getChunkAt(x, z).load(true);
                instanceWorld.getChunkAt(x, z).setForceLoaded(true);
            }
        }

        // Create bot NPC
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        String botName = getBotName(difficulty);
        NPC bot = registry.createNPC(EntityType.PLAYER, botName);
        
        // Spawn bot at spawn2
        bot.spawn(spawn2);

        // Store bot reference
        playerBots.put(uuid, bot);

        // Give bot the kit equipment
        equipBot(bot, kitName);

        // Create bot duel tracking
        BotDuel botDuel = new BotDuel(uuid, bot.getId(), kitName, difficulty, instanceWorldName, template);
        activeBotDuels.put(uuid, botDuel);
        
        // Small delay to ensure chunks are fully loaded before teleporting
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                endBotDuel(uuid, false);
                return;
            }
            
            // Prepare player (clear inventory, heal, etc.)
            player.getInventory().clear();
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(20f);
            player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
            player.teleport(spawn1);

            // Give player kit
            plugin.getKitManager().applyKit(player, kitName);

            // Start countdown and then combat
            player.sendMessage(plugin.prefix() + "§6⚔ Práctica vs Bot " + difficulty.displayName);
            player.sendMessage(plugin.prefix() + "§7Kit: §e" + kitName + " §7| §cNo afecta ELO/Tier");

            startCountdown(player, bot, botDuel);
        }, 10L); // 0.5 second delay for chunk loading

        return true;
    }

    private void startCountdown(Player player, NPC bot, BotDuel botDuel) {
        new BukkitRunnable() {
            int count = 3;

            @Override
            public void run() {
                if (!player.isOnline() || !bot.isSpawned()) {
                    endBotDuel(player.getUniqueId(), false);
                    cancel();
                    return;
                }

                if (count > 0) {
                    String color = count == 3 ? "§c" : count == 2 ? "§6" : "§a";
                    player.sendTitle(color + "§l" + count, "§7Prepárate...", 0, 25, 5);
                    player.playSound(player.getLocation(), 
                            org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                    count--;
                } else {
                    player.sendTitle("§a§l¡PELEA!", "§7vs Bot " + botDuel.difficulty.displayName, 0, 30, 10);
                    player.playSound(player.getLocation(), 
                            org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
                    
                    // Mark duel as active
                    botDuel.active = true;
                    
                    // Open arena wall (like in regular duels)
                    World instanceWorld = Bukkit.getWorld(botDuel.instanceWorldName);
                    if (instanceWorld != null) {
                        plugin.getWallManager().animateOpen(
                                botDuel.template.getName(), instanceWorld);
                    }
                    
                    // Show bot duel scoreboard
                    showBotDuelScoreboard(player, botDuel);
                    
                    // Start bot AI
                    BotCombatAI ai = new BotCombatAI(plugin, bot, player, 
                            botDuel.difficulty, botDuel.kitName);
                    botDuel.setAI(ai);
                    ai.start();
                    
                    // Start scoreboard update task
                    startScoreboardTask(player, botDuel);
                    
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private String getBotName(BotDifficulty difficulty) {
        // NO color codes - they cause death message errors in Paper
        return switch (difficulty) {
            case EASY -> "Bot_Facil";
            case MEDIUM -> "Bot_Medio";
            case HARD -> "Bot_Dificil";
            case HACKER -> "Bot_Hacker";
        };
    }

    private void equipBot(NPC bot, String kitName) {
        if (!bot.isSpawned()) return;
        
        Player fakePlayer = (Player) bot.getEntity();
        if (fakePlayer == null) return;

        // Apply kit to bot
        plugin.getKitManager().applyKit(fakePlayer, kitName);
        
        // Also set equipment trait for visual
        Equipment equipment = bot.getOrAddTrait(Equipment.class);
        PlayerInventory inv = fakePlayer.getInventory();
        
        equipment.set(Equipment.EquipmentSlot.HAND, inv.getItemInMainHand());
        equipment.set(Equipment.EquipmentSlot.HELMET, inv.getHelmet());
        equipment.set(Equipment.EquipmentSlot.CHESTPLATE, inv.getChestplate());
        equipment.set(Equipment.EquipmentSlot.LEGGINGS, inv.getLeggings());
        equipment.set(Equipment.EquipmentSlot.BOOTS, inv.getBoots());
    }

    /**
     * Ends a bot duel.
     */
    public void endBotDuel(UUID playerUUID, boolean playerWon) {
        BotDuel botDuel = activeBotDuels.remove(playerUUID);
        if (botDuel == null) return;

        // Stop scoreboard task
        stopScoreboardTask(playerUUID);

        // Stop AI
        if (botDuel.ai != null) {
            botDuel.ai.stop();
        }

        // Remove bot NPC
        NPC bot = playerBots.remove(playerUUID);
        if (bot != null) {
            bot.destroy();
        }

        // Restore player
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            // Restore player
            player.getInventory().clear();
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
            plugin.getLobbyManager().giveLobbyItems(player);
            player.teleport(plugin.getLobbySpawn());
            
            // Restore lobby scoreboard
            plugin.getScoreboardManager().restoreLobbyScoreboard(player);
            
            String result = playerWon ? "§a§l¡VICTORIA!" : "§c§lDERROTA";
            player.sendTitle(result, "§7Práctica vs Bot " + botDuel.difficulty.displayName, 10, 60, 20);
            player.sendMessage(plugin.prefix() + result + " §7(Sin cambios en ELO/Tier)");
        }

        // Destroy arena instance
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getArenaInstanceManager().destroyInstance(botDuel.instanceWorldName);
        }, 20L);
    }

    /**
     * Called when a bot dies in combat.
     */
    public void onBotDeath(NPC bot) {
        for (Map.Entry<UUID, NPC> entry : playerBots.entrySet()) {
            if (entry.getValue().getId() == bot.getId()) {
                BotDuel duel = activeBotDuels.get(entry.getKey());
                // Only end if duel is active (after countdown)
                if (duel != null && duel.active) {
                    endBotDuel(entry.getKey(), true);
                }
                return;
            }
        }
    }

    /**
     * Called when player kills the bot - handles it immediately to avoid death message errors.
     */
    public void onBotKilled(NPC bot, Player killer) {
        UUID playerUUID = killer.getUniqueId();
        BotDuel duel = activeBotDuels.get(playerUUID);
        
        if (duel != null && duel.active) {
            // Destroy bot immediately to prevent death animation/message
            if (bot.isSpawned()) {
                bot.despawn();
            }
            bot.destroy();
            
            // End the duel with player victory
            endBotDuel(playerUUID, true);
        }
    }

    /**
     * Called when a player in a bot duel dies.
     */
    public void onPlayerDeath(Player player) {
        if (activeBotDuels.containsKey(player.getUniqueId())) {
            endBotDuel(player.getUniqueId(), false);
        }
    }

    /**
     * Check if player is in a bot duel.
     */
    public boolean isInBotDuel(UUID uuid) {
        return activeBotDuels.containsKey(uuid);
    }

    /**
     * Get the bot NPC for a player's bot duel.
     */
    public NPC getPlayerBot(UUID uuid) {
        return playerBots.get(uuid);
    }

    /** Players who disconnected during a bot duel */
    private final Set<UUID> disconnectedPlayers = new HashSet<>();

    /**
     * Called when a player disconnects during a bot duel.
     */
    public void onPlayerDisconnect(UUID uuid) {
        if (activeBotDuels.containsKey(uuid)) {
            disconnectedPlayers.add(uuid);
            endBotDuel(uuid, false);
        }
    }

    /**
     * Check if player was in a bot duel when they disconnected.
     */
    public boolean wasInBotDuel(UUID uuid) {
        return disconnectedPlayers.contains(uuid);
    }

    /**
     * Clear disconnected player flag after handling reconnect.
     */
    public void clearDisconnectedPlayer(UUID uuid) {
        disconnectedPlayers.remove(uuid);
    }

    /**
     * Clean up all bot duels on plugin disable.
     */
    public void shutdown() {
        for (UUID uuid : new ArrayList<>(activeBotDuels.keySet())) {
            endBotDuel(uuid, false);
        }
        disconnectedPlayers.clear();
    }

    /**
     * Inner class to track bot duel state.
     */
    // ═══════════════════════════════════════════════════════════════════════
    // SCOREBOARD
    // ═══════════════════════════════════════════════════════════════════════

    private final Map<UUID, org.bukkit.scheduler.BukkitTask> scoreboardTasks = new HashMap<>();

    private void showBotDuelScoreboard(Player player, BotDuel botDuel) {
        plugin.getScoreboardManager().showBotDuelScoreboard(player, botDuel);
    }

    private void startScoreboardTask(Player player, BotDuel botDuel) {
        UUID uuid = player.getUniqueId();
        
        // Cancel existing task if any
        if (scoreboardTasks.containsKey(uuid)) {
            scoreboardTasks.get(uuid).cancel();
        }
        
        // Update scoreboard every second
        org.bukkit.scheduler.BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!activeBotDuels.containsKey(uuid)) {
                    cancel();
                    scoreboardTasks.remove(uuid);
                    return;
                }
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) {
                    cancel();
                    scoreboardTasks.remove(uuid);
                    return;
                }
                showBotDuelScoreboard(p, botDuel);
            }
        }.runTaskTimer(plugin, 20L, 20L);
        
        scoreboardTasks.put(uuid, task);
    }

    private void stopScoreboardTask(UUID uuid) {
        if (scoreboardTasks.containsKey(uuid)) {
            scoreboardTasks.get(uuid).cancel();
            scoreboardTasks.remove(uuid);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // /PVPLEAVE SUPPORT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Called when player uses /pvpleave in a bot duel.
     * Returns true if the player was in a bot duel and it was ended.
     */
    public boolean forfeitBotDuel(UUID uuid) {
        if (activeBotDuels.containsKey(uuid)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(plugin.prefix() + "§cTe has rendido en el duelo contra el bot.");
            }
            endBotDuel(uuid, false);
            return true;
        }
        return false;
    }

    public static class BotDuel {
        public final UUID playerUUID;
        public final int botNpcId;
        public final String kitName;
        public final BotDifficulty difficulty;
        public final String instanceWorldName;
        public final ArenaTemplate template;
        public BotCombatAI ai;
        public boolean active = false;
        public long startTimeMillis = System.currentTimeMillis();

        public BotDuel(UUID playerUUID, int botNpcId, String kitName, 
                       BotDifficulty difficulty, String instanceWorldName, 
                       ArenaTemplate template) {
            this.playerUUID = playerUUID;
            this.botNpcId = botNpcId;
            this.kitName = kitName;
            this.difficulty = difficulty;
            this.instanceWorldName = instanceWorldName;
            this.template = template;
        }

        public void setAI(BotCombatAI ai) {
            this.ai = ai;
        }

        public long getElapsedSeconds() {
            return (System.currentTimeMillis() - startTimeMillis) / 1000;
        }
    }
}
