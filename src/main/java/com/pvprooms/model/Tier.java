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

    //                 minElo  minPts  display    colour   icon
    UNRANKED(   0,    -1, "Sin Rango", "§7",    Material.GRAY_CONCRETE),
    LT5     ( 800,     0, "LT5",      "§8",    Material.STONE),
    HT5     (1000,   100, "HT5",      "§f",    Material.IRON_INGOT),
    LT4     (1200,   300, "LT4",      "§a",    Material.GOLD_INGOT),
    HT4     (1500,   600, "HT4",      "§2",    Material.GOLDEN_SWORD),
    LT3     (1800,  1000, "LT3",      "§b",    Material.DIAMOND),
    HT3     (2100,  1400, "HT3",      "§3",    Material.DIAMOND_SWORD),
    LT2     (2400,  1900, "LT2",      "§9",    Material.EMERALD),
    HT2     (2700,  2500, "HT2",      "§5",    Material.NETHER_STAR),
    LT1     (3000,  3100, "LT1",      "§6",    Material.TOTEM_OF_UNDYING),
    HT1     (3400,  3800, "HT1",      "§c§l",  Material.BEACON);

    /** Minimum ELO required to reach this tier (ELO queue). */
    public final int      minElo;
    /** Minimum tier-points required (TIER queue). -1 = UNRANKED sentinel. */
    public final int      minPoints;
    /** Short display name. */
    public final String   displayName;
    /** Bukkit colour code prefix. */
    public final String   colour;
    /** Material used as icon in GUIs. */
    public final Material icon;

    Tier(int minElo, int minPoints, String displayName, String colour, Material icon) {
        this.minElo       = minElo;
        this.minPoints    = minPoints;
        this.displayName  = displayName;
        this.colour       = colour;
        this.icon         = icon;
    }

    /** Returns the coloured display string, e.g. "§c§lHT1". */
    public String formatted() {
        return colour + "§l" + displayName;
    }

    /**
     * Score this tier contributes to the overall ranking (sum across all kits).
     * Used by TierManager to compute a player's total score.
     */
    public int tierScore() {
        return switch (this) {
            case UNRANKED -> 0;
            case LT5     -> 15;
            case HT5     -> 25;
            case LT4     -> 40;
            case HT4     -> 60;
            case LT3     -> 80;
            case HT3     -> 100;
            case LT2     -> 125;
            case HT2     -> 150;
            case LT1     -> 185;
            case HT1     -> 220;
        };
    }

    /**
     * Resolves a tier from tier-points (TIER queue system, independent of ELO).
     * Returns UNRANKED only if pts {@literal <} 0 (never played that kit).
     */
    public static Tier fromPoints(int pts) {
        if (pts < 0) return UNRANKED;
        Tier result = LT5;
        for (Tier t : values()) {
            if (t == UNRANKED) continue;
            if (pts >= t.minPoints) result = t;
            else break;
        }
        return result;
    }

    /** Returns true if this tier is at most 1 step apart from the other (for queue expansion). */
    public boolean isAdjacent(Tier other) {
        return Math.abs(this.ordinal() - other.ordinal()) <= 1;
    }

    /**
     * Returns UNRANKED if the player hasn't played yet,
     * otherwise resolves from their ELO.
     */
    public static Tier forPlayer(com.pvprooms.managers.EloManager eloManager, java.util.UUID uuid) {
        if (!eloManager.hasEloRecord(uuid)) return UNRANKED;
        return fromElo(eloManager.getElo(uuid));
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
