package com.pvprooms.model;

import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a live duel between two players.
 * Stores state, the arena instance world name, the kit being used,
 * spectators, and timing information.
 */
public class Duel {

    public enum State {
        COUNTDOWN,  // Pre-fight countdown
        FIGHTING,   // Active fight
        ENDED       // Match is over, cleanup pending
    }

    private final UUID id;
    private final UUID player1;
    private final UUID player2;
    private final String kitName;
    private final String instanceWorldName;
    private final ArenaTemplate arenaTemplate;

    private State state = State.COUNTDOWN;
    private UUID winner;
    private long startTimeMillis;
    private int countdownTask  = -1;
    private int durationTask   = -1;
    private int scoreboardTask = -1;

    private final Set<UUID> spectators = new HashSet<>();

    public Duel(UUID player1, UUID player2, String kitName,
                String instanceWorldName, ArenaTemplate template) {
        this.id                = UUID.randomUUID();
        this.player1           = player1;
        this.player2           = player2;
        this.kitName           = kitName;
        this.instanceWorldName = instanceWorldName;
        this.arenaTemplate     = template;
        this.startTimeMillis   = System.currentTimeMillis();
    }

    // ── Participant helpers ────────────────────────────────────────────────

    public boolean hasPlayer(UUID uuid) {
        return player1.equals(uuid) || player2.equals(uuid);
    }

    /** Returns the opponent UUID of the given player inside this duel. */
    public UUID getOpponent(UUID uuid) {
        if (player1.equals(uuid)) return player2;
        if (player2.equals(uuid)) return player1;
        return null;
    }

    // ── Spectator helpers ──────────────────────────────────────────────────

    public void addSpectator(UUID uuid)    { spectators.add(uuid); }
    public void removeSpectator(UUID uuid) { spectators.remove(uuid); }
    public boolean isSpectator(UUID uuid)  { return spectators.contains(uuid); }
    public Set<UUID> getSpectators()       { return new HashSet<>(spectators); }

    // ── Elapsed time ──────────────────────────────────────────────────────

    public long getElapsedSeconds() {
        return (System.currentTimeMillis() - startTimeMillis) / 1000L;
    }

    // ── Getters / setters ──────────────────────────────────────────────────

    public UUID getId()               { return id; }
    public UUID getPlayer1()          { return player1; }
    public UUID getPlayer2()          { return player2; }
    public String getKitName()        { return kitName; }
    public String getInstanceWorldName() { return instanceWorldName; }
    public ArenaTemplate getArenaTemplate() { return arenaTemplate; }

    public State getState()           { return state; }
    public void setState(State state) { this.state = state; }

    public UUID getWinner()           { return winner; }
    public void setWinner(UUID winner){ this.winner = winner; }

    public long getStartTimeMillis()  { return startTimeMillis; }
    public void setStartTimeMillis(long t) { this.startTimeMillis = t; }

    public int getCountdownTask()  { return countdownTask; }
    public void setCountdownTask(int t) { this.countdownTask = t; }

    public int getDurationTask()   { return durationTask; }
    public void setDurationTask(int t) { this.durationTask = t; }

    public int getScoreboardTask() { return scoreboardTask; }
    public void setScoreboardTask(int t) { this.scoreboardTask = t; }
}
