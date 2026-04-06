package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitTask;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages holograms for leaderboards, stats, and information displays.
 */
public class LeaderboardHologramManager implements Listener {

    public enum HoloType {
        TOP_GENERAL, TOP_KIT, TOP_ELO, TOP_WINS, TOP_STREAK, TOP_KDR,
        STATS_ONLINE, STATS_DUELS, STATS_QUEUE, STATS_TODAY, STATS_WEEK,
        INFO_WELCOME, INFO_RULES, INFO_RANKS, INFO_KITS, INFO_COMMANDS, INFO_REWARDS, INFO_ELO, INFO_SEASONS, INFO_WEB,
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
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        // Delay loading until worlds are fully loaded (prevents entities not spawning)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            loadFromConfig();
            spawnAllHolograms();
            startRefreshTask();
            plugin.getLogger().info("[Holograms] Cargados " + holograms.size() + " hologramas.");
        }, 40L); // 2 seconds delay
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // When a chunk loads, check if any holograms need to be respawned
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        
        for (HoloData holo : holograms.values()) {
            Location loc = holo.location();
            if (loc == null || loc.getWorld() == null) continue;
            if (!loc.getWorld().equals(event.getWorld())) continue;
            
            int holoChunkX = loc.getBlockX() >> 4;
            int holoChunkZ = loc.getBlockZ() >> 4;
            
            if (holoChunkX == chunkX && holoChunkZ == chunkZ) {
                // Remove any existing entities first (prevents duplicates)
                removeHologramEntitiesNear(loc, 5.0);
                
                // Respawn the hologram
                List<String> lines = holo.type() == HoloType.CUSTOM ? holo.customLines() : generateLines(holo.type(), holo.subtype(), holo.lines());
                List<UUID> newEntities = spawnHologramLines(loc, lines);
                
                holograms.put(holo.id(), new HoloData(holo.id(), holo.type(), holo.subtype(), loc, holo.refreshSeconds(), holo.lines(), holo.customLines(), newEntities));
            }
        }
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
                    case TOP_GENERAL -> "&6&l⚔ &e&lTOP PLAYERS";
                    case TOP_KIT -> "&6&l⚔ &e&lTOP " + (subtype != null ? subtype.toUpperCase() : "KIT");
                    case TOP_ELO -> "&6&l★ &e&lTOP ELO";
                    case TOP_WINS -> "&a&l✓ &2&lTOP WINS";
                    case TOP_STREAK -> "&c&l🔥 &4&lTOP STREAK";
                    case TOP_KDR -> "&e&l⚡ &6&lTOP K/D";
                    default -> "&6&l⚔ &e&lLEADERBOARD";
                };
                lines.add(title);
                lines.add(" ");
                
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
                if (lines.size() <= 2) {
                    lines.add("&7Sin jugadores aún");
                }
            }
            
            case STATS_ONLINE -> {
                lines.add("&a&l⬤ &2&lONLINE");
                lines.add("&a" + Bukkit.getOnlinePlayers().size() + " &7jugadores");
            }
            
            case STATS_DUELS -> {
                lines.add("&c&l⚔ &4&lDUELOS");
                int duels = plugin.getDuelManager() != null ? plugin.getDuelManager().getActiveDuels().size() : 0;
                lines.add("&c" + duels + " &7en curso");
            }
            
            case STATS_QUEUE -> {
                lines.add("&e&l⏳ &6&lCOLA");
                int queue = plugin.getQueueManager() != null ? plugin.getQueueManager().getTotalQueued() : 0;
                lines.add("&e" + queue + " &7esperando");
            }
            
            case INFO_WELCOME -> {
                lines.add("&5&l✦ &d&lBIENVENIDO &5&l✦");
                lines.add(" ");
                lines.add("&7Usa &e/queue &7para jugar");
                lines.add("&7Usa &e/stats &7para tu perfil");
            }
            
            case INFO_RULES -> {
                lines.add("&c&l✖ &4&lREGLAS &c&l✖");
                lines.add(" ");
                lines.add("&71. No hacks ni cheats");
                lines.add("&72. No insultar");
                lines.add("&73. No teaming en FFA");
                lines.add("&74. Respeta a todos");
            }
            
            case INFO_RANKS -> {
                lines.add("&6&l★ &e&lSISTEMA TIERS &6&l★");
                lines.add(" ");
                lines.add("&9◆ LT5 &8→ &b◆ HT5");
                lines.add("&a◆ LT4 &8→ &2◆ HT4");
                lines.add("&e◆ LT3 &8→ &6◆ HT3");
                lines.add(" ");
                lines.add("&d&l⚡ TIERS ÉLITE &d&l⚡");
                lines.add("&c◆ LT2 &8→ &4◆ HT2");
                lines.add("&d◆ LT1 &8→ &c&l◆ HT1");
                lines.add(" ");
                lines.add("&b&ndiscord.mlmc.lat");
            }
            
            case INFO_KITS -> {
                lines.add("&e&l⚔ &6&lKITS &e&l⚔");
                lines.add(" ");
                if (plugin.getKitManager() != null) {
                    plugin.getKitManager().getKitNames().forEach(k -> 
                        lines.add("&7• &f" + k)
                    );
                }
            }
            
            case INFO_COMMANDS -> {
                lines.add("&b&l✎ &3&lCOMANDOS &b&l✎");
                lines.add(" ");
                lines.add("&e/queue &7- Jugar");
                lines.add("&e/stats &7- Estadísticas");
                lines.add("&e/duel &7- Retar");
            }
            
            case INFO_ELO -> {
                lines.add("&6&l★ &e&lSISTEMA ELO &6&l★");
                lines.add(" ");
                lines.add("&7Inicial: &f1000 ELO");
                lines.add("&7Ganas: &a+15 a +30");
                lines.add("&7Pierdes: &c-10 a -25");
            }
            
            case INFO_WEB -> {
                lines.add("&6&l★ &e&lPÁGINA WEB &6&l★");
                lines.add(" ");
                lines.add("&b&ntiers.mlmc.lat");
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
            case "lt5" -> "&9";
            case "ht5" -> "&b";
            case "lt4" -> "&a";
            case "ht4" -> "&2";
            case "lt3" -> "&e";
            case "ht3" -> "&6";
            case "lt2" -> "&c";
            case "ht2" -> "&4";
            case "lt1" -> "&d";
            case "ht1" -> "&c&l";
            default -> "&7";
        };
    }

    private void startRefreshTask() {
        // Cancel existing task if any
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        // Refresh every 10 seconds
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAllHolograms, 20L * 10, 20L * 10);
    }

    private void refreshAllHolograms() {
        for (HoloData holo : holograms.values()) {
            // Skip custom holograms (they don't need refresh)
            if (holo.type() == HoloType.CUSTOM) continue;
            
            Location loc = holo.location();
            if (loc == null || loc.getWorld() == null) continue;
            
            // Load chunk if needed to ensure hologram is visible
            int chunkX = loc.getBlockX() >> 4;
            int chunkZ = loc.getBlockZ() >> 4;
            if (!loc.getWorld().isChunkLoaded(chunkX, chunkZ)) {
                // Don't load chunk just for refresh, but mark for update when loaded
                continue;
            }
            
            // Remove old entities by tag near location
            removeHologramEntitiesNear(loc, 5.0);
            
            // Generate fresh lines with current data
            List<String> lines = generateLines(holo.type(), holo.subtype(), holo.lines());
            List<UUID> newEntities = spawnHologramLines(loc, lines);
            
            // Update the hologram data with new entity UUIDs
            holograms.put(holo.id(), new HoloData(holo.id(), holo.type(), holo.subtype(), loc, holo.refreshSeconds(), holo.lines(), holo.customLines(), newEntities));
        }
    }

    /** Load hologram data from config WITHOUT spawning entities */
    private void loadFromConfig() {
        holograms.clear();
        
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
                
                String worldName = locSec.getString("world", "world");
                double x = locSec.getDouble("x");
                double y = locSec.getDouble("y");
                double z = locSec.getDouble("z");
                
                var world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("[Holograms] Mundo '" + worldName + "' no encontrado para holograma " + id);
                    continue;
                }
                
                Location loc = new Location(world, x, y, z);
                
                // Store data without entities (will spawn later)
                HoloData data = new HoloData(id, type, subtype, loc, refresh, lineCount, customLines, new ArrayList<>());
                holograms.put(id, data);
                
                if (id >= nextId) nextId = id + 1;
            } catch (Exception e) {
                plugin.getLogger().warning("[Holograms] Error cargando holograma " + key + ": " + e.getMessage());
            }
        }
    }
    
    /** Spawn all holograms from loaded data */
    private void spawnAllHolograms() {
        // First clean up any existing hologram entities
        removeAllHologramEntities();
        
        for (Map.Entry<Integer, HoloData> entry : holograms.entrySet()) {
            HoloData holo = entry.getValue();
            Location loc = holo.location();
            
            if (loc == null || loc.getWorld() == null) continue;
            
            // Load chunk if needed
            if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                loc.getWorld().loadChunk(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
            }
            
            List<String> lines = holo.type() == HoloType.CUSTOM ? holo.customLines() : generateLines(holo.type(), holo.subtype(), holo.lines());
            List<UUID> entities = spawnHologramLines(loc, lines);
            
            // Update with entity UUIDs
            holograms.put(holo.id(), new HoloData(holo.id(), holo.type(), holo.subtype(), loc, holo.refreshSeconds(), holo.lines(), holo.customLines(), entities));
        }
    }
    
    /** Legacy method for compatibility */
    public void load() {
        loadFromConfig();
        spawnAllHolograms();
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
