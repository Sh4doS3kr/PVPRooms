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
 * Manages the ELO ranking system.
 * All data is stored in plugins/PvPRoomsPro/elo.yml.
 *
 * Formula used:
 *   expectedScore = 1 / (1 + 10^((opponentElo - playerElo) / 400))
 *   eloChange = K * (actualScore - expectedScore)
 *   where K = 32 by default, actualScore = 1 for win, 0 for loss.
 */
public class EloManager {

    private static final int K_FACTOR = 32;

    private final PvPRoomsPro plugin;
    private final File eloFile;
    private FileConfiguration eloConfig;

    /** uuid string → elo integer */
    private final Map<String, Integer> eloMap = new HashMap<>();

    /** uuid string → display name (cached for leaderboard) */
    private final Map<String, String> nameMap = new HashMap<>();

    public EloManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
        this.eloFile = new File(plugin.getDataFolder(), "elo.yml");
        loadElo();
    }

    // ── Load / Save ────────────────────────────────────────────────────────

    public void loadElo() {
        eloMap.clear();
        nameMap.clear();
        if (!eloFile.exists()) {
            saveElo();
            return;
        }
        eloConfig = YamlConfiguration.loadConfiguration(eloFile);
        if (!eloConfig.contains("players")) return;

        for (String uuidStr : eloConfig.getConfigurationSection("players").getKeys(false)) {
            String p = "players." + uuidStr;
            int elo = eloConfig.getInt(p + ".elo",
                    plugin.getConfig().getInt("elo.starting-elo", 1000));
            String name = eloConfig.getString(p + ".name", uuidStr);
            eloMap.put(uuidStr, elo);
            nameMap.put(uuidStr, name);
        }
    }

    public void saveElo() {
        eloConfig = new YamlConfiguration();
        for (Map.Entry<String, Integer> entry : eloMap.entrySet()) {
            String p = "players." + entry.getKey();
            eloConfig.set(p + ".elo",  entry.getValue());
            eloConfig.set(p + ".name", nameMap.getOrDefault(entry.getKey(), entry.getKey()));
        }
        try {
            eloConfig.save(eloFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save elo.yml", e);
        }
    }

    // ── ELO logic ──────────────────────────────────────────────────────────

    /**
     * Returns the ELO for a player UUID, initializing to the configured default if absent.
     */
    public int getElo(UUID uuid) {
        return eloMap.getOrDefault(uuid.toString(),
                plugin.getConfig().getInt("elo.starting-elo", 1000));
    }

    /**
     * Sets the ELO for a player.
     */
    public void setElo(UUID uuid, String playerName, int elo) {
        eloMap.put(uuid.toString(), Math.max(0, elo));
        nameMap.put(uuid.toString(), playerName);
    }

    /**
     * Processes the result of a duel and updates both players' ELO.
     * Returns an array: [winnerChange, loserChange] (both positive values).
     */
    public int[] processResult(UUID winner, String winnerName, UUID loser, String loserName) {
        int winnerElo = getElo(winner);
        int loserElo  = getElo(loser);

        // Expected scores using standard ELO formula
        double expectedWinner = 1.0 / (1.0 + Math.pow(10, (loserElo - winnerElo) / 400.0));
        double expectedLoser  = 1.0 - expectedWinner;

        int winnerChange = (int) Math.round(K_FACTOR * (1.0 - expectedWinner));
        int loserChange  = (int) Math.round(K_FACTOR * (0.0 - expectedLoser));

        // Ensure minimum gain/loss
        winnerChange = Math.max(winnerChange, 5);
        int loserLoss = Math.max(Math.abs(loserChange), 5);

        setElo(winner, winnerName, winnerElo + winnerChange);
        setElo(loser,  loserName,  Math.max(0, loserElo - loserLoss));

        saveElo();
        return new int[]{winnerChange, loserLoss};
    }

    // ── Leaderboard ────────────────────────────────────────────────────────

    /**
     * Returns the top N players sorted by ELO descending.
     * Each entry is a String: "#1 PlayerName — ELO"
     */
    public List<String> getTopPlayers(int limit) {
        return eloMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(e -> {
                    String name = nameMap.getOrDefault(e.getKey(), e.getKey());
                    return name + " §7— §e" + e.getValue();
                })
                .collect(Collectors.toList());
    }

    // ── Admin helpers ───────────────────────────────────────────────────────

    /** Resets one player's ELO to the configured default. */
    public void resetElo(UUID uuid) {
        int def = plugin.getConfig().getInt("elo.starting-elo", 1000);
        String name = nameMap.getOrDefault(uuid.toString(), uuid.toString());
        setElo(uuid, name, def);
        saveElo();
    }

    /** Resets ALL players' ELO to the configured default. */
    public void resetAllElo() {
        int def = plugin.getConfig().getInt("elo.starting-elo", 1000);
        for (String uuidStr : new ArrayList<>(eloMap.keySet())) {
            eloMap.put(uuidStr, def);
        }
        saveElo();
    }

    /**
     * Looks up a UUID from the cached player name (case-insensitive).
     * Returns null if not found.
     */
    public UUID getUUIDByName(String name) {
        for (Map.Entry<String, String> e : nameMap.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) {
                try { return UUID.fromString(e.getKey()); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }

    /** Returns true if the player has at least one recorded game (is in elo.yml). */
    public boolean hasEloRecord(UUID uuid) {
        return eloMap.containsKey(uuid.toString());
    }

    public int getDefaultElo() {
        return plugin.getConfig().getInt("elo.starting-elo", 1000);
    }

    public int getPlayerCount() { return eloMap.size(); }

    /** Returns a copy of the name map for display purposes. */
    public Map<String, String> getNameMap() { return Collections.unmodifiableMap(nameMap); }

    /** Returns a copy of the elo map. */
    public Map<String, Integer> getEloMap() { return Collections.unmodifiableMap(eloMap); }

    /** Returns the rank (1-based) of a player, or -1 if not ranked yet. */
    public int getRank(UUID uuid) {
        List<String> sorted = eloMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        int idx = sorted.indexOf(uuid.toString());
        return idx == -1 ? -1 : idx + 1;
    }
}
