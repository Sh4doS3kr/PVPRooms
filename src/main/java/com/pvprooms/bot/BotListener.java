package com.pvprooms.bot;

import com.pvprooms.PvPRoomsPro;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCDeathEvent;
import net.citizensnpcs.api.event.NPCDespawnEvent;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.BroadcastMessageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
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

    /**
     * Intercept broadcast messages (death messages, kill messages from other plugins)
     * and hide any that mention a practice bot name.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBroadcastMessage(BroadcastMessageEvent event) {
        String msg = PlainTextComponentSerializer.plainText().serialize(event.message());
        // Check if message contains any active bot name
        for (NPC bot : plugin.getBotManager().getAllActiveBots()) {
            if (bot != null && msg.contains(bot.getName())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // ── Resolve the actual attacker (could be a projectile) ───────────────
        Player player = null;
        Vector arrowKnockbackDir = null;
        if (event.getDamager() instanceof Player p) {
            player = p;
        } else if (event.getDamager() instanceof Projectile proj
                && proj.getShooter() instanceof Player p) {
            player = p;
            // Knockback from arrow follows the arrow's travel direction
            arrowKnockbackDir = proj.getVelocity().normalize();
            arrowKnockbackDir.setY(0);
            if (arrowKnockbackDir.lengthSquared() < 0.001) arrowKnockbackDir = null;
        }

        // Allow damage between players and bots in bot duels
        if (player != null) {
            final Vector arrowDir = arrowKnockbackDir;
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
                        // Check for totem in offhand first
                        ItemStack offhand = botEntity.getEquipment().getItemInOffHand();
                        if (offhand != null && offhand.getType() == org.bukkit.Material.TOTEM_OF_UNDYING) {
                            // Totem activates! Cancel death, apply totem effects
                            event.setCancelled(true);
                            
                            // Consume totem — must call setItemInOffHand; mutating the copy has no effect on NPC equipment
                            int totemAmount = offhand.getAmount();
                            if (totemAmount <= 1) {
                                botEntity.getEquipment().setItemInOffHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR));
                            } else {
                                offhand.setAmount(totemAmount - 1);
                                botEntity.getEquipment().setItemInOffHand(offhand);
                            }
                            
                            // Apply totem effects (vanilla behavior)
                            botEntity.setHealth(1.0);
                            botEntity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.REGENERATION, 900, 1)); // 45 sec Regen II
                            botEntity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.ABSORPTION, 100, 1)); // 5 sec Absorption II
                            botEntity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, 800, 0)); // 40 sec Fire Res
                            
                            // Totem animation and sound
                            botEntity.getWorld().playSound(botEntity.getLocation(), 
                                org.bukkit.Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
                            botEntity.getWorld().spawnParticle(
                                org.bukkit.Particle.TOTEM_OF_UNDYING, 
                                botEntity.getLocation().add(0, 1, 0), 100, 0.5, 1, 0.5, 0.5);
                            
                            return;
                        }
                        
                        // No totem - Bot will die
                        event.setCancelled(true);
                        
                        // Immediately end the duel and destroy bot
                        plugin.getBotManager().onBotKilled(bot, player);
                        return;
                    }
                    
                    // Shield blocking: NMS startUsingItem makes vanilla apply reduction
                    // automatically via the BLOCKING damage modifier in the event.
                    // We only need to play the sound here.
                    BotManager.BotDuel botDuel = plugin.getBotManager().getBotDuel(player.getUniqueId());
                    boolean shielding = botDuel != null && botDuel.ai != null && botDuel.ai.isShieldBlocking();
                    if (shielding) {
                        botEntity.getWorld().playSound(botEntity.getLocation(),
                                org.bukkit.Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
                    }

                    // Apply knockback (NPCs don't receive it by default)
                    // For arrows: use arrow travel direction; for melee: use attacker direction
                    Vector kbDir = arrowDir != null ? arrowDir.clone() : null;
                    if (kbDir == null) {
                        kbDir = botEntity.getLocation().toVector()
                                .subtract(player.getLocation().toVector());
                        kbDir.setY(0);
                        if (kbDir.lengthSquared() > 0.001) kbDir.normalize();
                    }

                    if (kbDir != null && kbDir.lengthSquared() > 0.001) {
                        double kbH = arrowDir != null ? 0.5 : 0.4;
                        double kbV = arrowDir != null ? 0.15 : 0.4;
                        if (arrowDir == null && player.isSprinting()) kbH += 0.4;
                        if (arrowDir == null) {
                            ItemStack weapon = player.getInventory().getItemInMainHand();
                            if (weapon != null && weapon.hasItemMeta()) {
                                int kbLevel = weapon.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.KNOCKBACK);
                                kbH += kbLevel * 0.5;
                            }
                        }
                        if (shielding) { kbH *= 0.15; kbV *= 0.15; }
                        botEntity.setVelocity(kbDir.multiply(kbH).setY(kbV));
                    }
                }
            }
        }
    }

    /**
     * Handle splash potions affecting bots.
     * Citizens NPCs don't receive potion effects by default, so we manually apply them.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPotionSplash(PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();
        
        // Check all affected entities
        for (LivingEntity entity : event.getAffectedEntities()) {
            // Check if this is a bot NPC
            if (CitizensAPI.getNPCRegistry() == null) continue;
            NPC npc = CitizensAPI.getNPCRegistry().getNPC(entity);
            if (npc == null) continue;
            
            // Check if this bot is in a bot duel
            BotManager.BotDuel duel = plugin.getBotManager().getBotDuelByBot(npc);
            if (duel == null) continue;
            
            // Get intensity based on distance (vanilla behavior)
            double intensity = event.getIntensity(entity);
            if (intensity <= 0) continue;
            
            // Apply potion effects to the bot
            ItemStack potionItem = potion.getItem();
            if (potionItem.hasItemMeta() && potionItem.getItemMeta() instanceof PotionMeta meta) {
                // Apply base effect
                if (meta.getBasePotionType() != null) {
                    for (PotionEffect effect : meta.getBasePotionType().getPotionEffects()) {
                        // Scale duration by intensity
                        int duration = (int) (effect.getDuration() * intensity);
                        if (duration > 0) {
                            entity.addPotionEffect(new PotionEffect(
                                effect.getType(), duration, effect.getAmplifier(), 
                                effect.isAmbient(), effect.hasParticles()));
                        }
                    }
                }
                
                // Apply custom effects
                for (PotionEffect effect : meta.getCustomEffects()) {
                    int duration = (int) (effect.getDuration() * intensity);
                    if (duration > 0) {
                        entity.addPotionEffect(new PotionEffect(
                            effect.getType(), duration, effect.getAmplifier(),
                            effect.isAmbient(), effect.hasParticles()));
                    }
                }
                
                // Handle instant effects (healing/harming)
                if (meta.getBasePotionType() != null) {
                    for (PotionEffect effect : meta.getBasePotionType().getPotionEffects()) {
                        if (effect.getType().isInstant()) {
                            // Instant health/harming - apply directly
                            double healthChange = 0;
                            if (effect.getType().equals(org.bukkit.potion.PotionEffectType.INSTANT_HEALTH)) {
                                healthChange = (4 << effect.getAmplifier()) * intensity;
                                entity.setHealth(Math.min(entity.getHealth() + healthChange, entity.getMaxHealth()));
                            } else if (effect.getType().equals(org.bukkit.potion.PotionEffectType.INSTANT_DAMAGE)) {
                                healthChange = (6 << effect.getAmplifier()) * intensity;
                                entity.damage(healthChange);
                            }
                        }
                    }
                }
            }
        }
    }
}
