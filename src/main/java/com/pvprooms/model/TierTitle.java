package com.pvprooms.model;

/**
 * Insignias de jugador basadas en la puntuación total de tiers acumulada en todos los kits.
 *
 * Orden de menor a mayor (por minScore):
 *  0  SIN_RANGO       —  Nunca jugado un duelo TIER
 *  1  NOVATO          —  Primera sangre
 *  2  SOLDADO         —  Se está formando
 *  3  LUCHADOR        —  Competidor real
 *  4  VETERANO        —  Experiencia probada
 *  5  GUERRERO        —  Domina varios kits
 *  6  CAMPEON         —  Entre los mejores de la liga
 *  7  GLADIADOR       —  Arena es su hogar
 *  8  ELITE           —  Nivel profesional
 *  9  MAESTRO         —  Maestro de Combate
 * 10  GRAN_MAESTRO    —  Cima del servidor
 * 11  LEYENDA         —  Solo los elegidos
 */
public enum TierTitle {

    SIN_RANGO   ("Sin Rango",        "§7",      "◌",   0),
    NOVATO      ("Novato",           "§8",      "✦",   15),
    SOLDADO     ("Soldado",          "§f",      "⚔",   60),
    LUCHADOR    ("Luchador",         "§a",      "⚔",   150),
    VETERANO    ("Veterano",         "§2",      "★",   280),
    GUERRERO    ("Guerrero",         "§b",      "★",   440),
    CAMPEON     ("Campeón",          "§3",      "♦",   640),
    GLADIADOR   ("Gladiador",        "§9",      "♦",   900),
    ELITE       ("Élite",            "§5",      "◆",   1200),
    MAESTRO     ("Maestro",          "§6",      "◆",   1600),
    GRAN_MAESTRO("Gran Maestro",     "§c",      "✦✦",  2100),
    LEYENDA     ("Leyenda",          "§4§l",    "★★",  2700);

    /** Nombre mostrado al jugador. */
    public final String name;
    /** Prefijo de color de Bukkit. */
    public final String colour;
    /** Símbolo decorativo (para el marcador y web). */
    public final String symbol;
    /** Puntuación mínima total para obtener esta insignia. */
    public final int    minScore;

    TierTitle(String name, String colour, String symbol, int minScore) {
        this.name     = name;
        this.colour   = colour;
        this.symbol   = symbol;
        this.minScore = minScore;
    }

    /** Nombre formateado con color, p.ej. "§6◆ Maestro". */
    public String formatted() {
        return colour + symbol + " " + name;
    }

    /** Devuelve la insignia correspondiente a la puntuación dada. */
    public static TierTitle fromScore(int score) {
        TierTitle result = SIN_RANGO;
        for (TierTitle t : values()) {
            if (score >= t.minScore) result = t;
            else break;
        }
        return result;
    }
}
