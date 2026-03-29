package com.pvprooms.model;

import org.bukkit.Registry;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

/**
 * Lightweight wrapper around a Minecraft armor trim combination.
 * Stores material and pattern as their registry key strings (e.g. "iron", "bolt").
 */
public class Trim {

    private final String material;
    private final String pattern;

    public Trim(String material, String pattern) {
        this.material = material.toLowerCase();
        this.pattern  = pattern.toLowerCase();
    }

    public String getMaterial() { return material; }
    public String getPattern()  { return pattern; }

    /** Resolves the Bukkit TrimMaterial. Returns null if key is unknown. */
    public TrimMaterial toBukkitMaterial() {
        for (TrimMaterial m : Registry.TRIM_MATERIAL) {
            if (m.getKey().getKey().equalsIgnoreCase(material)) return m;
        }
        return null;
    }

    /** Resolves the Bukkit TrimPattern. Returns null if key is unknown. */
    public TrimPattern toBukkitPattern() {
        for (TrimPattern p : Registry.TRIM_PATTERN) {
            if (p.getKey().getKey().equalsIgnoreCase(pattern)) return p;
        }
        return null;
    }

    /** Serialises to "material:pattern" for YAML storage. */
    @Override
    public String toString() { return material + ":" + pattern; }

    /** Deserialises from "material:pattern". Returns null on bad input. */
    public static Trim fromString(String s) {
        if (s == null || !s.contains(":")) return null;
        String[] parts = s.split(":", 2);
        return new Trim(parts[0].trim(), parts[1].trim());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Trim other)) return false;
        return material.equals(other.material) && pattern.equals(other.pattern);
    }

    @Override
    public int hashCode() { return 31 * material.hashCode() + pattern.hashCode(); }
}
