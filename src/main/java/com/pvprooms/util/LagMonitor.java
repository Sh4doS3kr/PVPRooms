package com.pvprooms.util;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Real-time lag spike detector and adaptive load reducer.
 *
 * Detection:
 *   Measures actual wall-clock time between main-thread ticks.
 *   A healthy tick completes in ≤ 50 ms (20 TPS). Sustained ticks
 *   above 80 ms signal a lag spike — regardless of source (GC, HDD,
 *   another plugin, external process, etc.).
 *
 * Levels:
 *   NORMAL  — TPS ≥ 17  → full feature set
 *   MILD    — TPS 10-17 → reduce optional update rates
 *   SEVERE  — TPS < 10  → skip all non-critical per-tick work
 *
 * Mitigation callbacks are invoked on level transitions so managers
 * can react (increase update intervals, skip packets, defer I/O).
 */
public class LagMonitor {

    // ── Lag levels ────────────────────────────────────────────────────────
    public enum LagLevel { NORMAL, MILD, SEVERE }

    // ── Thresholds ────────────────────────────────────────────────────────
    /** Rolling window size in ticks */
    private static final int  SAMPLE      = 20;
    /** TPS at or below which lag is MILD */
    private static final double MILD_TPS  = 17.0;
    /** TPS at or below which lag is SEVERE */
    private static final double SEVERE_TPS = 10.0;

    // ── State ─────────────────────────────────────────────────────────────
    private final PvPRoomsPro plugin;
    private final long[]  samples = new long[SAMPLE];
    private int           head    = 0;
    private long          lastNs  = 0;

    private volatile double    currentTPS = 20.0;
    private volatile LagLevel  level      = LagLevel.NORMAL;
    /** Consecutive severe ticks before declaring SEVERE (avoids false positives) */
    private int severeStreak  = 0;
    private int mildStreak    = 0;
    private static final int STREAK_REQUIRED = 3;

    private BukkitTask task;

    public LagMonitor(PvPRoomsPro plugin) {
        this.plugin = plugin;
        // Pre-fill samples with healthy 50 ms ticks
        for (int i = 0; i < SAMPLE; i++) samples[i] = 50_000_000L;
    }

    // ── Public API ────────────────────────────────────────────────────────

    public void start() {
        lastNs = System.nanoTime();
        task = new BukkitRunnable() {
            @Override public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void shutdown() {
        if (task != null) { task.cancel(); task = null; }
    }

    /** Current measured TPS (0 – 20). */
    public double getTPS() { return currentTPS; }

    /** Current lag severity. */
    public LagLevel getLevel() { return level; }

    /** True if TPS has dropped below normal threshold. */
    public boolean isLagging() { return level != LagLevel.NORMAL; }

    /** True only when lag is critically severe — skip everything optional. */
    public boolean isSevere() { return level == LagLevel.SEVERE; }

    // ── Tick measurement ──────────────────────────────────────────────────

    private void tick() {
        long now     = System.nanoTime();
        long elapsed = now - lastNs;
        lastNs       = now;

        samples[head] = elapsed;
        head = (head + 1) % SAMPLE;

        // Rolling average tick duration
        long sum = 0;
        for (long s : samples) sum += s;
        double avgMs = (sum / (double) SAMPLE) / 1_000_000.0;

        // Convert avg tick time to TPS (cap at 20)
        currentTPS = Math.min(20.0, 1_000.0 / Math.max(avgMs, 1.0));

        // Evaluate new level with hysteresis to avoid flapping
        LagLevel candidate;
        if (currentTPS <= SEVERE_TPS) {
            candidate = LagLevel.SEVERE;
        } else if (currentTPS <= MILD_TPS) {
            candidate = LagLevel.MILD;
        } else {
            candidate = LagLevel.NORMAL;
        }

        if (candidate == LagLevel.SEVERE) {
            severeStreak++;
            mildStreak = 0;
        } else if (candidate == LagLevel.MILD) {
            mildStreak++;
            severeStreak = 0;
        } else {
            severeStreak = 0;
            mildStreak   = 0;
        }

        LagLevel newLevel;
        if (severeStreak >= STREAK_REQUIRED) {
            newLevel = LagLevel.SEVERE;
        } else if (mildStreak >= STREAK_REQUIRED) {
            newLevel = LagLevel.MILD;
        } else if (candidate == LagLevel.NORMAL && level != LagLevel.NORMAL) {
            // Recover to NORMAL immediately once TPS is healthy again
            newLevel = LagLevel.NORMAL;
        } else {
            newLevel = level; // no change yet
        }

        if (newLevel != level) {
            LagLevel prev = level;
            level = newLevel;
            onLevelChange(prev, newLevel);
        }
    }

    // ── Level change notification ──────────────────────────────────────────

    private void onLevelChange(LagLevel from, LagLevel to) {
        String tpsStr = String.format("%.1f", currentTPS);
        switch (to) {
            case NORMAL -> {
                if (from != LagLevel.NORMAL) {
                    plugin.getLogger().info("[LagMonitor] TPS restaurado (" + tpsStr + ") — reanudando operaciones normales.");
                }
            }
            case MILD -> plugin.getLogger().warning(
                "[LagMonitor] Lag detectado — TPS: " + tpsStr +
                " — Reduciendo carga del plugin...");
            case SEVERE -> plugin.getLogger().severe(
                "[LagMonitor] ¡LAG GRAVE! TPS: " + tpsStr +
                " — Modo emergencia: saltando operaciones no críticas.");
        }
        // Notify plugin to apply / remove mitigations
        plugin.applyLagMitigation(to);
    }
}
