package com.pvprooms.listeners;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Duel;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles combat events during duels.
 *
 * Responsibilities:
 *  - Cancels all PvP damage outside of active duels (friendly fire protection)
 *  - Prevents spectators from receiving or dealing damage
 *  - Cancels environmental damage during countdown (fall, fire, etc.)
 *  - Updates scoreboards after damage events during a fight
 */
public class CombatListener implements Listener {

    private final PvPRoomsPro plugin;
    
    /** Tracks pending swings - if a swing doesn't result in a hit within 50ms, it's a miss */
    private final Map<UUID, Long> pendingSwings = new ConcurrentHashMap<>();

    public CombatListener(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ── Crystal kit: attack speed changes with held item ────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        Duel duel = plugin.getDuelManager().getDuelByPlayer(player.getUniqueId());
        boolean inBotCrystal = plugin.getBotManager().isInBotDuel(player.getUniqueId())
                && "crystal".equalsIgnoreCase(plugin.getBotManager().getBotDuel(player.getUniqueId()) != null
                ? plugin.getBotManager().getBotDuel(player.getUniqueId()).kitName : "");

        String ffaKit = plugin.getDuelManager().getFFAKit(player.getUniqueId());
        boolean inFfaCrystal = "crystal".equalsIgnoreCase(ffaKit);

        boolean inCrystalDuel = (duel != null && "crystal".equalsIgnoreCase(duel.getKitName()))
                || inBotCrystal || inFfaCrystal;
        if (!inCrystalDuel) return;

        var atkSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (atkSpeed == null) return;

        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        boolean holdingCrystal = newItem != null && newItem.getType() == Material.END_CRYSTAL;

        if (holdingCrystal) {
            atkSpeed.setBaseValue(1024.0);
        } else {
            atkSpeed.setBaseValue(4.0);
        }
    }
    
    // ── Swing tracking for accuracy ─────────────────────────────────────────
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Only track swings during duels
        if (!plugin.getDuelManager().isInDuel(uuid) && 
            !plugin.getDuelManager().isInFFA(uuid) &&
            (plugin.getBotManager() == null || !plugin.getBotManager().isInBotDuel(uuid))) {
            return;
        }
        
        // Record this swing as pending
        long now = System.currentTimeMillis();
        Long lastSwing = pendingSwings.put(uuid, now);
        
        // Check if previous swing was a miss (no hit recorded within 100ms)
        if (lastSwing != null && now - lastSwing > 100) {
            plugin.getStatsManager().recordMiss(uuid, player.getName());
        }
        
        // Schedule check for this swing
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Long swingTime = pendingSwings.get(uuid);
            if (swingTime != null && swingTime == now) {
                // This swing didn't result in a hit - record as miss
                pendingSwings.remove(uuid);
                plugin.getStatsManager().recordMiss(uuid, player.getName());
            }
        }, 2L); // 2 ticks = 100ms
    }
    
    /** Called when a hit is recorded to clear the pending swing */
    private void clearPendingSwing(UUID uuid) {
        pendingSwings.remove(uuid);
    }

    // ── PvP damage control ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // ── Bot Duel: Player attacking bot ──
        if (event.getEntity() instanceof LivingEntity && CitizensAPI.getNPCRegistry() != null) {
            NPC npc = CitizensAPI.getNPCRegistry().getNPC(event.getEntity());
            if (npc != null && event.getDamager() instanceof Player player) {
                if (plugin.getBotManager() != null && plugin.getBotManager().isInBotDuel(player.getUniqueId())) {
                    NPC playerBot = plugin.getBotManager().getPlayerBot(player.getUniqueId());
                    if (playerBot != null && playerBot.getId() == npc.getId()) {
                        // Player attacking their bot - ALLOW damage
                        event.setCancelled(false);
                        return;
                    }
                }
            }
        }
        
        // ── Bot Duel: Bot attacking player ──
        if (event.getEntity() instanceof Player victim && CitizensAPI.getNPCRegistry() != null) {
            NPC attackerNpc = CitizensAPI.getNPCRegistry().getNPC(event.getDamager());
            if (attackerNpc != null) {
                if (plugin.getBotManager() != null && plugin.getBotManager().isInBotDuel(victim.getUniqueId())) {
                    NPC playerBot = plugin.getBotManager().getPlayerBot(victim.getUniqueId());
                    if (playerBot != null && playerBot.getId() == attackerNpc.getId()) {
                        // Bot attacking player - ALLOW damage
                        event.setCancelled(false);
                        return;
                    }
                }
            }
        }
        
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(event.getDamager());

        // Check if victim is in FFA match first
        if (plugin.getDuelManager().isInFFA(victim.getUniqueId())) {
            // Allow end crystal explosion damage (damager is EnderCrystal, not a player)
            if (attacker == null && event.getDamager() instanceof org.bukkit.entity.EnderCrystal) {
                if (plugin.getDuelManager().isFrozen(victim.getUniqueId())) {
                    event.setCancelled(true);
                    return;
                }
                return; // allow crystal damage in FFA
            }
            // Both must be in the same FFA match
            if (attacker != null && plugin.getDuelManager().isInFFA(attacker.getUniqueId())) {
                UUID victimMatch = plugin.getDuelManager().getFFAMatchId(victim.getUniqueId());
                UUID attackerMatch = plugin.getDuelManager().getFFAMatchId(attacker.getUniqueId());
                if (victimMatch != null && victimMatch.equals(attackerMatch)) {
                    // Check if still frozen (countdown)
                    if (plugin.getDuelManager().isFrozen(victim.getUniqueId()) || 
                        plugin.getDuelManager().isFrozen(attacker.getUniqueId())) {
                        event.setCancelled(true);
                        return;
                    }
                    // 2v2 friendly fire prevention — teammates can't hurt each other
                    if (plugin.getDuelManager().areTeammates(attacker.getUniqueId(), victim.getUniqueId())) {
                        event.setCancelled(true);
                        return;
                    }
                    // Allow FFA/2v2 damage
                    return;
                }
            }
            // Attacker not in same FFA - cancel
            event.setCancelled(true);
            return;
        }

        // Protect victim: must be in an active FIGHTING duel
        Duel victimDuel = plugin.getDuelManager().getDuelByPlayer(victim.getUniqueId());

        if (victimDuel == null) {
            // Victim is not in a duel — cancel incoming PvP
            if (attacker != null) event.setCancelled(true);
            return;
        }

        // Victim is a spectator inside a duel — no damage
        if (victimDuel.isSpectator(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Duel must be in FIGHTING state (not COUNTDOWN)
        if (victimDuel.getState() != Duel.State.FIGHTING) {
            event.setCancelled(true);
            return;
        }

        if (attacker != null) {
            Duel attackerDuel = plugin.getDuelManager().getDuelByPlayer(attacker.getUniqueId());

            // Attacker must be in the same duel
            if (attackerDuel == null || !attackerDuel.getId().equals(victimDuel.getId())) {
                event.setCancelled(true);
                return;
            }

            // Attacker cannot be a spectator
            if (attackerDuel.isSpectator(attacker.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
        }

        // Record hit for accuracy tracking and clear pending swing
        if (attacker != null) {
            clearPendingSwing(attacker.getUniqueId());
            plugin.getStatsManager().recordHit(attacker.getUniqueId(), attacker.getName());
        }
        
        // Damage is valid — update scoreboard for both players after 1 tick
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player p1 = plugin.getServer().getPlayer(victimDuel.getPlayer1());
            Player p2 = plugin.getServer().getPlayer(victimDuel.getPlayer2());
            if (p1 != null) plugin.getScoreboardManager().updateDuelScoreboard(p1, victimDuel);
            if (p2 != null) plugin.getScoreboardManager().updateDuelScoreboard(p2, victimDuel);
        }, 1L);
    }

    // ── Environmental damage during countdown ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Duel duel = plugin.getDuelManager().getDuelByPlayer(player.getUniqueId());
        if (duel == null) return;

        // Cancel all damage during countdown phase
        if (duel.getState() == Duel.State.COUNTDOWN) {
            event.setCancelled(true);
            return;
        }

        // Cancel all damage for spectators
        if (duel.isSpectator(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ── Utility ────────────────────────────────────────────────────────────

    /**
     * Resolves the attacking Player from an event damager entity.
     * Handles direct hits and projectiles (arrows, tridents, etc.).
     */
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
