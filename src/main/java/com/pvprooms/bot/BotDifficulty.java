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
    
    EASY("§aFácil", 600, 0.70, 0.25, 4, 50),        // Beginner - slower but still attacks
    MEDIUM("§eMedio", 500, 0.80, 0.35, 3, 40),     // Average player - consistent attacks
    HARD("§cDifícil", 300, 0.93, 0.50, 2, 35),     // Skilled player - fast, accurate, heals early
    HACKER("§4§lHACKER", 150, 0.98, 0.70, 1, 25),  // Inhuman - insane reactions and accuracy
    ADAPTIVE("§d§lAdaptivo", 450, 0.85, 0.40, 3, 35), // Learns from player
    DUMMY("§7Quieto", 9999, 0.0, 0.0, 20, 0);      // Practice dummy - no movement, no attacks, massive HP

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
