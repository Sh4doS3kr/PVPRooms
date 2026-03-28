package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.WallBlock;
import com.pvprooms.model.WallConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages arena wall configurations. Supports multiple named walls per arena.
 *
 * Flow:
 *  1. /admin setupwall <wallId>           → gives selection hoe tagged with wallId
 *  2. Left-click block = pos A,  right-click block = pos B
 *  3. /admin setupwall <wallId> <block>   → scans region, saves wall
 *
 * walls.yml format:
 *   walls:
 *     arena1:
 *       norte:
 *         blocks: ["x,y,z,MATERIAL", ...]
 *       sur:
 *         blocks: [...]
 *
 * On duel start → animateOpen()  opens ALL walls of the arena (bottom→top)
 * On duel end   → animateClose() closes ALL walls of the arena (top→bottom)
 */
public class WallManager {

    public static final String TOOL_PREFIX = "§6§lWall Setup: §e";

    private final PvPRoomsPro plugin;
    private final File wallsFile;
    private FileConfiguration wallsConfig;

    /** arena (lower) → wallId (lower) → WallConfig */
    private final Map<String, Map<String, WallConfig>> walls = new HashMap<>();

    /** player UUID → { pos1, pos2 } */
    private final Map<UUID, Location[]> selections = new HashMap<>();

    /** player UUID → wallId currently being configured */
    private final Map<UUID, String> pendingWallId = new HashMap<>();

    public WallManager(PvPRoomsPro plugin) {
        this.plugin    = plugin;
        this.wallsFile = new File(plugin.getDataFolder(), "walls.yml");
        loadWalls();
    }

    // ── Persistence ───────────────────────────────────────────────────────

    public void loadWalls() {
        walls.clear();
        if (!wallsFile.exists()) return;
        wallsConfig = YamlConfiguration.loadConfiguration(wallsFile);
        if (!wallsConfig.contains("walls")) return;

        for (String arena : wallsConfig.getConfigurationSection("walls").getKeys(false)) {
            var arenaSection = wallsConfig.getConfigurationSection("walls." + arena);
            if (arenaSection == null) continue;
            for (String wallId : arenaSection.getKeys(false)) {
                List<String> raw = wallsConfig.getStringList("walls." + arena + "." + wallId + ".blocks");
                List<WallBlock> blocks = new ArrayList<>();
                for (String s : raw) {
                    WallBlock wb = WallBlock.deserialize(s);
                    if (wb != null) blocks.add(wb);
                }
                if (!blocks.isEmpty()) {
                    walls.computeIfAbsent(arena.toLowerCase(), k -> new LinkedHashMap<>())
                            .put(wallId.toLowerCase(), new WallConfig(arena + ":" + wallId, blocks));
                }
            }
        }
        int total = walls.values().stream().mapToInt(Map::size).sum();
        plugin.getLogger().info("Loaded " + total + " wall(s) across " + walls.size() + " arena(s).");
    }

    public void saveWalls() {
        wallsConfig = new YamlConfiguration();
        for (var arenaEntry : walls.entrySet()) {
            for (var wallEntry : arenaEntry.getValue().entrySet()) {
                String path = "walls." + arenaEntry.getKey() + "." + wallEntry.getKey() + ".blocks";
                List<String> raw = new ArrayList<>();
                for (WallBlock wb : wallEntry.getValue().getBlocks()) raw.add(wb.serialize());
                wallsConfig.set(path, raw);
            }
        }
        try {
            wallsConfig.save(wallsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save walls.yml", e);
        }
    }

    // ── Setup tool ────────────────────────────────────────────────────────

    public void giveSetupTool(Player player, String wallId) {
        pendingWallId.put(player.getUniqueId(), wallId.toLowerCase());
        clearSelection(player.getUniqueId());

        ItemStack hoe = new ItemStack(Material.WOODEN_HOE);
        ItemMeta meta = hoe.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TOOL_PREFIX + wallId);
            meta.setLore(List.of(
                    "§7Muro: §e" + wallId,
                    "§7Click izquierdo:  §ePunto A",
                    "§7Click derecho:    §ePunto B",
                    "§7Luego: §f/admin setupwall " + wallId + " <tipo_bloque>"
            ));
            hoe.setItemMeta(meta);
        }
        player.getInventory().addItem(hoe);
        player.sendMessage(plugin.prefix() + "§aHerramienta para muro §e" + wallId + " §arecibida.");
        player.sendMessage(plugin.prefix() + "§fClick izq §8→ §ePunto A  §f| Click der §8→ §ePunto B");
    }

    public boolean isSetupTool(ItemStack item) {
        if (item == null || item.getType() != Material.WOODEN_HOE) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName()
                && meta.getDisplayName().startsWith(TOOL_PREFIX);
    }

    public String getToolWallId(ItemStack item) {
        if (!isSetupTool(item)) return null;
        return item.getItemMeta().getDisplayName().substring(TOOL_PREFIX.length());
    }

    // ── Selection management ──────────────────────────────────────────────

    public void setPos1(UUID uuid, Location loc) {
        selections.computeIfAbsent(uuid, k -> new Location[2])[0] = loc;
    }

    public void setPos2(UUID uuid, Location loc) {
        selections.computeIfAbsent(uuid, k -> new Location[2])[1] = loc;
    }

    public Location[] getSelection(UUID uuid) { return selections.get(uuid); }

    public boolean hasFullSelection(UUID uuid) {
        Location[] sel = selections.get(uuid);
        return sel != null && sel[0] != null && sel[1] != null;
    }

    public void clearSelection(UUID uuid) { selections.remove(uuid); }

    public String getPendingWallId(UUID uuid) { return pendingWallId.get(uuid); }

    // ── Scan and save wall ────────────────────────────────────────────────

    /**
     * Scans the selection, finds blocks of {@code targetMaterial}, and saves them
     * as wall {@code wallId} for the arena. Returns block count, or -1 if no selection.
     */
    public int setupWall(String arenaName, String wallId, UUID adminUUID, Material targetMaterial) {
        Location[] sel = getSelection(adminUUID);
        if (sel == null || sel[0] == null || sel[1] == null) return -1;

        World world = sel[0].getWorld();
        if (world == null) return -1;

        int minX = Math.min(sel[0].getBlockX(), sel[1].getBlockX());
        int maxX = Math.max(sel[0].getBlockX(), sel[1].getBlockX());
        int minY = Math.min(sel[0].getBlockY(), sel[1].getBlockY());
        int maxY = Math.max(sel[0].getBlockY(), sel[1].getBlockY());
        int minZ = Math.min(sel[0].getBlockZ(), sel[1].getBlockZ());
        int maxZ = Math.max(sel[0].getBlockZ(), sel[1].getBlockZ());

        List<WallBlock> blocks = new ArrayList<>();
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == targetMaterial)
                        blocks.add(new WallBlock(x, y, z, block.getType()));
                }

        if (blocks.isEmpty()) return 0;

        walls.computeIfAbsent(arenaName.toLowerCase(), k -> new LinkedHashMap<>())
                .put(wallId.toLowerCase(), new WallConfig(arenaName + ":" + wallId, blocks));
        saveWalls();
        clearSelection(adminUUID);
        pendingWallId.remove(adminUUID);
        return blocks.size();
    }

    /** Removes a specific wall from an arena. Returns false if not found. */
    public boolean removeWall(String arenaName, String wallId) {
        Map<String, WallConfig> arenaWalls = walls.get(arenaName.toLowerCase());
        if (arenaWalls == null) return false;
        boolean removed = arenaWalls.remove(wallId.toLowerCase()) != null;
        if (removed) saveWalls();
        return removed;
    }

    // ── Animation: open (FIGHT!) ──────────────────────────────────────────

    /** Opens ALL configured walls for the arena simultaneously. */
    public void animateOpen(String arenaName, World world) {
        Map<String, WallConfig> arenaWalls = walls.get(arenaName.toLowerCase());
        if (arenaWalls == null || arenaWalls.isEmpty()) return;
        for (WallConfig cfg : arenaWalls.values()) {
            animateSingleOpen(cfg, world);
        }
    }

    private void animateSingleOpen(WallConfig cfg, World world) {
        List<Map.Entry<Integer, List<WallBlock>>> rows =
                new ArrayList<>(cfg.byYAscending().entrySet());

        new BukkitRunnable() {
            int idx = 0;
            @Override public void run() {
                if (!world.isChunkLoaded(0, 0)) { cancel(); return; }
                if (idx >= rows.size()) { cancel(); return; }
                List<WallBlock> row = rows.get(idx).getValue();
                for (WallBlock wb : row)
                    world.getBlockAt(wb.x, wb.y, wb.z).setType(Material.AIR, false);
                float pitch = 0.8f + (0.6f / Math.max(rows.size() - 1, 1)) * idx;
                world.playSound(new Location(world, row.get(0).x, row.get(0).y, row.get(0).z),
                        Sound.BLOCK_FENCE_GATE_OPEN, 0.6f, pitch);
                idx++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ── Animation: close (duel end) ───────────────────────────────────────

    /** Closes ALL configured walls for the arena simultaneously. */
    public void animateClose(String arenaName, World world) {
        Map<String, WallConfig> arenaWalls = walls.get(arenaName.toLowerCase());
        if (arenaWalls == null || arenaWalls.isEmpty()) return;
        for (WallConfig cfg : arenaWalls.values()) {
            animateSingleClose(cfg, world);
        }
    }

    private void animateSingleClose(WallConfig cfg, World world) {
        List<Map.Entry<Integer, List<WallBlock>>> rows =
                new ArrayList<>(cfg.byYDescending().entrySet());

        new BukkitRunnable() {
            int idx = 0;
            @Override public void run() {
                if (!world.isChunkLoaded(0, 0)) { cancel(); return; }
                if (idx >= rows.size()) { cancel(); return; }
                List<WallBlock> row = rows.get(idx).getValue();
                for (WallBlock wb : row)
                    world.getBlockAt(wb.x, wb.y, wb.z).setType(wb.material, false);
                float pitch = 0.8f + (0.6f / Math.max(rows.size() - 1, 1)) * idx;
                world.playSound(new Location(world, row.get(0).x, row.get(0).y, row.get(0).z),
                        Sound.BLOCK_FENCE_GATE_CLOSE, 0.6f, pitch);
                idx++;
            }
        }.runTaskTimer(plugin, 3L, 2L);
    }

    // ── Queries ───────────────────────────────────────────────────────────

    public boolean hasWalls(String arenaName) {
        Map<String, WallConfig> m = walls.get(arenaName.toLowerCase());
        return m != null && !m.isEmpty();
    }

    public Set<String> getWallIds(String arenaName) {
        Map<String, WallConfig> m = walls.get(arenaName.toLowerCase());
        return m != null ? Collections.unmodifiableSet(m.keySet()) : Collections.emptySet();
    }

    public WallConfig getWall(String arenaName, String wallId) {
        Map<String, WallConfig> m = walls.get(arenaName.toLowerCase());
        return m != null ? m.get(wallId.toLowerCase()) : null;
    }
}
