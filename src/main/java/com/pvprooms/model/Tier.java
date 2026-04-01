package com.pvprooms.model;

import org.bukkit.Material;

/**
 * MCTiers competitive ranking system.
 *
 * Sistema de puntos DIFÍCIL - requiere muchas victorias:
 *   - Ganas ~8-12 puntos por victoria (según diferencia de tier)
 *   - Pierdes ~6-10 puntos por derrota
 *   - Necesitas ~15-25 victorias para subir cada tier
 *   - Tiers Elite (LT2+) requieren verificación manual
 *
 * Order from lowest (ordinal 0) to highest (ordinal 10):
 *   0  UNRANKED — never played              pts < 0
 *   1  LT5      — Low Tier 5 (Beginner)     pts 0+    (~0 wins)
 *   2  HT5      — High Tier 5               pts 100+  (~4 wins)
 *   3  LT4      — Low Tier 4                pts 300+  (~12 wins)
 *   4  HT4      — High Tier 4               pts 600+  (~24 wins)
 *   5  LT3      — Low Tier 3                pts 1000+ (~40 wins)
 *   6  HT3      — High Tier 3               pts 1500+ (~60 wins)
 *   7  LT2      — Low Tier 2 (Elite)        pts 2200+ (Verificación)
 *   8  HT2      — High Tier 2 (Elite)       pts 3200+ (Verificación)
 *   9  LT1      — Low Tier 1 (Elite)        pts 4500+ (Verificación)
 *  10  HT1      — High Tier 1 (Elite)       pts 6000+ (Verificación)
 */
public enum Tier {

    //                    minElo  minPts  display     colour    icon
    UNRANKED  (    0,    -1, "Unranked",  "§7",     Material.GRAY_CONCRETE),
    LT5       (  800,     0, "LT5",       "§9",     Material.LIGHT_BLUE_DYE),
    HT5       (  900,   100, "HT5",       "§b",     Material.LIGHT_BLUE_CONCRETE),
    LT4       ( 1000,   300, "LT4",       "§a",     Material.LIME_DYE),
    HT4       ( 1100,   600, "HT4",       "§2",     Material.LIME_CONCRETE),
    LT3       ( 1250,  1000, "LT3",       "§e",     Material.YELLOW_DYE),
    HT3       ( 1400,  1500, "HT3",       "§6",     Material.ORANGE_CONCRETE),
    LT2       ( 1600,  2200, "LT2",       "§c",     Material.ORANGE_DYE),      // Elite - Verificación
    HT2       ( 1850,  3200, "HT2",       "§4",     Material.RED_CONCRETE),    // Elite - Verificación
    LT1       ( 2150,  4500, "LT1",       "§d",     Material.PINK_DYE),        // Elite - Verificación
    HT1       ( 2500,  6000, "HT1",       "§c§l",   Material.RED_DYE);         // Elite - Verificación

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
            case LT5      -> 5;
            case HT5      -> 15;
            case LT4      -> 30;
            case HT4      -> 50;
            case LT3      -> 75;
            case HT3      -> 105;
            case LT2      -> 140;
            case HT2      -> 180;
            case LT1      -> 225;
            case HT1      -> 280;
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
