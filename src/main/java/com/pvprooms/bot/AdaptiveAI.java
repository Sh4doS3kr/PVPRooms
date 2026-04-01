package com.pvprooms.bot;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptive AI system that learns from player behavior.
 * 
 * Tracks and learns:
 * - Attack patterns (timing, frequency, crit rate)
 * - Movement patterns (strafing, W-tapping, jumping)
 * - Healing behavior (when they heal, what they use)
 * - Combo patterns (how they chain attacks)
 * - Defensive behavior (blocking, retreating)
 * - Weapon preferences
 * 
 * The bot then mirrors these patterns to fight like the player.
 */
public class AdaptiveAI {

    private final PvPRoomsPro plugin;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final File dataFile;

    public AdaptiveAI(PvPRoomsPro plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "adaptive_ai.yml");
        load();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PLAYER PROFILE - Stores learned behavior
    // ══════════════════════════════════════════════════════════════════════════

    public static class PlayerProfile {
        // Attack patterns
        public double avgAttackInterval = 500;      // ms between attacks
        public double critRate = 0.2;               // % of attacks that are crits
        public double comboLength = 3;              // average hits before reset
        public double attackAccuracy = 0.5;         // % of swings that hit
        
        // Movement patterns
        public double strafeFrequency = 0.3;        // how often they strafe
        public double wTapFrequency = 0.2;          // how often they W-tap
        public double jumpFrequency = 0.15;         // how often they jump in combat
        public double sprintFrequency = 0.7;        // how often they sprint
        public double sneakFrequency = 0.1;         // how often they sneak (reduce KB)
        
        // Healing behavior
        public double healThreshold = 0.4;          // HP% when they start healing
        public double potionUseRate = 0.3;          // how often they use pots vs gapples
        
        // Defensive behavior
        public double blockFrequency = 0.2;         // how often they shield
        public double retreatThreshold = 0.25;      // HP% when they retreat
        
        // Aggression
        public double aggressionLevel = 0.5;        // 0 = passive, 1 = very aggressive
        public double chaseDistance = 6.0;          // how far they chase
        
        // Learning data (raw samples)
        private final List<Long> attackIntervals = new ArrayList<>();
        private int totalAttacks = 0;
        private int critAttacks = 0;
        private int totalSwings = 0;
        private int hitsLanded = 0;
        private int strafes = 0;
        private int wTaps = 0;
        private int jumps = 0;
        private int combatTicks = 0;
        private int sprints = 0;
        private int sneaks = 0;
        private int blocks = 0;
        private int heals = 0;
        private int potionHeals = 0;
        private int gappleHeals = 0;
        private double totalHealHp = 0;
        private int retreats = 0;
        private int lowHpMoments = 0;
        
        // Session tracking
        private long lastAttackTime = 0;
        private long sessionStart = 0;
        private int sessionSamples = 0;
        
        public void startSession() {
            sessionStart = System.currentTimeMillis();
            sessionSamples = 0;
        }
        
        public void recordAttack(boolean isCrit, boolean hitTarget) {
            long now = System.currentTimeMillis();
            totalSwings++;
            
            if (hitTarget) {
                totalAttacks++;
                if (isCrit) critAttacks++;
                
                if (lastAttackTime > 0) {
                    long interval = now - lastAttackTime;
                    if (interval > 100 && interval < 2000) { // Valid interval
                        attackIntervals.add(interval);
                        if (attackIntervals.size() > 100) {
                            attackIntervals.remove(0); // Keep last 100
                        }
                    }
                }
                lastAttackTime = now;
                hitsLanded++;
            }
            
            recalculate();
        }
        
        public void recordStrafe() { strafes++; combatTicks++; recalculate(); }
        public void recordWTap() { wTaps++; combatTicks++; recalculate(); }
        public void recordJump() { jumps++; combatTicks++; recalculate(); }
        public void recordSprint() { sprints++; combatTicks++; recalculate(); }
        public void recordSneak() { sneaks++; combatTicks++; recalculate(); }
        public void recordBlock() { blocks++; combatTicks++; recalculate(); }
        public void recordCombatTick() { combatTicks++; }
        
        public void recordHeal(boolean isPotion, double hpPercent) {
            heals++;
            totalHealHp += hpPercent;
            if (isPotion) potionHeals++;
            else gappleHeals++;
            recalculate();
        }
        
        public void recordRetreat() { retreats++; lowHpMoments++; recalculate(); }
        public void recordLowHp() { lowHpMoments++; recalculate(); }
        
        private void recalculate() {
            sessionSamples++;
            
            // Only recalculate every 10 samples for performance
            if (sessionSamples % 10 != 0) return;
            
            // Attack patterns
            if (!attackIntervals.isEmpty()) {
                avgAttackInterval = attackIntervals.stream()
                        .mapToLong(Long::longValue).average().orElse(500);
            }
            if (totalAttacks > 0) {
                critRate = (double) critAttacks / totalAttacks;
            }
            if (totalSwings > 0) {
                attackAccuracy = (double) hitsLanded / totalSwings;
            }
            
            // Movement patterns (as frequency per combat tick)
            if (combatTicks > 10) {
                strafeFrequency = Math.min(1.0, (double) strafes / combatTicks * 20);
                wTapFrequency = Math.min(1.0, (double) wTaps / combatTicks * 20);
                jumpFrequency = Math.min(1.0, (double) jumps / combatTicks * 20);
                sprintFrequency = Math.min(1.0, (double) sprints / combatTicks * 5);
                sneakFrequency = Math.min(1.0, (double) sneaks / combatTicks * 20);
                blockFrequency = Math.min(1.0, (double) blocks / combatTicks * 20);
            }
            
            // Healing behavior
            if (heals > 0) {
                healThreshold = totalHealHp / heals;
                potionUseRate = (double) potionHeals / heals;
            }
            
            // Retreat behavior
            if (lowHpMoments > 0) {
                retreatThreshold = 0.3 - (0.1 * ((double) retreats / lowHpMoments));
                retreatThreshold = Math.max(0.15, Math.min(0.4, retreatThreshold));
            }
            
            // Aggression (based on attack frequency and chase behavior)
            aggressionLevel = Math.min(1.0, 
                    (1000.0 / avgAttackInterval) * 0.5 + 
                    attackAccuracy * 0.3 + 
                    (1.0 - retreatThreshold) * 0.2);
        }
        
        public Map<String, Object> serialize() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("avgAttackInterval", avgAttackInterval);
            map.put("critRate", critRate);
            map.put("comboLength", comboLength);
            map.put("attackAccuracy", attackAccuracy);
            map.put("strafeFrequency", strafeFrequency);
            map.put("wTapFrequency", wTapFrequency);
            map.put("jumpFrequency", jumpFrequency);
            map.put("sprintFrequency", sprintFrequency);
            map.put("sneakFrequency", sneakFrequency);
            map.put("healThreshold", healThreshold);
            map.put("potionUseRate", potionUseRate);
            map.put("blockFrequency", blockFrequency);
            map.put("retreatThreshold", retreatThreshold);
            map.put("aggressionLevel", aggressionLevel);
            map.put("totalSamples", sessionSamples);
            return map;
        }
        
        public static PlayerProfile deserialize(Map<String, Object> map) {
            PlayerProfile p = new PlayerProfile();
            if (map == null) return p;
            
            p.avgAttackInterval = getDouble(map, "avgAttackInterval", 500);
            p.critRate = getDouble(map, "critRate", 0.2);
            p.comboLength = getDouble(map, "comboLength", 3);
            p.attackAccuracy = getDouble(map, "attackAccuracy", 0.5);
            p.strafeFrequency = getDouble(map, "strafeFrequency", 0.3);
            p.wTapFrequency = getDouble(map, "wTapFrequency", 0.2);
            p.jumpFrequency = getDouble(map, "jumpFrequency", 0.15);
            p.sprintFrequency = getDouble(map, "sprintFrequency", 0.7);
            p.sneakFrequency = getDouble(map, "sneakFrequency", 0.1);
            p.healThreshold = getDouble(map, "healThreshold", 0.4);
            p.potionUseRate = getDouble(map, "potionUseRate", 0.3);
            p.blockFrequency = getDouble(map, "blockFrequency", 0.2);
            p.retreatThreshold = getDouble(map, "retreatThreshold", 0.25);
            p.aggressionLevel = getDouble(map, "aggressionLevel", 0.5);
            p.sessionSamples = getInt(map, "totalSamples", 0);
            return p;
        }
        
        private static double getDouble(Map<String, Object> map, String key, double def) {
            Object v = map.get(key);
            if (v instanceof Number) return ((Number) v).doubleValue();
            return def;
        }
        
        private static int getInt(Map<String, Object> map, String key, int def) {
            Object v = map.get(key);
            if (v instanceof Number) return ((Number) v).intValue();
            return def;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PROFILE MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    public PlayerProfile getProfile(UUID uuid) {
        return profiles.computeIfAbsent(uuid, k -> new PlayerProfile());
    }

    public PlayerProfile getOrCreateProfile(Player player) {
        return getProfile(player.getUniqueId());
    }

    public void startLearningSession(Player player) {
        getProfile(player.getUniqueId()).startSession();
    }

    public boolean hasEnoughData(UUID uuid) {
        PlayerProfile p = profiles.get(uuid);
        return p != null && p.sessionSamples >= 50;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PERSISTENCE
    // ══════════════════════════════════════════════════════════════════════════

    public void load() {
        if (!dataFile.exists()) return;
        
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        var section = cfg.getConfigurationSection("profiles");
        if (section == null) return;
        
        for (String uuidStr : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                var data = cfg.getConfigurationSection("profiles." + uuidStr);
                if (data != null) {
                    profiles.put(uuid, PlayerProfile.deserialize(data.getValues(false)));
                }
            } catch (Exception ignored) {}
        }
        
        plugin.getLogger().info("[AdaptiveAI] Loaded " + profiles.size() + " player profiles.");
    }

    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        
        for (var entry : profiles.entrySet()) {
            String path = "profiles." + entry.getKey().toString();
            for (var data : entry.getValue().serialize().entrySet()) {
                cfg.set(path + "." + data.getKey(), data.getValue());
            }
        }
        
        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[AdaptiveAI] Failed to save: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CONVERT PROFILE TO BOT PARAMETERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Converts a player profile into bot combat parameters.
     * The bot will fight similarly to how the player fights.
     */
    public BotParameters toBotParameters(UUID uuid) {
        PlayerProfile p = getProfile(uuid);
        
        BotParameters params = new BotParameters();
        
        // Attack timing (convert ms to ticks, clamped)
        params.ticksBetweenAttacks = Math.max(4, Math.min(20, (int)(p.avgAttackInterval / 50)));
        params.critChance = p.critRate;
        params.hitAccuracy = p.attackAccuracy;
        
        // Movement
        params.strafeChance = p.strafeFrequency;
        params.wTapChance = p.wTapFrequency;
        params.jumpChance = p.jumpFrequency;
        params.sprintChance = p.sprintFrequency;
        params.sneakChance = p.sneakFrequency;
        
        // Healing
        params.healThreshold = p.healThreshold;
        params.preferPotions = p.potionUseRate > 0.5;
        
        // Defense
        params.blockChance = p.blockFrequency;
        params.retreatThreshold = p.retreatThreshold;
        
        // Aggression
        params.aggressionLevel = p.aggressionLevel;
        
        // Reaction time (based on attack interval)
        params.reactionTimeMs = (int) Math.max(150, Math.min(500, p.avgAttackInterval * 0.6));
        
        return params;
    }

    /**
     * Parameters for adaptive bot behavior.
     */
    public static class BotParameters {
        public int ticksBetweenAttacks = 10;
        public double critChance = 0.2;
        public double hitAccuracy = 0.5;
        public double strafeChance = 0.3;
        public double wTapChance = 0.2;
        public double jumpChance = 0.15;
        public double sprintChance = 0.7;
        public double sneakChance = 0.1;
        public double healThreshold = 0.4;
        public boolean preferPotions = false;
        public double blockChance = 0.2;
        public double retreatThreshold = 0.25;
        public double aggressionLevel = 0.5;
        public int reactionTimeMs = 300;
    }
}
