package com.pvprooms.listeners;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Duel;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.UUID;

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

    public CombatListener(PvPRoomsPro plugin) {
        this.plugin = plugin;
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
                    // Allow FFA damage
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
