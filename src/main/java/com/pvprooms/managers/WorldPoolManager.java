package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.ArenaTemplate;
import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

/**
 * Pre-creates arena world instances and recycles them after each duel/round.
 *
 * Benefits for HDD servers:
 *  - World copy happens at startup (or idle time), NOT when a player starts a duel.
 *  - Between rounds in Tier mode, swapping to a clean pool world is instant
 *    (players teleport immediately while the dirty world resets in the background).
 *  - Pool worlds persist across server restarts — if already copied, just loaded.
 *
 * Pool world names: pvp_pool_<sanitizedTemplateName>_<index>
 */
public class WorldPoolManager {

    private final PvPRoomsPro plugin;

    /** templateName -> queue of world names that are clean and ready */
    private final Map<String, Queue<String>> readyPools = new ConcurrentHashMap<>();

    /** worldName -> templateName, for all pool worlds (ready + in-use) */
    private final Map<String, String> worldToTemplate = new ConcurrentHashMap<>();

    /** Pool worlds currently being reset asynchronously */
    private final Set<String> resetting = ConcurrentHashMap.newKeySet();

    /** Pool worlds currently in use (borrowed) */
    private final Set<String> inUse = ConcurrentHashMap.newKeySet();

    /** When true, defer new world copies (set by LagMonitor during severe lag) */
    private volatile boolean paused = false;

    public static final String POOL_PREFIX = "pvp_pool_";

    public WorldPoolManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ── Startup warm-up ─────────────────────────────────────────────────────

    /**
     * Warms up the pool for all fully-configured arenas.
     * Staggered: each world copy starts {@code staggerTicks} ticks after the previous.
     *
     * @param initialDelayTicks  Ticks to wait before starting the first copy.
     * @param staggerTicks       Ticks between consecutive world creations (spreads HDD I/O).
     */
    public void warmUpAll(int initialDelayTicks, int staggerTicks) {
        if (!plugin.getConfig().getBoolean("arena-pool.enabled", true)) return;

        int poolSize = plugin.getConfig().getInt("arena-pool.size-per-arena", 2);
        List<ArenaTemplate> templates = plugin.getArenaManager().getAllArenas().stream()
                .filter(ArenaTemplate::isFullyConfigured)
                .toList();

        int slot = 0;
        for (ArenaTemplate template : templates) {
            for (int i = 0; i < poolSize; i++) {
                String worldName = POOL_PREFIX + sanitize(template.getName()) + "_" + i;
                worldToTemplate.put(worldName, template.getName());
                final int delay = initialDelayTicks + (slot * staggerTicks);
                final String wn = worldName;
                final ArenaTemplate t = template;
                Bukkit.getScheduler().runTaskLater(plugin, () -> ensurePoolWorld(wn, t), delay);
                slot++;
            }
        }
        plugin.getLogger().info("[Pool] Precalentando " + (templates.size() * poolSize)
                + " mundos de arena (stagger: " + staggerTicks + " ticks).");
    }

    /**
     * Loads an existing pool world folder or copies it fresh from the template.
     * On completion, the world is added to the ready queue.
     */
    private void ensurePoolWorld(String worldName, ArenaTemplate template) {
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);

        // If already loaded in Bukkit (shouldn't normally happen at startup), just add to pool
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            addToReady(template.getName(), worldName);
            plugin.getLogger().info("[Pool] Reutilizando mundo ya cargado: " + worldName);
            return;
        }

        // If folder exists on disk, load it directly (saved clean on previous shutdown)
        if (worldFolder.exists()) {
            loadWorld(worldName, () -> {
                addToReady(template.getName(), worldName);
                plugin.getLogger().info("[Pool] Mundo del pool cargado desde disco: " + worldName);
            });
            return;
        }

        // No folder — copy from template asynchronously, then load
        copyFromTemplateAsync(worldName, template, () -> {
            addToReady(template.getName(), worldName);
            plugin.getLogger().info("[Pool] Mundo del pool creado: " + worldName);
        });
    }

    // ── Borrow / Return ──────────────────────────────────────────────────────

    /**
     * Borrows a clean world from the pool for the given template.
     * Returns {@code null} if no world is currently available (caller falls back to on-demand).
     */
    public World borrowWorld(ArenaTemplate template) {
        Queue<String> pool = readyPools.get(template.getName());
        if (pool == null) return null;

        while (!pool.isEmpty()) {
            String worldName = pool.poll();
            if (worldName == null) break;
            World w = Bukkit.getWorld(worldName);
            if (w != null) {
                inUse.add(worldName);
                plugin.getLogger().info("[Pool] Mundo prestado: " + worldName
                        + " (quedan " + (pool.size()) + " en pool)");
                return w;
            }
            // World not loaded for some reason — skip it
            plugin.getLogger().warning("[Pool] Mundo del pool no cargado, descartando: " + worldName);
        }
        return null;
    }

    /**
     * Returns a used pool world back to the pool.
     * Resets it from the template asynchronously; once done, it's ready for the next duel.
     */
    public void returnWorld(String worldName, ArenaTemplate template) {
        if (!isPoolWorld(worldName)) return;
        if (resetting.contains(worldName)) return;

        inUse.remove(worldName);
        resetting.add(worldName);

        // Remove dropped items before reset
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            world.getEntitiesByClass(org.bukkit.entity.Item.class)
                    .forEach(org.bukkit.entity.Entity::remove);
        }

        // Reset world and re-add to pool when done
        plugin.getArenaInstanceManager().resetInstance(worldName, template, () -> {
            resetting.remove(worldName);
            addToReady(template.getName(), worldName);
            plugin.getLogger().info("[Pool] Mundo devuelto al pool: " + worldName
                    + " (pool " + template.getName() + " tiene "
                    + readyPools.getOrDefault(template.getName(), new ConcurrentLinkedQueue<>()).size() + ")");
        });
    }

    // ── Lag mitigation ───────────────────────────────────────────────────────

    /**
     * Called by LagMonitor: pause/resume background world warming.
     * Active duels are never affected — only idle background copies are deferred.
     */
    public void setPaused(boolean paused) {
        if (this.paused != paused) {
            this.paused = paused;
            plugin.getLogger().info("[Pool] Background world warming " + (paused ? "PAUSADO (lag detectado)" : "reanudado"));
        }
    }

    public boolean isPaused() { return paused; }

    // ── Query ────────────────────────────────────────────────────────────────

    public boolean isPoolWorld(String worldName) {
        return worldName != null && worldName.startsWith(POOL_PREFIX);
    }

    public String getTemplateForWorld(String worldName) {
        return worldToTemplate.get(worldName);
    }

    public int getReadyCount(String templateName) {
        Queue<String> q = readyPools.get(templateName);
        return q != null ? q.size() : 0;
    }

    // ── Shutdown ─────────────────────────────────────────────────────────────

    /**
     * Unloads all pool worlds without saving.
     * The folders remain on disk so the next startup can load them directly.
     */
    public void shutdown() {
        for (String worldName : worldToTemplate.keySet()) {
            World w = Bukkit.getWorld(worldName);
            if (w != null) {
                Location lobby = plugin.getLobbySpawn();
                for (var p : w.getPlayers()) p.teleport(lobby);
                Bukkit.unloadWorld(w, false);
            }
        }
        readyPools.clear();
        inUse.clear();
        resetting.clear();
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private void addToReady(String templateName, String worldName) {
        readyPools.computeIfAbsent(templateName, k -> new ConcurrentLinkedQueue<>()).add(worldName);
    }

    private void loadWorld(String worldName, Runnable onLoaded) {
        // Loading a world must happen on the main thread
        if (Bukkit.isPrimaryThread()) {
            doLoadWorld(worldName, onLoaded);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> doLoadWorld(worldName, onLoaded));
        }
    }

    private void doLoadWorld(String worldName, Runnable onLoaded) {
        // Delete session.lock so Bukkit accepts the folder
        new File(Bukkit.getWorldContainer(), worldName + "/session.lock").delete();
        new File(Bukkit.getWorldContainer(), worldName + "/uid.dat").delete();

        WorldCreator creator = new WorldCreator(worldName);
        creator.generator(plugin.getArenaInstanceManager().voidGenerator());
        creator.generateStructures(false);
        World w = Bukkit.createWorld(creator);
        if (w != null) {
            applySettings(w);
            if (onLoaded != null) onLoaded.run();
        } else {
            plugin.getLogger().severe("[Pool] No se pudo cargar el mundo del pool: " + worldName);
        }
    }

    private void copyFromTemplateAsync(String worldName, ArenaTemplate template, Runnable onReady) {
        String srcName = (template.getWorldName() != null && !template.getWorldName().isBlank())
                ? template.getWorldName() : template.getName();
        File srcDir = new File(Bukkit.getWorldContainer(), srcName);
        try { srcDir = srcDir.getCanonicalFile(); }
        catch (IOException e) { srcDir = srcDir.getAbsoluteFile(); }

        File destDir = new File(Bukkit.getWorldContainer(), worldName);
        final File finalSrc = srcDir;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (destDir.exists()) deleteDirectory(destDir);
                copyDirectory(finalSrc.toPath(), destDir.toPath());
                new File(destDir, "session.lock").delete();
                new File(destDir, "uid.dat").delete();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "[Pool] Error copiando plantilla: " + worldName, e);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> doLoadWorld(worldName, onReady));
        });
    }

    private void applySettings(World w) {
        w.setAutoSave(false);
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        w.setTime(6000L);
    }

    private static String sanitize(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        try {
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                    Files.deleteIfExists(f);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException ex) throws IOException {
                    Files.deleteIfExists(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[Pool] No se pudo borrar: " + dir.getName(), e);
        }
    }
}
