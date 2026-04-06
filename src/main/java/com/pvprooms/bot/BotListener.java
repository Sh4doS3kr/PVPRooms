package com.pvprooms.bot;

import com.pvprooms.PvPRoomsPro;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCDeathEvent;
import net.citizensnpcs.api.event.NPCDespawnEvent;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.BroadcastMessageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Handles events related to bot practice duels.
 * 
 * Totem system: ALL bot damage goes through a single HIGHEST-priority handler
 * that manually checks for totems before allowing death. Citizens NPCs do NOT
 * have native totem support, so we handle it entirely ourselves.
 * 
 * Knockback system: Uses the vanilla Minecraft knockback formula from the
 * MC source code. The formula halves existing velocity then adds the knockback
 * vector, rather than replacing velocity entirely. This produces natural-feeling
 * knockback identical to vanilla player-vs-player combat.
 */
public class BotListener implements Listener {

    private final PvPRoomsPro plugin;

    public BotListener(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CONNECTION EVENTS
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getBotManager().isInBotDuel(player.getUniqueId())) {
            plugin.getBotManager().onPlayerDisconnect(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getBotManager().wasInBotDuel(player.getUniqueId())) {
            plugin.getBotManager().clearDisconnectedPlayer(player.getUniqueId());
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.teleport(plugin.getLobbySpawn());
                plugin.getLobbyManager().giveLobbyItems(player);
                player.sendMessage(plugin.prefix() + "§7Tu duelo contra bot terminó por desconexión.");
            }, 5L);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DEATH & DESPAWN EVENTS
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // ── Bot NPC dying: suppress death message ──
        if (CitizensAPI.getNPCRegistry() != null) {
            NPC npc = CitizensAPI.getNPCRegistry().getNPC(player);
            if (npc != null) {
                BotManager.BotDuel duel = plugin.getBotManager().getBotDuelByBot(npc);
                if (duel != null) {
                    event.setCancelled(true);
                    event.setDeathMessage(null);
                    event.getDrops().clear();
                    event.setDroppedExp(0);
                    return;
                }
            }
        }

        // ── Real player dying in bot duel ──
        if (plugin.getBotManager().isInBotDuel(player.getUniqueId())) {
            event.setCancelled(true);
            event.setDeathMessage(null);
            event.getDrops().clear();
            event.setDroppedExp(0);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(20f);
            player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
            plugin.getBotManager().onPlayerDeath(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNPCDeath(NPCDeathEvent event) {
        NPC npc = event.getNPC();
        BotManager.BotDuel duel = plugin.getBotManager().getBotDuelByBot(npc);
        if (duel != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
        plugin.getBotManager().onBotDeath(npc);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNPCDespawn(NPCDespawnEvent event) {
        NPC npc = event.getNPC();
        plugin.getBotManager().onBotDeath(npc);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BROADCAST SUPPRESSION
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBroadcastMessage(BroadcastMessageEvent event) {
        String msg = PlainTextComponentSerializer.plainText().serialize(event.message());
        for (NPC bot : plugin.getBotManager().getAllActiveBots()) {
            if (bot != null && msg.contains(bot.getName())) {
                event.setCancelled(true);
                return;
            }
        }
        // Also check recently dead bot names (bot may already be removed from playerBots)
        for (String name : plugin.getBotManager().getRecentBotNames()) {
            if (msg.contains(name)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UNIFIED BOT DAMAGE HANDLER — ALL damage to bots goes through here
    // 
    // Runs at HIGHEST priority so we are the LAST handler to touch the event.
    // This ensures no other plugin can undo our totem activation or modify
    // the damage after we've checked it.
    //
    // We handle:
    //  - Totem activation (for ALL damage sources)
    //  - Bot death (if no totem)
    //  - Vanilla knockback (for player melee/projectile attacks)
    //  - Shield blocking sound
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBotDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity botEntity)) return;
        if (CitizensAPI.getNPCRegistry() == null) return;

        NPC npc = CitizensAPI.getNPCRegistry().getNPC(botEntity);
        if (npc == null) return;

        BotManager.BotDuel duel = plugin.getBotManager().getBotDuelByBot(npc);
        if (duel == null || !duel.active) return;

        // ── Resolve attacker (player or projectile shooter) ──
        Player attacker = null;
        Vector arrowDir = null;
        boolean isPlayerAttack = false;

        if (event instanceof EntityDamageByEntityEvent byEntity) {
            if (byEntity.getDamager() instanceof Player p) {
                attacker = p;
                isPlayerAttack = true;
            } else if (byEntity.getDamager() instanceof Projectile proj
                    && proj.getShooter() instanceof Player p) {
                attacker = p;
                isPlayerAttack = true;
                arrowDir = proj.getVelocity().clone();
                arrowDir.setY(0);
                if (arrowDir.lengthSquared() > 0.001) {
                    arrowDir.normalize();
                } else {
                    arrowDir = null;
                }
            }
        }

        // ── If player attack: verify it's from the duel opponent ──
        if (isPlayerAttack && attacker != null) {
            if (!plugin.getBotManager().isInBotDuel(attacker.getUniqueId())) return;
            NPC playerBot = plugin.getBotManager().getPlayerBot(attacker.getUniqueId());
            if (playerBot == null || !playerBot.getEntity().equals(botEntity)) return;
            // Allow the damage (un-cancel if another handler cancelled it)
            event.setCancelled(false);
        }

        // ── Calculate effective health (base + absorption) ──
        double currentHealth = botEntity.getHealth();
        double absorption = 0;
        if (botEntity instanceof Player botPlayer) {
            absorption = botPlayer.getAbsorptionAmount();
        }
        double effectiveHealth = currentHealth + absorption;
        double finalDamage = event.getFinalDamage();

        // ── LETHAL DAMAGE CHECK — Totem or death ──
        if (effectiveHealth - finalDamage <= 0) {
            // Search for totem: offhand first, then entire inventory
            ItemStack foundTotem = null;
            int totemSlot = -1;
            boolean totemInOffhand = false;

            ItemStack offhand = botEntity.getEquipment() != null
                    ? botEntity.getEquipment().getItemInOffHand() : null;

            if (offhand != null && offhand.getType() == Material.TOTEM_OF_UNDYING) {
                foundTotem = offhand;
                totemInOffhand = true;
            }

            // Also check main hand
            if (foundTotem == null) {
                ItemStack mainHand = botEntity.getEquipment() != null
                        ? botEntity.getEquipment().getItemInMainHand() : null;
                if (mainHand != null && mainHand.getType() == Material.TOTEM_OF_UNDYING) {
                    foundTotem = mainHand;
                    totemSlot = -2; // special marker for main hand
                }
            }

            // Search entire inventory
            if (foundTotem == null && botEntity instanceof Player botPlayer) {
                PlayerInventory inv = botPlayer.getInventory();
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack item = inv.getItem(i);
                    if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
                        foundTotem = item;
                        totemSlot = i;
                        break;
                    }
                }
            }

            if (foundTotem != null) {
                // ═══ TOTEM ACTIVATES! ═══
                event.setCancelled(true);

                // Consume totem
                consumeTotem(botEntity, foundTotem, totemSlot, totemInOffhand);

                // Apply vanilla totem effects
                activateTotemEffects(botEntity);

                // Safety: ensure bot health is set on next tick too (in case
                // another handler or the server resets it)
                final LivingEntity safeRef = botEntity;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (safeRef.isValid() && !safeRef.isDead()) {
                        if (safeRef.getHealth() <= 0) {
                            safeRef.setHealth(1.0);
                        }
                    }
                }, 1L);

                // Notify AI to flee and heal (pearl away + eat)
                if (duel.ai != null) {
                    duel.ai.onTotemPop();
                }

                return; // Totem consumed — bot survives
            }

            // ═══ NO TOTEM — Bot dies ═══
            event.setCancelled(true);
            if (isPlayerAttack && attacker != null) {
                plugin.getBotManager().onBotKilled(npc, attacker);
            } else {
                plugin.getBotManager().onBotDeath(npc);
            }
            return;
        }

        // ── NON-LETHAL DAMAGE — Apply knockback and shield effects ──
        if (isPlayerAttack && attacker != null) {
            // Shield blocking sound
            BotManager.BotDuel botDuel = plugin.getBotManager().getBotDuel(attacker.getUniqueId());
            boolean shielding = botDuel != null && botDuel.ai != null && botDuel.ai.isShieldBlocking();
            if (shielding) {
                botEntity.getWorld().playSound(botEntity.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
            }

            // Apply vanilla knockback (NPCs don't receive it by default)
            applyVanillaKnockback(botEntity, attacker, arrowDir, shielding);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TOTEM HELPER METHODS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Consume one totem from the bot's inventory.
     */
    private void consumeTotem(LivingEntity botEntity, ItemStack totem, int slot, boolean inOffhand) {
        if (inOffhand) {
            // Was in offhand
            if (totem.getAmount() <= 1) {
                botEntity.getEquipment().setItemInOffHand(new ItemStack(Material.AIR));
            } else {
                totem.setAmount(totem.getAmount() - 1);
            }
        } else if (slot == -2) {
            // Was in main hand
            if (totem.getAmount() <= 1) {
                botEntity.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));
            } else {
                totem.setAmount(totem.getAmount() - 1);
            }
        } else if (slot >= 0 && botEntity instanceof Player botPlayer) {
            // Was in inventory
            if (totem.getAmount() <= 1) {
                botPlayer.getInventory().setItem(slot, null);
            } else {
                totem.setAmount(totem.getAmount() - 1);
            }
        }
    }

    /**
     * Apply vanilla totem-of-undying effects to the bot entity.
     * Exactly mimics vanilla: set health to 1, clear fire, add regen/absorption/fire resist.
     */
    private void activateTotemEffects(LivingEntity entity) {
        // Set health to 1 (vanilla behavior)
        entity.setHealth(1.0);

        // Clear negative effects
        entity.setFireTicks(0);
        entity.removePotionEffect(PotionEffectType.POISON);
        entity.removePotionEffect(PotionEffectType.WITHER);

        // Apply vanilla totem buffs
        entity.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, 1));  // 45s Regen II
        entity.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1));    // 5s Absorption II
        entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0)); // 40s Fire Resist

        // Totem activation visual + sound
        entity.getWorld().playSound(entity.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        entity.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                entity.getLocation().add(0, 1, 0), 100, 0.5, 1, 0.5, 0.5);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VANILLA KNOCKBACK SYSTEM
    //
    // Based on the Minecraft source code (LivingEntity.knockback method):
    //
    //   Vec3 vel = this.getDeltaMovement();
    //   Vec3 knockVec = new Vec3(dx, 0, dz).normalize().scale(strength);
    //   newVelX = vel.x / 2.0 - knockVec.x;
    //   newVelY = onGround ? min(0.4, vel.y / 2.0 + strength) : vel.y;
    //   newVelZ = vel.z / 2.0 - knockVec.z;
    //
    // In the source, knockVec points TOWARD the attacker (sin/cos of attacker yaw).
    // Subtracting it pushes the victim AWAY from the attacker.
    //
    // In our code, kbDir points FROM attacker TO victim (away from attacker),
    // so we ADD it instead of subtracting.
    //
    // Base attack:          strength = 0.4
    // Sprint bonus:         separate call with strength = 0.5
    // KB enchant per level: separate call with strength = level * 0.5
    //
    // Each "separate call" takes the ALREADY-modified velocity and applies
    // another round of halving + adding.
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Apply vanilla-accurate knockback to a bot entity.
     * This mimics the exact MC source code formula.
     */
    private void applyVanillaKnockback(LivingEntity botEntity, Player attacker,
                                        Vector arrowDir, boolean shielding) {
        // Direction from attacker to bot (victim gets pushed this way)
        Vector kbDir;
        if (arrowDir != null) {
            kbDir = arrowDir.clone();
        } else {
            kbDir = botEntity.getLocation().toVector()
                    .subtract(attacker.getLocation().toVector());
            kbDir.setY(0);
            if (kbDir.lengthSquared() < 0.001) return;
            kbDir.normalize();
        }

        // ── Step 1: Base knockback (strength 0.4) ──
        Vector vel = botEntity.getVelocity().clone();
        vel = applyKnockbackStep(vel, kbDir, 0.4, botEntity.isOnGround());

        // ── Step 2: Sprint bonus (strength 0.5) — separate knockback call ──
        if (arrowDir == null && attacker.isSprinting()) {
            vel = applyKnockbackStep(vel, kbDir, 0.5, botEntity.isOnGround());
        }

        // ── Step 3: Knockback enchantment — separate call per level ──
        if (arrowDir == null) {
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            if (weapon != null) {
                int kbLevel = weapon.getEnchantmentLevel(Enchantment.KNOCKBACK);
                if (kbLevel > 0) {
                    vel = applyKnockbackStep(vel, kbDir, kbLevel * 0.5, botEntity.isOnGround());
                }
            }
        }

        // ── Step 4: Arrow knockback (Punch enchantment) ──
        if (arrowDir != null) {
            // Arrows have their own knockback from Punch enchantment
            // Base arrow KB is already handled by strength 0.4 above
            // Punch adds extra: each level adds 0.5 strength
            if (attacker.getInventory().getItemInMainHand().getType() == Material.BOW) {
                int punchLevel = attacker.getInventory().getItemInMainHand()
                        .getEnchantmentLevel(Enchantment.PUNCH);
                if (punchLevel > 0) {
                    vel = applyKnockbackStep(vel, kbDir, punchLevel * 0.5, botEntity.isOnGround());
                }
            }
        }

        // ── Shield reduces knockback significantly ──
        if (shielding) {
            vel.setX(vel.getX() * 0.15);
            vel.setY(vel.getY() * 0.15);
            vel.setZ(vel.getZ() * 0.15);
        }

        botEntity.setVelocity(vel);
    }

    /**
     * Apply one round of the vanilla knockback formula.
     * Each "round" corresponds to one knockback() call in the MC source.
     *
     * Formula per round:
     *   velX = currentVelX / 2.0 + direction.x * strength
     *   velY = onGround ? min(0.4, currentVelY / 2.0 + strength) : currentVelY
     *   velZ = currentVelZ / 2.0 + direction.z * strength
     *
     * @param currentVel The entity's current velocity
     * @param direction  Normalized direction FROM attacker TO victim (push direction)
     * @param strength   Knockback strength for this round
     * @param onGround   Whether the entity is on the ground
     * @return The new velocity after this knockback round
     */
    private Vector applyKnockbackStep(Vector currentVel, Vector direction,
                                       double strength, boolean onGround) {
        double newX = currentVel.getX() / 2.0 + direction.getX() * strength;
        double newY;
        if (onGround) {
            newY = Math.min(0.4, currentVel.getY() / 2.0 + strength);
        } else {
            // Airborne: don't add vertical knockback (Java Edition behavior)
            newY = currentVel.getY();
        }
        double newZ = currentVel.getZ() / 2.0 + direction.getZ() * strength;
        return new Vector(newX, newY, newZ);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SPLASH POTION HANDLER
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPotionSplash(PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();

        for (LivingEntity entity : event.getAffectedEntities()) {
            if (CitizensAPI.getNPCRegistry() == null) continue;
            NPC npc = CitizensAPI.getNPCRegistry().getNPC(entity);
            if (npc == null) continue;

            BotManager.BotDuel duel = plugin.getBotManager().getBotDuelByBot(npc);
            if (duel == null) continue;

            double intensity = event.getIntensity(entity);
            if (intensity <= 0) continue;

            ItemStack potionItem = potion.getItem();
            if (potionItem.hasItemMeta() && potionItem.getItemMeta() instanceof PotionMeta meta) {
                // Apply base effects
                if (meta.getBasePotionType() != null) {
                    for (PotionEffect effect : meta.getBasePotionType().getPotionEffects()) {
                        if (effect.getType().isInstant()) {
                            applyInstantEffect(entity, effect, intensity);
                        } else {
                            int duration = (int) (effect.getDuration() * intensity);
                            if (duration > 0) {
                                entity.addPotionEffect(new PotionEffect(
                                        effect.getType(), duration, effect.getAmplifier(),
                                        effect.isAmbient(), effect.hasParticles()));
                            }
                        }
                    }
                }
                // Apply custom effects
                for (PotionEffect effect : meta.getCustomEffects()) {
                    if (effect.getType().isInstant()) {
                        applyInstantEffect(entity, effect, intensity);
                    } else {
                        int duration = (int) (effect.getDuration() * intensity);
                        if (duration > 0) {
                            entity.addPotionEffect(new PotionEffect(
                                    effect.getType(), duration, effect.getAmplifier(),
                                    effect.isAmbient(), effect.hasParticles()));
                        }
                    }
                }
            }
        }
    }

    private void applyInstantEffect(LivingEntity entity, PotionEffect effect, double intensity) {
        if (effect.getType().equals(PotionEffectType.INSTANT_HEALTH)) {
            double heal = (4 << effect.getAmplifier()) * intensity;
            entity.setHealth(Math.min(entity.getHealth() + heal, entity.getMaxHealth()));
        } else if (effect.getType().equals(PotionEffectType.INSTANT_DAMAGE)) {
            double dmg = (6 << effect.getAmplifier()) * intensity;
            entity.damage(dmg);
        }
    }
}
