package com.pvprooms.model;

import java.util.*;
import java.util.stream.Collectors;

/** Stores the complete wall configuration for one arena. */
public class WallConfig {

    private final String arenaName;
    private final List<WallBlock> blocks;
    private final int minY;
    private final int maxY;

    public WallConfig(String arenaName, List<WallBlock> blocks) {
        this.arenaName = arenaName;
        this.blocks    = Collections.unmodifiableList(new ArrayList<>(blocks));
        this.minY = blocks.stream().mapToInt(b -> b.y).min().orElse(0);
        this.maxY = blocks.stream().mapToInt(b -> b.y).max().orElse(0);
    }

    /** Returns blocks grouped by Y level, sorted bottom→top. */
    public TreeMap<Integer, List<WallBlock>> byYAscending() {
        TreeMap<Integer, List<WallBlock>> map = new TreeMap<>();
        for (WallBlock b : blocks) {
            map.computeIfAbsent(b.y, k -> new ArrayList<>()).add(b);
        }
        return map;
    }

    /** Returns blocks grouped by Y level, sorted top→bottom. */
    public TreeMap<Integer, List<WallBlock>> byYDescending() {
        TreeMap<Integer, List<WallBlock>> map = new TreeMap<>(Collections.reverseOrder());
        for (WallBlock b : blocks) {
            map.computeIfAbsent(b.y, k -> new ArrayList<>()).add(b);
        }
        return map;
    }

    public String getArenaName() { return arenaName; }
    public List<WallBlock> getBlocks() { return blocks; }
    public int getMinY() { return minY; }
    public int getMaxY() { return maxY; }
    public int size()    { return blocks.size(); }
}
