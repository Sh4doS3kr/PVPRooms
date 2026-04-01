package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Manages player statistics: wins, losses, kills, deaths, streaks.
 * Data stored in plugins/PvPRoomsPro/stats.yml
 */
public class StatsManager {

    private final PvPRoomsPro plugin;
    private final File statsFile;

    /** uuid string → PlayerStats */
    private final Map<String, PlayerStats> statsMap = new HashMap<>();
    /** uuid string → display name */
    private final Map<String, String> nameMap = new HashMap<>();

    /** Minimum total games (wins + losses) required to display a K/D ratio. */
    public static final int MIN_GAMES_FOR_KDR = 10;

    public record PlayerStats(int wins, int losses, int kills, int deaths, int currentStreak, int bestStreak, int hits, int swings) {
        /** Returns true when the player has played enough games to show a K/D ratio. */
        public boolean hasKDR() {
            return (wins + losses) >= MIN_GAMES_FOR_KDR;
        }

        /** Raw K/D ratio. Always call {@link #hasKDR()} first before displaying. */
        public double getKDR() {
            return deaths == 0 ? kills : (double) kills / deaths;
        }
        
        /** Returns hit accuracy as percentage (0-100) */
        public double getAccuracy() {
            return swings == 0 ? 0 : ((double) hits / swings) * 100;
        }
    }

    public StatsManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        load();
    }

    // ── Load / Save ────────────────────────────────────────────────────────

    public void load() {
        statsMap.clear();
        nameMap.clear();
        if (!statsFile.exists()) {
            save();
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(statsFile);
        if (!cfg.contains("players")) return;

        for (String uuidStr : cfg.getConfigurationSection("players").getKeys(false)) {
            String p = "players." + uuidStr;
            int wins = cfg.getInt(p + ".wins", 0);
            int losses = cfg.getInt(p + ".losses", 0);
            int kills = cfg.getInt(p + ".kills", 0);
            int deaths = cfg.getInt(p + ".deaths", 0);
            int currentStreak = cfg.getInt(p + ".currentStreak", 0);
            int bestStreak = cfg.getInt(p + ".bestStreak", 0);
            int hits = cfg.getInt(p + ".hits", 0);
            int swings = cfg.getInt(p + ".swings", 0);
            String name = cfg.getString(p + ".name", uuidStr);
            
            statsMap.put(uuidStr, new PlayerStats(wins, losses, kills, deaths, currentStreak, bestStreak, hits, swings));
            nameMap.put(uuidStr, name);
        }
        plugin.getLogger().info("[StatsManager] " + statsMap.size() + " jugadores cargados.");
    }

    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, PlayerStats> entry : statsMap.entrySet()) {
            String p = "players." + entry.getKey();
            PlayerStats s = entry.getValue();
            cfg.set(p + ".wins", s.wins());
            cfg.set(p + ".losses", s.losses());
            cfg.set(p + ".kills", s.kills());
            cfg.set(p + ".deaths", s.deaths());
            cfg.set(p + ".currentStreak", s.currentStreak());
            cfg.set(p + ".bestStreak", s.bestStreak());
            cfg.set(p + ".hits", s.hits());
            cfg.set(p + ".swings", s.swings());
            cfg.set(p + ".name", nameMap.getOrDefault(entry.getKey(), entry.getKey()));
        }
        try {
            cfg.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save stats.yml", e);
        }
    }

    // ── Stats tracking ────────────────────────────────────────────────────

    public void recordWin(UUID uuid, String name) {
        String key = uuid.toString();
        nameMap.put(key, name);
        PlayerStats old = statsMap.getOrDefault(key, new PlayerStats(0, 0, 0, 0, 0, 0, 0, 0));
        int newStreak = old.currentStreak() + 1;
        int bestStreak = Math.max(old.bestStreak(), newStreak);
        statsMap.put(key, new PlayerStats(old.wins() + 1, old.losses(), old.kills(), old.deaths(), newStreak, bestStreak, old.hits(), old.swings()));
    }

    public void recordLoss(UUID uuid, String name) {
        String key = uuid.toString();
        nameMap.put(key, name);
        PlayerStats old = statsMap.getOrDefault(key, new PlayerStats(0, 0, 0, 0, 0, 0, 0, 0));
        statsMap.put(key, new PlayerStats(old.wins(), old.losses() + 1, old.kills(), old.deaths(), 0, old.bestStreak(), old.hits(), old.swings()));
    }

    public void recordKill(UUID uuid, String name) {
        String key = uuid.toString();
        nameMap.put(key, name);
        PlayerStats old = statsMap.getOrDefault(key, new PlayerStats(0, 0, 0, 0, 0, 0, 0, 0));
        statsMap.put(key, new PlayerStats(old.wins(), old.losses(), old.kills() + 1, old.deaths(), old.currentStreak(), old.bestStreak(), old.hits(), old.swings()));
    }

    public void recordDeath(UUID uuid, String name) {
        String key = uuid.toString();
        nameMap.put(key, name);
        PlayerStats old = statsMap.getOrDefault(key, new PlayerStats(0, 0, 0, 0, 0, 0, 0, 0));
        statsMap.put(key, new PlayerStats(old.wins(), old.losses(), old.kills(), old.deaths() + 1, old.currentStreak(), old.bestStreak(), old.hits(), old.swings()));
    }

    // ── Getters ────────────────────────────────────────────────────────────

    public PlayerStats getStats(UUID uuid) {
        return statsMap.getOrDefault(uuid.toString(), new PlayerStats(0, 0, 0, 0, 0, 0, 0, 0));
    }
    
    /** Record a successful hit (attack that connected) */
    public void recordHit(UUID uuid, String name) {
        String key = uuid.toString();
        nameMap.put(key, name);
        PlayerStats old = statsMap.getOrDefault(key, new PlayerStats(0, 0, 0, 0, 0, 0, 0, 0));
        statsMap.put(key, new PlayerStats(old.wins(), old.losses(), old.kills(), old.deaths(), old.currentStreak(), old.bestStreak(), old.hits() + 1, old.swings() + 1));
    }
    
    /** Record a missed swing (attack that didn't connect) */
    public void recordMiss(UUID uuid, String name) {
        String key = uuid.toString();
        nameMap.put(key, name);
        PlayerStats old = statsMap.getOrDefault(key, new PlayerStats(0, 0, 0, 0, 0, 0, 0, 0));
        statsMap.put(key, new PlayerStats(old.wins(), old.losses(), old.kills(), old.deaths(), old.currentStreak(), old.bestStreak(), old.hits(), old.swings() + 1));
    }

    public String getName(UUID uuid) {
        return nameMap.getOrDefault(uuid.toString(), "???");
    }

    // ── Leaderboards ───────────────────────────────────────────────────────

    public List<Map.Entry<String, Integer>> getTopByWins(int limit) {
        return statsMap.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().wins()))
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Map.Entry<String, Integer>> getTopByStreak(int limit) {
        return statsMap.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().currentStreak()))
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Map.Entry<String, Double>> getTopByKDR(int limit) {
        return statsMap.entrySet().stream()
                .filter(e -> e.getValue().hasKDR()) // require MIN_GAMES_FOR_KDR games
                .map(e -> Map.entry(e.getKey(), e.getValue().getKDR()))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public Map<String, String> getNameMap() {
        return Collections.unmodifiableMap(nameMap);
    }
}
