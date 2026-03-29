package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages NPCs for queue, instant match, info, etc.
 */
public class NpcManager implements Listener {

    public enum NpcType {
        QUEUE, INSTANT, UNRANKED, FFA, STATS, LEADERBOARD, KITS, SHOP,
        INFO, EVENTS, TOURNAMENTS, PRACTICE, SPECTATE, PARTY,
        DUEL_1V1, DUEL_2V2, BO3, BO5
    }

    public record NpcData(int id, NpcType type, String kit, Location location, String skin, String name, boolean lookAtPlayer, UUID entityUuid) {}

    private final PvPRoomsPro plugin;
    private final Map<Integer, NpcData> npcs = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> entityToNpc = new ConcurrentHashMap<>();
    private final Deque<Integer> undoStack = new ArrayDeque<>();
    private int nextId = 1;
    private File dataFile;

    public NpcManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "npcs.yml");
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public int createNpc(NpcType type, String kit, Location loc, String name) {
        int id = nextId++;
        
        // Spawn villager entity
        Villager villager = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setCustomName(colorize(name));
        villager.setCustomNameVisible(true);
        villager.setMetadata("pvpnpc", new FixedMetadataValue(plugin, id));
        villager.setProfession(Villager.Profession.NONE);
        
        NpcData data = new NpcData(id, type, kit, loc.clone(), null, name, true, villager.getUniqueId());
        npcs.put(id, data);
        entityToNpc.put(villager.getUniqueId(), id);
        undoStack.push(id);
        
        save();
        return id;
    }

    public boolean deleteNpc(int id) {
        NpcData data = npcs.remove(id);
        if (data == null) return false;
        
        entityToNpc.remove(data.entityUuid());
        
        // Remove entity from world
        if (data.entityUuid() != null) {
            Bukkit.getWorlds().forEach(w -> w.getEntities().stream()
                .filter(e -> e.getUniqueId().equals(data.entityUuid()))
                .forEach(e -> e.remove()));
        }
        
        save();
        return true;
    }

    public Integer undoLast() {
        if (undoStack.isEmpty()) return null;
        int id = undoStack.pop();
        if (deleteNpc(id)) return id;
        return null;
    }

    public NpcData getNearestNpc(Location loc, double maxDistance) {
        return npcs.values().stream()
            .filter(n -> n.location().getWorld().equals(loc.getWorld()))
            .filter(n -> n.location().distance(loc) <= maxDistance)
            .min(Comparator.comparingDouble(n -> n.location().distance(loc)))
            .orElse(null);
    }

    public void setNpcName(int id, String name) {
        NpcData old = npcs.get(id);
        if (old == null) return;
        
        npcs.put(id, new NpcData(id, old.type(), old.kit(), old.location(), old.skin(), name, old.lookAtPlayer(), old.entityUuid()));
        
        // Update entity
        Bukkit.getWorlds().forEach(w -> w.getEntities().stream()
            .filter(e -> e.getUniqueId().equals(old.entityUuid()))
            .forEach(e -> e.setCustomName(colorize(name))));
        
        save();
    }

    public void moveNpc(int id, Location newLoc) {
        NpcData old = npcs.get(id);
        if (old == null) return;
        
        npcs.put(id, new NpcData(id, old.type(), old.kit(), newLoc.clone(), old.skin(), old.name(), old.lookAtPlayer(), old.entityUuid()));
        
        Bukkit.getWorlds().forEach(w -> w.getEntities().stream()
            .filter(e -> e.getUniqueId().equals(old.entityUuid()))
            .forEach(e -> e.teleport(newLoc)));
        
        save();
    }

    public Collection<NpcData> getAllNpcs() {
        return npcs.values();
    }

    public NpcData getNpc(int id) {
        return npcs.get(id);
    }

    /** Remove all villagers with pvpnpc metadata to prevent accumulation */
    public void removeAllNpcEntities() {
        for (var world : Bukkit.getWorlds()) {
            world.getEntities().stream()
                .filter(e -> e instanceof Villager)
                .filter(e -> e.hasMetadata("pvpnpc"))
                .forEach(e -> e.remove());
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractAtEntityEvent event) {
        if (!event.getRightClicked().hasMetadata("pvpnpc")) return;
        
        int id = event.getRightClicked().getMetadata("pvpnpc").get(0).asInt();
        NpcData npc = npcs.get(id);
        if (npc == null) return;
        
        event.setCancelled(true);
        handleNpcClick(event.getPlayer(), npc);
    }

    private void handleNpcClick(Player player, NpcData npc) {
        switch (npc.type()) {
            case QUEUE -> {
                if (npc.kit() != null && !npc.kit().isEmpty()) {
                    player.performCommand("queue " + npc.kit());
                } else {
                    player.performCommand("queue");
                }
            }
            case INSTANT -> {
                if (npc.kit() != null && !npc.kit().isEmpty()) {
                    player.performCommand("instant " + npc.kit());
                } else {
                    player.performCommand("instant");
                }
            }
            case UNRANKED -> player.performCommand("unranked");
            case FFA -> player.performCommand("ffa join");
            case STATS -> player.performCommand("stats");
            case LEADERBOARD -> player.performCommand("leaderboard");
            case KITS -> player.performCommand("kits");
            case SHOP -> player.performCommand("shop");
            case INFO -> player.performCommand("pvpinfo");
            case EVENTS -> player.performCommand("events");
            case TOURNAMENTS -> player.performCommand("tournament");
            case PRACTICE -> player.performCommand("practice");
            case SPECTATE -> player.performCommand("spectate");
            case PARTY -> player.performCommand("party");
            case DUEL_1V1 -> player.performCommand("duel 1v1");
            case DUEL_2V2 -> player.performCommand("duel 2v2");
            case BO3 -> player.performCommand("duel bo3");
            case BO5 -> player.performCommand("duel bo5");
        }
    }

    public void load() {
        // Clean up ALL existing NPC villagers first to prevent accumulation
        removeAllNpcEntities();
        npcs.clear();
        entityToNpc.clear();
        
        if (!dataFile.exists()) return;
        
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = cfg.getConfigurationSection("npcs");
        if (section == null) return;
        
        for (String key : section.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                ConfigurationSection npcSec = section.getConfigurationSection(key);
                if (npcSec == null) continue;
                
                NpcType type = NpcType.valueOf(npcSec.getString("type", "QUEUE"));
                String kit = npcSec.getString("kit");
                String name = npcSec.getString("name", "&eNPC");
                String skin = npcSec.getString("skin");
                boolean lookAt = npcSec.getBoolean("look_at_player", true);
                
                ConfigurationSection locSec = npcSec.getConfigurationSection("location");
                if (locSec == null) continue;
                
                Location loc = new Location(
                    Bukkit.getWorld(locSec.getString("world", "world")),
                    locSec.getDouble("x"),
                    locSec.getDouble("y"),
                    locSec.getDouble("z"),
                    (float) locSec.getDouble("yaw"),
                    (float) locSec.getDouble("pitch")
                );
                
                if (loc.getWorld() == null) continue;
                
                // Spawn entity
                Villager villager = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
                villager.setAI(false);
                villager.setInvulnerable(true);
                villager.setSilent(true);
                villager.setCustomName(colorize(name));
                villager.setCustomNameVisible(true);
                villager.setMetadata("pvpnpc", new FixedMetadataValue(plugin, id));
                villager.setProfession(Villager.Profession.NONE);
                
                NpcData data = new NpcData(id, type, kit, loc, skin, name, lookAt, villager.getUniqueId());
                npcs.put(id, data);
                entityToNpc.put(villager.getUniqueId(), id);
                
                if (id >= nextId) nextId = id + 1;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load NPC " + key + ": " + e.getMessage());
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        
        for (NpcData npc : npcs.values()) {
            String path = "npcs." + npc.id();
            cfg.set(path + ".type", npc.type().name());
            cfg.set(path + ".kit", npc.kit());
            cfg.set(path + ".name", npc.name());
            cfg.set(path + ".skin", npc.skin());
            cfg.set(path + ".look_at_player", npc.lookAtPlayer());
            
            Location loc = npc.location();
            cfg.set(path + ".location.world", loc.getWorld().getName());
            cfg.set(path + ".location.x", loc.getX());
            cfg.set(path + ".location.y", loc.getY());
            cfg.set(path + ".location.z", loc.getZ());
            cfg.set(path + ".location.yaw", loc.getYaw());
            cfg.set(path + ".location.pitch", loc.getPitch());
        }
        
        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save NPCs: " + e.getMessage());
        }
    }

    public void shutdown() {
        // Remove all NPC entities
        for (NpcData npc : npcs.values()) {
            if (npc.entityUuid() != null) {
                Bukkit.getWorlds().forEach(w -> w.getEntities().stream()
                    .filter(e -> e.getUniqueId().equals(npc.entityUuid()))
                    .forEach(e -> e.remove()));
            }
        }
    }

    private String colorize(String text) {
        return text == null ? "" : text.replace("&", "§");
    }
}
