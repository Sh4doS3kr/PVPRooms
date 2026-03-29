package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages holograms for leaderboards, stats, and information displays.
 */
public class LeaderboardHologramManager {

    public enum HoloType {
        TOP_GENERAL, TOP_KIT, TOP_ELO, TOP_WINS, TOP_STREAK, TOP_KDR,
        STATS_ONLINE, STATS_DUELS, STATS_QUEUE, STATS_TODAY, STATS_WEEK,
        INFO_WELCOME, INFO_RULES, INFO_RANKS, INFO_KITS, INFO_COMMANDS, INFO_REWARDS, INFO_ELO, INFO_SEASONS,
        EVENT_NEXT, EVENT_ACTIVE, EVENT_WINNERS,
        CUSTOM
    }

    public record HoloData(int id, HoloType type, String subtype, Location location, int refreshSeconds, int lines, List<String> customLines, List<UUID> entityUuids) {}

    private final PvPRoomsPro plugin;
    private final Map<Integer, HoloData> holograms = new ConcurrentHashMap<>();
    private final Deque<Integer> undoStack = new ArrayDeque<>();
    private int nextId = 1;
    private File dataFile;
    private BukkitTask refreshTask;

    public LeaderboardHologramManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "holograms.yml");
        load();
        startRefreshTask();
    }

    public int createHologram(HoloType type, String subtype, Location loc) {
        int id = nextId++;
        
        List<String> lines = generateLines(type, subtype, 10);
        List<UUID> entities = spawnHologramLines(loc, lines);
        
        HoloData data = new HoloData(id, type, subtype, loc.clone(), 30, 10, new ArrayList<>(), entities);
        holograms.put(id, data);
        undoStack.push(id);
        
        save();
        return id;
    }

    public int createCustomHologram(Location loc, List<String> customLines) {
        int id = nextId++;
        
        List<UUID> entities = spawnHologramLines(loc, customLines);
        
        HoloData data = new HoloData(id, HoloType.CUSTOM, null, loc.clone(), 0, customLines.size(), new ArrayList<>(customLines), entities);
        holograms.put(id, data);
        undoStack.push(id);
        
        save();
        return id;
    }

    private static final String HOLO_TAG = "pvprooms_hologram";

    private List<UUID> spawnHologramLines(Location baseLoc, List<String> lines) {
        List<UUID> uuids = new ArrayList<>();
        double y = baseLoc.getY() + (lines.size() * 0.28);
        
        for (String line : lines) {
            Location loc = baseLoc.clone();
            loc.setY(y);
            
            ArmorStand stand = (ArmorStand) baseLoc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setCustomName(colorize(line));
            stand.setCustomNameVisible(true);
            stand.setInvulnerable(true);
            stand.addScoreboardTag(HOLO_TAG);
            
            uuids.add(stand.getUniqueId());
            y -= 0.28;
        }
        
        return uuids;
    }

    /** Remove all armor stands with our tag near a location */
    private void removeHologramEntitiesNear(Location loc, double radius) {
        if (loc.getWorld() == null) return;
        loc.getWorld().getNearbyEntities(loc, radius, radius, radius).stream()
            .filter(e -> e instanceof ArmorStand)
            .filter(e -> e.getScoreboardTags().contains(HOLO_TAG))
            .forEach(e -> e.remove());
    }

    /** Remove all hologram armor stands in all worlds */
    public void removeAllHologramEntities() {
        for (var world : Bukkit.getWorlds()) {
            world.getEntities().stream()
                .filter(e -> e instanceof ArmorStand)
                .filter(e -> e.getScoreboardTags().contains(HOLO_TAG))
                .forEach(e -> e.remove());
        }
    }

    public boolean deleteHologram(int id) {
        HoloData data = holograms.remove(id);
        if (data == null) return false;
        
        // Remove entities by tag near location (more reliable)
        removeHologramEntitiesNear(data.location(), 5.0);
        
        // Also try by UUID as backup
        for (UUID uuid : data.entityUuids()) {
            Bukkit.getWorlds().forEach(w -> w.getEntities().stream()
                .filter(e -> e.getUniqueId().equals(uuid))
                .forEach(e -> e.remove()));
        }
        
        save();
        return true;
    }

    public Integer undoLast() {
        if (undoStack.isEmpty()) return null;
        int id = undoStack.pop();
        if (deleteHologram(id)) return id;
        return null;
    }

    public HoloData getNearestHologram(Location loc, double maxDistance) {
        return holograms.values().stream()
            .filter(h -> h.location().getWorld().equals(loc.getWorld()))
            .filter(h -> h.location().distance(loc) <= maxDistance)
            .min(Comparator.comparingDouble(h -> h.location().distance(loc)))
            .orElse(null);
    }

    public void moveHologram(int id, Location newLoc) {
        HoloData old = holograms.get(id);
        if (old == null) return;
        
        // Remove old entities
        for (UUID uuid : old.entityUuids()) {
            Bukkit.getWorlds().forEach(w -> w.getEntities().stream()
                .filter(e -> e.getUniqueId().equals(uuid))
                .forEach(e -> e.remove()));
        }
        
        // Spawn new
        List<String> lines = old.type() == HoloType.CUSTOM ? old.customLines() : generateLines(old.type(), old.subtype(), old.lines());
        List<UUID> newEntities = spawnHologramLines(newLoc, lines);
        
        holograms.put(id, new HoloData(id, old.type(), old.subtype(), newLoc.clone(), old.refreshSeconds(), old.lines(), old.customLines(), newEntities));
        save();
    }

    public void addLine(int id, String text) {
        HoloData old = holograms.get(id);
        if (old == null || old.type() != HoloType.CUSTOM) return;
        
        List<String> newLines = new ArrayList<>(old.customLines());
        newLines.add(text);
        
        // Remove and recreate
        for (UUID uuid : old.entityUuids()) {
            Bukkit.getWorlds().forEach(w -> w.getEntities().stream()
                .filter(e -> e.getUniqueId().equals(uuid))
                .forEach(e -> e.remove()));
        }
        
        List<UUID> newEntities = spawnHologramLines(old.location(), newLines);
        holograms.put(id, new HoloData(id, HoloType.CUSTOM, null, old.location(), 0, newLines.size(), newLines, newEntities));
        save();
    }

    public void setLine(int id, int lineNum, String text) {
        HoloData old = holograms.get(id);
        if (old == null || old.type() != HoloType.CUSTOM) return;
        if (lineNum < 0 || lineNum >= old.customLines().size()) return;
        
        List<String> newLines = new ArrayList<>(old.customLines());
        newLines.set(lineNum, text);
        
        // Remove and recreate
        for (UUID uuid : old.entityUuids()) {
            Bukkit.getWorlds().forEach(w -> w.getEntities().stream()
                .filter(e -> e.getUniqueId().equals(uuid))
                .forEach(e -> e.remove()));
        }
        
        List<UUID> newEntities = spawnHologramLines(old.location(), newLines);
        holograms.put(id, new HoloData(id, HoloType.CUSTOM, null, old.location(), 0, newLines.size(), newLines, newEntities));
        save();
    }

    public void deleteLine(int id, int lineNum) {
        HoloData old = holograms.get(id);
        if (old == null || old.type() != HoloType.CUSTOM) return;
        if (lineNum < 0 || lineNum >= old.customLines().size()) return;
        
        List<String> newLines = new ArrayList<>(old.customLines());
        newLines.remove(lineNum);
        
        // Remove and recreate
        for (UUID uuid : old.entityUuids()) {
            Bukkit.getWorlds().forEach(w -> w.getEntities().stream()
                .filter(e -> e.getUniqueId().equals(uuid))
                .forEach(e -> e.remove()));
        }
        
        List<UUID> newEntities = spawnHologramLines(old.location(), newLines);
        holograms.put(id, new HoloData(id, HoloType.CUSTOM, null, old.location(), 0, newLines.size(), newLines, newEntities));
        save();
    }

    public Collection<HoloData> getAllHolograms() {
        return holograms.values();
    }

    public HoloData getHologram(int id) {
        return holograms.get(id);
    }

    private List<String> generateLines(HoloType type, String subtype, int count) {
        List<String> lines = new ArrayList<>();
        
        switch (type) {
            case TOP_GENERAL, TOP_KIT, TOP_ELO, TOP_WINS, TOP_STREAK, TOP_KDR -> {
                String title = switch(type) {
                    case TOP_GENERAL -> "&5&l⚔ TOP PLAYERS";
                    case TOP_KIT -> "&5&l⚔ TOP " + (subtype != null ? subtype.toUpperCase() : "KIT");
                    case TOP_ELO -> "&6&l★ TOP ELO";
                    case TOP_WINS -> "&a&l✓ TOP WINS";
                    case TOP_STREAK -> "&c&l🔥 TOP STREAK";
                    case TOP_KDR -> "&e&l⚡ TOP K/D";
                    default -> "&5&l⚔ LEADERBOARD";
                };
                lines.add(title);
                lines.add("&8&m                              ");
                
                // Get top players based on type
                int rank = 1;
                switch (type) {
                    case TOP_ELO -> {
                        // Use EloManager for ELO rankings
                        List<String> topElo = plugin.getEloManager().getTopPlayers(count);
                        for (String entry : topElo) {
                            String medal = getMedal(rank);
                            String[] parts = entry.split(" §7— §e");
                            String name = parts.length > 0 ? parts[0] : "???";
                            String elo = parts.length > 1 ? parts[1] : "0";
                            lines.add(medal + " &f" + name + " &7- &e" + elo + " ELO");
                            rank++;
                        }
                    }
                    case TOP_WINS -> {
                        // Use StatsManager for wins
                        var topWins = plugin.getStatsManager().getTopByWins(count);
                        for (var entry : topWins) {
                            String medal = getMedal(rank);
                            String name = plugin.getStatsManager().getNameMap().getOrDefault(entry.getKey(), "???");
                            lines.add(medal + " &f" + name + " &7- &a" + entry.getValue() + " victorias");
                            rank++;
                        }
                    }
                    case TOP_STREAK -> {
                        // Use StatsManager for streaks
                        var topStreak = plugin.getStatsManager().getTopByStreak(count);
                        for (var entry : topStreak) {
                            String medal = getMedal(rank);
                            String name = plugin.getStatsManager().getNameMap().getOrDefault(entry.getKey(), "???");
                            lines.add(medal + " &f" + name + " &7- &c" + entry.getValue() + " racha");
                            rank++;
                        }
                    }
                    case TOP_KDR -> {
                        // Use StatsManager for K/D ratio
                        var topKDR = plugin.getStatsManager().getTopByKDR(count);
                        for (var entry : topKDR) {
                            String medal = getMedal(rank);
                            String name = plugin.getStatsManager().getNameMap().getOrDefault(entry.getKey(), "???");
                            String kdr = String.format("%.2f", entry.getValue());
                            lines.add(medal + " &f" + name + " &7- &e" + kdr + " K/D");
                            rank++;
                        }
                    }
                    default -> {
                        // Use TierManager for points-based rankings (TOP_GENERAL, TOP_KIT)
                        List<TierManager.PlayerRank> top = plugin.getTierManager().getTopPlayers(count);
                        for (TierManager.PlayerRank ps : top) {
                            String medal = getMedal(rank);
                            String name = plugin.getServer().getOfflinePlayer(ps.uuid()).getName();
                            if (name == null) name = "???";
                            lines.add(medal + " &f" + name + " &7- &f" + ps.score() + " pts");
                            rank++;
                        }
                    }
                }
                lines.add("&8&m                              ");
            }
            
            case STATS_ONLINE -> {
                lines.add("&a&l⬤ ONLINE");
                lines.add("&8&m              ");
                lines.add("&7Players: &a" + Bukkit.getOnlinePlayers().size());
                lines.add("&8&m              ");
            }
            
            case STATS_DUELS -> {
                lines.add("&c&l⚔ ACTIVE DUELS");
                lines.add("&8&m              ");
                int duels = plugin.getDuelManager() != null ? plugin.getDuelManager().getActiveDuels().size() : 0;
                lines.add("&7Duels: &c" + duels);
                lines.add("&8&m              ");
            }
            
            case STATS_QUEUE -> {
                lines.add("&e&l⏳ QUEUE");
                lines.add("&8&m              ");
                int queue = plugin.getQueueManager() != null ? plugin.getQueueManager().getTotalQueued() : 0;
                lines.add("&7In queue: &e" + queue);
                lines.add("&8&m              ");
            }
            
            case INFO_WELCOME -> {
                lines.add("&5&l✦ BIENVENIDO ✦");
                lines.add("&8&m                    ");
                lines.add("&7¡Bienvenido al servidor!");
                lines.add("&7Usa &e/queue &7para jugar");
                lines.add("&7Usa &e/stats &7para ver tu perfil");
                lines.add("&8&m                    ");
            }
            
            case INFO_RULES -> {
                lines.add("&c&l✖ REGLAS ✖");
                lines.add("&8&m                    ");
                lines.add("&71. No hacks ni cheats");
                lines.add("&72. No insultar");
                lines.add("&73. No teaming en FFA");
                lines.add("&74. Respeta a todos");
                lines.add("&8&m                    ");
            }
            
            case INFO_RANKS -> {
                lines.add("&5&l★ SISTEMA DE RANGOS ★");
                lines.add("&8&m                    ");
                lines.add("&8◆ Sin Rango &7- 0 pts");
                lines.add("&7◆ Hierro &7- 0+ pts");
                lines.add("&6◆ Bronce &7- 100+ pts");
                lines.add("&f◆ Plata &7- 300+ pts");
                lines.add("&e◆ Oro &7- 600+ pts");
                lines.add("&a◆ Esmeralda &7- 1000+ pts");
                lines.add("&b◆ Diamante &7- 1400+ pts");
                lines.add("&d◆ Maestro &7- 1900+ pts");
                lines.add("&c◆ Leyenda &7- 2600+ pts");
                lines.add("&8&m                    ");
            }
            
            case INFO_KITS -> {
                lines.add("&e&l⚔ KITS DISPONIBLES ⚔");
                lines.add("&8&m                    ");
                if (plugin.getKitManager() != null) {
                    plugin.getKitManager().getKitNames().forEach(k -> 
                        lines.add("&7• &f" + k)
                    );
                }
                lines.add("&8&m                    ");
            }
            
            case INFO_COMMANDS -> {
                lines.add("&b&l✎ COMANDOS ✎");
                lines.add("&8&m                    ");
                lines.add("&e/queue &7- Unirse a cola");
                lines.add("&e/stats &7- Ver estadísticas");
                lines.add("&e/leaderboard &7- Rankings");
                lines.add("&e/duel <player> &7- Retar");
                lines.add("&e/spectate &7- Ver partidas");
                lines.add("&8&m                    ");
            }
            
            case INFO_ELO -> {
                lines.add("&6&l★ SISTEMA ELO ★");
                lines.add("&8&m                    ");
                lines.add("&7ELO inicial: &f1000");
                lines.add("&7Ganas: &a+15 a +30 ELO");
                lines.add("&7Pierdes: &c-10 a -25 ELO");
                lines.add("&7Basado en tu rival");
                lines.add("&8&m                    ");
            }
            
            default -> {
                lines.add("&7Hologram");
            }
        }
        
        return lines;
    }

    private String getMedal(int rank) {
        return switch(rank) {
            case 1 -> "&6①";
            case 2 -> "&7②";
            case 3 -> "&c③";
            default -> "&8" + rank + ".";
        };
    }

    private String getTierColor(String tier) {
        if (tier == null) return "&7";
        return switch(tier.toLowerCase()) {
            case "hierro" -> "&7";
            case "bronce" -> "&6";
            case "plata" -> "&f";
            case "oro" -> "&e";
            case "esmeralda" -> "&a";
            case "diamante" -> "&b";
            case "maestro" -> "&d";
            case "leyenda" -> "&c";
            default -> "&7";
        };
    }

    private void startRefreshTask() {
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAllHolograms, 20L * 30, 20L * 30);
    }

    private void refreshAllHolograms() {
        for (HoloData holo : holograms.values()) {
            if (holo.type() == HoloType.CUSTOM) continue;
            if (holo.refreshSeconds() <= 0) continue;
            
            // Remove old entities by tag near location (reliable)
            removeHologramEntitiesNear(holo.location(), 5.0);
            
            // Spawn new
            List<String> lines = generateLines(holo.type(), holo.subtype(), holo.lines());
            List<UUID> newEntities = spawnHologramLines(holo.location(), lines);
            
            holograms.put(holo.id(), new HoloData(holo.id(), holo.type(), holo.subtype(), holo.location(), holo.refreshSeconds(), holo.lines(), holo.customLines(), newEntities));
        }
    }

    public void load() {
        // Clean up ALL hologram entities first to prevent accumulation
        Bukkit.getScheduler().runTaskLater(plugin, this::removeAllHologramEntities, 20L);
        
        if (!dataFile.exists()) return;
        
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = cfg.getConfigurationSection("holograms");
        if (section == null) return;
        
        for (String key : section.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                ConfigurationSection holoSec = section.getConfigurationSection(key);
                if (holoSec == null) continue;
                
                HoloType type = HoloType.valueOf(holoSec.getString("type", "CUSTOM"));
                String subtype = holoSec.getString("subtype");
                int refresh = holoSec.getInt("refresh_seconds", 30);
                int lineCount = holoSec.getInt("lines", 10);
                List<String> customLines = holoSec.getStringList("custom_lines");
                
                ConfigurationSection locSec = holoSec.getConfigurationSection("location");
                if (locSec == null) continue;
                
                Location loc = new Location(
                    Bukkit.getWorld(locSec.getString("world", "world")),
                    locSec.getDouble("x"),
                    locSec.getDouble("y"),
                    locSec.getDouble("z")
                );
                
                if (loc.getWorld() == null) continue;
                
                List<String> lines = type == HoloType.CUSTOM ? customLines : generateLines(type, subtype, lineCount);
                List<UUID> entities = spawnHologramLines(loc, lines);
                
                HoloData data = new HoloData(id, type, subtype, loc, refresh, lineCount, customLines, entities);
                holograms.put(id, data);
                
                if (id >= nextId) nextId = id + 1;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load hologram " + key + ": " + e.getMessage());
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        
        for (HoloData holo : holograms.values()) {
            String path = "holograms." + holo.id();
            cfg.set(path + ".type", holo.type().name());
            cfg.set(path + ".subtype", holo.subtype());
            cfg.set(path + ".refresh_seconds", holo.refreshSeconds());
            cfg.set(path + ".lines", holo.lines());
            cfg.set(path + ".custom_lines", holo.customLines());
            
            Location loc = holo.location();
            cfg.set(path + ".location.world", loc.getWorld().getName());
            cfg.set(path + ".location.x", loc.getX());
            cfg.set(path + ".location.y", loc.getY());
            cfg.set(path + ".location.z", loc.getZ());
        }
        
        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save holograms: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (refreshTask != null) refreshTask.cancel();
        
        for (HoloData holo : holograms.values()) {
            for (UUID uuid : holo.entityUuids()) {
                Bukkit.getWorlds().forEach(w -> w.getEntities().stream()
                    .filter(e -> e.getUniqueId().equals(uuid))
                    .forEach(e -> e.remove()));
            }
        }
    }

    private String colorize(String text) {
        return text == null ? "" : text.replace("&", "§");
    }
}
