package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.ArenaTemplate;
import org.bukkit.*;
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

        // Unload the world without saving (discard all changes)
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

    // ── Pool world reset (no unloadWorld — avoids DimensionDataStorage spike) ──

    /**
     * Resets a pre-warmed pool world WITHOUT calling Bukkit.unloadWorld().
     *
     * Problem with resetInstance for pool worlds: Bukkit.unloadWorld(w, false) still
     * triggers DimensionDataStorage.saveAndJoin() on the main thread, causing 3000+ms
     * lag spikes even with save=false.
     *
     * This method avoids that by:
     *   1. Unloading only individual chunks (no disk I/O, just discards memory cache)
     *   2. Copying template files async (world folder on disk gets refreshed)
     *   3. Callback on main thread (chunks reload from fresh files on next access)
     */
    public void resetPoolWorld(String worldName, ArenaTemplate template, Runnable callback) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[Pool] Cannot reset non-existent pool world: " + worldName);
            if (callback != null) Bukkit.getScheduler().runTask(plugin, callback);
            return;
        }

        // Disable auto-save and clear dropped items
        world.setAutoSave(false);
        world.getEntitiesByClass(org.bukkit.entity.Item.class).forEach(org.bukkit.entity.Entity::remove);

        // Unload all loaded chunks WITHOUT saving — fast (memory discard, no disk write)
        // This does NOT trigger DimensionDataStorage.saveAndJoin()
        for (Chunk chunk : world.getLoadedChunks()) {
            chunk.unload(false);
        }

        // Resolve template source folder
        String worldFolderName = (template.getWorldName() != null && !template.getWorldName().isBlank())
                ? template.getWorldName() : template.getName();
        File sourceDir = new File(Bukkit.getWorldContainer(), worldFolderName);
        try { sourceDir = sourceDir.getCanonicalFile(); }
        catch (IOException e) { sourceDir = sourceDir.getAbsoluteFile(); }

        File destDir = new File(Bukkit.getWorldContainer(), worldName);
        final File finalSourceDir = sourceDir;

        // Async: copy template files over the existing world folder
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                copyDirectory(finalSourceDir.toPath(), destDir.toPath());
                new File(destDir, "session.lock").delete();
                new File(destDir, "uid.dat").delete();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "[Pool] Error resetting pool world " + worldName, e);
            }

            // Back to main thread — world is still registered in Bukkit,
            // chunks will reload from fresh files on next access
            Bukkit.getScheduler().runTask(plugin, () -> {
                World w = Bukkit.getWorld(worldName);
                if (w != null) {
                    w.setAutoSave(false);
                    w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                    w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                    w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                    w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
                    w.setTime(6000L);
                }
                plugin.getLogger().info("[Pool] Pool world reset (sin unload): " + worldName);
                if (callback != null) callback.run();
            });
        });
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
            Bukkit.unloadWorld(world, false); // false = don't save chunks
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
