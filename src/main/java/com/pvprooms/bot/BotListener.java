package com.pvprooms.bot;

import com.pvprooms.PvPRoomsPro;
import net.citizensnpcs.api.event.NPCDeathEvent;
import net.citizensnpcs.api.event.NPCDespawnEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

/**
 * Handles events related to bot practice duels.
 */
public class BotListener implements Listener {

    private final PvPRoomsPro plugin;

    public BotListener(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // If player disconnects during bot duel, end the duel and mark for teleport on rejoin
        if (plugin.getBotManager().isInBotDuel(player.getUniqueId())) {
            plugin.getBotManager().onPlayerDisconnect(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Teleport to lobby if they disconnected during a bot duel
        if (plugin.getBotManager().wasInBotDuel(player.getUniqueId())) {
            plugin.getBotManager().clearDisconnectedPlayer(player.getUniqueId());
            // Teleport to lobby after 1 tick to ensure player is fully loaded
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.teleport(plugin.getLobbySpawn());
                plugin.getLobbyManager().giveLobbyItems(player);
                player.sendMessage(plugin.prefix() + "§7Tu duelo contra bot terminó por desconexión.");
            }, 5L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        
        if (plugin.getBotManager().isInBotDuel(player.getUniqueId())) {
            // Cancel death completely to prevent death screen
            event.setCancelled(true);
            event.setDeathMessage(null);
            event.getDrops().clear();
            event.setDroppedExp(0);
            
            // Immediately heal and respawn player to prevent death screen
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(20f);
            player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
            
            // Handle bot duel loss (teleport to lobby, etc.)
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
                    
                    LivingEntity botEntity = (LivingEntity) bot.getEntity();
                    
                    // Check if this damage will kill the bot
                    double finalDamage = event.getFinalDamage();
                    double currentHealth = botEntity.getHealth();
                    
                    if (currentHealth - finalDamage <= 0) {
                        // Bot will die - MUST handle manually to avoid death message errors
                        event.setCancelled(true);
                        
                        // Immediately end the duel and destroy bot
                        plugin.getBotManager().onBotKilled(bot, player);
                        return;
                    }
                    
                    // Apply knockback to bot (NPCs don't receive knockback by default)
                    Vector knockback = botEntity.getLocation().toVector()
                            .subtract(player.getLocation().toVector())
                            .normalize()
                            .multiply(0.5)
                            .setY(0.35);
                    
                    // Apply knockback after 1 tick to ensure damage is processed first
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (botEntity.isValid()) {
                            botEntity.setVelocity(botEntity.getVelocity().add(knockback));
                        }
                    }, 1L);
                }
            }
        }
    }
}
