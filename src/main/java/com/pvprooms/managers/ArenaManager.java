package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.ArenaTemplate;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages arena templates: creation, spawn assignment, deletion, persistence.
 * Data is stored in plugins/PvPRoomsPro/arenas.yml.
 *
 * Arena template worlds must be present inside the
 * plugins/PvPRoomsPro/maps/<arenaName>/ folder so the ArenaInstanceManager
 * can copy them when starting a match.
 */
public class ArenaManager {

    private final PvPRoomsPro plugin;
    private final File arenasFile;
    private FileConfiguration arenasConfig;

    /** In-memory registry: arena name (lowercase) → ArenaTemplate */
    private final Map<String, ArenaTemplate> arenas = new LinkedHashMap<>();

    public ArenaManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
        this.arenasFile = new File(plugin.getDataFolder(), "arenas.yml");
        loadArenas();
    }

    // ── Load / Save ────────────────────────────────────────────────────────

    public void loadArenas() {
        arenas.clear();
        if (!arenasFile.exists()) {
            saveArenas();
            return;
        }

        arenasConfig = YamlConfiguration.loadConfiguration(arenasFile);
        if (!arenasConfig.contains("arenas")) return;

        for (String name : arenasConfig.getConfigurationSection("arenas").getKeys(false)) {
            String p = "arenas." + name;
            ArenaTemplate t = new ArenaTemplate(name);
            t.setWorldName(arenasConfig.getString(p + ".world", name));

            t.setSpawn1Raw(
                    arenasConfig.getDouble(p + ".spawn1.x"),
                    arenasConfig.getDouble(p + ".spawn1.y"),
                    arenasConfig.getDouble(p + ".spawn1.z"),
                    (float) arenasConfig.getDouble(p + ".spawn1.yaw"),
                    (float) arenasConfig.getDouble(p + ".spawn1.pitch")
            );
            t.setSpawn2Raw(
                    arenasConfig.getDouble(p + ".spawn2.x"),
                    arenasConfig.getDouble(p + ".spawn2.y"),
                    arenasConfig.getDouble(p + ".spawn2.z"),
                    (float) arenasConfig.getDouble(p + ".spawn2.yaw"),
                    (float) arenasConfig.getDouble(p + ".spawn2.pitch")
            );
            t.setAllowExplosions(arenasConfig.getBoolean(p + ".config.allow-explosions", false));
            t.setAllowBlockBreak(arenasConfig.getBoolean(p + ".config.allow-block-break",  false));
            t.setAllowBlockPlace(arenasConfig.getBoolean(p + ".config.allow-block-place",  false));

            arenas.put(name.toLowerCase(), t);
        }
        plugin.getLogger().info("Loaded " + arenas.size() + " arena template(s).");
    }

    public void saveArenas() {
        arenasConfig = new YamlConfiguration();
        for (ArenaTemplate t : arenas.values()) {
            String p = "arenas." + t.getName();
            arenasConfig.set(p + ".world", t.getWorldName());

            arenasConfig.set(p + ".spawn1.x",     t.getSpawn1X());
            arenasConfig.set(p + ".spawn1.y",     t.getSpawn1Y());
            arenasConfig.set(p + ".spawn1.z",     t.getSpawn1Z());
            arenasConfig.set(p + ".spawn1.yaw",   t.getSpawn1Yaw());
            arenasConfig.set(p + ".spawn1.pitch", t.getSpawn1Pitch());

            arenasConfig.set(p + ".spawn2.x",     t.getSpawn2X());
            arenasConfig.set(p + ".spawn2.y",     t.getSpawn2Y());
            arenasConfig.set(p + ".spawn2.z",     t.getSpawn2Z());
            arenasConfig.set(p + ".spawn2.yaw",   t.getSpawn2Yaw());
            arenasConfig.set(p + ".spawn2.pitch", t.getSpawn2Pitch());
            arenasConfig.set(p + ".config.allow-explosions", t.isAllowExplosions());
            arenasConfig.set(p + ".config.allow-block-break",  t.isAllowBlockBreak());
            arenasConfig.set(p + ".config.allow-block-place",  t.isAllowBlockPlace());
        }
        try {
            arenasConfig.save(arenasFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save arenas.yml", e);
        }
    }

    // ── CRUD ───────────────────────────────────────────────────────────────

    /**
     * Creates a new arena template entry with the given name.
     * Does NOT copy world files; the admin must place the world folder in
     * plugins/PvPRoomsPro/maps/<name>/ manually.
     *
     * @return false if an arena with that name already exists.
     */
    public boolean createArena(String name) {
        if (arenas.containsKey(name.toLowerCase())) return false;
        ArenaTemplate t = new ArenaTemplate(name);
        t.setWorldName(name);
        arenas.put(name.toLowerCase(), t);
        saveArenas();
        return true;
    }

    /**
     * Sets spawn point 1 for an arena from a player's current location.
     */
    public boolean setSpawn1(String name, Player player) {
        ArenaTemplate t = arenas.get(name.toLowerCase());
        if (t == null) return false;
        t.setSpawn1(player.getLocation());
        saveArenas();
        return true;
    }

    /**
     * Sets spawn point 2 for an arena from a player's current location.
     */
    public boolean setSpawn2(String name, Player player) {
        ArenaTemplate t = arenas.get(name.toLowerCase());
        if (t == null) return false;
        t.setSpawn2(player.getLocation());
        saveArenas();
        return true;
    }

    /**
     * Deletes an arena template.
     *
     * @return false if the arena does not exist.
     */
    public boolean deleteArena(String name) {
        if (arenas.remove(name.toLowerCase()) == null) return false;
        saveArenas();
        return true;
    }

    // ── Query helpers ──────────────────────────────────────────────────────

    public ArenaTemplate getArena(String name) {
        return arenas.get(name.toLowerCase());
    }

    public boolean arenaExists(String name) {
        return arenas.containsKey(name.toLowerCase());
    }

    /** Returns a random fully-configured arena template, or null if none available. */
    public ArenaTemplate getRandomArena() {
        List<ArenaTemplate> configured = new ArrayList<>();
        for (ArenaTemplate t : arenas.values()) {
            if (t.isFullyConfigured()) configured.add(t);
        }
        if (configured.isEmpty()) return null;
        return configured.get(new Random().nextInt(configured.size()));
    }

    public Collection<ArenaTemplate> getAllArenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    public List<String> getArenaNames() {
        return new ArrayList<>(arenas.keySet());
    }
}
