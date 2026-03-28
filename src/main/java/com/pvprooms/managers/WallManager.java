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
 * Manages arena wall configurations.
 *
 * Flow:
 *  1. Admin runs /admin setupwall → gets the selection hoe
 *  2. Left-click  = position A,  right-click = position B
 *  3. Admin runs /admin setupwall <blockType>
 *     (arena is inferred from the world the admin is currently in)
 *  4. WallManager scans the region, saves block positions to walls.yml
 *
 * On duel start  → animateOpen()  (bottom→top, rows every 2 ticks)
 * On duel end    → animateClose() (top→bottom, rows every 2 ticks)
 */
public class WallManager {

    public static final String TOOL_NAME = "§6§lWall Setup Tool";

    private final PvPRoomsPro plugin;
    private final File wallsFile;
    private FileConfiguration wallsConfig;

    /** arena name (lower) → WallConfig */
    private final Map<String, WallConfig> walls = new HashMap<>();

    /** player UUID → { pos1, pos2 } selection */
    private final Map<UUID, Location[]> selections = new HashMap<>();

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
            List<String> raw = wallsConfig.getStringList("walls." + arena + ".blocks");
            List<WallBlock> blocks = new ArrayList<>();
            for (String s : raw) {
                WallBlock wb = WallBlock.deserialize(s);
                if (wb != null) blocks.add(wb);
            }
            if (!blocks.isEmpty()) {
                walls.put(arena.toLowerCase(), new WallConfig(arena, blocks));
            }
        }
        plugin.getLogger().info("Loaded wall configs for " + walls.size() + " arena(s).");
    }

    public void saveWalls() {
        wallsConfig = new YamlConfiguration();
        for (WallConfig cfg : walls.values()) {
            List<String> raw = new ArrayList<>();
            for (WallBlock wb : cfg.getBlocks()) raw.add(wb.serialize());
            wallsConfig.set("walls." + cfg.getArenaName() + ".blocks", raw);
        }
        try {
            wallsConfig.save(wallsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save walls.yml", e);
        }
    }

    // ── Setup tool ────────────────────────────────────────────────────────

    /** Gives the wall-setup hoe to a player. */
    public void giveSetupTool(Player player) {
        ItemStack hoe = new ItemStack(Material.WOODEN_HOE);
        ItemMeta meta = hoe.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TOOL_NAME);
            meta.setLore(List.of(
                    "§7Click izquierdo: §ePunto A",
                    "§7Click derecho:   §ePunto B",
                    "§7Luego: §f/admin setupwall <tipo_bloque>"
            ));
            hoe.setItemMeta(meta);
        }
        player.getInventory().addItem(hoe);
        player.sendMessage(plugin.prefix() + "§a¡Herramienta de muro recibida!");
        player.sendMessage(plugin.prefix() + "§7 §fClick izq §8→ §ePunto A  §f| Click der §8→ §ePunto B");
        player.sendMessage(plugin.prefix() + "§7Después: §f/admin setupwall <tipo_bloque>");
    }

    public boolean isSetupTool(ItemStack item) {
        if (item == null || item.getType() != Material.WOODEN_HOE) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && TOOL_NAME.equals(meta.getDisplayName());
    }

    // ── Selection management ──────────────────────────────────────────────

    public void setPos1(UUID uuid, Location loc) {
        selections.computeIfAbsent(uuid, k -> new Location[2])[0] = loc;
    }

    public void setPos2(UUID uuid, Location loc) {
        selections.computeIfAbsent(uuid, k -> new Location[2])[1] = loc;
    }

    public Location[] getSelection(UUID uuid) {
        return selections.get(uuid);
    }

    public boolean hasFullSelection(UUID uuid) {
        Location[] sel = selections.get(uuid);
        return sel != null && sel[0] != null && sel[1] != null;
    }

    public void clearSelection(UUID uuid) {
        selections.remove(uuid);
    }

    // ── Scan and save wall ────────────────────────────────────────────────

    /**
     * Scans the player's current selection for blocks matching {@code targetMaterial},
     * saves them as the wall for the given arena, and returns the block count found.
     * Returns -1 if the selection is incomplete.
     */
    public int setupWall(String arenaName, UUID adminUUID, Material targetMaterial) {
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
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == targetMaterial) {
                        blocks.add(new WallBlock(x, y, z, block.getType()));
                    }
                }
            }
        }

        if (blocks.isEmpty()) return 0;

        walls.put(arenaName.toLowerCase(), new WallConfig(arenaName, blocks));
        saveWalls();
        clearSelection(adminUUID);
        return blocks.size();
    }

    // ── Animation: open (FIGHT!) ──────────────────────────────────────────

    /**
     * Animates the wall rising upward, row by row (bottom → top).
     * Each row is cleared 2 ticks after the previous one.
     */
    public void animateOpen(String arenaName, World world) {
        WallConfig cfg = walls.get(arenaName.toLowerCase());
        if (cfg == null || cfg.size() == 0) return;

        List<Map.Entry<Integer, List<WallBlock>>> rows =
                new ArrayList<>(cfg.byYAscending().entrySet());

        new BukkitRunnable() {
            int idx = 0;

            @Override
            public void run() {
                if (world == null || !world.isChunkLoaded(0, 0)) { cancel(); return; }
                if (idx >= rows.size()) { cancel(); return; }

                List<WallBlock> row = rows.get(idx).getValue();
                for (WallBlock wb : row) {
                    world.getBlockAt(wb.x, wb.y, wb.z).setType(Material.AIR, false);
                }
                // Sound per row — pitch rises as wall goes up
                float pitch = 0.8f + (0.6f / Math.max(rows.size() - 1, 1)) * idx;
                world.playSound(
                        new Location(world, row.get(0).x, row.get(0).y, row.get(0).z),
                        Sound.BLOCK_FENCE_GATE_OPEN, 0.6f, pitch);
                idx++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ── Animation: close (duel end) ───────────────────────────────────────

    /**
     * Restores the wall row by row (top → bottom).
     */
    public void animateClose(String arenaName, World world) {
        WallConfig cfg = walls.get(arenaName.toLowerCase());
        if (cfg == null || cfg.size() == 0) return;

        List<Map.Entry<Integer, List<WallBlock>>> rows =
                new ArrayList<>(cfg.byYDescending().entrySet());

        new BukkitRunnable() {
            int idx = 0;

            @Override
            public void run() {
                if (world == null || !world.isChunkLoaded(0, 0)) { cancel(); return; }
                if (idx >= rows.size()) { cancel(); return; }

                List<WallBlock> row = rows.get(idx).getValue();
                for (WallBlock wb : row) {
                    world.getBlockAt(wb.x, wb.y, wb.z).setType(wb.material, false);
                }
                float pitch = 0.8f + (0.6f / Math.max(rows.size() - 1, 1)) * idx;
                world.playSound(
                        new Location(world, row.get(0).x, row.get(0).y, row.get(0).z),
                        Sound.BLOCK_FENCE_GATE_CLOSE, 0.6f, pitch);
                idx++;
            }
        }.runTaskTimer(plugin, 3L, 2L); // slight delay after duel end
    }

    // ── Queries ───────────────────────────────────────────────────────────

    public WallConfig getWallConfig(String arenaName) {
        return walls.get(arenaName.toLowerCase());
    }

    public boolean hasWall(String arenaName) {
        return walls.containsKey(arenaName.toLowerCase());
    }
}
