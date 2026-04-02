package com.pvprooms.bot;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.ArenaTemplate;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
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
    private AdaptiveAI adaptiveAI;

    public BotManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
        this.adaptiveAI = new AdaptiveAI(plugin);
        checkCitizens();
    }
    
    public AdaptiveAI getAdaptiveAI() {
        return adaptiveAI;
    }
    
    public void saveAdaptiveData() {
        if (adaptiveAI != null) adaptiveAI.save();
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

        // Use arena connected to kit (same system as regular duels)
        String connectedArena = plugin.getKitManager().getConnectedArena(kitName);
        if (connectedArena != null) connectedArena = connectedArena.trim();
        ArenaTemplate template = null;
        
        if (connectedArena != null && !connectedArena.isEmpty()) {
            template = plugin.getArenaManager().getArena(connectedArena);
            if (template == null || !template.isFullyConfigured()) {
                player.sendMessage(plugin.prefix() + "§e⚠ Arena vinculada al kit no disponible. Usando arena aleatoria...");
                template = null;
            }
        }
        
        // Fallback to random arena if no connected arena
        if (template == null) {
            List<ArenaTemplate> arenas = new ArrayList<>();
            for (ArenaTemplate t : plugin.getArenaManager().getAllArenas()) {
                if (t.isFullyConfigured()) arenas.add(t);
            }
            if (arenas.isEmpty()) {
                player.sendMessage(plugin.prefix() + "§cNo hay arenas disponibles.");
                return false;
            }
            template = arenas.get(new Random().nextInt(arenas.size()));
        }

        // Create instance world
        String matchId = "bot_" + uuid.toString().substring(0, 8);
        // World name must match ArenaInstanceManager's naming: prefix + matchId
        String instancePrefix = plugin.getConfig().getString("arenas.instance-prefix", "pvp_match_");
        String instanceWorldName = instancePrefix + matchId;
        World instanceWorld = plugin.getArenaInstanceManager().createInstance(template, matchId);
        
        if (instanceWorld == null) {
            player.sendMessage(plugin.prefix() + "§cError al crear la arena. Contacta a un admin.");
            return false;
        }

        // Get spawn locations from template for the instance world
        Location spawn1 = template.getSpawn1(instanceWorld);
        Location spawn2 = template.getSpawn2(instanceWorld);
        
        // Validate spawn locations
        if (spawn1 == null || spawn2 == null) {
            player.sendMessage(plugin.prefix() + "§cError: Spawns no configurados en la arena.");
            plugin.getArenaInstanceManager().destroyInstance(instanceWorldName);
            return false;
        }
        
        plugin.getLogger().info("[BotDuel] Arena: " + template.getName() + 
                ", Spawn1: " + spawn1.getBlockX() + "," + spawn1.getBlockY() + "," + spawn1.getBlockZ() +
                ", Spawn2: " + spawn2.getBlockX() + "," + spawn2.getBlockY() + "," + spawn2.getBlockZ());
        
        // Force load chunks at spawn points FIRST (most critical)
        spawn1.getChunk().load(true);
        spawn1.getChunk().setForceLoaded(true);
        spawn2.getChunk().load(true);
        spawn2.getChunk().setForceLoaded(true);
        
        // Load surrounding chunks for the arena area
        int minX = Math.min(spawn1.getBlockX(), spawn2.getBlockX()) - 16;
        int maxX = Math.max(spawn1.getBlockX(), spawn2.getBlockX()) + 16;
        int minZ = Math.min(spawn1.getBlockZ(), spawn2.getBlockZ()) - 16;
        int maxZ = Math.max(spawn1.getBlockZ(), spawn2.getBlockZ()) + 16;
        
        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
                Chunk chunk = instanceWorld.getChunkAt(x, z);
                chunk.load(true);
                chunk.setForceLoaded(true);
            }
        }

        // Store spawn locations for later use (capture them now while world is valid)
        final Location finalSpawn1 = spawn1.clone();
        final Location finalSpawn2 = spawn2.clone();

        // Create bot duel tracking FIRST (bot ID will be set later)
        BotDuel botDuel = new BotDuel(uuid, -1, kitName, difficulty, instanceWorldName, template);
        activeBotDuels.put(uuid, botDuel);

        // Delay bot and player spawn to ensure chunks are fully loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                endBotDuel(uuid, false);
                return;
            }
            
            // Re-validate world exists
            World world = Bukkit.getWorld(instanceWorldName);
            if (world == null) {
                player.sendMessage(plugin.prefix() + "§cError: El mundo de la arena no existe.");
                endBotDuel(uuid, false);
                return;
            }
            
            // Update spawn locations with current world reference
            Location playerSpawn = new Location(world, finalSpawn1.getX(), finalSpawn1.getY(), finalSpawn1.getZ(),
                    finalSpawn1.getYaw(), finalSpawn1.getPitch());
            Location botSpawn = new Location(world, finalSpawn2.getX(), finalSpawn2.getY(), finalSpawn2.getZ(),
                    finalSpawn2.getYaw(), finalSpawn2.getPitch());
            
            // Force load spawn chunks again
            playerSpawn.getChunk().load(true);
            botSpawn.getChunk().load(true);
            
            // Create and spawn bot NPC
            NPCRegistry registry = CitizensAPI.getNPCRegistry();
            String botName = getBotName(difficulty);
            NPC bot = registry.createNPC(EntityType.PLAYER, botName);
            
            // Spawn at a temporary safe location first, then teleport to exact position
            // This prevents Citizens from adjusting Y to "highest safe block"
            bot.spawn(botSpawn);
            
            // Store bot reference
            playerBots.put(uuid, bot);
            botDuel.setBotNpcId(bot.getId());
            
            // Give bot the kit equipment
            equipBot(bot, kitName);
            
            // Force teleport to exact spawn position AFTER Citizens finishes spawn logic
            // Using a 2-tick delay ensures the entity is fully initialized
            final Location exactSpawn = botSpawn.clone();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (bot.isSpawned() && bot.getEntity() != null) {
                    bot.getEntity().teleport(exactSpawn);
                    bot.getEntity().setGravity(true);
                    if (bot.getEntity() instanceof Player botPlayer) {
                        botPlayer.setAllowFlight(false);
                        botPlayer.setFlying(false);
                    }
                    plugin.getLogger().info("[BotDuel] Bot force-teleported to exact spawn: " + 
                        exactSpawn.getBlockX() + "," + exactSpawn.getBlockY() + "," + exactSpawn.getBlockZ());
                }
            }, 2L);
            
            // Prepare player
            player.getInventory().clear();
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(20f);
            player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
            
            // Teleport player
            player.teleport(playerSpawn);

            // Give player kit
            plugin.getKitManager().applyKit(player, kitName);

            // Start countdown and then combat
            player.sendMessage(plugin.prefix() + "§6⚔ Práctica vs Bot " + difficulty.displayName);
            player.sendMessage(plugin.prefix() + "§7Kit: §e" + kitName + " §7| §cNo afecta ELO/Tier");

            startCountdown(player, bot, botDuel, playerSpawn, botSpawn);
        }, 40L); // 2 second delay for proper chunk loading

        return true;
    }

    private void startCountdown(Player player, NPC bot, BotDuel botDuel, Location playerSpawn, Location botSpawn) {
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
                    
                    // Open arena walls
                    World instanceWorld = Bukkit.getWorld(botDuel.instanceWorldName);
                    if (instanceWorld != null) {
                        String arenaName = botDuel.template.getName();
                        if (plugin.getWallManager().hasWalls(arenaName)) {
                            plugin.getWallManager().animateOpen(arenaName, instanceWorld);
                        } else {
                            // No WallManager config – force-clear barrier/glass blocks
                            // between the two spawn points so player and bot aren't trapped
                            forceClearWalls(instanceWorld, playerSpawn, botSpawn);
                        }
                    }
                    
                    // Show bot duel scoreboard
                    showBotDuelScoreboard(player, botDuel);
                    
                    // Start bot AI (with adaptive parameters if ADAPTIVE mode)
                    BotCombatAI ai;
                    if (botDuel.difficulty == BotDifficulty.ADAPTIVE) {
                        // Use learned player profile
                        AdaptiveAI.BotParameters params = adaptiveAI.toBotParameters(player.getUniqueId());
                        ai = new BotCombatAI(plugin, bot, player, botDuel.difficulty, botDuel.kitName, params);
                        adaptiveAI.startLearningSession(player);
                    } else {
                        ai = new BotCombatAI(plugin, bot, player, botDuel.difficulty, botDuel.kitName, null);
                    }
                    botDuel.setAI(ai);
                    ai.start();
                    
                    // Start scoreboard update task
                    startScoreboardTask(player, botDuel);
                    
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Fallback for arenas with no WallManager config.
     * Scans the bounding box around both spawns and removes any solid
     * wall-type blocks (barriers, glass variants, iron bars, chain, etc.)
     * that could trap players or the bot.
     */
    private void forceClearWalls(World world, Location spawn1, Location spawn2) {
        // Typical wall materials used in PvP arenas
        Set<org.bukkit.Material> wallMaterials = Set.of(
            org.bukkit.Material.BARRIER,
            org.bukkit.Material.GLASS,
            org.bukkit.Material.GLASS_PANE,
            org.bukkit.Material.IRON_BARS,
            org.bukkit.Material.WHITE_STAINED_GLASS,
            org.bukkit.Material.WHITE_STAINED_GLASS_PANE,
            org.bukkit.Material.GRAY_STAINED_GLASS,
            org.bukkit.Material.GRAY_STAINED_GLASS_PANE,
            org.bukkit.Material.BLACK_STAINED_GLASS,
            org.bukkit.Material.BLACK_STAINED_GLASS_PANE,
            org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS,
            org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS_PANE
        );

        int minX = Math.min(spawn1.getBlockX(), spawn2.getBlockX()) - 8;
        int maxX = Math.max(spawn1.getBlockX(), spawn2.getBlockX()) + 8;
        int minY = Math.min(spawn1.getBlockY(), spawn2.getBlockY()) - 1;
        int maxY = Math.max(spawn1.getBlockY(), spawn2.getBlockY()) + 6;
        int minZ = Math.min(spawn1.getBlockZ(), spawn2.getBlockZ()) - 8;
        int maxZ = Math.max(spawn1.getBlockZ(), spawn2.getBlockZ()) + 8;

        int cleared = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                    if (wallMaterials.contains(block.getType())) {
                        block.setType(org.bukkit.Material.AIR, false);
                        cleared++;
                    }
                }
            }
        }
        if (cleared > 0) {
            plugin.getLogger().info("[BotDuel] Cleared " + cleared + " wall block(s) in arena (no WallManager config).");
        }
    }

    private String getBotName(BotDifficulty difficulty) {
        // NO color codes - they cause death message errors in Paper
        return switch (difficulty) {
            case EASY -> "Bot_Facil";
            case MEDIUM -> "Bot_Medio";
            case HARD -> "Bot_Dificil";
            case HACKER -> "Bot_Hacker";
            case ADAPTIVE -> "Bot_Adaptivo";
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

        // Close arena walls (same as regular duels)
        World instanceWorld = Bukkit.getWorld(botDuel.instanceWorldName);
        if (instanceWorld != null && botDuel.template != null) {
            plugin.getWallManager().animateClose(botDuel.template.getName(), instanceWorld);
        }

        // Restore player
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            // Restore player
            player.getInventory().clear();
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
            var atkSpeed = player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_SPEED);
            if (atkSpeed != null) atkSpeed.setBaseValue(4.0);
            plugin.getLobbyManager().giveLobbyItems(player);
            player.teleport(plugin.getLobbySpawn());
            
            // Restore lobby scoreboard
            plugin.getScoreboardManager().restoreLobbyScoreboard(player);
            
            String result = playerWon ? "§a§l¡VICTORIA!" : "§c§lDERROTA";
            player.sendTitle(result, "§7Práctica vs Bot " + botDuel.difficulty.displayName, 10, 60, 20);
            player.sendMessage(plugin.prefix() + result + " §7(Sin cambios en ELO/Tier)");
        }

        // Destroy arena instance (delay to allow wall animation)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getArenaInstanceManager().destroyInstance(botDuel.instanceWorldName);
        }, 60L);
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
     * Get the bot duel for a player.
     */
    public BotDuel getBotDuel(UUID uuid) {
        return activeBotDuels.get(uuid);
    }

    /**
     * Returns true if the player is in a bot duel that hasn't started yet (countdown phase).
     */
    public boolean isInBotCountdown(UUID uuid) {
        BotDuel duel = activeBotDuels.get(uuid);
        return duel != null && !duel.active;
    }

    /**
     * Get the bot NPC for a player's bot duel.
     */
    public NPC getPlayerBot(UUID uuid) {
        return playerBots.get(uuid);
    }

    /**
     * Get all currently active bot NPCs (for filtering broadcast messages).
     */
    public Collection<NPC> getAllActiveBots() {
        return playerBots.values();
    }

    /**
     * Find a bot duel by the bot NPC.
     */
    public BotDuel getBotDuelByBot(NPC bot) {
        if (bot == null) return null;
        for (Map.Entry<UUID, NPC> entry : playerBots.entrySet()) {
            if (entry.getValue().getId() == bot.getId()) {
                return activeBotDuels.get(entry.getKey());
            }
        }
        return null;
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
        public int botNpcId;  // Not final - set after bot creation
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
        
        public void setBotNpcId(int id) {
            this.botNpcId = id;
        }

        public long getElapsedSeconds() {
            return (System.currentTimeMillis() - startTimeMillis) / 1000;
        }
    }
}
