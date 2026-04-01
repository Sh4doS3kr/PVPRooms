package com.pvprooms.bot;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks player combat behavior to feed into the Adaptive AI system.
 * Records attacks, movement patterns, healing, etc.
 */
public class PlayerBehaviorTracker implements Listener {

    private final PvPRoomsPro plugin;
    private final Map<UUID, Long> lastAttackTime = new HashMap<>();
    private final Map<UUID, Long> lastJumpTime = new HashMap<>();
    private final Map<UUID, Boolean> wasOnGround = new HashMap<>();

    public PlayerBehaviorTracker(PvPRoomsPro plugin) {
        this.plugin = plugin;
        startJumpDetectionTask();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player)) return;
        
        // Only track during duels or bot practice
        if (!isInCombatSession(attacker)) return;
        
        AdaptiveAI ai = plugin.getBotManager().getAdaptiveAI();
        AdaptiveAI.PlayerProfile profile = ai.getProfile(attacker.getUniqueId());
        
        // Record attack
        boolean isCrit = attacker.getFallDistance() > 0 && !attacker.isOnGround();
        profile.recordAttack(isCrit, true);
        
        // Record W-tap (sprint reset detection)
        long now = System.currentTimeMillis();
        Long lastAttack = lastAttackTime.get(attacker.getUniqueId());
        if (lastAttack != null && now - lastAttack < 600 && !attacker.isSprinting()) {
            profile.recordWTap();
        }
        lastAttackTime.put(attacker.getUniqueId(), now);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        if (!isInCombatSession(player)) return;
        
        AdaptiveAI ai = plugin.getBotManager().getAdaptiveAI();
        AdaptiveAI.PlayerProfile profile = ai.getProfile(player.getUniqueId());
        
        if (event.isSprinting()) {
            profile.recordSprint();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!isInCombatSession(player)) return;
        
        AdaptiveAI ai = plugin.getBotManager().getAdaptiveAI();
        AdaptiveAI.PlayerProfile profile = ai.getProfile(player.getUniqueId());
        
        if (event.isSneaking()) {
            profile.recordSneak();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!isInCombatSession(player)) return;
        
        AdaptiveAI ai = plugin.getBotManager().getAdaptiveAI();
        AdaptiveAI.PlayerProfile profile = ai.getProfile(player.getUniqueId());
        
        double hpPercent = player.getHealth() / player.getMaxHealth();
        
        // Check if it's a healing item
        var type = event.getItem().getType();
        if (type.name().contains("GOLDEN_APPLE") || type.name().contains("ENCHANTED_GOLDEN_APPLE")) {
            profile.recordHeal(false, hpPercent);
        }
    }

    private void startJumpDetectionTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (!isInCombatSession(player)) continue;
                    
                    UUID uuid = player.getUniqueId();
                    Boolean prevOnGround = wasOnGround.get(uuid);
                    boolean currentOnGround = player.isOnGround();
                    
                    // Detect jump (was on ground, now not on ground with upward velocity)
                    if (prevOnGround != null && prevOnGround && !currentOnGround 
                            && player.getVelocity().getY() > 0.1) {
                        
                        Long lastJump = lastJumpTime.get(uuid);
                        long now = System.currentTimeMillis();
                        
                        // Cooldown to avoid spam detection
                        if (lastJump == null || now - lastJump > 300) {
                            AdaptiveAI ai = plugin.getBotManager().getAdaptiveAI();
                            ai.getProfile(uuid).recordJump();
                            lastJumpTime.put(uuid, now);
                        }
                    }
                    
                    wasOnGround.put(uuid, currentOnGround);
                    
                    // Record combat tick for frequency calculations
                    AdaptiveAI ai = plugin.getBotManager().getAdaptiveAI();
                    ai.getProfile(uuid).recordCombatTick();
                }
            }
        }.runTaskTimer(plugin, 1L, 2L); // Every 2 ticks
    }

    private boolean isInCombatSession(Player player) {
        UUID uuid = player.getUniqueId();
        
        // In a real duel
        if (plugin.getDuelManager().isInDuel(uuid)) return true;
        
        // In bot practice
        if (plugin.getBotManager().isInBotDuel(uuid)) return true;
        
        return false;
    }
}
