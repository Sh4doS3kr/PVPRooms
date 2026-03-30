package com.pvprooms.bot;

import com.pvprooms.PvPRoomsPro;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * Combat AI for practice bots.
 * Handles attacking, healing, potion throwing, and movement.
 */
public class BotCombatAI {

    private final PvPRoomsPro plugin;
    private final NPC npc;
    private final Player target;
    private final BotDifficulty difficulty;
    private final String kitName;
    private final Random random = new Random();
    
    private BukkitTask combatTask;
    private BukkitTask movementTask;
    private long lastAttackTime = 0;
    private long lastHealTime = 0;
    private long lastPotionTime = 0;
    private int comboCount = 0;
    private boolean isStrafing = false;
    private int strafeDirection = 1;

    public BotCombatAI(PvPRoomsPro plugin, NPC npc, Player target, 
                       BotDifficulty difficulty, String kitName) {
        this.plugin = plugin;
        this.npc = npc;
        this.target = target;
        this.difficulty = difficulty;
        this.kitName = kitName;
    }

    public void start() {
        startCombatLoop();
        startMovementLoop();
    }

    public void stop() {
        if (combatTask != null) {
            combatTask.cancel();
            combatTask = null;
        }
        if (movementTask != null) {
            movementTask.cancel();
            movementTask = null;
        }
    }

    private void startCombatLoop() {
        combatTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!npc.isSpawned() || target == null || !target.isOnline() || target.isDead()) {
                    stop();
                    return;
                }

                LivingEntity botEntity = (LivingEntity) npc.getEntity();
                if (botEntity == null || botEntity.isDead()) {
                    stop();
                    return;
                }

                double distance = botEntity.getLocation().distance(target.getLocation());
                double health = botEntity.getHealth();
                double maxHealth = botEntity.getMaxHealth();
                double healthPercent = (health / maxHealth) * 100;

                // Healing logic
                if (healthPercent <= difficulty.healThreshold) {
                    tryHeal(botEntity);
                }

                // Potion throwing logic (for kits with potions)
                if (distance <= 10) {
                    tryThrowPotion(botEntity);
                }

                // Combat logic
                if (distance <= 4.0) {
                    tryAttack(botEntity, distance);
                } else if (distance <= 16) {
                    // Chase the player
                    npc.getNavigator().setTarget(target, true);
                }
            }
        }.runTaskTimer(plugin, 5L, difficulty.ticksBetweenActions);
    }

    private void startMovementLoop() {
        movementTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!npc.isSpawned() || target == null || !target.isOnline()) {
                    return;
                }

                LivingEntity botEntity = (LivingEntity) npc.getEntity();
                if (botEntity == null) return;

                double distance = botEntity.getLocation().distance(target.getLocation());

                // Strafing behavior (more aggressive at higher difficulties)
                if (distance <= 5 && random.nextDouble() < difficulty.hitAccuracy * 0.5) {
                    performStrafe(botEntity);
                }

                // W-tapping (sprint reset) at higher difficulties
                if (difficulty == BotDifficulty.HARD || difficulty == BotDifficulty.HACKER) {
                    if (random.nextDouble() < 0.3 && botEntity instanceof Player botPlayer) {
                        botPlayer.setSprinting(false);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (botEntity.isValid()) botPlayer.setSprinting(true);
                        }, 2L);
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 5L);
    }

    private void tryAttack(LivingEntity botEntity, double distance) {
        long now = System.currentTimeMillis();
        
        // Check reaction time
        if (now - lastAttackTime < difficulty.reactionTimeMs) {
            return;
        }

        // Check if we should hit (accuracy)
        if (random.nextDouble() > difficulty.hitAccuracy) {
            return;
        }

        // Look at target
        Location targetLoc = target.getLocation();
        botEntity.teleport(botEntity.getLocation().setDirection(
                targetLoc.toVector().subtract(botEntity.getLocation().toVector()).normalize()));

        // Attack if in range
        if (distance <= 3.5) {
            // Swing arm animation
            if (botEntity instanceof Player botPlayer) {
                botPlayer.swingMainHand();
            }

            // Deal damage
            double baseDamage = getWeaponDamage(botEntity);
            
            // Critical hit chance (higher for harder difficulties)
            boolean isCrit = random.nextDouble() < (difficulty.hitAccuracy * 0.3) 
                    && botEntity.getFallDistance() > 0;
            if (isCrit) {
                baseDamage *= 1.5;
            }

            // Apply combo bonus for hacker difficulty
            if (difficulty == BotDifficulty.HACKER) {
                comboCount++;
                if (comboCount > 3) {
                    baseDamage *= 1.1;
                }
            }

            target.damage(baseDamage, botEntity);
            lastAttackTime = now;

            // Knockback
            Vector knockback = target.getLocation().toVector()
                    .subtract(botEntity.getLocation().toVector())
                    .normalize()
                    .multiply(0.4)
                    .setY(0.35);
            target.setVelocity(target.getVelocity().add(knockback));
        }
    }

    private void tryHeal(LivingEntity botEntity) {
        long now = System.currentTimeMillis();
        if (now - lastHealTime < 2000) return; // 2 second cooldown

        // Find golden apples in inventory
        if (botEntity instanceof Player botPlayer) {
            PlayerInventory inv = botPlayer.getInventory();
            int slot = findItem(inv, Material.GOLDEN_APPLE);
            
            if (slot == -1) {
                slot = findItem(inv, Material.ENCHANTED_GOLDEN_APPLE);
            }

            if (slot != -1) {
                final int gappleSlot = slot; // Make effectively final for lambda
                // Simulate eating
                int heldSlot = inv.getHeldItemSlot();
                inv.setHeldItemSlot(gappleSlot);
                
                // Eating delay based on difficulty
                int eatDelay = switch (difficulty) {
                    case EASY -> 40;
                    case MEDIUM -> 32;
                    case HARD -> 24;
                    case HACKER -> 10; // Almost instant
                };

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!botEntity.isValid()) return;
                    
                    ItemStack item = inv.getItem(gappleSlot);
                    if (item != null && (item.getType() == Material.GOLDEN_APPLE 
                            || item.getType() == Material.ENCHANTED_GOLDEN_APPLE)) {
                        
                        // Apply effects
                        if (item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
                            botEntity.addPotionEffect(new PotionEffect(
                                    PotionEffectType.REGENERATION, 400, 1));
                            botEntity.addPotionEffect(new PotionEffect(
                                    PotionEffectType.ABSORPTION, 2400, 3));
                            botEntity.addPotionEffect(new PotionEffect(
                                    PotionEffectType.RESISTANCE, 6000, 0));
                            botEntity.addPotionEffect(new PotionEffect(
                                    PotionEffectType.FIRE_RESISTANCE, 6000, 0));
                        } else {
                            botEntity.addPotionEffect(new PotionEffect(
                                    PotionEffectType.REGENERATION, 100, 1));
                            botEntity.addPotionEffect(new PotionEffect(
                                    PotionEffectType.ABSORPTION, 2400, 0));
                        }

                        // Consume item
                        item.setAmount(item.getAmount() - 1);
                        inv.setHeldItemSlot(heldSlot);
                    }
                }, eatDelay);

                lastHealTime = now;
            }
        }
    }

    private void tryThrowPotion(LivingEntity botEntity) {
        long now = System.currentTimeMillis();
        if (now - lastPotionTime < 3000) return; // 3 second cooldown

        if (!(botEntity instanceof Player botPlayer)) return;

        PlayerInventory inv = botPlayer.getInventory();
        
        // Find splash potions
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == Material.SPLASH_POTION) {
                // Throw at target with some accuracy based on difficulty
                Location targetLoc = target.getLocation();
                
                // Add some inaccuracy for easier difficulties
                if (difficulty != BotDifficulty.HACKER) {
                    double spread = (1.0 - difficulty.hitAccuracy) * 2;
                    targetLoc.add(
                            random.nextGaussian() * spread,
                            random.nextGaussian() * spread * 0.5,
                            random.nextGaussian() * spread
                    );
                }

                // Calculate throw velocity
                Vector direction = targetLoc.toVector()
                        .subtract(botEntity.getLocation().toVector())
                        .normalize();
                
                double distance = botEntity.getLocation().distance(target.getLocation());
                direction.multiply(Math.min(distance * 0.15, 1.5));
                direction.setY(direction.getY() + 0.3);

                // Spawn and throw potion
                org.bukkit.entity.ThrownPotion thrown = botEntity.getWorld()
                        .spawn(botEntity.getEyeLocation(), org.bukkit.entity.ThrownPotion.class);
                thrown.setItem(item.clone());
                thrown.setVelocity(direction);
                thrown.setShooter(botEntity);

                // Consume potion
                item.setAmount(item.getAmount() - 1);
                lastPotionTime = now;
                break;
            }
        }
    }

    private void performStrafe(LivingEntity botEntity) {
        if (random.nextDouble() < 0.3) {
            strafeDirection *= -1;
        }

        Vector strafe = botEntity.getLocation().getDirection()
                .crossProduct(new Vector(0, 1, 0))
                .normalize()
                .multiply(0.3 * strafeDirection);

        botEntity.setVelocity(botEntity.getVelocity().add(strafe));
    }

    private double getWeaponDamage(LivingEntity botEntity) {
        if (!(botEntity instanceof Player botPlayer)) return 1.0;
        
        ItemStack weapon = botPlayer.getInventory().getItemInMainHand();
        if (weapon == null) return 1.0;

        return switch (weapon.getType()) {
            case NETHERITE_SWORD -> 8.0;
            case DIAMOND_SWORD -> 7.0;
            case IRON_SWORD -> 6.0;
            case STONE_SWORD -> 5.0;
            case WOODEN_SWORD, GOLDEN_SWORD -> 4.0;
            case NETHERITE_AXE -> 10.0;
            case DIAMOND_AXE -> 9.0;
            case IRON_AXE -> 9.0;
            case STONE_AXE -> 9.0;
            case MACE -> 7.0;
            default -> 1.0;
        };
    }

    private int findItem(PlayerInventory inv, Material material) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == material) {
                return i;
            }
        }
        return -1;
    }

    public BotDifficulty getDifficulty() {
        return difficulty;
    }
}
