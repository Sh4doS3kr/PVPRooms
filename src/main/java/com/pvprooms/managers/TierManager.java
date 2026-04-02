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

    // ══════════════════════════════════════════════════════════════════════
    // SISTEMA DE PUNTOS COMPETITIVO - TIER (Mejor de 10 rondas)
    // ══════════════════════════════════════════════════════════════════════
    // Tier matches son al mejor de 10 (primero en llegar a 10 puntos gana)
    // Dan más puntos que ELO porque son partidas más largas
    // Tiers Elite (LT2+) requieren verificación manual via Discord
    // ══════════════════════════════════════════════════════════════════════
    
    public static final int WIN_BASE         = 25;   // Puntos base por victoria (más que ELO porque son 10 rondas)
    public static final int LOSS_BASE        = 15;   // Puntos base perdidos por derrota
    public static final int TIER_DIFF_BONUS  = 5;    // Bonus por vencer a tier superior
    public static final int MIN_LOSS         = 8;    // Mínimo que pierdes
    public static final int MAX_WIN          = 50;   // Máximo que ganas
    public static final int MIN_WIN          = 15;   // Mínimo que ganas
    
    // Bonus por rachas
    public static final int STREAK_BONUS     = 5;    // Bonus extra por racha de 3+
    public static final int MAX_STREAK_BONUS = 25;   // Máximo bonus por racha
    
    // Tracking de rachas
    private final Map<UUID, Integer> winStreaks = new HashMap<>();

    private final PvPRoomsPro plugin;
    private final File dataFile;

    /** uuid → (kitName_lower → puntos) */
    private final Map<UUID, Map<String, Integer>> pointsByKit = new LinkedHashMap<>();

    /** uuid → discordId (cuenta vinculada) */
    private final Map<UUID, String> discordLinks = new LinkedHashMap<>();

    /** código temporal → [uuid, discordId, discordUsername, timestamp] */
    private final Map<String, String[]> pendingLinkCodes = new java.util.concurrent.ConcurrentHashMap<>();

    public TierManager(PvPRoomsPro plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "tier_points.yml");
        load();
    }

    // ── Persistencia ──────────────────────────────────────────────────────

    public void load() {
        pointsByKit.clear();
        discordLinks.clear();
        if (!dataFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        var section = cfg.getConfigurationSection("points");
        if (section != null) {
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
        }
        var discordSection = cfg.getConfigurationSection("discord_links");
        if (discordSection != null) {
            for (String uuidStr : discordSection.getKeys(false)) {
                try {
                    discordLinks.put(UUID.fromString(uuidStr), discordSection.getString(uuidStr, ""));
                } catch (IllegalArgumentException ignored) {}
            }
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
        for (var e : discordLinks.entrySet()) {
            cfg.set("discord_links." + e.getKey(), e.getValue());
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
     * Sistema competitivo complejo:
     *   - Puntos base + bonus por tier difference
     *   - Bonus por rachas de victorias
     *   - Elite tiers (LT2+) tienen cap - requieren verificación manual
     */
    public void recordResult(UUID winnerUUID, UUID loserUUID, String kitName) {
        String kit = kitName.toLowerCase();

        int wPts = effectivePts(winnerUUID, kit);
        int lPts = effectivePts(loserUUID,  kit);

        Tier wTierBefore = Tier.fromPoints(wPts);
        Tier lTier       = Tier.fromPoints(lPts);

        // ═══════════════════════════════════════════════════════════════════
        // CÁLCULO DE PUNTOS GANADOS
        // ═══════════════════════════════════════════════════════════════════
        
        // tierDiff > 0 → ganador tenía tier menor que perdedor (upset = más puntos)
        int tierDiff = lTier.ordinal() - wTierBefore.ordinal();
        
        // Base + bonus por vencer a tier superior
        int winGain = WIN_BASE + Math.max(0, tierDiff) * TIER_DIFF_BONUS;
        
        // Actualizar racha del ganador
        int currentStreak = winStreaks.getOrDefault(winnerUUID, 0) + 1;
        winStreaks.put(winnerUUID, currentStreak);
        
        // Bonus por racha (a partir de 3 victorias seguidas)
        if (currentStreak >= 3) {
            int streakBonus = Math.min((currentStreak - 2) * STREAK_BONUS, MAX_STREAK_BONUS);
            winGain += streakBonus;
        }
        
        // Aplicar límites
        winGain = Math.max(MIN_WIN, Math.min(winGain, MAX_WIN));
        
        // ═══════════════════════════════════════════════════════════════════
        // CÁLCULO DE PUNTOS PERDIDOS
        // ═══════════════════════════════════════════════════════════════════
        
        // Pierdes menos si el oponente era de tier superior
        int lossDeduct = LOSS_BASE - Math.max(0, -tierDiff) * TIER_DIFF_BONUS;
        lossDeduct = Math.max(MIN_LOSS, lossDeduct);
        
        // Resetear racha del perdedor
        winStreaks.put(loserUUID, 0);
        
        // ═══════════════════════════════════════════════════════════════════
        // CAP PARA TIERS ELITE (LT2+) - Requieren verificación manual
        // ═══════════════════════════════════════════════════════════════════
        int newWPts = wPts + winGain;
        
        // Si el jugador alcanzaría LT2 (3000 pts), cap a 2999 sin verificación
        // Los admins deben usar /admin settier para verificar jugadores elite
        if (wTierBefore.ordinal() < Tier.LT2.ordinal() && newWPts >= Tier.LT2.minPoints) {
            newWPts = Tier.LT2.minPoints - 1; // Cap justo debajo de LT2
            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null) {
                winner.sendMessage(plugin.prefix() + "§d§l⚡ ¡HAS ALCANZADO EL LÍMITE DE HT3!");
                winner.sendMessage(plugin.prefix() + "§7Para subir a §cTiers Elite §7(LT2+), necesitas:");
                winner.sendMessage("§7  • Abrir ticket en §b§ntiers.mlmc.lat/tickets.html");
                winner.sendMessage("§7  • Verificación por un admin");
            }
        }
        
        int newLPts = Math.max(0, lPts - lossDeduct);

        setPts(winnerUUID, kit, newWPts);
        setPts(loserUUID,  kit, newLPts);
        save();

        // ═══════════════════════════════════════════════════════════════════
        // NOTIFICACIONES
        // ═══════════════════════════════════════════════════════════════════
        
        Player winner = Bukkit.getPlayer(winnerUUID);
        Player loser = Bukkit.getPlayer(loserUUID);
        Tier wTierAfter = Tier.fromPoints(newWPts);
        
        // Mostrar puntos ganados/perdidos
        if (winner != null) {
            String streakMsg = currentStreak >= 3 ? " §6(Racha x" + currentStreak + ")" : "";
            winner.sendMessage(plugin.prefix() + "§a+" + winGain + " puntos" + streakMsg + 
                    " §8[§f" + newWPts + "§7/" + getNextTierPoints(wTierAfter) + "§8]");
        }
        if (loser != null) {
            loser.sendMessage(plugin.prefix() + "§c-" + lossDeduct + " puntos §8[§f" + newLPts + "§8]");
        }
        
        // Notificar subida de rango
        if (wTierAfter.ordinal() > wTierBefore.ordinal()) {
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
        
        // Notificar bajada de rango
        Tier lTierAfter = Tier.fromPoints(newLPts);
        if (lTierAfter.ordinal() < lTier.ordinal() && loser != null) {
            loser.sendMessage(plugin.prefix() + "§c§l▼ BAJASTE DE RANGO");
            loser.sendMessage(plugin.prefix() + "  §8" + lTier.colour + lTier.displayName
                    + " §8→ §r" + lTierAfter.colour + lTierAfter.displayName);
        }
    }
    
    /**
     * Returns the points needed for the next tier.
     */
    private int getNextTierPoints(Tier currentTier) {
        Tier[] tiers = Tier.values();
        int nextOrdinal = currentTier.ordinal() + 1;
        if (nextOrdinal < tiers.length) {
            return tiers[nextOrdinal].minPoints;
        }
        return currentTier.minPoints; // Already max tier
    }

    private int effectivePts(UUID uuid, String kit) {
        int p = getPoints(uuid, kit);
        return p < 0 ? 0 : p;
    }

    private void setPts(UUID uuid, String kit, int points) {
        pointsByKit.computeIfAbsent(uuid, k -> new LinkedHashMap<>())
                   .put(kit, Math.max(0, points));
    }

    /**
     * Sets a player's tier points directly (for admin verification of elite tiers).
     * Elite tiers (LT2, HT2, LT1, HT1) require verification via Discord ticket.
     */
    public void setPoints(UUID uuid, String kitName, int points) {
        setPts(uuid, kitName.toLowerCase(), points);
        save();
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

    // ═══════════════════════════════════════════════════════════════════════
    // NOTA: ELO y TIER son sistemas INDEPENDIENTES
    // - Cola ELO → solo afecta ELO (EloManager)
    // - Cola TIER → solo afecta puntos de tier (TierManager)
    // NO sincronizar entre ellos
    // ═══════════════════════════════════════════════════════════════════════

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

    /** Top N jugadores por puntuación total. Incluye jugadores con ELO aunque no tengan tier points. */
    public List<PlayerRank> getTopPlayers(int limit) {
        // Combine players from both TierManager and EloManager
        // Use a Map to deduplicate by player name (handles UUID format differences)
        Map<String, UUID> playersByName = new LinkedHashMap<>();
        
        // Add from TierManager
        for (UUID uuid : pointsByKit.keySet()) {
            String name = resolveName(uuid);
            if (name != null && !name.isEmpty()) {
                playersByName.putIfAbsent(name.toLowerCase(), uuid);
            }
        }
        
        // Add all players from EloManager (those who have played ELO matches)
        for (String uuidStr : plugin.getEloManager().getEloMap().keySet()) {
            try {
                // Normalize UUID format (add dashes if missing)
                String normalized = uuidStr.toLowerCase();
                if (!normalized.contains("-") && normalized.length() == 32) {
                    normalized = normalized.substring(0, 8) + "-" + normalized.substring(8, 12) + "-" +
                                 normalized.substring(12, 16) + "-" + normalized.substring(16, 20) + "-" +
                                 normalized.substring(20);
                }
                UUID uuid = UUID.fromString(normalized);
                String name = resolveName(uuid);
                if (name != null && !name.isEmpty()) {
                    playersByName.putIfAbsent(name.toLowerCase(), uuid);
                }
            } catch (IllegalArgumentException ignored) {}
        }
        
        return playersByName.values().stream()
                .map(uuid -> new PlayerRank(uuid, getTotalScore(uuid), null, -1, getElo(uuid)))
                .sorted((a, b) -> {
                    // Combined average of (ELO + Points) for fair ranking
                    int combinedA = (a.elo() + a.score()) / 2;
                    int combinedB = (b.elo() + b.score()) / 2;
                    return Integer.compare(combinedB, combinedA);
                })
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /** Resolve player name from UUID. */
    private String resolveName(UUID uuid) {
        var online = plugin.getServer().getPlayer(uuid);
        if (online != null) return online.getName();
        var offline = plugin.getServer().getOfflinePlayer(uuid);
        return offline.getName();
    }

    /** Top N jugadores para un kit específico, ordenados por puntos del kit. Desempata por ELO. */
    public List<PlayerRank> getTopForKit(String kitName, int limit) {
        String kit = kitName.toLowerCase();
        List<PlayerRank> list = new ArrayList<>();
        for (var entry : pointsByKit.entrySet()) {
            Integer pts = entry.getValue().get(kit);
            if (pts != null && pts >= 0) {
                list.add(new PlayerRank(entry.getKey(), getTotalScore(entry.getKey()), kit, pts, getElo(entry.getKey())));
            }
        }
        // Sort by kit points (higher first), then by ELO as tiebreaker (higher first)
        list.sort((a, b) -> {
            int cmp = Integer.compare(b.kitPoints(), a.kitPoints()); // Higher points first
            if (cmp != 0) return cmp;
            return Integer.compare(b.elo(), a.elo()); // Higher ELO first as tiebreaker
        });
        return list.stream().limit(limit).collect(Collectors.toList());
    }
    
    /** Helper to get player's ELO for tiebreaker sorting. */
    private int getElo(UUID uuid) {
        return plugin.getEloManager().getElo(uuid);
    }

    /** Todos los UUIDs con al menos un registro de tier. */
    public Set<UUID> getAllTrackedPlayers() {
        return Collections.unmodifiableSet(pointsByKit.keySet());
    }
    
    /** Número de jugadores con datos de tier. */
    public int getPlayerCount() {
        return pointsByKit.size();
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

    // ── Discord Link ──────────────────────────────────────────────────────

    /**
     * Generates a 6-digit code and stores it as a pending link.
     * The Discord bot sends this code to the player in-game; the player
     * then tells the bot the code to confirm.
     * @return the generated code
     */
    public String createLinkCode(UUID uuid, String discordId, String discordUsername) {
        // Expire old codes after 5 minutes
        long now = System.currentTimeMillis();
        pendingLinkCodes.entrySet().removeIf(e -> now - Long.parseLong(e.getValue()[3]) > 300_000);

        String code = String.format("%06d", new java.util.Random().nextInt(1_000_000));
        pendingLinkCodes.put(code, new String[]{ uuid.toString(), discordId, discordUsername, String.valueOf(now) });
        return code;
    }

    /**
     * Confirms a pending link code. Returns the UUID that was linked, or null if invalid/expired.
     */
    public UUID confirmLinkCode(String code, String discordId) {
        String[] data = pendingLinkCodes.remove(code);
        if (data == null) return null;
        long age = System.currentTimeMillis() - Long.parseLong(data[3]);
        if (age > 300_000) return null; // expired
        if (!data[1].equals(discordId)) return null; // wrong Discord account

        UUID uuid = UUID.fromString(data[0]);
        discordLinks.put(uuid, discordId);
        save();
        return uuid;
    }

    /** Returns the Discord ID linked to this Minecraft UUID, or null if not linked. */
    public String getLinkedDiscord(UUID uuid) {
        return discordLinks.get(uuid);
    }

    /** Returns the Minecraft UUID linked to this Discord ID, or null. */
    public UUID getLinkedUUID(String discordId) {
        return discordLinks.entrySet().stream()
                .filter(e -> e.getValue().equals(discordId))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }

    // ── Inner record ─────────────────────────────────────────────────────

    public record PlayerRank(UUID uuid, int score, String kit, int kitPoints, int elo) {}
}
