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

        // ── Fuente: mundo raíz con el nombre de la plantilla ──────────────
        File sourceDir = new File(Bukkit.getWorldContainer(), template.getName());

        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            plugin.getLogger().warning("[PvPRooms] Mundo plantilla no encontrado: "
                    + sourceDir.getAbsolutePath()
                    + " — usa /arena create <nombre> o copia tu mundo a la raíz del servidor.");
            return null;
        }

        // ── Guardar el mundo fuente si está cargado ────────────────────────
        // Esto garantiza que los chunks en memoria se escriban al disco
        // antes de copiar, incluso con múltiples partidas en curso.
        World sourceWorld = Bukkit.getWorld(template.getName());
        if (sourceWorld != null) {
            sourceWorld.save();
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
