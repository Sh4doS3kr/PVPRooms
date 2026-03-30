package com.pvprooms.bot;

import com.pvprooms.PvPRoomsPro;
import net.citizensnpcs.api.event.NPCDeathEvent;
import net.citizensnpcs.api.event.NPCDespawnEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Handles events related to bot practice duels.
 */
public class BotListener implements Listener {

    private final PvPRoomsPro plugin;

    public BotListener(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        
        if (plugin.getBotManager().isInBotDuel(player.getUniqueId())) {
            // Cancel death message for bot duels
            event.setDeathMessage(null);
            
            // Handle bot duel loss
            plugin.getBotManager().onPlayerDeath(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNPCDeath(NPCDeathEvent event) {
        NPC npc = event.getNPC();
        plugin.getBotManager().onBotDeath(npc);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNPCDespawn(NPCDespawnEvent event) {
        // If bot despawns unexpectedly, end the duel
        NPC npc = event.getNPC();
        plugin.getBotManager().onBotDeath(npc);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Allow damage between players and bots in bot duels
        if (event.getDamager() instanceof Player player) {
            if (plugin.getBotManager().isInBotDuel(player.getUniqueId())) {
                NPC bot = plugin.getBotManager().getPlayerBot(player.getUniqueId());
                if (bot != null && bot.getEntity() != null 
                        && bot.getEntity().equals(event.getEntity())) {
                    // Player attacking their bot - allow it
                    event.setCancelled(false);
                }
            }
        }
    }
}
