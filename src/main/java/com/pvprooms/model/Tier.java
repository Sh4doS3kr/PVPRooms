package com.pvprooms.model;

import org.bukkit.Material;

/**
 * Custom PvP rank ladder for this server.
 *
 * Order from lowest (ordinal 0) to highest (ordinal 8):
 *   0  UNRANKED   — never played           ELO  < 800  |  pts  < 0
 *   1  HIERRO     — Iron                   ELO  800+   |  pts  0+
 *   2  BRONCE     — Bronze                 ELO 1000+   |  pts  100+
 *   3  PLATA      — Silver                 ELO 1200+   |  pts  300+
 *   4  ORO        — Gold                   ELO 1500+   |  pts  600+
 *   5  ESMERALDA  — Emerald                ELO 1800+   |  pts 1000+
 *   6  DIAMANTE   — Diamond                ELO 2100+   |  pts 1400+
 *   7  MAESTRO    — Master                 ELO 2400+   |  pts 1900+
 *   8  LEYENDA    — Legend (elite)         ELO 2800+   |  pts 2600+
 */
public enum Tier {

    //                    minElo  minPts  display       colour    icon
    UNRANKED  (    0,    -1, "Sin Rango",  "§7",     Material.GRAY_CONCRETE),
    HIERRO    (  800,     0, "Hierro",     "§8",     Material.IRON_INGOT),
    BRONCE    ( 1000,   100, "Bronce",     "§6",     Material.COPPER_INGOT),
    PLATA     ( 1200,   300, "Plata",      "§f",     Material.IRON_BLOCK),
    ORO       ( 1500,   600, "Oro",        "§e",     Material.GOLD_INGOT),
    ESMERALDA ( 1800,  1000, "Esmeralda",  "§a",     Material.EMERALD),
    DIAMANTE  ( 2100,  1400, "Diamante",   "§b",     Material.DIAMOND),
    MAESTRO   ( 2400,  1900, "Maestro",    "§5",     Material.NETHER_STAR),
    LEYENDA   ( 2800,  2600, "Leyenda",    "§c§l",   Material.BEACON);

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
            case UNRANKED   -> 0;
            case HIERRO     -> 10;
            case BRONCE     -> 25;
            case PLATA      -> 45;
            case ORO        -> 70;
            case ESMERALDA  -> 100;
            case DIAMANTE   -> 135;
            case MAESTRO    -> 175;
            case LEYENDA    -> 220;
        };
    }

    /**
     * Resolves a tier from tier-points (TIER queue system, independent of ELO).
     * Returns UNRANKED only if pts {@literal <} 0 (never played that kit).
     */
    public static Tier fromPoints(int pts) {
        if (pts < 0) return UNRANKED;
        Tier result = HIERRO;
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
