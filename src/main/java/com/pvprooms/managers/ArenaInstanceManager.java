package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.ArenaTemplate;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Level;

/**
 * Handles cloning arena template worlds into temporary instance worlds
 * and cleaning them up when a duel ends.
 *
 * Flow:
 *  1. createInstance(template, matchId) — copies the template folder to a new world folder
 *  2. Bukkit loads the new world
 *  3. Match runs
 *  4. destroyInstance(worldName) — unloads the world and deletes the folder
 */
public class ArenaInstanceManager {

    private final PvPRoomsPro plugin;

    public ArenaInstanceManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    /**
     * Disables auto-save on all currently-loaded template worlds.
     * Should be called once at startup after arenas are loaded.
     * Template worlds are read-only — Minecraft must never auto-save them.
     */
    public void disableAutoSaveOnTemplates() {
        for (ArenaTemplate t : plugin.getArenaManager().getAllArenas()) {
            String name = (t.getWorldName() != null && !t.getWorldName().isBlank())
                    ? t.getWorldName() : t.getName();
            World w = Bukkit.getWorld(name);
            if (w != null) {
                w.setAutoSave(false);
                plugin.getLogger().info("[PvPRooms] AutoSave desactivado en plantilla: " + name);
            }
        }
    }

    // ── World creation ─────────────────────────────────────────────────────

    /**
     * Synchronously copies the template world folder and loads it as a new World.
     *
     * @param template The arena template to clone.
     * @param matchId  A unique ID appended to the world name.
     * @return The loaded World, or null if something went wrong.
     */
    public World createInstance(ArenaTemplate template, String matchId) {
        String instanceName = plugin.getConfig().getString("arenas.instance-prefix", "pvp_match_") + matchId;

        // ── Fuente: mundo raíz con el nombre de la plantilla ──────────────────
        // Usar getWorldName() (nombre real del folder); si no está seteado, caer en getName()
        String worldFolderName = (template.getWorldName() != null && !template.getWorldName().isBlank())
                ? template.getWorldName() : template.getName();
        File sourceDir = new File(Bukkit.getWorldContainer(), worldFolderName);
        // Normalizar la ruta para resolver './' que aparece en algunos servidores (Pterodactyl)
        try { sourceDir = sourceDir.getCanonicalFile(); }
        catch (IOException e) { sourceDir = sourceDir.getAbsoluteFile(); }

        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            plugin.getLogger().warning("[PvPRooms] Mundo plantilla no encontrado: "
                    + sourceDir.getAbsolutePath()
                    + " — copia la carpeta '‎" + worldFolderName + "' a la raíz del servidor.");
            return null;
        }

        // ── Destino único por partida: pvp_match_<matchId> ────────────────
        File destDir = new File(Bukkit.getWorldContainer(), instanceName);
        if (destDir.exists()) {
            deleteDirectory(destDir);
        }

        try {
            copyDirectory(sourceDir.toPath(), destDir.toPath());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[PvPRooms] Error copiando plantilla " + template.getName() + " → " + instanceName, e);
            return null;
        }

        // Borrar session.lock y uid.dat para que Bukkit asigne un UID nuevo
        new File(destDir, "session.lock").delete();
        new File(destDir, "uid.dat").delete();

        // ── Cargar la instancia con generador vacío ────────────────────────
        // El generador vacío evita que Bukkit genere chunks nuevos
        // fuera de los ya copiados de la plantilla.
        WorldCreator creator = new WorldCreator(instanceName);
        creator.generator(new VoidChunkGenerator());
        creator.generateStructures(false);
        World instance = Bukkit.createWorld(creator);

        if (instance != null) {
            instance.setAutoSave(false);
            instance.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            instance.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            instance.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            instance.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            instance.setTime(6000L);
            plugin.getLogger().info("[PvPRooms] Instancia creada: " + instanceName
                    + " (plantilla: " + template.getName() + ")");
        } else {
            plugin.getLogger().severe("[PvPRooms] Bukkit devolvió null al crear " + instanceName);
        }

        return instance;
    }

    // ── World reset (for multi-round matches) ────────────────────────────────

    /**
     * Resets an instance world by unloading it, re-copying from template, and reloading.
     * Used between rounds in Tier matches to restore all blocks, explosions, etc.
     *
     * @param worldName Name of the instance world to reset.
     * @param template  The arena template to copy from.
     * @param callback  Runnable to execute after the world is reloaded (on main thread).
     */
    public void resetInstance(String worldName, ArenaTemplate template, Runnable callback) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[PvPRooms] Cannot reset non-existent world: " + worldName);
            if (callback != null) Bukkit.getScheduler().runTask(plugin, callback);
            return;
        }

        // Remove all dropped items from the arena before reset
        world.getEntitiesByClass(org.bukkit.entity.Item.class).forEach(org.bukkit.entity.Entity::remove);

        // Teleport players to a safe location temporarily (they'll be repositioned after reset)
        Location tempLoc = plugin.getLobbySpawn();
        for (var player : world.getPlayers()) {
            player.teleport(tempLoc);
        }

        // Pre-unload all chunks individually (no save) before calling unloadWorld.
        // This discards dirty chunk data from memory so DimensionDataStorage.saveAndJoin()
        // has minimal state to persist, reducing the main-thread block from ~3500ms to ~0ms.
        for (Chunk chunk : world.getLoadedChunks()) {
            chunk.unload(false);
        }
        Bukkit.unloadWorld(world, false);

        // Get source and destination paths
        String worldFolderName = (template.getWorldName() != null && !template.getWorldName().isBlank())
                ? template.getWorldName() : template.getName();
        File sourceDir = new File(Bukkit.getWorldContainer(), worldFolderName);
        try { sourceDir = sourceDir.getCanonicalFile(); }
        catch (IOException e) { sourceDir = sourceDir.getAbsoluteFile(); }

        File destDir = new File(Bukkit.getWorldContainer(), worldName);

        final File finalSourceDir = sourceDir;

        // Delete and re-copy asynchronously, then reload on main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Delete old instance folder
            deleteDirectory(destDir);

            // Re-copy from template
            try {
                copyDirectory(finalSourceDir.toPath(), destDir.toPath());
                new File(destDir, "session.lock").delete();
                new File(destDir, "uid.dat").delete();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "[PvPRooms] Error resetting arena " + worldName, e);
            }

            // Reload world on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                WorldCreator creator = new WorldCreator(worldName);
                creator.generator(new VoidChunkGenerator());
                creator.generateStructures(false);
                World reloaded = Bukkit.createWorld(creator);

                if (reloaded != null) {
                    reloaded.setAutoSave(false);
                    reloaded.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                    reloaded.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                    reloaded.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                    reloaded.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
                    reloaded.setTime(6000L);
                    plugin.getLogger().info("[PvPRooms] Arena reset: " + worldName);
                }

                if (callback != null) callback.run();
            });
        });
    }

    // ── Pool world reset (zero disk I/O) ─────────────────────────────────────

    /**
     * Resets a pool world with ZERO disk I/O and NO world unload.
     *
     * Why no file copy is needed:
     *   Pool worlds are created with setAutoSave(false). During a duel, block
     *   changes accumulate only in RAM. chunk.unload(false) discards them without
     *   writing to disk. Therefore the on-disk region files always contain the
     *   original clean template data — no copy required.
     *
     * Why no unloadWorld:
     *   Bukkit.unloadWorld() triggers DimensionDataStorage.saveAndJoin() on the
     *   main thread even with save=false, causing 3000+ms spikes.
     *
     * After reset, spawn chunks are pre-warmed async (Paper API) so the next
     * duel has no on-demand chunk-load spike when players teleport in.
     */
    public void resetPoolWorld(String worldName, ArenaTemplate template, Runnable callback) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[Pool] Cannot reset non-existent pool world: " + worldName);
            if (callback != null) callback.run();
            return;
        }

        world.setAutoSave(false);

        // Remove lingering non-player entities (items, orbs, projectiles, crystals)
        for (org.bukkit.entity.Entity e : world.getEntities()) {
            if (!(e instanceof Player)) e.remove();
        }

        // Unload all loaded chunks WITHOUT saving — pure RAM discard, no disk write.
        // Disk files were never modified (autoSave=false), so they're already clean.
        for (Chunk chunk : world.getLoadedChunks()) {
            chunk.unload(false);
        }

        // Reset gamerules (world object persists, just re-apply)
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setTime(6000L);

        plugin.getLogger().info("[Pool] Pool world reset (zero I/O): " + worldName);

        // Notify caller — reset is complete (all sync, instant)
        if (callback != null) callback.run();

        // Force-load spawn chunks so they stay in RAM permanently (survive round resets)
        forceLoadSpawnChunks(world, template);
    }

    /**
     * Force-loads chunks around both spawn points of the template.
     *
     * setChunkForceLoaded keeps the chunk pinned in RAM permanently — it will
     * never be unloaded by Paper's tick-based eviction. This prevents the
     * on-demand chunk-load CPU spike when players teleport to a reset arena.
     *
     * We force-load a 5x5 area around each spawn (covers most arena combat zones).
     * The world object persists across resets, so force-loaded chunks survive
     * the chunk.unload(false) calls in resetPoolWorld.
     */
    public void forceLoadSpawnChunks(World world, ArenaTemplate template) {
        try {
            Location s1 = template.getSpawn1(world);
            Location s2 = template.getSpawn2(world);
            for (Location spawn : new Location[]{s1, s2}) {
                if (spawn == null) continue;
                int cx = spawn.getBlockX() >> 4;
                int cz = spawn.getBlockZ() >> 4;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        world.setChunkForceLoaded(cx + dx, cz + dz, true);
                    }
                }
            }
            plugin.getLogger().info("[Pool] Force-loaded spawn chunks for " + world.getName());
        } catch (Exception e) {
            plugin.getLogger().warning("[Pool] Error force-loading chunks for " + world.getName() + ": " + e.getMessage());
        }
    }

    // ── World destruction ──────────────────────────────────────────────────

    /**
     * Safely unloads the instance world and schedules its folder deletion.
     *
     * @param worldName Name of the instance world to destroy.
     */
    public void destroyInstance(String worldName) {
        World world = Bukkit.getWorld(worldName);

        if (world != null) {
            // Teleport any remaining players out before unloading
            Location lobby = plugin.getLobbySpawn();
            for (var player : world.getPlayers()) {
                player.teleport(lobby);
            }
            // Pre-unload chunks to minimize DimensionDataStorage blocking in unloadWorld
            for (Chunk chunk : world.getLoadedChunks()) {
                chunk.unload(false);
            }
            Bukkit.unloadWorld(world, false);
        }

        // Async folder deletion to avoid blocking the main thread
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        new BukkitRunnable() {
            @Override
            public void run() {
                deleteDirectory(worldFolder);
            }
        }.runTaskAsynchronously(plugin);
    }

    // ── File utilities ─────────────────────────────────────────────────────

    /**
     * Recursively copies a directory tree from source to target.
     */
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)),
                        StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Recursively deletes a directory and all its contents.
     */
    private void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        try {
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.deleteIfExists(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not fully delete world folder: " + dir.getName(), e);
        }
    }

    /** Returns a void chunk generator usable anywhere in the plugin. */
    public org.bukkit.generator.ChunkGenerator voidGenerator() {
        return new VoidChunkGenerator();
    }

    // ── Inner class: Void chunk generator ─────────────────────────────────

    /**
     * Prevents Bukkit from regenerating new chunks in the instance world.
     * The template world already has all necessary terrain.
     */
    private static class VoidChunkGenerator extends org.bukkit.generator.ChunkGenerator {
        @Override
        public boolean shouldGenerateNoise() { return false; }
        @Override
        public boolean shouldGenerateSurface() { return false; }
        @Override
        public boolean shouldGenerateBedrock() { return false; }
        @Override
        public boolean shouldGenerateCaves() { return false; }
        @Override
        public boolean shouldGenerateDecorations() { return false; }
        @Override
        public boolean shouldGenerateMobs() { return false; }
        @Override
        public boolean shouldGenerateStructures() { return false; }
    }
}
