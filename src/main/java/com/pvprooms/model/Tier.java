package com.pvprooms.model;

import org.bukkit.Material;

/**
 * Standard competitive Minecraft PvP tier ladder used by the community.
 * Source: mctiers.com / r/CompetitiveMinecraft
 *
 * Order from lowest (ordinal 0) to highest (ordinal 10):
 *   0  UNRANKED  —  no rank yet             < 800  ELO
 *   1  LT5       —  Low  Tier 5             800  – 999
 *   2  HT5       —  High Tier 5            1000  – 1199  ← default start ~1000
 *   3  LT4       —  Low  Tier 4            1200  – 1499
 *   4  HT4       —  High Tier 4            1500  – 1799
 *   5  LT3       —  Low  Tier 3            1800  – 2099
 *   6  HT3       —  High Tier 3            2100  – 2399
 *   7  LT2       —  Low  Tier 2            2400  – 2699
 *   8  HT2       —  High Tier 2            2700  – 2999
 *   9  LT1       —  Low  Tier 1            3000  – 3399
 *  10  HT1       —  High Tier 1 (elite)    3400+
 */
public enum Tier {

    UNRANKED(   0, "Unranked", "§7",    Material.GRAY_CONCRETE),
    LT5     ( 800, "LT5",      "§8",    Material.STONE),
    HT5     (1000, "HT5",      "§f",    Material.IRON_INGOT),
    LT4     (1200, "LT4",      "§a",    Material.GOLD_INGOT),
    HT4     (1500, "HT4",      "§2",    Material.GOLDEN_SWORD),
    LT3     (1800, "LT3",      "§b",    Material.DIAMOND),
    HT3     (2100, "HT3",      "§3",    Material.DIAMOND_SWORD),
    LT2     (2400, "LT2",      "§9",    Material.EMERALD),
    HT2     (2700, "HT2",      "§5",    Material.NETHER_STAR),
    LT1     (3000, "LT1",      "§6",    Material.TOTEM_OF_UNDYING),
    HT1     (3400, "HT1",      "§c§l",  Material.BEACON);

    /** Minimum ELO required to reach this tier. */
    public final int      minElo;
    /** Short display name (e.g. "HT1", "LT3", "Unranked"). */
    public final String   displayName;
    /** Bukkit colour code prefix. */
    public final String   colour;
    /** Material used as icon in GUIs. */
    public final Material icon;

    Tier(int minElo, String displayName, String colour, Material icon) {
        this.minElo       = minElo;
        this.displayName  = displayName;
        this.colour       = colour;
        this.icon         = icon;
    }

    /** Returns the coloured display string, e.g. "§c§lHT1". */
    public String formatted() {
        return colour + "§l" + displayName;
    }

    /** Returns true if this tier is at most 1 step apart from the other (for queue expansion). */
    public boolean isAdjacent(Tier other) {
        return Math.abs(this.ordinal() - other.ordinal()) <= 1;
    }

    /** Returns the highest tier whose minElo ≤ elo. */
    public static Tier fromElo(int elo) {
        Tier result = UNRANKED;
        for (Tier t : values()) {
            if (elo >= t.minElo) result = t;
            else break;
        }
        return result;
    }
}
