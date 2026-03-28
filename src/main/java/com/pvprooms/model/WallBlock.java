package com.pvprooms.model;

import org.bukkit.Material;

/** A single block position that belongs to a wall configuration. */
public class WallBlock {
    public final int x, y, z;
    public final Material material;

    public WallBlock(int x, int y, int z, Material material) {
        this.x = x; this.y = y; this.z = z; this.material = material;
    }

    /** Serialises to "x,y,z,MATERIAL" for YAML storage. */
    public String serialize() {
        return x + "," + y + "," + z + "," + material.name();
    }

    /** Deserialises from "x,y,z,MATERIAL". Returns null on parse error. */
    public static WallBlock deserialize(String s) {
        String[] p = s.split(",", 4);
        if (p.length < 4) return null;
        try {
            Material mat = Material.matchMaterial(p[3]);
            if (mat == null) return null;
            return new WallBlock(Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                    Integer.parseInt(p[2]), mat);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
