package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.WallBlock;
import com.pvprooms.model.WallConfig;
import org.bukkit.Bukkit;
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

    // ── Animation: open (FIGHT!) — portcullis rising ────────────────────────────

    /** Opens ALL configured walls for the arena simultaneously. */
    public void animateOpen(String arenaName, World world) {
        plugin.getLogger().info("[Walls] animateOpen called for arena: '" + arenaName + "' in world: " + (world != null ? world.getName() : "null"));
        plugin.getLogger().info("[Walls] Available arenas in walls map: " + walls.keySet());
        
        Map<String, WallConfig> arenaWalls = walls.get(arenaName.toLowerCase());
        if (arenaWalls == null || arenaWalls.isEmpty()) {
            plugin.getLogger().warning("[Walls] No walls found for arena: " + arenaName.toLowerCase());
            return;
        }
        
        plugin.getLogger().info("[Walls] Found " + arenaWalls.size() + " wall(s) for arena " + arenaName);
        for (WallConfig cfg : arenaWalls.values()) {
            plugin.getLogger().info("[Walls] Opening wall with " + cfg.getBlocks().size() + " blocks");
            animateSingleOpen(cfg, world);
        }
    }

    /**
     * Portcullis rising: each step removes the bottom row and places a copy
     * one block above the current top, so the wall physically travels upward.
     *
     * Timeline (wall originally at Y=minY..maxY, height=H):
     *   step 0 : remove Y=minY,   place Y=maxY+1  → wall at [minY+1..maxY+1]
     *   step 1 : remove Y=minY+1, place Y=maxY+2  → wall at [minY+2..maxY+2]
     *   ...
     *   step H-1: original blocks all gone, wall now at [maxY+1..maxY+H] (above play space)
     */
    private void animateSingleOpen(WallConfig cfg, World world) {
        TreeMap<Integer, List<WallBlock>> byY = cfg.byYAscending();
        List<Integer> yLevels = new ArrayList<>(byY.keySet());
        int H = yLevels.size();
        if (H == 0) return;
        int maxY = cfg.getMaxY();

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!isWorldAlive(world)) { cancel(); return; }
                if (step >= H) {
                    // All original blocks gone — play final clunk
                    WallBlock ref = cfg.getBlocks().get(0);
                    world.playSound(new Location(world, ref.x, maxY + H, ref.z),
                            Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1.0f, 1.4f);
                    cancel();
                    return;
                }

                int removeY = yLevels.get(step);
                List<WallBlock> removedRow = byY.get(removeY);

                int placeY = maxY + step + 1;

                // 1. PLACE new top row first (wall is momentarily H+1 tall — no visual gap)
                for (WallBlock wb : removedRow)
                    world.getBlockAt(wb.x, placeY, wb.z).setType(wb.material, true);

                // 2. Remove bottom row after (wall drops back to H tall)
                for (WallBlock wb : removedRow)
                    world.getBlockAt(wb.x, wb.y, wb.z).setType(Material.AIR, true);

                // 3. Sound: metallic chain scraping upward
                float pitch = 0.75f + (0.5f / Math.max(H - 1, 1)) * step;
                Location soundLoc = new Location(world, removedRow.get(0).x, removeY, removedRow.get(0).z);
                world.playSound(soundLoc, Sound.BLOCK_CHAIN_STEP,  0.9f, pitch);
                if (step % 3 == 0)
                    world.playSound(soundLoc, Sound.BLOCK_PISTON_EXTEND, 0.35f, pitch);

                step++;
            }
        }.runTaskTimer(plugin, 0L, 3L); // 3 ticks per row = 150ms each
    }

    // ── Animation: close (duel end) — portcullis descending ─────────────────────

    /** Closes ALL configured walls for the arena simultaneously. */
    public void animateClose(String arenaName, World world) {
        Map<String, WallConfig> arenaWalls = walls.get(arenaName.toLowerCase());
        if (arenaWalls == null || arenaWalls.isEmpty()) return;
        for (WallConfig cfg : arenaWalls.values()) animateSingleClose(cfg, world);
    }

    /**
     * Portcullis descending: restores original blocks from top to bottom
     * while removing the floating rows above. Looks like the wall comes back down.
     */
    private void animateSingleClose(WallConfig cfg, World world) {
        TreeMap<Integer, List<WallBlock>> byYDesc = cfg.byYDescending();
        List<Integer> yLevels = new ArrayList<>(byYDesc.keySet()); // top→bottom
        int H = yLevels.size();
        if (H == 0) return;
        int maxY = cfg.getMaxY();

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!isWorldAlive(world)) { cancel(); return; }
                if (step >= H) {
                    WallBlock ref = cfg.getBlocks().get(0);
                    world.playSound(new Location(world, ref.x, ref.y, ref.z),
                            Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.0f, 0.8f);
                    cancel();
                    return;
                }

                int restoreY = yLevels.get(step);
                List<WallBlock> row = byYDesc.get(restoreY);

                int clearY = maxY + (H - step); // topmost floating row first

                // 1. RESTORE original row first (wall momentarily H+1 tall — no gap)
                for (WallBlock wb : row)
                    world.getBlockAt(wb.x, wb.y, wb.z).setType(wb.material, true);

                // 2. Remove floating row above after
                for (WallBlock wb : row)
                    world.getBlockAt(wb.x, clearY, wb.z).setType(Material.AIR, true);

                float pitch = 1.25f - (0.5f / Math.max(H - 1, 1)) * step;
                Location soundLoc = new Location(world, row.get(0).x, restoreY, row.get(0).z);
                world.playSound(soundLoc, Sound.BLOCK_CHAIN_STEP,  0.9f, pitch);
                if (step % 3 == 0)
                    world.playSound(soundLoc, Sound.BLOCK_PISTON_CONTRACT, 0.35f, pitch);

                step++;

                // Final step: force-update all restored blocks so they reconnect
                if (step == H) {
                    for (WallBlock wb : cfg.getBlocks())
                        world.getBlockAt(wb.x, wb.y, wb.z).getState().update(true, false);
                }
            }
        }.runTaskTimer(plugin, 4L, 3L);
    }

    private boolean isWorldAlive(World world) {
        return world != null && Bukkit.getWorld(world.getName()) != null;
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
