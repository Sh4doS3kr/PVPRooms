package com.pvprooms.bot;

/**
 * Difficulty levels for practice bots.
 * Each level affects reaction time, accuracy, and behavior complexity.
 */
public enum BotDifficulty {
    EASY("§aFácil", 600, 0.3, 0.4, 8, 60),
    MEDIUM("§eMedio", 400, 0.5, 0.6, 5, 40),
    HARD("§cDifícil", 200, 0.75, 0.8, 3, 20),
    HACKER("§4§lHACKER", 50, 0.95, 1.0, 1, 5);

    public final String displayName;
    public final int reactionTimeMs;      // How fast bot reacts
    public final double hitAccuracy;       // Chance to land a hit (0-1)
    public final double blockChance;       // Chance to block/shield
    public final int ticksBetweenActions;  // Ticks between combat decisions
    public final int healThreshold;        // HP% to start healing

    BotDifficulty(String displayName, int reactionTimeMs, double hitAccuracy, 
                  double blockChance, int ticksBetweenActions, int healThreshold) {
        this.displayName = displayName;
        this.reactionTimeMs = reactionTimeMs;
        this.hitAccuracy = hitAccuracy;
        this.blockChance = blockChance;
        this.ticksBetweenActions = ticksBetweenActions;
        this.healThreshold = healThreshold;
    }
}
