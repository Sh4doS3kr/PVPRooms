package com.pvprooms.model;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Represents a saved arena template.
 * Templates hold the world name and two spawn locations.
 * They are cloned into temporary instance worlds for each duel.
 */
public class ArenaTemplate {

    private final String name;
    private String worldName;
    private double spawn1X, spawn1Y, spawn1Z;
    private float spawn1Yaw, spawn1Pitch;
    private double spawn2X, spawn2Y, spawn2Z;
    private float spawn2Yaw, spawn2Pitch;

    // ── Arena-specific game rules ───────────────────────────────
    private boolean allowExplosions = false;
    private boolean allowBlockBreak  = false;
    private boolean allowBlockPlace  = false;

    public ArenaTemplate(String name) {
        this.name = name;
    }

    // ── Spawn helpers ──────────────────────────────────────────────────────

    /**
     * Stores spawn 1 position from a live Location object.
     * The world name is also taken from this location.
     */
    public void setSpawn1(Location loc) {
        this.worldName = loc.getWorld().getName();
        this.spawn1X = loc.getX();
        this.spawn1Y = loc.getY();
        this.spawn1Z = loc.getZ();
        this.spawn1Yaw = loc.getYaw();
        this.spawn1Pitch = loc.getPitch();
    }

    /** Stores spawn 2 position from a live Location object. */
    public void setSpawn2(Location loc) {
        this.spawn2X = loc.getX();
        this.spawn2Y = loc.getY();
        this.spawn2Z = loc.getZ();
        this.spawn2Yaw = loc.getYaw();
        this.spawn2Pitch = loc.getPitch();
    }

    /**
     * Rebuilds a live Spawn 1 Location using the provided world.
     * Used when spawning players inside a cloned instance world.
     */
    public Location getSpawn1(World world) {
        return new Location(world, spawn1X, spawn1Y, spawn1Z, spawn1Yaw, spawn1Pitch);
    }

    /**
     * Rebuilds a live Spawn 2 Location using the provided world.
     */
    public Location getSpawn2(World world) {
        return new Location(world, spawn2X, spawn2Y, spawn2Z, spawn2Yaw, spawn2Pitch);
    }

    /** Returns true if both spawns have been set (Y != 0 is a basic sanity check). */
    public boolean isFullyConfigured() {
        return worldName != null && spawn1Y != 0 && spawn2Y != 0;
    }

    // ── Getters / setters ──────────────────────────────────────────────────

    public String getName()  { return name; }
    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }

    public boolean isAllowExplosions() { return allowExplosions; }
    public void setAllowExplosions(boolean v) { this.allowExplosions = v; }

    public boolean isAllowBlockBreak() { return allowBlockBreak; }
    public void setAllowBlockBreak(boolean v) { this.allowBlockBreak = v; }

    public boolean isAllowBlockPlace() { return allowBlockPlace; }
    public void setAllowBlockPlace(boolean v) { this.allowBlockPlace = v; }

    public double getSpawn1X()  { return spawn1X; }
    public double getSpawn1Y()  { return spawn1Y; }
    public double getSpawn1Z()  { return spawn1Z; }
    public float  getSpawn1Yaw()   { return spawn1Yaw; }
    public float  getSpawn1Pitch() { return spawn1Pitch; }

    public void setSpawn1Raw(double x, double y, double z, float yaw, float pitch) {
        this.spawn1X = x; this.spawn1Y = y; this.spawn1Z = z;
        this.spawn1Yaw = yaw; this.spawn1Pitch = pitch;
    }

    public double getSpawn2X()  { return spawn2X; }
    public double getSpawn2Y()  { return spawn2Y; }
    public double getSpawn2Z()  { return spawn2Z; }
    public float  getSpawn2Yaw()   { return spawn2Yaw; }
    public float  getSpawn2Pitch() { return spawn2Pitch; }

    public void setSpawn2Raw(double x, double y, double z, float yaw, float pitch) {
        this.spawn2X = x; this.spawn2Y = y; this.spawn2Z = z;
        this.spawn2Yaw = yaw; this.spawn2Pitch = pitch;
    }
}
