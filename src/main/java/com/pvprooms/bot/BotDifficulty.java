package com.pvprooms.bot;

/**
 * Difficulty levels for practice bots.
 * Each level affects reaction time, accuracy, and behavior complexity.
 */
public enum BotDifficulty {
    // Human-like difficulty values based on real Minecraft PvP
    // Average human reaction time: 200-300ms
    // Minecraft attack cooldown: 500ms (10 ticks)
    // Professional players hit ~70% of attacks
    
    EASY("§aFácil", 500, 0.35, 0.3, 10, 50),      // Beginner player
    MEDIUM("§eMedio", 350, 0.50, 0.45, 8, 40),    // Average player
    HARD("§cDifícil", 280, 0.62, 0.55, 6, 30),    // Skilled player (human-like)
    HACKER("§4§lHACKER", 80, 0.92, 0.85, 2, 10);  // Inhuman (for challenge)

    public final String displayName;
    public final int reactionTimeMs;      // How fast bot reacts (humans: 200-300ms)
    public final double hitAccuracy;       // Chance to land a hit (good players: 60-70%)
    public final double blockChance;       // Chance to block/shield
    public final int ticksBetweenActions;  // Ticks between combat decisions (vanilla: 10)
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
