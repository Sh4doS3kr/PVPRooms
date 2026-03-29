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

    public record PlayerStats(int wins, int losses, int kills, int deaths, int currentStreak, int bestStreak) {
        public double getKDR() {
            return deaths == 0 ? kills : (double) kills / deaths;
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
            String name = cfg.getString(p + ".name", uuidStr);
            
            statsMap.put(uuidStr, new PlayerStats(wins, losses, kills, deaths, currentStreak, bestStreak));
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
        PlayerStats old = statsMap.getOrDefault(key, new PlayerStats(0, 0, 0, 0, 0, 0));
        int newStreak = old.currentStreak() + 1;
        int bestStreak = Math.max(old.bestStreak(), newStreak);
        statsMap.put(key, new PlayerStats(old.wins() + 1, old.losses(), old.kills(), old.deaths(), newStreak, bestStreak));
    }

    public void recordLoss(UUID uuid, String name) {
        String key = uuid.toString();
        nameMap.put(key, name);
        PlayerStats old = statsMap.getOrDefault(key, new PlayerStats(0, 0, 0, 0, 0, 0));
        statsMap.put(key, new PlayerStats(old.wins(), old.losses() + 1, old.kills(), old.deaths(), 0, old.bestStreak()));
    }

    public void recordKill(UUID uuid, String name) {
        String key = uuid.toString();
        nameMap.put(key, name);
        PlayerStats old = statsMap.getOrDefault(key, new PlayerStats(0, 0, 0, 0, 0, 0));
        statsMap.put(key, new PlayerStats(old.wins(), old.losses(), old.kills() + 1, old.deaths(), old.currentStreak(), old.bestStreak()));
    }

    public void recordDeath(UUID uuid, String name) {
        String key = uuid.toString();
        nameMap.put(key, name);
        PlayerStats old = statsMap.getOrDefault(key, new PlayerStats(0, 0, 0, 0, 0, 0));
        statsMap.put(key, new PlayerStats(old.wins(), old.losses(), old.kills(), old.deaths() + 1, old.currentStreak(), old.bestStreak()));
    }

    // ── Getters ────────────────────────────────────────────────────────────

    public PlayerStats getStats(UUID uuid) {
        return statsMap.getOrDefault(uuid.toString(), new PlayerStats(0, 0, 0, 0, 0, 0));
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
                .filter(e -> e.getValue().kills() > 0 || e.getValue().deaths() > 0)
                .map(e -> Map.entry(e.getKey(), e.getValue().getKDR()))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public Map<String, String> getNameMap() {
        return Collections.unmodifiableMap(nameMap);
    }
}
