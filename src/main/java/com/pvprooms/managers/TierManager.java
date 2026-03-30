package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Tier;
import com.pvprooms.model.TierTitle;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Gestiona los puntos de tier por jugador por kit.
 *
 * Este sistema es INDEPENDIENTE del ELO:
 *  - Cola TIER  → actualiza TierPoints  (sin cambio de ELO)
 *  - Cola ELO   → actualiza ELO         (sin cambio de TierPoints)
 *
 * Persistencia: plugins/PvPRoomsPro/tier_points.yml
 *
 * Formato YAML:
 *   points:
 *     uuid1:
 *       espada: 1500
 *       arco: 340
 *     uuid2:
 *       espada: 80
 */
public class TierManager {

    // ── Constantes de puntos ──────────────────────────────────────────────
    public static final int WIN_BASE         = 18;
    public static final int LOSS_BASE        = 12;
    public static final int TIER_DIFF_BONUS  = 4;   // por cada tier de diferencia
    public static final int MIN_LOSS         = 5;
    public static final int MAX_WIN          = 35;

    private final PvPRoomsPro plugin;
    private final File dataFile;

    /** uuid → (kitName_lower → puntos) */
    private final Map<UUID, Map<String, Integer>> pointsByKit = new LinkedHashMap<>();

    public TierManager(PvPRoomsPro plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "tier_points.yml");
        load();
    }

    // ── Persistencia ──────────────────────────────────────────────────────

    public void load() {
        pointsByKit.clear();
        if (!dataFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        var section = cfg.getConfigurationSection("points");
        if (section == null) return;

        for (String uuidStr : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                var kitSection = cfg.getConfigurationSection("points." + uuidStr);
                if (kitSection == null) continue;
                Map<String, Integer> kits = new LinkedHashMap<>();
                for (String kit : kitSection.getKeys(false)) {
                    kits.put(kit.toLowerCase(), kitSection.getInt(kit, 0));
                }
                pointsByKit.put(uuid, kits);
            } catch (IllegalArgumentException ignored) {}
        }
        plugin.getLogger().info("[TierManager] " + pointsByKit.size() + " jugadores cargados.");
    }

    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (var e : pointsByKit.entrySet()) {
            String base = "points." + e.getKey();
            for (var kit : e.getValue().entrySet()) {
                cfg.set(base + "." + kit.getKey(), kit.getValue());
            }
        }
        try {
            cfg.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar tier_points.yml", ex);
        }
    }

    // ── Gestión de puntos ────────────────────────────────────────────────

    /**
     * Devuelve los puntos del jugador para ese kit.
     * @return -1 si nunca ha jugado ese kit (Sin Rango)
     */
    public int getPoints(UUID uuid, String kitName) {
        Map<String, Integer> kits = pointsByKit.get(uuid);
        if (kits == null) return -1;
        return kits.getOrDefault(kitName.toLowerCase(), -1);
    }

    public boolean hasPlayed(UUID uuid, String kitName) {
        return getPoints(uuid, kitName) >= 0;
    }

    /**
     * Registra el resultado de un match BO3 completo.
     * Ajusta puntos según diferencia de tier entre los jugadores.
     * Si el ganador sube de rango, se le notifica con título y mensaje.
     */
    public void recordResult(UUID winnerUUID, UUID loserUUID, String kitName) {
        String kit = kitName.toLowerCase();

        int wPts = effectivePts(winnerUUID, kit);
        int lPts = effectivePts(loserUUID,  kit);

        Tier wTierBefore = Tier.fromPoints(wPts);
        Tier lTier       = Tier.fromPoints(lPts);

        // tierDiff > 0 → ganador tenía tier menor que perdedor (upset)
        int tierDiff   = lTier.ordinal() - wTierBefore.ordinal();
        int winGain    = Math.min(WIN_BASE  + Math.max(0, tierDiff)  * TIER_DIFF_BONUS, MAX_WIN);
        int lossDeduct = Math.max(LOSS_BASE - Math.max(0, -tierDiff) * TIER_DIFF_BONUS, MIN_LOSS);

        int newWPts = wPts + winGain;
        int newLPts = Math.max(0, lPts - lossDeduct);

        setPts(winnerUUID, kit, newWPts);
        setPts(loserUUID,  kit, newLPts);
        save();

        // ── Notificar subida de rango al ganador ──────────────────────────
        Tier wTierAfter = Tier.fromPoints(newWPts);
        if (wTierAfter.ordinal() > wTierBefore.ordinal()) {
            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null) {
                String prefix = plugin.prefix();
                winner.sendMessage(prefix + "§6§l▲ ¡SUBISTE DE RANGO!");
                winner.sendMessage(prefix + "  §8" + wTierBefore.colour + wTierBefore.displayName
                        + " §8→ §r" + wTierAfter.colour + "§l" + wTierAfter.displayName);
                winner.sendTitle(
                        ChatColor.translateAlternateColorCodes('&', "&6&l▲ SUBISTE DE RANGO"),
                        ChatColor.translateAlternateColorCodes('&',
                                wTierAfter.colour.replace("§", "&") + "&l" + wTierAfter.displayName),
                        10, 80, 20);
                winner.playSound(winner.getLocation(),
                        org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        }
    }

    private int effectivePts(UUID uuid, String kit) {
        int p = getPoints(uuid, kit);
        return p < 0 ? 0 : p;
    }

    private void setPts(UUID uuid, String kit, int points) {
        pointsByKit.computeIfAbsent(uuid, k -> new LinkedHashMap<>())
                   .put(kit, Math.max(0, points));
    }

    // ── Consultas de tier ────────────────────────────────────────────────

    public Tier getTier(UUID uuid, String kitName) {
        int pts = getPoints(uuid, kitName.toLowerCase());
        return Tier.fromPoints(pts); // fromPoints(-1) → UNRANKED
    }

    /**
     * Devuelve el tier más alto del jugador entre todos sus kits.
     * Si no ha jugado ningún kit, devuelve UNRANKED.
     * Usar en el scoreboard del lobby para mostrar un rango global consistente.
     */
    public Tier getBestTier(UUID uuid) {
        Map<String, Integer> kits = pointsByKit.get(uuid);
        if (kits == null || kits.isEmpty()) return Tier.UNRANKED;
        return kits.values().stream()
                   .map(Tier::fromPoints)
                   .max(Comparator.comparingInt(Tier::ordinal))
                   .orElse(Tier.UNRANKED);
    }

    /**
     * Sincroniza los puntos del jugador para un kit basándose en su ELO.
     * Se llama tras un duelo ELO para mantener TierManager coherente con el scoreboard.
     * Solo sube puntos, nunca los reduce por sincronización.
     */
    public void syncFromElo(UUID uuid, String kitName, int elo) {
        Tier eloTier = Tier.fromElo(elo);
        if (eloTier == Tier.UNRANKED) return;
        String kit   = kitName.toLowerCase();
        int current  = effectivePts(uuid, kit);
        int eloFloor = eloTier.minPoints; // puntos mínimos de ese tier
        if (eloFloor > current) {
            Tier before = Tier.fromPoints(current);
            setPts(uuid, kit, eloFloor);
            save();
            // Notificar subida de rango si aplica
            Tier after = Tier.fromPoints(eloFloor);
            if (after.ordinal() > before.ordinal()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    String prefix = plugin.prefix();
                    player.sendMessage(prefix + "§6§l▲ ¡SUBISTE DE RANGO!");
                    player.sendMessage(prefix + "  §8" + before.colour + before.displayName
                            + " §8→ §r" + after.colour + "§l" + after.displayName);
                    player.sendTitle(
                            ChatColor.translateAlternateColorCodes('&', "&6&l▲ SUBISTE DE RANGO"),
                            ChatColor.translateAlternateColorCodes('&',
                                    after.colour.replace("§", "&") + "&l" + after.displayName),
                            10, 80, 20);
                    player.playSound(player.getLocation(),
                            org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                }
            }
        }
    }

    /** Puntuación total = suma de tierScore() de cada kit jugado. */
    public int getTotalScore(UUID uuid) {
        Map<String, Integer> kits = pointsByKit.get(uuid);
        if (kits == null || kits.isEmpty()) return 0;
        return kits.values().stream()
                   .mapToInt(pts -> Tier.fromPoints(pts).tierScore())
                   .sum();
    }

    public TierTitle getTitle(UUID uuid) {
        return TierTitle.fromScore(getTotalScore(uuid));
    }

    /** Devuelve los puntos por kit del jugador (sólo kits jugados). */
    public Map<String, Integer> getKitPoints(UUID uuid) {
        return Collections.unmodifiableMap(
                pointsByKit.getOrDefault(uuid, Collections.emptyMap()));
    }

    /** Devuelve la suma total de puntos del jugador (alias de getTotalScore). */
    public int getTotalPoints(UUID uuid) {
        return getTotalScore(uuid);
    }

    // ── Rankings ──────────────────────────────────────────────────────────

    /** Top N jugadores por puntuación total. */
    public List<PlayerRank> getTopPlayers(int limit) {
        return pointsByKit.keySet().stream()
                .map(uuid -> new PlayerRank(uuid, getTotalScore(uuid), null, -1))
                .filter(r -> r.score() > 0)
                .sorted(Comparator.comparingInt(PlayerRank::score).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /** Top N jugadores para un kit específico, ordenados por puntos del kit. */
    public List<PlayerRank> getTopForKit(String kitName, int limit) {
        String kit = kitName.toLowerCase();
        List<PlayerRank> list = new ArrayList<>();
        for (var entry : pointsByKit.entrySet()) {
            Integer pts = entry.getValue().get(kit);
            if (pts != null && pts >= 0) {
                list.add(new PlayerRank(entry.getKey(), getTotalScore(entry.getKey()), kit, pts));
            }
        }
        list.sort(Comparator.comparingInt(PlayerRank::kitPoints).reversed());
        return list.stream().limit(limit).collect(Collectors.toList());
    }

    /** Todos los UUIDs con al menos un registro de tier. */
    public Set<UUID> getAllTrackedPlayers() {
        return Collections.unmodifiableSet(pointsByKit.keySet());
    }

    /** Resetea TODOS los datos de tiers de TODOS los jugadores. */
    public void resetAllData() {
        pointsByKit.clear();
        save();
        plugin.getLogger().info("[TierManager] TODOS los datos de tiers han sido reseteados.");
    }

    /** Resetea los datos de tier de un jugador específico. */
    public void resetPlayer(UUID uuid) {
        pointsByKit.remove(uuid);
        save();
    }

    // ── Inner record ─────────────────────────────────────────────────────

    public record PlayerRank(UUID uuid, int score, String kit, int kitPoints) {}
}
